package ru.admincontrol.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Файл logs/admin_control.log и копия в config/admin_control/logs/.
 */
public final class ActionLog {
	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
	private static Path gameLog;
	private static Path configLog;

	private ActionLog() {
	}

	public static void init(Path gameLogsDir, Path configLogsDir) {
		try {
			Files.createDirectories(gameLogsDir);
			Files.createDirectories(configLogsDir);
			gameLog = gameLogsDir.resolve("admin_control.log");
			configLog = configLogsDir.resolve("admin_control.log");
		} catch (IOException e) {
			System.err.println("[AdminControl] Не удалось инициализировать лог: " + e.getMessage());
		}
	}

	public static void close() {
		gameLog = null;
		configLog = null;
	}

	public static void action(String action, String adminNick, String targetNick, String reason) {
		String line = "[" + FORMAT.format(Instant.now()) + "] [" + action + "] Admin:" + nullToDash(adminNick)
				+ " Target:" + nullToDash(targetNick) + " Reason:" + nullToDash(reason);
		write(line);
	}

	public static void info(String message) {
		write("[" + FORMAT.format(Instant.now()) + "] [INFO] " + message);
	}

	public static void error(String message, Throwable throwable) {
		write("[" + FORMAT.format(Instant.now()) + "] [ERROR] " + message + " :: " + throwable.getMessage());
		if (throwable.getMessage() == null) {
			write("[" + FORMAT.format(Instant.now()) + "] [ERROR] " + throwable.getClass().getName());
		}
	}

	private static void write(String line) {
		append(gameLog, line);
		append(configLog, line);
		System.out.println("[AdminControl] " + line);
	}

	private static void append(Path path, String line) {
		if (path == null) {
			return;
		}
		try {
			Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}
}
