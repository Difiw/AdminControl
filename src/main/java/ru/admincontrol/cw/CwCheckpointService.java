package ru.admincontrol.cw;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two independent story save slots per player, both bound to the player's own story instance:
 * <ul>
 *   <li>AUTO ({@code Progress.rollingCheckpoint}) is written only by the scheduler, every {@link #INTERVAL} ticks of
 *   <em>active story time</em> (time the player really spends inside their story world; paused story time and time
 *   in the main world do not count). The deadline advances from the previous deadline, so a manual save, a death or a
 *   lag spike never moves it, and a long gap never produces a burst of catch-up saves.</li>
 *   <li>MANUAL ({@code Progress.respawnPoint}) is written only by the story tokens after server-side checks.</li>
 * </ul>
 * Everything here runs on the server thread, which serialises auto/manual/death/restore operations.
 */
public final class CwCheckpointService {
	public static final long INTERVAL = 20L * 60L * 7L;
	public static final String REASON_MISSING = "missing", REASON_CORRUPT = "corrupt", REASON_FOREIGN = "foreign", REASON_WORLD = "world";
	private static final long MANUAL_COOLDOWN_MS = 3000L, REPLACE_CONFIRM_MS = 10_000L, WARN_EVERY_MS = 300_000L;
	private static final double MIN_Y = -64, MAX_Y = 320;
	private static final Map<UUID, Long> MANUAL_COOLDOWN = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> RESPAWN_CONFIRM = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();
	/** Players who just entered the story and still need their first AUTO snapshot (taken on the next safe tick). */
	private static final java.util.Set<UUID> ENTRY_SAVE = ConcurrentHashMap.newKeySet();

	public enum Slot { AUTO, MANUAL }
	/** What the return screen shows for one slot. {@code reason} is a REASON_* code when unavailable. */
	public record Status(boolean available, long ageSeconds, String detail, String reason) {}

	private CwCheckpointService() {}

	static boolean autosaveDue(long activeTicks, long nextAutosaveActiveTick) { return activeTicks >= nextAutosaveActiveTick; }
	static long nextDeadline(long previousDeadline) { return previousDeadline + INTERVAL; }
	/** Next deadline after an autosave: measured from the previous deadline, but never in the past (no catch-up burst). */
	static long rescheduled(long previousDeadline, long activeTicks) {
		long next = nextDeadline(previousDeadline);
		return next <= activeTicks ? activeTicks + INTERVAL : next;
	}

	public static void tick(MinecraftServer server) {
		if (server == null) return;
		boolean changed = false;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!CwStoryRules.inside(p) || !p.isAlive() || p.isSpectator()) continue;
			CwStoryService.Progress q = CwStoryService.session(p);
			if (ENTRY_SAVE.contains(p.getUuid())) {
				// First save right on story entry; the seven-minute timer starts from this moment.
				if (hazard(p)) continue;
				CwStoryService.Snapshot first = capture(p, Slot.AUTO);
				if (first == null) { warnRateLimited(p, "entry snapshot skipped: no active story instance"); continue; }
				if (!store(p, Slot.AUTO, first)) { warnRateLimited(p, "entry snapshot could not be written to its file"); continue; }
				q.nextAutosaveActiveTick = q.activeTicks + INTERVAL;
				ENTRY_SAVE.remove(p.getUuid());
				changed = true;
				AdminControlMod.LOGGER.info("[CW] entry AUTO snapshot saved player={} story={}", p.getUuidAsString(), first.storyId);
				continue;
			}
			if (q.paused || !autosaveDue(q.activeTicks, q.nextAutosaveActiveTick)) continue;
			if (hazard(p)) continue; // due but not now: never freeze a lethal spot as a restore point
			CwStoryService.Snapshot snapshot = capture(p, Slot.AUTO);
			if (snapshot == null) { warnRateLimited(p, "autosave skipped: no active story instance"); continue; }
			if (!store(p, Slot.AUTO, snapshot)) { warnRateLimited(p, "autosave could not be written to its file"); continue; }
			q.nextAutosaveActiveTick = rescheduled(q.nextAutosaveActiveTick, q.activeTicks);
			changed = true;
			AdminControlMod.LOGGER.info("[CW] AUTO snapshot saved player={} story={}", p.getUuidAsString(), snapshot.storyId);
		}
		if (changed) CwStoryService.save();
	}

	/** "Save" token: writes the MANUAL slot only and never touches the AUTO schedule. */
	public static String manual(ServerPlayerEntity p) { return setRespawn(p); }

	/** "Respawn point" token: same MANUAL slot, with a replace confirmation when a point already exists. */
	public static String setRespawn(ServerPlayerEntity p) {
		if (!CwStoryRules.inside(p)) return "Точку можно задать только в Люмире.";
		if (!p.isAlive() || CwRespawnService.pending(p)) return "Сейчас сохранение недоступно.";
		long now = System.currentTimeMillis();
		if (now - MANUAL_COOLDOWN.getOrDefault(p.getUuid(), 0L) < MANUAL_COOLDOWN_MS) return "Сохранение уже выполнено; подождите.";
		String unsafe = unsafeSpot(p);
		if (unsafe != null) return unsafe;
		CwStoryService.Progress q = CwStoryService.session(p);
		if (slotSnapshot(p, Slot.MANUAL) != null && now - RESPAWN_CONFIRM.getOrDefault(p.getUuid(), 0L) > REPLACE_CONFIRM_MS) {
			RESPAWN_CONFIRM.put(p.getUuid(), now);
			return "Повторите использование в течение 10 секунд, чтобы заменить точку.";
		}
		CwStoryService.Snapshot snapshot = capture(p, Slot.MANUAL);
		if (snapshot == null) return "Ваш мир в Люмире не определён.";
		RESPAWN_CONFIRM.remove(p.getUuid());
		MANUAL_COOLDOWN.put(p.getUuid(), now);
		if (!store(p, Slot.MANUAL, snapshot)) return "Не удалось записать файл сохранения.";
		CwStoryService.touch(q, "manual-point");
		CwStoryService.save();
		AdminControlMod.LOGGER.info("[CW] MANUAL snapshot saved player={} story={}", p.getUuidAsString(), snapshot.storyId);
		return null;
	}

	/** Complete snapshot of the player's story profile, or null if the player has no valid story instance. */
	public static CwStoryService.Snapshot capture(ServerPlayerEntity p, Slot slot) {
		CwInstanceRegistry.Instance instance = CwStoryWorldService.instanceOf(p);
		String world = p.getWorld().getRegistryKey().getValue().toString();
		if (instance == null || !world.equals(instance.worldKey) || !p.isAlive()) return null;
		CwStoryService.Progress q = CwStoryService.session(p);
		NbtCompound nbt = new NbtCompound();
		p.writeNbt(nbt);
		CwStoryService.Snapshot s = new CwStoryService.Snapshot();
		s.kind = slot.name().toLowerCase(java.util.Locale.ROOT);
		s.ownerUuid = p.getUuidAsString();
		s.storyId = instance.storyId;
		s.instanceId = instance.instanceId;
		s.createdAt = System.currentTimeMillis();
		s.description = q.chapter > 0 ? "Глава " + q.chapter : "Начало пути";
		s.profileNbt = nbt.asString();
		s.world = world;
		s.x = p.getX(); s.y = p.getY(); s.z = p.getZ(); s.yaw = p.getYaw(); s.pitch = p.getPitch();
		s.stageId = q.stageId; s.sceneId = q.sceneId; s.storyTime = q.storyTime;
		s.progressJson = CwStoryService.progressJson(q);
		if (p.getWorld() instanceof ServerWorld sw) {
			s.entities = CwWorldState.captureEntities(sw);
			CwStoryWorldService.Session session = CwStoryWorldService.session(p.getUuid());
			if (session != null) s.blocks = CwWorldState.captureBlocks(sw, session.bounds);
		}
		s.advancementsJson = CwWorldState.captureAdvancements(p);
		if (p.getWorld() instanceof ServerWorld fileWorld) {
			// full file checkpoint of the whole personal world; the logical block/entity capture above stays as a fallback
			s.worldBackupId = CwWorldBackupService.snapshot(p.getServer(), fileWorld, p.getUuid(), instance.instanceId, slot.name().toLowerCase(java.util.Locale.ROOT));
		}
		return s;
	}

	/**
	 * The saved snapshot of a slot, always read back from its file. A save that only exists in the old in-memory
	 * fields (written by a previous version) is moved into a file first.
	 */
	public static CwStoryService.Snapshot slotSnapshot(ServerPlayerEntity p, Slot slot) {
		String name = slot == Slot.AUTO ? CwSaveStore.AUTO : CwSaveStore.MANUAL;
		CwStoryService.Snapshot fromFile = CwSaveStore.read(p, name);
		if (fromFile != null) return fromFile;
		CwStoryService.Progress q = CwStoryService.session(p);
		CwStoryService.Snapshot legacy = slot == Slot.AUTO ? q.rollingCheckpoint : q.respawnPoint;
		if (legacy != null && CwSaveStore.write(p, name, legacy)) { if (slot == Slot.AUTO) q.rollingCheckpoint = null; else q.respawnPoint = null; CwStoryService.save(); }
		return legacy;
	}

	/** Writes the snapshot to its file; only a successfully written save counts as saved. */
	private static boolean store(ServerPlayerEntity p, Slot slot, CwStoryService.Snapshot snapshot) {
		if (!CwSaveStore.write(p, slot == Slot.AUTO ? CwSaveStore.AUTO : CwSaveStore.MANUAL, snapshot)) return false;
		CwStoryService.Progress q = CwStoryService.session(p);
		if (slot == Slot.AUTO) q.rollingCheckpoint = null; else q.respawnPoint = null;
		return true;
	}

	public static Status status(ServerPlayerEntity p, Slot slot) {
		CwStoryService.Progress q = CwStoryService.session(p);
		CwStoryService.Snapshot s = slotSnapshot(p, slot);
		String reason = problem(p, s);
		if (reason != null) return new Status(false, 0, "", reason);
		return new Status(true, Math.max(0L, (System.currentTimeMillis() - s.createdAt) / 1000L), s.description == null ? "" : s.description, "");
	}

	/** Null when the snapshot belongs to this player's current story instance and can be restored; otherwise a REASON_* code. */
	public static String problem(ServerPlayerEntity p, CwStoryService.Snapshot s) {
		CwInstanceRegistry.Instance instance = CwStoryWorldService.instanceOf(p);
		String reason = ownershipProblem(p.getUuid(), s, instance == null ? null : instance.worldKey,
				instance == null ? null : instance.instanceId, instance == null ? null : instance.storyId);
		if (reason != null) return reason;
		Identifier id = Identifier.tryParse(instance.worldKey);
		if (p.getServer() == null || id == null || p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id)) == null) return REASON_WORLD;
		try { StringNbtReader.parse(s.profileNbt); }
		catch (Exception e) { return REASON_CORRUPT; }
		return null;
	}

	/** Pure ownership/integrity check (no Minecraft state), so it can be unit-tested. */
	static String ownershipProblem(UUID player, CwStoryService.Snapshot s, String instanceWorld, String instanceId, String instanceStory) {
		if (s == null) return REASON_MISSING;
		if (s.profileNbt == null || s.profileNbt.isBlank() || s.world == null || !CwStoryRules.isStoryWorldId(s.world)
				|| !finite(s.x) || !finite(s.y) || !finite(s.z) || s.y < MIN_Y || s.y > MAX_Y) return REASON_CORRUPT;
		if (s.ownerUuid != null && !s.ownerUuid.equals(player.toString())) return REASON_FOREIGN;
		if (instanceWorld == null) return REASON_WORLD;
		// A save made in an earlier world slot or instance of the same player is still theirs: it is loaded into the current one.
		return null;
	}

	private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

	/**
	 * Restores exactly the given snapshot. All validation and NBT parsing happen before the player is touched, so a bad
	 * snapshot can never leave a half-applied player (position of one profile, inventory of another).
	 */
	public static String restore(ServerPlayerEntity p, CwStoryService.Snapshot s) {
		if (p == null || p.getServer() == null) return "Игрок недоступен.";
		String reason = problem(p, s);
		if (reason != null) return reasonText(reason);
		CwInstanceRegistry.Instance instanceNow = CwStoryWorldService.instanceOf(p);
		if (s.worldBackupId != null && instanceNow != null && CwWorldBackupService.exists(p.getServer(), p.getUuid(), instanceNow.instanceId, s.worldBackupId)) {
			if (CwWorldBackupService.busy(p.getUuid())) return "Загрузка уже выполняется.";
			// Whole-world load runs over several ticks: the world is emptied, its files are replaced, then the profile is applied.
			CwWorldBackupService.restore(p, s, error -> {
				if (error != null) AdminControlMod.LOGGER.warn("[CW] file restore refused player={}: {}; falling back to the logical restore", p.getUuidAsString(), error);
				String problem = restoreProfile(p, s, error != null);
				if (problem != null) p.sendMessage(net.minecraft.text.Text.literal(problem), false);
				else if (error != null) p.sendMessage(net.minecraft.text.Text.literal("[CW] Файлы мира не удалось заменить (" + error + "); загружены позиция, предметы и постройки точки."), false);
			});
			return null;
		}
		return restoreProfile(p, s, true);
	}

	private static String restoreProfile(ServerPlayerEntity p, CwStoryService.Snapshot s, boolean logicalWorld) {
		if (p == null || p.getServer() == null) return "Игрок недоступен.";
		NbtCompound parsed;
		ServerWorld world;
		try {
			parsed = StringNbtReader.parse(s.profileNbt);
			CwInstanceRegistry.Instance current = CwStoryWorldService.instanceOf(p);
			world = current == null ? null : p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(current.worldKey)));
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] snapshot parse failed player={}", p.getUuidAsString(), e);
			return reasonText(REASON_CORRUPT);
		}
		if (world == null) return reasonText(REASON_WORLD);
		NbtCompound backup = CwProfile.capture(p); // closes the screen first, so a held item is part of the rollback copy
		try {
			world.getChunk(BlockPos.ofFloored(s.x, s.y, s.z)); // load the target chunk before the player arrives
			CwProfile.apply(p, parsed); // the whole profile is replaced: ender chest, effects and riding state of the old state cannot leak into the save
			String moved = CwSafeTeleport.move(p, world, s.x, s.y, s.z, s.yaw, s.pitch, "restore");
			if (moved != null) throw new IllegalStateException(moved);
			CwWorldState.restoreAdvancements(p, s.advancementsJson);
			if (logicalWorld && s.blocks != null) CwWorldState.restoreBlocks(world, s.blocks);
			if (logicalWorld && s.entities != null) CwWorldState.restoreEntities(world, s.entities);
			p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
			p.setVelocity(0, 0, 0);
			p.velocityModified = true;
			p.fallDistance = 0;
			p.extinguish();
			if (p.getHealth() < 1.0F) p.setHealth(1.0F);
			CwStoryWorldService.resync(p);
			CwStoryService.Progress q = CwStoryService.session(p);
			q.stageId = s.stageId; q.sceneId = s.sceneId; q.storyTime = s.storyTime;
			CwStoryService.applyProgress(q, s.progressJson); // chapter, flags, choices, quests, NPC and world state of the save
			CwStoryService.touch(q, "restore:" + s.kind);
			CwStoryService.save();
			CwStoryClock.sync(p, q);
			bindSpawnAnchor(p);
			return null;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] snapshot restore failed player={} kind={}", p.getUuidAsString(), s.kind, e);
			try { CwProfile.apply(p, backup); CwStoryWorldService.resync(p); }
			catch (Exception rollback) { AdminControlMod.LOGGER.error("[CW] restore rollback failed player={}", p.getUuidAsString(), rollback); }
			return "Не удалось загрузить точку. Попробуйте другую.";
		}
	}

	/**
	 * Makes a safe place inside the player's own story world the vanilla respawn point (forced), so that dying in the
	 * story never routes through the overworld. Order: manual point, auto point, story entry point, current position.
	 */
	public static void bindSpawnAnchor(ServerPlayerEntity p) {
		if (p == null || !CwStoryRules.inside(p) || !(p.getWorld() instanceof ServerWorld world)) return;
		CwStoryService.Progress q = CwStoryService.session(p);
		String key = world.getRegistryKey().getValue().toString();
		BlockPos pos = null;
		float yaw = p.getYaw();
		for (CwStoryService.Snapshot s : Arrays.asList(slotSnapshot(p, Slot.MANUAL), slotSnapshot(p, Slot.AUTO))) {
			if (s == null || problem(p, s) != null) continue;
			BlockPos candidate = BlockPos.ofFloored(s.x, s.y, s.z);
			if (roomFor(world, candidate)) { pos = candidate; yaw = s.yaw; break; }
		}
		if (pos == null) {
			CwStoryWorldService.Session session = CwStoryWorldService.session(p.getUuid());
			if (session != null && key.equals(session.instanceWorld)) {
				BlockPos entry = BlockPos.ofFloored(session.entryX, session.entryY, session.entryZ);
				if (roomFor(world, entry)) { pos = entry; yaw = session.entryYaw; }
			}
		}
		if (pos == null && roomFor(world, p.getBlockPos())) pos = p.getBlockPos();
		if (pos != null) p.setSpawnPoint(world.getRegistryKey(), pos, yaw, true, false);
	}

	/** Same test vanilla applies to a forced spawn point: feet and head block must not be solid. */
	private static boolean roomFor(ServerWorld world, BlockPos pos) {
		BlockState feet = world.getBlockState(pos), head = world.getBlockState(pos.up());
		return feet.getBlock().canMobSpawnInside(feet) && head.getBlock().canMobSpawnInside(head);
	}

	/** Manual points only on a solid floor with free space and no fluid. */
	private static String unsafeSpot(ServerPlayerEntity p) {
		World w = p.getWorld();
		BlockPos pos = p.getBlockPos();
		String unsafe = "Это место небезопасно.";
		if (!p.isOnGround() || p.isInsideWall()) return unsafe;
		if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) return unsafe;
		if (!w.getBlockState(pos).getCollisionShape(w, pos).isEmpty() || !w.getBlockState(pos.up()).getCollisionShape(w, pos.up()).isEmpty()) return unsafe;
		if (!w.getFluidState(pos).isEmpty()) return unsafe;
		return null;
	}

	/** Auto points are only deferred (never refused) while the player is in an immediately dangerous spot. */
	private static boolean hazard(ServerPlayerEntity p) {
		World w = p.getWorld();
		return p.isOnFire() || p.getY() < w.getBottomY() + 2 || p.isInsideWall() || w.getFluidState(p.getBlockPos()).isIn(FluidTags.LAVA);
	}

	public static String reasonText(String reason) {
		return switch (reason) {
			case REASON_MISSING -> "Точка ещё не создана.";
			case REASON_FOREIGN -> "Точка относится к другой ветке Люмиры.";
			case REASON_WORLD -> "Эта часть Люмиры сейчас недоступна.";
			default -> "Точка повреждена.";
		};
	}

	private static void warnRateLimited(ServerPlayerEntity p, String message) {
		long now = System.currentTimeMillis();
		if (now - LAST_WARN.getOrDefault(p.getUuid(), 0L) < WARN_EVERY_MS) return;
		LAST_WARN.put(p.getUuid(), now);
		AdminControlMod.LOGGER.warn("[CW] {} player={}", message, p.getUuidAsString());
	}

	/** Called when a player has just entered their story world: the first AUTO snapshot is taken immediately. */
	public static void requestEntrySave(UUID id) { if (id != null) ENTRY_SAVE.add(id); }

	public static void clear(UUID id) { ENTRY_SAVE.remove(id); MANUAL_COOLDOWN.remove(id); RESPAWN_CONFIRM.remove(id); LAST_WARN.remove(id); }
}
