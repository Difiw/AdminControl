package ru.admincontrol.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server-side story dimensions report their own clock; the client keeps the time delivered by time packets. */
@Mixin(World.class)
public abstract class WorldTimeMixin {
	@Inject(method="getTimeOfDay",at=@At("HEAD"),cancellable=true)
	private void adminControl$storyTime(CallbackInfoReturnable<Long> cir){World self=(World)(Object)this;if(!self.isClient&&ru.admincontrol.cw.CwStoryRules.inside(self))cir.setReturnValue(ru.admincontrol.cw.CwStoryClock.time(self));}
}
