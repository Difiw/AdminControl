package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.net.ModPackets;

public final class ClanNametags {
	private static String lastKey = "";
	private static byte[] lastPayload;

	private ClanNametags() {
	}

	public static void apply(ServerPlayerEntity player) {
		if (player != null) {
			refreshAll(player.getServer());
		}
	}

	public static void refreshAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		var list = server.getPlayerManager().getPlayerList();
		StringBuilder key = new StringBuilder(list.size() * 24);
		for (ServerPlayerEntity player : list) {
			key.append(player.getGameProfile().getName()).append('|');
			ClanRecord clan = NpcService.isNpc(player) ? null : ClanService.of(player);
			key.append(clan == null || clan.name == null ? "" : clan.name).append(';');
		}
		String next = key.toString();
		if (next.equals(lastKey) && lastPayload != null) {
			return;
		}
		PacketByteBuf built = snapshot(list);
		lastPayload = new byte[built.readableBytes()];
		built.getBytes(built.readerIndex(), lastPayload);
		lastKey = next;
		for (ServerPlayerEntity viewer : list) {
			if (viewer == null || viewer.networkHandler == null || viewer.networkHandler instanceof NpcNetworkHandler) {
				continue;
			}
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeBytes(lastPayload);
			ServerPlayNetworking.send(viewer, ModPackets.CLAN_TAGS, buf);
		}
	}

	private static PacketByteBuf snapshot(java.util.List<ServerPlayerEntity> list) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(list.size());
		for (ServerPlayerEntity player : list) {
			buf.writeString(player.getGameProfile().getName(), 16);
			ClanRecord clan = NpcService.isNpc(player) ? null : ClanService.of(player);
			buf.writeString(clan == null || clan.name == null ? "" : clan.name, 16);
		}
		return buf;
	}
}
