package ru.admincontrol.lumira.recipe;

import com.google.gson.JsonObject;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraContent;

/**
 * Обычный фигурный рецепт, который совпадает только пока пересчёт идёт внутри рунного верстака. На обычном
 * верстаке и в сетке 2×2 игрока продвинутые предметы собрать нельзя.
 */
public class RuneShapedRecipe extends ShapedRecipe {
	public static final ThreadLocal<Boolean> GATE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	public RuneShapedRecipe(ShapedRecipe base) {
		super(base.getId(), base.getGroup(), base.getCategory(), base.getWidth(), base.getHeight(),
				base.getIngredients(), base.getOutput(null), base.showNotification());
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		return GATE.get() && super.matches(inventory, world);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return LumiraContent.RUNE_SHAPED;
	}

	public static final class Serializer implements RecipeSerializer<RuneShapedRecipe> {
		@Override
		public RuneShapedRecipe read(Identifier id, JsonObject json) {
			return new RuneShapedRecipe(RecipeSerializer.SHAPED.read(id, json));
		}

		@Override
		public RuneShapedRecipe read(Identifier id, PacketByteBuf buf) {
			return new RuneShapedRecipe(RecipeSerializer.SHAPED.read(id, buf));
		}

		@Override
		public void write(PacketByteBuf buf, RuneShapedRecipe recipe) {
			RecipeSerializer.SHAPED.write(buf, recipe);
		}
	}
}
