package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.FileCheckFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Проверка файлов игрока: админ просматривает папки (resourcepacks, mods, shaderpacks, config),
 * ищет по ним, открывает архивы/текстовые файлы и скачивает файлы на сервер.
 */
public final class PlayerFileService {
	public static final int CHUNK = 24_000;
	public static final int TEXT_CHUNK = 8_000;
	public static final List<String> FOLDERS = List.of("resourcepacks", "mods", "shaderpacks", "config");

	public static final int OP_LIST = 0;
	/** Поиск по имени внутри открытой папки/архива. */
	public static final int OP_SEARCH = 1;
	public static final int OP_VIEW = 2;
	public static final int OP_DOWNLOAD = 3;
	/** Поиск текста внутри файлов открытой папки/архива. */
	public static final int OP_GREP = 4;

	public static final int KIND_ENTRIES = 0;
	public static final int KIND_TEXT = 1;
	public static final int KIND_ERROR = 2;

	private static final int MAX_ENTRIES = 1500;
	private static final int MAX_TEXT = 100_000;
	private static final long MAX_FILE = 64L * 1024 * 1024;
	private static final long PENDING_TTL = 120_000L;

	/** Кого проверяет админ (uuid админа → uuid игрока). */
	private static final Map<UUID, UUID> VIEWING = new ConcurrentHashMap<>();
	private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, Download> DOWNLOADS = new ConcurrentHashMap<>();
	private static int nextId = 1;

	private PlayerFileService() {
	}

	private static final class Pending {
		UUID admin;
		UUID target;
		int token;
		int op;
		String folder;
		String path;
		String query;
		int kind = KIND_ENTRIES;
		final List<PlayerFileEntry> entries = new ArrayList<>();
		final StringBuilder text = new StringBuilder();
		String error = "";
		boolean truncated;
		long createdAt = System.currentTimeMillis();
	}

	private static final class Download {
		UUID adminUuid;
		String adminNick;
		String targetNick;
		String label;
		long written;
		int nextChunk;
		int expectedTotal = -1;
		long createdAt = System.currentTimeMillis();
		MessageDigest digest;
	}

	public static boolean allowedFolder(String folder) {
		return folder != null && FOLDERS.contains(folder);
	}

	/** /filecheck: открыть окно проверки файлов игрока. */
	public static String open(ServerPlayerEntity admin, ServerPlayerEntity target) {
		if (admin == null || target == null) {
			return "Игрок не найден.";
		}
		if (!ServerPlayNetworking.canSend(target, ModPackets.FILES_REQUEST)) {
			return "У игрока «" + target.getGameProfile().getName()
					+ "» не установлен клиент Admin Control — проверить файлы нельзя.";
		}
		VIEWING.put(admin.getUuid(), target.getUuid());
		ScreenNav.openRoot(admin, new FileCheckFactory(target.getUuid(), target.getGameProfile().getName()));
		return null;
	}

	/** Действие из окна админа: открыть папку, поиск, просмотр файла, скачивание. */
	public static void onAdmin(ServerPlayerEntity admin, int token, int op, String folder, String path, String query) {
		if (admin == null || DataManager.get() == null) {
			return;
		}
		if (DataManager.get().getAdminLevel(admin) < AdminRanks.ADMIN) {
			reply(admin, token, op, folder, path, query, KIND_ERROR, List.of(), "", "Недостаточно прав.", false);
			return;
		}
		UUID targetUuid = VIEWING.get(admin.getUuid());
		MinecraftServer server = admin.getServer();
		ServerPlayerEntity target = targetUuid == null || server == null ? null
				: server.getPlayerManager().getPlayer(targetUuid);
		if (target == null) {
			reply(admin, token, op, folder, path, query, KIND_ERROR, List.of(), "", "Игрок вышел с сервера.", false);
			return;
		}
		if (!allowedFolder(folder) || op < OP_LIST || op > OP_GREP) {
			return;
		}
		String cleanPath = path == null ? "" : path;
		if (op == OP_DOWNLOAD) {
			pull(admin, target, folder, cleanPath);
			return;
		}
		prune();
		Pending p = new Pending();
		p.admin = admin.getUuid();
		p.target = target.getUuid();
		p.token = token;
		p.op = op;
		p.folder = folder;
		p.path = cleanPath;
		p.query = query == null ? "" : query;
		int id;
		synchronized (PlayerFileService.class) {
			id = nextId++;
		}
		PENDING.put(id, p);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(id);
		buf.writeByte(op);
		buf.writeString(folder, 32);
		buf.writeString(cleanPath, PlayerFileEntry.PATH_MAX);
		buf.writeString(p.query, 64);
		ServerPlayNetworking.send(target, ModPackets.FILES_REQUEST, buf);
	}

