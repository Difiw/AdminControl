package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.DataManager;

@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceMixin {
	@Shadow
	public abstract Entity getEntity();

	@Inject(method = "hasPermissionLevel", at = @At("HEAD"), cancellable = true)
	private void adminControl$hasPermissionLevel(int level, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		Entity entity = this.getEntity();
		if (entity instanceof ServerPlayerEntity player && data.cheatsAllowed(player)) {
			cir.setReturnValue(true);
		}
	}
}
