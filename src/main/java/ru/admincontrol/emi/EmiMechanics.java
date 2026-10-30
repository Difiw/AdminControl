package ru.admincontrol.emi;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Способы получения, которые нельзя вывести из таблиц добычи: особые механики ванили (яйцо дракона, элитры, звезда
 * Нижнего мира...). Используется и сервером (дополняет разыгранные таблицы), и клиентом без серверной поддержки.
 */
public final class EmiMechanics {
	private record Entry(Item item, String key, Item icon) {
	}

	private static final List<Entry> CURATED = List.of(
			new Entry(Items.DRAGON_EGG, "dragon_egg", Items.DRAGON_HEAD),
			new Entry(Items.ELYTRA, "elytra", Items.ITEM_FRAME),
			new Entry(Items.DRAGON_HEAD, "dragon_head", Items.ITEM_FRAME),
			new Entry(Items.NETHER_STAR, "nether_star", Items.WITHER_SKELETON_SKULL),
			new Entry(Items.WITHER_ROSE, "wither_rose", Items.WITHER_SKELETON_SKULL),
			new Entry(Items.DRAGON_BREATH, "dragon_breath", Items.GLASS_BOTTLE));

	/** Блоки, которые выглядят добываемыми, но получаются только в творческом режиме. */
	private static final java.util.Set<Item> CREATIVE_ONLY = java.util.Set.of(Items.BUDDING_AMETHYST, Items.SPAWNER, Items.REINFORCED_DEEPSLATE,
			Items.END_PORTAL_FRAME, Items.FROGSPAWN, Items.FARMLAND, Items.DIRT_PATH, Items.INFESTED_STONE, Items.INFESTED_COBBLESTONE,
			Items.INFESTED_STONE_BRICKS, Items.INFESTED_DEEPSLATE, Items.CHORUS_PLANT, Items.PETRIFIED_OAK_SLAB);

	private EmiMechanics() {
	}

	private static EmiRecipe source(Item item, String note, String detail, Item icon) {
		EmiRecipe r = new EmiRecipe();
		r.kind = EmiRecipe.SOURCE;
		r.note = note;
		r.typeId = detail;
		r.inputs.add(icon == null ? new ArrayList<>() : new ArrayList<>(List.of(new ItemStack(icon))));
		r.outputs.add(new ItemStack(item));
		return r;
	}

	/** Все особые механики (для сервера). */
	public static List<EmiRecipe> curated() {
		List<EmiRecipe> out = new ArrayList<>();
		for (Entry e : CURATED) {
			out.add(source(e.item, "mech", e.key, e.icon));
		}
		return out;
	}

	/**
	 * Режим без серверной поддержки: таблицы добычи клиенту недоступны, поэтому честно показываем только особые
	 * механики, «блок из мира» для блоков и «творческий режим» для остального.
	 */
	public static List<EmiRecipe> local(Item item, boolean hasRecipes) {
		List<EmiRecipe> out = new ArrayList<>();
		for (Entry e : CURATED) {
			if (e.item == item) {
				out.add(source(item, "mech", e.key, e.icon));
			}
		}
		if (!hasRecipes && out.isEmpty() && item instanceof BlockItem bi && !(item instanceof SpawnEggItem) && !CREATIVE_ONLY.contains(item)
				&& bi.getBlock().getDefaultState().getHardness(net.minecraft.world.EmptyBlockView.INSTANCE, net.minecraft.util.math.BlockPos.ORIGIN) >= 0) {
			Block b = bi.getBlock();
			out.add(source(item, "world_block", net.minecraft.registry.Registries.BLOCK.getId(b).toString(), item));
		}
		if (out.isEmpty() && !hasRecipes) {
			out.add(source(item, "creative", net.minecraft.registry.Registries.ITEM.getId(item).toString(), null));
		}
		return out;
	}
}
