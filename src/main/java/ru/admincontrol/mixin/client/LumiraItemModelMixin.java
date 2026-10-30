package ru.admincontrol.mixin.client;

import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.item.ItemEncryption;
import ru.admincontrol.lumira.LumiraContent;

/** Зашифрованная картинка: вместо модели предмета (в инвентаре, руке и на земле) показывается «неизвестный предмет». */
@Mixin(ItemRenderer.class)
public abstract class LumiraItemModelMixin {
	@Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
	private void admin_control$hideIcon(ItemStack stack, World world, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
		if (LumiraContent.HIDDEN_ITEM != null && ItemEncryption.iconHidden(stack.getItem())) {
			cir.setReturnValue(((ItemRenderer) (Object) this).getModels().getModel(LumiraContent.HIDDEN_ITEM));
		}
	}
}
