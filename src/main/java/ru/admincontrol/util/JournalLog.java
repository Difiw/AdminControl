package ru.admincontrol.util;

import ru.admincontrol.data.DataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Журналы МП, тренировок и боксов: только дописываются, очищаются лишь командой. */
public final class JournalLog {
	public static final String EVENTS = "events";
	public static final String TRAINING = "training";
	public static final String BOX = "box";
	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final Object IO = new Object();

	private JournalLog() {
	}

	public static void add(String journal, String text) {
		Path file = file(journal);
		if (file == null || text == null || text.isBlank()) {
			return;
		}
		String line = "[" + FORMAT.format(Instant.now()) + "] " + text + System.lineSeparator();
		synchronized (IO) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				ActionLog.error("Не удалось записать журнал " + journal, e);
			}
		}
	}

	/** Новые записи сверху. */
	public static List<String> lines(String journal) {
		Path file = file(journal);
		List<String> out = new ArrayList<>();
		if (file == null || !Files.exists(file)) {
			return out;
		}
		synchronized (IO) {
			try {
				for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					if (!raw.isBlank()) {
						out.add(raw);
					}
				}
			} catch (IOException e) {
				ActionLog.error("Не удалось прочитать журнал " + journal, e);
			}
		}
		Collections.reverse(out);
		return out;
	}

	public static boolean clear(String journal) {
		Path file = file(journal);
		if (file == null) {
			return false;
		}
		synchronized (IO) {
			try {
				return Files.deleteIfExists(file);
			} catch (IOException e) {
				ActionLog.error("Не удалось очистить журнал " + journal, e);
				return false;
			}
		}
	}

	private static Path file(String journal) {
		DataManager data = DataManager.get();
		return data == null || journal == null ? null : data.getConfigDir().resolve("logs").resolve(journal + ".log");
	}
}
