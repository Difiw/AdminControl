package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;

public final class TabListColors {
	private TabListColors() {
	}

	public static Text displayName(ServerPlayerEntity player) {
		if (player == null) {
			return null;
		}
		DataManager data = DataManager.get();
		int level = data == null ? 0 : data.getAdminLevel(player);
		return Chat.nick(player.getGameProfile().getName(), level);
	}

	public static void refresh(MinecraftServer server) {
		if (server == null) {
			return;
		}
		var players = server.getPlayerManager().getPlayerList();
		if (players.isEmpty()) {
			return;
		}
		server.getPlayerManager().sendToAll(new net.minecraft.network.packet.s2c.play.PlayerListS2CPacket(
				java.util.EnumSet.of(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME), players));
	}
}
