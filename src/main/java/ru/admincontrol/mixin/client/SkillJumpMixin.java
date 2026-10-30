package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;

/** «Пружина»: прыжок считает клиент своего игрока, уровень приходит с сервера. */
@Mixin(LivingEntity.class)
public class SkillJumpMixin {
	@Inject(method = "getJumpVelocity", at = @At("RETURN"), cancellable = true)
	private void adminControl$spring(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient || self != MinecraftClient.getInstance().player) {
			return;
		}
		double points = SkillEffects.clientJump(self);
		if (points > 0) {
			cir.setReturnValue(cir.getReturnValueF() + SkillTree.jumpBonus(points));
		}
	}
}
