package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Дневная статистика администраторов для /admstats и /astats. */
public final class AdminStats {
	public static final long AFK_AFTER_MS = 90_000L;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<UUID, Motion> MOTION = new ConcurrentHashMap<>();
	private static final Map<UUID, OpenView> VIEWS = new ConcurrentHashMap<>();

	private static Store store = new Store();
	private static boolean dirty;

	private AdminStats() {
	}

	private static final class Store {
		Map<String, Admin> admins = new HashMap<>();
	}

	private static final class Admin {
		String nick = "";
		long since;
		long totalSeconds;
		long lastJoinAt;
		long lastLeaveAt;
		List<Session> sessions = new ArrayList<>();
		Map<String, Day> days = new HashMap<>();
	}

	/** Вход/выход конкретного администратора. */
	private static final class Session {
		long joinAt;
		long leaveAt;
	}

	private static final int MAX_SESSIONS = 400;

	private static final class Day {
		int bans;
		int warns;
		int kicks;
		int jails;
		int verbal;
		int events;
		int boxes;
		long online;
		long afk;
		List<LogView> logs = new ArrayList<>();
	}

	private static final class LogView {
		long at;
		String what = "";
		long seconds;
		boolean chat;
	}

	private static final class Motion {
		double x;
		double y;
		double z;
		long lastMove;
	}

	private record OpenView(String what, long at) {
	}

	public static void onRecord(ServerCommandSource source, String action) {
		if (source == null || action == null || !(source.getEntity() instanceof ServerPlayerEntity player)) {
			return;
		}
		Day day = today(player);
		if (day == null) {
			return;
		}
		switch (action) {
			case "BAN", "PERMABAN" -> day.bans++;
			case "WARN" -> day.warns++;
			case "KICK" -> day.kicks++;
			case "JAIL" -> day.jails++;
			case "VERBAL_WARN" -> day.verbal++;
			default -> {
				return;
			}
		}
		dirty = true;
	}

	public static void onEvent(ServerPlayerEntity host) {
		Day day = today(host);
		if (day != null) {
			day.events++;
			dirty = true;
		}
	}

	public static void onBox(ServerPlayerEntity host) {
		Day day = today(host);
		if (day != null) {
			day.boxes++;
			dirty = true;
		}
	}

	/** Лог открыт в интерфейсе: время считается до закрытия окна. */
	public static void logOpened(ServerPlayerEntity viewer, String what) {
		if (viewer == null || !isAdmin(viewer)) {
			return;
		}
		closeView(viewer);
		VIEWS.put(viewer.getUuid(), new OpenView(what == null ? "Логи" : what, System.currentTimeMillis()));
	}

	public static void logClosed(ServerPlayerEntity viewer) {
		closeView(viewer);
	}

	/** Лог выведен в чат или консоль: время просмотра не измеряется. */
	public static void logToChat(ServerCommandSource source, String what) {
		if (source == null || !(source.getEntity() instanceof ServerPlayerEntity viewer)) {
			return;
		}
		Day day = today(viewer);
		if (day == null) {
			return;
		}
		LogView view = new LogView();
		view.at = System.currentTimeMillis();
		view.what = what == null ? "Логи" : what;
		view.chat = true;
		day.logs.add(view);
		dirty = true;
	}

	private static void closeView(ServerPlayerEntity viewer) {
		if (viewer == null) {
			return;
		}
		OpenView open = VIEWS.remove(viewer.getUuid());
		if (open == null) {
			return;
		}
		Day day = today(viewer);
		if (day == null) {
			return;
		}
		LogView view = new LogView();
		view.at = open.at();
		view.what = open.what();
		view.seconds = Math.max(0L, (System.currentTimeMillis() - open.at()) / 1000L);
		day.logs.add(view);
		dirty = true;
	}

	public static void granted(String nick) {
		Admin admin = admin(nick);
		if (admin != null && admin.since <= 0L) {
			admin.since = System.currentTimeMillis();
			dirty = true;
		}
	}

