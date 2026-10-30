package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.AutoFit;

@Mixin(MinecraftClient.class)
public class MinecraftClientFitMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void adminControl$fitScreen(Screen screen, CallbackInfo ci) {
		AutoFit.beforeOpen((MinecraftClient) (Object) this, screen);
	}

	@Inject(method = "onResolutionChanged", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/util/Window;setScaleFactor(D)V", shift = At.Shift.AFTER))
	private void adminControl$refit(CallbackInfo ci) {
		AutoFit.afterRescale((MinecraftClient) (Object) this);
	}
}
