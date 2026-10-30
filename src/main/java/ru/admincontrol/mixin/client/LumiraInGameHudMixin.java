package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.lumira.client.LumiraHud;

/** В сюжетке ванильные панель быстрого доступа, полосы здоровья/сытости/брони и опыта заменены магическим HUD. */
@Mixin(InGameHud.class)
public abstract class LumiraInGameHudMixin {
	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
	private void admin_control$hotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
		if (LumiraHud.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
	private void admin_control$status(DrawContext context, CallbackInfo ci) {
		if (LumiraHud.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void admin_control$xp(DrawContext context, int x, CallbackInfo ci) {
		if (LumiraHud.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
	private void admin_control$held(DrawContext context, CallbackInfo ci) {
		if (LumiraHud.active()) {
			ci.cancel();
		}
	}
}
