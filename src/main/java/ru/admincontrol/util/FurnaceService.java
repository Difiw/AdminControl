package ru.admincontrol.util;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Встроенная автопечь: плавит без топлива, пока игрок в сети. */
public final class FurnaceService {
	public static final int MAX_FURNACES = 3;
	public static final int SLOTS = MAX_FURNACES * 2;
	private static final Map<UUID, SimpleInventory> LIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, int[]> PROGRESS = new ConcurrentHashMap<>();

	private FurnaceService() {
	}

	public static ModSettings s() {
		return DataManager.get().settings();
	}

	public static int level(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return 0;
		}
		PlayerRecord rec = DataManager.get().progress(player);
		if (SkillTreeService.nodeOff(rec, ru.admincontrol.skill.SkillTree.FURNACE)) {
			return 0;
		}
		return Math.max(0, Math.min(s().furnaceMaxLevel(), rec.furnaceLevel));
	}

	public static int count(ServerPlayerEntity player) {
		return s().furnaceCountFor(level(player));
	}

	public static int intervalTicks(ServerPlayerEntity player) {
		return s().furnaceSecondsFor(level(player)) * 20;
	}

	public static int progress(ServerPlayerEntity player, int furnace) {
		int[] p = player == null ? null : PROGRESS.get(player.getUuid());
		return p == null || furnace < 0 || furnace >= p.length ? 0 : p[furnace];
	}

	public static SimpleInventory inventory(ServerPlayerEntity player) {
		return LIVE.computeIfAbsent(player.getUuid(), id -> {
			PlayerRecord rec = DataManager.get().progress(player);
			SimpleInventory inv = new SimpleInventory(SLOTS);
			StackIo.fill(inv, rec.furnaceItems, SLOTS);
			inv.addListener(changed -> {
				rec.furnaceItems = StackIo.writeInv(changed, SLOTS);
				DataManager.get().markDirty();
			});
			return inv;
		});
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		SimpleInventory inv = LIVE.remove(player.getUuid());
		PROGRESS.remove(player.getUuid());
		if (inv != null && DataManager.get() != null) {
			DataManager.get().progress(player).furnaceItems = StackIo.writeInv(inv, SLOTS);
			DataManager.get().markDirty();
		}
	}

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player)) {
				continue;
			}
			int level = level(player);
			if (level <= 0) {
				continue;
			}
			SimpleInventory inv = inventory(player);
			int[] prog = PROGRESS.computeIfAbsent(player.getUuid(), id -> new int[MAX_FURNACES]);
			int interval = intervalTicks(player);
			int furnaces = count(player);
			for (int i = 0; i < MAX_FURNACES; i++) {
				if (i >= furnaces) {
					prog[i] = 0;
					continue;
				}
				ItemStack input = inv.getStack(i * 2);
				ItemStack output = inv.getStack(i * 2 + 1);
				ItemStack result = smelt(player.getServerWorld(), input);
				if (result.isEmpty() || !fits(output, result)) {
					prog[i] = 0;
					continue;
				}
				if (++prog[i] < interval) {
					continue;
				}
				prog[i] = 0;
				input.decrement(1);
				if (output.isEmpty()) {
					inv.setStack(i * 2 + 1, result.copy());
				} else {
					output.increment(result.getCount());
				}
				inv.setStack(i * 2, input.isEmpty() ? ItemStack.EMPTY : input);
				inv.markDirty();
			}
		}
	}

	public static ItemStack smelt(ServerWorld world, ItemStack input) {
		if (world == null || input == null || input.isEmpty()) {
			return ItemStack.EMPTY;
		}
		SimpleInventory probe = new SimpleInventory(input.copyWithCount(1));
		return world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, probe, world)
				.map(recipe -> recipe.craft(probe, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
	}

	private static boolean fits(ItemStack output, ItemStack result) {
		if (output.isEmpty()) {
			return true;
		}
		return ItemStack.canCombine(output, result) && output.getCount() + result.getCount() <= output.getMaxCount();
	}
}
