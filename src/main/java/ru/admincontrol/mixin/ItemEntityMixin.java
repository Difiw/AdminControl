package ru.admincontrol.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Unique
	private ItemStack adminControl$pickupStack = ItemStack.EMPTY;

	@Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
	private void adminControl$pickupBlock(PlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		// Plot NPCs are scenery/actors: thrown items must never enter their inventory.
		// Ordinary NPCs deliberately keep vanilla pickup behaviour.
		if (ru.admincontrol.util.CwNpcService.isStoryNpc(serverPlayer)) {
			ci.cancel();
			return;
		}
		if (AuthService.pending(serverPlayer) || ru.admincontrol.util.FreezeService.actionsBlocked(serverPlayer)) {
			ci.cancel();
			return;
		}
		ItemStack ground = ((ItemEntity) (Object) this).getStack();
		if (ru.admincontrol.util.CwService.itemBlocked(serverPlayer, ground)) {
			ru.admincontrol.util.CwService.denyItem(serverPlayer, ground);
			ci.cancel();
			return;
		}
		this.adminControl$pickupStack = ((ItemEntity) (Object) this).getStack().copy();
	}

	@Inject(method = "onPlayerCollision", at = @At("RETURN"))
	private void adminControl$pickupLog(PlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || AuthService.pending(serverPlayer)) {
			return;
		}
		ItemEntity self = (ItemEntity) (Object) this;
		ItemStack logged = this.adminControl$pickupStack;
		this.adminControl$pickupStack = ItemStack.EMPTY;
		if (logged == null || logged.isEmpty()) {
			return;
		}
		if (self.isRemoved() || self.getStack().isEmpty()) {
			AdminNotify.playerAction(serverPlayer, ActionKind.PICKUP, "подобрал предмет "
					+ ru.admincontrol.util.ItemLabels.of(logged) + " x" + logged.getCount());
		}
	}
}
