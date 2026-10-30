package ru.admincontrol.client;

/** Последняя позиция курсора в GUI — чтобы клик не уводил мышь в центр. */
public final class GuiCursor {
	public static double x;
	public static double y;
	public static boolean valid;

	private GuiCursor() {
	}

	public static void remember(double mx, double my) {
		x = mx;
		y = my;
		valid = true;
	}
}
