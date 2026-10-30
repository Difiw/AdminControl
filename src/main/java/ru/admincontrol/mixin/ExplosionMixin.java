package ru.admincontrol.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.ClanWarService;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.HomeProtection;

import java.util.List;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	@Final
	private World world;

	@Shadow
	@Final
	private double x;

	@Shadow
	@Final
	private double y;

	@Shadow
	@Final
	private double z;

	@Shadow
	@Final
	private float power;

	@Shadow
	public abstract List<BlockPos> getAffectedBlocks();

	@Shadow
	public abstract net.minecraft.entity.LivingEntity getCausingEntity();

	@Inject(method = "affectWorld", at = @At("HEAD"))
	private void adminControl$protectEventAndWar(boolean particles, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (this.world != null && !this.world.isClient) {
			ru.admincontrol.util.PrivateService.onExplosion(this.world, this.x, this.y, this.z, this.power,
					this.getCausingEntity());
		}
		List<BlockPos> blocks = this.getAffectedBlocks();
		if (blocks == null || blocks.isEmpty()) {
			return;
		}
		if (EventService.isMeat()) {
			blocks.removeIf(EventService::meatContains);
		}
		ClanWarService.protectExplosions(this.world, blocks);
		HomeProtection.protectExplosions(this.world, blocks);
	}
}
