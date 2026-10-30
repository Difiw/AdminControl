package ru.admincontrol.util;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.CwRule;
import ru.admincontrol.data.DataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Правила /cw item, /cw mod, /cw command. */
public final class CwService {
	public static final String ITEM = "item";
	public static final String MOD = "mod";
	public static final String COMMAND = "command";
	private static final Map<UUID, Long> NOTICE = new ConcurrentHashMap<>();
	private static int tick;

	private CwService() {
	}

	private static List<CwRule> rules() {
		DataManager data = DataManager.get();
		if (data == null) {
			return List.of();
		}
		if (data.settings().cwRules == null) {
			data.settings().cwRules = new ArrayList<>();
		}
		return data.settings().cwRules;
	}

	public static String normalizeKey(String kind, String raw) {
		String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (key.startsWith("/")) {
			key = key.substring(1);
		}
		if (ITEM.equals(kind)) {
			Identifier id = Identifier.tryParse(key);
			return id == null ? key : id.toString();
		}
		if (MOD.equals(kind)) {
			return resolveModId(key);
		}
		return key;
	}

	public static String resolveModId(String raw) {
		String want = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			String name = mod.getMetadata().getName() == null ? "" : mod.getMetadata().getName();
			if (id.equalsIgnoreCase(want) || name.replace(" ", "").equalsIgnoreCase(want)) {
				return id.toLowerCase(Locale.ROOT);
			}
		}
		return want;
	}

	public static boolean protectedMod(String id) {
		return "minecraft".equals(id) || "java".equals(id) || "fabricloader".equals(id)
				|| id.startsWith("fabric") || ru.admincontrol.AdminControlMod.MOD_ID.equals(id);
	}

	/** Задать правило. scope=null — для всех (старые правила по этому ключу сбрасываются); by — ранг поставившего. */
	public static void set(String kind, String key, boolean allow, String scope, String arg, int by) {
		List<CwRule> list = rules();
		String sc = scope == null ? "all" : scope;
		String a = arg == null ? "" : arg.trim();
		list.removeIf(r -> replaces(r, kind, key, sc, a));
		if (!"all".equals(sc) || !allow) {
			CwRule rule = new CwRule(kind, key, allow, sc, a);
			rule.by = by;
			list.add(rule);
		}
		DataManager.get().saveAll();
	}

	/** Старший ранг среди тех, кто поставил правила, которые заменит такое изменение (0 — таких нет). */
	public static int replacedRank(String kind, String key, String scope, String arg) {
		String sc = scope == null ? "all" : scope;
		String a = arg == null ? "" : arg.trim();
		int max = 0;
		for (CwRule r : rules()) {
			if (replaces(r, kind, key, sc, a)) {
				max = Math.max(max, r.by == null ? AdminRanks.DEV : r.by);
			}
		}
		return max;
	}

	private static boolean replaces(CwRule r, String kind, String key, String scope, String arg) {
		if (!kind.equals(r.kind) || !key.equals(r.key)) {
			return false;
		}
		return "all".equals(scope) || (scope.equals(r.scope) && arg.equalsIgnoreCase(r.arg));
	}

	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (CwRule r : rules()) {
			lines.add(r.kind + " " + r.key + " → " + (r.allow ? "разрешено" : "запрещено") + " (" + scopeLabel(r) + ")");
		}
		return lines;
	}

	public static String scopeLabel(String scope, String arg) {
		return switch (scope == null ? "all" : scope) {
			case "players" -> "игрокам";
			case "admins" -> "администрации";
			case "level" -> "админам " + arg + "+ ур.";
			case "nick" -> "для " + arg;
			default -> "для всех";
		};
	}

	private static String scopeLabel(CwRule r) {
		return scopeLabel(r.scope, r.arg);
	}

	private static int specificity(CwRule r) {
		return switch (r.scope == null ? "all" : r.scope) {
			case "nick" -> 3;
			case "level" -> 2;
			case "players", "admins" -> 1;
			default -> 0;
		};
	}

	private static boolean applies(CwRule r, ServerPlayerEntity player, int level) {
		if (r.by != null && level > r.by) {
			return false;
		}
		String scope = r.scope == null ? "all" : r.scope;
		return switch (scope) {
			case "players" -> level < AdminRanks.MOD;
			case "admins" -> level >= AdminRanks.MOD;
			case "level" -> {
				Integer min = AdminRanks.parseAssignable(r.arg);
				yield min != null && level >= min;
			}
			case "nick" -> player.getGameProfile().getName().equalsIgnoreCase(r.arg);
			default -> true;
		};
	}

	/** null — правил нет, иначе итог самого точного подходящего правила. */
	private static Boolean decide(String kind, String key, ServerPlayerEntity player) {
		if (player == null || key == null || key.isEmpty() || NpcService.isNpc(player)) {
			return null;
		}
		List<CwRule> list = rules();
		if (list.isEmpty()) {
			return null;
		}
		int level = DataManager.get() == null ? 0 : DataManager.get().getAdminLevel(player);
		CwRule best = null;
		for (CwRule r : list) {
			if (!kind.equals(r.kind) || !key.equals(r.key) || !applies(r, player, level)) {
				continue;
			}
			if (best == null || specificity(r) > specificity(best)
					|| (specificity(r) == specificity(best) && !r.allow)) {
				best = r;
			}
		}
		return best == null ? null : best.allow;
	}

	public static boolean itemBlocked(ServerPlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || rules().isEmpty()) {
			return false;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		Boolean byItem = decide(ITEM, id.toString(), player);
		if (byItem != null) {
			return !byItem;
		}
		return modBlocked(player, id.getNamespace());
	}

	public static boolean modBlocked(ServerPlayerEntity player, String namespace) {
		if (namespace == null || "minecraft".equals(namespace)) {
			return false;
		}
		Boolean byMod = decide(MOD, namespace, player);
		return byMod != null && !byMod;
	}

	public static boolean blockBlocked(ServerPlayerEntity player, net.minecraft.block.Block block) {
		if (block == null || rules().isEmpty()) {
			return false;
		}
		return modBlocked(player, Registries.BLOCK.getId(block).getNamespace());
	}

	public static boolean entityBlocked(ServerPlayerEntity player, net.minecraft.entity.Entity entity) {
		if (entity == null || rules().isEmpty()) {
			return false;
		}
		return modBlocked(player, Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace());
	}

	/** Имя запрещённой команды или null. Учитывает алиасы, namespace и /execute ... run. */
	public static String commandBlocked(ServerPlayerEntity player, String raw) {
		if (player == null || raw == null || rules().isEmpty() || player.getServer() == null) {
			return null;
		}
		String text = raw.trim();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		String[] parts = text.split("\\s+");
		for (int i = 0; i < parts.length; i++) {
			boolean start = i == 0 || "run".equalsIgnoreCase(parts[i - 1]);
			if (!start || parts[i].isBlank()) {
				continue;
			}
			String name = parts[i].toLowerCase(Locale.ROOT);
			String blocked = checkCommandName(player, name);
			if (blocked != null) {
				return blocked;
			}
		}
		return null;
	}

	private static String checkCommandName(ServerPlayerEntity player, String name) {
		String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
		String canon = canonical(player.getServer(), bare);
		for (String key : new String[] {name, bare, canon}) {
			Boolean d = decide(COMMAND, key, player);
			if (d != null) {
				return d ? null : "/" + bare;
			}
		}
		for (CwRule r : rules()) {
			if (COMMAND.equals(r.kind) && canon.equals(canonical(player.getServer(), r.key))) {
				Boolean d = decide(COMMAND, r.key, player);
				if (d != null) {
					return d ? null : "/" + bare;
				}
			}
		}
		return null;
	}

	public static String canonical(MinecraftServer server, String name) {
		if (server == null || name == null) {
			return name == null ? "" : name;
		}
		String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
		try {
			CommandNode<ServerCommandSource> node = server.getCommandManager().getDispatcher().getRoot().getChild(bare);
			if (node != null && node.getRedirect() != null && !node.getRedirect().getName().isEmpty()) {
				return node.getRedirect().getName().toLowerCase(Locale.ROOT);
			}
		} catch (RuntimeException ignored) {
		}
		return bare;
	}

	public static void notice(ServerPlayerEntity player, String text) {
		if (player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Long prev = NOTICE.get(player.getUuid());
		if (prev != null && now - prev < 1500L) {
			return;
		}
		NOTICE.put(player.getUuid(), now);
		Chat.send(player, Chat.red(text));
	}

	public static void denyItem(ServerPlayerEntity player, ItemStack stack) {
		notice(player, "Предмет «" + ItemLabels.of(stack) + "» вам запрещён.");
	}

	/** Раз в 10 тиков убирает запрещённые предметы из инвентаря. */
	public static void tick(MinecraftServer server) {
		if (server == null || rules().isEmpty() || ++tick % 10 != 0) {
			return;
		}
		boolean hasItemRules = false;
		for (CwRule r : rules()) {
			if (ITEM.equals(r.kind) || MOD.equals(r.kind)) {
				hasItemRules = true;
				break;
			}
		}
		if (!hasItemRules) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player)) {
				continue;
			}
			purge(player);
		}
	}

	private static void purge(ServerPlayerEntity player) {
		var inv = player.getInventory();
		ItemStack first = ItemStack.EMPTY;
		boolean creative = player.interactionManager.getGameMode() == GameMode.CREATIVE;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && itemBlocked(player, stack)) {
				if (first.isEmpty()) {
					first = stack.copy();
				}
				inv.setStack(i, ItemStack.EMPTY);
				if (!creative) {
					dropAway(player, stack);
				}
			}
		}
		var handler = player.currentScreenHandler;
		if (handler != null) {
			ItemStack cursor = handler.getCursorStack();
			if (!cursor.isEmpty() && itemBlocked(player, cursor)) {
				if (first.isEmpty()) {
					first = cursor.copy();
				}
				handler.setCursorStack(ItemStack.EMPTY);
				if (!creative) {
					dropAway(player, cursor);
				}
			}
		}
		var craft = player.playerScreenHandler.getCraftingInput();
		for (int i = 0; i < craft.size(); i++) {
			ItemStack stack = craft.getStack(i);
			if (!stack.isEmpty() && itemBlocked(player, stack)) {
				if (first.isEmpty()) {
					first = stack.copy();
				}
				craft.setStack(i, ItemStack.EMPTY);
				if (!creative) {
					dropAway(player, stack);
				}
			}
		}
		if (!first.isEmpty()) {
			player.playerScreenHandler.syncState();
			if (handler != null && handler != player.playerScreenHandler) {
				handler.syncState();
			}
			denyItem(player, first);
		}
	}

	private static void dropAway(ServerPlayerEntity player, ItemStack stack) {
		var entity = new net.minecraft.entity.ItemEntity(player.getWorld(), player.getX(), player.getEyeY() - 0.3,
				player.getZ(), stack.copy());
		entity.setPickupDelay(60);
		entity.setVelocity(player.getRotationVector().multiply(0.3));
		player.getWorld().spawnEntity(entity);
	}
}
