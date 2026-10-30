package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.net.ModPackets;

public final class ActivityLock {
	private ActivityLock() {
	}

	public static boolean blocked(ServerPlayerEntity player) {
		return player != null && (TrainingService.active(player) || EventService.member(player));
	}

	public static void sync(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(blocked(player));
		ServerPlayNetworking.send(player, ModPackets.ACTIVITY_LOCK, buf);
	}

	public static void deny(ServerPlayerEntity player) {
		deny(player, false);
	}

	public static void deny(ServerPlayerEntity player, boolean message) {
		if (player == null) {
			return;
		}
		player.closeHandledScreen();
		if (message) {
			Chat.send(player, Chat.red("На МП и тренировке инвентарь и /clan закрыты."));
		}
	}
}
