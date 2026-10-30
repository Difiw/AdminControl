package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Боковое табло в стиле серверного scoreboard: тёмный фон, золотой заголовок, тень текста. */
public final class BoardHud {
	private static final int OUTER = 0x4A000000;
	private static final int INNER = 0x3A14141C;
	private static final int HEADER = 0x421C1810;
	private static final int LINE = 0x48E8B86D;
	private static final int TITLE = 0xFFD76A;
	private static final int LABEL_BAL = 0x7FE3FF;
	private static final int LABEL_LUCK = 0xE39CFF;
	private static final int LABEL_POW = 0xFFAA55;
	private static final int LABEL_CLAN = 0x7CFF9A;
	private static final int VALUE = 0xFFE27A;

	private static boolean visible;
	private static int coins;
	private static double power = 1.0;
	private static double luck;
	private static String clan = "нет";
	private static boolean titled;
	/** Сюжетная панель «Лиры» / «Мирты»: заменяет обычные строки, пока игрок в сюжетном мире. */
	private static boolean story;
	private static long lyres;
	private static long myrtles;

	private BoardHud() {
	}

	public static void updateStory(boolean show, long lyresValue, long myrtlesValue) {
		story = true;
		visible = show;
		lyres = Math.max(0, lyresValue);
		myrtles = Math.max(0, myrtlesValue);
	}

	public static void leaveStory() {
		story = false;
	}

	public static void setTitled(boolean on) {
		titled = on;
	}

	public static void update(boolean show, int coinValue, double powerValue, double luckValue, String clanName) {
		visible = show;
		coins = Math.max(0, coinValue);
		power = powerValue;
		luck = luckValue;
		clan = clanName == null || clanName.isBlank() ? "нет" : clanName;
	}

	public static void update(boolean show, int coinValue, double powerValue, double luckValue) {
		update(show, coinValue, powerValue, luckValue, clan);
	}

	public static void hide() {
		visible = false;
		story = false;
	}

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!visible || client.player == null || client.options.hudHidden || SpectateHud.spectating()) {
			return;
		}
		TextRenderer tr = client.textRenderer;
		int lineH = tr.fontHeight + 3;
		String title = titled ? "Common World" : "";
		String clanValue = AcTheme.ellipsize(tr, clan, 72);
		Row[] rows = story ? new Row[]{
				new Row("Лиры", ru.admincontrol.util.Money.format(lyres), LABEL_BAL),
				new Row("Мирты", ru.admincontrol.util.Money.format(myrtles), LABEL_CLAN)
		} : new Row[]{
				new Row("Баланс", ru.admincontrol.util.Money.format(coins), LABEL_BAL),
				new Row("Удача", String.format(Locale.US, "%.1f", luck), LABEL_LUCK),
				new Row("Сила", String.format(Locale.US, "%.1f", power), LABEL_POW),
				new Row("Клан", clanValue, LABEL_CLAN)
		};
		int gap = 14;
		int contentW = tr.getWidth(title);
		for (Row row : rows) {
			contentW = Math.max(contentW, tr.getWidth(row.label) + gap + tr.getWidth(row.value));
		}
		int padX = 6;
		int w = contentW + padX * 2;
		int headerH = titled ? tr.fontHeight + 6 : 0;
		int bodyH = lineH * rows.length + 4;
		int h = headerH + 3 + bodyH;
		int x = client.getWindow().getScaledWidth() - w - 6;
		int y = client.getWindow().getScaledHeight() / 2 - h / 2;
		context.fill(x - 2, y - 2, x + w + 2, y + h + 2, OUTER);
		context.fill(x, y, x + w, y + h, INNER);
		if (titled) {
			context.fill(x, y, x + w, y + headerH, HEADER);
			context.fill(x + 4, y + headerH - 1, x + w - 4, y + headerH, LINE);
			context.drawTextWithShadow(tr, title, x + (w - tr.getWidth(title)) / 2, y + 4, TITLE);
		}
		int rowY = y + headerH + 3;
		for (int i = 0; i < rows.length; i++) {
			Row row = rows[i];
			if ((i & 1) == 1) {
				context.fill(x + 1, rowY - 1, x + w - 1, rowY + lineH - 1, 0x14000000);
			}
			context.drawTextWithShadow(tr, row.label, x + padX, rowY, row.labelColor);
			context.drawTextWithShadow(tr, row.value, x + w - padX - tr.getWidth(row.value), rowY, VALUE);
			rowY += lineH;
		}
	}

	private static final class Row {
		final String label;
		final String value;
		final int labelColor;

		Row(String label, String value, int labelColor) {
			this.label = label;
			this.value = value;
			this.labelColor = labelColor;
		}
	}
}
