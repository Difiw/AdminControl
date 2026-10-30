package ru.admincontrol.util;

/** Формат денег: 34423545 → 34.423.545 */
public final class Money {
	private Money() {
	}

	public static String format(int value) {
		return format((long) value);
	}

	public static String format(long value) {
		boolean neg = value < 0;
		long n = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
		String raw = Long.toString(n);
		StringBuilder out = new StringBuilder(raw.length() + raw.length() / 3 + 1);
		int len = raw.length();
		for (int i = 0; i < len; i++) {
			if (i > 0 && (len - i) % 3 == 0) {
				out.append('.');
			}
			out.append(raw.charAt(i));
		}
		return neg ? "-" + out : out.toString();
	}

	public static String wc(int value) {
		return format(value) + " WC";
	}

	public static String wc(long value) {
		return format(value) + " WC";
	}
}
