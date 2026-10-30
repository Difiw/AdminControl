package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.mixin.DisplayEntityAccessor;
import ru.admincontrol.mixin.TextDisplayAccessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Приваты из магазина: блок 1×1×1 одного из 5 уровней, 8 одинаковых блоков кубом 2×2×2 —
 * диаметр ×1.5 и HP ×2. HP снимают взрывы и снаряды чужих игроков, удар рукой — нет.
 */
public final class PrivateService {
	public static final String TAG = "AdminControlPrivate";
	public static final int LEVELS = 5;
	public static final int CUBE = 8;
	/** Сколько отслеживаются действия администратора на территории после снятия привата. */
	public static final long WATCH_MS = 30L * 60L * 1000L;
	private static final Block[] BLOCKS = {
			Blocks.COPPER_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK
	};
	private static final String[] MATERIAL = {"медный", "золотой", "железный", "алмазный", "незеритовый"};
	private static final int[] HP = {200, 300, 500, 1000, 2000};
	private static final int[] DIAMETER = {5, 8, 17, 25, 37};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

	private static Store store = new Store();
	private static boolean dirty;
	private static int ticks;
	private static final Map<String, Long> HIT_NOTICE = new HashMap<>();
	private static final String LABEL_TAG = "admin_control_private_hp";
	private static final Map<String, UUID> LABELS = new HashMap<>();
	private static final List<Entity> STRAY_LABELS = new ArrayList<>();

	private PrivateService() {
	}

	public static final class Priv {
		public String owner = "";
		public String ownerNick = "";
		public int level = 1;
		public String dimension = "";
		public List<int[]> blocks = new ArrayList<>();
		public int hp;
		public long createdAt;
		public String removedBy = "";
		public long removedAt;
	}

	public static final class Notice {
		public String owner = "";
		public String text = "";
	}

	public static final class Watch {
		public String admin = "";
		public String adminNick = "";
		public String owner = "";
		public String ownerNick = "";
		public String dimension = "";
		public int minX;
		public int minY;
		public int minZ;
		public int maxX;
		public int maxY;
		public int maxZ;
		public long until;
		public Map<String, Integer> counts = new LinkedHashMap<>();
	}

	private static final class Store {
		List<Priv> active = new ArrayList<>();
		List<Priv> removed = new ArrayList<>();
		List<Notice> notices = new ArrayList<>();
		List<Watch> watches = new ArrayList<>();
	}

	public record Area(String owner, String ownerNick, HomeProtection.Box box) {
	}

	// ---------- уровни ----------

	public static int baseHp(int level) {
		return HP[clampLevel(level) - 1];
	}

	public static int baseDiameter(int level) {
		return DIAMETER[clampLevel(level) - 1];
	}

	public static int cubeDiameter(int level) {
		return baseDiameter(level) * 3 / 2;
	}

	public static int baseRadius(int level) {
		return baseDiameter(level) / 2;
	}

	public static int cubeHp(int level) {
		return baseHp(level) * 2;
	}

	public static int cubeRadius(int level) {
		return cubeDiameter(level) / 2;
	}

	public static int diameter(Priv p) {
		return merged(p) ? cubeDiameter(p.level) : baseDiameter(p.level);
	}

	public static String material(int level) {
		return MATERIAL[clampLevel(level) - 1];
	}

	public static Block block(int level) {
		return BLOCKS[clampLevel(level) - 1];
	}

	public static String levelLabel(int level) {
		return "ур." + clampLevel(level) + " (" + material(level) + ")";
	}

	private static int clampLevel(int level) {
		return Math.max(1, Math.min(LEVELS, level));
	}

	public static int price(int level) {
		ModSettings s = PlayFeatures.s();
		return s == null ? 0 : s.privatePrice(level);
	}

	public static ItemStack item(int level) {
		int lvl = clampLevel(level);
		ItemStack stack = new ItemStack(block(lvl));
		stack.getOrCreateNbt().putInt(TAG, lvl);
		stack.setCustomName(Text.literal("Приват " + levelLabel(lvl))
				.formatted(Formatting.GOLD).styled(s -> s.withItalic(false)));
		NbtList lore = new NbtList();
		lore.add(loreLine("Диаметр " + baseDiameter(lvl) + ", HP " + baseHp(lvl), Formatting.GRAY));
		lore.add(loreLine("Куб 2×2×2 из 8 таких блоков: диаметр " + cubeDiameter(lvl) + ", HP " + cubeHp(lvl),
				Formatting.GRAY));
		lore.add(loreLine("Поставьте блок, чтобы создать приват", Formatting.YELLOW));
		stack.getOrCreateSubNbt("display").put("Lore", lore);
		return stack;
	}

