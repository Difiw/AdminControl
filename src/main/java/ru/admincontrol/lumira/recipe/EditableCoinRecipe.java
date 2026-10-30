package ru.admincontrol.lumira.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.lumira.CoinRecipes;
import ru.admincontrol.lumira.LumiraContent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Рецепт монеты из настроек сервера (/dserver). Бесформенный; пустой список выключает рецепт. */
public class EditableCoinRecipe extends SpecialCraftingRecipe {
	private final boolean lyra;

	public EditableCoinRecipe(Identifier id, CraftingRecipeCategory category, boolean lyra) {
		super(id, category);
		this.lyra = lyra;
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		if (world.isClient) {
			return false;
		}
		List<String> need = CoinRecipes.recipe(lyra);
		if (need.isEmpty()) {
			return false;
		}
		Map<String, Integer> want = new HashMap<>();
		for (String id : need) {
			want.merge(id, 1, Integer::sum);
		}
		Map<String, Integer> have = new HashMap<>();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty()) {
				have.merge(Registries.ITEM.getId(stack.getItem()).toString(), 1, Integer::sum);
			}
		}
		return want.equals(have);
	}

	@Override
	public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
		return new ItemStack(LumiraContent.item(lyra ? "coin_lyra" : "coin_myrtle"), CoinRecipes.count(lyra));
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 1;
	}

	@Override
	public ItemStack getOutput(DynamicRegistryManager registryManager) {
		return new ItemStack(LumiraContent.item(lyra ? "coin_lyra" : "coin_myrtle"));
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return lyra ? LumiraContent.COIN_LYRA_RECIPE : LumiraContent.COIN_MYRTLE_RECIPE;
	}

	public static String serializerId(boolean lyra) {
		return AdminControlMod.MOD_ID + ":" + (lyra ? "coin_lyra_recipe" : "coin_myrtle_recipe");
	}
}
