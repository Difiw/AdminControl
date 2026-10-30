package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Клавиша инвентаря (E) закрывает любое окно мода, кроме момента ввода текста. */
@Mixin(HandledScreen.class)
public class HandledScreenKeysMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void adminControl$inventoryKeyCloses(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		Screen self = (Screen) (Object) this;
		if (!self.getClass().getName().startsWith("ru.admincontrol.client.")) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || !client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			return;
		}
		if (self.getFocused() instanceof TextFieldWidget field && field.isFocused()) {
			cir.setReturnValue(true);
			return;
		}
		if (self.shouldCloseOnEsc()) {
			self.close();
		}
		cir.setReturnValue(true);
	}
}
