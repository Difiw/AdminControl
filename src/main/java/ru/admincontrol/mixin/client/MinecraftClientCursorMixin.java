package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.ActivityLock;
import ru.admincontrol.client.AuthLock;
import ru.admincontrol.client.GuiCursor;
import ru.admincontrol.client.PunishmentScreen;
import ru.admincontrol.util.PunishmentNotice;

@Mixin(MinecraftClient.class)
public class MinecraftClientCursorMixin {
	@Shadow
	public net.minecraft.client.Mouse mouse;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen adminControl$punishScreen(Screen screen) {
		if (!(screen instanceof DisconnectedScreen disconnected)) {
			return screen;
		}
		DisconnectedScreenAccessor access = (DisconnectedScreenAccessor) disconnected;
		PunishmentNotice notice = PunishmentNotice.parse(access.adminControl$reason());
		if (notice == null) {
			return screen;
		}
		return new PunishmentScreen(access.adminControl$parent(), notice);
	}

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void adminControl$blockInventory(Screen screen, CallbackInfo ci) {
		if ((screen instanceof InventoryScreen && ActivityLock.active()) || AuthLock.blocksScreen(screen)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleInputEvents", at = @At("HEAD"))
	private void adminControl$authKeys(CallbackInfo ci) {
		if (AuthLock.active()) {
			AuthLock.suppressKeys((MinecraftClient) (Object) this);
		}
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void adminControl$restoreCursor(Screen screen, CallbackInfo ci) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		if (screen == null || this.mouse == null || self.currentScreen != screen) {
			return;
		}
		if (self.getWindow() == null) {
			return;
		}
		double nx = GuiCursor.valid ? GuiCursor.x : ((MousePosAccessor) this.mouse).adminControl$getX();
		double ny = GuiCursor.valid ? GuiCursor.y : ((MousePosAccessor) this.mouse).adminControl$getY();
		((MousePosAccessor) this.mouse).adminControl$setX(nx);
		((MousePosAccessor) this.mouse).adminControl$setY(ny);
		InputUtil.setCursorParameters(self.getWindow().getHandle(), InputUtil.GLFW_CURSOR_NORMAL, nx, ny);
	}
}
