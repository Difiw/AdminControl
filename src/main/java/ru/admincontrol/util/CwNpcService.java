package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.admincontrol.net.ModPackets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Skin loading and client synchronization for the CW Expedition NPC commands. */
public final class CwNpcService {
	private static final long MAX_PNG_BYTES = 2L * 1024 * 1024;
	private static final String MISSING_FOLDER = "[CW NPC] Не удалось создать папку mods/Skins/.";
	private static final String EMPTY = "[CW NPC] В папке отсутствуют PNG-скины.";
	private static final String INVALID = "[CW NPC] Некорректный PNG-скин. Требуется формат 64×64.";
	private static final List<String> SKINS = new ArrayList<>();
	private static String scanError;
	private static volatile String lastCreatedId;

	private CwNpcService() {}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve("mods").resolve("Skins").toAbsolutePath().normalize();
	}

	public static synchronized String reload() {
		SKINS.clear();
		scanError = null;
		Path dir = folder();
		try { Files.createDirectories(dir); }
		catch (IOException e) { scanError = MISSING_FOLDER; return scanError; }
		try (var files = Files.list(dir)) {
			files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
					.map(p -> p.getFileName().toString().substring(0, p.getFileName().toString().length() - 4))
					.filter(s -> s.matches("[\\p{L}\\p{N}_-]{1,32}")).sorted(String.CASE_INSENSITIVE_ORDER).forEach(SKINS::add);
		} catch (IOException e) { scanError = "[CW NPC] Не удалось прочитать папку со скинами."; return scanError; }
		if (SKINS.isEmpty()) { scanError = EMPTY; return scanError; }
		return null;
	}

	public static synchronized List<String> skins() {
		if (scanError == null && SKINS.isEmpty()) reload();
		return List.copyOf(SKINS);
	}

	public static synchronized String scanError() { return scanError; }
	public static byte[] skinBytes(String id){try{String error=validate(id);return error==null?Files.readAllBytes(file(id)):new byte[0];}catch(Exception e){return new byte[0];}}

	public static String validate(String id) {
		if (id != null && (id.toLowerCase().endsWith(".jpg") || id.toLowerCase().endsWith(".jpeg")))
			return "[CW NPC] JPEG не поддерживается. Нужен Minecraft skin PNG 64×64.";
		try { Files.createDirectories(folder()); } catch (IOException e) { return MISSING_FOLDER; }
		Path file = file(id);
		if (file == null || !Files.isRegularFile(file)) return "[CW NPC] Скин не найден: " + id + ".png";
		try {
			long size = Files.size(file);
			if (size <= 0 || size > MAX_PNG_BYTES) return INVALID;
			byte[] bytes = Files.readAllBytes(file);
			if (bytes.length < 24 || bytes[0] != (byte) 0x89 || bytes[1] != 'P' || bytes[2] != 'N'
					|| bytes[3] != 'G' || bytes[4] != 13 || bytes[5] != 10 || bytes[6] != 26 || bytes[7] != 10
					|| readInt(bytes, 16) != 64 || readInt(bytes, 20) != 64) return INVALID;
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			return image != null && image.getWidth() == 64 && image.getHeight() == 64 ? null : INVALID;
		} catch (IOException | RuntimeException e) { return INVALID; }
	}

	private static int readInt(byte[] bytes, int offset) {
		return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
				| (bytes[offset + 2] & 255) << 8 | (bytes[offset + 3] & 255);
	}

	public static String create(ServerPlayerEntity admin, String skinId) {
		if (skins().isEmpty()) return scanError == null ? EMPTY : scanError;
		String error = validate(skinId);
		if (error != null) return error;
		if (!skins().contains(skinId)) return "[CW NPC] Скин не найден: " + skinId + ".png";
		String name = "CW" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
		String result = NpcService.spawnSkinned(admin, skinId, name);
		if (result == null) {
			NpcPlayerEntity npc = NpcService.find(admin.getServer(), name);
			lastCreatedId = npc == null ? null : npc.getUuid().toString();
		}
		return result;
	}

	public static String lastCreatedId() { return lastCreatedId; }

	/** Only NPCs registered by the CW story constructor have a logical story id. */
	public static boolean isStoryNpc(ServerPlayerEntity player) {
		if (!(player instanceof NpcPlayerEntity)) return false;
		NpcStore.Saved saved = NpcStore.get(player.getGameProfile().getName());
		return saved != null && saved.logicalId != null && !saved.logicalId.isBlank();
	}

	public static String info(String id) {
		if (skins().isEmpty()) return scanError == null ? EMPTY : scanError;
		String error = validate(id);
		if (error != null) return error;
		return "Скин «" + id + "»: PNG 64×64, путь " + folder().resolve(id + ".png");
	}

	public static void syncSkinToAll(MinecraftServer server, UUID uuid, String skinId) {
		String error = validate(skinId);
		if (error != null) return;
		try {
			byte[] bytes = Files.readAllBytes(file(skinId));
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				send(player, uuid, skinId, bytes);
			}
		} catch (Exception e) { ActionLog.error("Не удалось синхронизировать скин NPC " + skinId, e); }
	}

	public static void syncExisting(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return;
		for (NpcStore.Saved saved : NpcStore.all()) {
			if (saved == null || saved.skinId == null || saved.skinId.isBlank()) continue;
			NpcPlayerEntity npc = NpcService.find(player.getServer(), saved.nick);
			if (npc != null) sendSkin(player, npc.getUuid(), saved.skinId);
		}
	}

	public static int clear(ServerWorld world) {
		if (world == null) return 0;
		int removed = 0;
		for (NpcStore.Saved saved : NpcStore.all()) {
			if (saved == null || saved.skinId == null || saved.skinId.isBlank()) continue;
			NpcPlayerEntity npc = NpcService.find(world.getServer(), saved.nick);
			if (npc != null && npc.getServerWorld() == world) {
				NpcService.remove(world.getServer(), npc.getUuid().toString());
				removed++;
			}
		}
		return removed;
	}

	public static List<String> ids(MinecraftServer server) {
		List<String> ids = new ArrayList<>();
		for (String nick : NpcService.names(server)) {
			NpcPlayerEntity npc = NpcService.find(server, nick);
			if (npc != null && NpcStore.get(nick) != null && NpcStore.get(nick).skinId != null
					&& !NpcStore.get(nick).skinId.isBlank()) ids.add(npc.getUuid().toString());
		}
		return ids;
	}

	public static void syncAll(MinecraftServer server) {
		for (String nick : NpcService.names(server)) {
			NpcPlayerEntity npc = NpcService.find(server, nick);
			NpcStore.Saved saved = NpcStore.get(nick);
			if (npc != null && saved != null && saved.skinId != null && !saved.skinId.isBlank())
				syncSkinToAll(server, npc.getUuid(), saved.skinId);
		}
	}

	private static void sendSkin(ServerPlayerEntity player, UUID uuid, String skinId) {
		try { byte[] bytes = Files.readAllBytes(file(skinId)); send(player, uuid, skinId, bytes); }
		catch (Exception e) { /* A removed skin must not disrupt player login or world loading. */ }
	}

	private static void send(ServerPlayerEntity player, UUID uuid, String skinId, byte[] bytes) {
		var packet = PacketByteBufs.create();
		packet.writeString(uuid.toString(), 36);
		packet.writeString(skinId, 32);
		packet.writeBoolean(isSlim(bytes));
		packet.writeByteArray(bytes);
		ServerPlayNetworking.send(player, ModPackets.NPC_SKIN, packet);
	}

	private static Path file(String id) {
		if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}")) return null;
		return folder().resolve(id + ".png");
	}

	private static boolean isSlim(byte[] bytes) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			// In a 64x64 skin the slim model's extra arm pixels are transparent in the base layer.
			for (int y = 20; y < 32; y++) if ((image.getRGB(47, y) >>> 24) != 0) return false;
			return true;
		} catch (Exception ignored) {}
		return false;
	}
}
