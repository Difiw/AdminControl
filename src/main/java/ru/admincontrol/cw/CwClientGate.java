package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit protocol handshake for client-required CW dimensions and editor UI. */
public final class CwClientGate {
	public static final int PROTOCOL = 8;
	private static final Map<UUID, Integer> CLIENTS = new ConcurrentHashMap<>();
	private CwClientGate() {}
	public static void hello(ServerPlayerEntity player) {
		CLIENTS.remove(player.getUuid());
		if (!ServerPlayNetworking.canSend(player, ModPackets.CW_HELLO)) return;
		var buf = PacketByteBufs.create(); buf.writeVarInt(PROTOCOL);
		var settings=ru.admincontrol.data.DataManager.get()==null?null:ru.admincontrol.data.DataManager.get().settings();
		buf.writeVarInt(settings==null?180:Math.max(30,Math.min(3600,settings.cwSkyCycleSeconds)));
		buf.writeVarInt(settings==null?100:Math.max(10,Math.min(100,settings.cwSkyIntensityPercent)));
		buf.writeBoolean(settings!=null&&settings.cwSkyReducedEffects);
		ServerPlayNetworking.send(player, ModPackets.CW_HELLO, buf);
		CwAnimationService.syncAll(player);
	}
	public static void acknowledge(ServerPlayerEntity player, int protocol) { CLIENTS.put(player.getUuid(), protocol); }
	public static void clear(ServerPlayerEntity player) { if (player != null) CLIENTS.remove(player.getUuid()); }
	public static String problem(ServerPlayerEntity player) {
		Integer version = CLIENTS.get(player.getUuid());
		if (version == null) {
			hello(player);
		}
		if (version == null) return "CW требует клиентский admin-control с поддержкой атмосферы и интерфейсов. Установите тот же JAR на клиент и переподключитесь.";
		if (version != PROTOCOL) return "Несовместимый CW client protocol: клиент=" + version + ", сервер=" + PROTOCOL + ". Обновите клиентский JAR.";
		return ru.admincontrol.emi.EmiServer.cwProblem(player);
	}
}
