package ru.admincontrol.mixin.client;

import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.AuthLock;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void adminControl$lockMove(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		if (!AuthLock.active() && ru.admincontrol.client.ClientFeatures.freeze() <= 0) {
			return;
		}
		KeyboardInput input = (KeyboardInput) (Object) this;
		input.movementForward = 0;
		input.movementSideways = 0;
		input.jumping = false;
		input.sneaking = false;
	}
}
