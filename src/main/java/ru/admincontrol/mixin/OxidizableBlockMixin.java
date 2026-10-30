package ru.admincontrol.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.OxidizableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.PrivateService;

@Mixin(OxidizableBlock.class)
public class OxidizableBlockMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void adminControl$noPrivateOxidation(BlockState state, ServerWorld world, BlockPos pos, Random random,
			CallbackInfo ci) {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		if (PrivateService.isPrivateBlock(world, pos)) {
			ci.cancel();
		}
	}
}
