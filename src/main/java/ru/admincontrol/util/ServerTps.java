package ru.admincontrol.util;

public final class ServerTps {
	private static long lastNanos = System.nanoTime();
	private static double tps = 20.0;
	private static double mspt = 50.0;

	private ServerTps() {
	}

	public static void onServerTick() {
		long now = System.nanoTime();
		double ms = (now - lastNanos) / 1_000_000.0;
		lastNanos = now;
		if (ms < 0.01) {
			ms = 0.01;
		}
		mspt = ms;
		tps = Math.min(20.0, 1000.0 / ms);
	}

	public static double tps() {
		return tps;
	}

	public static double mspt() {
		return mspt;
	}

	public static String formatted() {
		return String.format(java.util.Locale.US, "%.1f (MSPT %.1f)", tps, mspt);
	}
}
