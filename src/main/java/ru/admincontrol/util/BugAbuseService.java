package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import ru.admincontrol.data.DataManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Поиск багаюза: находки уходят в баг-лист и администрации, игрока и админа не наказывают. */
public final class BugAbuseService {
	/** Одна и та же находка у игрока — не чаще раза за этот срок. */
	private static final long COOLDOWN_MS = 10L * 60 * 1000;
	private static final long ITEM_COOLDOWN_MS = 30L * 60 * 1000;
	/** Сколько секунд подряд голова в блоке, прежде чем это баг, а не случайность. */
	private static final int WALL_SECONDS = 5;
	private static final Set<String> ILLEGAL = Set.of(
			"bedrock", "barrier", "command_block", "chain_command_block", "repeating_command_block",
			"command_block_minecart", "structure_block", "structure_void", "jigsaw", "light", "debug_stick",
			"end_portal_frame", "spawner", "reinforced_deepslate", "budding_amethyst", "petrified_oak_slab",
			"knowledge_book");
	private static final Map<String, Long> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> IN_WALL = new ConcurrentHashMap<>();

	private BugAbuseService() {
	}

	/** Раз в секунду; инвентарь проверяется раз в 5 секунд. */
	public static void tick(MinecraftServer server, int tickCounter) {
		if (server == null || DataManager.get() == null || !DataManager.get().settings().bugSearchEnabled) {
			return;
		}
		boolean items = tickCounter % 100 == 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!watched(p)) {
				IN_WALL.remove(p.getUuid());
				continue;
			}
			checkPlace(p);
			checkWall(p);
			if (items && !TrainingService.active(p) && !EventService.member(p)) {
				checkInventory(p);
			}
		}
	}

	private static boolean watched(ServerPlayerEntity p) {
		return p != null && !NpcService.isNpc(p) && !AuthService.pending(p) && !p.isSpectator() && !p.isCreative()
				&& !EventMapEditor.participant(p);
	}

	private static void checkPlace(ServerPlayerEntity p) {
		ServerWorld world = p.getServerWorld();
		if (world.getRegistryKey() == World.NETHER && p.getY() >= 128) {
			once(p, "roof", COOLDOWN_MS, "Крыша Ада",
					"Игрок над бедроковым потолком Ада (Y=" + p.getBlockY() + "). Туда попадают только через баг: перл, лодка, портал или проход сквозь бедрок.");
		}
		WorldBorder border = world.getWorldBorder();
		double inside = border.getDistanceInsideBorder(p.getX(), p.getZ());
		if (inside < -2) {
			once(p, "border", COOLDOWN_MS, "За границей мира",
					"Игрок за границей мира на " + (int) Math.ceil(-inside) + " бл. Граница проходится только через баг (лодка, перл, вход/выход из Ада у края).");
		}
	}

	private static void checkWall(ServerPlayerEntity p) {
		if (!p.isInsideWall() || p.hasVehicle() || p.isSleeping()) {
			IN_WALL.remove(p.getUuid());
			return;
		}
		int seconds = IN_WALL.merge(p.getUuid(), 1, Integer::sum);
		if (seconds < WALL_SECONDS) {
			return;
		}
		BlockPos eye = BlockPos.ofFloored(p.getEyePos());
		BlockState state = p.getWorld().getBlockState(eye);
		once(p, "wall", COOLDOWN_MS, "Внутри блока",
				"Голова игрока " + seconds + "+ с внутри блока «" + ItemLabels.of(state.getBlock()) + "» (" + eye.toShortString()
						+ "). Возможен проход сквозь стену или застревание из-за бага коллизии.");
	}

	private static void checkInventory(ServerPlayerEntity p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(stack.getItem());
			String name = ItemLabels.of(stack);
			if ("minecraft".equals(id.getNamespace()) && (ILLEGAL.contains(id.getPath()) || id.getPath().endsWith("_spawn_egg"))) {
				once(p, "item:" + id, ITEM_COOLDOWN_MS, "Запрещённый предмет",
						"В инвентаре «" + name + "» ×" + stack.getCount() + " — в выживании не добывается (баг, дюп или выдача администратором).");
			}
			if (stack.getCount() > stack.getMaxCount()) {
				once(p, "stack:" + id, ITEM_COOLDOWN_MS, "Переполненный стак",
						"«" + name + "» ×" + stack.getCount() + " в одной ячейке при максимуме " + stack.getMaxCount() + " — признак дюпа.");
			}
			for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.get(stack).entrySet()) {
				Enchantment ench = e.getKey();
				int level = e.getValue() == null ? 0 : e.getValue();
				if (ench != null && level > ench.getMaxLevel()) {
					once(p, "ench:" + id + ":" + Registries.ENCHANTMENT.getId(ench), ITEM_COOLDOWN_MS, "Зачарование выше максимума",
							"«" + name + "»: «" + ench.getName(level).getString() + "» — уровень " + level + " при максимуме "
									+ ench.getMaxLevel() + ".");
				}
			}
		}
	}

	/** Блок, который в выживании не ломается (бедрок, рамка портала Энда, барьер…), сломан игроком. */
	public static void onBreak(ServerPlayerEntity p, World world, BlockPos pos, BlockState state) {
		if (DataManager.get() == null || !DataManager.get().settings().bugSearchEnabled || !watched(p)
				|| world == null || pos == null || state == null) {
			return;
		}
		if (state.getHardness(world, pos) >= 0) {
			return;
		}
		if (!cooldownPassed(p, "break:" + pos.asLong(), COOLDOWN_MS)) {
			return;
		}
		BugList.report(p, "Сломан неразрушимый блок",
				"Игрок сломал «" + ItemLabels.of(state.getBlock()) + "» в " + pos.toShortString()
						+ " — в выживании этот блок не ломается.",
				world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	private static void once(ServerPlayerEntity p, String key, long cooldown, String type, String description) {
		if (cooldownPassed(p, key, cooldown)) {
			BugList.report(p, type, description);
		}
	}

	private static boolean cooldownPassed(ServerPlayerEntity p, String key, long cooldown) {
		String full = p.getUuidAsString() + "|" + key;
		long now = System.currentTimeMillis();
		Long last = LAST.get(full);
		if (last != null && now - last < cooldown) {
			return false;
		}
		LAST.put(full, now);
		if (LAST.size() > 5000) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > ITEM_COOLDOWN_MS);
		}
		return true;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			IN_WALL.remove(player.getUuid());
		}
	}
}
