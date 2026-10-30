package ru.admincontrol.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * Isolated Common World: Expedition name-blacklist gate. Runs after (higher priority number) the existing punishment
 * check and only when that check let the player through, so ordinary bans/jails keep their behaviour and screen.
 */
@Mixin(value = PlayerManager.class, priority = 2000)
public abstract class PlayerManagerCweMixin {
	@Inject(method = "checkCanJoin", at = @At("RETURN"), cancellable = true)
	private void adminControl$cweBlacklist(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
		if (!ru.admincontrol.util.ModGate.on() || cir.getReturnValue() != null || profile == null) {
			return;
		}
		Text denial = ru.admincontrol.cw.CweBlacklist.joinDenial(profile.getName(), profile.getId());
		if (denial != null) {
			cir.setReturnValue(denial);
		}
	}
}
