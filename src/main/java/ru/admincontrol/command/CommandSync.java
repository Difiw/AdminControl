package ru.admincontrol.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CommandSync {
	private CommandSync() {
	}

	public static void refresh(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || player.getServer() == null) {
			return;
		}
		player.getServer().getPlayerManager().sendCommandTree(player);
	}

	public static void refreshAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			refresh(player);
		}
	}

	public static void refreshAllExcept(MinecraftServer server, String nick) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (nick != null && player.getGameProfile().getName().equalsIgnoreCase(nick)) {
				continue;
			}
			refresh(player);
		}
	}
}
