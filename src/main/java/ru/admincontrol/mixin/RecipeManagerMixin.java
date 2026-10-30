package ru.admincontrol.mixin;

import com.google.gson.JsonElement;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.admincontrol.lumira.recipe.RecipeEdits;

import java.util.Map;

/** Накладывает правки администратора (редактор крафтов в /dserver) на рецепты при каждой загрузке данных. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
	@ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
			at = @At("HEAD"), argsOnly = true)
	private Map<Identifier, JsonElement> admin_control$overlay(Map<Identifier, JsonElement> map) {
		return RecipeEdits.overlay(map);
	}
}