	private static void prune() {
		long now = System.currentTimeMillis();
		PENDING.values().removeIf(p -> now - p.createdAt > PENDING_TTL);
	}

	/** Ответ клиента игрока (по частям). */
	public static void onReply(ServerPlayerEntity target, int id, boolean last, int kind,
			List<PlayerFileEntry> entries, String text, boolean truncated) {
		Pending p = PENDING.get(id);
		if (p == null || target == null || !target.getUuid().equals(p.target)) {
			return;
		}
		p.kind = kind;
		p.truncated |= truncated;
		if (kind == KIND_ENTRIES) {
			for (PlayerFileEntry e : entries) {
				if (p.entries.size() >= MAX_ENTRIES) {
					p.truncated = true;
					break;
				}
				p.entries.add(e);
			}
		} else if (kind == KIND_TEXT) {
			if (p.text.length() + text.length() > MAX_TEXT) {
				p.text.append(text, 0, Math.max(0, MAX_TEXT - p.text.length()));
				p.truncated = true;
			} else {
				p.text.append(text);
			}
		} else {
			p.error = text;
		}
		if (!last) {
			return;
		}
		PENDING.remove(id);
		MinecraftServer server = target.getServer();
		ServerPlayerEntity admin = server == null ? null : server.getPlayerManager().getPlayer(p.admin);
		if (admin != null) {
			reply(admin, p.token, p.op, p.folder, p.path, p.query, p.kind, p.entries, p.text.toString(), p.error, p.truncated);
		}
	}

