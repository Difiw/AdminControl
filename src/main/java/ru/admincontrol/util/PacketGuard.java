package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Size and rate guard in front of every Admin Control C2S channel. */
public final class PacketGuard {
	private static final int MAX_PACKET = 1_100_000;
	private static final int MAX_PER_SECOND = 80;
	private static final Map<UUID, Deque<Long>> WINDOWS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> REPORTED = new ConcurrentHashMap<>();
	private PacketGuard() {}

	public static boolean allow(ServerPlayerEntity player, Identifier channel, int bytes) {
		if (player == null) return false;
		long now = System.currentTimeMillis();
		Deque<Long> q = WINDOWS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
		q.addLast(now);
		while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.removeFirst();
		String why = null;
		if (bytes < 0 || bytes > MAX_PACKET) why = "payload " + bytes + " bytes, max " + MAX_PACKET;
		else if (q.size() > MAX_PER_SECOND) why = q.size() + " custom payloads/s, max " + MAX_PER_SECOND;
		if (why == null) return true;
		Long previous = REPORTED.put(player.getUuid(), now);
		if (previous == null || now - previous > 30_000L) {
			AntiCheatService.confirmed(player, AntiCheatRules.PACKET_ABUSE, "спам/подделка пакетов",
					why, java.util.List.of("Канал: " + channel, "Пакет отклонён до обработчика."));
		}
		return false;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) return;
		WINDOWS.remove(player.getUuid());
		REPORTED.remove(player.getUuid());
	}
}
