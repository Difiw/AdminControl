package ru.admincontrol.mixin;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.cw.CwChatFilter;

/**
 * In a story session (test or play) the player receives only story messages, their own chat messages and private
 * messages. Everything the server broadcasts to everybody else never reaches the client at all.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerStoryChatMixin {
	@Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
	private void admin_control$storyChat(SentMessage message, boolean filterMaskEnabled, MessageType.Parameters params, CallbackInfo ci) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		if (CwChatFilter.blocksChat(self, params)) {
			ci.cancel();
		}
	}

	@Inject(method = "sendMessageToClient", at = @At("HEAD"), cancellable = true)
	private void admin_control$storyBroadcast(Text message, boolean overlay, CallbackInfo ci) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		if (CwChatFilter.broadcasting() && CwChatFilter.inStorySession(self)) {
			ci.cancel();
		}
	}
}
