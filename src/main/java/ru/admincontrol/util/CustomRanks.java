package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.command.CommandSync;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.CustomRank;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Уровни администрации из /aroles. Уровень доступа вводится как у штатных рангов («2», «2.5»), а внутренний уровень
 * выбирается свободный между штатными: «2» — сразу над вторым, «2.5» — посередине между вторым и третьим.
 * Поэтому все проверки вида «уровень не ниже X» работают для новой роли как для ближайшего штатного уровня снизу,
 * а команды берутся из её собственного списка (его видно и можно менять в /aranks).
 */
public final class CustomRanks {
	public static final String DEFAULT_FORMAT = "[{роль}] <{ник}> {сообщение}";
	public static final int DEFAULT_COLOR = 0x55FFFF;
	public static final int MAX_TITLE = 32;
	public static final int MAX_ROLE = 24;
	public static final int MAX_FORMAT = 64;
	/** Внутренний уровень штатного ранга по номеру 1…9 (индекс 0 не используется). */
	private static final int[] DISPLAY_LEVELS = {
			0, AdminRanks.MOD, AdminRanks.SMOD, AdminRanks.ADMIN, AdminRanks.SENIOR,
			AdminRanks.CURATOR, AdminRanks.ZGA, AdminRanks.TECH, AdminRanks.GA, AdminRanks.DEV
	};
	/** Занятые штатными рангами внутренние уровни (35 — старый 3.5, переносится в старшего администратора). */
	private static final int[] RESERVED = {
			AdminRanks.MOD, AdminRanks.SMOD, AdminRanks.ADMIN, AdminRanks.SENIOR, 35, AdminRanks.BLOGGER,
			AdminRanks.CURATOR, AdminRanks.ZGA, AdminRanks.TECH, AdminRanks.GA, AdminRanks.DEV
	};

	private CustomRanks() {
	}

	public static List<CustomRank> list() {
		DataManager data = DataManager.get();
		if (data == null || data.settings() == null) {
			return List.of();
		}
		ModSettings s = data.settings();
		if (s.customRanks == null) {
			s.customRanks = new ArrayList<>();
		}
		return s.customRanks;
	}

	public static List<CustomRank> sorted() {
		List<CustomRank> out = new ArrayList<>(list());
		out.sort((a, b) -> Integer.compare(a.level, b.level));
		return out;
	}

	public static CustomRank byLevel(int level) {
		if (isReserved(level)) {
			return null;
		}
		for (CustomRank r : list()) {
			if (r != null && r.level == level) {
				return r;
			}
		}
		return null;
	}

	/** Роль по ключу («r1») или уровню доступа («2.5»). */
	public static CustomRank byKey(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String want = raw.trim().replace(',', '.').toLowerCase(Locale.ROOT);
		for (CustomRank r : list()) {
			if (r != null && (want.equals(r.key) || want.equals(r.access))) {
				return r;
			}
		}
		return null;
	}

	public static boolean isReserved(int level) {
		for (int value : RESERVED) {
			if (value == level) {
				return true;
			}
		}
		return false;
	}

	/** «2», «2.5», «2,5» → десятые доли (25); null — не уровень от 1 до 8.9 с одной цифрой после точки. */
	public static Integer parseAccess(String raw) {
		if (raw == null) {
			return null;
		}
		String v = raw.trim().replace(',', '.');
		if (!v.matches("[1-8](\\.[0-9])?")) {
			return null;
		}
		int whole = v.charAt(0) - '0';
		int tenth = v.length() == 3 ? v.charAt(2) - '0' : 0;
		return whole * 10 + tenth;
	}

	public static String formatAccess(int tenths) {
		return tenths % 10 == 0 ? Integer.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
	}

	/** Номер штатного уровня для внутреннего (1…9); для ранга между уровнями — нижний. */
	public static int floorDisplay(int level) {
		int best = 0;
		for (int i = 1; i < DISPLAY_LEVELS.length; i++) {
			if (DISPLAY_LEVELS[i] <= level) {
				best = i;
			}
		}
		return best;
	}

	public static int standardLevel(int display) {
		return display >= 1 && display < DISPLAY_LEVELS.length ? DISPLAY_LEVELS[display] : AdminRanks.NONE;
	}

	/** Свободный внутренний уровень для уровня доступа; -1 — между штатными уровнями нет места. */
	public static int allocate(int tenths, CustomRank self) {
		int whole = tenths / 10;
		int tenth = tenths % 10;
		int lower = DISPLAY_LEVELS[whole];
		int upper = DISPLAY_LEVELS[whole + 1];
		int target = tenth == 0 ? lower + 1 : lower + Math.round((upper - lower) * tenth / 10f);
		target = Math.max(lower + 1, Math.min(upper - 1, target));
		for (int step = 0; step < upper - lower; step++) {
			for (int candidate : new int[] {target + step, target - step}) {
				if (candidate > lower && candidate < upper && free(candidate, self)) {
					return candidate;
				}
			}
		}
		return -1;
	}

