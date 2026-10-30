package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable per-player transition state machine of CW Expedition (the same role the inventory vault plays for Events,
 * but with an explicit phase and a journal).
 *
 * <pre>
 *   NORMAL -> ENTERING_BUILD -> BUILD      NORMAL -> ENTERING_STORY -> STORY
 *   BUILD  -> EXITING -> NORMAL            STORY  -> EXITING        -> NORMAL
 * </pre>
 *
 * Every phase is written to {@code cw-player-state/<uuid>.json} (checksummed, fsynced, with a backup) BEFORE the world is
 * touched, and every step is appended to {@code cw-journal/transitions.jsonl}. A phase other than NORMAL / BUILD / STORY
 * found after a restart means the previous transition was interrupted and must be finished or rolled back by the
 * recovery code. A transition id is carried through log lines and error messages so a failure can be traced.
 * The class has no Minecraft dependencies so the machine can be unit-tested.
 */
public final class CwPlayerState {
	public enum Mode { NORMAL, ENTERING_BUILD, BUILD, ENTERING_STORY, STORY, EXITING }
	public enum Kind { ENTER_BUILD, EXIT_BUILD, ENTER_STORY, EXIT_STORY }

	public static final class Record {
		public int schemaVersion = 1;
		public String player;
		public Mode mode = Mode.NORMAL;
		/** The stable mode the player was in when the current transition started / the one it is heading to. */
		public String from, to;
		public String transitionId;
		public long updatedAt;
		public String detail;
	}

	/** A running transition. {@code error} is set instead when it could not be started. */
	public static final class Transition {
		public final String id;
		public final UUID player;
		public final Kind kind;
		/** Stable mode the player returns to when this story transition ends (an administrator may test the story from BUILD). */
		public final Mode returnMode;
		private Transition(String id, UUID player, Kind kind, Mode returnMode) { this.id = id; this.player = player; this.kind = kind; this.returnMode = returnMode; }
	}

	public record Begin(Transition transition, String error) {
		public boolean ok() { return transition != null; }
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<UUID, Transition> IN_FLIGHT = new ConcurrentHashMap<>();
	private static final Map<UUID, Record> CACHE = new ConcurrentHashMap<>();
	private static volatile Path baseDir;

	private CwPlayerState() {}

	/** Used by tests; production resolves the folder from the mod's config directory. */
	public static synchronized void configure(Path dir) { baseDir = dir; CACHE.clear(); IN_FLIGHT.clear(); }

	private static Path base() {
		if (baseDir != null) return baseDir;
		return DataManager.get() == null ? null : DataManager.get().getConfigDir();
	}

	private static Path stateFile(UUID id) { Path b = base(); return b == null ? null : b.resolve("cw-player-state").resolve(id + ".json"); }
	private static Path journalFile() { Path b = base(); return b == null ? null : b.resolve("cw-journal").resolve("transitions.jsonl"); }

	public static Set<UUID> inFlight() { return Set.copyOf(IN_FLIGHT.keySet()); }
	public static boolean busy(UUID player) { return player != null && IN_FLIGHT.containsKey(player); }

	/** Id of the transition currently running for the player (or the last interrupted one), for diagnostics. */
	public static String currentTransition(UUID player) {
		Transition t = IN_FLIGHT.get(player);
		if (t != null) return t.id;
		Record r = get(player);
		return r == null || r.mode == Mode.NORMAL || r.mode == Mode.BUILD || r.mode == Mode.STORY ? null : r.transitionId;
	}

	public static synchronized Record get(UUID player) {
		if (player == null) return null;
		Record cached = CACHE.get(player);
		if (cached != null) return cached;
		Path file = stateFile(player);
		Record loaded = null;
		if (file != null) {
			CwDurable.Loaded read = CwDurable.read(file);
			if (read.present()) {
				try { loaded = GSON.fromJson(read.json(), Record.class); }
				catch (Exception e) { AdminControlMod.LOGGER.error("[CW] player state unreadable player={}", player, e); }
			}
		}
		if (loaded == null) { loaded = new Record(); loaded.player = player.toString(); }
		CACHE.put(player, loaded);
		return loaded;
	}

