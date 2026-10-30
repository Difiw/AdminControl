package ru.admincontrol.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.screen.ClanWarehouseFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanWarService {
	public static final String[] CORE_BLOCKS = {
			"minecraft:netherite_block",
			"minecraft:beacon",
			"minecraft:gold_block",
			"minecraft:diamond_block",
			"minecraft:emerald_block",
			"minecraft:redstone_block",
			"minecraft:amethyst_block",
			"minecraft:crying_obsidian",
			"minecraft:lodestone",
			"minecraft:ancient_debris"
	};

	private static Challenge pending;
	private static War war;
	private static final ConcurrentHashMap<String, Long> cooldownUntil = new ConcurrentHashMap<>();

	private ClanWarService() {
	}

	public static final class Challenge {
		public String id = "";
		public String fromId = "";
		public String toId = "";
		public UUID challenger;
		public long expiresAt;
	}

	public static final class Side {
		public String clanId = "";
		public BlockPos core;
		public BlockPos chest;
		public BlockState saved;
		public NbtCompound savedBe;
		public String dimension = "";
		public boolean broken;
	}

	public static final class War {
		public final Side a = new Side();
		public final Side b = new Side();
		public long startedAt;
		public long maxUntil;
		public long lootUntil;
		public BlockPos jailMin;
		public final Set<UUID> jailed = ConcurrentHashMap.newKeySet();
		public boolean ending;
		public boolean scored;
	}

	public static boolean active() {
		return war != null;
	}

	public static boolean fightingClan(String clanId) {
		return war != null && clanId != null && (clanId.equals(war.a.clanId) || clanId.equals(war.b.clanId));
	}

	public static boolean privateSuppressed(String clanId) {
		return fightingClan(clanId);
	}

	public static boolean jailed(ServerPlayerEntity player) {
		return player != null && war != null && war.jailed.contains(player.getUuid());
	}

	public static boolean memberOfWar(ServerPlayerEntity player) {
		if (war == null || player == null) {
			return false;
		}
		ClanRecord clan = ClanService.of(player);
		return clan != null && fightingClan(clan.id);
	}

	public static boolean warehouseLocked(ClanRecord clan, ServerPlayerEntity player) {
		if (war == null || clan == null) {
			return false;
		}
		if (!fightingClan(clan.id)) {
			return false;
		}
		Side own = sideOf(clan.id);
		if (own != null && own.broken && !isEnemy(player, clan.id)) {
			return true;
		}
		return false;
	}

	public static boolean treasuryLocked(ClanRecord clan, ServerPlayerEntity player) {
		return warehouseLocked(clan, player);
	}

	public static boolean canRaid(ClanRecord target, ServerPlayerEntity player) {
		if (war == null || target == null || player == null) {
			return false;
		}
		Side side = sideOf(target.id);
		return side != null && side.broken && isEnemy(player, target.id);
	}

	public static String jailMessage() {
		return "Вы в тюрьме битвы кланов до её окончания.";
	}

	public static String challenge(ServerPlayerEntity player, String clanName) {
		if (player == null) {
			return "Нужен игрок.";
		}
		if (!ClanService.enabled()) {
			return "Кланы выключены.";
		}
		if (war != null) {
			return "Уже идёт битва кланов.";
		}
		if (EventService.active()) {
			return "Сначала дождитесь конца мероприятия.";
		}
		ClanRecord from = ClanService.of(player);
		if (from == null) {
			return "Вы не в клане.";
		}
		if (from.home == null || !from.homeBought) {
			return "Сначала купите и поставьте дом клана.";
		}
		ClanRecord to = ClanService.byName(clanName);
		if (to == null) {
			return ClanService.notFound(clanName);
		}
		if (from.id.equals(to.id)) {
			return "Нельзя вызвать свой клан.";
		}
		if (to.home == null || !to.homeBought) {
			return "У противника нет дома клана.";
		}
		ModSettings s = settings();
		int min = Math.max(1, s.clanWarMinOnline);
		int aOn = onlineMembers(player.getServer(), from);
		int bOn = onlineMembers(player.getServer(), to);
		if (aOn < min || bOn < min) {
			return "Нужно минимум " + min + " игрока в сети в каждом клане (сейчас "
					+ aOn + " и " + bOn + ").";
		}
		long now = System.currentTimeMillis();
		Long cd = cooldownUntil.get(pairKey(from.id, to.id));
		if (cd != null && cd > now) {
			long sec = Math.max(1, (cd - now) / 1000L);
			return "Перезарядка битвы между этими кланами: ещё " + sec + " сек.";
		}
		if (pending != null && pending.expiresAt > now) {
			return "Уже есть заявка на битву. Админы решают её или она истечёт.";
		}
		Challenge ch = new Challenge();
		ch.id = Integer.toHexString((int) (now & 0xfffff));
		ch.fromId = from.id;
		ch.toId = to.id;
		ch.challenger = player.getUuid();
		ch.expiresAt = now + Math.max(30, s.clanWarChallengeSeconds) * 1000L;
		pending = ch;
		if (s.clanWarAutoApprove && aOn >= Math.max(2, min) && bOn >= Math.max(2, min)) {
			String err = start(player.getServer(), ch, "авто-одобрение (/dserver)");
			return err == null ? null : err;
		}
		notifyAdmins(player.getServer(), from, to, ch);
		broadcastClans(player.getServer(), from, to, Chat.gold("Клан «" + from.name + "» вызывает «"
				+ to.name + "» на битву. Ждём решение админов (/cwarok, /cwardeny)."));
		return null;
	}

	public static String accept(ServerPlayerEntity admin, String id) {
		if (pending == null) {
			return "Нет заявки на битву.";
		}
		if (id != null && !id.isBlank() && !id.equalsIgnoreCase(pending.id)) {
			return "Неверный id заявки. Актуальный: " + pending.id;
		}
		Challenge ch = pending;
		return start(admin.getServer(), ch, admin.getGameProfile().getName());
	}

	public static String deny(ServerPlayerEntity admin, String id) {
		if (pending == null) {
			return "Нет заявки на битву.";
		}
		if (id != null && !id.isBlank() && !id.equalsIgnoreCase(pending.id)) {
			return "Неверный id заявки. Актуальный: " + pending.id;
		}
		ClanRecord from = ClanService.find(pending.fromId);
		ClanRecord to = ClanService.find(pending.toId);
		pending = null;
		MinecraftServer server = admin.getServer();
		Chat.broadcast(server, Chat.yellow("Админ " + admin.getGameProfile().getName()
				+ " отклонил битву кланов"
				+ (from == null ? "" : " «" + from.name + "»")
				+ (to == null ? "" : " против «" + to.name + "»") + "."));
		return null;
	}

	public static String stop(MinecraftServer server, String reason) {
		if (war == null) {
			return "Битва кланов не идёт.";
		}
		end(server, reason == null || reason.isBlank() ? "остановлена" : reason);
		return null;
	}

	public static String takeTreasury(ServerPlayerEntity player, int amount) {
		if (war == null) {
			return "Сейчас нет битвы кланов.";
		}
		if (jailed(player)) {
			return jailMessage();
		}
		ClanRecord mine = ClanService.of(player);
		if (mine == null || !fightingClan(mine.id)) {
			return "Вы не в сражающемся клане.";
		}
		ClanRecord target = enemyClan(mine.id);
		if (target == null) {
			return "Противник не найден.";
		}
		if (!canRaid(target, player)) {
			return "Сначала сломайте ядро дома противника.";
		}
		if (target.treasury <= 0) {
			return "Казна противника пуста.";
		}
		int take = amount <= 0 ? target.treasury : Math.min(amount, target.treasury);
		target.treasury -= take;
		Economy.giveCurrency(player, take, "трофей казны клана " + target.name);
		DataManager.get().markDirty();
		Chat.send(player, Chat.green("Забрано из казны «" + target.name + "»: " + Money.wc(take) + "."));
		return null;
	}

	public static boolean allowBreak(ServerPlayerEntity player, BlockPos pos) {
		if (war == null || player == null || pos == null) {
			return true;
		}
		Side side = sideAt(player, pos);
		if (side == null || side.broken || side.core == null || !side.core.equals(pos)) {
			if (side != null && side.chest != null && side.chest.equals(pos)) {
				Chat.send(player, Chat.red("Сундук трофеев нельзя сломать."));
				return false;
			}
			return true;
		}
		if (jailed(player) || !memberOfWar(player)) {
			Chat.send(player, Chat.red("Ядро дома может сломать только вражеский клан."));
			return false;
		}
		if (!isEnemy(player, side.clanId)) {
			Chat.send(player, Chat.red("Нельзя ломать ядро своего клана."));
			return false;
		}
		return true;
	}

	public static void onBroken(ServerPlayerEntity player, BlockPos pos) {
		if (war == null || player == null || pos == null) {
			return;
		}
		Side side = sideAt(player, pos);
		if (side == null || side.broken || side.core == null || !side.core.equals(pos)) {
			return;
		}
		if (!isEnemy(player, side.clanId)) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		vacuumDrops(world, pos);
		side.broken = true;
		side.chest = pos.toImmutable();
		world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), 3);
		ClanRecord victim = ClanService.find(side.clanId);
		ClanRecord raider = ClanService.of(player);
		int loot = Math.max(15, settings().clanWarLootSeconds);
		if (war.lootUntil <= 0L) {
			war.lootUntil = System.currentTimeMillis() + loot * 1000L;
		}
		int left = (int) Math.max(1L, (war.lootUntil - System.currentTimeMillis() + 999L) / 1000L);
		Chat.broadcast(player.getServer(), Chat.gold("Ядро клана «"
				+ (victim == null ? "?" : victim.name) + "» сломано игроком "
				+ player.getGameProfile().getName()
				+ "! На грабёж осталось " + left + " сек. Казна: /clanwar take"));
		if (victim != null && victim.warehouseLevel > 0) {
			player.openHandledScreen(new ClanWarehouseFactory(victim, false));
		}
		if (raider != null) {
			if (!war.scored) {
				war.scored = true;
				ClanTop.warWin(player.getServer(), raider);
			}
			broadcastClan(player.getServer(), raider, Chat.green("Склад и казна «"
					+ (victim == null ? "?" : victim.name) + "» открыты. Сундук на доме — склад, казна: /clanwar take."));
		}
		if (victim != null) {
			broadcastClan(player.getServer(), victim, Chat.red("Ядро сломано. Склад и казна вашего клана закрыты до конца битвы."));
		}
	}

	public static ActionResult useBlock(ServerPlayerEntity player, BlockPos pos) {
		if (war == null || player == null || pos == null) {
			return null;
		}
		if (jailed(player)) {
			Chat.send(player, Chat.red(jailMessage()));
			return ActionResult.FAIL;
		}
		Side side = sideAtChest(player, pos);
		if (side == null || !side.broken) {
			return null;
		}
		ClanRecord target = ClanService.find(side.clanId);
		if (target == null) {
			return ActionResult.FAIL;
		}
		if (!isEnemy(player, target.id)) {
			Chat.send(player, Chat.red("Этот сундук могут открыть только противники клана."));
			return ActionResult.FAIL;
		}
		if (target.warehouseLevel <= 0) {
			Chat.send(player, Chat.yellow("У клана нет склада. Казна: /clanwar take"));
			return ActionResult.SUCCESS;
		}
		player.openHandledScreen(new ClanWarehouseFactory(target, false));
		return ActionResult.SUCCESS;
	}

	public static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		if (pending != null && pending.expiresAt <= now) {
			ClanRecord from = ClanService.find(pending.fromId);
			ClanRecord to = ClanService.find(pending.toId);
			pending = null;
			Chat.broadcast(server, Chat.yellow("Заявка на битву кланов истекла"
					+ (from == null ? "" : " («" + from.name + "»")
					+ (to == null ? "" : " vs «" + to.name + "»")
					+ (from != null || to != null ? ")" : "") + "."));
		}
		if (war == null) {
			return;
		}
		keepJailed(server);
		ensureCores(server);
		if (war.lootUntil > 0L && now >= war.lootUntil) {
			end(server, "время грабежа вышло");
			return;
		}
		if (now >= war.maxUntil) {
			end(server, "время битвы вышло");
		}
	}

	public static void afterRespawn(ServerPlayerEntity player) {
		if (player == null || war == null || !memberOfWar(player) || war.ending) {
			return;
		}
		war.jailed.add(player.getUuid());
		teleportJail(player);
		Chat.send(player, Chat.red("Вас убили в битве кланов. Вы в бедроковой тюрьме до конца боя. Вещи остались на земле."));
	}

	public static void afterJoin(ServerPlayerEntity player) {
		if (jailed(player)) {
			teleportJail(player);
		}
	}

	public static void onDisconnect(ServerPlayerEntity player) {
	}

	private static String start(MinecraftServer server, Challenge ch, String by) {
		if (ch == null) {
			return "Нет заявки.";
		}
		if (war != null) {
			return "Битва уже идёт.";
		}
		if (EventService.active()) {
			return "Сначала дождитесь конца мероприятия.";
		}
		ClanRecord a = ClanService.find(ch.fromId);
		ClanRecord b = ClanService.find(ch.toId);
		if (a == null || b == null || a.home == null || b.home == null) {
			pending = null;
			return "Один из кланов больше недоступен.";
		}
		ModSettings s = settings();
		int min = Math.max(1, s.clanWarMinOnline);
		if (onlineMembers(server, a) < min || onlineMembers(server, b) < min) {
			return "Недостаточно игроков в сети.";
		}
		pending = null;
		War next = new War();
		next.a.clanId = a.id;
		next.b.clanId = b.id;
		next.startedAt = System.currentTimeMillis();
		next.maxUntil = next.startedAt + Math.max(3, s.clanWarMaxMinutes) * 60_000L;
		next.a.dimension = a.home.dimension;
		next.b.dimension = b.home.dimension;
		next.a.core = homePos(a);
		next.b.core = homePos(b);
		placeCore(server, next.a);
		placeCore(server, next.b);
		buildJail(server, next);
		war = next;
		HomeProtection.refresh();
		Chat.broadcast(server, Chat.gold("Битва кланов: «" + a.name + "» против «" + b.name
				+ "»! Приват домов снят. Сломайте ядро (" + coreLabel() + ") на доме врага. Одобрил: " + by + "."));
		return null;
	}

	private static void end(MinecraftServer server, String reason) {
		if (war == null || war.ending) {
			return;
		}
		war.ending = true;
		War done = war;
		for (UUID id : done.jailed) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				player.changeGameMode(GameMode.SURVIVAL);
				SpawnService.teleportWorld(player);
				Chat.send(player, Chat.yellow("Битва кланов окончена. Вы на спавне."));
			}
		}
		removeMarker(server, done.a);
		removeMarker(server, done.b);
		clearJail(server, done);
		ClanRecord a = ClanService.find(done.a.clanId);
		ClanRecord b = ClanService.find(done.b.clanId);
		long cd = System.currentTimeMillis() + Math.max(0, settings().clanWarCooldownMinutes) * 60_000L;
		if (a != null && b != null) {
			cooldownUntil.put(pairKey(a.id, b.id), cd);
		}
		war = null;
		HomeProtection.refresh();
		Chat.broadcast(server, Chat.gold("Битва кланов завершена (" + reason + "). Приваты домов восстановлены, если были куплены. Трофеи остаются у тех, кто их забрал."));
		if (a != null && b != null) {
			String ra = done.a.broken ? "ядро сломано" : "ядро цело";
			String rb = done.b.broken ? "ядро сломано" : "ядро цело";
			Chat.broadcast(server, Chat.yellow("«" + a.name + "»: " + ra + ". «" + b.name + "»: " + rb + "."));
		}
	}

	private static void placeCore(MinecraftServer server, Side side) {
		ServerWorld world = SpawnService.resolveWorld(server, side.dimension);
		if (world == null || side.core == null) {
			return;
		}
		side.saved = world.getBlockState(side.core);
		BlockEntity be = world.getBlockEntity(side.core);
		if (be != null) {
			side.savedBe = be.createNbt();
			world.removeBlockEntity(side.core);
		}
		world.setBlockState(side.core, coreBlock().getDefaultState(), 3);
	}

	private static void removeMarker(MinecraftServer server, Side side) {
		ServerWorld world = SpawnService.resolveWorld(server, side.dimension);
		if (world == null || side.core == null) {
			return;
		}
		BlockPos pos = side.broken && side.chest != null ? side.chest : side.core;
		world.removeBlockEntity(pos);
		if (side.saved != null) {
			world.setBlockState(pos, side.saved, 3);
			if (side.savedBe != null) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be != null) {
					be.readNbt(side.savedBe);
					be.markDirty();
				}
			}
		} else {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
		}
	}

	private static BlockPos homePos(ClanRecord clan) {
		return new BlockPos((int) Math.floor(clan.home.x), (int) Math.floor(clan.home.y), (int) Math.floor(clan.home.z));
	}

	private static Block coreBlock() {
		String raw = settings().clanWarCoreBlock;
		String id = raw == null || raw.isBlank() ? CORE_BLOCKS[0] : raw;
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}
		Identifier ident = Identifier.tryParse(id);
		Block block = ident == null ? Blocks.NETHERITE_BLOCK : Registries.BLOCK.get(ident);
		return block == null || block == Blocks.AIR ? Blocks.NETHERITE_BLOCK : block;
	}

	public static String coreLabel() {
		return Registries.BLOCK.getId(coreBlock()).toString();
	}

	public static String cycleCore() {
		ModSettings s = settings();
		int at = 0;
		for (int i = 0; i < CORE_BLOCKS.length; i++) {
			if (CORE_BLOCKS[i].equalsIgnoreCase(s.clanWarCoreBlock)) {
				at = i;
				break;
			}
		}
		s.clanWarCoreBlock = CORE_BLOCKS[(at + 1) % CORE_BLOCKS.length];
		DataManager.get().saveAll();
		return s.clanWarCoreBlock;
	}

	private static void buildJail(MinecraftServer server, War next) {
		ServerWorld world = server.getOverworld();
		int y = Math.min(world.getTopY() - 8, 240);
		BlockPos origin = new BlockPos(120000, y, 120000);
		next.jailMin = origin;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				for (int yy = 0; yy < 5; yy++) {
					boolean edge = x == 0 || x == 6 || z == 0 || z == 6 || yy == 0 || yy == 4;
					world.setBlockState(origin.add(x, yy, z),
							edge ? Blocks.BEDROCK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
		world.setBlockState(origin.add(3, 3, 3), Blocks.SEA_LANTERN.getDefaultState(), 2);
	}

	private static void clearJail(MinecraftServer server, War done) {
		if (done.jailMin == null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				for (int yy = 0; yy < 5; yy++) {
					world.setBlockState(done.jailMin.add(x, yy, z), Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
	}

	private static void teleportJail(ServerPlayerEntity player) {
		if (war == null || war.jailMin == null || player == null) {
			return;
		}
		ServerWorld world = player.getServer().getOverworld();
		double x = war.jailMin.getX() + 3.5;
		double y = war.jailMin.getY() + 1.1;
		double z = war.jailMin.getZ() + 3.5;
		TeleportLog.labeled("тюрьма битвы кланов", () ->
				WorldGate.allow(() -> player.teleport(world, x, y, z, player.getYaw(), 0)));
		player.changeGameMode(GameMode.ADVENTURE);
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.fallDistance = 0;
	}

	private static void keepJailed(MinecraftServer server) {
		if (war == null || war.jailMin == null) {
			return;
		}
		Box box = new Box(war.jailMin.getX() + 1, war.jailMin.getY() + 1, war.jailMin.getZ() + 1,
				war.jailMin.getX() + 6, war.jailMin.getY() + 4, war.jailMin.getZ() + 6);
		for (UUID id : war.jailed) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				continue;
			}
			if (!box.contains(player.getX(), player.getY(), player.getZ())) {
				teleportJail(player);
			}
			if (player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
				player.changeGameMode(GameMode.ADVENTURE);
			}
			if (player.getHealth() < player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			player.extinguish();
		}
	}

	private static Side sideOf(String clanId) {
		if (war == null || clanId == null) {
			return null;
		}
		if (clanId.equals(war.a.clanId)) {
			return war.a;
		}
		if (clanId.equals(war.b.clanId)) {
			return war.b;
		}
		return null;
	}

	public static void protectExplosions(World world, List<BlockPos> blocks) {
		if (war == null || world == null || blocks == null || blocks.isEmpty()) {
			return;
		}
		String dim = world.getRegistryKey().getValue().toString();
		blocks.removeIf(pos -> protectedAt(dim, pos));
	}

	private static boolean protectedAt(String dim, BlockPos pos) {
		return matchesSide(war.a, dim, pos) || matchesSide(war.b, dim, pos);
	}

	private static boolean matchesSide(Side side, String dim, BlockPos pos) {
		if (side == null || pos == null || !sameDim(dim, side.dimension)) {
			return false;
		}
		if (side.core != null && side.core.equals(pos)) {
			return true;
		}
		return side.chest != null && side.chest.equals(pos);
	}

	private static void ensureCores(MinecraftServer server) {
		if (war == null) {
			return;
		}
		restoreCoreIfMissing(server, war.a);
		restoreCoreIfMissing(server, war.b);
	}

	private static void restoreCoreIfMissing(MinecraftServer server, Side side) {
		if (side == null || side.broken || side.core == null) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(server, side.dimension);
		if (world == null) {
			return;
		}
		Block expect = coreBlock();
		if (!world.getBlockState(side.core).isOf(expect)) {
			world.setBlockState(side.core, expect.getDefaultState(), 3);
		}
	}

	private static boolean sameDim(ServerPlayerEntity player, Side side) {
		return player != null && side != null
				&& sameDim(player.getWorld().getRegistryKey().getValue().toString(), side.dimension);
	}

	private static boolean sameDim(String dim, String sideDim) {
		if (sideDim == null || sideDim.isBlank()) {
			return true;
		}
		return dim != null && dim.equalsIgnoreCase(sideDim);
	}

	private static Side sideAt(ServerPlayerEntity player, BlockPos pos) {
		if (war == null || pos == null || player == null) {
			return null;
		}
		if (war.a.core != null && war.a.core.equals(pos) && sameDim(player, war.a)) {
			return war.a;
		}
		if (war.b.core != null && war.b.core.equals(pos) && sameDim(player, war.b)) {
			return war.b;
		}
		return null;
	}

	private static Side sideAtChest(ServerPlayerEntity player, BlockPos pos) {
		if (war == null || pos == null || player == null) {
			return null;
		}
		if (war.a.chest != null && war.a.chest.equals(pos) && sameDim(player, war.a)) {
			return war.a;
		}
		if (war.b.chest != null && war.b.chest.equals(pos) && sameDim(player, war.b)) {
			return war.b;
		}
		return sideAt(player, pos);
	}

	private static boolean isEnemy(ServerPlayerEntity player, String clanId) {
		ClanRecord mine = ClanService.of(player);
		return mine != null && fightingClan(mine.id) && !mine.id.equals(clanId);
	}

	private static ClanRecord enemyClan(String clanId) {
		if (war == null) {
			return null;
		}
		if (clanId.equals(war.a.clanId)) {
			return ClanService.find(war.b.clanId);
		}
		if (clanId.equals(war.b.clanId)) {
			return ClanService.find(war.a.clanId);
		}
		return null;
	}

	private static int onlineMembers(MinecraftServer server, ClanRecord clan) {
		int n = 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player)) {
				continue;
			}
			ClanRecord of = ClanService.of(player);
			if (of != null && clan.id.equals(of.id)) {
				n++;
			}
		}
		return n;
	}

	private static void notifyAdmins(MinecraftServer server, ClanRecord from, ClanRecord to, Challenge ch) {
		MutableText line = Chat.gold("Заявка битвы кланов #" + ch.id + ": «" + from.name + "» → «" + to.name + "». ");
		line.append(Text.literal("[Разрешить]").formatted(Formatting.GREEN, Formatting.BOLD).styled(st -> st
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cwarok " + ch.id))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Начать битву")))));
		line.append(Text.literal("  "));
		line.append(Text.literal("[Отклонить]").formatted(Formatting.RED, Formatting.BOLD).styled(st -> st
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cwardeny " + ch.id))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Отклонить")))));
		for (ServerPlayerEntity admin : server.getPlayerManager().getPlayerList()) {
			if (DataManager.get().isAdmin(admin)) {
				Chat.send(admin, line);
			}
		}
	}

	private static void broadcastClans(MinecraftServer server, ClanRecord a, ClanRecord b, Text text) {
		broadcastClan(server, a, text);
		broadcastClan(server, b, text);
	}

	private static void broadcastClan(MinecraftServer server, ClanRecord clan, Text text) {
		if (clan == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ClanRecord of = ClanService.of(player);
			if (of != null && clan.id.equals(of.id)) {
				Chat.send(player, text);
			}
		}
	}

	private static String pairKey(String a, String b) {
		if (a.compareToIgnoreCase(b) <= 0) {
			return a + "|" + b;
		}
		return b + "|" + a;
	}

	private static void vacuumDrops(ServerWorld world, BlockPos pos) {
		Box box = new Box(pos).expand(1.5);
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
			item.discard();
		}
	}

	private static ModSettings settings() {
		return PlayFeatures.s();
	}
}
