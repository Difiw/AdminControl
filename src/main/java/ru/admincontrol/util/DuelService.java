package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary developer-only, reward-free player duel beta. */
public final class DuelService {
	private static final long EXPIRES_MS = 60_000L;
	private static final Map<UUID, Invite> INVITES = new ConcurrentHashMap<>();
	private record Invite(UUID challenger, long expiresAt) {}
	private DuelService() {}

	public static String invite(ServerPlayerEntity from, ServerPlayerEntity to) {
		String gate = gate(from);
		if (gate != null) return gate;
		if (to == null || to == from) return "Укажите другого игрока в сети.";
		if (!developer(to)) return "Участвовать могут только разработчики уровня 9.";
		if (EventService.active()) return "Сейчас уже идёт мероприятие или другая дуэль.";
		INVITES.put(to.getUuid(), new Invite(from.getUuid(), System.currentTimeMillis() + EXPIRES_MS));
		Chat.send(to, Chat.gold(from.getGameProfile().getName() + " вызывает вас на дуэль без награды. /duel accept или /duel deny (60 сек)."));
		Chat.send(from, Chat.green("Вызов отправлен игроку " + to.getGameProfile().getName() + "."));
		return null;
	}

	public static String accept(ServerPlayerEntity target) {
		String gate = gate(target);
		if (gate != null) return gate;
		Invite invite = INVITES.remove(target.getUuid());
		if (invite == null || invite.expiresAt < System.currentTimeMillis()) return "Активного вызова на дуэль нет.";
		ServerPlayerEntity challenger = target.getServer().getPlayerManager().getPlayer(invite.challenger);
		if (challenger == null) return "Игрок, вызвавший вас, вышел с сервера.";
		if (!EventService.startRestrictedDuel(challenger, target)) return "Не удалось запустить дуэль: проверьте, что другие МП не запущены.";
		if (!EventService.join(target)) { EventService.stop(target.getServer(), "дуэль отменена: второй игрок не вошёл"); return "Не удалось записать второго игрока."; }
		return null;
	}

	public static String deny(ServerPlayerEntity target) {
		Invite invite = target == null ? null : INVITES.remove(target.getUuid());
		if (invite == null) return "Активного вызова на дуэль нет.";
		if (target.getServer() != null) {
			ServerPlayerEntity from = target.getServer().getPlayerManager().getPlayer(invite.challenger);
			if (from != null) Chat.send(from, Chat.yellow(target.getGameProfile().getName() + " отклонил вызов на дуэль."));
		}
		return null;
	}

	public static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		INVITES.entrySet().removeIf(e -> e.getValue().expiresAt < now);
	}

	private static String gate(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) return "Команда доступна только игроку.";
		if (!developer(player)) return "Дуэли доступны только разработчику уровня 9.";
		if (!DataManager.get().settings().betaFeaturesEnabled) return "Дуэли сейчас выключены (включаются в /dserver).";
		return null;
	}
	private static boolean developer(ServerPlayerEntity player) {
		return player != null && DataManager.get() != null && DataManager.get().getAdminLevel(player) >= AdminRanks.DEV;
	}
}
