package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.SpawnService;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Постоянное void-измерение авторинга (BUILD-профиль игрока) и аварийное восстановление.
 *
 * <p>Профиль NORMAL замораживается в {@code cw-builder-returns.json} (durable-файл: fsync, контрольная сумма, резервная
 * копия) ДО любых изменений игрока. BUILD-профиль живёт в той же записи и обновляется при выходе, отключении,
 * каждую минуту и при остановке сервера. Переход ведёт {@link CwPlayerState}: фаза пишется на диск до изменения мира,
 * повторный вызов идемпотентен, параллельные переходы одного игрока отклоняются.</p>
 */
public final class CwBuilderWorld {
	public static final RegistryKey<World> KEY = RegistryKey.of(RegistryKeys.WORLD,
			new Identifier(AdminControlMod.MOD_ID, "cw_builder"));
	public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, ReturnPoint> RETURNS = new LinkedHashMap<>();
	private static final Map<UUID, Long> INVITES = new ConcurrentHashMap<>();
	private static final long INVITE_MILLIS = 120_000L;
	/** Player state is stored every second; the world chunks are flushed once a minute. */
	private static final int AUTOSAVE_TICKS = 20, WORLD_FLUSH_TICKS = 1200;
	private static boolean loaded;
	/** True when neither the journal nor its backup could be read: nothing may be switched until an administrator looks. */
	private static boolean loadFailed;

	public static final class ReturnPoint {
		public int schemaVersion = 3;
		public String sessionId;
		public String transitionId;
		public String phase = "PREPARING";
		/** NORMAL profile frozen at entry. */
		public String profileNbt;
		/** BUILD profile, kept between sessions. */
		public String builderProfileNbt;
		public String dimension;
		public double x, y, z;
		public float yaw, pitch;
		public String gameMode;
		public boolean agmEnabled;
		public long createdAt;
		public long updatedAt;
		public String failure;
	}

	private CwBuilderWorld() {}

	public static ServerWorld world(MinecraftServer server) { return server == null ? null : server.getWorld(KEY); }
	public static boolean inside(ServerPlayerEntity player) { return player != null && player.getWorld().getRegistryKey().equals(KEY); }
	public static void invite(ServerPlayerEntity player) { if (player != null) INVITES.put(player.getUuid(), System.currentTimeMillis() + INVITE_MILLIS); }
	public static boolean invited(ServerPlayerEntity player) {
		Long until = player == null ? null : INVITES.get(player.getUuid());
		if (until == null) return false;
		if (until < System.currentTimeMillis()) { INVITES.remove(player.getUuid()); return false; }
		return true;
	}
	public static void revoke(ServerPlayerEntity player) { if (player != null) INVITES.remove(player.getUuid()); }
	public static String accept(ServerPlayerEntity player) {
		if (!invited(player)) return "Приглашение на стройку отсутствует или истекло.";
		INVITES.remove(player.getUuid());
		return enter(player);
	}

	public static void prepare(MinecraftServer server) {
		load();
		CwInstanceRegistry.getOrCreate(CwInstanceRegistry.Type.TEMPLATE,null,"builder",1,"authoring","main",
				AdminControlMod.MOD_ID+":cw_builder","dimensions/"+AdminControlMod.MOD_ID+"/cw_builder","persistent authoring template");
		ServerWorld world = world(server);
		if (world == null) {
			AdminControlMod.LOGGER.error("CW builder dimension admin_control:cw_builder не зарегистрировано");
			return;
		}
		world.getChunk(ORIGIN);
		ensureEmergencyPlatform(world);
		AdminControlMod.LOGGER.info("[CW] builder world ready key={} folder={} loadedChunks={}", KEY.getValue(), worldFolder(world), loadedChunks(world));
	}

	// ---- enter ----

