package ru.admincontrol.util;

import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;

import java.util.ArrayList;

/** /cw server stop: 10 секунд отсчёта, затем штатная остановка как /stop. */
public final class ServerStopService {
	public static final int COUNTDOWN = 10;
	private static boolean active;
	private static long stopAt;
	private static int lastShown = -1;
	private static String reason = "";

	private ServerStopService() {
	}

	public static boolean active() {
		return active;
	}

	public static String start(MinecraftServer server, String why) {
		if (server == null) {
			return "Сервер недоступен.";
		}
		if (active) {
			return "Остановка сервера уже запущена.";
		}
		active = true;
		reason = why == null ? "" : why.trim();
		stopAt = System.currentTimeMillis() + COUNTDOWN * 1000L;
		lastShown = -1;
		if (EventService.active()) {
			EventService.stop(server, "остановлено: остановка сервера");
		}
		EventMapEditor.stopAll(server, "остановка сервера");
		TrainingService.stopAll(server);
		if (BoxEventService.active()) {
			BoxEventService.forceCancel(server);
		}
		if (ClanWarService.active()) {
			ClanWarService.stop(server, "остановка сервера");
		}
		Chat.broadcast(server, Chat.red("Сервер будет остановлен через " + COUNTDOWN + " секунд. Причина: "
				+ (reason.isBlank() ? "не указана" : reason)));
		show(server, COUNTDOWN);
		return null;
	}

	/** Во время отсчёта МП, тренировки и бокс доступны только админам 8+. */
	public static boolean blocks(ServerPlayerEntity player) {
		if (!active) {
			return false;
		}
		DataManager data = DataManager.get();
		return player == null || data == null || data.getAdminLevel(player) < AdminRanks.GA;
	}

	public static boolean deny(ServerPlayerEntity player) {
		if (!blocks(player)) {
			return false;
		}
		if (player != null) {
			Chat.send(player, Chat.red("Сервер останавливается: мероприятия, тренировки и бокс недоступны."));
		}
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (!active || server == null) {
			return;
		}
		long left = stopAt - System.currentTimeMillis();
		int sec = (int) Math.max(0L, (left + 999L) / 1000L);
		if (sec != lastShown && sec > 0) {
			show(server, sec);
		}
		if (left > 0) {
			return;
		}
		active = false;
		Text kick = Text.literal("Сервер остановлен" + (reason.isBlank() ? "." : ": " + reason));
		for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
			if (player.networkHandler != null && !(player.networkHandler instanceof NpcNetworkHandler)) {
				player.networkHandler.disconnect(kick);
			}
		}
		if (DataManager.get() != null) {
			DataManager.get().saveNow();
		}
		server.stop(false);
	}

	private static void show(MinecraftServer server, int sec) {
		lastShown = sec;
		Text title = Text.literal("Выключение сервера:").styled(s -> s.withColor(0xFF5555));
		Text sub = Text.literal(String.valueOf(sec)).styled(s -> s.withColor(0xFFFF55).withBold(true));
		Text bar = Text.literal("Причина: " + (reason.isBlank() ? "не указана" : reason)).styled(s -> s.withColor(0xFFAA00));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.networkHandler == null || player.networkHandler instanceof NpcNetworkHandler) {
				continue;
			}
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 5));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
			player.networkHandler.sendPacket(new TitleS2CPacket(title));
			player.networkHandler.sendPacket(new OverlayMessageS2CPacket(bar));
		}
	}
}
