package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.lumira.client.LumiraHud;

/**
 * Для перекрашенных окон контейнеров: не рисуются ванильные фоновые текстуры (панель рисует LumiraContainerMixin),
 * а тёмно-серый ванильный цвет заголовков заменяется светлым, чтобы текст читался на тёмной панели.
 */
@Mixin(DrawContext.class)
public abstract class LumiraDrawContextMixin {
	@Inject(method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", at = @At("HEAD"), cancellable = true)
	private void admin_control$skipBackground(Identifier texture, int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
		if (!texture.getNamespace().equals("minecraft") || !texture.getPath().startsWith("textures/gui/container/")
				|| !LumiraHud.restyle(MinecraftClient.getInstance().currentScreen)) {
			return;
		}
		DrawContext self = (DrawContext) (Object) this;
		if (texture.getPath().contains("tabs")) {
			// вкладки и ползунок творческого инвентаря рисуются в теме сюжета
			if (width == 26 && height == 32) {
				admin_control$tab(self, x, y, v);
				ci.cancel();
			} else if (width == 12 && height == 15) {
				admin_control$scroller(self, x, y, u);
				ci.cancel();
			}
			return;
		}
		if (u == 0 && width >= 100 && height >= 30) {
			ci.cancel();
		}
	}

	private static void admin_control$tab(DrawContext c, int x, int y, int v) {
		boolean top = v < 64;
		boolean selected = v == 32 || v == 96;
		int border = selected ? 0xFF3FE0D0 : 0xFF39505A;
		int fill = selected ? 0xFF222A32 : 0xFF141A20;
		int ty = top ? (selected ? y : y + 3) : y;
		int th = top ? (selected ? 32 : 29) : (selected ? 32 : 29);
		c.fill(x, ty, x + 26, ty + th, border);
		c.fill(x + 1, ty + (top ? 1 : 0), x + 25, ty + th - (top ? 0 : 1), fill);
		if (selected) {
			c.fill(x + 1, top ? ty + th - 2 : ty, x + 25, top ? ty + th : ty + 2, 0xFF222A32);
		}
	}

	private static void admin_control$scroller(DrawContext c, int x, int y, int u) {
		boolean enabled = u < 240;
		c.fill(x, y, x + 12, y + 15, enabled ? 0xFF3FE0D0 : 0xFF39505A);
		c.fillGradient(x + 1, y + 1, x + 11, y + 14, enabled ? 0xFF6FF0E4 : 0xFF2B333B, enabled ? 0xFF1F9C90 : 0xFF1B2229);
		c.fill(x + 3, y + 5, x + 9, y + 6, enabled ? 0xFF0F4A44 : 0xFF11161A);
		c.fill(x + 3, y + 8, x + 9, y + 9, enabled ? 0xFF0F4A44 : 0xFF11161A);
		c.fill(x + 3, y + 11, x + 9, y + 12, enabled ? 0xFF0F4A44 : 0xFF11161A);
	}

	@ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int admin_control$titleColor(int color, TextRenderer renderer, Text text, int x, int y, int sameColor, boolean shadow) {
		return color == 4210752 && LumiraHud.restyle(MinecraftClient.getInstance().currentScreen) ? 0xFFE8F4F2 : color;
	}
}
