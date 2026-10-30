package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.AuthLock;
import ru.admincontrol.client.GuiCursor;

@Mixin(Mouse.class)
public class MouseMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private boolean cursorLocked;

	@Shadow
	private double x;

	@Shadow
	private double y;

	@Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
	private void adminControl$lockLook(CallbackInfo ci) {
		if (AuthLock.active() || ru.admincontrol.client.ClientFeatures.freeze() >= 2) {
			ci.cancel();
		}
	}

	@Inject(method = "onCursorPos", at = @At("RETURN"))
	private void adminControl$trackGui(long window, double xpos, double ypos, CallbackInfo ci) {
		if (this.client != null && this.client.currentScreen != null && !this.cursorLocked) {
			GuiCursor.remember(this.x, this.y);
		}
	}

	@Inject(method = "onMouseButton", at = @At("HEAD"))
	private void adminControl$saveClick(long window, int button, int action, int mods, CallbackInfo ci) {
		if (this.client != null && this.client.currentScreen != null) {
			GuiCursor.remember(this.x, this.y);
		}
	}

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void adminControl$schematicScroll(long window,double horizontal,double vertical,CallbackInfo ci){
		if(this.client!=null&&this.client.currentScreen==null&&(AuthLock.active()||ru.admincontrol.client.CwSchematicPreview.scroll(vertical)))ci.cancel();
	}

	@Inject(method = "lockCursor", at = @At("HEAD"))
	private void adminControl$saveBeforeLock(CallbackInfo ci) {
		if (!this.cursorLocked) {
			GuiCursor.remember(this.x, this.y);
		}
	}

	@Inject(method = "unlockCursor", at = @At("HEAD"), cancellable = true)
	private void adminControl$keepGuiCursor(CallbackInfo ci) {
		boolean wasLocked = this.cursorLocked;
		this.cursorLocked = false;
		double nx;
		double ny;
		if (GuiCursor.valid) {
			nx = GuiCursor.x;
			ny = GuiCursor.y;
		} else if (wasLocked && this.client != null && this.client.getWindow() != null) {
			nx = this.client.getWindow().getWidth() / 2.0;
			ny = this.client.getWindow().getHeight() / 2.0;
		} else {
			nx = this.x;
			ny = this.y;
		}
		this.x = nx;
		this.y = ny;
		if (this.client != null && this.client.getWindow() != null) {
			InputUtil.setCursorParameters(this.client.getWindow().getHandle(), InputUtil.GLFW_CURSOR_NORMAL, nx, ny);
		}
		ci.cancel();
	}
}
