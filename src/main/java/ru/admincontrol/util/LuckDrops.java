package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;

public final class LuckDrops {
	private LuckDrops() {
	}

	public static List<ItemStack> applyBlockDrops(BlockState state, LootContextParameterSet.Builder builder, List<ItemStack> original) {
		List<ItemStack> ores = CasinoService.applyOreLuck(state, builder, original);
		return applyGravelFlint(state, builder, ores);
	}

	public static List<ItemStack> applyGravelFlint(BlockState state, LootContextParameterSet.Builder builder, List<ItemStack> original) {
		if (original == null || state == null || builder == null || DataManager.get() == null) {
			return original;
		}
		if (!state.isOf(Blocks.GRAVEL)) {
			return original;
		}
		ItemStack tool = builder.getOptional(LootContextParameters.TOOL);
		if (tool != null && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, tool) > 0) {
			return original;
		}
		Entity entity = builder.getOptional(LootContextParameters.THIS_ENTITY);
		if (!(entity instanceof ServerPlayerEntity player)) {
			return original;
		}
		PlayerRecord record = DataManager.get().progress(player);
		double luck = record.luck();
		if (luck <= 0) {
			return original;
		}
		ServerWorld world = builder.getWorld();
		Random random = world.getRandom();
		boolean hasFlint = false;
		boolean hasGravel = false;
		for (ItemStack stack : original) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (stack.isOf(Items.FLINT)) {
				hasFlint = true;
			}
			if (stack.isOf(Items.GRAVEL)) {
				hasGravel = true;
			}
		}
		List<ItemStack> out = new ArrayList<>(original.size() + 1);
		for (ItemStack stack : original) {
			out.add(stack == null || stack.isEmpty() ? stack : stack.copy());
		}
		// Базовый ванильный шанс ~10%. Удача добавляет шанс получить кремний, если выпал только гравий.
		double convertChance = Math.min(0.92, luck * 0.05);
		if (!hasFlint && hasGravel && random.nextDouble() < convertChance) {
			for (int i = 0; i < out.size(); i++) {
				ItemStack stack = out.get(i);
				if (stack != null && stack.isOf(Items.GRAVEL) && !stack.isEmpty()) {
					if (stack.getCount() <= 1) {
						out.set(i, new ItemStack(Items.FLINT));
					} else {
						stack.decrement(1);
						out.add(new ItemStack(Items.FLINT));
					}
					hasFlint = true;
					break;
				}
			}
		}
		// Если кремний уже выпал — шанс на ещё один.
		if (hasFlint) {
			double extraChance = Math.min(0.65, luck * 0.03);
			if (random.nextDouble() < extraChance) {
				out.add(new ItemStack(Items.FLINT));
			}
		}
		return out;
	}

	public static float lootContextLuck(ServerPlayerEntity player, float vanillaLuck) {
		if (player == null || DataManager.get() == null) {
			return vanillaLuck;
		}
		double luck = DataManager.get().progress(player).luck();
		return vanillaLuck + (float) (luck * 0.4);
	}

	public static ItemStack boostMobDrop(ServerPlayerEntity killer, ItemStack stack, Random random) {
		if (killer == null || stack == null || stack.isEmpty() || DataManager.get() == null || random == null) {
			return stack;
		}
		double luck = DataManager.get().progress(killer).luck();
		if (luck <= 0) {
			return stack;
		}
		ItemStack copy = stack.copy();
		int base = copy.getCount();
		int guaranteed = (int) (luck / 8.0);
		double chance = Math.min(0.75, luck * 0.04);
		int extra = guaranteed * base;
		for (int i = 0; i < base; i++) {
			if (random.nextDouble() < chance) {
				extra++;
			}
		}
		if (extra > 0) {
			copy.setCount(Math.min(copy.getMaxCount() * 8, base + extra));
		}
		return copy;
	}
}
