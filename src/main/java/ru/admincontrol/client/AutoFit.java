package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.Window;
import ru.admincontrol.mixin.client.HandledScreenAccessor;

/**
 * Если окно мода не помещается в текущий масштаб интерфейса (маленькое окно игры или крупный масштаб),
 * на время этого окна масштаб уменьшается, после закрытия возвращается выбранный в настройках.
 */
public final class AutoFit {
	/**
	 * Без запаса: масштаб меняется, только если сама панель не влезает в экран.
	 * Кнопки навигации при нехватке места встают над панелью, сбоку или в её углу.
	 */
	private static final int PAD_W = 0;
	private static final int PAD_H = 0;

	private static boolean changed;

	/** Окно, которое меняет высоту панели уже после открытия: подгонять под наибольшую. */
	public interface Sized {
		int adminControl$maxPanelHeight();
	}

	private AutoFit() {
	}

	public static void beforeOpen(MinecraftClient client, Screen next) {
		if (client == null || client.getWindow() == null) {
			return;
		}
		double target = targetScale(client, next);
		Window window = client.getWindow();
		if (target > 0) {
			if (window.getScaleFactor() != target) {
				window.setScaleFactor(target);
			}
			changed = true;
		} else if (changed) {
			changed = false;
			window.setScaleFactor(defaultScale(client));
		}
	}

	/** После пересчёта масштаба игрой (смена размера окна, F11): снова подогнать открытое окно мода. */
	public static void afterRescale(MinecraftClient client) {
		if (client == null || client.getWindow() == null) {
			return;
		}
		double target = targetScale(client, client.currentScreen);
		if (target > 0) {
			client.getWindow().setScaleFactor(target);
			changed = true;
		} else {
			changed = false;
		}
	}

	private static int defaultScale(MinecraftClient client) {
		return client.getWindow().calculateScaleFactor(client.options.getGuiScale().getValue(), client.forcesUnicodeFont());
	}

	/** Нужный масштаб или 0, если окно и так помещается / это не окно мода. */
	private static double targetScale(MinecraftClient client, Screen screen) {
		if (!(screen instanceof HandledScreen<?> handled)
				|| !screen.getClass().getName().startsWith("ru.admincontrol.client.")) {
			return 0;
		}
		HandledScreenAccessor access = (HandledScreenAccessor) handled;
		int needW = access.adminControl$getBackgroundWidth() + PAD_W;
		int panelH = access.adminControl$getBackgroundHeight();
		if (screen instanceof Sized sized) {
			panelH = Math.max(panelH, sized.adminControl$maxPanelHeight());
		}
		int needH = panelH + PAD_H;
		Window window = client.getWindow();
		int def = defaultScale(client);
		double fit = Math.min(window.getFramebufferWidth() / (double) needW,
				window.getFramebufferHeight() / (double) needH);
		if (fit >= def) {
			return 0;
		}
		return Math.max(1.0, fit);
	}
}
