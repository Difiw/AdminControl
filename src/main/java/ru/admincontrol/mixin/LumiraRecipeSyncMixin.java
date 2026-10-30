package ru.admincontrol.mixin;

import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.DynamicRegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.admincontrol.item.ItemEncryption;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Блокировка просмотра крафта: рецепты зашифрованных предметов не отправляются клиентам, поэтому EMI/REI/JEI и
 * книга рецептов не могут показать, как они делаются. Сервер при этом крафтит как обычно.
 */
@Mixin(SynchronizeRecipesS2CPacket.class)
public abstract class LumiraRecipeSyncMixin {
	@ModifyVariable(method = "<init>(Ljava/util/Collection;)V", at = @At("HEAD"), argsOnly = true)
	private static Collection<Recipe<?>> admin_control$hideBlocked(Collection<Recipe<?>> recipes) {
		if (!ItemEncryption.recipesBlocked()) {
			return recipes;
		}
		List<Recipe<?>> kept = new ArrayList<>(recipes.size());
		for (Recipe<?> recipe : recipes) {
			boolean blocked = false;
			try {
				List<net.minecraft.item.Item> used = new ArrayList<>();
				for (net.minecraft.recipe.Ingredient ingredient : recipe.getIngredients()) {
					for (net.minecraft.item.ItemStack stack : ingredient.getMatchingStacks()) {
						used.add(stack.getItem());
					}
				}
				blocked = ItemEncryption.recipeBlocked(recipe.getOutput(DynamicRegistryManager.EMPTY).getItem(), used);
			} catch (RuntimeException ignored) {
			}
			if (!blocked) {
				kept.add(recipe);
			}
		}
		return kept;
	}
}
