package ru.admincontrol.emi.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Подключает панель CW EMI к контейнерам и Creative через события экранов Fabric. Ввод символов в поиск приходит из
 * KeyboardCharMixin (у Creative свой charTyped, до которого события экрана не доходят).
 */
public final class CwEmiOverlay {
	private CwEmiOverlay() {
	}

	private static CwEmiPanel current;

	static void setCurrent(CwEmiPanel panel) {
		current = panel;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof HandledScreen<?>) || !eligible(screen)) {
				return;
			}
			CwEmiPanel panel = new CwEmiPanel(screen, false, (item, obtain) -> MinecraftClient.getInstance().setScreen(new CwEmiRecipeScreen(screen, item, obtain)));
			current = panel;
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> panel.render(ctx, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> !panel.mouseClicked(mx, my, button));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hs, vs) -> !panel.mouseScrolled(mx, my, vs));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scan, mods) -> !panel.keyPressed(key, scan, mods));
			ScreenEvents.remove(screen).register(s -> {
				panel.field.setFocused(false);
				if (current == panel) {
					current = null;
				}
			});
		});
	}

	/** Вызывается KeyboardCharMixin до обработки символа экраном. true — символ принят полем поиска. */
	public static boolean charTyped(int codePoint) {
		CwEmiPanel p = current;
		if (p == null || codePoint > 0xFFFF || MinecraftClient.getInstance().currentScreen != p.screen || !p.field.isFocused()) {
			return false;
		}
		p.field.typed((char) codePoint);
		return true;
	}

	static boolean eligible(Screen screen) {
		String name = screen.getClass().getName();
		if (name.startsWith("ru.admincontrol.")) {
			return name.startsWith("ru.admincontrol.lumira.client.Rune");
		}
		return true;
	}
}