	public static void revoked(String nick) {
		Admin admin = admin(nick);
		if (admin != null) {
			admin.since = 0L;
			dirty = true;
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		closeView(player);
		MOTION.remove(player.getUuid());
	}

	/** Вход админа: пишется только в статистику этого ника. */
	public static void recordJoin(ServerPlayerEntity player) {
		if (player == null || !isAdmin(player)) {
			return;
		}
		Admin admin = admin(player.getGameProfile().getName());
		if (admin == null) {
			return;
		}
		ensureSessions(admin);
		long now = System.currentTimeMillis();
		if (!admin.sessions.isEmpty()) {
			Session last = admin.sessions.get(admin.sessions.size() - 1);
			if (last != null && last.joinAt > 0L && last.leaveAt <= 0L) {
				last.leaveAt = Math.max(last.joinAt, now - 1000L);
			}
		}
		admin.lastJoinAt = now;
		Session session = new Session();
		session.joinAt = now;
		admin.sessions.add(session);
		trimSessions(admin);
		dirty = true;
	}

	/** Выход админа: закрывает сессию именно этого ника. */
	public static void recordLeave(ServerPlayerEntity player) {
		if (player == null || !isAdmin(player)) {
			return;
		}
		Admin admin = admin(player.getGameProfile().getName());
		if (admin == null) {
			return;
		}
		ensureSessions(admin);
		long now = System.currentTimeMillis();
		admin.lastLeaveAt = now;
		for (int i = admin.sessions.size() - 1; i >= 0; i--) {
			Session session = admin.sessions.get(i);
			if (session != null && session.joinAt > 0L && session.leaveAt <= 0L) {
				session.leaveAt = Math.max(session.joinAt, now);
				dirty = true;
				return;
			}
		}
		if (admin.lastJoinAt > 0L) {
			Session session = new Session();
			session.joinAt = admin.lastJoinAt;
			session.leaveAt = now;
			admin.sessions.add(session);
			trimSessions(admin);
			dirty = true;
		}
	}

	public static String lastJoinText(String nick) {
		Admin admin = find(nick);
		if (admin == null || admin.lastJoinAt <= 0L) {
			return "неизвестно";
		}
		return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE)
				.format(Instant.ofEpochMilli(admin.lastJoinAt));
	}