	private static void reply(ServerPlayerEntity admin, int token, int op, String folder, String path, String query,
			int kind, List<PlayerFileEntry> entries, String text, String error, boolean truncated) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(token);
		buf.writeByte(op);
		buf.writeString(folder == null ? "" : folder, 32);
		buf.writeString(path == null ? "" : path, PlayerFileEntry.PATH_MAX);
		buf.writeString(query == null ? "" : query, 64);
		buf.writeByte(kind);
		buf.writeBoolean(truncated);
		if (kind == KIND_ENTRIES) {
			buf.writeVarInt(entries.size());
			for (PlayerFileEntry e : entries) {
				e.write(buf);
			}
		} else if (kind == KIND_TEXT) {
			buf.writeString(text, MAX_TEXT);
		} else {
			buf.writeString(error == null ? "" : error, 300);
		}
		ServerPlayNetworking.send(admin, ModPackets.FILES_VIEW, buf);
	}

	private static void pull(ServerPlayerEntity admin, ServerPlayerEntity target, String folder, String path) {
		if (path.isBlank() || !allowedFolder(folder) || unsafePath(path)) {
			Chat.send(admin, Chat.red("Недопустимый путь файла."));
			return;
		}
		if (!ServerPlayNetworking.canSend(admin, ModPackets.FILE_SAVE)) {
			Chat.send(admin, Chat.red("Обновите клиент Admin Control — скачивание идёт к вам на компьютер."));
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(folder, 32);
		buf.writeString(path, PlayerFileEntry.PATH_MAX);
		ServerPlayNetworking.send(target, ModPackets.FILE_PULL, buf);
		Download d = new Download();
		d.adminUuid = admin.getUuid();
		d.adminNick = admin.getGameProfile().getName();
		d.targetNick = target.getGameProfile().getName();
		d.label = folder + "/" + path;
		try { d.digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException ignored) {}
		DOWNLOADS.put(key(target.getUuid(), folder, path), d);
		Chat.send(admin, Chat.gold("Скачиваю «" + d.label + "» к вам на компьютер…"));
	}

	/** Часть файла от клиента игрока — сразу пересылается админу, на сервере не сохраняется. */
	public static void onData(ServerPlayerEntity target, String folder, String path, int chunk, int total, byte[] data) {
		if (target == null || DataManager.get() == null) {
			return;
		}
		String k = key(target.getUuid(), folder, path);
		Download d = DOWNLOADS.get(k);
		if (d == null) {
			return;
		}
		MinecraftServer server = target.getServer();
		ServerPlayerEntity admin = server == null ? null : server.getPlayerManager().getPlayer(d.adminUuid);
		if (admin == null) {
			DOWNLOADS.remove(k);
			return;
		}
		if (!allowedFolder(folder) || unsafePath(path) || System.currentTimeMillis() - d.createdAt > PENDING_TTL) {
			fail(d, admin, k, folder, path, "запрос просрочен или путь недопустим");
			return;
		}
		if (total < 0) {
			fail(d, admin, k, folder, path, "файл недоступен или больше 64 МБ");
			return;
		}
		int maxChunks = (int) ((MAX_FILE + CHUNK - 1L) / CHUNK);
		if (total < 1 || total > maxChunks || chunk < 0 || chunk >= total || data == null || data.length > CHUNK) {
			fail(d, admin, k, folder, path, "неверный размер/номер части");
			return;
		}
		if (d.expectedTotal < 0) d.expectedTotal = total;
		if (d.expectedTotal != total) {
			fail(d, admin, k, folder, path, "количество частей изменилось в процессе");
			return;
		}
		if (chunk != d.nextChunk) {
			fail(d, admin, k, folder, path, "части пришли не по порядку");
			return;
		}
		d.written += data.length;
		if (d.digest != null) d.digest.update(data);
		if (d.written > MAX_FILE) {
			fail(d, admin, k, folder, path, "файл слишком большой");
			return;
		}
		forward(admin, d.targetNick, folder, path, chunk, total, data, "");
		d.nextChunk++;
		if (chunk + 1 >= total) {
			DOWNLOADS.remove(k);
			String hash = d.digest == null ? "n/a" : java.util.HexFormat.of().formatHex(d.digest.digest());
			ActivityLog.admin(d.adminNick, "Скачал себе файл игрока " + d.targetNick + " для ручной проверки: " + d.label
					+ " (" + human(d.written) + ", SHA-256 " + hash + ", источник — недоверенный клиент)");
			AdminNotify.toAdmins(server, admin, Chat.gold("[Проверка файлов] " + d.adminNick
					+ " скачал файл игрока " + d.targetNick + ": " + d.label));
		}
	}

	private static void forward(ServerPlayerEntity admin, String targetNick, String folder, String path, int chunk,
			int total, byte[] data, String error) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(targetNick, 32);
		buf.writeString(folder, 32);
		buf.writeString(path, PlayerFileEntry.PATH_MAX);
		buf.writeVarInt(chunk);
		buf.writeVarInt(total);
		buf.writeByteArray(data);
		buf.writeString(error, 200);
		ServerPlayNetworking.send(admin, ModPackets.FILE_SAVE, buf);
	}

	private static void fail(Download d, ServerPlayerEntity admin, String k, String folder, String path, String why) {
		DOWNLOADS.remove(k);
		forward(admin, d.targetNick, folder, path, 0, -1, new byte[0], why);
	}

	private static String key(UUID target, String folder, String path) {
		return target + "\0" + folder + "/" + path;
	}

	private static boolean unsafePath(String raw) {
		if (raw == null || raw.length() > PlayerFileEntry.PATH_MAX) return true;
		String p = raw.replace('\\', '/');
		return p.startsWith("/") || p.matches("^[A-Za-z]:.*") || p.indexOf('\0') >= 0
				|| java.util.Arrays.stream(p.split("/")).anyMatch(".."::equals);
	}

	private static final Set<String> SKIP = Set.of("", ".", "..");

	/** Путь внутри папки без выхода наружу ("!/" архива превращается в обычную папку). */
	public static String relative(String raw) {
		String p = raw == null ? "" : raw.replace("!/", "/").replace('\\', '/');
		List<String> parts = new ArrayList<>();
		for (String part : p.split("/")) {
			if (SKIP.contains(part.trim())) {
				continue;
			}
			parts.add(safe(part));
		}
		return parts.isEmpty() ? "file" : String.join("/", parts);
	}

	public static String safe(String raw) {
		String s = raw == null ? "" : raw.replaceAll("[^A-Za-zА-Яа-яЁё0-9._\\- ]", "_").trim();
		if (s.isBlank() || SKIP.contains(s)) {
			return "file";
		}
		return s;
	}

	public static String human(long bytes) {
		if (bytes < 1024) {
			return bytes + " Б";
		}
		if (bytes < 1024 * 1024) {
			return String.format(Locale.ROOT, "%.1f КБ", bytes / 1024.0);
		}
		return String.format(Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024.0));
	}
}
