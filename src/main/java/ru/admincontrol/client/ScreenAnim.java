package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

/** Плавное появление окон мода: лёгкое увеличение с подъёмом снизу при открытии. */
public final class ScreenAnim {
	private static final long OPEN_MS = 230;
	private static Screen current;
	private static long start;
	private static boolean pushed;

	private ScreenAnim() {
	}

	public static void begin(Screen screen, DrawContext context) {
		pushed = false;
		if (screen == null || !screen.getClass().getName().startsWith("ru.admincontrol.")
				|| screen instanceof ExtraChestScreen || screen instanceof AutoFurnaceScreen) {
			return;
		}
		long now = Util.getMeasuringTimeMs();
		if (screen != current) {
			current = screen;
			start = now;
		}
		double t = (now - start) / (double) OPEN_MS;
		if (t >= 1) {
			return;
		}
		float scale = 0.95f + 0.05f * easeBack(t);
		float lift = 10f * (1 - easeOut(t));
		float cx = screen.width / 2f;
		float cy = screen.height / 2f;
		context.getMatrices().push();
		context.getMatrices().translate(cx, cy + lift, 0f);
		context.getMatrices().scale(scale, scale, 1f);
		context.getMatrices().translate(-cx, -cy, 0f);
		pushed = true;
	}

	public static void end(DrawContext context) {
		if (pushed) {
			context.getMatrices().pop();
			pushed = false;
		}
	}

	/** Сколько миллисекунд прошло с открытия текущего окна. */
	public static long sinceOpen(Screen screen) {
		return screen == current ? Util.getMeasuringTimeMs() - start : Long.MAX_VALUE;
	}

	private static float easeOut(double t) {
		double u = 1 - Math.max(0, Math.min(1, t));
		return (float) (1 - u * u * u);
	}

	private static float easeBack(double t) {
		double x = Math.max(0, Math.min(1, t));
		double c1 = 1.70158;
		double c3 = c1 + 1;
		return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
	}
}
