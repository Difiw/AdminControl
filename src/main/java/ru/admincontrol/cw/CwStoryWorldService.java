package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry into and exit from pre-registered persistent story world slots.
 *
 * <p>Two profiles exist per player besides BUILD. The MAIN profile (inventory, position, effects, game mode of the ordinary
 * world) is frozen into a {@link Session} when the player enters the story and is written to disk before anything
 * changes. The STORY profile lives in {@code cw-story-profiles.json} and is written when the player leaves, periodically,
 * and on disconnect. All files are durable (fsync, checksum, backup). Transitions are driven by {@link CwPlayerState}:
 * parse and validate everything first, touch the player only afterwards, and roll back to the previous profile on any
 * failure instead of producing a hybrid. A session survives disconnects and server restarts: only an explicit exit (or
 * an administrative reset) ends it, and a returning player is put back into the same personal story world.</p>
 *
 * <p>The authored template is copied into a story instance exactly once ({@code Instance.seededAt}); later entries
 * reuse the instance as it is, so blocks built or changed during the story survive exits and restarts.</p>
 */
public final class CwStoryWorldService {
	/** MAIN-profile return record plus the story binding of one active story session. Persisted as JSON. */
	public static final class Session {
		public int version = 2;
		/** Main world to return to. */
		public String returnWorld;
		public double x, y, z;
		public float yaw, pitch;
		public String profileNbt;
		/** The ordinary world's advancements, frozen while the player is in Люмира (which has its own). */
		public String mainAdvancements;
		public String gameMode;
		public String storyKey;
		public String instanceId, storyId, instanceWorld;
		/** Block region of the story map (min x,y,z then max x,y,z) used by world saves; null for sessions created before saves covered blocks. */
		public int[] bounds;
		/** Safe start of the story instance, the last resort respawn anchor. */
		public double entryX, entryY, entryZ;
		public float entryYaw;
		/** NORMAL, or BUILD when an administrator started a test from the build world. */
		public String originMode = "NORMAL";
		public String transitionId;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<String, String> STORY_PROFILES = new ConcurrentHashMap<>();
	/** Люмира has its own advancements per player; stored next to the story profiles. */
	private static final Map<String, String> STORY_ADVANCEMENTS = new ConcurrentHashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {}.getType();
	private static boolean loaded, sessionsLoaded, advancementsLoaded;
	/** Set when a store and its backup are both unreadable: switching is blocked until an administrator restores the file. */
	private static volatile String storeProblem;

	private CwStoryWorldService() {}

	public static String enterPersonal(ServerPlayerEntity player, String story, String template, int version, String slot) {
		return enter(player, CwInstanceRegistry.Type.PERSONAL, player.getUuid(), story, template, version, slot, "personal story entry", false);
	}

	public static String enterTest(ServerPlayerEntity admin, String story, String template, int version) { return enterTest(admin, story, template, version, false); }

	public static String enterTest(ServerPlayerEntity admin, String story, String template, int version, boolean restart) {
		if (restart) {
			if (CwPlayerState.busy(admin.getUuid())) return "Переход уже выполняется. Подождите.";
			loadProfiles(); STORY_PROFILES.remove(profileKey(admin, CwInstanceRegistry.Type.TEST, story)); saveProfiles();
		}
		return enter(admin, CwInstanceRegistry.Type.TEST, admin.getUuid(), story, template, version, "test", "explicit test preview", restart);
	}

	public static String problem() { return storeProblem; }

	private static String enter(ServerPlayerEntity p, CwInstanceRegistry.Type type, UUID owner, String story, String template, int version, String slot, String reason, boolean resync) {
		if (p == null || p.getServer() == null) return "Игрок или сервер недоступен.";
		if (CwStoryRules.inside(p)) return CwStoryService.inTest(p)
				? "Уже запущен администраторский тест; сначала завершите его."
				: "Уже запущен официальный сюжет; сначала покиньте его командой /storyexit.";
		loadProfiles(); loadSessions();
		if (storeProblem != null) return "Хранилище сюжета повреждено и не восстановлено автоматически: " + storeProblem + ". Сообщите администратору: /cw diag.";
		String clientProblem = CwClientGate.problem(p); if (clientProblem != null) return clientProblem;
		CwPlayerState.Begin begin = CwPlayerState.begin(p.getUuid(), p.getName().getString(), CwPlayerState.Kind.ENTER_STORY);
		if (!begin.ok()) return begin.error();
		CwPlayerState.Transition tr = begin.transition();
		try {
			return enterLocked(p, type, owner, story, template, version, slot, reason, resync, tr);
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] story entry crashed transition={} player={}", tr.id, p.getUuidAsString(), e);
			CwPlayerState.leaveInterrupted(tr, "exception " + e);
			return "Не удалось открыть вашу Люмиру (код " + tr.id + "). Состояние сохранено; повторите или обратитесь к администратору.";
		}
	}

