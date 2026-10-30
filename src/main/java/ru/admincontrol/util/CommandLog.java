package ru.admincontrol.util;

/**
 * Чтобы одно выполнение команды не писалось в лог дважды.
 */
public final class CommandLog {
	private static final ThreadLocal<Boolean> LOGGED = ThreadLocal.withInitial(() -> false);

	private CommandLog() {
	}

	public static void mark() {
		LOGGED.set(true);
	}

	public static boolean take() {
		boolean value = Boolean.TRUE.equals(LOGGED.get());
		LOGGED.set(false);
		return value;
	}
}