	private static boolean free(int level, CustomRank self) {
		if (isReserved(level)) {
			return false;
		}
		for (CustomRank r : list()) {
			if (r != null && r != self && r.level == level) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Стартовый набор команд. Целый уровень — команды этого штатного уровня (с учётом настроек /aranks).
	 * Промежуточный — команды нижнего уровня плюс команды верхнего, кроме наказаний, управления админами,
	 * настроек сервера и логов: так же устроен штатный промежуточный уровень 4.5 (блогер).
	 */
	public static List<String> autoCommands(int tenths) {
		int whole = tenths / 10;
		int lower = DISPLAY_LEVELS[whole];
		int upper = DISPLAY_LEVELS[whole + 1];
		boolean between = tenths % 10 != 0;
		List<String> out = new ArrayList<>();
		for (RankPerms.Item item : RankPerms.COMMANDS) {
			boolean base = RankPerms.allowed(lower, item.id);
			boolean extra = between && !base && RankPerms.allowed(upper, item.id) && !RankPerms.sensitive(item.id);
			if (base || extra) {
				out.add(item.id);
			}
		}
		return out;
	}

	public static int color(int level, int fallback) {
		CustomRank r = byLevel(level);
		if (r == null) {
			return fallback;
		}
		int rgb = r.color & 0xFFFFFF;
		return rgb == 0 ? DEFAULT_COLOR : rgb;
	}

	/** Строка чата по формату роли: текст до и после {сообщение} — цветом роли, само сообщение — белым. */
	public static Text chatLine(CustomRank r, String nick, String message) {
		String role = r.role == null || r.role.isBlank() ? r.title : r.role;
		return chatLine(r.format, role, color(r.level, DEFAULT_COLOR), nick, message);
	}

	public static Text chatLine(String rawFormat, String role, int rgb, String nick, String message) {
		String format = rawFormat == null || !rawFormat.contains("{сообщение}") ? DEFAULT_FORMAT : rawFormat;
		String filled = format.replace("{роль}", role == null ? "" : role).replace("{ник}", nick == null ? "" : nick);
		int at = filled.indexOf("{сообщение}");
		String before = colorCodes(filled.substring(0, at));
		String after = colorCodes(filled.substring(at + "{сообщение}".length()));
		MutableText line = Text.empty();
		line.append(Chat.color(before, rgb));
		line.append(Text.literal(message == null ? "" : message).formatted(Formatting.WHITE));
		if (!after.isEmpty()) {
			line.append(Chat.color(after, rgb));
		}
		return line;
	}

	private static String colorCodes(String text) {
		return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00a7$1");
	}

	public static String nextKey() {
		int max = 0;
		for (CustomRank r : list()) {
			if (r != null && r.key != null && r.key.matches("r\\d+")) {
				max = Math.max(max, Integer.parseInt(r.key.substring(1)));
			}
		}
		return "r" + (max + 1);
	}

	/** Сколько десятых уровня может выдавать редактор: ГА — до 7.9, разработчик — до 8.9. */
	public static int maxTenths(int editorRank) {
		return editorRank >= AdminRanks.DEV ? 89 : editorRank >= AdminRanks.GA ? 79 : 0;
	}

	public static boolean canEdit(int editorRank, CustomRank r) {
		if (r == null || editorRank < AdminRanks.GA) {
			return false;
		}
		/** Разработчик может менять и удалять любой созданный уровень. */
		if (editorRank >= AdminRanks.DEV) {
			return true;
		}
		return r.level < editorRank;
	}

	/** Создать (key пустой) или изменить роль. Возвращает текст ошибки или null. */
	public static String save(ServerPlayerEntity editor, String key, String title, String role, String format,
			String colorHex, String accessRaw) {
		DataManager data = DataManager.get();
		if (data == null) {
			return "Admin Control не загружен.";
		}
		int editorRank = data.getAdminLevel(editor);
		if (editorRank < AdminRanks.GA) {
			return "Создавать уровни могут только ГА и разработчик.";
		}
		CustomRank existing = key == null || key.isBlank() ? null : byKey(key);
		if (key != null && !key.isBlank() && existing == null) {
			return "Роль не найдена — возможно, её уже удалили.";
		}
		if (existing != null && !canEdit(editorRank, existing)) {
			return "Эту роль может менять только тот, кто выше её по уровню.";
		}
		String t = title == null ? "" : title.trim();
		String rl = role == null ? "" : role.trim();
		String f = format == null || format.isBlank() ? DEFAULT_FORMAT : format.trim();
		if (t.isEmpty() || t.length() > MAX_TITLE) {
			return "Название уровня: от 1 до " + MAX_TITLE + " символов.";
		}
		if (rl.isEmpty() || rl.length() > MAX_ROLE) {
			return "Название роли: от 1 до " + MAX_ROLE + " символов.";
		}
		if (f.length() > MAX_FORMAT || !f.contains("{сообщение}")) {
			return "Формат сообщения должен содержать {сообщение} (до " + MAX_FORMAT + " символов).";
		}
		Integer rgb = NickColors.parse(colorHex);
		if (rgb == null) {
			return "Цвет: шесть шестнадцатеричных цифр, например #55ffff.";
		}
		Integer tenths = parseAccess(accessRaw);
		if (tenths == null) {
			return "Уровень доступа: число от 1 до 8.9, например 2 или 2.5.";
		}
		if (tenths > maxTenths(editorRank)) {
			return "Вы можете создавать уровни только до " + formatAccess(maxTenths(editorRank)) + ".";
		}
		String access = formatAccess(tenths);
		for (CustomRank other : list()) {
			if (other != existing && access.equals(other.access)) {
				return "Уровень доступа " + access + " уже занят ролью «" + other.title + "».";
			}
		}
		int level = existing != null && access.equals(existing.access) ? existing.level : allocate(tenths, existing);
		if (level < 0) {
			return "Между уровнями " + (tenths / 10) + " и " + (tenths / 10 + 1) + " больше нет свободного места.";
		}
		for (CustomRank other : list()) {
			if (other != existing && other.title != null && other.title.equalsIgnoreCase(t)) {
				return "Уровень с названием «" + t + "» уже есть.";
			}
		}
		CustomRank r = existing;
		boolean created = r == null;
		if (created) {
			r = new CustomRank();
			r.key = nextKey();
			r.commands = new ArrayList<>(autoCommands(tenths));
			list().add(r);
		} else if (r.level != level) {
			moveHolders(r.level, level);
		}
		r.level = level;
		r.access = access;
		r.title = t;
		r.role = rl;
		r.format = f;
		r.color = rgb;
		data.saveAll();
		refresh(editor.getServer());
		AdminNotify.record(editor.getCommandSource(), created ? "AROLES CREATE" : "AROLES EDIT", r.key,
				access + " " + t + " [" + rl + "] " + NickColors.hex(rgb));
		return null;
	}

	/** Заново подобрать команды роли по её уровню доступа. */
	public static String resetCommands(ServerPlayerEntity editor, String key) {
		DataManager data = DataManager.get();
		CustomRank r = byKey(key);
		if (data == null || r == null) {
			return "Роль не найдена.";
		}
		if (!canEdit(data.getAdminLevel(editor), r)) {
			return "Эту роль может менять только тот, кто выше её по уровню.";
		}
		Integer tenths = parseAccess(r.access);
		if (tenths == null) {
			return "У роли неверный уровень доступа.";
		}
		r.commands = new ArrayList<>(autoCommands(tenths));
		r.punishStaff = null;
		data.saveAll();
		refresh(editor.getServer());
		AdminNotify.record(editor.getCommandSource(), "AROLES RESET", r.key, r.access + " " + r.title);
		return null;
	}

	/** Удалить роль; её администраторы переходят на штатный уровень под ней. */
	public static String delete(ServerPlayerEntity editor, String key) {
		DataManager data = DataManager.get();
		CustomRank r = byKey(key);
		if (data == null || r == null) {
			return "Роль не найдена.";
		}
		if (!canEdit(data.getAdminLevel(editor), r)) {
			return "Эту роль может удалить только тот, кто выше её по уровню.";
		}
		int fallback = standardLevel(floorDisplay(r.level));
		moveHolders(r.level, fallback);
		list().remove(r);
		data.saveAll();
		refresh(editor.getServer());
		AdminNotify.record(editor.getCommandSource(), "AROLES DELETE", r.key,
				r.access + " " + r.title + " → " + AdminRanks.title(fallback));
		return null;
	}

	/** Сколько администраторов сейчас на этом уровне. */
	public static int holders(int level) {
		DataManager data = DataManager.get();
		if (data == null) {
			return 0;
		}
		return Math.max(count(data.settings().adminLevelsByNick, level), count(data.settings().adminLevelsByUuid, level));
	}

	private static int count(Map<String, Integer> map, int level) {
		int count = 0;
		if (map != null) {
			for (Integer value : map.values()) {
				if (value != null && value == level) {
					count++;
				}
			}
		}
		return count;
	}

	private static void moveHolders(int from, int to) {
		ModSettings s = DataManager.get().settings();
		s.adminLevelsByUuid.replaceAll((k, v) -> v != null && v == from ? to : v);
		s.adminLevelsByNick.replaceAll((k, v) -> v != null && v == from ? to : v);
		moveKey(s.rankBanDays, from, to);
		moveKey(s.rankJailMinutes, from, to);
	}

	private static void moveKey(Map<String, Integer> map, int from, int to) {
		if (map == null) {
			return;
		}
		Integer value = map.remove(String.valueOf(from));
		if (value != null && !isReserved(to)) {
			map.put(String.valueOf(to), value);
		}
	}

	private static void refresh(MinecraftServer server) {
		if (server != null) {
			CommandSync.refreshAll(server);
			TabListColors.refresh(server);
		}
	}

	/** Для подсказок /add_admin и /set_admin: ключи ролей, которые этот ранг может выдавать. */
	public static List<String> assignableKeys(int sourceRank) {
		List<String> out = new ArrayList<>();
		for (CustomRank r : sorted()) {
			if (r.level < sourceRank) {
				out.add(r.key);
				if (parseAccess(r.access) != null && AdminRanks.parseStandard(r.access) == null) {
					out.add(r.access);
				}
			}
		}
		return out;
	}
}
