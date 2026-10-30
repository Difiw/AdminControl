package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.event.PlayerTracker;
import ru.admincontrol.net.ModPackets;

/**
 * Admin Control работает только когда в мире есть ГА (или разработчик).
 * До этого мод «спит»: команды скрыты (кроме /add_admin), события, защита и проверки не срабатывают.
 */
public final class ModGate {
	private static volatile boolean on;
	private static volatile MinecraftServer server;

	private ModGate() {
	}

	public static boolean on() {
		return on;
	}

	/** Новый мир / выключение: состояние берётся из данных без уведомлений. */
	public static void reset(MinecraftServer current) {
		server = current;
		DataManager data = DataManager.get();
		on = current != null && data != null && data.hasHeadStaff();
		if (current != null && !on) {
			AdminControlMod.LOGGER.warn("Admin Control отключён: на сервере нет ГА. Назначьте: /add_admin <ник> 8");
		}
	}

	/** После изменения списка админов: включить/выключить мод на лету. */
	public static void refresh() {
		DataManager data = DataManager.get();
		MinecraftServer current = server;
		boolean now = current != null && data != null && data.hasHeadStaff();
		if (now == on) {
			return;
		}
		on = now;
		current.execute(() -> changed(current, now));
	}

	private static void changed(MinecraftServer current, boolean active) {
		if (active) {
			NpcService.restoreAll(current);
		} else {
			ClanWarService.stop(current, "Admin Control отключён");
			if (EventService.active()) {
				EventService.stop(current, "остановлено: Admin Control отключён");
			}
			TrainingService.stopAll(current);
			NpcService.removeAll(current);
		}
		for (ServerPlayerEntity player : current.getPlayerManager().getPlayerList()) {
			current.getCommandManager().sendCommandTree(player);
			if (active) {
				PlayerTracker.joined(current, player);
			} else {
				clearClient(player);
			}
			Chat.send(player, active
					? Chat.green("Admin Control включён: на сервере назначен ГА.")
					: Chat.yellow("Admin Control отключён: на сервере нет ГА."));
		}
		AdminControlMod.LOGGER.info(active ? "Admin Control включён: назначен ГА."
				: "Admin Control отключён: на сервере нет ГА.");
	}

	private static void clearClient(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, ModPackets.BOARD)) {
			return;
		}
		PacketByteBuf board = PacketByteBufs.create();
		board.writeBoolean(false);
		board.writeVarInt(0);
		board.writeDouble(0);
		ServerPlayNetworking.send(player, ModPackets.BOARD, board);
		PacketByteBuf features = PacketByteBufs.create();
		features.writeVarInt(0);
		ServerPlayNetworking.send(player, ModPackets.FEATURES, features);
		PacketByteBuf freeze = PacketByteBufs.create();
		freeze.writeVarInt(0);
		ServerPlayNetworking.send(player, ModPackets.FREEZE, freeze);
		PacketByteBuf auth = PacketByteBufs.create();
		auth.writeBoolean(false);
		ServerPlayNetworking.send(player, ModPackets.AUTH_LOCK, auth);
		PacketByteBuf activity = PacketByteBufs.create();
		activity.writeBoolean(false);
		ServerPlayNetworking.send(player, ModPackets.ACTIVITY_LOCK, activity);
	}
}
