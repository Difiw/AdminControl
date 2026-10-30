package ru.admincontrol.util;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

public final class CreativeCatalog {
	public static final int PAGE_SIZE = 45;
	public static final String[] TAB_LABELS = {
			"Все", "Блоки", "Бой", "Инструменты", "Еда", "Зелья", "Разное"
	};

	private CreativeCatalog() {
	}

	public static int tabs() {
		return TAB_LABELS.length;
	}

	public static String label(int tab) {
		int i = Math.floorMod(tab, TAB_LABELS.length);
		return TAB_LABELS[i];
	}

	public static List<ItemStack> items(int tab) {
		int kind = Math.floorMod(tab, TAB_LABELS.length);
		List<ItemStack> out = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (item == Items.AIR || !matches(kind, item)) {
				continue;
			}
			out.add(new ItemStack(item));
		}
		return out;
	}

	public static int pages(int tab) {
		int size = items(tab).size();
		return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private static boolean matches(int tab, Item item) {
		boolean block = item instanceof BlockItem;
		boolean combat = item instanceof SwordItem || item instanceof ArmorItem || item instanceof BowItem
				|| item instanceof CrossbowItem || item == Items.SHIELD || item == Items.TRIDENT
				|| item == Items.ARROW || item == Items.SPECTRAL_ARROW || item == Items.TIPPED_ARROW
				|| item == Items.TOTEM_OF_UNDYING;
		boolean tool = item instanceof ToolItem || item instanceof MiningToolItem || item == Items.SHEARS
				|| item == Items.FLINT_AND_STEEL || item == Items.FISHING_ROD || item == Items.COMPASS
				|| item == Items.CLOCK;
		boolean food = item.isFood();
		boolean potion = item instanceof PotionItem || item == Items.EXPERIENCE_BOTTLE
				|| item == Items.DRAGON_BREATH || item == Items.GLASS_BOTTLE;
		return switch (tab) {
			case 1 -> block;
			case 2 -> combat;
			case 3 -> tool && !combat;
			case 4 -> food;
			case 5 -> potion;
			case 6 -> !block && !combat && !tool && !food && !potion;
			default -> true;
		};
	}
}
