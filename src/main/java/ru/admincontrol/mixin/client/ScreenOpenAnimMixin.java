package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.ScreenAnim;

@Mixin(Screen.class)
public abstract class ScreenOpenAnimMixin {
	@Inject(method = "renderWithTooltip", at = @At("HEAD"))
	private void adminControl$openAnimBegin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnim.begin((Screen) (Object) this, context);
	}

	@Inject(method = "renderWithTooltip", at = @At("RETURN"))
	private void adminControl$openAnimEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnim.end(context);
	}
}