	private static NbtString loreLine(String text, Formatting color) {
		return NbtString.of(Text.Serializer.toJson(Text.literal(text).formatted(color)
				.styled(s -> s.withItalic(false))));
	}

	public static int levelOf(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
			return 0;
		}
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(TAG)) {
			return 0;
		}
		int lvl = nbt.getInt(TAG);
		return lvl >= 1 && lvl <= LEVELS && stack.getItem() == block(lvl).asItem() ? lvl : 0;
	}

	// ---------- состояние привата ----------

	public static boolean merged(Priv p) {
		if (p == null || p.blocks.size() != CUBE) {
			return false;
		}
		int[] min = min(p);
		int[] max = max(p);
		return max[0] - min[0] == 1 && max[1] - min[1] == 1 && max[2] - min[2] == 1;
	}

	public static int maxHp(Priv p) {
		return merged(p) ? cubeHp(p.level) : baseHp(p.level);
	}

	public static int radius(Priv p) {
		return merged(p) ? cubeRadius(p.level) : baseRadius(p.level);
	}

	private static int[] min(Priv p) {
		int[] out = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		for (int[] b : p.blocks) {
			for (int i = 0; i < 3; i++) {
				out[i] = Math.min(out[i], b[i]);
			}
		}
		return out;
	}

	private static int[] max(Priv p) {
		int[] out = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (int[] b : p.blocks) {
			for (int i = 0; i < 3; i++) {
				out[i] = Math.max(out[i], b[i]);
			}
		}
		return out;
	}

	public static HomeProtection.Box box(Priv p) {
		if (p == null || p.blocks.isEmpty()) {
			return null;
		}
		int r = radius(p);
		int[] min = min(p);
		int[] max = max(p);
		return new HomeProtection.Box(min[0] - r, min[1] - r, min[2] - r, max[0] + r, max[1] + r, max[2] + r,
				p.dimension);
	}

	public static String center(Priv p) {
		if (p == null || p.blocks.isEmpty()) {
			return "—";
		}
		int[] b = min(p);
		return b[0] + ", " + b[1] + ", " + b[2];
	}

	public static List<Area> areas() {
		List<Area> out = new ArrayList<>();
		for (Priv p : store.active) {
			HomeProtection.Box box = box(p);
			if (box != null) {
				out.add(new Area(p.owner, p.ownerNick, box));
			}
		}
		return out;
	}

	public static List<Priv> active() {
		return new ArrayList<>(store.active);
	}

	public static Priv activeOf(String nickOrUuid) {
		return find(store.active, nickOrUuid);
	}

	public static Priv removedOf(String nickOrUuid) {
		return find(store.removed, nickOrUuid);
	}

	private static Priv find(List<Priv> list, String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		for (Priv p : list) {
			if (key.equalsIgnoreCase(p.owner) || key.equalsIgnoreCase(p.ownerNick)) {
				return p;
			}
		}
		return null;
	}

	public static HomeProtection.Box boxOf(ServerPlayerEntity player) {
		return player == null ? null : box(activeOf(player.getUuid().toString()));
	}

	private static Priv atBlock(String dim, BlockPos pos) {
		for (Priv p : store.active) {
			if (!p.dimension.equalsIgnoreCase(dim)) {
				continue;
			}
			for (int[] b : p.blocks) {
				if (b[0] == pos.getX() && b[1] == pos.getY() && b[2] == pos.getZ()) {
					return p;
				}
			}
		}
		return null;
	}

	public static boolean isPrivateBlock(World world, BlockPos pos) {
		return world != null && !world.isClient && pos != null && !store.active.isEmpty()
				&& atBlock(dim(world), pos) != null;
	}

	private static String dim(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static boolean isOwner(Priv p, ServerPlayerEntity player) {
		return p != null && player != null && player.getUuid().toString().equalsIgnoreCase(p.owner);
	}

	// ---------- установка ----------

	/** null — можно ставить, иначе текст отказа. */
	public static String canPlace(ServerPlayerEntity player, World world, BlockPos pos, int level) {
		if (DataManager.get() == null) {
			return "Приваты сейчас недоступны.";
		}
		String dim = dim(world);
		Priv own = activeOf(player.getUuid().toString());
		if (own == null) {
			if (overlapsOthers(player.getUuid().toString(), dim, pos, level)) {
				return "Здесь нельзя поставить приват: его территория пересечётся с чужим приватом.";
			}
			return null;
		}
		if (own.level != level) {
			return "У вас уже есть приват " + levelLabel(own.level)
					+ ". Объединять можно только блоки одного уровня.";
		}
		if (!own.dimension.equalsIgnoreCase(dim) || own.blocks.size() >= CUBE || !fitsCube(own, pos)) {
			return "У вас уже есть приват. Новый блок того же уровня ставится вплотную к нему:"
					+ " 8 одинаковых блоков образуют куб 2×2×2.";
		}
		return null;
	}

	private static boolean fitsCube(Priv p, BlockPos pos) {
		int[] min = min(p);
		int[] max = max(p);
		int[] at = {pos.getX(), pos.getY(), pos.getZ()};
		for (int i = 0; i < 3; i++) {
			if (Math.max(max[i], at[i]) - Math.min(min[i], at[i]) > 1) {
				return false;
			}
		}
		for (int[] b : p.blocks) {
			if (b[0] == at[0] && b[1] == at[1] && b[2] == at[2]) {
				return false;
			}
		}
		return true;
	}

	private static boolean overlapsOthers(String owner, String dim, BlockPos pos, int level) {
		int mine = cubeRadius(level) + 1;
		for (Priv p : store.active) {
			if (p.owner.equalsIgnoreCase(owner) || !p.dimension.equalsIgnoreCase(dim) || p.blocks.isEmpty()) {
				continue;
			}
			int reach = mine + cubeRadius(p.level) + 1;
			int[] b = min(p);
			if (Math.abs(b[0] - pos.getX()) <= reach && Math.abs(b[1] - pos.getY()) <= reach
					&& Math.abs(b[2] - pos.getZ()) <= reach) {
				return true;
			}
		}
		return false;
	}

	public static void placed(ServerPlayerEntity player, World world, BlockPos pos, int level) {
		String uuid = player.getUuid().toString();
		String nick = player.getGameProfile().getName();
		Priv own = activeOf(uuid);
		if (own == null) {
			Priv p = new Priv();
			p.owner = uuid;
			p.ownerNick = nick;
			p.level = clampLevel(level);
			p.dimension = dim(world);
			p.blocks.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
			p.hp = baseHp(level);
			p.createdAt = System.currentTimeMillis();
			store.active.add(p);
			Chat.send(player, Chat.green("Приват " + levelLabel(level) + " создан: диаметр " + diameter(p)
					+ ", HP " + p.hp + "."));
			AdminNotify.playerAction(player, ActionKind.PLACE, "создал приват " + levelLabel(level) + " "
					+ pos.toShortString() + " (" + p.dimension + ")", true);
		} else {
			boolean wasMerged = merged(own);
			own.ownerNick = nick;
			own.blocks.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
			if (!wasMerged && merged(own)) {
				own.hp = Math.min(cubeHp(own.level), own.hp * 2);
				Chat.send(player, Chat.green("Куб 2×2×2 собран! Диаметр " + diameter(own) + ", HP " + own.hp + "/"
						+ maxHp(own) + "."));
				AdminNotify.playerAction(player, ActionKind.PLACE, "собрал куб привата " + levelLabel(own.level)
						+ " " + center(own), true);
			} else {
				Chat.send(player, Chat.green("Блок привата добавлен (" + own.blocks.size() + "/" + CUBE + ")."));
			}
		}
		changed();
	}

	// ---------- ломание владельцем ----------

	/** false — отменить обычное ломание (блок привата обработан здесь или запрещён). */
	public static boolean beforeBreak(ServerPlayerEntity player, World world, BlockPos pos) {
		if (world == null || world.isClient || store.active.isEmpty()) {
			return true;
		}
		Priv p = atBlock(dim(world), pos);
		if (p == null) {
			return true;
		}
		if (!isOwner(p, player)) {
			player.sendMessage(Chat.red("Это чужой приват."), true);
			return false;
		}
		if (!player.getMainHandStack().isIn(ItemTags.PICKAXES)) {
			player.sendMessage(Chat.red("Свой приват можно сломать только киркой."), true);
			return false;
		}
		boolean wasMerged = merged(p);
		p.blocks.removeIf(b -> b[0] == pos.getX() && b[1] == pos.getY() && b[2] == pos.getZ());
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
		Block.dropStack(world, pos, item(p.level));
		if (p.blocks.isEmpty()) {
			store.active.remove(p);
			Chat.send(player, Chat.yellow("Приват снят, блок возвращён."));
			AdminNotify.playerAction(player, ActionKind.BREAK, "сломал свой приват " + levelLabel(p.level) + " "
					+ pos.toShortString(), true);
		} else {
			if (wasMerged) {
				p.hp = Math.max(1, Math.min(baseHp(p.level), p.hp / 2));
			}
			Chat.send(player, Chat.yellow("Блок привата снят (" + p.blocks.size() + "/" + CUBE + ")."));
		}
		changed();
		return false;
	}

	// ---------- урон ----------

	public static void onExplosion(World world, double x, double y, double z, float power, Entity causer) {
		if (world == null || world.isClient || store.active.isEmpty() || power <= 0f) {
			return;
		}
		String dim = dim(world);
		double reach = power * 2.0 + 2.0;
		int damage = Math.max(1, Math.round(power * 10f));
		String source = causer == null ? "взрыв" : "взрыв (" + causer.getName().getString() + ")";
		for (Priv p : new ArrayList<>(store.active)) {
			if (!p.dimension.equalsIgnoreCase(dim) || nearest(p, x, y, z) > reach) {
				continue;
			}
			if (causer instanceof ServerPlayerEntity sp && isOwner(p, sp)) {
				continue;
			}
			damage(world.getServer(), p, damage, source);
		}
	}

	public static void onProjectileHit(World world, BlockPos pos, Entity owner) {
		if (world == null || world.isClient || store.active.isEmpty() || pos == null) {
			return;
		}
		Priv p = atBlock(dim(world), pos);
		if (p == null || owner instanceof ServerPlayerEntity sp && isOwner(p, sp)) {
			return;
		}
		damage(world.getServer(), p, 2, owner == null ? "снаряд" : "снаряд (" + owner.getName().getString() + ")");
	}

	private static double nearest(Priv p, double x, double y, double z) {
		double best = Double.MAX_VALUE;
		for (int[] b : p.blocks) {
			double dx = b[0] + 0.5 - x;
			double dy = b[1] + 0.5 - y;
			double dz = b[2] + 0.5 - z;
			best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return best;
	}

	private static void damage(MinecraftServer server, Priv p, int amount, String source) {
		p.hp -= amount;
		dirty = true;
		if (p.hp <= 0) {
			destroy(server, p, source);
			return;
		}
		ServerPlayerEntity owner = server == null ? null : server.getPlayerManager().getPlayer(p.ownerNick);
		long now = System.currentTimeMillis();
		Long last = HIT_NOTICE.get(p.owner);
		if (owner != null && (last == null || now - last > 5000L)) {
			HIT_NOTICE.put(p.owner, now);
			owner.sendMessage(Chat.red("Ваш приват атакован: " + source + ". HP " + p.hp + "/" + maxHp(p)), true);
		}
		ActivityLog.player(p.ownerNick, "Приват " + levelLabel(p.level) + " получил урон " + amount + " (" + source
				+ "), HP " + p.hp + "/" + maxHp(p), true);
	}

	private static void destroy(MinecraftServer server, Priv p, String source) {
		store.active.remove(p);
		clearBlocks(server, p);
		String text = "Ваш приват " + levelLabel(p.level) + " (" + center(p) + ") разрушен: " + source + ".";
		notifyOwner(server, p, text);
		ActionLog.info("Приват игрока " + p.ownerNick + " разрушен: " + source);
		changed();
	}

	private static void clearBlocks(MinecraftServer server, Priv p) {
		ServerWorld world = server == null ? null : SpawnService.resolveWorld(server, p.dimension);
		if (world == null) {
			return;
		}
		for (int[] b : p.blocks) {
			BlockPos pos = new BlockPos(b[0], b[1], b[2]);
			if (world.getBlockState(pos).isOf(block(p.level))) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
			}
		}
	}

	// ---------- снятие и восстановление администратором ----------

	public static String remove(ServerPlayerEntity admin, String adminNick, String target, MinecraftServer server) {
		Priv p = activeOf(target);
		if (p == null) {
			return null;
		}
		store.active.remove(p);
		clearBlocks(server, p);
		long now = System.currentTimeMillis();
		p.removedBy = adminNick;
		p.removedAt = now;
		store.removed.removeIf(old -> old.owner.equalsIgnoreCase(p.owner));
		store.removed.add(p);
		HomeProtection.Box box = box(p);
		if (admin != null && box != null) {
			store.watches.removeIf(w -> w.owner.equalsIgnoreCase(p.owner) && w.admin.equalsIgnoreCase(
					admin.getUuid().toString()) && flushWatch(server, w));
			Watch w = new Watch();
			w.admin = admin.getUuid().toString();
			w.adminNick = adminNick;
			w.owner = p.owner;
			w.ownerNick = p.ownerNick;
			w.dimension = p.dimension;
			w.minX = box.minX;
			w.minY = box.minY;
			w.minZ = box.minZ;
			w.maxX = box.maxX;
			w.maxY = box.maxY;
			w.maxZ = box.maxZ;
			w.until = now + WATCH_MS;
			store.watches.add(w);
		}
		boolean online = server != null && server.getPlayerManager().getPlayer(p.ownerNick) != null;
		String text = online
				? "Администратор " + adminNick + " снял ваш приват " + levelLabel(p.level) + " (" + center(p) + ")."
				: "Пока вас не было, администратор " + adminNick + " снял ваш приват " + levelLabel(p.level)
						+ " (" + center(p) + "). Время снятия: " + TIME.format(Instant.ofEpochMilli(now)) + ".";
		notifyOwner(server, p, text);
		changed();
		return p.ownerNick;
	}

	/** null — восстановлено, иначе текст ошибки. */
	public static String restore(String adminNick, String target, MinecraftServer server) {
		Priv p = removedOf(target);
		if (p == null) {
			return "У игрока нет привата, снятого администратором.";
		}
		if (activeOf(p.owner) != null) {
			return "У игрока уже есть действующий приват — восстановление невозможно.";
		}
		ServerWorld world = server == null ? null : SpawnService.resolveWorld(server, p.dimension);
		if (world == null) {
			return "Мир привата недоступен: " + p.dimension;
		}
		for (int[] b : p.blocks) {
			world.setBlockState(new BlockPos(b[0], b[1], b[2]), block(p.level).getDefaultState(), 3);
		}
		store.removed.remove(p);
		p.removedBy = "";
		p.removedAt = 0L;
		store.active.add(p);
		store.watches.removeIf(w -> w.owner.equalsIgnoreCase(p.owner) && flushWatch(server, w));
		notifyOwner(server, p, "Администратор " + adminNick + " восстановил ваш приват " + levelLabel(p.level)
				+ " (" + center(p) + ").");
		changed();
		return null;
	}

	private static void notifyOwner(MinecraftServer server, Priv p, String text) {
		ActivityLog.player(p.ownerNick, text, true);
		ServerPlayerEntity owner = server == null ? null : server.getPlayerManager().getPlayer(p.ownerNick);
		if (owner != null && !AuthService.pending(owner)) {
			Chat.send(owner, Chat.gold(text));
			return;
		}
		Notice n = new Notice();
		n.owner = p.owner;
		n.text = text;
		store.notices.add(n);
		dirty = true;
	}

	// ---------- слежка за администратором на бывшей территории ----------

	public static void track(ServerPlayerEntity actor, ActionKind kind, String action, boolean ok) {
		if (store.watches.isEmpty() || actor == null || kind == null || !ok) {
			return;
		}
		String key = category(kind, action);
		if (key == null) {
			return;
		}
		String uuid = actor.getUuid().toString();
		String dim = dim(actor.getWorld());
		BlockPos pos = actor.getBlockPos();
		long now = System.currentTimeMillis();
		for (Watch w : store.watches) {
			if (!w.admin.equalsIgnoreCase(uuid) || now > w.until || !w.dimension.equalsIgnoreCase(dim)
					|| pos.getX() < w.minX || pos.getX() > w.maxX || pos.getY() < w.minY || pos.getY() > w.maxY
					|| pos.getZ() < w.minZ || pos.getZ() > w.maxZ) {
				continue;
			}
			w.counts.merge(key, 1, Integer::sum);
			ActivityLog.admin(w.adminNick, "Территория снятого привата " + w.ownerNick + ": " + action, true);
			dirty = true;
		}
	}

	private static String category(ActionKind kind, String action) {
		String low = action == null ? "" : action.toLowerCase(Locale.ROOT);
		return switch (kind) {
			case PLACE -> "place";
			case BREAK -> "break";
			case HIT_BLOCK -> low.startsWith("ударил/использовал") ? "use" : null;
			case WORKSTATION -> low.contains("chest") || low.contains("сундук") || low.contains("бочк")
					|| low.contains("barrel") || low.contains("шалкер") || low.contains("shulker")
					? "chest"
					: low.contains("door") || low.contains("двер") || low.contains("люк") || low.contains("калитк")
							|| low.contains("gate") ? "door" : "station";
			case SCREEN -> "screen";
			case DROP -> "drop";
			case PICKUP -> "pickup";
			case ATTACK_MOB, ATTACK_PLAYER -> "attack";
			case KILL_MONSTER, KILL_PEACEFUL -> "kill";
			case USE_ITEM -> "item";
			case CREATIVE_TAKE -> "creative";
			default -> null;
		};
	}

	private static String categoryLabel(String key) {
		return switch (key) {
			case "place" -> "поставил блоков";
			case "break" -> "сломал блоков";
			case "use" -> "взаимодействий с блоками";
			case "chest" -> "открыл сундуков/хранилищ";
			case "door" -> "использовал дверей/люков/калиток";
			case "station" -> "использовал рабочих блоков и механизмов";
			case "screen" -> "открыл интерфейсов";
			case "drop" -> "выбросил предметов";
			case "pickup" -> "подобрал предметов";
			case "attack" -> "атаковал существ";
			case "kill" -> "убил существ";
			case "item" -> "использовал предметов";
			case "creative" -> "взял предметов из креатива";
			default -> key;
		};
	}

	/** Итог слежки; пустой итог не отправляется. Всегда true — для removeIf. */
	private static boolean flushWatch(MinecraftServer server, Watch w) {
		if (w.counts.isEmpty()) {
			return true;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : w.counts.entrySet()) {
			if (e.getValue() == null || e.getValue() <= 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(categoryLabel(e.getKey())).append(": ").append(e.getValue());
		}
		if (sb.length() == 0) {
			return true;
		}
		ActivityLog.admin(w.adminNick, "Итог действий на территории снятого привата " + w.ownerNick + ": " + sb, true);
		Priv fake = new Priv();
		fake.owner = w.owner;
		fake.ownerNick = w.ownerNick;
		notifyOwner(server, fake, "Администратор " + w.adminNick + " на территории вашего снятого привата: " + sb + ".");
		return true;
	}

	// ---------- тик, загрузка, сохранение ----------

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		ticks++;
		if (ticks % 20 == 0) {
			deliverNotices(server);
			long now = System.currentTimeMillis();
			if (store.watches.removeIf(w -> now > w.until && flushWatch(server, w))) {
				dirty = true;
			}
		}
		if (ticks % 200 == 0) {
			validateBlocks(server);
		}
		if (ticks % 10 == 0) {
			updateLabels(server);
		}
		if (dirty && ticks % 600 == 0) {
			save();
		}
	}

	// ---------- надпись HP над блоком привата ----------

	private static void updateLabels(MinecraftServer server) {
		if (!STRAY_LABELS.isEmpty()) {
			for (Entity e : STRAY_LABELS) {
				if (!e.isRemoved() && !LABELS.containsValue(e.getUuid())) {
					e.discard();
				}
			}
			STRAY_LABELS.clear();
		}
		for (Priv p : store.active) {
			ServerWorld world = SpawnService.resolveWorld(server, p.dimension);
			if (world == null || p.blocks.isEmpty()) {
				continue;
			}
			int[] min = min(p);
			int[] max = max(p);
			double x = (min[0] + max[0] + 1) / 2.0;
			double y = max[1] + 1.2;
			double z = (min[2] + max[2] + 1) / 2.0;
			if (!world.isChunkLoaded(min[0] >> 4, min[2] >> 4)) {
				continue;
			}
			Text text = labelText(p);
			UUID id = LABELS.get(p.owner);
			Entity existing = id == null ? null : world.getEntity(id);
			if (existing instanceof DisplayEntity.TextDisplayEntity label && !label.isRemoved()) {
				if (label.squaredDistanceTo(x, y, z) > 0.01) {
					label.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
				}
				TextDisplayAccessor access = (TextDisplayAccessor) label;
				if (!text.equals(access.adminControl$getText())) {
					access.adminControl$setText(text);
				}
				continue;
			}
			DisplayEntity.TextDisplayEntity label = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
			label.addCommandTag(LABEL_TAG);
			label.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
			((DisplayEntityAccessor) label).adminControl$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
			((DisplayEntityAccessor) label).adminControl$setViewRange(0.5f);
			((TextDisplayAccessor) label).adminControl$setText(text);
			LABELS.put(p.owner, label.getUuid());
			if (!world.spawnEntity(label)) {
				LABELS.remove(p.owner);
			}
		}
		LABELS.entrySet().removeIf(entry -> {
			Priv p = activeOf(entry.getKey());
			if (p != null) {
				return false;
			}
			for (ServerWorld world : server.getWorlds()) {
				Entity e = world.getEntity(entry.getValue());
				if (e != null) {
					e.discard();
				}
			}
			return true;
		});
	}

	private static Text labelText(Priv p) {
		int hp = Math.max(0, p.hp);
		int max = maxHp(p);
		Formatting color = hp * 3 <= max ? Formatting.RED : hp * 3 <= max * 2 ? Formatting.YELLOW : Formatting.GREEN;
		return Text.literal("HP " + hp + " / " + max).formatted(color);
	}

	/** Надписи сохраняются в чанках вместе с миром; лишние (после перезапуска) убираются при загрузке. */
	public static void onEntityLoad(Entity entity, ServerWorld world) {
		if (entity instanceof DisplayEntity.TextDisplayEntity && entity.getCommandTags().contains(LABEL_TAG)
				&& !LABELS.containsValue(entity.getUuid())) {
			STRAY_LABELS.add(entity);
		}
	}

	public static void removeLabels(MinecraftServer server) {
		if (server != null) {
			for (UUID id : LABELS.values()) {
				for (ServerWorld world : server.getWorlds()) {
					Entity e = world.getEntity(id);
					if (e != null) {
						e.discard();
					}
				}
			}
		}
		LABELS.clear();
		STRAY_LABELS.clear();
	}

	private static void deliverNotices(MinecraftServer server) {
		if (store.notices.isEmpty()) {
			return;
		}
		boolean any = store.notices.removeIf(n -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(java.util.UUID.fromString(n.owner));
			if (player == null || AuthService.pending(player)) {
				return false;
			}
			Chat.send(player, Chat.gold(n.text));
			return true;
		});
		if (any) {
			dirty = true;
		}
	}

	private static void validateBlocks(MinecraftServer server) {
		boolean change = false;
		for (Priv p : new ArrayList<>(store.active)) {
			ServerWorld world = SpawnService.resolveWorld(server, p.dimension);
			if (world == null) {
				continue;
			}
			int before = p.blocks.size();
			boolean wasMerged = merged(p);
			p.blocks.removeIf(b -> {
				BlockPos pos = new BlockPos(b[0], b[1], b[2]);
				return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
						&& !world.getBlockState(pos).isOf(block(p.level));
			});
			if (p.blocks.size() == before) {
				continue;
			}
			change = true;
			if (p.blocks.isEmpty()) {
				store.active.remove(p);
				notifyOwner(server, p, "Блок вашего привата " + levelLabel(p.level) + " пропал — приват снят.");
			} else if (wasMerged && !merged(p)) {
				p.hp = Math.max(1, Math.min(baseHp(p.level), p.hp / 2));
			}
		}
		if (change) {
			changed();
		}
	}

	private static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("privates.json");
	}

	public static void load() {
		store = new Store();
		dirty = false;
		HIT_NOTICE.clear();
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Store loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
			if (loaded != null) {
				store.active = loaded.active == null ? new ArrayList<>() : loaded.active;
				store.removed = loaded.removed == null ? new ArrayList<>() : loaded.removed;
				store.notices = loaded.notices == null ? new ArrayList<>() : loaded.notices;
				store.watches = loaded.watches == null ? new ArrayList<>() : loaded.watches;
				store.active.removeIf(p -> p == null || p.blocks == null || p.blocks.isEmpty());
			}
		} catch (Exception e) {
			ActionLog.error("Не удалось прочитать privates.json", e);
		}
	}

	public static void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("privates.json.tmp");
			Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить privates.json", e);
		}
	}

	private static void changed() {
		dirty = true;
		save();
		HomeProtection.refresh();
	}

	// ---------- тексты для /aprivate ----------

	public static String time(long ms) {
		return ms <= 0 ? "—" : TIME.format(Instant.ofEpochMilli(ms));
	}

	public static List<String> info(Priv p, boolean removed) {
		List<String> lines = new ArrayList<>();
		HomeProtection.Box box = box(p);
		lines.add("Владелец: " + p.ownerNick);
		lines.add("Уровень: " + levelLabel(p.level));
		lines.add("Форма: " + (merged(p) ? "куб 2×2×2 (диаметр ×1.5, HP ×2)" : "блоков " + p.blocks.size() + "/" + CUBE));
		lines.add("HP: " + Math.max(0, p.hp) + " / " + maxHp(p));
		lines.add("Диаметр: " + diameter(p) + " (радиус " + radius(p) + ")");
		lines.add("Мир: " + p.dimension);
		lines.add("Центр: " + center(p));
		if (box != null) {
			lines.add("Территория: " + box.minX + ", " + box.minY + ", " + box.minZ + "  —  "
					+ box.maxX + ", " + box.maxY + ", " + box.maxZ);
		}
		lines.add("Создан: " + time(p.createdAt));
		if (removed) {
			lines.add("Снят администратором " + p.removedBy + ": " + time(p.removedAt));
			lines.add("Восстановить: /aprivate restore " + p.ownerNick);
		}
		return lines;
	}

	public static List<String> levels() {
		List<String> lines = new ArrayList<>();
		int[] count = new int[LEVELS + 1];
		int cubes = 0;
		for (Priv p : store.active) {
			count[clampLevel(p.level)]++;
			if (merged(p)) {
				cubes++;
			}
		}
		for (int lvl = 1; lvl <= LEVELS; lvl++) {
			lines.add("Ур." + lvl + " — " + material(lvl) + " блок: HP " + baseHp(lvl) + ", диаметр " + baseDiameter(lvl)
					+ "  ·  куб 2×2×2: HP " + cubeHp(lvl) + ", диаметр " + cubeDiameter(lvl)
					+ "  ·  цена " + Money.wc(price(lvl)) + "  ·  активных: " + count[lvl]);
		}
		lines.add("");
		lines.add("Всего активных приватов: " + store.active.size() + " (из них кубов: " + cubes + ")");
		lines.add("Снято администраторами (можно восстановить): " + store.removed.size());
		return lines;
	}

	public static List<String> players() {
		List<String> lines = new ArrayList<>();
		List<Priv> list = new ArrayList<>(store.active);
		list.sort((a, b) -> a.ownerNick.compareToIgnoreCase(b.ownerNick));
		for (Priv p : list) {
			lines.add(p.ownerNick + "  ·  " + levelLabel(p.level) + "  ·  диаметр " + diameter(p) + "  ·  HP "
					+ Math.max(0, p.hp) + "/" + maxHp(p) + "  ·  " + (merged(p) ? "куб" : p.blocks.size() + "/" + CUBE)
					+ "  ·  " + center(p) + " (" + p.dimension + ")");
		}
		if (!store.removed.isEmpty()) {
			lines.add("");
			lines.add("Сняты администраторами:");
			for (Priv p : store.removed) {
				lines.add(p.ownerNick + "  ·  " + levelLabel(p.level) + "  ·  снял " + p.removedBy + " " + time(p.removedAt));
			}
		}
		return lines;
	}
}
