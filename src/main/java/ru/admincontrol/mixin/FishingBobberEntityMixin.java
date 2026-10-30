package ru.admincontrol.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
	@Shadow
	public abstract PlayerEntity getPlayerOwner();

	@Inject(method = "use", at = @At("RETURN"))
	private void adminControl$fish(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity owner = this.getPlayerOwner();
		if (!(owner instanceof ServerPlayerEntity player) || AuthService.pending(player)) {
			return;
		}
		Integer pulled = cir.getReturnValue();
		if (pulled != null && pulled > 0) {
			AdminNotify.playerAction(player, ActionKind.FISHING, "рыбалка, добыча " + pulled);
		} else {
			AdminNotify.playerAction(player, ActionKind.FISHING, "забросил / подтянул удочку");
		}
	}
}
