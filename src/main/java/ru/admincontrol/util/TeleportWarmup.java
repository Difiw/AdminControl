package ru.admincontrol.util;

import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ожидание 5 секунд перед телепортом (команды, тренировка, МП).
 * Получил урон — таймер начинается заново, чтобы нельзя было сбежать из PvP.
 */
public final class TeleportWarmup {
	public static final int SECONDS = 5;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private TeleportWarmup() {
	}

	private static final class Pending {
		String label;
		Runnable action;
		long endsAt;
		int shown = -1;
	}

	/** Запустить ожидание; action выполнится на серверном потоке, если 5 секунд не было урона. */
	public static void start(ServerPlayerEntity player, String label, Runnable action) {
		if (player == null || action == null) {
			return;
		}
		if (NpcService.isNpc(player)) {
			action.run();
			return;
		}
		Pending prev = PENDING.remove(player.getUuid());
		if (prev != null) {
			Chat.send(player, Chat.yellow("Предыдущая телепортация («" + prev.label + "») заменена."));
		}
		Pending p = new Pending();
		p.label = label == null || label.isBlank() ? "телепортация" : label;
		p.action = action;
		p.endsAt = System.currentTimeMillis() + SECONDS * 1000L;
		PENDING.put(player.getUuid(), p);
		Chat.send(player, Chat.yellow("Телепортация через " + SECONDS + " секунд («" + p.label
				+ "»). Не получайте урон — иначе таймер начнётся заново."));
		show(player, p, SECONDS);
	}

	public static boolean pending(ServerPlayerEntity player) {
		return player != null && PENDING.containsKey(player.getUuid());
	}

	/** Игрок получил урон — таймер с нуля. */
	public static void onDamage(ServerPlayerEntity player) {
		Pending p = player == null ? null : PENDING.get(player.getUuid());
		if (p == null) {
			return;
		}
		p.endsAt = System.currentTimeMillis() + SECONDS * 1000L;
		p.shown = -1;
		player.sendMessage(Chat.red("Вы получили урон — таймер телепортации сброшен."), true);
		show(player, p, SECONDS);
	}

	public static void cancel(ServerPlayerEntity player, String why) {
		Pending p = player == null ? null : PENDING.remove(player.getUuid());
		if (p == null) {
			return;
		}
		clearTitle(player);
		if (why != null && !why.isBlank()) {
			Chat.send(player, Chat.red("Телепортация отменена: " + why));
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			PENDING.remove(player.getUuid());
		}
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty() || server == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (UUID id : PENDING.keySet().toArray(UUID[]::new)) {
			Pending p = PENDING.get(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (p == null) {
				continue;
			}
			if (player == null || player.isRemoved()) {
				PENDING.remove(id);
				continue;
			}
			if (!player.isAlive()) {
				cancel(player, "вы погибли.");
				continue;
			}
			if (FreezeService.frozen(player)) {
				cancel(player, "вы заморожены.");
				continue;
			}
			long left = p.endsAt - now;
			if (left > 0) {
				int sec = (int) ((left + 999L) / 1000L);
				if (sec != p.shown) {
					show(player, p, sec);
				}
				continue;
			}
			PENDING.remove(id);
			clearTitle(player);
			try {
				TpGuard.guarded(p.action);
			} catch (RuntimeException e) {
				ru.admincontrol.AdminControlMod.LOGGER.error("Телепорт после ожидания", e);
				Chat.send(player, Chat.red("Телепортация не удалась."));
			}
		}
	}

	private static void show(ServerPlayerEntity player, Pending p, int sec) {
		p.shown = sec;
		if (player.networkHandler == null) {
			return;
		}
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 5));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(
				Text.literal(String.valueOf(sec)).styled(s -> s.withColor(0xFFFF55).withBold(true))));
		player.networkHandler.sendPacket(new TitleS2CPacket(
				Text.literal("Телепортация через:").styled(s -> s.withColor(0x55FFFF))));
	}

	private static void clearTitle(ServerPlayerEntity player) {
		if (player.networkHandler != null) {
			player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
		}
	}
}
