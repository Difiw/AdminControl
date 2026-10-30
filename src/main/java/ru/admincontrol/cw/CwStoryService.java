package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.ActionLog;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Versioned per-player CW Expedition progress plus isolated, non-persistent admin test sessions. */
public final class CwStoryService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type STORE_TYPE = new TypeToken<Map<String, Progress>>(){}.getType();
	private static final Map<String, Progress> LIVE = new LinkedHashMap<>();
	private static final Map<UUID, Progress> TEST = new ConcurrentHashMap<>();
	private static final Map<String, Progress> SAVED_TEST = new LinkedHashMap<>();
	private static final Map<UUID, UUID> TEST_TARGET = new ConcurrentHashMap<>();
	private static final Map<UUID, Progress> PENDING_TEST = new ConcurrentHashMap<>();
	/** Morning of a new story day: a bright, ordinary daytime sky by default. */
	public static final long DEFAULT_STORY_TIME = 1000L;
	private static boolean loaded;

	public static final class Progress {
		public int version = 2;
		public int chapter;
		public String sceneId = "not_started";
		public String stageId = "not_started";
		public Set<String> completed = new LinkedHashSet<>();
		public Map<String, Boolean> flags = new LinkedHashMap<>();
		public Map<String, String> choices = new LinkedHashMap<>();
		public Map<String, String> npcState = new LinkedHashMap<>();
		public Map<String, String> worldState = new LinkedHashMap<>();
		public long storyTime = DEFAULT_STORY_TIME;
		/** AUTO slot: the rolling seven-minute autosave. Only the scheduler writes it. */
		public Snapshot rollingCheckpoint;
		/** MANUAL slot: the point set by the story token. Only the token writes it. */
		public Snapshot respawnPoint;
		public long nextAutosaveActiveTick = 20L * 60L * 7L;
		public long activeTicks;
		public boolean paused;
		public long modifiedAt = System.currentTimeMillis();
		public String modifiedBy = "system";
		public Progress copy() { return GSON.fromJson(GSON.toJson(this), Progress.class); }
	}
	/** Atomic, complete story-profile snapshot. NBT contains inventory, position, health and effects. */
	public static final class Snapshot {
		public static final int CURRENT_SCHEMA = 2;
		public int schemaVersion = CURRENT_SCHEMA;
		/** Ownership binding checked on every restore. Null only in schema-1 data written before bindings existed. */
		public String ownerUuid, storyId, instanceId;
		public String kind;
		public long createdAt;
		public String description;
		public String profileNbt;
		public String world;
		public double x, y, z;
		public float yaw, pitch;
		public String stageId, sceneId;
		public long storyTime;
		/** Full story progress at save time (chapter, stage, flags, choices, quests, NPC and world state). */
		public String progressJson;
		/** Entities of the story world (mobs, dropped items, orbs...) and the advancement file at save time. */
		public java.util.List<String> entities;
		public String advancementsJson;
		/** Every block and block entity of the story map (stored inside the save archive, never in the JSON). */
		public transient byte[] blocks;
		/** Id of the full file checkpoint of the personal world (cw-world-backups/owner/instance/id); null for older saves. */
		public String worldBackupId;
		public Snapshot copy() { return GSON.fromJson(GSON.toJson(this), Snapshot.class); }
	}

	private CwStoryService() {}
	private static Path file() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-progress.json"); }
	private static Path testFile() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-test-progress.json"); }
	private static synchronized void ensureLoaded() {
		if (loaded) return; loaded = true;
		Path path = file();
		if (path != null && Files.isRegularFile(path)) try {
			Map<String, Progress> read = GSON.fromJson(CwDurable.text(path), STORE_TYPE);
			if (read != null) LIVE.putAll(read);
		} catch (Exception e) { ActionLog.error("Не удалось прочитать CW-прогресс", e); }
		Path tests=testFile();if(tests!=null&&Files.isRegularFile(tests))try{Map<String,Progress> read=GSON.fromJson(CwDurable.text(tests),STORE_TYPE);if(read!=null)SAVED_TEST.putAll(read);}catch(Exception e){ActionLog.error("Не удалось прочитать тестовый CW-прогресс",e);}
	}
	public static synchronized Progress live(UUID player) { ensureLoaded(); return LIVE.computeIfAbsent(player.toString(), k -> new Progress()); }
	public static synchronized Progress find(UUID player) { ensureLoaded(); Progress p=LIVE.get(player.toString()); return p==null?null:p; }
	public static synchronized void reset(UUID player) { ensureLoaded(); LIVE.remove(player.toString()); save(); }
	public static Progress effective(ServerPlayerEntity admin, UUID target) {
		Progress test = TEST.get(admin.getUuid());
		return test != null ? test : live(target);
	}
	public static boolean inTest(ServerPlayerEntity admin) { return TEST.containsKey(admin.getUuid()); }
	/** Progress of the session the player is currently in: the isolated test copy for admin tests, otherwise the real one. */
	public static Progress session(ServerPlayerEntity player) { Progress test = TEST.get(player.getUuid()); return test != null ? test : live(player.getUuid()); }
	public static void enterTest(ServerPlayerEntity admin, UUID target) {
		enterTest(admin,target,false);
	}
	public static synchronized void enterTest(ServerPlayerEntity admin,UUID target,boolean restart){ensureLoaded();TEST_TARGET.put(admin.getUuid(),target);Progress pending=PENDING_TEST.remove(admin.getUuid());Progress selected=restart?new Progress():SAVED_TEST.get(admin.getUuidAsString());TEST.put(admin.getUuid(),pending!=null&&!restart?pending:selected==null?live(target).copy():selected.copy());}
	public static void setPendingTest(ServerPlayerEntity admin, Progress progress) { if(admin!=null&&progress!=null)PENDING_TEST.put(admin.getUuid(),progress.copy()); }
	public static void clearPendingTest(ServerPlayerEntity admin) { if(admin!=null)PENDING_TEST.remove(admin.getUuid()); }
	public static synchronized void exitTest(ServerPlayerEntity admin) { ensureLoaded();Progress progress=TEST.remove(admin.getUuid());if(progress!=null){touch(progress,"test-exit:"+admin.getUuidAsString());SAVED_TEST.put(admin.getUuidAsString(),progress.copy());saveTests();}TEST_TARGET.remove(admin.getUuid());PENDING_TEST.remove(admin.getUuid()); }
	/** Once-per-second copy of every running test into the saved-test file, so a crash costs at most one second of test progress. */
	public static synchronized void snapshotTestsAsync() {
		if (TEST.isEmpty()) return;
		ensureLoaded();
		TEST.forEach((id, progress) -> SAVED_TEST.put(id.toString(), progress.copy()));
		Path path = testFile(); if (path == null) return;
		CwDurable.writeAsync(path, GSON.toJson(SAVED_TEST));
	}
	public static synchronized boolean hasSavedTest(UUID admin){ensureLoaded();Progress p=SAVED_TEST.get(admin.toString());return started(p);}
	public static boolean started(Progress p){return p!=null&&(p.chapter>0||!"not_started".equals(p.stageId));}
	public static synchronized void markMovement(ServerPlayerEntity player){if(player==null)return;if(inTest(player)){Progress p=TEST.get(player.getUuid());if(!started(p)){p.chapter=1;p.stageId="chapter1_start";touch(p,"test:first_movement");}}else{Progress p=live(player.getUuid());if(!started(p)){p.chapter=1;p.stageId="chapter1_start";touch(p,"story:first_movement");save();}}}
	public static UUID testTarget(ServerPlayerEntity admin) { return TEST_TARGET.get(admin.getUuid()); }
	public static Progress test(ServerPlayerEntity admin) { return TEST.get(admin.getUuid()); }
	public static synchronized void save() {
		Path path = file(); if (path == null) return;
		if (!CwDurable.write(path, GSON.toJson(LIVE))) ActionLog.error("Не удалось сохранить CW-прогресс", new java.io.IOException(path.toString()));
	}
	/** Same file as {@link #save()}, written by the background writer (used by the once-per-second state save). */
	public static synchronized void saveAsync() {
		Path path = file(); if (path == null) return;
		CwDurable.writeAsync(path, GSON.toJson(LIVE));
	}
	private static void saveTests(){Path path=testFile();if(path==null)return;if(!CwDurable.write(path,GSON.toJson(SAVED_TEST)))ActionLog.error("Не удалось сохранить тестовый CW-прогресс",new java.io.IOException(path.toString()));}
	/** Progress without the legacy in-memory save slots, as stored inside a save file. */
	public static String progressJson(Progress p) { Progress c = p.copy(); c.rollingCheckpoint = null; c.respawnPoint = null; return GSON.toJson(c); }

	/** Applies the saved story state to the live progress. Timers, pause state and the save slots are left alone. */
	public static void applyProgress(Progress target, String json) {
		if (json == null || json.isBlank()) return;
		Progress s = GSON.fromJson(json, Progress.class);
		if (s == null) return;
		target.chapter = s.chapter; target.sceneId = s.sceneId; target.stageId = s.stageId; target.storyTime = s.storyTime;
		target.completed = s.completed; target.flags = s.flags; target.choices = s.choices; target.npcState = s.npcState; target.worldState = s.worldState;
	}
	public static void touch(Progress p, String source) { p.modifiedAt = System.currentTimeMillis(); p.modifiedBy = source; }
	public static String describe(Progress p) {
		return "глава=" + p.chapter + ", этап=" + p.stageId + ", пауза=" + p.paused
				+ ", завершено=" + p.completed.size() + ", изменено=" + Instant.ofEpochMilli(p.modifiedAt);
	}
}