	private static String enterLocked(ServerPlayerEntity p, CwInstanceRegistry.Type type, UUID owner, String story, String template, int version, String slot,
			String reason, boolean resync, CwPlayerState.Transition tr) {
		CwInstanceRegistry.Instance i = CwInstanceRegistry.allocate(type, owner, template, version, story, slot, reason);
		if (i == null) { CwPlayerState.abort(tr, "no free slots", tr.returnMode); return "Нет свободных сюжетных слотов (16/16). Архивируйте ненужный test instance."; }
		Identifier id = Identifier.tryParse(i.worldKey);
		if (id == null) { CwPlayerState.abort(tr, "bad worldKey", tr.returnMode); return "Некорректный worldKey в registry."; }
		ServerWorld world = p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		if (world == null) {
			i.lifecycle = CwInstanceRegistry.Lifecycle.ERROR; i.updatedAt = System.currentTimeMillis(); CwInstanceRegistry.save();
			CwPlayerState.abort(tr, "world missing", tr.returnMode);
			return "Мир " + i.worldKey + " не загружен; datapack слота отсутствует.";
		}
		String storyKey = profileKey(p, type, story);

		// 0. The template is copied into the instance once. A later entry returns to the instance as the player left it.
		ServerWorld templateWorld = CwBuilderWorld.world(p.getServer());
		BlockPos spawn; int[] bounds; int changed;
		boolean adopt = i.seededAt == 0 && !resync && STORY_PROFILES.containsKey(storyKey);
		if (i.seededAt == 0 || resync) {
			CwTemplateSync.Result sync = adopt ? CwTemplateSync.measure(templateWorld) : CwTemplateSync.copy(templateWorld, world);
			if (!sync.ok()) { CwPlayerState.abort(tr, "template: " + sync.error(), tr.returnMode); return sync.error(); }
			spawn = sync.spawn(); changed = sync.changed();
			bounds = new int[] {sync.min().getX(), sync.min().getY(), sync.min().getZ(), sync.max().getX(), sync.max().getY(), sync.max().getZ()};
			i.seededAt = System.currentTimeMillis(); i.seededVersion = version; i.seedBounds = bounds.clone();
			i.seedSpawn = new double[] {spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5};
			i.updatedAt = i.seededAt;
			if (!CwInstanceRegistry.save()) {
				i.seededAt = 0;
				CwPlayerState.abort(tr, "registry not stored", tr.returnMode);
				return "Не удалось записать реестр экземпляров; вход отменён (код " + tr.id + ").";
			}
			CwPlayerState.step(tr, (adopt ? "instance adopted" : "template copied") + " changed=" + changed + " bounds=" + java.util.Arrays.toString(bounds));
		} else {
			bounds = i.seedBounds == null ? null : i.seedBounds.clone();
			double[] s = i.seedSpawn == null ? new double[] {.5, 65, .5} : i.seedSpawn;
			spawn = BlockPos.ofFloored(s[0], s[1], s[2]);
			changed = -1;
			CwPlayerState.step(tr, "instance reused (seeded " + i.seededAt + ")");
		}

		// 1. Freeze the MAIN profile and persist the session BEFORE the player is changed in any way.
		NbtCompound mainProfile = CwProfile.capture(p);
		Session session = new Session();
		session.returnWorld = p.getWorld().getRegistryKey().getValue().toString();
		session.x = p.getX(); session.y = p.getY(); session.z = p.getZ(); session.yaw = p.getYaw(); session.pitch = p.getPitch();
		session.profileNbt = mainProfile.asString();
		session.mainAdvancements = CwWorldState.captureAdvancements(p);
		session.gameMode = p.interactionManager.getGameMode().getName();
		session.storyKey = storyKey;
		session.instanceId = i.instanceId; session.storyId = i.storyId; session.instanceWorld = i.worldKey;
		session.bounds = bounds;
		session.entryX = spawn.getX() + .5; session.entryY = spawn.getY(); session.entryZ = spawn.getZ() + .5; session.entryYaw = p.getYaw();
		session.originMode = tr.returnMode.name(); session.transitionId = tr.id;
		SESSIONS.put(p.getUuid(), session);
		if (!saveSessions()) {
			SESSIONS.remove(p.getUuid());
			CwPlayerState.abort(tr, "session not stored", tr.returnMode);
			return "Не удалось сохранить состояние перехода. Попробуйте позже (код " + tr.id + ").";
		}
		CwPlayerState.step(tr, "MAIN profile stored: " + CwProfile.describe(mainProfile));
		i.lifecycle = CwInstanceRegistry.Lifecycle.LOADED; i.updatedAt = System.currentTimeMillis(); CwInstanceRegistry.save();

		// 2. Switch to the STORY profile; any failure restores MAIN and removes the session.
		try {
			String saved = STORY_PROFILES.get(session.storyKey);
			NbtCompound storyNbt = saved == null || saved.isBlank() ? null : CwProfile.parse(saved);
			boolean continuing = storyNbt != null;
			if (continuing) CwProfile.apply(p, storyNbt); else CwProfile.clean(p);
			loadAdvancements();
			CwWorldState.restoreAdvancements(p, STORY_ADVANCEMENTS.getOrDefault(session.storyKey, "{}"));
			double targetX = continuing ? p.getX() : spawn.getX() + .5, targetY = continuing ? p.getY() : spawn.getY(), targetZ = continuing ? p.getZ() : spawn.getZ() + .5;
			float targetYaw = continuing ? p.getYaw() : session.entryYaw, targetPitch = continuing ? p.getPitch() : 0;
			if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ) || targetY < world.getBottomY() - 8) {
				targetX = spawn.getX() + .5; targetY = spawn.getY(); targetZ = spawn.getZ() + .5;
			}
			p.setInvulnerable(false); p.getAbilities().invulnerable = false; p.getAbilities().flying = false;
			String problem = CwSafeTeleport.move(p, world, targetX, targetY, targetZ, targetYaw, targetPitch, tr.id);
			if (problem != null) throw new IllegalStateException(problem);
			p.changeGameMode(GameMode.SURVIVAL);
			p.sendAbilitiesUpdate();
			ru.admincontrol.util.SkillTreeService.applyEffects(p);
			resync(p);
			CwCheckpointService.bindSpawnAnchor(p);
			// First AUTO snapshot only for a new story (or when none exists); a returning player keeps the saves they have.
			if (!continuing || CwCheckpointService.slotSnapshot(p, CwCheckpointService.Slot.AUTO) == null) CwCheckpointService.requestEntrySave(p.getUuid());
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] story entry failed transition={} player={}", tr.id, p.getUuidAsString(), e);
			boolean restored = restoreMain(p, session);
			SESSIONS.remove(p.getUuid()); saveSessions();
			if (restored) CwPlayerState.abort(tr, "entry failed: " + e.getMessage(), tr.returnMode); else CwPlayerState.leaveInterrupted(tr, "entry failed and rollback failed: " + e.getMessage());
			return "Не удалось открыть вашу Люмиру (код " + tr.id + ").";
		}
		CwPlayerState.commit(tr);
		AdminControlMod.LOGGER.info("[CW] story session started transition={} player={} story={} instance={} dimension={} folder={} loadedChunks={} seed={}", tr.id,
				p.getUuidAsString(), story, i.instanceId, i.worldKey, CwBuilderWorld.worldFolder(world), CwBuilderWorld.loadedChunks(world), changed < 0 ? "reused" : "changed=" + changed);
		p.sendMessage(net.minecraft.text.Text.literal(changed < 0 ? "[CW] Вы вернулись в свой экземпляр Люмиры." : "[CW] Карта подготовлена: изменено блоков — " + changed + "."), true);
		return null;
	}

	/** Explicit end of the story session: saves the STORY profile, restores MAIN and the main-world position. */
	public static String leaveTest(ServerPlayerEntity player) { return leave(player, true); }

	private static String leave(ServerPlayerEntity player, boolean saveStory) {
		if (player == null || player.getServer() == null) return "Игрок или сервер недоступен.";
		loadProfiles(); loadSessions();
		Session session = SESSIONS.get(player.getUuid());
		CwPlayerState.Mode back = session != null && "BUILD".equals(session.originMode) ? CwPlayerState.Mode.BUILD : CwPlayerState.Mode.NORMAL;
		CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.EXIT_STORY, back);
		if (!begin.ok()) return begin.error();
		CwPlayerState.Transition tr = begin.transition();
		try {
			return leaveLocked(player, saveStory, session, tr);
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] story exit crashed transition={} player={}", tr.id, player.getUuidAsString(), e);
			CwPlayerState.leaveInterrupted(tr, "exception " + e);
			return "Не удалось вернуться в основной мир (код " + tr.id + "). Прогресс сохранён, попробуйте ещё раз.";
		}
	}

	private static String leaveLocked(ServerPlayerEntity player, boolean saveStory, Session session, CwPlayerState.Transition tr) {
		if (session == null) {
			// No session: the player is somewhere in a story world with no frozen MAIN profile. Never invent a profile; move them to the main spawn.
			ServerWorld overworld = player.getServer().getOverworld();
			String problem = CwSafeTeleport.move(player, overworld, overworld.getSpawnPos().getX() + .5, overworld.getSpawnPos().getY(), overworld.getSpawnPos().getZ() + .5, 0, 0, tr.id);
			if (problem != null) { CwPlayerState.leaveInterrupted(tr, problem); return "Мир возврата недоступен (код " + tr.id + ")."; }
			ru.admincontrol.util.AuthService.rebase(player);
			CwPlayerState.abort(tr, "no session", CwPlayerState.Mode.NORMAL);
			return null;
		}
		Identifier id = Identifier.tryParse(session.returnWorld);
		ServerWorld target = id == null ? null : player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		if (target == null) { CwPlayerState.abort(tr, "return world missing", CwPlayerState.Mode.STORY); return "Мир возврата недоступен."; }
		NbtCompound main;
		try {
			if (session.profileNbt == null || session.profileNbt.isBlank()) throw new IllegalStateException("MAIN profile is empty");
			main = CwProfile.parse(session.profileNbt);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] main profile unreadable transition={} player={}", tr.id, player.getUuidAsString(), e);
			CwPlayerState.abort(tr, "main profile unreadable", CwPlayerState.Mode.STORY);
			return "Основной профиль повреждён; обратитесь к администратору. Люмира не закрыта (код " + tr.id + ").";
		}
		NbtCompound story = CwProfile.capture(player);
		if (saveStory) {
			STORY_PROFILES.put(session.storyKey, story.asString());
			if (!saveProfiles()) { CwPlayerState.abort(tr, "story profile not stored", CwPlayerState.Mode.STORY); return "Не удалось сохранить прогресс сюжета; выход отменён (код " + tr.id + ")."; }
			storeStoryAdvancements(player, session);
		}
		CwPlayerState.step(tr, "STORY profile stored: " + CwProfile.describe(story));
		try {
			CwProfile.apply(player, main);
			if (session.mainAdvancements != null) CwWorldState.restoreAdvancements(player, session.mainAdvancements);
			String problem = CwSafeTeleport.move(player, target, session.x, session.y, session.z, session.yaw, session.pitch, tr.id);
			if (problem != null) throw new IllegalStateException(problem);
			player.changeGameMode(GameMode.byName(session.gameMode, GameMode.SURVIVAL));
			CwProfile.applyAbilities(player, main);
			ru.admincontrol.util.SkillTreeService.applyEffects(player);
			resync(player);
			ru.admincontrol.util.AuthService.rebase(player);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] story exit failed transition={} player={}", tr.id, player.getUuidAsString(), e);
			try { if (CwStoryRules.inside(player)) { CwProfile.apply(player, story); player.changeGameMode(GameMode.SURVIVAL); resync(player); } }
			catch (Exception rollback) { AdminControlMod.LOGGER.error("[CW] story exit rollback failed transition={} player={}", tr.id, player.getUuidAsString(), rollback); }
			CwPlayerState.abort(tr, "exit failed: " + e.getMessage(), CwPlayerState.Mode.STORY);
			return "Не удалось вернуться в основной мир (код " + tr.id + "). Прогресс сохранён, попробуйте ещё раз.";
		}
		flushStoryWorld(player.getServer(), session);
		if (SESSIONS.remove(player.getUuid()) != null) saveSessions();
		CwPlayerState.commit(tr);
		AdminControlMod.LOGGER.info("[CW] story session ended transition={} player={}", tr.id, player.getUuidAsString());
		return null;
	}

	public static String leaveStory(ServerPlayerEntity player) {
		if (player == null) return "Игрок недоступен.";
		if (!CwStoryRules.inside(player)) return "Вы сейчас не находитесь в Люмире.";
		if (CwStoryService.inTest(player)) CwStoryService.exitTest(player);
		CwStoryService.save();
		return leaveTest(player);
	}

	/** Stores the player's current profile as the STORY profile right now (used at the moment of death). */
	public static void saveStoryProfileNow(ServerPlayerEntity player) {
		loadProfiles(); loadSessions();
		Session session = player == null ? null : SESSIONS.get(player.getUuid());
		if (session == null) return;
		NbtCompound nbt = new NbtCompound();
		player.writeNbt(nbt);
		STORY_PROFILES.put(session.storyKey, nbt.asString());
		storeStoryAdvancements(player, session);
		saveProfiles();
	}

	/** Exit from the death window: the player is in the waiting dimension, the STORY profile was stored at death and stays untouched. */
	public static String leaveWithoutSaving(ServerPlayerEntity player) {
		if (player == null) return "Игрок недоступен.";
		if (CwStoryService.inTest(player)) CwStoryService.exitTest(player);
		CwStoryService.save();
		return leave(player, false);
	}

	/** Periodic persistence of every active STORY profile (crash safety). */
	public static void autosave(MinecraftServer server) {
		if (server == null) return;
		loadProfiles(); loadSessions();
		// Every second: the player profile and the session metadata (position, inventory, effects, progress) go to disk via the
		// background writer. Once a minute additionally: advancements and the chunk flush of the story world.
		boolean heavy = server.getTicks() % 1200 == 0;
		boolean changed = false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Session session = SESSIONS.get(player.getUuid());
			if (session == null || !CwStoryRules.inside(player) || !player.isAlive() || CwPlayerState.busy(player.getUuid())) continue;
			NbtCompound nbt = new NbtCompound();
			player.writeNbt(nbt);
			STORY_PROFILES.put(session.storyKey, nbt.asString());
			if (heavy) {
				storeStoryAdvancements(player, session);
				flushStoryWorld(server, session, false);
			}
			changed = true;
		}
		if (changed) {
			if (storeProblem == null) { CwDurable.writeAsync(profilesFile(), GSON.toJson(STORY_PROFILES)); }
			CwStoryService.saveAsync();
		}
		CwStoryService.snapshotTestsAsync();
	}

	/**
	 * Disconnecting does NOT end the story: the story profile is stored and the session stays bound to the player, so a
	 * reconnect (or server restart) puts them back into their own story world. Admin tests are ended instead.
	 */
	public static void onDisconnect(ServerPlayerEntity player) {
		if (player == null) return;
		loadProfiles(); loadSessions();
		CwPlayerState.release(player.getUuid(), "disconnect");
		Session session = SESSIONS.get(player.getUuid());
		if (session == null) return;
		if (CwStoryService.inTest(player)) { CwStoryService.exitTest(player); leaveTest(player); return; }
		if (!CwStoryRules.inside(player)) return;
		NbtCompound nbt = new NbtCompound();
		player.writeNbt(nbt);
		STORY_PROFILES.put(session.storyKey, nbt.asString());
		storeStoryAdvancements(player, session);
		saveProfiles();
		CwStoryService.save();
		flushStoryWorld(player.getServer(), session);
	}

	/** Игрок оказался в сюжетном мире без сессии (старый сбой): безопасно возвращаем в основной мир. */
	private static void strandedToMain(ServerPlayerEntity player) {
		AdminControlMod.LOGGER.warn("[CW] player found in story world without a session; moving to main world spawn player={}", player.getUuidAsString());
		try {
			player.closeHandledScreen();
			ServerWorld overworld = player.getServer().getOverworld();
			BlockPos spawn = overworld.getSpawnPos();
			String problem = CwSafeTeleport.move(player, overworld, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, overworld.getSpawnAngle(), 0, "stranded");
			if (problem != null) { AdminControlMod.LOGGER.error("[CW] stranded return failed player={}: {}", player.getUuidAsString(), problem); return; }
			player.changeGameMode(GameMode.SURVIVAL);
			ru.admincontrol.util.SkillTreeService.applyEffects(player);
			resync(player);
			ru.admincontrol.util.AuthService.rebase(player);
			player.sendMessage(net.minecraft.text.Text.literal("[CW] Вы возвращены в основной мир."), false);
			CwPlayerState.force(player.getUuid(), CwPlayerState.Mode.NORMAL, "stranded in story world without a session");
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] stranded return failed player={}", player.getUuidAsString(), e);
		}
	}

	/** Rebinds a returning player to the session: resumes the story in their own world, or repairs a session whose story world no longer exists. */
	public static void onJoin(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return;
		loadSessions(); loadProfiles();
		Session session = SESSIONS.get(player.getUuid());
		// Выход из игры в окне смерти не оставляет игрока в сюжетном мире: основной профиль и основной мир возвращаются, продолжить можно через NPC.
		if (CwRespawnService.inLimbo(player)) {
			if (session != null) {
				String error = leaveWithoutSaving(player);
				if (error != null) player.sendMessage(net.minecraft.text.Text.literal("[CW] " + error), false);
			} else {
				strandedToMain(player);
			}
			return;
		}
		if (session == null) {
			if (CwStoryRules.inside(player)) {
				if (storeProblem != null) { player.sendMessage(net.minecraft.text.Text.literal("[CW] Хранилище сюжета повреждено: " + storeProblem + ". Администратор: /cw diag."), false); return; }
				strandedToMain(player);
			}
			return;
		}
		CwInstanceRegistry.Instance instance = session.instanceId == null ? null : CwInstanceRegistry.get(session.instanceId);
		boolean usable = instance != null && (instance.lifecycle == CwInstanceRegistry.Lifecycle.LOADED || instance.lifecycle == CwInstanceRegistry.Lifecycle.READY);
		if (CwStoryRules.inside(player)) {
			if (usable && session.instanceWorld != null && session.instanceWorld.equals(player.getWorld().getRegistryKey().getValue().toString())) {
				if (CwStoryService.inTest(player) || "test".equals(instance.slot)) { // an administrator's test never survives a disconnect
					CwStoryService.save();
					String error = leave(player, true);
					if (error != null) player.sendMessage(net.minecraft.text.Text.literal("[CW] " + error), false);
					return;
				}
				resumeSession(player, session);
				return;
			}
			AdminControlMod.LOGGER.warn("[CW] story instance unavailable on join player={}; returning to main profile", player.getUuidAsString());
			String error = leave(player, false);
			if (error != null) player.sendMessage(net.minecraft.text.Text.literal("[CW] " + error), false);
			return;
		}
		if (!usable) {
			// The story dimension is gone and the vanilla login put the story profile into another world: restore MAIN.
			AdminControlMod.LOGGER.warn("[CW] story world missing on join player={}; restoring main profile", player.getUuidAsString());
			String error = leave(player, false);
			if (error != null) player.sendMessage(net.minecraft.text.Text.literal("[CW] " + error), false);
		} else {
			// Vanilla may construct the login player in the overworld even though our durable session still owns a valid
			// story instance. The session is authoritative: deleting it here lost the return profile and caused the exact
			// "logged out in story, logged in in overworld" failure. Resume it from whatever world vanilla selected.
			AdminControlMod.LOGGER.warn("[CW] player joined outside active story instance; resuming session player={} actual={} wanted={} state={}",
					player.getUuidAsString(), player.getServerWorld().getRegistryKey().getValue(), session.instanceWorld, CwPlayerState.mode(player.getUuid()));
			resumeSession(player, session);
		}
	}

	/** Reapply the newest STORY profile and continue in its bound world, regardless of the dimension vanilla used at login. */
	private static void resumeSession(ServerPlayerEntity player, Session session) {
		CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.ENTER_STORY);
		boolean tracked = begin.ok();
		if (!tracked && CwPlayerState.mode(player.getUuid()) != CwPlayerState.Mode.ENTERING_STORY && CwPlayerState.mode(player.getUuid()) != CwPlayerState.Mode.STORY) return;
		try {
			Identifier worldId = Identifier.tryParse(session.instanceWorld);
			ServerWorld world = worldId == null ? null : player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, worldId));
			if (world == null) throw new IllegalStateException("story world is unavailable: " + session.instanceWorld);
			String saved = STORY_PROFILES.get(session.storyKey);
			double x, y, z;
			float yaw, pitch;
			if (saved != null && !saved.isBlank()) {
				NbtCompound nbt = CwProfile.parse(saved);
				CwProfile.apply(player, nbt);
				x = player.getX(); y = player.getY(); z = player.getZ(); yaw = player.getYaw(); pitch = player.getPitch();
			} else {
				// The session journal is written before the first teleport. A crash in that narrow window has no STORY
				// snapshot yet; create the same clean profile as a normal first entry instead of leaving MAIN in overworld.
				CwProfile.clean(player);
				x = session.entryX; y = session.entryY; z = session.entryZ; yaw = session.entryYaw; pitch = 0;
			}
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
					|| y < world.getBottomY() || y >= world.getTopY()) { x = session.entryX; y = session.entryY; z = session.entryZ; }
			String problem = CwSafeTeleport.move(player, world, x, y, z, yaw, pitch, begin.ok() ? begin.transition().id : "resume");
			if (problem != null) throw new IllegalStateException(problem);
			loadAdvancements();
			CwWorldState.restoreAdvancements(player, STORY_ADVANCEMENTS.getOrDefault(session.storyKey, "{}"));
			player.changeGameMode(GameMode.SURVIVAL);
			ru.admincontrol.util.SkillTreeService.applyEffects(player);
			resync(player);
			CwCheckpointService.bindSpawnAnchor(player);
			ru.admincontrol.util.AuthService.rebase(player);
			if (tracked) CwPlayerState.commit(begin.transition()); else CwPlayerState.force(player.getUuid(), CwPlayerState.Mode.STORY, "resumed in place");
			AdminControlMod.LOGGER.info("[CW] story session resumed player={} world={} chunk={},{} authPending={}", player.getUuidAsString(), session.instanceWorld,
					player.getBlockX() >> 4, player.getBlockZ() >> 4, ru.admincontrol.util.AuthService.pending(player));
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] story resume failed player={}", player.getUuidAsString(), e);
			if (tracked) CwPlayerState.leaveInterrupted(begin.transition(), "resume failed: " + e.getMessage());
			player.sendMessage(net.minecraft.text.Text.literal("[CW] Не удалось продолжить сюжет: профиль сохранён, администратор может восстановить его через /cw diag."), false);
		}
	}

	/** Removes every isolated profile owned by the player after safely returning an active player. A full backup is written first. */
	public static synchronized String resetPlayer(ServerPlayerEntity player) {
		if (player == null) return "Игрок недоступен.";
		if (CwStoryRules.inside(player)) { player.closeHandledScreen(); String error = leaveStory(player); if (error != null) return error; }
		return resetOffline(player.getUuid());
	}

	public static synchronized String resetOffline(UUID playerId) {
		if (playerId == null) return "UUID не задан.";
		loadProfiles(); loadSessions();
		String backup = writeResetBackup(playerId);
		if (backup == null) return "Не удалось записать резервную копию перед сбросом; сброс отменён.";
		String prefix = playerId + "/";
		STORY_PROFILES.keySet().removeIf(k -> k.startsWith(prefix)); saveProfiles(); loadAdvancements(); STORY_ADVANCEMENTS.keySet().removeIf(k -> k.startsWith(prefix)); saveAdvancements();
		if (SESSIONS.remove(playerId) != null) saveSessions();
		for (var i : CwInstanceRegistry.all().values()) if (playerId.toString().equals(i.ownerUuid) && i.type != CwInstanceRegistry.Type.TEMPLATE) {
			String error = CwInstanceRegistry.schedule(i.instanceId, "RESET"); if (error != null) return error;
		}
		CwSaveStore.deleteAll(playerId);
		CwCheckpointService.clear(playerId);
		CwStoryService.reset(playerId);
		return null;
	}

	private static String writeResetBackup(UUID playerId) {
		DataManager data = DataManager.get();
		if (data == null) return null;
		Path file = data.getConfigDir().resolve("cw-reset-backups").resolve(playerId + "-" + System.currentTimeMillis() + ".json");
		return CwDurable.write(file, backupJson(playerId)) ? file.toString() : null;
	}

	/** JSON dump of everything story-related owned by exactly this UUID (progress, story profiles, session incl. main profile). */
	public static synchronized String backupJson(UUID playerId) {
		loadProfiles(); loadSessions();
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("player", playerId.toString());
		out.put("progress", CwStoryService.find(playerId));
		Map<String, String> profiles = new java.util.LinkedHashMap<>();
		String prefix = playerId + "/";
		STORY_PROFILES.forEach((k, v) -> { if (k.startsWith(prefix)) profiles.put(k, v); });
		out.put("storyProfiles", profiles);
		out.put("session", SESSIONS.get(playerId));
		out.put("saves", CwSaveStore.dump(playerId));
		loadAdvancements();
		Map<String, String> adv = new java.util.LinkedHashMap<>();
		STORY_ADVANCEMENTS.forEach((k, v) -> { if (k.startsWith(prefix)) adv.put(k, v); });
		out.put("storyAdvancements", adv);
		return GSON.toJson(out);
	}

	public static boolean hasSession(UUID player) { loadSessions(); return player != null && SESSIONS.containsKey(player); }
	public static Session session(UUID player) { loadSessions(); return player == null ? null : SESSIONS.get(player); }

	/** The story instance bound to the player's active session, or null if none / no longer usable. */
	public static CwInstanceRegistry.Instance instanceOf(ServerPlayerEntity player) {
		Session session = player == null ? null : session(player.getUuid());
		if (session == null || session.instanceId == null) return null;
		CwInstanceRegistry.Instance i = CwInstanceRegistry.get(session.instanceId);
		if (i == null) return null;
		return switch (i.lifecycle) { case LOADED, READY -> i; default -> null; };
	}

	/** Safe story start of the player's session, used when vanilla routing must be repaired. */
	public static String returnToStorySpawn(ServerPlayerEntity player) {
		Session session = player == null ? null : session(player.getUuid());
		if (session == null || player.getServer() == null) return "Сессия в Люмире не найдена.";
		Identifier id = Identifier.tryParse(session.instanceWorld);
		ServerWorld world = id == null ? null : player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		if (world == null) return "Эта часть Люмиры недоступна.";
		String problem = CwSafeTeleport.move(player, world, session.entryX, session.entryY, session.entryZ, session.entryYaw, 0, "story-spawn");
		return problem == null ? null : "Не удалось вернуться на точку входа: " + problem;
	}

	/** Pushes server-only state (abilities, effects, health, experience) to the client after profile NBT was loaded. */
	public static void resync(ServerPlayerEntity p) { CwProfile.resync(p); }

	/** Marks the player's story instances for a fresh copy of the template on the next entry. Their progress in blocks is replaced; profiles stay. */
	public static String requestResync(UUID playerId) {
		if (playerId == null) return "UUID не задан.";
		int n = 0;
		for (var i : CwInstanceRegistry.all().values()) if (playerId.toString().equals(i.ownerUuid) && i.type != CwInstanceRegistry.Type.TEMPLATE) { i.seededAt = 0; i.seedBounds = null; i.seedSpawn = null; n++; }
		if (n == 0) return "У игрока нет экземпляров.";
		return CwInstanceRegistry.save() ? null : "Не удалось записать реестр.";
	}

	// ---- diagnostics ----

	public static synchronized List<String> diagnostics(UUID playerId, MinecraftServer server) {
		loadProfiles(); loadSessions(); loadAdvancements();
		List<String> out = new ArrayList<>();
		out.add("story store: " + (storeProblem == null ? "ok" : "ПРОБЛЕМА: " + storeProblem));
		Session s = playerId == null ? null : SESSIONS.get(playerId);
		if (s == null) out.add("story session: нет");
		else {
			out.add("story session: instance=" + s.instanceId + " world=" + s.instanceWorld + " origin=" + s.originMode + " transition=" + s.transitionId
					+ String.format(Locale.ROOT, " mainDim=%s mainPos=%.1f %.1f %.1f", s.returnWorld, s.x, s.y, s.z) + " mainProfile=" + (s.profileNbt == null ? "НЕТ" : s.profileNbt.length() + " символов"));
			CwInstanceRegistry.Instance i = s.instanceId == null ? null : CwInstanceRegistry.get(s.instanceId);
			if (i != null) {
				Identifier id = Identifier.tryParse(i.worldKey);
				ServerWorld w = server == null || id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
				out.add("instance: lifecycle=" + i.lifecycle + " seededAt=" + i.seededAt + " templateVersion=" + i.seededVersion + " folder=" + (w == null ? i.worldFolder : CwBuilderWorld.worldFolder(w))
						+ " loadedChunks=" + (w == null ? 0 : CwBuilderWorld.loadedChunks(w)));
			}
		}
		if (playerId != null) {
			String prefix = playerId + "/";
			long profiles = STORY_PROFILES.keySet().stream().filter(k -> k.startsWith(prefix)).count();
			out.add("story profiles: " + profiles);
		}
		return out;
	}

	private static boolean restoreMain(ServerPlayerEntity p, Session session) {
		if (session == null) return false;
		try {
			CwProfile.apply(p, CwProfile.parse(session.profileNbt));
			if (session.mainAdvancements != null) CwWorldState.restoreAdvancements(p, session.mainAdvancements);
			Identifier id = Identifier.tryParse(session.returnWorld);
			ServerWorld w = id == null ? null : p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
			if (w != null) {
				String problem = CwSafeTeleport.move(p, w, session.x, session.y, session.z, session.yaw, session.pitch, session.transitionId);
				if (problem != null) throw new IllegalStateException(problem);
			}
			p.changeGameMode(GameMode.byName(session.gameMode, GameMode.SURVIVAL));
			CwProfile.applyAbilities(p, CwProfile.parse(session.profileNbt));
			resync(p);
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] main profile rollback failed player={}", p.getUuidAsString(), e);
			return false;
		}
	}

	private static void flushStoryWorld(MinecraftServer server, Session session) { flushStoryWorld(server, session, true); }

	private static void flushStoryWorld(MinecraftServer server, Session session, boolean flush) {
		if (server == null || session == null || session.instanceWorld == null) return;
		Identifier id = Identifier.tryParse(session.instanceWorld);
		ServerWorld world = id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		if (world == null) return;
		try { world.getChunkManager().save(flush); }
		catch (RuntimeException e) { AdminControlMod.LOGGER.error("[CW] story world flush failed world={}", session.instanceWorld, e); }
	}

	// ---- persistence ----

	private static Path advancementsFile() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-advancements.json"); }
	private static Path profilesFile() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-profiles.json"); }
	private static Path sessionsFile() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-sessions.json"); }

	/** Reads a store through the durable layer; an unreadable store (file and backup) blocks switching instead of starting empty. */
	private static String readStore(Path file, String what) {
		CwDurable.Loaded read = CwDurable.read(file);
		if (read.source() == CwDurable.Source.UNREADABLE) {
			storeProblem = what + " не читается (" + read.note() + "); повреждённый файл сохранён рядом как *.corrupt-*";
			AdminControlMod.LOGGER.error("[CW] {}", storeProblem);
			return null;
		}
		return read.present() ? read.json() : null;
	}

	private static synchronized void loadAdvancements() {
		if (advancementsLoaded) return;
		Path file = advancementsFile();
		if (file == null) return;
		advancementsLoaded = true;
		String json = readStore(file, "достижения Люмиры");
		if (json == null) return;
		try { Map<String, String> read = GSON.fromJson(json, STRING_MAP); if (read != null) STORY_ADVANCEMENTS.putAll(read); }
		catch (Exception e) { storeProblem = "достижения Люмиры: " + e.getMessage(); AdminControlMod.LOGGER.error("Не удалось прочитать достижения Люмиры", e); }
	}

	private static synchronized boolean saveAdvancements() { return storeProblem == null && write(advancementsFile(), GSON.toJson(STORY_ADVANCEMENTS), "достижения Люмиры"); }

	/** Stores the player's current (story) advancements under the session's story key. */
	private static void storeStoryAdvancements(ServerPlayerEntity player, Session session) {
		loadAdvancements();
		String json = CwWorldState.captureAdvancements(player);
		STORY_ADVANCEMENTS.put(session.storyKey, json == null ? "{}" : json);
		saveAdvancements();
	}

	private static String profileKey(ServerPlayerEntity p, CwInstanceRegistry.Type type, String story) {
		return p.getUuidAsString() + "/" + type.name().toLowerCase(Locale.ROOT) + "/" + (story == null ? "default" : story);
	}

	private static synchronized void loadProfiles() {
		if (loaded) return;
		Path file = profilesFile();
		if (file == null) return;
		loaded = true;
		String json = readStore(file, "сюжетные профили");
		if (json == null) return;
		try { Map<String, String> read = GSON.fromJson(json, STRING_MAP); if (read != null) STORY_PROFILES.putAll(read); }
		catch (Exception e) { storeProblem = "сюжетные профили: " + e.getMessage(); AdminControlMod.LOGGER.error("Не удалось прочитать сюжетные профили", e); }
	}

	private static synchronized void loadSessions() {
		if (sessionsLoaded) return;
		Path file = sessionsFile();
		if (file == null) return;
		sessionsLoaded = true;
		String json = readStore(file, "сюжетные сессии");
		if (json == null) return;
		try {
			Map<String, Session> read = GSON.fromJson(json, new TypeToken<Map<String, Session>>() {}.getType());
			if (read != null) read.forEach((k, v) -> { try { if (v != null) SESSIONS.put(UUID.fromString(k), v); } catch (IllegalArgumentException ignored) { /* skip malformed key */ } });
		} catch (Exception e) { storeProblem = "сюжетные сессии: " + e.getMessage(); AdminControlMod.LOGGER.error("Не удалось прочитать сюжетные сессии", e); }
	}

	private static synchronized boolean saveProfiles() { return storeProblem == null && write(profilesFile(), GSON.toJson(STORY_PROFILES), "сюжетные профили"); }

	private static synchronized boolean saveSessions() {
		if (storeProblem != null) return false;
		Map<String, Session> out = new java.util.LinkedHashMap<>();
		SESSIONS.forEach((k, v) -> out.put(k.toString(), v));
		return write(sessionsFile(), GSON.toJson(out), "сюжетные сессии");
	}

	private static boolean write(Path file, String json, String what) {
		if (file == null) return false;
		boolean ok = CwDurable.write(file, json);
		if (!ok) AdminControlMod.LOGGER.error("Не удалось сохранить {}", what);
		return ok;
	}
}
