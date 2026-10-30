package ru.admincontrol.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.SkillData;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.skill.SkillTree.Currency;
import ru.admincontrol.skill.SkillTree.Daily;
import ru.admincontrol.skill.SkillTree.Node;
import ru.admincontrol.skill.SkillTree.Quest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Дерево навыков на сервере: покупки, эффекты, задания и синхронизация. Клиент только показывает и просит. */
public final class SkillTreeService {
	public static final int MAX_COINS = 1_000_000;
	private static final int HISTORY = 40;
	private static final long BUY_COOLDOWN_MS = 150;
	private static final int POLL_TICKS = 100;
	private static final long POLL_SECONDS = POLL_TICKS / 20;

	private static final long KILL_CAP_PER_TYPE = 40;
	private static final long ORE_CAP = 250;
	private static final long STONE_CAP = 1500;
	private static final long LOG_CAP = 600;
	private static final long JUMP_CAP = 300;
	private static final long SNEAK_CAP_CM = 40_000;
	private static final long WALK_CAP_CM = 800_000;

	private static final Set<String> ELITE = Set.of(
			"minecraft:enderman", "minecraft:blaze", "minecraft:witch", "minecraft:wither_skeleton",
			"minecraft:piglin_brute", "minecraft:ravager", "minecraft:evoker", "minecraft:vindicator",
			"minecraft:guardian", "minecraft:elder_guardian", "minecraft:ghast", "minecraft:shulker",
			"minecraft:hoglin", "minecraft:zoglin", "minecraft:illusioner");
	private static final Set<String> BOSSES = Set.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:warden");

	private static final UUID HEALTH_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0001");
	private static final UUID ARMOR_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0002");
	private static final UUID SPEED_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0003");
	private static final UUID DAMAGE_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0004");
	private static final UUID ATTACK_SPEED_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0005");
	private static final UUID KNOCKBACK_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0006");
	private static final UUID TOUGHNESS_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0007");
	private static final UUID LUCK_ID = UUID.fromString("5d0c1a62-7a3e-4b8e-9f61-3a1c0e5b0008");

