package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.CwTransitionOverlay;

/**
 * While a Люмира transition is running the vanilla "loading terrain" screen draws nothing of its own (no text, no
 * dirt background); the transition overlay is shown instead. Every other world change keeps the vanilla screen.
 */
@Mixin(DownloadingTerrainScreen.class)
public abstract class DownloadingTerrainScreenMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void adminControl$hideWhileTransition(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!CwTransitionOverlay.active()) return;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFF050709);
		CwTransitionOverlay.render(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
		ci.cancel();
	}
}