	public static String enter(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return "Команда доступна только игроку.";
		load();
		if (loadFailed) return "Журнал стройки повреждён и не восстановлен автоматически. Сообщите администратору: /cw diag.";
		ReturnPoint previous = RETURNS.get(player.getUuidAsString());
		if (previous != null && !"RESTORED".equals(previous.phase)) return "Уже активна сессия " + previous.sessionId + " (" + previous.phase + ").";
		if (inside(player)) return "Вы уже в строительном измерении.";
		ServerWorld target = world(player.getServer());
		if (target == null) return "Строительное измерение не загружено. Проверьте datapack мода.";
		CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.ENTER_BUILD);
		if (!begin.ok()) return begin.error();
		CwPlayerState.Transition tr = begin.transition();
		try {
			return enterLocked(player, target, previous, tr);
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] builder enter crashed transition={} player={}", tr.id, player.getUuidAsString(), e);
			CwPlayerState.leaveInterrupted(tr, "exception " + e);
			return "Вход на стройку не удался (код " + tr.id + "). Состояние сохранено, повторите или обратитесь к администратору.";
		}
	}

	private static String enterLocked(ServerPlayerEntity player, ServerWorld target, ReturnPoint previous, CwPlayerState.Transition tr) {
		ReturnPoint point = new ReturnPoint();
		// A completed record also holds the durable BUILD profile; keep it when a new NORMAL-profile transaction replaces the old return point.
		if (previous != null) point.builderProfileNbt = previous.builderProfileNbt;
		point.sessionId = UUID.randomUUID().toString();
		point.transitionId = tr.id;
		NbtCompound normal = CwProfile.capture(player); // closes the screen first: cursor stack and crafting grid stay in the saved inventory
		point.dimension = player.getServerWorld().getRegistryKey().getValue().toString();
		point.x = player.getX(); point.y = player.getY(); point.z = player.getZ();
		point.yaw = player.getYaw(); point.pitch = player.getPitch();
		point.gameMode = player.interactionManager.getGameMode().getName();
		point.profileNbt = normal.asString();
		point.agmEnabled = DataManager.get() != null && DataManager.get().getOrCreate(player).agmEnabled;
		point.createdAt = System.currentTimeMillis();
		point.updatedAt = point.createdAt;
		synchronized (CwBuilderWorld.class) { RETURNS.put(player.getUuidAsString(), point); }
		if (!save()) {
			synchronized (CwBuilderWorld.class) { if (previous == null) RETURNS.remove(player.getUuidAsString()); else RETURNS.put(player.getUuidAsString(), previous); }
			CwPlayerState.abort(tr, "return point not stored", CwPlayerState.Mode.NORMAL);
			return "Не удалось сохранить точку возврата; вход отменён (код " + tr.id + ").";
		}
		CwPlayerState.step(tr, "NORMAL profile stored: " + CwProfile.describe(normal));
		try {
			NbtCompound build = point.builderProfileNbt == null || point.builderProfileNbt.isBlank() ? null : CwProfile.parse(point.builderProfileNbt);
			if (build != null) CwProfile.apply(player, build); else CwProfile.clean(player);
			if (DataManager.get() != null) { DataManager.get().getOrCreate(player).agmEnabled = true; DataManager.get().markDirty(); }
			target.getChunk(ORIGIN); ensureEmergencyPlatform(target);
			player.changeGameMode(GameMode.CREATIVE);
			double[] at = CwBuilderEntry.resolve(player, target);
			String problem = CwSafeTeleport.move(player, target, at[0], at[1], at[2], (float) at[3], (float) at[4], tr.id);
			if (problem != null) throw new IllegalStateException(problem);
			CwProfile.resync(player);
		} catch (Exception e) {
			String reason = "enter failed: " + e.getMessage();
			AdminControlMod.LOGGER.error("[CW] builder enter failed transition={} player={}", tr.id, player.getUuidAsString(), e);
			point.phase = "RETURN_PENDING"; point.failure = reason; point.updatedAt = System.currentTimeMillis(); save();
			String recovery = restoreProfile(player, point, tr.id);
			if (recovery == null) CwPlayerState.abort(tr, reason, CwPlayerState.Mode.NORMAL); else CwPlayerState.leaveInterrupted(tr, reason + "; " + recovery);
			return recovery == null ? "Вход отменён: " + e.getMessage() + " (код " + tr.id + ")" : "Вход отменён; " + recovery + " (код " + tr.id + ")";
		}
		point.phase = "ACTIVE"; point.updatedAt = System.currentTimeMillis();
		if (!save()) {
			point.phase = "RETURN_PENDING"; point.failure = "ACTIVE не записан";
			String recovery = restoreProfile(player, point, tr.id);
			if (recovery == null) CwPlayerState.abort(tr, "ACTIVE not stored", CwPlayerState.Mode.NORMAL); else CwPlayerState.leaveInterrupted(tr, recovery);
			return "Вход отменён: журнал ACTIVE не записан (код " + tr.id + ").";
		}
		CwPlayerState.commit(tr);
		CwAudit.writeSystem(player.getName().getString(), "builder enter", point.sessionId, "ACTIVE");
		return null;
	}

	// ---- leave ----

	/** Выход со стройки; затем игроку предлагается выбрать, куда вернуться в следующий раз. */
	public static String leave(ServerPlayerEntity player) {
		return leave(player, true);
	}

	public static String leave(ServerPlayerEntity player, boolean askEntry) {
		boolean wasInside = inside(player);
		if (wasInside) CwBuilderEntry.rememberLast(player);
		String error = leaveInternal(player);
		if (error == null && askEntry && wasInside) CwBuilderEntry.openMenu(player);
		return error;
	}

	private static String leaveInternal(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return "Команда доступна только игроку.";
		load();
		if (loadFailed) return "Журнал стройки повреждён и не восстановлен автоматически. Сообщите администратору: /cw diag.";
		ReturnPoint point;
		synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
		if (point == null) {
			CwSafeTeleport.move(player, player.getServer().getOverworld(), player.getServer().getOverworld().getSpawnPos().getX() + .5,
					player.getServer().getOverworld().getSpawnPos().getY(), player.getServer().getOverworld().getSpawnPos().getZ() + .5, 0, 0, "no-record");
			return "Точка входа не найдена: выполнено безопасное возвращение на спавн мира.";
		}
		if ("RESTORED".equals(point.phase) && !inside(player)) return "Сессия уже восстановлена.";
		CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.EXIT_BUILD);
		if (!begin.ok()) return begin.error();
		CwPlayerState.Transition tr = begin.transition();
		try {
			if (inside(player)) { point.builderProfileNbt = CwProfile.capture(player).asString(); flushWorld(world(player.getServer()), true); }
			point.phase = "RETURN_PENDING"; point.transitionId = tr.id; point.updatedAt = System.currentTimeMillis();
			if (!save()) {
				CwPlayerState.abort(tr, "BUILD profile not stored", CwPlayerState.Mode.BUILD);
				return "Не удалось сохранить строительный профиль и фазу RETURN_PENDING; восстановление отложено (код " + tr.id + ").";
			}
			CwPlayerState.step(tr, "BUILD profile stored");
			String error = restoreProfile(player, point, tr.id);
			if (error == null) CwPlayerState.commit(tr); else CwPlayerState.leaveInterrupted(tr, error);
			return error;
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] builder leave crashed transition={} player={}", tr.id, player.getUuidAsString(), e);
			CwPlayerState.leaveInterrupted(tr, "exception " + e);
			return "Выход со стройки не удался (код " + tr.id + "). Профиль сохранён, повторите команду.";
		}
	}

	/**
	 * Puts the NORMAL profile back. The whole profile is replaced (never merged), so repeating this is harmless. If the
	 * NORMAL profile is missing nothing is touched: an empty profile is never handed out in its place.
	 */
	private static String restoreProfile(ServerPlayerEntity player, ReturnPoint point, String transitionId) {
		NbtCompound normal;
		try {
			if (point.profileNbt == null || point.profileNbt.isBlank()) throw new IllegalStateException("NORMAL profile is missing");
			normal = CwProfile.parse(point.profileNbt);
		} catch (Exception e) {
			point.phase = "RETURN_PENDING"; point.failure = "NORMAL profile unreadable: " + e.getMessage(); point.updatedAt = System.currentTimeMillis(); save();
			AdminControlMod.LOGGER.error("[CW] NORMAL profile unreadable transition={} player={}", transitionId, player.getUuidAsString(), e);
			return "Основной профиль не читается; игрок не изменён, данные сохранены для recovery (код " + transitionId + ").";
		}
		NbtCompound builderBefore = inside(player) ? CwProfile.capture(player) : null;
		try {
			CwProfile.apply(player, normal);
			ServerWorld world = SpawnService.resolveWorld(player.getServer(), point.dimension);
			String problem = CwSafeTeleport.move(player, world, point.x, point.y, point.z, point.yaw, point.pitch, transitionId);
			if (problem != null) throw new IllegalStateException(problem);
			GameMode mode = GameMode.byName(point.gameMode, GameMode.SURVIVAL); player.changeGameMode(mode);
			CwProfile.applyAbilities(player, normal);
			if (DataManager.get() != null) { DataManager.get().getOrCreate(player).agmEnabled = point.agmEnabled; DataManager.get().markDirty(); }
			CwProfile.resync(player);
			ru.admincontrol.util.AuthService.rebase(player);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] NORMAL restore failed transition={} player={}", transitionId, player.getUuidAsString(), e);
			point.phase = "RETURN_PENDING"; point.failure = e.getMessage(); point.updatedAt = System.currentTimeMillis(); save();
			// Back to the BUILD profile if the player is still on the build world: no hybrid of two profiles.
			if (builderBefore != null && inside(player)) {
				try { CwProfile.apply(player, builderBefore); player.changeGameMode(GameMode.CREATIVE); CwProfile.resync(player); }
				catch (Exception rollback) { AdminControlMod.LOGGER.error("[CW] BUILD rollback failed transition={} player={}", transitionId, player.getUuidAsString(), rollback); }
			}
			return "Профиль сохранён для recovery: " + point.failure + " (код " + transitionId + ")";
		}
		point.phase = "RESTORED"; point.failure = null; point.updatedAt = System.currentTimeMillis();
		if (!save()) return "Профиль восстановлен, но подтверждение RESTORED не записано; повторное восстановление заблокировано до проверки журнала.";
		CwAudit.writeSystem(player.getName().getString(), "builder restore", point.sessionId, "RESTORED");
		return null;
	}

	// ---- join / disconnect / respawn ----

	/**
	 * On join, before the player can act: finish or roll back an unfinished builder session. A player who was found in
	 * the builder world has their current (vanilla-saved) state stored as the BUILD profile first, so nothing built or
	 * collected before a crash is lost, and only then is the NORMAL profile restored.
	 */
	public static void recoverOnJoin(ServerPlayerEntity player) {
		load();
		if (loadFailed || player == null || player.getServer() == null) return;
		// A story session may deliberately sit on top of an ACTIVE BUILD session (admin test from the authoring world).
		// STORY is the outer/owning profile until it exits; touching the builder return point here would restore NORMAL
		// over STORY and issue a second dimension change during the same login.
		if (CwStoryWorldService.hasSession(player.getUuid())) {
			AdminControlMod.LOGGER.info("[CW] builder join recovery deferred to active story session player={} world={}",
					player.getUuidAsString(), player.getServerWorld().getRegistryKey().getValue());
			return;
		}
		ReturnPoint point;
		synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
		if (point != null && !"RESTORED".equals(point.phase)) {
			CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.EXIT_BUILD);
			if (!begin.ok()) return;
			CwPlayerState.Transition tr = begin.transition();
			if (inside(player)) point.builderProfileNbt = CwProfile.capture(player).asString();
			String error = restoreProfile(player, point, tr.id);
			if (error == null) CwPlayerState.commit(tr);
			else { CwPlayerState.leaveInterrupted(tr, error); player.sendMessage(net.minecraft.text.Text.literal("[CW] " + error), false); }
		} else if (point == null && inside(player)) {
			// В строительном мире без записи о входе (старый сбой): безопасно на спавн основного мира.
			AdminControlMod.LOGGER.warn("[CW] player found in builder world without a record; moving to main world spawn player={}", player.getUuidAsString());
			ServerWorld overworld = player.getServer().getOverworld();
			CwSafeTeleport.move(player, overworld, overworld.getSpawnPos().getX() + .5, overworld.getSpawnPos().getY(), overworld.getSpawnPos().getZ() + .5, 0, 0, "orphan");
			ru.admincontrol.util.AuthService.rebase(player);
		} else if (CwPlayerState.interrupted(player.getUuid()) && point != null && "RESTORED".equals(point.phase) && !CwStoryWorldService.hasSession(player.getUuid())) {
			CwPlayerState.force(player.getUuid(), CwPlayerState.Mode.NORMAL, "journal says RESTORED");
		}
	}

	public static synchronized Map<String, ReturnPoint> sessions() { load(); return Map.copyOf(RETURNS); }

	public static void onDisconnect(ServerPlayerEntity player) {
		load();
		ReturnPoint point;
		synchronized (CwBuilderWorld.class) { point = player == null ? null : RETURNS.get(player.getUuidAsString()); }
		if (point == null || "RESTORED".equals(point.phase)) return;
		// Only a player who really stands in the builder world has a BUILD profile to store; anything else would copy the NORMAL profile into BUILD.
		if (inside(player)) {
			NbtCompound builder = new NbtCompound(); player.writeNbt(builder); point.builderProfileNbt = builder.asString();
			flushWorld(world(player.getServer()), true);
		}
		point.phase = "RETURN_PENDING"; point.failure = "disconnect: main profile will be restored on next join";
		point.updatedAt = System.currentTimeMillis(); save();
		CwPlayerState.release(player.getUuid(), "disconnect");
		CwAudit.writeSystem(player.getName().getString(), "builder disconnect", point.sessionId, "RETURN_PENDING");
	}

	/** Saves the BUILD profile of everyone currently building and flushes the build world (crash safety, once a minute). */
	public static void autosave(MinecraftServer server) {
		if (server == null || server.getTicks() % AUTOSAVE_TICKS != 0) return;
		load();
		if (loadFailed) return;
		boolean any = false, changed = false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!inside(player) || CwPlayerState.busy(player.getUuid())) continue;
			any = true;
			ReturnPoint point;
			synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
			if (point == null || !"ACTIVE".equals(point.phase)) continue;
			NbtCompound builder = new NbtCompound(); player.writeNbt(builder);
			point.builderProfileNbt = builder.asString(); point.updatedAt = System.currentTimeMillis(); changed = true;
		}
		if (changed) saveAsync();
		if (any && server.getTicks() % WORLD_FLUSH_TICKS == 0) flushWorld(world(server), false);
	}

	public static void flush() { load(); if (!loadFailed) save(); }

	/** Server stop: BUILD profiles of everyone still inside are stored before the players are disconnected, then the world is written out. */
	public static void flush(MinecraftServer server) {
		load();
		if (server != null && !loadFailed) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (!inside(player)) continue;
				ReturnPoint point;
				synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
				if (point == null || "RESTORED".equals(point.phase)) continue;
				NbtCompound builder = new NbtCompound(); player.writeNbt(builder); point.builderProfileNbt = builder.asString();
				point.updatedAt = System.currentTimeMillis();
			}
			flushWorld(world(server), true);
		}
		flush();
	}

	public static String forceRecover(ServerPlayerEntity player) {
		if (player == null) return "Игрок должен быть онлайн.";
		load();
		ReturnPoint point;
		synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
		if (point == null) return "Сессия игрока не найдена.";
		if ("RESTORED".equals(point.phase)) { CwPlayerState.force(player.getUuid(), CwPlayerState.Mode.NORMAL, "recover: already restored"); return "Сессия уже восстановлена."; }
		CwPlayerState.force(player.getUuid(), CwPlayerState.Mode.BUILD, "recover requested");
		CwPlayerState.Begin begin = CwPlayerState.begin(player.getUuid(), player.getName().getString(), CwPlayerState.Kind.EXIT_BUILD);
		if (!begin.ok()) return begin.error();
		if (inside(player)) point.builderProfileNbt = CwProfile.capture(player).asString();
		String error = restoreProfile(player, point, begin.transition().id);
		if (error == null) CwPlayerState.commit(begin.transition()); else CwPlayerState.leaveInterrupted(begin.transition(), error);
		return error;
	}

	public static void resumeAfterRespawn(ServerPlayerEntity player) {
		load();
		ReturnPoint point;
		synchronized (CwBuilderWorld.class) { point = RETURNS.get(player.getUuidAsString()); }
		if (point == null || "RESTORED".equals(point.phase) || player.getServer() == null) return;
		ServerWorld target = world(player.getServer());
		if (target == null) { point.phase = "RETURN_PENDING"; point.failure = "builder world unavailable after death"; save(); return; }
		CwProfile.clean(player); // a respawned body carries nothing from the NORMAL profile
		player.changeGameMode(GameMode.CREATIVE);
		if (DataManager.get() != null) DataManager.get().getOrCreate(player).agmEnabled = true;
		String problem = CwSafeTeleport.move(player, target, 0.5, 65.0, 0.5, 0f, 0f, "respawn");
		if (problem != null) { point.failure = "respawn teleport: " + problem; point.updatedAt = System.currentTimeMillis(); save(); return; }
		point.phase = "ACTIVE"; point.builderProfileNbt = CwProfile.capture(player).asString(); point.updatedAt = System.currentTimeMillis(); save();
		CwProfile.resync(player);
	}

	// ---- diagnostics ----

	/** Human-readable state for /cw diag. */
	public static java.util.List<String> diagnostics(UUID id, MinecraftServer server) {
		load();
		java.util.List<String> out = new java.util.ArrayList<>();
		out.add("builder journal: " + (file() == null ? "n/a" : file()) + (loadFailed ? " [НЕ ЧИТАЕТСЯ]" : ""));
		ServerWorld w = world(server);
		out.add("builder dimension: " + KEY.getValue() + " folder=" + (w == null ? "n/a" : worldFolder(w)) + " loadedChunks=" + (w == null ? 0 : loadedChunks(w)));
		ReturnPoint p;
		synchronized (CwBuilderWorld.class) { p = id == null ? null : RETURNS.get(id.toString()); }
		if (p == null) out.add("builder record: нет");
		else out.add("builder record: phase=" + p.phase + " session=" + p.sessionId + " transition=" + p.transitionId + " normalDim=" + p.dimension
				+ String.format(java.util.Locale.ROOT, " normalPos=%.1f %.1f %.1f", p.x, p.y, p.z) + " normalProfile=" + (p.profileNbt == null ? "НЕТ" : p.profileNbt.length() + " символов")
				+ " buildProfile=" + (p.builderProfileNbt == null ? "нет" : p.builderProfileNbt.length() + " символов") + (p.failure == null ? "" : " failure=" + p.failure));
		return out;
	}

	public static String worldFolder(ServerWorld world) {
		try { return net.minecraft.world.dimension.DimensionType.getSaveDirectory(world.getRegistryKey(), world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)).toAbsolutePath().toString(); }
		catch (Exception e) { return "n/a"; }
	}

	public static int loadedChunks(ServerWorld world) { return world.getChunkManager().getLoadedChunkCount(); }

	private static void flushWorld(ServerWorld world, boolean flush) {
		if (world == null) return;
		try { world.getChunkManager().save(flush); }
		catch (RuntimeException e) { AdminControlMod.LOGGER.error("[CW] builder world flush failed", e); }
	}

	// ---- persistence ----

	private static Path file() {
		return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-builder-returns.json");
	}
	private static void ensureEmergencyPlatform(ServerWorld world) {
		if (!world.getBlockState(ORIGIN).isAir()) return;
		for (int x=-64;x<=64;x++) for(int z=-64;z<=64;z++) {
			int top=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
			BlockPos floor=new BlockPos(x,top-1,z);
			if(top>world.getBottomY()&&!world.getBlockState(floor).isAir()&&world.getBlockState(floor).isSolidBlock(world,floor)) return;
		}
		world.setBlockState(ORIGIN, Blocks.BEDROCK.getDefaultState(), 3);
	}
	private static synchronized void load() {
		if (loaded) return;
		Path file = file(); if (file == null) return;
		loaded = true;
		CwDurable.Loaded read = CwDurable.read(file);
		if (read.source() == CwDurable.Source.UNREADABLE) { loadFailed = true; AdminControlMod.LOGGER.error("[CW] cw-builder-returns.json и резервная копия не читаются ({}); переходы заблокированы до ручного восстановления", read.note()); return; }
		if (!read.present()) return;
		try {
			Map<String, ReturnPoint> parsed = GSON.fromJson(read.json(), new com.google.gson.reflect.TypeToken<Map<String, ReturnPoint>>(){}.getType());
			if (parsed != null) RETURNS.putAll(parsed);
			if (read.source() == CwDurable.Source.BACKUP) AdminControlMod.LOGGER.warn("[CW] builder journal restored from backup");
		} catch (Exception e) { loadFailed = true; AdminControlMod.LOGGER.error("Не удалось разобрать возвраты CW builder", e); }
	}
	private static synchronized void saveAsync() {
		Path file = file(); if (file == null || loadFailed) return;
		CwDurable.writeAsync(file, GSON.toJson(RETURNS));
	}

	private static synchronized boolean save() {
		Path file = file(); if (file == null || loadFailed) return false;
		return CwDurable.write(file, GSON.toJson(RETURNS));
	}
}
