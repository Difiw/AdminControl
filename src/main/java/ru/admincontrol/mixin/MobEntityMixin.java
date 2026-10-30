package ru.admincontrol.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.EventService;

@Mixin(MobEntity.class)
public class MobEntityMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void adminControl$calmTarget(LivingEntity target, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (target instanceof ServerPlayerEntity player && EventService.calm(player)) {
			ci.cancel();
			MobEntity self = (MobEntity) (Object) this;
			if (self.getTarget() == player) {
				self.setTarget(null);
			}
		}
	}
}
