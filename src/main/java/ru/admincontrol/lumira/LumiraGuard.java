package ru.admincontrol.lumira;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.cw.CwStoryRules;

import java.util.Set;

/**
 * Защита прогрессии: из обычных руд в сюжетке разрешены только уголь и железо. Продукты исключённых руд не должны
 * появляться в добыче мобов и сундуков сюжетных и строительного измерений.
 */
public final class LumiraGuard {
	private static final Set<String> FORBIDDEN = Set.of(
			"copper_ore", "deepslate_copper_ore", "raw_copper", "copper_ingot", "raw_copper_block",
			"gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold", "gold_ingot", "gold_nugget", "raw_gold_block",
			"diamond_ore", "deepslate_diamond_ore", "diamond",
			"emerald_ore", "deepslate_emerald_ore", "emerald",
			"lapis_ore", "deepslate_lapis_ore", "lapis_lazuli",
			"redstone_ore", "deepslate_redstone_ore", "redstone",
			"nether_quartz_ore", "quartz",
			"ancient_debris", "netherite_scrap", "netherite_ingot");

	private LumiraGuard() {
	}

	public static boolean protectedWorld(World world) {
		return CwStoryRules.inside(world) || world.getRegistryKey().equals(CwBuilderWorld.KEY);
	}

	public static boolean forbidden(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		net.minecraft.util.Identifier id = Registries.ITEM.getId(stack.getItem());
		return id.getNamespace().equals("minecraft") && FORBIDDEN.contains(id.getPath());
	}
}
