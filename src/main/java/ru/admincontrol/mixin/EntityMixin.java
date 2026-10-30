package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.EventService;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void adminControl$ballPushable(CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (EventService.isFootballBall((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isCollidable", at = @At("HEAD"), cancellable = true)
	private void adminControl$ballCollidable(CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (EventService.isFootballBall((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
	private void adminControl$ballCollidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (EventService.isFootballBall((Entity) (Object) this) || EventService.isFootballBall(other)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
	private void adminControl$ballPush(Entity other, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (EventService.isFootballBall((Entity) (Object) this) || EventService.isFootballBall(other)) {
			ci.cancel();
		}
	}
}
