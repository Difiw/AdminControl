package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AccountReset;
import ru.admincontrol.util.CweNotice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent Common World: Expedition name blacklist. One list for both kinds of entries (ACCOUNT = existing account,
 * RESERVED = name held before the account exists). It is independent from the vanilla ban list and from the punishment
 * system. Names are compared case-insensitively; entries of existing accounts also carry the UUID, so renaming does not
 * bypass them.
 *
 * <p>Cleanup of an existing account's data runs only after the session has ended, only on data addressed by the exact
 * account UUID, and only after a backup was written. If the account cannot be identified or the backup fails, nothing is
 * deleted and the name stays blocked.</p>
 */
public final class CweBlacklist {
	public static final int PAGE_SIZE = 10;
	private static final long CLEANUP_GRACE_TICKS = 40L, CLEANUP_GIVE_UP_TICKS = 1200L;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static final Map<String, Long> WAITING_SINCE = new LinkedHashMap<>();
	private static boolean loaded;

	public static final class Entry {
		public int version = 1;
		public String name;
		public String uuid;
		/** ACCOUNT or RESERVED. */
		public String kind;
		public long addedAt;
		/** Staff-only information; never sent to the blocked player. */
		public String addedBy;
		public boolean cleanupPending;
		/** Set only by the developer command /delaccinfo yes true; cleanup never runs without it. */
		public boolean cleanupApproved;
		public String cleanupResult;
	}

	private CweBlacklist() {}

	public static String key(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
	public static int pages(int size) { return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE); }

	private static Path file() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cwe-blacklist.json"); }

