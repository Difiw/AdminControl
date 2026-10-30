package ru.admincontrol.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.AutoFurnaceScreen;
import ru.admincontrol.client.BackButton;
import ru.admincontrol.client.ClientFeatures;
import ru.admincontrol.client.ExtraChestScreen;
import ru.admincontrol.client.FeatureButton;
import ru.admincontrol.client.SkillTreeButton;
import ru.admincontrol.net.ModPackets;

import java.util.List;

@Mixin(Screen.class)
public abstract class InventoryScreenMixin {
	@Shadow
	protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

	@Inject(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("TAIL"))
	private void adminControl$featureTabsOpen(CallbackInfo ci) {
		adminControl$featureTabs();
	}

	@Inject(method = "clearAndInit()V", at = @At("TAIL"))
	private void adminControl$featureTabsResize(CallbackInfo ci) {
		adminControl$featureTabs();
	}

	private void adminControl$featureTabs() {
		if (ru.admincontrol.lumira.client.LumiraHud.inCwWorld()) {
			return;
		}
		Screen self = (Screen) (Object) this;
		for (Element child : self.children()) {
			if (child instanceof FeatureButton || child instanceof SkillTreeButton || child instanceof BackButton) {
				return;
			}
		}
		String currentId;
		if (self instanceof InventoryScreen) {
			currentId = "";
		} else if (self instanceof ExtraChestScreen && self.getTitle().getString().equals("Рюкзак")) {
			currentId = "backpack";
		} else if (self instanceof AutoFurnaceScreen) {
			currentId = "furnace";
		} else if (self instanceof CraftingScreen) {
			currentId = "workbench";
		} else {
			return;
		}
		List<String> owned = ClientFeatures.order();
		boolean other = false;
		for (String id : owned) {
			other |= !id.equals(currentId);
		}
		if (other) {
			HandledScreen<?> screen = (HandledScreen<?>) self;
			int index = 0;
			for (String id : owned) {
				FeatureButton button = FeatureButton.of(screen, index, id, currentId);
				if (button != null) {
					this.addDrawableChild(button);
					index++;
				}
			}
		}
		HandledScreen<?> handled = (HandledScreen<?>) self;
		if (!(self instanceof InventoryScreen)) {
			this.addDrawableChild(new BackButton(handled));
		}
		if (ClientPlayNetworking.canSend(ModPackets.SKILL_OPEN)) {
			this.addDrawableChild(new SkillTreeButton(handled));
		}
	}
}
