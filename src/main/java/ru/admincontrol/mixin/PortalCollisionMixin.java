package ru.admincontrol.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nether and End portals do nothing inside Люмира. */
@Mixin({NetherPortalBlock.class, EndPortalBlock.class})
public abstract class PortalCollisionMixin {
	@Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
	private void adminControl$noPortalsInStory(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (ru.admincontrol.cw.CwStoryRules.inside(world)) ci.cancel();
	}
}