	public static String lastLeaveText(String nick) {
		Admin admin = find(nick);
		if (admin == null || admin.lastLeaveAt <= 0L) {
			return "неизвестно";
		}
		return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE)
				.format(Instant.ofEpochMilli(admin.lastLeaveAt));
	}

	/** Журнал входов/выходов выбранного админа (не зрителя). */
	public static List<String> sessionLines(String nick) {
		Admin admin = find(nick);
		List<String> out = new ArrayList<>();
		String name = admin == null || admin.nick == null || admin.nick.isBlank() ? nick : admin.nick;
		out.add("Входы и выходы администратора: " + name);
		if (admin == null) {
			out.add("Для этого админа ещё нет записей.");
			return out;
		}
		ensureSessions(admin);
		out.add("Всего записей: " + admin.sessions.size() + " (новые сверху)");
		out.add("");
		if (admin.sessions.isEmpty()) {
			out.add("Пока нет сохранённых заходов — история пишется с момента обновления мода.");
			if (admin.lastJoinAt > 0L) {
				out.add("Известен только последний вход: " + lastJoinText(nick));
			}
			return out;
		}
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE);
		for (int i = admin.sessions.size() - 1; i >= 0; i--) {
			Session session = admin.sessions.get(i);
			if (session == null || session.joinAt <= 0L) {
				continue;
			}
			out.add("Вход  " + fmt.format(Instant.ofEpochMilli(session.joinAt)));
			if (session.leaveAt > 0L) {
				out.add("Выход " + fmt.format(Instant.ofEpochMilli(session.leaveAt))
						+ "  (" + duration((session.leaveAt - session.joinAt) / 1000L) + ")");
			} else {
				out.add("Выход — ещё онлайн");
			}
			out.add("");
		}
		return out;
	}

	private static void ensureSessions(Admin admin) {
		if (admin.sessions == null) {
			admin.sessions = new ArrayList<>();
		}
	}

	private static void trimSessions(Admin admin) {
		ensureSessions(admin);
		while (admin.sessions.size() > MAX_SESSIONS) {
			admin.sessions.remove(0);
		}
	}

	/** Раз в секунду: онлайн и АФК. Активность — только перемещение вне интерфейсов. */
	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player) || !isAdmin(player)) {
				continue;
			}
			Motion m = MOTION.get(player.getUuid());
			boolean inScreen = player.currentScreenHandler != null && player.currentScreenHandler != player.playerScreenHandler;
			if (m == null) {
				m = new Motion();
				m.x = player.getX();
				m.y = player.getY();
				m.z = player.getZ();
				m.lastMove = now;
				MOTION.put(player.getUuid(), m);
			} else {
				double dx = player.getX() - m.x;
				double dy = player.getY() - m.y;
				double dz = player.getZ() - m.z;
				m.x = player.getX();
				m.y = player.getY();
				m.z = player.getZ();
				if (!inScreen && dx * dx + dy * dy + dz * dz > 0.0025) {
					m.lastMove = now;
				}
			}
			Day day = today(player);
			Admin admin = admin(player.getGameProfile().getName());
			if (day == null || admin == null) {
				continue;
			}
			day.online++;
			admin.totalSeconds++;
			if (now - m.lastMove > AFK_AFTER_MS) {
				day.afk++;
			}
			dirty = true;
		}
		if (dirty && server.getTicks() % 1200 < 20) {
			save();
		}
	}

	/** Дни от выдачи админки (или первой записи) до сегодня, новые сверху. */
	public static List<LocalDate> days(String nick) {
		List<LocalDate> out = new ArrayList<>();
		LocalDate today = LocalDate.now(ZONE);
		Admin admin = find(nick);
		LocalDate from = today;
		if (admin != null) {
			if (admin.since > 0L) {
				from = Instant.ofEpochMilli(admin.since).atZone(ZONE).toLocalDate();
			}
			for (String key : admin.days.keySet()) {
				try {
					LocalDate d = LocalDate.parse(key);
					if (d.isBefore(from)) {
						from = d;
					}
				} catch (Exception ignored) {
				}
			}
		}
		for (LocalDate d = today; !d.isBefore(from); d = d.minusDays(1)) {
			out.add(d);
		}
		return out;
	}

	public static String dayLabel(String nick, LocalDate date) {
		Admin admin = find(nick);
		Day day = admin == null ? null : admin.days.get(date.toString());
		String label = SHOW.format(date);
		if (day == null) {
			return label + "  ·  нет активности";
		}
		return label + "  ·  онлайн " + duration(day.online) + "  ·  наказаний "
				+ (day.bans + day.warns + day.kicks + day.jails + day.verbal);
	}

	public static List<String> dayLines(String nick, LocalDate date) {
		Admin admin = find(nick);
		Day day = admin == null ? null : admin.days.get(date.toString());
		if (day == null) {
			day = new Day();
		}
		List<String> out = new ArrayList<>();
		out.add("Статистика " + (admin == null || admin.nick.isBlank() ? nick : admin.nick) + " за " + SHOW.format(date));
		out.add("");
		out.add("Баны: " + day.bans);
		out.add("Варны: " + day.warns);
		out.add("Кики: " + day.kicks);
		out.add("Jail: " + day.jails);
		out.add("Устные предупреждения: " + day.verbal);
		out.add("");
		out.add("Евенты: " + day.events);
		out.add("Box: " + day.boxes);
		out.add("");
		out.add("Всего на сервере провёл времени: " + duration(admin == null ? 0L : admin.totalSeconds));
		out.add("За этот день: " + duration(day.online));
		out.add("АФК за этот день: " + duration(day.afk) + " (без перемещения больше 90 сек)");
		out.add("");
		long logSeconds = 0L;
		for (LogView v : day.logs) {
			logSeconds += v.seconds;
		}
		out.add("Проверка логов: " + day.logs.size() + " раз, всего " + duration(logSeconds));
		if (day.logs.isEmpty()) {
			out.add("  логи не открывались");
		}
		for (LogView v : day.logs) {
			out.add("  " + TIME.format(Instant.ofEpochMilli(v.at)) + "  " + v.what + "  —  "
					+ (v.chat ? "вывод в чат" : duration(v.seconds)));
		}
		return out;
	}

	private static String duration(long seconds) {
		long s = Math.max(0L, seconds);
		long h = s / 3600;
		long m = s % 3600 / 60;
		long sec = s % 60;
		if (h > 0) {
			return h + " ч " + m + " мин";
		}
		if (m > 0) {
			return m + " мин " + sec + " сек";
		}
		return sec + " сек";
	}

	private static boolean isAdmin(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		return data != null && player != null && data.isAdmin(player);
	}

	private static Day today(ServerPlayerEntity player) {
		if (!isAdmin(player)) {
			return null;
		}
		Admin admin = admin(player.getGameProfile().getName());
		return admin == null ? null : admin.days.computeIfAbsent(LocalDate.now(ZONE).toString(), k -> new Day());
	}

	private static Admin admin(String nick) {
		if (nick == null || nick.isBlank()) {
			return null;
		}
		Admin admin = store.admins.computeIfAbsent(nick.toLowerCase(Locale.ROOT), k -> new Admin());
		admin.nick = nick;
		return admin;
	}

	public static boolean known(String nick) {
		return find(nick) != null;
	}

	private static Admin find(String nick) {
		return nick == null ? null : store.admins.get(nick.toLowerCase(Locale.ROOT));
	}

	private static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("admin_stats.json");
	}

	public static void load() {
		store = new Store();
		MOTION.clear();
		VIEWS.clear();
		dirty = false;
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Store loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
			if (loaded != null && loaded.admins != null) {
				store = loaded;
			}
		} catch (Exception e) {
			ActionLog.error("Не удалось прочитать admin_stats.json", e);
		}
	}

	public static void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("admin_stats.json.tmp");
			Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить admin_stats.json", e);
		}
	}
}
