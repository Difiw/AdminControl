package ru.admincontrol.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.mixin.MobEntityTargetsAccessor;

import java.util.ArrayList;
import java.util.List;

/** Матч тренировки с ботами: союзники и противники — обычные мобы с командными целями, правила зависят от режима. */
public final class BotMatch {
	public static final String[] NAMES = {"Арена дуэлей", "Командный бой", "Захват точки", "Защита кристалла",
			"Волны противников", "Выживание", "Снайперы", "Битва с боссом"};
	public static final String[] DIFFS = {"Простой", "Средний", "Сложный"};
	public static final int[] DEF_ALLIES = {3, 4, 4, 5, 2, 3, 2, 4};
	/** 0 у «Выживания» — бесконечный поток. */
	public static final int[] DEF_ENEMIES = {3, 4, 4, 6, 8, 0, 5, 1};
	public static final int[] MINUTES = {5, 6, 6, 3, 8, 5, 6, 8};
	private static final boolean[] WIN_ON_TIMEOUT = {false, false, false, true, false, true, false, false};
	private static final int[] BASE_COINS = {60, 80, 90, 100, 110, 120, 100, 200};
	private static final double[] DIFF_COINS = {0.6, 1.0, 1.6};

	public static final int RUNNING = 0;
	public static final int WON = 1;
	public static final int LOST = 2;

	private static final int ZONE_RADIUS = 4;
	private static final int CRYSTAL_RADIUS = 3;

	public final int mode;
	public final int diff;
	public final int allies;
	public final int enemies;
	public int result = RUNNING;
	public String resultText = "";

	private final List<MobEntity> allyBots = new ArrayList<>();
	private final List<MobEntity> enemyBots = new ArrayList<>();
	private TrainingArena arena;
	private ServerWorld world;
	private EndCrystalEntity crystal;
	private double crystalHp = 100;
	private double capture;
	private int wave;
	private int ticks;
	private int killed;
	private int spawned;
	private boolean started;
	private int startTick;
	private int lostRespawns;
	private final java.util.Map<MobEntity, Integer> slots = new java.util.IdentityHashMap<>();
	private final java.util.Map<MobEntity, LivingEntity> targets = new java.util.IdentityHashMap<>();

	public BotMatch(int mode, int diff, int allies, int enemies) {
		this.mode = Math.max(0, Math.min(NAMES.length - 1, mode));
		this.diff = Math.max(0, Math.min(2, diff));
		this.allies = Math.max(0, Math.min(8, allies));
		this.enemies = enemiesFixed(this.mode) ? DEF_ENEMIES[this.mode] : Math.max(1, Math.min(16, enemies));
	}

	public static boolean enemiesFixed(int mode) {
		return mode == 5 || mode == 7;
	}

	public static boolean winOnTimeout(int mode) {
		return WIN_ON_TIMEOUT[Math.max(0, Math.min(WIN_ON_TIMEOUT.length - 1, mode))];
	}

	public static int coins(int mode, int diff, int allies, int enemies) {
		mode = Math.max(0, Math.min(NAMES.length - 1, mode));
		double mult = DIFF_COINS[Math.max(0, Math.min(2, diff))];
		double crowd = 1.0 + Math.max(0, (enemiesFixed(mode) ? DEF_ENEMIES[mode] : enemies) - allies) * 0.05;
		return Math.max(1, (int) Math.round(BASE_COINS[mode] * mult * crowd));
	}

	public static int powerTenths(int mode, int diff) {
		return 1 + Math.max(0, Math.min(2, diff)) + (mode == 7 ? 1 : 0);
	}

	public void begin(TrainingArena arena, ServerWorld world, ServerPlayerEntity player) {
		this.arena = arena;
		this.world = world;
		double cx = arena.spawnX();
		double southZ = arena.minZ + 3.5;
		if (player.networkHandler != null) {
			TeleportLog.silent(() -> {
				player.networkHandler.requestTeleport(cx, arena.minY + 1.0, southZ, 0f, 0f);
				player.fallDistance = 0;
			});
		}
	}

