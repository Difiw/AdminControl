package ru.admincontrol.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.net.ModPackets;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
	@Inject(method = "clickButton", at = @At("HEAD"), cancellable = true)
	private void adminControl$intButton(int syncId, int buttonId, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen == null || client.player == null) {
			return;
		}
		if (!client.currentScreen.getClass().getName().startsWith("ru.admincontrol")) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(syncId);
		buf.writeVarInt(buttonId);
		ClientPlayNetworking.send(ModPackets.SCREEN_BUTTON, buf);
		ci.cancel();
	}
}