	private static synchronized void load() {
		if (loaded) return;
		Path path = file();
		if (path == null) return;
		loaded = true;
		if (!Files.isRegularFile(path)) return;
		try {
			Map<String, Entry> read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), new TypeToken<Map<String, Entry>>() {}.getType());
			if (read != null) read.forEach((k, v) -> { if (v != null && v.name != null) ENTRIES.put(key(v.name), v); });
		} catch (Exception e) { AdminControlMod.LOGGER.error("[CWE] blacklist read failed", e); }
	}

	private static synchronized boolean save() {
		Path path = file();
		if (path == null) return false;
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(ENTRIES), StandardCharsets.UTF_8);
			try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
			return true;
		} catch (Exception e) { AdminControlMod.LOGGER.error("[CWE] blacklist write failed", e); return false; }
	}

	public static synchronized List<Entry> list() {
		load();
		List<Entry> out = new ArrayList<>(ENTRIES.values());
		out.sort(Comparator.comparing(e -> key(e.name)));
		return out;
	}

	public static synchronized Entry find(String name) { load(); return ENTRIES.get(key(name)); }

	/** Entry matching a joining profile by name or by account UUID. */
	public static synchronized Entry match(String name, UUID uuid) {
		load();
		Entry byName = ENTRIES.get(key(name));
		if (byName != null) return byName;
		if (uuid != null) for (Entry e : ENTRIES.values()) if (e.uuid != null && e.uuid.equalsIgnoreCase(uuid.toString())) return e;
		return null;
	}

	/** Disconnect text for a blocked join, or null if the profile may join. Called before any profile is created. */
	public static Text joinDenial(String name, UUID uuid) {
		Entry e = match(name, uuid);
		if (e == null) return null;
		AdminControlMod.LOGGER.info("[CWE] join refused for blacklisted name {}", e.name);
		return CweNotice.text(notice(e));
	}

	private static CweNotice.Data notice(Entry e) {
		boolean account = "ACCOUNT".equals(e.kind) || existingRecord(e.name) != null;
		return new CweNotice.Data(account ? CweNotice.Kind.ACCOUNT : CweNotice.Kind.RESERVED, e.name, e.addedAt);
	}

	private static PlayerRecord existingRecord(String name) {
		DataManager data = DataManager.get();
		if (data == null) return null;
		PlayerRecord r = data.findByNick(name).orElse(null);
		return r == null || r.nps ? null : r;
	}

	/** /bl add: blocks an existing account, disconnects it and schedules the safe cleanup. Returns an error text or null. */
	public static synchronized String add(MinecraftServer server, String actor, String nick) {
		if (!CweNotice.validName(nick)) return "Некорректное имя: допустимы латиница, цифры и _ (до 16 символов).";
		load();
		if (ENTRIES.containsKey(key(nick))) return "Имя уже в чёрном списке.";
		PlayerRecord record = existingRecord(nick);
		if (record == null) return "Аккаунт не найден. Для несуществующего имени используйте /bl reserve " + nick + ".";
		Entry e = new Entry();
		e.name = record.nickname == null || record.nickname.isBlank() ? nick : record.nickname;
		e.uuid = record.uuid;
		e.kind = "ACCOUNT";
		e.addedAt = System.currentTimeMillis();
		e.addedBy = actor;
		e.cleanupPending = true;
		ENTRIES.put(key(e.name), e);
		if (!save()) { ENTRIES.remove(key(e.name)); return "Не удалось сохранить список. Ничего не изменено."; }
		log(actor, "bl add", e.name, "listed uuid=" + e.uuid);
		kick(server, e);
		return null;
	}

	/** /bl reserve: holds a name that has no account yet. */
	public static synchronized String reserve(MinecraftServer server, String actor, String nick) {
		if (!CweNotice.validName(nick)) return "Некорректное имя: допустимы латиница, цифры и _ (до 16 символов).";
		load();
		if (ENTRIES.containsKey(key(nick))) return "Имя уже в чёрном списке.";
		if (existingRecord(nick) != null || (server != null && CommandUtil.findOnline(server, nick).isPresent()))
			return "Аккаунт с таким именем уже существует. Используйте /bl add " + nick + ".";
		Entry e = new Entry();
		e.name = nick; e.kind = "RESERVED"; e.addedAt = System.currentTimeMillis(); e.addedBy = actor;
		ENTRIES.put(key(nick), e);
		if (!save()) { ENTRIES.remove(key(nick)); return "Не удалось сохранить список. Ничего не изменено."; }
		log(actor, "bl reserve", e.name, "reserved");
		return null;
	}

	/** /bl del and /bl free: remove an entry whatever its kind (the account state may have changed since). Data is never restored. */
	public static synchronized String remove(String actor, String nick, String command) {
		load();
		Entry e = ENTRIES.remove(key(nick));
		if (e == null) return "Имени нет в чёрном списке.";
		if (!save()) { ENTRIES.put(key(nick), e); return "Не удалось сохранить список. Ничего не изменено."; }
		WAITING_SINCE.remove(key(nick));
		log(actor, command, e.name, "removed kind=" + e.kind);
		return null;
	}

	/** /delaccinfo yes true: lets the cleanup run for every account entry that is pending right now. Returns how many were approved. */
	public static synchronized int approveCleanup(String actor) {
		load();
		int n = 0;
		for (Entry e : ENTRIES.values()) if (e.cleanupPending && !e.cleanupApproved) { e.cleanupApproved = true; n++; }
		if (n > 0 && !save()) { for (Entry e : ENTRIES.values()) if (e.cleanupPending) e.cleanupApproved = false; return -1; }
		log(actor, "delaccinfo", "pending=" + n, "approved");
		return n;
	}

	private static void kick(MinecraftServer server, Entry e) {
		if (server == null) return;
		CommandUtil.findOnline(server, e.name).ifPresent(p -> p.networkHandler.disconnect(CweNotice.text(notice(e))));
	}

	/** Server tick: resumes pending cleanups once the blocked player is really gone (also after a restart). */
	public static synchronized void tick(MinecraftServer server) {
		if (server == null || server.getTicks() % 20 != 0) return;
		load();
		for (Entry e : new ArrayList<>(ENTRIES.values())) {
			if (!e.cleanupPending || !e.cleanupApproved) continue;
			String k = key(e.name);
			ServerPlayerEntity online = CommandUtil.findOnline(server, e.name).orElse(null);
			long now = server.getTicks();
			if (online != null) {
				long since = WAITING_SINCE.computeIfAbsent(k, x -> now);
				if (now - since > CLEANUP_GIVE_UP_TICKS) { fail(e, "игрок не отключился вовремя; очистка отложена, имя заблокировано"); }
				else if (now - since > CLEANUP_GRACE_TICKS) online.networkHandler.disconnect(CweNotice.text(notice(e)));
				continue;
			}
			if (WAITING_SINCE.containsKey(k) && now - WAITING_SINCE.get(k) < CLEANUP_GRACE_TICKS) continue;
			WAITING_SINCE.remove(k);
			cleanup(server, e);
		}
	}

	private static void fail(Entry e, String message) {
		e.cleanupPending = false;
		e.cleanupResult = "FAILED: " + message;
		save();
		log("system", "bl cleanup", e.name, e.cleanupResult);
	}

	private static void cleanup(MinecraftServer server, Entry e) {
		UUID uuid;
		try { uuid = e.uuid == null ? null : UUID.fromString(e.uuid); } catch (IllegalArgumentException ex) { uuid = null; }
		PlayerRecord record = uuid == null ? null : DataManager.get().findByUuid(uuid).orElse(null);
		if (uuid == null || record == null || !uuid.toString().equalsIgnoreCase(record.uuid == null ? "" : record.uuid)) {
			fail(e, "владелец данных не определён однозначно; ничего не удалено");
			return;
		}
		try {
			Path dir = backup(server, uuid, record);
			String error = AccountReset.apply(server, record, "чёрный список CW Expedition");
			if (error != null) { fail(e, "сброс аккаунта: " + error + "; резервная копия " + dir.getFileName()); return; }
			CwStoryWorldService.resetOffline(uuid);
			e.cleanupPending = false;
			e.cleanupResult = "OK backup=" + dir.getFileName();
			save();
			log("system", "bl cleanup", e.name, e.cleanupResult);
		} catch (Exception ex) {
			AdminControlMod.LOGGER.error("[CWE] cleanup failed for {}", e.name, ex);
			fail(e, "резервная копия или очистка не удались: " + ex.getClass().getSimpleName() + "; данные не тронуты");
		}
	}

	/** Copies exactly the files addressed by this UUID plus a JSON dump of the account and story data. */
	private static Path backup(MinecraftServer server, UUID uuid, PlayerRecord record) throws Exception {
		String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(java.time.Instant.now());
		Path dir = DataManager.get().getConfigDir().resolve("cwe-backups").resolve(uuid + "-" + stamp);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("account-record.json"), GSON.toJson(record), StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("story-data.json"), CwStoryWorldService.backupJson(uuid), StandardCharsets.UTF_8);
		copyIfExists(server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuid + ".dat"), dir);
		copyIfExists(server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuid + ".dat_old"), dir);
		copyIfExists(server.getSavePath(WorldSavePath.STATS).resolve(uuid + ".json"), dir);
		copyIfExists(server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(uuid + ".json"), dir.resolve("advancements.json"));
		return dir;
	}

	private static void copyIfExists(Path source, Path target) throws Exception {
		if (!Files.isRegularFile(source)) return;
		Path to = Files.isDirectory(target) ? target.resolve(source.getFileName()) : target;
		Files.copy(source, to, StandardCopyOption.REPLACE_EXISTING);
	}

	public static void log(String actor, String command, String target, String result) {
		AdminControlMod.LOGGER.info("[CWE] {} {} {} -> {}", actor, command, target, result);
		CwAudit.writeSystem(actor, command, target, result);
	}
}