	/** Спавн ботов откладывается, пока чанки арены не загружены: иначе мобы пропадают, а матч «выигрывается» сам. */
	private void doSpawn(ServerPlayerEntity player) {
		double cx = this.arena.spawnX();
		double southZ = this.arena.minZ + 3.5;
		this.started = true;
		this.startTick = this.ticks;
		for (int i = 0; i < this.allies; i++) {
			double x = cx + (i - (this.allies - 1) / 2.0) * 2.0;
			spawn(true, EntityType.VINDICATOR, x, southZ + 1.8, i + 1);
		}
		switch (this.mode) {
			case 3 -> {
				this.crystal = EntityType.END_CRYSTAL.create(world);
				if (this.crystal != null) {
					this.crystal.refreshPositionAndAngles(cx, arena.minY + 1.0, arena.minZ + 2.5, 0f, 0f);
					this.crystal.setShowBottom(false);
					this.crystal.setInvulnerable(true);
					world.spawnEntity(this.crystal);
				}
				spawnEnemies(this.enemies);
			}
			case 4 -> startWave();
			case 5 -> spawnEnemies(survivalCap());
			case 6 -> spawnEnemies(this.enemies);
			case 7 -> spawn(false, EntityType.VINDICATOR, cx, arena.maxZ - 3.0, 1);
			default -> spawnEnemies(this.enemies);
		}
		if (player.networkHandler != null) {
			Chat.send(player, Chat.yellow("Режим «" + NAMES[this.mode] + "»: " + goal()));
		}
	}

	private String goal() {
		return switch (this.mode) {
			case 2 -> "удерживайте центр арены, пока ваша команда одна в зоне.";
			case 3 -> "не дайте врагам уничтожить кристалл до конца таймера.";
			case 4 -> "отбейте 3 волны противников.";
			case 5 -> "продержитесь до конца таймера.";
			case 7 -> "победите босса вместе с союзниками.";
			default -> "победите всех противников.";
		};
	}

	private int survivalCap() {
		return Math.min(8, 3 + this.diff + this.ticks / 1200);
	}

	private void startWave() {
		this.wave++;
		int per = (int) Math.ceil(this.enemies / 3.0);
		int count = this.wave < 3 ? per : Math.max(1, this.enemies - per * 2);
		spawnEnemies(count);
		if (this.wave > 1) {
			announce("Волна " + this.wave + "/3!");
		}
	}

	private void spawnEnemies(int count) {
		for (int i = 0; i < count; i++) {
			double inner = Math.max(4, TrainingArena.SIZE - 6);
			double x = arena.minX + 3 + (i + 0.5) * (inner / Math.max(1, count));
			boolean archer = this.mode == 6 || (this.mode == 5 && (this.spawned % 3 == 2));
			spawn(false, archer ? EntityType.SKELETON : EntityType.VINDICATOR, x, arena.maxZ - 2.5, this.spawned + 1);
		}
	}

	private void announce(String text) {
		if (this.world == null) {
			return;
		}
		for (ServerPlayerEntity p : this.world.getPlayers()) {
			if (this.arena.contains(p.getBlockPos())) {
				p.sendMessage(Text.literal(text).formatted(Formatting.GOLD), true);
			}
		}
	}

