package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Дата и время сервера из /ptime в правом верхнем углу — поверх игры и любого открытого интерфейса. */
public final class PtimeHud {
	private static final String[] WEEKDAYS = {"понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"};
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static long serverShift;
	private static ZoneOffset zone = ZoneOffset.UTC;
	private static long until;

	private PtimeHud() {
	}

	public static void show(long serverMillis, int offsetSeconds, int seconds) {
		long now = System.currentTimeMillis();
		serverShift = serverMillis - now;
		zone = ZoneOffset.ofTotalSeconds(Math.max(-18 * 3600, Math.min(18 * 3600, offsetSeconds)));
		until = now + Math.max(1, seconds) * 1000L;
	}

	public static void hide() {
		until = 0;
	}

	/** Из HUD — только когда интерфейс не открыт, иначе рисует экран после себя. */
	public static void renderHud(DrawContext context) {
		if (MinecraftClient.getInstance().currentScreen == null) {
			render(context);
		}
	}

	public static void render(DrawContext context) {
		long now = System.currentTimeMillis();
		if (now >= until) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;
		LocalDateTime at = LocalDateTime.ofEpochSecond(Math.floorDiv(now + serverShift, 1000L), 0, zone);
		String time = at.format(TIME);
		String date = at.format(DATE);
		String day = WEEKDAYS[at.getDayOfWeek().getValue() - 1];
		int timeW = font.getWidth(time) * 2;
		int w = Math.max(timeW, Math.max(font.getWidth(date), font.getWidth(day))) + 12;
		int h = 46;
		int x = client.getWindow().getScaledWidth() - w - 4;
		int y = 4;
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 500);
		context.fill(x, y, x + w, y + h, 0xD0101820);
		context.drawBorder(x, y, w, h, 0xFFE0B040);
		context.getMatrices().push();
		context.getMatrices().translate(x + (w - timeW) / 2f, y + 4, 0);
		context.getMatrices().scale(2f, 2f, 1f);
		context.drawText(font, time, 0, 0, 0xFFFFFFFF, true);
		context.getMatrices().pop();
		context.drawText(font, date, x + (w - font.getWidth(date)) / 2, y + 24, 0xFFFFD866, true);
		context.drawText(font, day, x + (w - font.getWidth(day)) / 2, y + 35, 0xFFC8D2E0, true);
		context.getMatrices().pop();
	}
}
