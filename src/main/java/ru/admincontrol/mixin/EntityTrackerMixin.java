package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.NpcService;

@Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
public class EntityTrackerMixin {
	@Shadow
	@Final
	private Entity entity;

	@Shadow
	private void stopTracking(ServerPlayerEntity player) {
	}

	@Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
	private void adminControl$hidePossessedNpc(ServerPlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (NpcService.shouldHideNpcFrom(this.entity, player)
				|| NpcService.shouldHideControllerFrom(this.entity, player)) {
			this.stopTracking(player);
			ci.cancel();
		}
	}
}
