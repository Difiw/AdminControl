package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.AcNavButtons;

@Mixin(Screen.class)
public abstract class ScreenNavClientMixin {
	@Inject(method = "clearChildren", at = @At("RETURN"))
	private void adminControl$navBack(CallbackInfo ci) {
		AcNavButtons.add((Screen) (Object) this);
	}
}
