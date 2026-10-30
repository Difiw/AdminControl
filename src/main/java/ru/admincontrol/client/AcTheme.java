package ru.admincontrol.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

/** Цвета как у табло Common World: полупрозрачный фон, жёлтый заголовок, белые имена, жёлтые числа. */
public final class AcTheme {
	public static final int BG = 0xA0101010;
	public static final int BG_HEAD = 0x50000000;
	public static final int TITLE = 0xFFFF55;
	public static final int NAME = 0xFFFFFF;
	public static final int SCORE = 0xFFFF55;
	public static final int MUTED = 0xAAAAAA;
	public static final int RED = 0xFF5555;
	public static final int ROW = 0x50000000;
	public static final int GOLD = TITLE;
	public static final int TEXT = NAME;
	public static final int GREEN = SCORE;
	public static final int FRAME = 0x50000000;
	public static final int FOOTER = 0x50000000;

	private AcTheme() {
	}

	public static void panel(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h, 24);
	}

	public static void panel(DrawContext context, int x, int y, int w, int h, int headerH) {
		context.fill(x - 2, y - 2, x + w, y + h, ROW);
		context.fill(x, y, x + w - 2, y + h - 2, BG);
		if (headerH > 0) {
			context.fill(x, y, x + w - 2, y + headerH, BG_HEAD);
			shimmer(context, x, y, w - 2, headerH);
		}
	}

	/** Мягкий блик, время от времени пробегающий по шапке панели. */
	public static void shimmer(DrawContext context, int x, int y, int w, int h) {
		long period = 5200;
		double t = Math.floorMod(Util.getMeasuringTimeMs() + x * 7L + y * 13L, period) / (double) period;
		if (t > 0.45 || w <= 0) {
			return;
		}
		int cx = x - 30 + (int) ((w + 60) * (t / 0.45));
		for (int i = -14; i <= 14; i++) {
			int col = cx + i;
			if (col < x || col >= x + w) {
				continue;
			}
			int a = (int) (0x18 * (1 - Math.abs(i) / 15.0));
			context.fill(col, y, col + 1, y + h, (a << 24) | 0xFFFFFF);
		}
	}

	public static void footer(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y + h - 16, x + w - 2, y + h - 2, ROW);
	}

	public static void frameAround(DrawContext context, int x, int y, int w, int h) {
		context.fill(x - 2, y - 2, x + w, y + h, ROW);
	}

	public static void row(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + h, ROW);
	}

	public static void title(TextRenderer tr, DrawContext context, String text, int x, int y) {
		context.drawText(tr, text, x, y, TITLE, false);
	}

	public static void titleCentered(TextRenderer tr, DrawContext context, String text, int x, int y, int w) {
		clippedCentered(tr, context, text, x, y, w, TITLE);
	}

	public static String ellipsize(TextRenderer tr, String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (tr.getWidth(text) <= maxWidth) {
			return text;
		}
		String ell = "...";
		int ew = tr.getWidth(ell);
		if (maxWidth <= ew) {
			return ell;
		}
		int w = 0;
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			int cw = tr.getWidth(String.valueOf(text.charAt(i)));
			if (w + cw + ew > maxWidth) {
				break;
			}
			out.append(text.charAt(i));
			w += cw;
		}
		return out + ell;
	}

	public static void clipped(TextRenderer tr, DrawContext context, String text, int x, int y, int maxWidth, int color) {
		context.drawText(tr, ellipsize(tr, text, maxWidth), x, y, color, false);
	}

	public static void clippedCentered(TextRenderer tr, DrawContext context, String text, int x, int y, int w, int color) {
		String t = ellipsize(tr, text, w);
		context.drawText(tr, t, x + (w - tr.getWidth(t)) / 2, y, color, false);
	}

	public static int wrapped(TextRenderer tr, DrawContext context, String text, int x, int y, int maxWidth, int color) {
		List<OrderedText> lines = tr.wrapLines(Text.literal(text == null ? "" : text), Math.max(8, maxWidth));
		int yy = y;
		for (OrderedText line : lines) {
			context.drawText(tr, line, x, yy, color, false);
			yy += 10;
		}
		return yy;
	}

	public static void title(TextRenderer tr, DrawContext context, Text text, int x, int y) {
		context.drawText(tr, text, x, y, TITLE, false);
	}
}
