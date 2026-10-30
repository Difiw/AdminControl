package ru.admincontrol.mixin;

import net.minecraft.item.HoneycombItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.PrivateService;

@Mixin(HoneycombItem.class)
public class HoneycombItemMixin {
	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void adminControl$noPrivateWax(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (PrivateService.isPrivateBlock(context.getWorld(), context.getBlockPos())) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}
}
