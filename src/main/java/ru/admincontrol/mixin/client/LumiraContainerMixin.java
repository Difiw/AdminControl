package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.lumira.client.LumiraHud;

/**
 * Окна контейнеров (сундуки, бочки, эндер-сундуки, шалкеры, воронки, раздатчики, верстаки, печи, наковальни,
 * творческий инвентарь…) в сюжетных мирах и на стройке разработчика рисуются в одном стиле с инвентарём:
 * тёмная сланцевая панель с бирюзовой рамкой. Ванильные фоновые текстуры при этом не рисуются (см. LumiraDrawContextMixin).
 */
@Mixin(HandledScreen.class)
public abstract class LumiraContainerMixin {
	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V"))
	private void admin_control$panel(DrawContext c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		HandledScreen<?> self = (HandledScreen<?>) (Object) this;
		if (!LumiraHud.restyle(self)) {
			return;
		}
		HandledScreenAccessor acc = (HandledScreenAccessor) self;
		int x = acc.adminControl$getX(), y = acc.adminControl$getY();
		int w = acc.adminControl$getBackgroundWidth(), h = acc.adminControl$getBackgroundHeight();
		c.fillGradient(x, y, x + w, y + h, 0xF0222A32, 0xF0161B21);
		c.drawBorder(x, y, w, h, 0xFFB9C6CE);
		c.drawBorder(x + 2, y + 2, w - 4, h - 4, 0xFF2B6F6A);
		for (int[] corner : new int[][]{{x + 4, y + 4}, {x + w - 8, y + 4}, {x + 4, y + h - 8}, {x + w - 8, y + h - 8}}) {
			c.fill(corner[0], corner[1], corner[0] + 4, corner[1] + 4, 0xFF3FE0D0);
			c.fill(corner[0] + 1, corner[1] + 1, corner[0] + 3, corner[1] + 3, 0xFF161B21);
		}
		if (self instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
			// дорожка полосы прокрутки творческого инвентаря
			c.fill(x + 173, y + 16, x + 189, y + 132, 0xFF0B0E11);
			c.drawBorder(x + 173, y + 16, 16, 116, 0xFF39505A);
		}
		for (Slot slot : self.getScreenHandler().slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			c.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF2B3138);
			c.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF4B555F);
		}
	}
}