	/** Админ → ник игрока, чьё дерево он смотрит (только просмотр). */
	private static final Map<UUID, String> VIEWING = new ConcurrentHashMap<>();
	private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
	private static final Set<String> DIRTY = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> LAST_BUY = new ConcurrentHashMap<>();
	private static final Map<UUID, double[]> LAST_POSE = new ConcurrentHashMap<>();
	private static final Map<Long, Boolean> PLACED = new LinkedHashMap<>(4096, 0.75f, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
			return size() > 150_000;
		}
	};
	private static final Map<UUID, Boolean> RECENT_KILLS = new LinkedHashMap<>(512, 0.75f, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
			return size() > 4096;
		}
	};
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

	private SkillTreeService() {
	}

	public static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (ModGate.on()) {
				applyEffects(newPlayer);
			}
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (ModGate.on() && !alive) {
				keepExperience(oldPlayer, newPlayer);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (ModGate.on() && entity instanceof ServerPlayerEntity player) {
				onPlayerDeath(player);
			}
		});
	}

	// ---------- данные ----------

	public static SkillData data(PlayerRecord record) {
		if (record.skills == null) {
			record.skills = new SkillData();
		}
		record.skills.normalize();
		return record.skills;
	}

	private static boolean skip(ServerPlayerEntity player) {
		return player == null || DataManager.get() == null || player.networkHandler == null
				|| player.networkHandler instanceof NpcNetworkHandler || NpcService.isNpc(player);
	}

	/** Чьё дерево: у админа, управляющего NPC, — дерево этого NPC; у NPC и игроков — своё. */
	private static PlayerRecord record(ServerPlayerEntity player) {
		return DataManager.get().progress(player);
	}

	public static int level(PlayerRecord record, Node node) {
		if (record == null || node == null) {
			return 0;
		}
		if (node == SkillTree.BACKPACK) {
			return Math.max(0, Math.min(5, record.backpackLevel));
		}
		if (node == SkillTree.WORKBENCH) {
			return record.workbenchOwned ? 1 : 0;
		}
		if (node == SkillTree.FURNACE) {
			return Math.max(0, record.furnaceLevel);
		}
		if (record.skills == null || record.skills.levels == null) {
			return 0;
		}
		Integer v = record.skills.levels.get(node.id);
		return v == null ? 0 : Math.max(0, Math.min(node.maxLevel, v));
	}

	public static int maxLevel(Node node) {
		if (node == SkillTree.FURNACE) {
			return Math.max(1, PlayFeatures.s().furnaceMaxLevel());
		}
		return node.maxLevel;
	}

	private static void setLevel(PlayerRecord record, Node node, int level) {
		long now = System.currentTimeMillis();
		if (node == SkillTree.BACKPACK) {
			if (record.backpackLevel <= 0) {
				record.backpackBoughtAt = now;
			}
			record.backpackLevel = level;
		} else if (node == SkillTree.WORKBENCH) {
			record.workbenchOwned = level > 0;
			record.workbenchBoughtAt = now;
		} else if (node == SkillTree.FURNACE) {
			if (record.furnaceLevel <= 0) {
				record.furnaceBoughtAt = now;
			}
			record.furnaceLevel = level;
		} else {
			data(record).levels.put(node.id, level);
		}
	}

	private static boolean migrated(Node node) {
		return node == SkillTree.BACKPACK || node == SkillTree.WORKBENCH || node == SkillTree.FURNACE;
	}

	/** Чего не хватает для покупки (по требованиям), или null. */
	public static String missingRequirement(PlayerRecord record, Node node) {
		List<String> missing = new ArrayList<>();
		for (SkillTree.Req req : node.requires) {
			Node need = SkillTree.node(req.node());
			if (need != null && level(record, need) < req.level()) {
				missing.add("«" + need.title + "» ур. " + req.level());
			}
		}
		return missing.isEmpty() ? null : String.join(", ", missing);
	}

	private static void ensureDay(SkillData sd) {
		String today = PlayFeatures.dayKey();
		if (!today.equals(sd.day)) {
			sd.day = today;
			sd.daily.clear();
			sd.dailyDone.clear();
		}
	}

	private static void history(SkillData sd, String text) {
		sd.history.add(TIME.format(Instant.now()) + "  " + text);
		while (sd.history.size() > HISTORY) {
			sd.history.remove(0);
		}
	}

	private static String key(String nick) {
		return nick == null ? "" : nick.toLowerCase(Locale.ROOT);
	}

	// ---------- эффекты ----------

	/** Итоги характеристик по сохранённым уровням; узлы отключённых веток не действуют. */
	public static double[] totals(PlayerRecord rec) {
		int[] levels = new int[SkillTree.NODES.size()];
		for (Node node : SkillTree.NODES) {
			levels[node.index] = nodeOff(rec, node) ? 0 : level(rec, node);
		}
		return SkillTree.totals(levels);
	}

	// ---------- отключение веток ----------

	private static List<String> offList(boolean admins) {
		ru.admincontrol.data.ModSettings s = DataManager.get().settings();
		if (admins) {
			if (s.skillBranchesOffAdmins == null) {
				s.skillBranchesOffAdmins = new ArrayList<>();
			}
			return s.skillBranchesOffAdmins;
		}
		if (s.skillBranchesOffPlayers == null) {
			s.skillBranchesOffPlayers = new ArrayList<>();
		}
		return s.skillBranchesOffPlayers;
	}

	public static boolean branchOff(boolean admins, SkillTree.Branch branch) {
		return branch != null && DataManager.get() != null && offList(admins).contains(branch.id());
	}

	/** Отключена ли ветка для владельца профиля: администрация и игроки настраиваются отдельно. */
	public static boolean branchOff(PlayerRecord rec, SkillTree.Branch branch) {
		if (rec == null || branch == null || DataManager.get() == null) {
			return false;
		}
		boolean staff = DataManager.get().getAdminLevel(rec.uuidOrNull(), rec.nickname) >= ru.admincontrol.data.AdminRanks.MOD;
		return branchOff(staff, branch);
	}

	public static boolean nodeOff(PlayerRecord rec, Node node) {
		return node != null && branchOff(rec, node.branch);
	}

	/**
	 * Включить или отключить ветку: scope — all, admins или players; rank — ранг того, кто меняет.
	 * Возвращает текст ошибки или null.
	 */
	public static String setBranch(MinecraftServer server, SkillTree.Branch branch, boolean enabled, String scope, int rank) {
		boolean players = !"admins".equals(scope);
		boolean admins = !"players".equals(scope);
		ru.admincontrol.data.ModSettings s = DataManager.get().settings();
		if (s.skillBranchRanks == null) {
			s.skillBranchRanks = new LinkedHashMap<>();
		}
		for (boolean forAdmins : new boolean[] {false, true}) {
			Integer by = s.skillBranchRanks.get(branch.id() + (forAdmins ? ":admins" : ":players"));
			if ((forAdmins ? admins : players) && by != null && by > rank) {
				return "Эту ветку отключил старший по рангу — изменить может только он или выше.";
			}
		}
		for (boolean forAdmins : new boolean[] {false, true}) {
			if (forAdmins ? !admins : !players) {
				continue;
			}
			String key = branch.id() + (forAdmins ? ":admins" : ":players");
			List<String> list = offList(forAdmins);
			list.remove(branch.id());
			if (enabled) {
				s.skillBranchRanks.remove(key);
			} else {
				list.add(branch.id());
				s.skillBranchRanks.put(key, rank);
			}
		}
		DataManager.get().saveAll();
		if (server == null) {
			return null;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (skip(player)) {
				continue;
			}
			applyEffects(player);
			InvButtons.sync(player);
			DIRTY.add(key(record(player).nickname));
		}
		broadcastNames(server, null);
		return null;
	}

	/**
	 * Применить дерево к сущности: атрибуты и кэш для миксинов. Админ, управляющий NPC, получает дерево NPC,
	 * а сам NPC — всегда своё; после выхода из NPC админу возвращается его собственное.
	 */
	public static void applyEffects(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		applyTo(player);
		NpcPlayerEntity npc = NpcService.controlledNpc(player);
		if (npc != null) {
			applyTo(npc);
		}
	}

	private static void applyTo(ServerPlayerEntity player) {
		double[] t = ru.admincontrol.cw.CwStoryRules.inside(player)
				? new double[SkillTree.Stat.values().length] : totals(record(player));
		SkillEffects.setServer(player.getUuid(), t);
		// Ивент: у всех максимум 20 HP независимо от прокачки (остальные эффекты дерева не меняются).
		modifier(player, EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_ID, "Дерево навыков: здоровье",
				ru.admincontrol.lumira.LumiraHealth.inEvent(player) ? 0 : t[SkillTree.Stat.HEALTH.ordinal()],
				EntityAttributeModifier.Operation.ADDITION);
		ru.admincontrol.lumira.LumiraHealth.apply(player);
		modifier(player, EntityAttributes.GENERIC_ARMOR, ARMOR_ID, "Дерево навыков: броня",
				t[SkillTree.Stat.ARMOR.ordinal()], EntityAttributeModifier.Operation.ADDITION);
		modifier(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, TOUGHNESS_ID, "Дерево навыков: прочность брони",
				t[SkillTree.Stat.TOUGHNESS.ordinal()], EntityAttributeModifier.Operation.ADDITION);
		modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, "Дерево навыков: скорость",
				t[SkillTree.Stat.SPEED.ordinal()] / 100.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
		modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, "Дерево навыков: урон",
				t[SkillTree.Stat.DAMAGE.ordinal()], EntityAttributeModifier.Operation.ADDITION);
		modifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED_ID, "Дерево навыков: скорость атаки",
				t[SkillTree.Stat.ATTACK_SPEED.ordinal()] / 100.0, EntityAttributeModifier.Operation.MULTIPLY_BASE);
		modifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Дерево навыков: стойкость",
				t[SkillTree.Stat.KNOCKBACK.ordinal()] / 100.0, EntityAttributeModifier.Operation.ADDITION);
		modifier(player, EntityAttributes.GENERIC_LUCK, LUCK_ID, "Дерево навыков: удача",
				t[SkillTree.Stat.LUCK.ordinal()], EntityAttributeModifier.Operation.ADDITION);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		if (!skip(player) && ServerPlayNetworking.canSend(player, ModPackets.SKILL_SELF)) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeDouble(t[SkillTree.Stat.JUMP.ordinal()]);
			buf.writeDouble(t[SkillTree.Stat.MINING.ordinal()]);
			buf.writeDouble(t[SkillTree.Stat.AIR.ordinal()]);
			ServerPlayNetworking.send(player, ModPackets.SKILL_SELF, buf);
		}
	}

	/** «Восстановление»: каждые 5 с лечит, если сытость не ниже 18. */
	private static void regen(ServerPlayerEntity player) {
		double perMinute = SkillEffects.server(player, SkillTree.Stat.REGEN);
		if (perMinute <= 0 || player.isDead() || player.getHealth() >= player.getMaxHealth()
				|| player.getHungerManager().getFoodLevel() < 18) {
			return;
		}
		player.heal((float) (perMinute * POLL_SECONDS / 60.0));
	}

	/** «Глаза шахтёра»: в темноте (свет у глаз ниже 6) ночное зрение без частиц, обновляется каждый опрос. */
	private static void nightEye(ServerPlayerEntity player) {
		if (SkillEffects.server(player, SkillTree.Stat.NIGHT_EYE) <= 0 || player.isDead()) {
			return;
		}
		if (player.getWorld().getLightLevel(player.getBlockPos().up()) >= 6) {
			return;
		}
		StatusEffectInstance have = player.getStatusEffect(StatusEffects.NIGHT_VISION);
		if (have != null && have.getDuration() > 320) {
			return;
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 320, 0, true, false, true));
	}

	private static void modifier(ServerPlayerEntity player, EntityAttribute attribute, UUID id, String name, double value,
			EntityAttributeModifier.Operation op) {
		EntityAttributeInstance inst = player.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		if (inst.getModifier(id) != null) {
			inst.removeModifier(id);
		}
		if (value != 0) {
			inst.addPersistentModifier(new EntityAttributeModifier(id, name, value, op));
		}
	}

	/** Дистанции ников всех игроков со «Скрытностью» — каждому клиенту. */
	public static void broadcastNames(MinecraftServer server, UUID leaving) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		List<String> nicks = new ArrayList<>();
		List<int[]> dist = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.getUuid().equals(leaving) || NpcService.isControlling(p)) {
				continue;
			}
			double shade = SkillEffects.server(p, SkillTree.Stat.NAME);
			double quiet = SkillEffects.server(p, SkillTree.Stat.SNEAK_NAME);
			if (shade <= 0 && quiet <= 0) {
				continue;
			}
			nicks.add(p.getGameProfile().getName());
			dist.add(new int[] {SkillTree.nameDistance(shade), SkillTree.sneakDistance(shade, quiet)});
		}
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getUuid().equals(leaving) || !ServerPlayNetworking.canSend(viewer, ModPackets.SKILL_NAMES)) {
				continue;
			}
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(nicks.size());
			for (int i = 0; i < nicks.size(); i++) {
				buf.writeString(nicks.get(i), 16);
				buf.writeVarInt(dist.get(i)[0]);
				buf.writeVarInt(dist.get(i)[1]);
			}
			ServerPlayNetworking.send(viewer, ModPackets.SKILL_NAMES, buf);
		}
	}

	private static void keepExperience(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
		if (skip(oldPlayer) || newPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			return;
		}
		double pct = totals(record(oldPlayer))[SkillTree.Stat.LEGACY.ordinal()];
		if (pct <= 0 || oldPlayer.totalExperience <= 0) {
			return;
		}
		int keep = (int) Math.floor(oldPlayer.totalExperience * pct / 100.0);
		if (keep > 0) {
			newPlayer.addExperience(keep);
		}
	}

	// ---------- вход / выход / тик ----------

	public static void onJoin(ServerPlayerEntity player) {
		if (skip(player)) {
			return;
		}
		ensureDay(data(record(player)));
		applyEffects(player);
		broadcastNames(player.getServer(), null);
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		boolean hadNames = SkillEffects.server(player, SkillTree.Stat.NAME) > 0 || SkillEffects.server(player, SkillTree.Stat.SNEAK_NAME) > 0;
		SkillEffects.clearServer(id);
		VIEWING.remove(id);
		OPEN.remove(id);
		LAST_BUY.remove(id);
		LAST_POSE.remove(id);
		if (hadNames) {
			broadcastNames(player.getServer(), id);
		}
	}

	public static void tick(MinecraftServer server, int tickCounter) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		if (tickCounter % POLL_TICKS == 0) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (!skip(player) && !AuthService.pending(player)) {
					poll(player);
					regen(player);
					nightEye(player);
				}
			}
		}
		if (tickCounter % 20 == 0 && !DIRTY.isEmpty()) {
			Set<String> dirty = new HashSet<>(DIRTY);
			DIRTY.clear();
			for (UUID id : OPEN) {
				ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(id);
				if (viewer == null) {
					OPEN.remove(id);
					continue;
				}
				String target = VIEWING.getOrDefault(id, record(viewer).nickname);
				if (dirty.contains(key(target))) {
					refresh(viewer, null, true);
				}
			}
		}
	}

	// ---------- окно ----------

	public static void open(ServerPlayerEntity player) {
		if (skip(player)) {
			return;
		}
		if (ru.admincontrol.cw.CwStoryRules.denyUi(player, "Дерево навыков")) return;
		VIEWING.remove(player.getUuid());
		sendState(player, record(player), false, true, "", true);
	}

	/** Открыть отдельный понятный экран заданий, минуя дерево навыков. */
	public static void openQuests(ServerPlayerEntity player) {
		if (skip(player)) {
			return;
		}
		if (ru.admincontrol.cw.CwStoryRules.denyUi(player, "Задания дерева навыков")) return;
		VIEWING.remove(player.getUuid());
		sendState(player, record(player), false, true, "", true, true);
	}

	public static void openView(ServerPlayerEntity admin, PlayerRecord target) {
		if (skip(admin) || target == null) {
			return;
		}
		if (target.nickname != null && target.nickname.equalsIgnoreCase(admin.getGameProfile().getName())) {
			open(admin);
			return;
		}
		VIEWING.put(admin.getUuid(), target.nickname);
		sendState(admin, target, true, true, "", true);
	}

	public static void onClose(ServerPlayerEntity player) {
		if (player != null) {
			OPEN.remove(player.getUuid());
			VIEWING.remove(player.getUuid());
		}
	}

	public static void refresh(ServerPlayerEntity player, String notice, boolean ok) {
		if (skip(player)) {
			return;
		}
		String target = VIEWING.get(player.getUuid());
		if (target == null) {
			sendState(player, record(player), false, false, notice, ok);
			return;
		}
		PlayerRecord rec = DataManager.get().findByNick(target).orElse(null);
		if (rec == null) {
			VIEWING.remove(player.getUuid());
			sendState(player, record(player), false, false, "Профиль " + target + " не найден.", false);
			return;
		}
		sendState(player, rec, true, false, notice, ok);
	}

	private static void sendState(ServerPlayerEntity viewer, PlayerRecord target, boolean viewOnly, boolean open,
			String notice, boolean ok) {
		sendState(viewer, target, viewOnly, open, notice, ok, false);
	}

	private static void sendState(ServerPlayerEntity viewer, PlayerRecord target, boolean viewOnly, boolean open,
			String notice, boolean ok, boolean questPage) {
		if (!ServerPlayNetworking.canSend(viewer, ModPackets.SKILL_STATE)) {
			if (open) {
				Chat.send(viewer, Chat.red("Дерево навыков требует мод Admin Control на клиенте той же версии."));
			}
			return;
		}
		SkillData sd = data(target);
		ensureDay(sd);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(open);
		buf.writeBoolean(viewOnly);
		buf.writeString(cut(target.nickname, 32), 32);
		buf.writeString(cut(notice == null ? "" : notice, 256), 256);
		buf.writeBoolean(ok);
		buf.writeVarInt(SkillTree.CURRENCIES.size());
		for (Currency c : SkillTree.CURRENCIES) {
			buf.writeString(c.id(), 16);
			buf.writeVarInt(sd.coins(c.id()));
			buf.writeVarInt(Math.max(0, sd.earned.getOrDefault(c.id(), 0)));
			buf.writeVarInt(Math.max(0, sd.spent.getOrDefault(c.id(), 0)));
		}
		buf.writeVarInt(SkillTree.NODES.size());
		for (Node node : SkillTree.NODES) {
			int lvl = level(target, node);
			int max = maxLevel(node);
			buf.writeString(node.id, 32);
			buf.writeVarInt(lvl);
			buf.writeVarInt(max);
			buf.writeString(cut(note(node, lvl, max), 200), 200);
		}
		buf.writeVarInt(SkillTree.QUESTS.size());
		for (Quest q : SkillTree.QUESTS) {
			buf.writeString(q.id(), 32);
			buf.writeVarLong(Math.max(0, sd.progress(q.id())));
			buf.writeVarInt(sd.stage(q.id()));
		}
		buf.writeVarInt(SkillTree.DAILIES.size());
		for (Daily d : SkillTree.DAILIES) {
			buf.writeString(d.id(), 32);
			buf.writeVarLong(Math.max(0, sd.dailyValue("q:" + d.id())));
			buf.writeBoolean(sd.dailyDone.contains(d.id()));
		}
		buf.writeVarLong(Math.max(0, sd.lifeSeconds));
		int n = Math.min(12, sd.history.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			buf.writeString(cut(sd.history.get(sd.history.size() - 1 - i), 200), 200);
		}
		List<String> off = new ArrayList<>();
		for (SkillTree.Branch b : SkillTree.BRANCHES) {
			if (branchOff(target, b)) {
				off.add(b.id());
			}
		}
		buf.writeVarInt(off.size());
		for (String id : off) {
			buf.writeString(id, 16);
		}
		buf.writeBoolean(questPage);
		ServerPlayNetworking.send(viewer, ModPackets.SKILL_STATE, buf);
		if (open) {
			OPEN.add(viewer.getUuid());
		}
	}

	private static String note(Node node, int lvl, int max) {
		if (node == SkillTree.FURNACE) {
			ru.admincontrol.data.ModSettings s = PlayFeatures.s();
			String now = lvl <= 0 ? "нет" : s.furnaceCountFor(lvl) + " шт. за " + s.furnaceSecondsFor(lvl) + " сек";
			String next = lvl >= max ? "максимум" : s.furnaceCountFor(lvl + 1) + " шт. за " + s.furnaceSecondsFor(lvl + 1) + " сек";
			return "Сейчас: " + now + ". Следующий: " + next + ".";
		}
		return "";
	}

	private static String cut(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max);
	}

	// ---------- покупка ----------

	public static void buy(ServerPlayerEntity player, String nodeId, int expectedLevel) {
		if (skip(player) || AuthService.pending(player)) {
			return;
		}
		if (ru.admincontrol.cw.CwStoryRules.denyUi(player, "Покупка навыков")) return;
		long now = System.currentTimeMillis();
		Long last = LAST_BUY.get(player.getUuid());
		if (last != null && now - last < BUY_COOLDOWN_MS) {
			return;
		}
		LAST_BUY.put(player.getUuid(), now);
		if (VIEWING.containsKey(player.getUuid())) {
			refresh(player, "Это просмотр чужого дерева — покупки недоступны.", false);
			return;
		}
		Node node = SkillTree.node(nodeId);
		if (node == null) {
			return;
		}
		PlayerRecord rec = record(player);
		if (nodeOff(rec, node)) {
			refresh(player, "Ветка «" + node.branch.title() + "» отключена администрацией.", false);
			return;
		}
		SkillData sd = data(rec);
		int lvl = level(rec, node);
		int max = maxLevel(node);
		if (expectedLevel != lvl) {
			refresh(player, "Данные обновились: «" + node.title + "» уже ур. " + lvl + ". Проверьте и нажмите ещё раз.", false);
			return;
		}
		if (lvl >= max) {
			refresh(player, "«" + node.title + "» уже на максимальном уровне.", false);
			return;
		}
		String missing = missingRequirement(rec, node);
		if (missing != null) {
			refresh(player, "Сначала откройте: " + missing + ".", false);
			return;
		}
		Currency cur = node.branch.currency();
		int cost = node.cost(lvl + 1);
		int balance = sd.coins(cur.id());
		if (balance < cost) {
			refresh(player, "Не хватает " + (cost - balance) + " " + cur.coin() + ": нужно " + cost + ", есть " + balance + ".", false);
			return;
		}
		int next = lvl + 1;
		sd.coins.put(cur.id(), balance - cost);
		sd.spent.merge(cur.id(), cost, Integer::sum);
		setLevel(rec, node, next);
		history(sd, "Куплено «" + node.title + "» ур. " + next + " за " + cost + " (" + cur.title() + ")");
		DataManager.get().markDirty();
		ActivityLog.player(player.getGameProfile().getName(), "Дерево навыков: куплено «" + node.title + "» ур. " + next
				+ " за " + cost + " " + cur.coin() + " (осталось " + (balance - cost) + ")");
		applyEffects(player);
		if (migrated(node)) {
			InvButtons.sync(player);
		}
		if (node.stat == SkillTree.Stat.NAME || node.stat == SkillTree.Stat.SNEAK_NAME) {
			broadcastNames(player.getServer(), null);
		}
		String effect = node == SkillTree.FURNACE ? note(node, next, max) : SkillTree.effect(node, next);
		Chat.send(player, Chat.green("Дерево навыков: «" + node.title + "» ур. " + next + " — " + effect));
		refresh(player, "Куплено: «" + node.title + "» ур. " + next + ". " + effect, true);
		DIRTY.add(key(rec.nickname));
	}

	// ---------- задания ----------

	private static void award(ServerPlayerEntity player, PlayerRecord rec, Currency cur, int amount, String why) {
		if (amount <= 0) {
			return;
		}
		SkillData sd = data(rec);
		int before = sd.coins(cur.id());
		int after = (int) Math.min(MAX_COINS, (long) before + amount);
		sd.coins.put(cur.id(), after);
		sd.earned.merge(cur.id(), after - before, Integer::sum);
		history(sd, "+" + (after - before) + " " + cur.title() + " — " + why);
		ActivityLog.player(rec.nickname, "Дерево навыков: +" + (after - before) + " " + cur.coin() + " — " + why);
		if (player != null) {
			Chat.send(player, Chat.gold("Дерево навыков: +" + (after - before) + " " + cur.coin() + " — " + why + ". /skills"));
		}
		DataManager.get().markDirty();
	}

	/** Награда коинами дерева без сообщения в чат. */
	public static void awardSilent(ServerPlayerEntity player, Currency cur, int amount, String why) {
		if (player == null || cur == null || amount <= 0 || DataManager.get() == null) {
			return;
		}
		PlayerRecord rec = record(player);
		if (branchOff(rec, SkillTree.branchOf(cur))) {
			return;
		}
		award(player, rec, cur, amount, why);
		DIRTY.add(key(rec.nickname));
	}

	/** Обмен физических монет на коины дерева (того, чьё дерево сейчас у игрока). Возвращает, сколько монет принято. */
	public static int redeemCoins(ServerPlayerEntity player, Currency cur, int amount) {
		if (player == null || cur == null || amount <= 0 || DataManager.get() == null) {
			return 0;
		}
		PlayerRecord rec = record(player);
		SkillData sd = data(rec);
		int before = sd.coins(cur.id());
		int taken = (int) Math.min(amount, Math.max(0L, (long) MAX_COINS - before));
		if (taken <= 0) {
			return 0;
		}
		sd.coins.put(cur.id(), before + taken);
		history(sd, "+" + taken + " " + cur.title() + " — обмен монет");
		ActivityLog.player(rec.nickname, "Дерево навыков: обменял " + taken + " монет на " + cur.coin()
				+ " (было " + before + ", стало " + (before + taken) + ")");
		DataManager.get().markDirty();
		DIRTY.add(key(rec.nickname));
		return taken;
	}

	public static int coins(ServerPlayerEntity player, Currency cur) {
		return player == null || cur == null || DataManager.get() == null ? 0 : data(record(player)).coins(cur.id());
	}

	/**
	 * Задания дерева навыков технически не выполняются в сюжетных мирах (личных, тестовых) и на стройке карты:
	 * прогресс, этапы и награды засчитываются только в обычном мире. Счётчики-базовые значения продолжают
	 * двигаться, поэтому сделанное в сюжете не засчитается задним числом после выхода.
	 */
	private static boolean questsBlocked(ServerPlayerEntity player) {
		return player != null && InvButtons.blocked(player);
	}

	private static void checkQuest(ServerPlayerEntity player, PlayerRecord rec, Quest q) {
		if (questsBlocked(player)) {
			return;
		}
		SkillData sd = data(rec);
		int stage = sd.stage(q.id());
		long value = sd.progress(q.id());
		while (stage < q.stages() && value >= q.goals()[stage]) {
			award(player, rec, q.currency(), q.rewards()[stage], q.title() + ", этап " + (stage + 1) + "/" + q.stages());
			stage++;
			sd.stages.put(q.id(), stage);
		}
	}

	private static void add(ServerPlayerEntity player, PlayerRecord rec, String questId, long delta) {
		if (questsBlocked(player)) {
			return;
		}
		Quest q = SkillTree.quest(questId);
		if (q == null || delta <= 0 || branchOff(rec, SkillTree.branchOf(q.currency()))) {
			return;
		}
		SkillData sd = data(rec);
		if (sd.stage(q.id()) >= q.stages()) {
			return;
		}
		sd.progress.put(q.id(), sd.progress(q.id()) + delta);
		checkQuest(player, rec, q);
		touched(rec);
	}

	private static void max(ServerPlayerEntity player, PlayerRecord rec, String questId, long value) {
		if (questsBlocked(player)) {
			return;
		}
		Quest q = SkillTree.quest(questId);
		if (q == null || branchOff(rec, SkillTree.branchOf(q.currency()))) {
			return;
		}
		SkillData sd = data(rec);
		if (value <= sd.progress(q.id()) || sd.stage(q.id()) >= q.stages()) {
			return;
		}
		sd.progress.put(q.id(), value);
		checkQuest(player, rec, q);
		touched(rec);
	}

	private static void seen(ServerPlayerEntity player, PlayerRecord rec, String set, String value, String questId) {
		if (questsBlocked(player)) {
			return;
		}
		SkillData sd = data(rec);
		List<String> list = sd.seen.computeIfAbsent(set, k -> new ArrayList<>());
		if (value == null || list.contains(value) || list.size() >= 512) {
			return;
		}
		list.add(value);
		max(player, rec, questId, list.size());
		touched(rec);
	}

	private static void daily(ServerPlayerEntity player, PlayerRecord rec, String dailyId, long delta) {
		if (questsBlocked(player)) {
			return;
		}
		Daily d = SkillTree.daily(dailyId);
		if (d == null || delta <= 0 || branchOff(rec, SkillTree.branchOf(d.currency()))) {
			return;
		}
		SkillData sd = data(rec);
		ensureDay(sd);
		if (sd.dailyDone.contains(d.id())) {
			return;
		}
		long value = sd.dailyValue("q:" + d.id()) + delta;
		sd.daily.put("q:" + d.id(), value);
		if (value >= d.goal()) {
			sd.dailyDone.add(d.id());
			award(player, rec, d.currency(), d.reward(), "ежедневное: " + d.title().replace("Сегодня: ", ""));
		}
		touched(rec);
	}

	/** Сколько из {@code delta} ещё можно засчитать сегодня по ключу лимита. */
	private static long cap(SkillData sd, String key, long delta, long cap) {
		ensureDay(sd);
		long used = sd.dailyValue("cap:" + key);
		long allowed = Math.max(0, Math.min(delta, cap - used));
		if (allowed > 0) {
			sd.daily.put("cap:" + key, used + allowed);
		}
		return allowed;
	}

	private static void touched(PlayerRecord rec) {
		DataManager.get().markDirty();
		DIRTY.add(key(rec.nickname));
	}

	private static boolean noProgress(ServerPlayerEntity player) {
		return skip(player) || AuthService.pending(player) || player.isCreative() || player.isSpectator();
	}

	public static void onTrainingDone(ServerPlayerEntity player) {
		if (player == null || skip(player)) {
			return;
		}
		PlayerRecord rec = record(player);
		add(player, rec, "sync_train", 1);
		add(player, rec, "sync_athlete", 1);
		daily(player, rec, "d_sync", 1);
	}

	public static void onCasinoSpin(ServerPlayerEntity player, long bet) {
		if (noProgress(player) || bet < 100) {
			return;
		}
		add(player, record(player), "chips_spins", 1);
		add(player, record(player), "chips_regular", 1);
	}

	public static void onMachineRepaired(ServerPlayerEntity player) {
		if (noProgress(player)) {
			return;
		}
		PlayerRecord rec = record(player);
		add(player, rec, "chips_repair", 1);
		add(player, rec, "chips_regular", 1);
		daily(player, rec, "d_chips", 1);
	}

	public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
		if (noProgress(player) || victim == null || victim instanceof PlayerEntity || !(victim instanceof Monster)) {
			return;
		}
		if (RECENT_KILLS.put(victim.getUuid(), Boolean.TRUE) != null) {
			return;
		}
		PlayerRecord rec = record(player);
		SkillData sd = data(rec);
		String type = Registries.ENTITY_TYPE.getId(victim.getType()).toString();
		boolean boss = BOSSES.contains(type);
		if (cap(sd, "kill:" + type, 1, KILL_CAP_PER_TYPE) <= 0 && !boss) {
			return;
		}
		add(player, rec, "fight_kills", 1);
		add(player, rec, "life_hunter", 1);
		daily(player, rec, "d_fight", 1);
		seen(player, rec, "hostile", type, "fight_types");
		if (victim.getGroup() == EntityGroup.UNDEAD) {
			add(player, rec, "life_undead", 1);
			add(player, rec, "fight_undead", 1);
		}
		if (ELITE.contains(type)) {
			add(player, rec, "fight_elite", 1);
		}
		if (boss) {
			seen(player, rec, "bosses", type, "fight_boss");
		}
		if (player.isSneaking()) {
			add(player, rec, "shadow_strike", 1);
		}
	}

	private static long placedKey(World world, BlockPos pos) {
		return pos.asLong() * 31L + world.getRegistryKey().getValue().hashCode();
	}

	public static void onPlaced(World world, BlockPos pos) {
		if (world != null && pos != null && !world.isClient) {
			PLACED.put(placedKey(world, pos), Boolean.TRUE);
		}
	}

	public static void onBreak(ServerPlayerEntity player, World world, BlockPos pos, BlockState state) {
		if (world == null || pos == null || state == null) {
			return;
		}
		if (PLACED.remove(placedKey(world, pos)) != null || noProgress(player)) {
			return;
		}
		Identifier id = Registries.BLOCK.getId(state.getBlock());
		String path = id.getPath();
		PlayerRecord rec = record(player);
		SkillData sd = data(rec);
		if (state.isIn(ConventionalBlockTags.ORES) || path.endsWith("_ore") || path.equals("ancient_debris")) {
			if (cap(sd, "ore", 1, ORE_CAP) <= 0) {
				return;
			}
			add(player, rec, "ore_ores", 1);
			add(player, rec, "ore_miner", 1);
			daily(player, rec, "d_ore", 1);
			seen(player, rec, "ores", oreType(id), "ore_types");
		} else if (state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.BASE_STONE_NETHER)
				|| path.equals("end_stone")) {
			if (cap(sd, "stone", 1, STONE_CAP) > 0) {
				add(player, rec, "ore_stone", 1);
				add(player, rec, "ore_miner", 1);
			}
		} else if (state.isIn(BlockTags.LOGS)) {
			if (cap(sd, "log", 1, LOG_CAP) > 0) {
				add(player, rec, "ore_logs", 1);
				add(player, rec, "ore_miner", 1);
			}
		}
	}

	private static String oreType(Identifier id) {
		String path = id.getPath();
		if (path.startsWith("deepslate_")) {
			path = path.substring("deepslate_".length());
		}
		if (path.endsWith("_ore")) {
			path = path.substring(0, path.length() - 4);
		}
		if (path.equals("nether_quartz")) {
			path = "quartz";
		}
		return id.getNamespace() + ":" + path;
	}

	public static void onEat(ServerPlayerEntity player, ItemStack stack) {
		if (noProgress(player) || stack == null || stack.isEmpty() || !stack.isFood()) {
			return;
		}
		seen(player, record(player), "foods", Registries.ITEM.getId(stack.getItem()).toString(), "life_food");
	}

	private static void onPlayerDeath(ServerPlayerEntity player) {
		if (skip(player)) {
			return;
		}
		PlayerRecord rec = record(player);
		data(rec).lifeSeconds = 0;
		touched(rec);
	}

	private static long stat(ServerPlayerEntity player, Identifier custom) {
		Stat<Identifier> s = Stats.CUSTOM.getOrCreateStat(custom);
		return Math.max(0, player.getStatHandler().getStat(s));
	}

	/** Прирост статистики с прошлого опроса; первый опрос только запоминает значение. */
	private static long delta(SkillData sd, String key, long current, long maxDelta) {
		Long base = sd.stats.put(key, current);
		if (base == null || current < base) {
			return 0;
		}
		return Math.min(current - base, maxDelta);
	}

	/** Активен ли игрок с прошлого опроса: поворачивал камеру или сам двигался (не в воде и не в транспорте). */
	private static boolean active(ServerPlayerEntity player) {
		double[] now = {player.getX(), player.getZ(), player.getYaw(), player.getPitch()};
		double[] last = LAST_POSE.put(player.getUuid(), now);
		if (last == null) {
			return false;
		}
		boolean turned = Math.abs(now[2] - last[2]) > 2.0 || Math.abs(now[3] - last[3]) > 2.0;
		double dx = now[0] - last[0];
		double dz = now[1] - last[1];
		boolean moved = dx * dx + dz * dz > 2.25 && !player.hasVehicle() && !player.isTouchingWater();
		return turned || moved;
	}

	private static void poll(ServerPlayerEntity player) {
		PlayerRecord rec = record(player);
		SkillData sd = data(rec);
		ensureDay(sd);
		boolean active = active(player) && !player.isSpectator() && !player.isCreative();
		String own = rec.uuid != null && rec.uuid.equals(player.getUuidAsString()) ? "" : "@" + player.getUuidAsString();
		long dWalk = delta(sd, "walk" + own, stat(player, Stats.WALK_ONE_CM) + stat(player, Stats.SPRINT_ONE_CM), 10_000);
		long dCrouch = delta(sd, "crouch" + own, stat(player, Stats.CROUCH_ONE_CM), 3_000);
		long dJump = delta(sd, "jump" + own, stat(player, Stats.JUMP), 40);
		long dEnchant = delta(sd, "enchant" + own, stat(player, Stats.ENCHANT_ITEM), 20);
		long dTrade = delta(sd, "trade" + own, stat(player, Stats.TRADED_WITH_VILLAGER), 40);
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		if (dEnchant > 0) {
			add(player, rec, "mind_enchant", dEnchant);
			add(player, rec, "mind_study", dEnchant);
		}
		if (dTrade > 0) {
			add(player, rec, "mind_trade", dTrade);
			add(player, rec, "mind_study", dTrade);
			daily(player, rec, "d_mind", dTrade);
		}
		max(player, rec, "mind_level", player.experienceLevel);
		if (!active) {
			return;
		}
		long walk = cap(sd, "walk", dWalk, WALK_CAP_CM);
		add(player, rec, "path_walk", walk);
		add(player, rec, "path_marathon", walk);
		daily(player, rec, "d_path", walk);
		long sneak = cap(sd, "sneak", dCrouch, SNEAK_CAP_CM);
		add(player, rec, "shadow_sneak", sneak);
		add(player, rec, "shadow_master", sneak);
		daily(player, rec, "d_shadow", sneak);
		add(player, rec, "path_jump", cap(sd, "jump", dJump, JUMP_CAP));
		sd.lifeSeconds += POLL_SECONDS;
		max(player, rec, "life_alive", sd.lifeSeconds);
		daily(player, rec, "d_life", POLL_SECONDS);
		World world = player.getWorld();
		BlockPos pos = player.getBlockPos();
		if (world.getRegistryKey() == World.OVERWORLD) {
			if (world.isNight() && world.isSkyVisible(pos.up())) {
				add(player, rec, "shadow_night", POLL_SECONDS);
			}
			BlockPos spawn = world.getSpawnPos();
			double dx = player.getX() - spawn.getX();
			double dz = player.getZ() - spawn.getZ();
			long dist = (long) Math.sqrt(dx * dx + dz * dz);
			long limit = sd.progress("path_far") + dWalk / 100 + 16;
			max(player, rec, "path_far", Math.min(dist, limit));
		}
		world.getBiome(pos).getKey().ifPresent(k -> seen(player, rec, "biomes", k.getValue().toString(), "path_biomes"));
		touched(rec);
	}

	// ---------- администрирование ----------

	/** Выдать или забрать коины. Возвращает текст ошибки или null при успехе. */
	public static String adjust(MinecraftServer server, String adminNick, PlayerRecord rec, Currency cur, int amount,
			boolean take) {
		if (rec == null || cur == null) {
			return "Игрок или валюта не найдены.";
		}
		if (amount <= 0) {
			return "Количество должно быть больше нуля.";
		}
		SkillData sd = data(rec);
		int before = sd.coins(cur.id());
		int after;
		if (take) {
			if (amount > before) {
				return "У " + rec.nickname + " только " + before + " " + cur.coin() + " — нельзя забрать " + amount + ".";
			}
			after = before - amount;
		} else {
			if ((long) before + amount > MAX_COINS) {
				return "Лимит " + MAX_COINS + " " + cur.coin() + ": сейчас " + before + ".";
			}
			after = before + amount;
		}
		sd.coins.put(cur.id(), after);
		String verb = take ? "забрал" : "выдал";
		history(sd, "Администратор " + adminNick + " " + verb + " " + amount + " " + cur.title() + " (" + before + " → " + after + ")");
		DataManager.get().markDirty();
		ActivityLog.admin(adminNick, "Дерево навыков: " + verb + " " + amount + " " + cur.coin() + " игроку "
				+ rec.nickname + " (было " + before + ", стало " + after + ")");
		ActivityLog.player(rec.nickname, "Дерево навыков: администратор " + adminNick + " " + verb + " " + amount
				+ " " + cur.coin() + " (было " + before + ", стало " + after + ")");
		ServerPlayerEntity online = server == null ? null : server.getPlayerManager().getPlayer(rec.nickname);
		if (online != null) {
			Chat.send(online, take
					? Chat.red("Администратор забрал " + amount + " " + cur.coin() + ". Осталось " + after + ".")
					: Chat.gold("Администратор выдал " + amount + " " + cur.coin() + ". Теперь " + after + ". /skills"));
		}
		DIRTY.add(key(rec.nickname));
		return null;
	}

	public static List<String> summary(PlayerRecord rec) {
		SkillData sd = data(rec);
		ensureDay(sd);
		List<String> out = new ArrayList<>();
		out.add("Дерево навыков: " + rec.nickname);
		StringBuilder bal = new StringBuilder("Баланс: ");
		for (Currency c : SkillTree.CURRENCIES) {
			bal.append(c.title()).append(' ').append(sd.coins(c.id()))
					.append(" (получено ").append(sd.earned.getOrDefault(c.id(), 0))
					.append(", потрачено ").append(sd.spent.getOrDefault(c.id(), 0)).append(")  ");
		}
		out.add(bal.toString().trim());
		for (SkillTree.Branch b : SkillTree.BRANCHES) {
			StringBuilder line = new StringBuilder(b.title() + ": ");
			boolean any = false;
			for (Node node : SkillTree.NODES) {
				if (node.branch != b) {
					continue;
				}
				line.append(node.title).append(' ').append(level(rec, node)).append('/').append(maxLevel(node)).append("; ");
				any = true;
			}
			if (any) {
				out.add(line.toString().trim());
			}
		}
		for (Quest q : SkillTree.QUESTS) {
			int stage = sd.stage(q.id());
			String goal = stage >= q.stages() ? "выполнено" : SkillTree.format(sd.progress(q.id()), q.divisor(), q.unit())
					+ "/" + SkillTree.format(q.goals()[stage], q.divisor(), q.unit());
			out.add("  " + q.title() + " [" + q.currency().title() + "]: этап " + Math.min(stage + 1, q.stages()) + "/"
					+ q.stages() + " — " + goal);
		}
		for (Daily d : SkillTree.DAILIES) {
			boolean done = sd.dailyDone.contains(d.id());
			out.add("  " + d.title() + ": " + (done ? "выполнено" : SkillTree.format(sd.dailyValue("q:" + d.id()), d.divisor(), d.unit())
					+ "/" + SkillTree.format(d.goal(), d.divisor(), d.unit())));
		}
		return out;
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		for (PlayerRecord rec : DataManager.get().records()) {
			if (rec == null || rec.nps || rec.nickname == null || rec.nickname.isBlank()) {
				continue;
			}
			int bought = 0;
			for (Node node : SkillTree.NODES) {
				bought += level(rec, node);
			}
			int earned = 0;
			int coins = 0;
			if (rec.skills != null) {
				SkillData sd = data(rec);
				for (Currency c : SkillTree.CURRENCIES) {
					earned += sd.earned.getOrDefault(c.id(), 0);
					coins += sd.coins(c.id());
				}
			}
			if (bought == 0 && earned == 0 && coins == 0) {
				continue;
			}
			out.add(rec.nickname + ": уровней куплено " + bought + ", коинов сейчас " + coins + ", заработано всего " + earned);
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}
}
