package ru.admincontrol.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.admincontrol.lumira.LumiraGuard;

import java.util.function.Consumer;

/** В сюжетных измерениях любая добыча (мобы, сундуки) не выдаёт продукты исключённых руд в обход прогрессии. */
@Mixin(LootTable.class)
public abstract class LumiraLootMixin {
	@ModifyVariable(method = "generateLoot(Lnet/minecraft/loot/context/LootContext;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private Consumer<ItemStack> admin_control$filterForbidden(Consumer<ItemStack> consumer, LootContext context,
															  Consumer<ItemStack> same) {
		if (context == null || context.getWorld() == null || !LumiraGuard.protectedWorld(context.getWorld())) {
			return consumer;
		}
		return stack -> {
			if (!LumiraGuard.forbidden(stack)) {
				consumer.accept(stack);
			}
		};
	}
}
