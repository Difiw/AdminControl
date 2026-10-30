package ru.admincontrol.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.cw.CwChatFilter;

import java.util.function.Function;

/** Marks server-wide text broadcasts (join/leave, deaths, advancements, /say…) so story players are skipped. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerStoryChatMixin {
	@Inject(method = "broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V", at = @At("HEAD"))
	private void admin_control$begin(Text message, Function<ServerPlayerEntity, Text> playerMessageFactory, boolean overlay, CallbackInfo ci) {
		CwChatFilter.enterBroadcast();
	}

	@Inject(method = "broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V", at = @At("RETURN"))
	private void admin_control$end(Text message, Function<ServerPlayerEntity, Text> playerMessageFactory, boolean overlay, CallbackInfo ci) {
		CwChatFilter.exitBroadcast();
	}
}
