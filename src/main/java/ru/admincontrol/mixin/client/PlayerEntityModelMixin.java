package ru.admincontrol.mixin.client;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.CwNpcAnimations;

/** Applies CW bone animations (bound NPC cycles and the editor preview) after vanilla poses the player model. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void adminControl$cwAnimation(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		CwNpcAnimations.apply((PlayerEntityModel<?>) (Object) this, entity);
	}
}
