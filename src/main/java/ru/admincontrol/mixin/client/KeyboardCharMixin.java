package ru.admincontrol.mixin.client;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.emi.client.CwEmiOverlay;

/** Символы для поля поиска CW EMI: у Creative свой charTyped, поэтому ввод перехватывается до экрана. */
@Mixin(Keyboard.class)
public abstract class KeyboardCharMixin {
	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void admin_control$emiChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
		if (CwEmiOverlay.charTyped(codePoint)) {
			ci.cancel();
		}
	}
}
