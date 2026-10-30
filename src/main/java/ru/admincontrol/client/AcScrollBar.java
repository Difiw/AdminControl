package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;

public final class AcScrollBar {
	public static final int WIDTH = 9;
	public boolean dragging;

	public void draw(DrawContext context, int x, int y, int h, int maxScroll, int visible, int scroll) {
		if (maxScroll <= 0 || h <= 8) {
			return;
		}
		context.fill(x, y, x + WIDTH, y + h, 0xCC000000);
		context.fill(x + 1, y + 1, x + WIDTH - 1, y + h - 1, 0x66000000);
		int total = maxScroll + visible;
		int thumb = Math.max(18, h * visible / Math.max(1, total));
		int room = Math.max(1, h - thumb);
		int ty = y + (int) (room * (scroll / (double) maxScroll));
		context.fill(x + 1, ty, x + WIDTH - 1, ty + thumb, 0xFF2A2A2A);
		context.fill(x + 2, ty + 1, x + WIDTH - 2, ty + thumb - 1, AcTheme.SCORE);
	}

	public int click(double mouseY, int y, int h, int maxScroll) {
		if (maxScroll <= 0) {
			return 0;
		}
		double t = (mouseY - y) / Math.max(1.0, h);
		return (int) Math.max(0, Math.min(maxScroll, Math.round(t * maxScroll)));
	}

	public boolean inTrack(double mx, double my, int x, int y, int h) {
		return mx >= x - 2 && mx < x + WIDTH + 3 && my >= y && my < y + h;
	}
}
