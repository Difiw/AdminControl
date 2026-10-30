package ru.admincontrol.util;

import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Логи игроков, админов и кланов. Сами не удаляются — только /clearlog и /aclearlog. */
public final class ActivityLog {
	private static final long MAX_LOG_BYTES = 8L * 1024 * 1024;
	private static final int KEEP_LOG_BYTES = 4 * 1024 * 1024;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE);
	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
	private static final Object IO = new Object();
	public static final char OK = '\u001e';
	public static final String OK_MARK = OK + "+";
	public static final String FAIL_MARK = OK + "-";

	private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();

	private ActivityLog() {
	}

	private static final class Pending {
		final Path file;
		final String line;

		Pending(Path file, String line) {
			this.file = file;
			this.line = line;
		}
	}

	public static final class Line {
		public final String text;
		public final boolean ok;
		public final ActionKind kind;

		public Line(String text, boolean ok) {
			this(text, ok, detectKind(text));
		}

		public Line(String text, boolean ok, ActionKind kind) {
			this.text = text == null ? "" : text;
			this.ok = ok;
			this.kind = kind;
		}
	}

	public static void player(String nick, String text) {
		player(nick, text, true);
	}

	public static void player(String nick, String text, boolean ok) {
		write("plog_", nick, text, ok);
	}

	public static void admin(String nick, String text) {
		admin(nick, text, true);
	}

	public static void admin(String nick, String text, boolean ok) {
		write("alog_", nick, text, ok);
	}

	public static void clan(String name, String text, boolean ok) {
		write("clog_", name, text, ok);
	}

	public static List<Line> readPlayer(String nick) {
		return read("plog_", nick);
	}

	public static List<Line> readAdmin(String nick) {
		return read("alog_", nick);
	}

	public static List<Line> readClan(String name) {
		return read("clog_", name);
	}

	/** Общий лог всех игроков или всех администраторов, по времени. */
	public static List<Line> readAll(boolean admin) {
		DataManager data = DataManager.get();
		List<Line> out = new ArrayList<>();
		if (data == null) {
			return out;
		}
		flushAll();
		String prefix = admin ? "alog_" : "plog_";
		Path dir = data.getConfigDir().resolve("logs");
		List<Path> files = new ArrayList<>();
		synchronized (IO) {
			try {
				if (Files.isDirectory(dir)) {
					try (var stream = Files.list(dir)) {
						stream.filter(p -> {
							String name = p.getFileName().toString();
							return name.startsWith(prefix) && name.endsWith(".log");
						}).forEach(files::add);
					}
				}
				Path legacy = dir.resolve(admin ? "admins" : "players");
				if (Files.isDirectory(legacy)) {
					try (var stream = Files.list(legacy)) {
						stream.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(files::add);
					}
				}
				for (Path file : files) {
					for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
						if (!raw.isBlank()) {
							out.add(parse(raw));
						}
					}
				}
			} catch (IOException e) {
				ActionLog.error("Не удалось прочитать общий лог", e);
			}
		}
		out.sort(java.util.Comparator.comparing(line -> {
			LocalDateTime at = stamp(line);
			return at == null ? LocalDateTime.MIN : at;
		}));
		return out;
	}

	public static boolean clearPlayer(String nick) {
		return delete(false, nick);
	}

	public static boolean clearAdmin(String nick) {
		return delete(true, nick);
	}

	public static int clearAllPlayers() {
		return deleteAll(false);
	}

	public static int clearAllAdmins() {
		return deleteAll(true);
	}

	private static void write(String prefix, String nick, String text, boolean ok) {
		DataManager data = DataManager.get();
		if (data == null || nick == null || nick.isBlank() || text == null) {
			return;
		}
		Path file = file(data, prefix, nick);
		if (file == null) {
			return;
		}
		String line = "[" + FORMAT.format(Instant.now()) + "] <" + nick + "> " + text
				+ (ok ? OK_MARK : FAIL_MARK) + System.lineSeparator();
		QUEUE.add(new Pending(file, line));
	}

	public static void flush() {
		flush(64);
	}

	public static void flushAll() {
		flush(Integer.MAX_VALUE);
	}

	private static void flush(int max) {
		synchronized (IO) {
			int n = 0;
			Pending item = QUEUE.poll();
			while (item != null && n < max) {
				try {
					Files.createDirectories(item.file.getParent());
					Files.writeString(item.file, item.line, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					rotateIfNeeded(item.file);
				} catch (IOException e) {
					ActionLog.error("Не удалось записать лог " + item.file, e);
				}
				n++;
				item = QUEUE.poll();
			}
		}
	}

	private static void rotateIfNeeded(Path file) throws IOException {
		if (!Files.exists(file) || Files.size(file) <= MAX_LOG_BYTES) return;
		byte[] all = Files.readAllBytes(file);
		int start = Math.max(0, all.length - KEEP_LOG_BYTES);
		while (start < all.length && all[start] != '\n') start++;
		if (start < all.length) start++;
		byte[] tail = java.util.Arrays.copyOfRange(all, start, all.length);
		Files.write(file, tail, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	private static List<Line> read(String prefix, String nick) {
		DataManager data = DataManager.get();
		List<Line> empty = new ArrayList<>();
		if (data == null || nick == null) {
			return empty;
		}
		Path file = file(data, prefix, nick);
		if (file == null || !Files.exists(file)) {
			return empty;
		}
		flushAll();
		synchronized (IO) {
			try {
				List<Line> out = new ArrayList<>();
				for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					if (!raw.isBlank()) {
						out.add(parse(raw));
					}
				}
				return out;
			} catch (IOException e) {
				return empty;
			}
		}
	}

	public static Line parsePublic(String raw) {
		return parse(raw);
	}

	public static LocalDateTime stamp(Line line) {
		return line == null ? null : parseDateTime(line.text);
	}

	public static LocalDateTime parseDateTime(String raw) {
		if (raw == null) {
			return null;
		}
		int start = raw.indexOf('[');
		int end = raw.indexOf(']');
		if (start < 0 || end <= start + 1) {
			return null;
		}
		String stamp = raw.substring(start + 1, end).trim();
		try {
			return LocalDateTime.parse(stamp, STAMP);
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

	public enum TimeFilter {
		ALL("Все время"),
		HOUR("Час"),
		TODAY("Сегодня"),
		YESTERDAY("Вчера");

		public final String label;

		TimeFilter(String label) {
			this.label = label;
		}
	}

	public static boolean inTime(Line line, TimeFilter filter) {
		if (filter == null || filter == TimeFilter.ALL) {
			return true;
		}
		LocalDateTime at = stamp(line);
		if (at == null) {
			return true;
		}
		LocalDateTime now = LocalDateTime.now(ZONE);
		return switch (filter) {
			case HOUR -> !at.isBefore(now.minusHours(1));
			case TODAY -> at.toLocalDate().equals(now.toLocalDate());
			case YESTERDAY -> at.toLocalDate().equals(now.toLocalDate().minusDays(1));
			default -> true;
		};
	}

	public static boolean matches(Line line, String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		if (line == null) {
			return false;
		}
		String hay = (line.text == null ? "" : line.text);
		if (line.kind != null && line.kind.label != null) {
			hay = hay + " " + line.kind.label + " " + line.kind.name();
		}
		if (!line.ok) {
			hay = hay + " ошибка fail";
		}
		String low = hay.toLowerCase(Locale.ROOT);
		for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (token.isBlank()) {
				continue;
			}
			if (!low.contains(token)) {
				return false;
			}
		}
		return true;
	}

	public static List<Line> filter(List<Line> lines, String query) {
		if (lines == null || lines.isEmpty() || query == null || query.isBlank()) {
			return lines == null ? List.of() : lines;
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines) {
			if (matches(line, query)) {
				out.add(line);
			}
		}
		return out;
	}

	private static Line parse(String raw) {
		if (raw == null) {
			return new Line("", true);
		}
		boolean ok = true;
		String text = raw;
		if (text.endsWith(FAIL_MARK)) {
			ok = false;
			text = text.substring(0, text.length() - FAIL_MARK.length());
		} else if (text.endsWith(OK_MARK)) {
			text = text.substring(0, text.length() - OK_MARK.length());
		}
		return new Line(text, ok);
	}

	public static ActionKind detectKind(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String low = text.toLowerCase(Locale.ROOT);
		if (low.contains("anti-cheat:") || low.contains("античит:")) {
			return ActionKind.ANTICHEAT;
		}
		ActionKind best = null;
		int bestLen = -1;
		for (ActionKind kind : ActionKind.values()) {
			String needle = kind.label + ":";
			int at = text.indexOf(needle);
			if (at >= 0 && kind.label.length() > bestLen) {
				best = kind;
				bestLen = kind.label.length();
			}
		}
		return best;
	}

	private static boolean delete(boolean admin, String nick) {
		DataManager data = DataManager.get();
		if (data == null || nick == null || nick.isBlank()) {
			return false;
		}
		synchronized (IO) {
			Path current = file(data, admin ? "alog_" : "plog_", nick);
			boolean ok = false;
			try {
				if (current != null && Files.exists(current)) {
					Files.deleteIfExists(current);
					ok = true;
				}
				String safe = nick.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
				Path legacy = data.getConfigDir().resolve("logs").resolve(admin ? "admins" : "players").resolve(safe + ".log");
				if (Files.exists(legacy)) {
					Files.deleteIfExists(legacy);
					ok = true;
				}
			} catch (IOException e) {
				ActionLog.error("Не удалось удалить лог " + nick, e);
			}
			return ok;
		}
	}

	private static int deleteAll(boolean admin) {
		DataManager data = DataManager.get();
		if (data == null) {
			return 0;
		}
		synchronized (IO) {
			int n = 0;
			Path dir = data.getConfigDir().resolve("logs");
			String prefix = admin ? "alog_" : "plog_";
			try {
				if (Files.isDirectory(dir)) {
					try (var stream = Files.list(dir)) {
						for (Path path : stream.toList()) {
							String name = path.getFileName().toString();
							if (name.startsWith(prefix) && name.endsWith(".log")) {
								Files.deleteIfExists(path);
								n++;
							}
						}
					}
				}
				Path legacy = dir.resolve(admin ? "admins" : "players");
				if (Files.isDirectory(legacy)) {
					try (var stream = Files.list(legacy)) {
						for (Path path : stream.toList()) {
							if (path.getFileName().toString().endsWith(".log")) {
								Files.deleteIfExists(path);
								n++;
							}
						}
					}
				}
			} catch (IOException e) {
				ActionLog.error("Не удалось очистить логи", e);
			}
			return n;
		}
	}

	private static Path file(DataManager data, String prefix, String nick) {
		String safe = nick.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		String name = prefix + safe + ".log";
		Path dir = data.getConfigDir().resolve("logs");
		Path current = dir.resolve(name);
		if (Files.exists(current)) {
			return current;
		}
		boolean admin = "alog_".equals(prefix);
		Path legacy = dir.resolve(admin ? "admins" : "players").resolve(safe + ".log");
		return Files.exists(legacy) ? legacy : current;
	}
}
