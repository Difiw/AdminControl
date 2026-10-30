package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.client.AcNavButtons;
import ru.admincontrol.util.NavReady;

@Mixin(HandledScreen.class)
public class HandledScreenNavMixin implements NavReady {
	@Unique
	private boolean adminControl$navReady;

	@Override
	public boolean adminControl$navReady() {
		return this.adminControl$navReady;
	}

	@Override
	public void adminControl$setNavReady(boolean value) {
		this.adminControl$navReady = value;
	}

	@Inject(method = "init()V", at = @At("RETURN"))
	private void adminControl$placeNav(CallbackInfo ci) {
		this.adminControl$navReady = true;
		AcNavButtons.add((HandledScreen<?>) (Object) this);
	}

	@Inject(method = "isClickOutsideBounds", at = @At("HEAD"), cancellable = true)
	private void adminControl$navBounds(double mouseX, double mouseY, int left, int top, int button,
			CallbackInfoReturnable<Boolean> cir) {
		HandledScreen<?> self = (HandledScreen<?>) (Object) this;
		if (!self.getClass().getName().startsWith("ru.admincontrol.client.")) {
			return;
		}
		HandledScreenAccessor access = (HandledScreenAccessor) self;
		int bw = access.adminControl$getBackgroundWidth();
		int bh = access.adminControl$getBackgroundHeight();
		boolean outside = mouseX < left || mouseY < top - 24
				|| mouseX >= left + bw || mouseY >= top + bh + 24;
		cir.setReturnValue(outside);
	}
}
