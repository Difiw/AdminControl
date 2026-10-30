package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.BugReport;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Баг-лист (/buglist): багаюз от античита и ошибки сервера. Хранится в bugs.json, наказаний не выдаёт. */
public final class BugList {
	public static final int MAX = 1000;
	/** Оранжево-коричневый цвет сообщений о баге в чате администрации. */
	public static final int COLOR = 0xC8742E;
	public static final String TYPE_ERROR = "Ошибка сервера";
	/** Одинаковая ошибка сервера в пределах этого срока — повтор той же записи, без нового сообщения в чат. */
	private static final long ERROR_MERGE_MS = 6L * 60 * 60 * 1000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
	private static final Object LOCK = new Object();
	private static List<BugReport> entries;
	private static Path loadedFrom;
	private static boolean dirty;
	private static volatile MinecraftServer server;

	private BugList() {
	}

	public static void attach(MinecraftServer s) {
		server = s;
	}

	public static void detach() {
		saveIfDirty();
		server = null;
		synchronized (LOCK) {
			entries = null;
			loadedFrom = null;
		}
	}

	/** Багаюз игрока на его месте. */
	public static void report(ServerPlayerEntity player, String type, String description) {
		if (player == null) {
			return;
		}
		report(player, type, description, player.getWorld().getRegistryKey().getValue().toString(),
				player.getBlockX(), player.getBlockY(), player.getBlockZ());
	}

	public static void report(ServerPlayerEntity player, String type, String description, String dimension, int x, int y, int z) {
		BugReport r = new BugReport();
		r.type = type;
		r.nick = player == null ? "" : player.getGameProfile().getName();
		r.uuid = player == null ? "" : player.getUuidAsString();
		r.dimension = dimension == null ? "" : dimension;
		r.x = x;
		r.y = y;
		r.z = z;
		r.hasPos = true;
		r.description = clip(description, 1000);
		if (!add(r)) {
			return;
		}
		if (!r.nick.isEmpty()) {
			ActivityLog.player(r.nick, "Баг-лист: " + type + " — " + r.description, false);
		}
		notifyAdmins(r);
	}

	/** Ошибка из логов сервера (может прийти из любого потока). */
	public static void serverError(String signature, String description) {
		long now = System.currentTimeMillis();
		BugReport fresh = null;
		synchronized (LOCK) {
			List<BugReport> list = load();
			if (list == null) {
				return;
			}
			for (int i = list.size() - 1; i >= 0; i--) {
				BugReport old = list.get(i);
				if (TYPE_ERROR.equals(old.type) && signature.equals(old.signature) && now - old.lastAt < ERROR_MERGE_MS) {
					old.count++;
					old.lastAt = now;
					dirty = true;
					return;
				}
			}
			fresh = new BugReport();
			fresh.type = TYPE_ERROR;
			fresh.signature = signature;
			fresh.description = clip(description, 1000);
		}
		if (add(fresh)) {
			notifyAdmins(fresh);
		}
	}

	private static boolean add(BugReport r) {
		synchronized (LOCK) {
			List<BugReport> list = load();
			if (list == null) {
				return false;
			}
			long now = System.currentTimeMillis();
			long id = 1;
			for (BugReport old : list) {
				id = Math.max(id, old.id + 1);
			}
			r.id = id;
			r.at = now;
			r.lastAt = now;
			list.add(r);
			while (list.size() > MAX) {
				list.remove(0);
			}
			dirty = true;
		}
		saveIfDirty();
		return true;
	}

	private static void notifyAdmins(BugReport r) {
		MinecraftServer s = server;
		if (s == null) {
			return;
		}
		StringBuilder line = new StringBuilder("[Баг-лист] ").append(SHORT.format(Instant.ofEpochMilli(r.at))).append(' ');
		if (!r.nick.isEmpty()) {
			line.append(r.nick).append(" — ");
		}
		line.append(r.type).append(": ").append(clip(r.description, 180));
		if (r.hasPos) {
			line.append(" · ").append(r.dimension).append(' ').append(r.x).append(' ').append(r.y).append(' ').append(r.z);
		}
		line.append(" · /buglist");
		Text text = Chat.color(line.toString(), COLOR);
		s.execute(() -> AdminNotify.toAdmins(s, null, text));
	}

	/** Новые сверху. */
	public static List<BugReport> list() {
		synchronized (LOCK) {
			List<BugReport> list = load();
			List<BugReport> out = list == null ? new ArrayList<>() : new ArrayList<>(list);
			Collections.reverse(out);
			return out;
		}
	}

	public static BugReport find(long id) {
		synchronized (LOCK) {
			List<BugReport> list = load();
			if (list != null) {
				for (BugReport r : list) {
					if (r.id == id) {
						return r;
					}
				}
			}
			return null;
		}
	}

	public static boolean delete(long id) {
		boolean removed;
		synchronized (LOCK) {
			List<BugReport> list = load();
			removed = list != null && list.removeIf(r -> r.id == id);
			dirty |= removed;
		}
		saveIfDirty();
		return removed;
	}

	public static int clear() {
		int n;
		synchronized (LOCK) {
			List<BugReport> list = load();
			n = list == null ? 0 : list.size();
			if (n > 0) {
				list.clear();
				dirty = true;
			}
		}
		saveIfDirty();
		return n;
	}

	public static void saveIfDirty() {
		String json;
		Path file;
		synchronized (LOCK) {
			if (!dirty || entries == null || loadedFrom == null) {
				return;
			}
			json = GSON.toJson(entries);
			file = loadedFrom;
			dirty = false;
		}
		ServerErrorWatch.quietly(() -> {
			try {
				Files.createDirectories(file.getParent());
				Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(tmp, json, StandardCharsets.UTF_8);
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				AdminControlMod.LOGGER.warn("Не удалось сохранить баг-лист {}", file, e);
			}
		});
	}

	private static List<BugReport> load() {
		DataManager data = DataManager.get();
		if (data == null) {
			return null;
		}
		Path file = data.getConfigDir().resolve("bugs.json");
		if (entries != null && file.equals(loadedFrom)) {
			return entries;
		}
		List<BugReport> list = new ArrayList<>();
		if (Files.isRegularFile(file)) {
			try {
				BugReport[] read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), BugReport[].class);
				if (read != null) {
					for (BugReport r : read) {
						if (r != null) {
							list.add(r);
						}
					}
				}
			} catch (Exception e) {
				AdminControlMod.LOGGER.warn("Не удалось прочитать баг-лист {} — файл переименован в bugs.json.broken", file, e);
				try {
					Files.move(file, file.resolveSibling("bugs.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {
				}
			}
		}
		entries = list;
		loadedFrom = file;
		dirty = false;
		return entries;
	}

	public static String time(long at) {
		return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at));
	}

	private static String clip(String s, int max) {
		if (s == null) {
			return "";
		}
		String t = s.replace('\n', ' ').replace('\r', ' ').trim();
		return t.length() > max ? t.substring(0, max) + "…" : t;
	}
}