	public static Mode mode(UUID player) { Record r = get(player); return r == null ? Mode.NORMAL : r.mode; }

	public static boolean interrupted(UUID player) {
		Mode m = mode(player);
		return m == Mode.ENTERING_BUILD || m == Mode.ENTERING_STORY || m == Mode.EXITING;
	}

	/**
	 * Starts a transition. Rejects a second concurrent transition of the same player (double click, logout racing a
	 * command, death during a switch) and entering a mode the player is already in. Exits are always allowed so that an
	 * interrupted state can be unwound; repeating an exit is harmless because it replays a full profile replacement.
	 */
	public static Begin begin(UUID player, String name, Kind kind) { return begin(player, name, kind, Mode.NORMAL); }

	/** {@code returnMode} (NORMAL or BUILD) is where an EXIT_STORY transition ends; it is ignored by the other kinds. */
	public static synchronized Begin begin(UUID player, String name, Kind kind, Mode returnMode) {
		if (player == null) return new Begin(null, "Игрок недоступен.");
		if (IN_FLIGHT.containsKey(player)) return new Begin(null, "Переход уже выполняется (" + IN_FLIGHT.get(player).id + "). Подождите.");
		Record r = get(player);
		switch (kind) {
			case ENTER_BUILD -> {
				if (r.mode == Mode.BUILD) return new Begin(null, "Вы уже на стройке.");
				if (r.mode != Mode.NORMAL) return new Begin(null, "Предыдущий переход не завершён (" + r.mode + ", " + r.transitionId + "). Требуется восстановление: /cw diag.");
			}
			case ENTER_STORY -> {
				if (r.mode == Mode.STORY) return new Begin(null, "Вы уже в сюжете.");
				if (r.mode != Mode.NORMAL && r.mode != Mode.BUILD) return new Begin(null, "Предыдущий переход не завершён (" + r.mode + ", " + r.transitionId + "). Требуется восстановление: /cw diag.");
			}
			default -> { }
		}
		String id = UUID.randomUUID().toString().substring(0, 8);
		Mode back = returnMode == Mode.BUILD ? Mode.BUILD : Mode.NORMAL;
		Transition t = new Transition(id, player, kind, kind == Kind.ENTER_STORY ? (r.mode == Mode.BUILD ? Mode.BUILD : Mode.NORMAL) : back);
		Mode now = r.mode;
		Mode phase = switch (kind) { case ENTER_BUILD -> Mode.ENTERING_BUILD; case ENTER_STORY -> Mode.ENTERING_STORY; default -> Mode.EXITING; };
		String from = switch (kind) { case ENTER_BUILD -> "NORMAL"; case ENTER_STORY -> now == Mode.BUILD ? "BUILD" : "NORMAL"; case EXIT_BUILD -> "BUILD"; case EXIT_STORY -> "STORY"; };
		String to = switch (kind) { case ENTER_BUILD -> "BUILD"; case ENTER_STORY -> "STORY"; case EXIT_STORY -> back.name(); default -> "NORMAL"; };
		if (!store(player, phase, from, to, id, "begin " + kind + " (was " + now + ")")) return new Begin(null, "Не удалось записать состояние перехода на диск; действие отменено.");
		IN_FLIGHT.put(player, t);
		journal(player, name, id, kind.name(), "BEGIN", "was=" + now);
		return new Begin(t, null);
	}

	/** Journal-only checkpoint inside a transition (profile saved, chunks ready, teleported ...). */
	public static void step(Transition t, String step) {
		if (t != null) journal(t.player, null, t.id, t.kind.name(), "STEP", step);
	}