	private void spawn(boolean ally, EntityType<? extends MobEntity> type, double x, double z, int index) {
		MobEntity mob = type.create(this.world);
		if (mob == null) {
			return;
		}
		double sx = Math.max(this.arena.minX + 2, Math.min(this.arena.maxX - 1, x));
		double sz = Math.max(this.arena.minZ + 2, Math.min(this.arena.maxZ - 1, z));
		mob.refreshPositionAndAngles(sx, this.arena.minY + 1.0, sz, ally ? 0f : 180f, 0f);
		mob.initialize(this.world, this.world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.SPAWN_EGG, null, null);
		mob.setPersistent();
		((MobEntityTargetsAccessor) mob).adminControl$targetSelector().clear(g -> true);
		boolean boss = !ally && this.mode == 7;
		int level = ally ? 1 : this.diff;
		boolean archer = type == EntityType.SKELETON;
		if (!archer) {
			mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(boss ? Items.DIAMOND_AXE : Items.IRON_AXE));
		}
		int color = ally ? 0x2A5FE0 : 0xD02A2A;
		dress(mob, EquipmentSlot.HEAD, Items.LEATHER_HELMET, color);
		dress(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, color);
		dress(mob, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, color);
		dress(mob, EquipmentSlot.FEET, Items.LEATHER_BOOTS, color);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setEquipmentDropChance(slot, 0f);
		}
		attr(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, 48);
		if (boss) {
			attr(mob, EntityAttributes.GENERIC_MAX_HEALTH, new double[]{120, 200, 320}[level]);
			attr(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, new double[]{7, 10, 14}[level]);
			attr(mob, EntityAttributes.GENERIC_ARMOR, new double[]{6, 10, 14}[level]);
			attr(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30);
			attr(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
			mob.setGlowing(true);
			mob.setCustomName(Text.literal("Страж арены").formatted(Formatting.DARK_RED, Formatting.BOLD));
		} else {
			attr(mob, EntityAttributes.GENERIC_MAX_HEALTH, archer ? new double[]{14, 20, 28}[level] : new double[]{16, 24, 34}[level]);
			attr(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, new double[]{3, 5, 7}[level]);
			attr(mob, EntityAttributes.GENERIC_ARMOR, new double[]{0, 2, 6}[level]);
			attr(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, new double[]{0.30, 0.34, 0.38}[level]);
			mob.setCustomName(Text.literal((ally ? "Союзник " : "Враг ") + index)
					.formatted(ally ? Formatting.BLUE : Formatting.RED));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomNameVisible(true);
		this.world.spawnEntity(mob);
		if (ally) {
			this.allyBots.add(mob);
		} else {
			this.enemyBots.add(mob);
			this.slots.put(mob, this.spawned);
			this.spawned++;
		}
	}

	private static void dress(MobEntity mob, EquipmentSlot slot, net.minecraft.item.Item item, int color) {
		ItemStack stack = new ItemStack(item);
		if (item instanceof DyeableArmorItem dyeable) {
			dyeable.setColor(stack, color);
		}
		mob.equipStack(slot, stack);
	}

	private static void attr(MobEntity mob, EntityAttribute attribute, double value) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** Один серверный тик матча. */
	public void tick(ServerPlayerEntity player) {
		if (this.result != RUNNING || this.world == null) {
			return;
		}
		this.ticks++;
		if (!this.started) {
			boolean ready = this.world.getChunkManager().isChunkLoaded(((int) this.arena.spawnX()) >> 4, ((int) this.arena.spawnZ()) >> 4);
			if ((ready && this.ticks >= 30) || this.ticks >= 200) {
				doSpawn(player);
			}
			return;
		}
		int before = this.enemyBots.size();
		int lost = 0;
		java.util.Iterator<MobEntity> it = this.enemyBots.iterator();
		while (it.hasNext()) {
			MobEntity b = it.next();
			if (b.isAlive() && !b.isRemoved()) {
				continue;
			}
			it.remove();
			this.targets.remove(b);
			if (b.isDead() || b.getRemovalReason() == net.minecraft.entity.Entity.RemovalReason.KILLED) {
				this.killed++;
			} else {
				lost++;
			}
		}
		// бота выгрузило вместе с чанком — это не победа игрока, а потеря: заменяем
		while (lost-- > 0 && this.lostRespawns < 24) {
			this.lostRespawns++;
			if (this.mode == 7) {
				spawn(false, EntityType.VINDICATOR, this.arena.spawnX(), this.arena.maxZ - 3.0, 1);
			} else {
				spawnEnemies(1);
			}
		}
		this.allyBots.removeIf(b -> {
			boolean gone = !b.isAlive() || b.isRemoved();
			if (gone) {
				this.targets.remove(b);
			}
			return gone;
		});
		if (this.ticks % 10 == 0) {
			retarget(player);
		}
		if (this.ticks % 20 == 0) {
			perSecond(player);
		}
	}

	private static boolean valid(LivingEntity e) {
		return e != null && e.isAlive() && !e.isRemoved();
	}

	/**
	 * Цели «липкие»: враг держит назначенную цель, пока она жива. Слоты распределяют врагов между игроком и союзниками,
	 * поэтому толпа не переключается с команды на игрока и обратно.
	 */
	private void retarget(ServerPlayerEntity player) {
		boolean playerOk = player != null && player.isAlive() && this.arena.contains(player.getBlockPos());
		List<LivingEntity> candidates = new ArrayList<>();
		if (playerOk) {
			candidates.add(player);
		}
		candidates.addAll(this.allyBots);
		for (MobEntity enemy : this.enemyBots) {
			LivingEntity cur = this.targets.get(enemy);
			if (!valid(cur) || (cur instanceof ServerPlayerEntity && !playerOk)) {
				cur = null;
				if (!candidates.isEmpty()) {
					int slot = this.slots.getOrDefault(enemy, 0);
					cur = candidates.get(slot % candidates.size());
				}
				if (cur != null) {
					this.targets.put(enemy, cur);
				}
			}
			if (cur != null) {
				if (enemy.getTarget() != cur) {
					enemy.setTarget(cur);
				}
				if (enemy.getNavigation().isIdle() && enemy.squaredDistanceTo(cur) > 9) {
					enemy.getNavigation().startMovingTo(cur, 1.1);
				}
			} else if (this.mode == 3 && this.crystal != null) {
				enemy.getNavigation().startMovingTo(this.crystal.getX(), this.crystal.getY(), this.crystal.getZ(), 1.1);
			} else if (this.mode == 2) {
				enemy.getNavigation().startMovingTo(this.arena.spawnX(), this.arena.minY + 1.0, this.arena.spawnZ(), 1.1);
			}
			// в режимах с точкой/кристаллом часть врагов идёт к цели режима, а не к людям
			if (cur != null && this.mode == 3 && this.crystal != null && this.slots.getOrDefault(enemy, 0) % 2 == 1
					&& enemy.squaredDistanceTo(cur) > 36) {
				enemy.getNavigation().startMovingTo(this.crystal.getX(), this.crystal.getY(), this.crystal.getZ(), 1.1);
			}
		}
		for (MobEntity ally : this.allyBots) {
			LivingEntity cur = this.targets.get(ally);
			if (!valid(cur)) {
				cur = null;
				double bestD = Double.MAX_VALUE;
				for (MobEntity enemy : this.enemyBots) {
					// предпочитаем тех, кто бьёт игрока, затем ближайших
					double d = ally.squaredDistanceTo(enemy) - (this.targets.get(enemy) == player ? 40 : 0);
					if (d < bestD) {
						bestD = d;
						cur = enemy;
					}
				}
				if (cur != null) {
					this.targets.put(ally, cur);
				}
			}
			if (cur != null) {
				if (ally.getTarget() != cur) {
					ally.setTarget(cur);
				}
			} else {
				ally.setTarget(null);
				if (playerOk && ally.squaredDistanceTo(player) > 36) {
					ally.getNavigation().startMovingTo(player, 1.1);
				}
			}
		}
	}

	private void perSecond(ServerPlayerEntity player) {
		if (this.ticks - this.startTick < 60) {
			return;
		}
		switch (this.mode) {
			case 2 -> captureTick(player);
			case 3 -> crystalTick();
			case 4 -> {
				if (this.enemyBots.isEmpty()) {
					if (this.wave >= 3) {
						finish(WON, "Все волны отбиты.");
					} else if (this.ticks % 60 == 0 || this.enemyBots.isEmpty()) {
						startWave();
					}
				}
			}
			case 5 -> {
				if (this.enemyBots.size() < survivalCap() && this.ticks % 80 == 0) {
					spawnEnemies(1);
				}
			}
			default -> {
				if (this.enemyBots.isEmpty()) {
					finish(WON, this.mode == 7 ? "Босс повержен." : "Все противники побеждены.");
				}
			}
		}
		if (this.mode == 2 && this.enemyBots.isEmpty() && this.result == RUNNING) {
			finish(WON, "Все противники побеждены.");
		}
	}

	private void captureTick(ServerPlayerEntity player) {
		double cx = this.arena.spawnX();
		double cz = this.arena.spawnZ();
		int blue = 0;
		int red = 0;
		if (player != null && player.isAlive() && inZone(player, cx, cz)) {
			blue++;
		}
		for (MobEntity b : this.allyBots) {
			if (inZone(b, cx, cz)) {
				blue++;
			}
		}
		for (MobEntity b : this.enemyBots) {
			if (inZone(b, cx, cz)) {
				red++;
			}
		}
		if (blue > 0 && red == 0) {
			this.capture = Math.min(100, this.capture + 4);
		} else if (red > 0 && blue == 0) {
			this.capture = Math.max(-100, this.capture - 4);
		}
		double y = this.arena.minY + 1.2;
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			this.world.spawnParticles(this.capture >= 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME,
					cx + Math.cos(a) * ZONE_RADIUS, y, cz + Math.sin(a) * ZONE_RADIUS, 1, 0, 0, 0, 0);
		}
		if (this.capture >= 100) {
			finish(WON, "Точка захвачена.");
		} else if (this.capture <= -100) {
			finish(LOST, "Противники захватили точку. Награда не выдана.");
		}
	}

	private static boolean inZone(net.minecraft.entity.Entity e, double cx, double cz) {
		double dx = e.getX() - cx;
		double dz = e.getZ() - cz;
		return dx * dx + dz * dz <= ZONE_RADIUS * ZONE_RADIUS + 1;
	}

	private void crystalTick() {
		if (this.crystal == null) {
			return;
		}
		double per = new double[]{0.8, 1.5, 2.5}[this.diff];
		for (MobEntity b : this.enemyBots) {
			double dx = b.getX() - this.crystal.getX();
			double dz = b.getZ() - this.crystal.getZ();
			if (dx * dx + dz * dz <= CRYSTAL_RADIUS * CRYSTAL_RADIUS) {
				this.crystalHp -= per;
				this.world.spawnParticles(ParticleTypes.CRIT, this.crystal.getX(), this.crystal.getY() + 1, this.crystal.getZ(),
						4, 0.4, 0.4, 0.4, 0.1);
			}
		}
		if (this.crystalHp <= 0) {
			finish(LOST, "Кристалл уничтожен. Награда не выдана.");
			return;
		}
		if (this.enemyBots.size() < this.enemies && this.ticks % 160 == 0) {
			spawnEnemies(1);
		}
	}

	private void finish(int outcome, String text) {
		if (this.result == RUNNING) {
			this.result = outcome;
			this.resultText = text;
		}
	}

	/** Строка состояния для боссбара. */
	public String status() {
		int foes = this.enemyBots.size();
		return switch (this.mode) {
			case 2 -> "Точка: " + (int) Math.round((this.capture + 100) / 2) + "%  •  Враги: " + foes;
			case 3 -> "Кристалл: " + Math.max(0, (int) Math.round(this.crystalHp)) + "%  •  Враги: " + foes;
			case 4 -> "Волна " + Math.max(1, this.wave) + "/3  •  Враги: " + foes;
			case 5 -> "Убито: " + this.killed + "  •  Враги: " + foes;
			case 7 -> "Босс: " + (foes == 0 ? "повержен" : (int) Math.ceil(this.enemyBots.get(0).getHealth()) + " HP");
			default -> "Враги: " + foes + "  •  Союзники: " + this.allyBots.size();
		};
	}

	public void cleanup() {
		for (MobEntity b : this.allyBots) {
			b.discard();
		}
		for (MobEntity b : this.enemyBots) {
			b.discard();
		}
		this.allyBots.clear();
		this.enemyBots.clear();
		if (this.crystal != null) {
			this.crystal.discard();
			this.crystal = null;
		}
	}
}
