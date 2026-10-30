package ru.admincontrol.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainingService {
	private static final long MOB_EVERY_MS = 5000L;
	private static final int MOBS_PER_WAVE = 3;
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private TrainingService() {
	}

	public static final class Session {
		public UUID uuid;
		public String type;
		public int minutes;
		public long endsAt;
		public String inventoryNbt = "";
		public String originDim = "";
		public double ox;
		public double oy;
		public double oz;
		public float yaw;
		public float pitch;
		public float health = 20f;
		public int food = 20;
		public float saturation = 5f;
		public TrainingArena arena = new TrainingArena();
		public boolean reward;
		public long nextMobAt;
		public GameMode gameMode = GameMode.SURVIVAL;
		public ServerBossBar bar;
		public String lastBar = "";
		public float lastBarPct = -1f;
		/** Матч с ботами (null — обычная тренировка с мобами). */
		public BotMatch match;
		public int botCoins;
		public int botPowerTenths;
	}

	/** Параметры тренировки с ботами из меню. */
	public record BotSetup(int mode, int difficulty, int allies, int enemies) {}

	public static boolean active(ServerPlayerEntity player) {
		return player != null && SESSIONS.containsKey(player.getUuid());
	}

	public static boolean blockedBreak(ServerPlayerEntity player, BlockPos pos) {
		Session session = player == null ? null : SESSIONS.get(player.getUuid());
		return session != null && session.arena != null && session.arena.isShell(pos);
	}

	private static final Map<UUID, BlockPos> PRESELECTED = new ConcurrentHashMap<>();

	/** Арена выбирается заранее, чтобы чанки успели прогрузиться за 5 секунд ожидания. */
	private static void preselect(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		BlockPos stand = chooseArenaPosition(world);
		PRESELECTED.put(player.getUuid(), stand);
		ChunkPrewarm.hold(world, stand.getX() + TrainingArena.SIZE / 2.0, stand.getZ() + TrainingArena.SIZE / 2.0, 2);
	}

	private static boolean precheck(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (player.getServer() != null && player.getWorld() != player.getServer().getOverworld()) {
			Chat.send(player, Chat.red("Тренировка доступна только в обычном мире."));
			return false;
		}
		if (active(player)) {
			Chat.send(player, Chat.red("Вы уже на тренировке."));
			return false;
		}
		if (EventService.member(player) || EventService.isHost(player)) {
			Chat.send(player, Chat.red("Нельзя зайти на тренировку во время мероприятия."));
			return false;
		}
		if (EventMapEditor.participant(player)) {
			Chat.send(player, Chat.red("Сначала закончите стройку карты МП (/devent)."));
			return false;
		}
		if (ClanWarService.memberOfWar(player) || ClanWarService.jailed(player)) {
			Chat.send(player, Chat.red("Нельзя зайти на тренировку во время битвы кланов."));
			return false;
		}
		if (!checkDailyLimit(player)) {
			return false;
		}
		return !FullStopLock.deny(player) && !ServerStopService.deny(player) && !FreezeService.deny(player);
	}

	/** Базовый лимит из /dserver + навыки ветки Sync. */
	public static int dailyLimit(ServerPlayerEntity player) {
		int base = 5;
		if (DataManager.get() != null) {
			base = Math.max(1, Math.min(50, DataManager.get().settings().trainingDailyMax));
		}
		int extra = (int) Math.round(ru.admincontrol.skill.SkillEffects.server(player,
				ru.admincontrol.skill.SkillTree.Stat.TRAIN_LIMIT));
		return Math.max(1, base + Math.max(0, extra));
	}

	private static boolean checkDailyLimit(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return true;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ru.admincontrol.util.PlayFeatures.touchDay(record);
		int used = Math.max(0, record.dailyTrainings);
		int limit = dailyLimit(player);
		if (used >= limit) {
			Chat.send(player, Chat.red("Дневной лимит тренировок: " + used + "/" + limit
					+ ". Завтра снова, либо улучшите ветку Sync."));
			return false;
		}
		return true;
	}

	private static void consumeDaily(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ru.admincontrol.util.PlayFeatures.touchDay(record);
		record.dailyTrainings = Math.max(0, record.dailyTrainings) + 1;
		DataManager.get().markDirty();
		Chat.send(player, Chat.gray("Тренировки сегодня: " + record.dailyTrainings + "/" + dailyLimit(player)));
	}

	/** Вход через меню: 5 секунд ожидания без урона, потом старт. */
	public static void request(ServerPlayerEntity player, String type, int minutes) {
		if (!precheck(player)) {
			return;
		}
		player.closeHandledScreen();
		preselect(player);
		TeleportWarmup.start(player, "тренировка", () -> start(player, type, minutes));
	}

	/** Вход в тренировку с ботами через меню: те же 5 секунд ожидания. */
	public static void requestBots(ServerPlayerEntity player, BotSetup setup) {
		if (!precheck(player)) {
			return;
		}
		player.closeHandledScreen();
		int minutes = BotMatch.MINUTES[Math.max(0, Math.min(BotMatch.MINUTES.length - 1, setup.mode()))];
		preselect(player);
		TeleportWarmup.start(player, "тренировка", () -> begin(player, "bot:" + setup.mode(), minutes, setup));
	}

	public static void start(ServerPlayerEntity player, String type, int minutes) {
		begin(player, type, minutes, null);
	}

	private static void begin(ServerPlayerEntity player, String type, int minutes, BotSetup setup) {
		if (!precheck(player)) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Session session = new Session();
		session.uuid = player.getUuid();
		session.type = type;
		session.minutes = minutes;
		session.endsAt = System.currentTimeMillis() + minutes * 60_000L;
		session.inventoryNbt = InventoryUtil.serialize(player);
		session.originDim = player.getWorld().getRegistryKey().getValue().toString();
		session.ox = player.getX();
		session.oy = player.getY();
		session.oz = player.getZ();
		session.yaw = player.getYaw();
		session.pitch = player.getPitch();
		session.health = player.getHealth();
		session.food = player.getHungerManager().getFoodLevel();
		session.saturation = player.getHungerManager().getSaturationLevel();
		session.gameMode = player.interactionManager.getGameMode();
		if (!InventoryVault.save(player, InventoryVault.TRAINING, session.inventoryNbt, session.originDim,
				session.ox, session.oy, session.oz, session.yaw, session.pitch, session.gameMode,
				session.health, session.food, session.saturation)) {
			Chat.send(player, Chat.red("Не удалось сохранить ваш инвентарь на диск — тренировка отменена, вещи при вас."));
			return;
		}
		BlockPos stand = PRESELECTED.remove(player.getUuid());
		final BlockPos chosen = stand;
		if (stand == null || SESSIONS.values().stream().anyMatch(o -> o != null && o.arena != null && o.arena.world == world
				&& o.arena.contains(chosen))) {
			stand = chooseArenaPosition(world);
		}
		session.arena.build(world, stand);
		SESSIONS.put(player.getUuid(), session);
		consumeDaily(player);
		player.closeHandledScreen();
		ActivityLock.sync(player);
		GodModeService.suspend(player, "тренировки");
		player.changeGameMode(GameMode.SURVIVAL);
		GodModeService.forceOff(player, false);
		TeleportLog.labeled("телепортировался в тренировку", () ->
				player.teleport(world, session.arena.spawnX(), session.arena.spawnY(), session.arena.spawnZ(),
						player.getYaw(), player.getPitch()));
		player.fallDistance = 0;
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		TrainingConfig.giveKit(player);
		if (setup != null) {
			session.match = new BotMatch(setup.mode(), setup.difficulty(), setup.allies(), setup.enemies());
			session.botCoins = BotMatch.coins(session.match.mode, session.match.diff, session.match.allies, session.match.enemies);
			session.botPowerTenths = BotMatch.powerTenths(session.match.mode, session.match.diff);
			session.match.begin(session.arena, world, player);
		} else {
			spawnWave(session, world, player);
		}
		session.nextMobAt = System.currentTimeMillis() + MOB_EVERY_MS;
		session.bar = new ServerBossBar(Text.literal("Тренировка").formatted(Formatting.GOLD),
				BossBar.Color.GREEN, BossBar.Style.PROGRESS);
		session.bar.setPercent(1f);
		session.bar.addPlayer(player);
		updateBar(session);
		JournalLog.add(JournalLog.TRAINING, "Старт · " + player.getGameProfile().getName() + " · «"
				+ TrainingConfig.label(type) + "», " + minutes + " мин · откуда: " + session.originDim + " "
				+ (int) Math.floor(session.ox) + ", " + (int) Math.floor(session.oy) + ", " + (int) Math.floor(session.oz));
		Chat.send(player, Chat.green("Тренировка началась: " + TrainingConfig.label(type) + ", " + minutes
				+ " мин. /off — выйти."));
	}

	private static BlockPos chooseArenaPosition(ServerWorld world) {
		BlockPos spawn = world.getSpawnPos();
		int configured = DataManager.get() == null ? 1000
				: DataManager.get().settings().trainingSpawnRadius;
		int radius = Math.max(64, Math.min(30000, configured <= 0 ? 1000 : configured));
		double borderSize = world.getWorldBorder().getSize();
		double borderMinX = world.getWorldBorder().getCenterX() - borderSize / 2.0 + TrainingArena.SIZE + 2;
		double borderMaxX = world.getWorldBorder().getCenterX() + borderSize / 2.0 - TrainingArena.SIZE - 2;
		double borderMinZ = world.getWorldBorder().getCenterZ() - borderSize / 2.0 + TrainingArena.SIZE + 2;
		double borderMaxZ = world.getWorldBorder().getCenterZ() + borderSize / 2.0 - TrainingArena.SIZE - 2;
		for (int attempt = 0; attempt < 64; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2.0;
			double distance = Math.sqrt(world.random.nextDouble()) * radius;
			int x = (int) Math.floor(spawn.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(spawn.getZ() + Math.sin(angle) * distance);
			if (x < borderMinX || x > borderMaxX || z < borderMinZ || z > borderMaxZ) {
				continue;
			}
			Box candidate = new Box(x - 2, world.getTopY() - TrainingArena.HEIGHT - 1, z - 2,
					x + TrainingArena.SIZE + 2, world.getTopY(), z + TrainingArena.SIZE + 2);
			boolean occupied = SESSIONS.values().stream().anyMatch(s -> s != null && s.arena != null
					&& s.arena.world == world && candidate.intersects(new Box(s.arena.minX, s.arena.minY, s.arena.minZ,
						s.arena.maxX + 1, s.arena.maxY + 1, s.arena.maxZ + 1)));
			if (!occupied) {
				return new BlockPos(x, world.getTopY() - TrainingArena.HEIGHT, z);
			}
		}
		int x = (int) Math.max(borderMinX, Math.min(borderMaxX, spawn.getX()));
		int z = (int) Math.max(borderMinZ, Math.min(borderMaxZ, spawn.getZ()));
		return new BlockPos(x, world.getTopY() - TrainingArena.HEIGHT, z);
	}

	public static void off(ServerPlayerEntity player) {
		end(player, false, "Тренировка остановлена.");
	}

	public static boolean onDeath(ServerPlayerEntity player) {
		if (!active(player)) {
			return false;
		}
		end(player, false, "Вы погибли на тренировке. Награда не выдана.");
		if (player.getHealth() <= 0f) {
			player.setHealth(player.getMaxHealth());
		}
		player.fallDistance = 0;
		player.setVelocity(0, 0, 0);
		return true;
	}

	public static List<String> listLines(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Session session : SESSIONS.values()) {
			if (session == null) {
				continue;
			}
			String nick = session.uuid == null ? "?" : session.uuid.toString();
			if (server != null && session.uuid != null) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.uuid);
				if (player != null) {
					nick = player.getGameProfile().getName();
				} else if (DataManager.get() != null) {
					nick = DataManager.get().findByUuid(session.uuid).map(r -> r.nickname).orElse(nick);
				}
			}
			long left = Math.max(0L, session.endsAt - now);
			int sec = (int) ((left + 999L) / 1000L);
			lines.add("• " + nick + " — " + TrainingConfig.label(session.type)
					+ ", осталось " + (sec / 60) + ":" + String.format(java.util.Locale.US, "%02d", sec % 60));
		}
		return lines;
	}

	public static void abort(ServerPlayerEntity player) {
		end(player, false, null);
	}

	public static void stopAll(MinecraftServer server) {
		for (UUID id : SESSIONS.keySet().toArray(UUID[]::new)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				end(player, false, "Тренировка прервана (остановка сервера).");
			} else {
				Session session = SESSIONS.remove(id);
				if (session != null) {
					String nick = DataManager.get() == null ? id.toString()
							: DataManager.get().findByUuid(id).map(r -> r.nickname).orElse(id.toString());
					JournalLog.add(JournalLog.TRAINING, "Итог · " + nick + " · «" + TrainingConfig.label(session.type)
							+ "» · прервана (остановка сервера, игрок не в сети)");
					clearBar(session);
					if (session.arena != null) {
						clearBox(session);
						session.arena.remove();
					}
				}
			}
		}
	}

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Session session = SESSIONS.get(player.getUuid());
			if (session == null) {
				continue;
			}
			if (session.arena != null) {
				GodModeService.forceOff(player, false);
				session.arena.keepInside(player);
				ServerWorld world = session.arena.world != null ? session.arena.world : player.getServerWorld();
				if (session.match != null) {
					session.match.tick(player);
				} else {
					if (server.getTicks() % 10 == 0) {
						aggro(session, world, player);
					}
					if (now >= session.nextMobAt) {
						session.nextMobAt = now + MOB_EVERY_MS;
						spawnWave(session, world, player);
					}
				}
			}
			updateBar(session);
			if (session.bar != null && !session.bar.getPlayers().contains(player)) {
				session.bar.addPlayer(player);
			}
			if (session.match != null) {
				if (session.match.result == BotMatch.WON) {
					end(player, true, null);
				} else if (session.match.result == BotMatch.LOST) {
					end(player, false, session.match.resultText);
				} else if (now >= session.endsAt) {
					if (BotMatch.winOnTimeout(session.match.mode)) {
						end(player, true, null);
					} else {
						end(player, false, "Время вышло. Награда не выдана.");
					}
				}
			} else if (now >= session.endsAt) {
				end(player, true, null);
			}
		}
	}

	private static void end(ServerPlayerEntity player, boolean success, String message) {
		if (player == null) {
			return;
		}
		Session session = SESSIONS.remove(player.getUuid());
		if (session == null) {
			return;
		}
		clearBar(session);
		clearBox(session);
		if (session.arena != null) {
			session.arena.remove();
		}
		player.getInventory().clear();
		InventoryUtil.restore(player, session.inventoryNbt);
		ActivityLock.sync(player);
		if (session.gameMode != null) {
			player.changeGameMode(session.gameMode);
		}
		teleportOrigin(player, session);
		InventoryVault.applyVitals(player, session.health, session.food, session.saturation);
		InventoryVault.clear(player);
		GodModeService.resume(player);
		String outcome;
		int rewardCoins = session.match != null ? session.botCoins : TrainingConfig.coins(session.type, session.minutes);
		int rewardTenths = session.match != null ? session.botPowerTenths : TrainingConfig.powerTenths(session.type, session.minutes);
		if (success) {
			outcome = "пройдена, +" + rewardCoins + " WC, сила +"
					+ String.format(java.util.Locale.US, "%.1f", rewardTenths / 10.0);
		} else {
			outcome = message == null ? "прервана" : message;
		}
		JournalLog.add(JournalLog.TRAINING, "Итог · " + player.getGameProfile().getName() + " · «"
				+ TrainingConfig.label(session.type) + "», " + session.minutes + " мин · " + outcome);
		if (success) {
			int coins = ProteinService.boostTrainingCoins(player, rewardCoins);
			int tenths = rewardTenths;
			if (coins > 0) {
				Economy.giveCurrency(player, coins, "награда за тренировку " + TrainingConfig.label(session.type));
			}
			if (tenths > 0 && DataManager.get() != null) {
				PlayerRecord record = DataManager.get().progress(player);
				double from = record.power();
				record.addEventPower(tenths / 10.0);
				DataManager.get().saveAll();
				ProgressLog.power(record, from, record.power(), ProgressLog.TRAINING);
				BalanceBoard.refresh(player, true);
			}
			Achievements.onTrainingPrize(player);
			SkillTreeService.onTrainingDone(player);
			Chat.send(player, Chat.green("Тренировка пройдена. +" + Money.wc(coins) + ", сила +"
					+ String.format(java.util.Locale.US, "%.1f", tenths / 10.0) + "."));
		} else if (message != null) {
			Chat.send(player, Chat.yellow(message));
		}
	}

	private static void updateBar(Session session) {
		if (session == null || session.bar == null) {
			return;
		}
		long total = Math.max(1L, session.minutes * 60_000L);
		long left = Math.max(0L, session.endsAt - System.currentTimeMillis());
		int sec = (int) ((left + 999L) / 1000L);
		int min = sec / 60;
		int rem = sec % 60;
		String label = TrainingConfig.label(session.type);
		String text = label + (session.match != null ? "  •  " + session.match.status() : "")
				+ "  •  " + min + ":" + String.format(java.util.Locale.US, "%02d", rem);
		float pct = Math.max(0f, Math.min(1f, left / (float) total));
		if (text.equals(session.lastBar) && Math.abs(pct - session.lastBarPct) < 0.01f) {
			return;
		}
		session.lastBar = text;
		session.lastBarPct = pct;
		session.bar.setName(Text.literal(text).formatted(Formatting.GOLD));
		session.bar.setPercent(pct);
	}

	private static void clearBar(Session session) {
		if (session == null || session.bar == null) {
			return;
		}
		session.bar.clearPlayers();
		session.bar.setVisible(false);
		session.bar = null;
	}

	private static void teleportOrigin(ServerPlayerEntity player, Session session) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		Identifier id = Identifier.tryParse(session.originDim);
		ServerWorld dest = server.getOverworld();
		if (id != null) {
			RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
			ServerWorld found = server.getWorld(key);
			if (found != null) {
				dest = found;
			}
		}
		final ServerWorld world = dest;
		TeleportLog.labeled("телепортировался из тренировки", () ->
				WorldGate.allow(() -> player.teleport(world, session.ox, session.oy, session.oz, session.yaw, session.pitch)));
		player.fallDistance = 0;
	}

	private static void spawnWave(Session session, ServerWorld world, ServerPlayerEntity player) {
		for (int i = 0; i < MOBS_PER_WAVE; i++) {
			spawnOne(session, world, player);
		}
	}

	private static void spawnOne(Session session, ServerWorld world, ServerPlayerEntity player) {
		if (session == null || session.arena == null || world == null) {
			return;
		}
		int inner = Math.max(1, TrainingArena.SIZE - 6);
		int x = session.arena.minX + 3 + world.random.nextInt(inner);
		int z = session.arena.minZ + 3 + world.random.nextInt(inner);
		int y = session.arena.minY + 1;
		BlockPos pos = new BlockPos(x, y, z);
		EntityType<? extends MobEntity> type = EntityType.ZOMBIE;
		if ("skeleton".equals(session.type)) {
			type = EntityType.SKELETON;
		} else if ("spider".equals(session.type)) {
			type = EntityType.SPIDER;
		}
		MobEntity mob = type.create(world);
		if (mob == null) {
			return;
		}
		mob.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0);
		LocalDifficulty difficulty = world.getLocalDifficulty(pos);
		mob.initialize(world, difficulty, SpawnReason.SPAWN_EGG, null, null);
		mob.setPersistent();
		mob.setAiDisabled(false);
		world.spawnEntity(mob);
		hunt(mob, player);
	}

	private static void aggro(Session session, ServerWorld world, ServerPlayerEntity player) {
		if (session == null || session.arena == null || world == null || player == null) {
			return;
		}
		Box box = new Box(session.arena.minX + 1, session.arena.minY + 1, session.arena.minZ + 1,
				session.arena.maxX, session.arena.maxY, session.arena.maxZ);
		for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box, Entity::isAlive)) {
			hunt(mob, player);
		}
	}

	private static void hunt(MobEntity mob, ServerPlayerEntity player) {
		if (mob == null || player == null || !player.isAlive()) {
			return;
		}
		mob.setTarget(player);
		mob.setAttacking(player);
		mob.getLookControl().lookAt(player, 180f, 180f);
		if (mob.getNavigation() != null && (mob.getNavigation().isIdle() || mob.squaredDistanceTo(player) > 4)) {
			mob.getNavigation().startMovingTo(player, 1.2);
		}
	}

	private static void clearBox(Session session) {
		if (session == null || session.arena == null || session.arena.world == null) {
			return;
		}
		ServerWorld world = session.arena.world;
		if (session.match != null) {
			session.match.cleanup();
		}
		Box box = new Box(session.arena.minX, session.arena.minY, session.arena.minZ,
				session.arena.maxX + 1, session.arena.maxY + 1, session.arena.maxZ + 1);
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, e -> true)) {
			mob.discard();
		}
		Iterator<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, e -> true).iterator();
		while (items.hasNext()) {
			items.next().discard();
		}
		for (Entity xp : world.getEntitiesByClass(net.minecraft.entity.ExperienceOrbEntity.class, box, e -> true)) {
			xp.discard();
		}
	}
}
