package ru.admincontrol.mixin;

import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.item.ItemEncryption;
import ru.admincontrol.lumira.LumiraContent;

/** Название творческой вкладки Люмиры шифруется вместе с предметами и показывается, когда шифрование выключено. */
@Mixin(ItemGroup.class)
public abstract class LumiraGroupNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void admin_control$name(CallbackInfoReturnable<Text> cir) {
		if (LumiraContent.GROUP != null && (Object) this == LumiraContent.GROUP && ItemEncryption.groupHidden()) {
			cir.setReturnValue(ItemEncryption.maskedName());
		}
	}
}
