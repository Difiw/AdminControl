package ru.admincontrol.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** CW EMI: у кузнечного рецепта getIngredients() пуст, поэтому шаблон, основу и добавку читаем напрямую. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
	@Accessor("template")
	Ingredient adminControl$template();

	@Accessor("base")
	Ingredient adminControl$base();

	@Accessor("addition")
	Ingredient adminControl$addition();

	@Accessor("result")
	ItemStack adminControl$result();
}
