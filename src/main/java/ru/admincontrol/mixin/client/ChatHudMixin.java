package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.client.CwLeaveConfirmScreen;
import ru.admincontrol.client.CwRespawnScreen;
import ru.admincontrol.client.CwStoryChat;
import ru.admincontrol.client.CwTransitionOverlay;

/**
 * The chat is hidden during Люмира transitions and while the death/save/leave windows are open. Inside the story worlds
 * (build, test, play) the vanilla chat is replaced by the story chat block above the hotbar, outside them it is untouched.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void adminControl$hideChat(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
		var screen = MinecraftClient.getInstance().currentScreen;
		if (CwTransitionOverlay.active() || screen instanceof CwRespawnScreen || screen instanceof CwLeaveConfirmScreen) {
			ci.cancel();
			return;
		}
		if (CwStoryChat.active()) {
			CwStoryChat.render(context, currentTick);
			ci.cancel();
		}
	}

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), cancellable = true)
	private void adminControl$storyMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
		if (CwStoryChat.active()) {
			CwStoryChat.add(message);
			ci.cancel();
		}
	}

	@Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
	private void adminControl$storyStyle(double x, double y, CallbackInfoReturnable<Style> cir) {
		if (CwStoryChat.active()) cir.setReturnValue(CwStoryChat.styleAt(x, y));
	}

	@Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
	private void adminControl$storyScroll(int amount, CallbackInfo ci) {
		if (CwStoryChat.active()) {
			CwStoryChat.scroll(amount);
			ci.cancel();
		}
	}

	@Inject(method = "resetScroll", at = @At("HEAD"))
	private void adminControl$storyResetScroll(CallbackInfo ci) {
		CwStoryChat.resetScroll();
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void adminControl$storyClear(boolean clearHistory, CallbackInfo ci) {
		if (clearHistory) CwStoryChat.clear();
	}
}