	/** The transition finished: the player is now in the stable target mode. */
	public static synchronized boolean commit(Transition t) {
		if (t == null) return false;
		Mode target = switch (t.kind) { case ENTER_BUILD -> Mode.BUILD; case ENTER_STORY -> Mode.STORY; case EXIT_STORY -> t.returnMode; default -> Mode.NORMAL; };
		boolean ok = store(t.player, target, target.name(), null, t.id, "commit " + t.kind);
		IN_FLIGHT.remove(t.player, t);
		journal(t.player, null, t.id, t.kind.name(), ok ? "COMMIT" : "COMMIT_UNSTORED", target.name());
		return ok;
	}

	/** The transition failed and everything was rolled back to {@code revertTo} (a stable mode). */
	public static synchronized void abort(Transition t, String reason, Mode revertTo) {
		if (t == null) return;
		Mode stable = revertTo == Mode.BUILD || revertTo == Mode.STORY ? revertTo : Mode.NORMAL;
		store(t.player, stable, stable.name(), null, t.id, "abort " + t.kind + ": " + reason);
		IN_FLIGHT.remove(t.player, t);
		journal(t.player, null, t.id, t.kind.name(), "ABORT", reason);
	}

	/** The transition stays unfinished (error mid-way, rollback impossible): recovery will pick it up. Releases only the in-memory guard. */
	public static synchronized void leaveInterrupted(Transition t, String reason) {
		if (t == null) return;
		IN_FLIGHT.remove(t.player, t);
		journal(t.player, null, t.id, t.kind.name(), "INTERRUPTED", reason);
	}

	/** Releases the in-memory guard of a player who left mid-transition; the persisted phase stays for recovery. */
	public static synchronized void release(UUID player, String why) {
		Transition t = player == null ? null : IN_FLIGHT.remove(player);
		if (t != null) journal(player, null, t.id, t.kind.name(), "INTERRUPTED", why);
	}

	/** Administrative / recovery override to a stable mode. */
	public static synchronized boolean force(UUID player, Mode stable, String why) {
		if (player == null) return false;
		Mode m = stable == Mode.BUILD || stable == Mode.STORY ? stable : Mode.NORMAL;
		IN_FLIGHT.remove(player);
		boolean ok = store(player, m, m.name(), null, "recovery", why);
		journal(player, null, "recovery", "FORCE", ok ? "OK" : "UNSTORED", m + " " + why);
		return ok;
	}

	private static boolean store(UUID player, Mode mode, String from, String to, String transitionId, String detail) {
		Record r = new Record();
		r.player = player.toString(); r.mode = mode; r.from = from; r.to = to; r.transitionId = transitionId;
		r.updatedAt = System.currentTimeMillis(); r.detail = detail;
		Path file = stateFile(player);
		if (file == null) return false;
		if (!CwDurable.write(file, GSON.toJson(r))) return false;
		CACHE.put(player, r);
		return true;
	}

	private static void journal(UUID player, String name, String id, String kind, String event, String detail) {
		Path file = journalFile();
		if (file == null) return;
		JsonObject line = new JsonObject();
		line.addProperty("t", System.currentTimeMillis());
		line.addProperty("player", String.valueOf(player));
		if (name != null) line.addProperty("name", name);
		line.addProperty("id", id);
		line.addProperty("kind", kind);
		line.addProperty("event", event);
		if (detail != null) line.addProperty("detail", detail);
		CwDurable.appendLine(file, new Gson().toJson(line));
		AdminControlMod.LOGGER.info("[CW] transition {} {} {} player={} {}", id, kind, event, player, detail == null ? "" : detail);
	}

	/** Last journal lines of one player, newest last. */
	public static java.util.List<String> tail(UUID player, int max) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String line : readJournal().split("\\R")) if (line.contains(player.toString())) out.add(line);
		return out.subList(Math.max(0, out.size() - max), out.size());
	}

	/** Visible for tests: the journal text. */
	static String readJournal() {
		Path file = journalFile();
		try { return file != null && Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : ""; }
		catch (Exception e) { return ""; }
	}
}
