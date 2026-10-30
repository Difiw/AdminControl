package ru.admincontrol.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.EventService;

@Mixin(PersistentProjectileEntity.class)
public class PersistentProjectileEntityMixin {
	@Inject(method = "onBlockHit", at = @At("HEAD"), cancellable = true)
	private void adminControl$primeTnt(BlockHitResult hit, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
		if (arrow.getWorld() != null && arrow.getWorld().isClient) {
			return;
		}
		if (hit != null && EventService.primeTntFromArrow(arrow, hit.getBlockPos())) {
			ci.cancel();
			return;
		}
		if (hit != null) {
			ru.admincontrol.util.PrivateService.onProjectileHit(arrow.getWorld(), hit.getBlockPos(), arrow.getOwner());
		}
	}
}
