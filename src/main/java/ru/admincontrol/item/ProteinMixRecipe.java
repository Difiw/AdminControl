package ru.admincontrol.item;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Батончик + любой коктейль → батончик с пометкой «смесь» (3 минуты). */
public class ProteinMixRecipe extends SpecialCraftingRecipe {
	public ProteinMixRecipe(Identifier id, CraftingRecipeCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		ItemStack bar = ItemStack.EMPTY;
		ItemStack shake = ItemStack.EMPTY;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof ProteinBarItem) {
				if (!bar.isEmpty() || ProteinBarItem.mixed(stack)) {
					return false;
				}
				bar = stack;
			} else if (stack.getItem() instanceof ProteinShakeItem) {
				if (!shake.isEmpty()) {
					return false;
				}
				shake = stack;
			} else {
				return false;
			}
		}
		return !bar.isEmpty() && !shake.isEmpty();
	}

	@Override
	public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.getItem() instanceof ProteinBarItem) {
				ItemStack out = stack.copy();
				out.setCount(1);
				return ProteinBarItem.markMixed(out);
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModItems.PROTEIN_MIX_SERIALIZER;
	}
}
