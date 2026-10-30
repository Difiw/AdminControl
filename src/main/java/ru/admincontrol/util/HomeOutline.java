package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HomeOutline {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

	private HomeOutline() {
	}

	public static boolean showing(UUID id) {
		return id != null && ON.contains(id);
	}

	public static void tick(MinecraftServer server) {
		if (server == null || ON.isEmpty()) {
			return;
		}
		if (server.getTicks() % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player != null && ON.contains(player.getUuid())) {
				sync(player, currentBox(player));
			}
		}
	}

	public static void toggle(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (ON.contains(player.getUuid())) {
			ON.remove(player.getUuid());
			sync(player, null);
			Chat.send(player, Chat.yellow("Приват скрыт."));
			return;
		}
		HomeProtection.Box box = currentBox(player);
		if (box == null) {
			Chat.send(player, Chat.red("У вас нет привата. Блоки привата продаются в /shop."));
			sync(player, null);
			return;
		}
		ON.add(player.getUuid());
		sync(player, box);
		Chat.send(player, Chat.green("Приват показан по граням."));
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			ON.remove(player.getUuid());
			LAST.remove(player.getUuid());
		}
	}

	private static HomeProtection.Box currentBox(ServerPlayerEntity player) {
		return PrivateService.boxOf(player);
	}

	private static void sync(ServerPlayerEntity player, HomeProtection.Box box) {
		String key = box == null ? "-" : box.minX + "," + box.minY + "," + box.minZ + ","
				+ box.maxX + "," + box.maxY + "," + box.maxZ + "," + box.dimension;
		if (key.equals(LAST.get(player.getUuid()))) {
			return;
		}
		LAST.put(player.getUuid(), key);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(box != null);
		if (box != null) {
			buf.writeVarInt(box.minX);
			buf.writeVarInt(box.minY);
			buf.writeVarInt(box.minZ);
			buf.writeVarInt(box.maxX);
			buf.writeVarInt(box.maxY);
			buf.writeVarInt(box.maxZ);
			buf.writeString(box.dimension == null ? "" : box.dimension, 200);
		}
		ServerPlayNetworking.send(player, ModPackets.HOME_OUTLINE, buf);
	}
}
