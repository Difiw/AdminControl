package ru.admincontrol.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Сроки наказаний: «1 нед. 3 дн.», «5 ч 20 мин», дата окончания. */
public final class Durations {
	private static final DateTimeFormatter UNTIL =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	private Durations() {
	}

	/** Две крупнейшие ненулевые единицы: недели, дни, часы, минуты (округление вниз до минуты). */
	public static String human(long millis) {
		long minutes = Math.max(0L, millis) / 60_000L;
		if (minutes <= 0) {
			return "меньше минуты";
		}
		long weeks = minutes / (7 * 24 * 60);
		minutes -= weeks * 7 * 24 * 60;
		long days = minutes / (24 * 60);
		minutes -= days * 24 * 60;
		long hours = minutes / 60;
		minutes -= hours * 60;
		long[] values = {weeks, days, hours, minutes};
		String[] units = {"нед.", "дн.", "ч", "мин"};
		StringBuilder out = new StringBuilder();
		int shown = 0;
		for (int i = 0; i < values.length && shown < 2; i++) {
			if (values[i] > 0) {
				if (out.length() > 0) {
					out.append(' ');
				}
				out.append(values[i]).append(' ').append(units[i]);
				shown++;
			} else if (shown > 0) {
				break;
			}
		}
		return out.toString();
	}

	public static String until(long epochMillis) {
		return UNTIL.format(Instant.ofEpochMilli(epochMillis));
	}
}
