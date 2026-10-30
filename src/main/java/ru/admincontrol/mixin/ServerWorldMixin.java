package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.WeatherLog;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
	@Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
	private void adminControl$eventSpawnBan(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (EventService.spawnBanned((ServerWorld) (Object) this, entity)
				|| ru.admincontrol.util.EventMapEditor.spawnBanned((ServerWorld) (Object) this, entity)) {
			cir.setReturnValue(false);
			return;
		}
		if (entity instanceof LightningEntity bolt) {
			WeatherLog.onLightning((ServerWorld) (Object) this, bolt);
		}
	}
}
