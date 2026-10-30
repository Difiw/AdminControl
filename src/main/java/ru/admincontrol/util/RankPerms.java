package ru.admincontrol.util;

import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RankPerms {
	public static final int[] LEVELS = {
			AdminRanks.MOD, AdminRanks.SMOD, AdminRanks.ADMIN, AdminRanks.SENIOR, AdminRanks.BLOGGER,
			AdminRanks.CURATOR, AdminRanks.ZGA, AdminRanks.TECH, AdminRanks.GA, AdminRanks.DEV
	};

	public static final List<Item> COMMANDS = List.of(
			item("g", "ТП к игроку"),
			item("gh", "ТП игрока к себе"),
			item("sg", "Тихий ТП к игроку"),
			item("sgh", "Тихий ТП игрока к себе"),
			item("sp", "Слежка"),
			item("spoff", "Выйти из слежки"),
			item("banp", "Временный бан"),
			item("unbanp", "Снять бан"),
			item("warnp", "Варн"),
			item("unwarnp", "Снять варн"),
			item("jailp", "Тюрьма"),
			item("unjailp", "Снять jail"),
			item("akickp", "Кик"),
			item("akill", "Убить игрока"),
			item("spawn", "На спавн игрока"),
			item("get", "Статистика"),
			item("conget", "Статистика в чат"),
			item("getach", "Достижения"),
			item("congetach", "Достижения в чат"),
			item("aplayer", "Панель игрока"),
			item("amsg", "Личное сообщение"),
			item("aeat", "Сытость"),
			item("adel", "Забрать предметы"),
			item("nmode", "Читы игроку"),
			item("getinv", "Инвентарь"),
			item("congetinv", "Инвентарь в чат"),
			item("punish", "История наказаний"),
			item("conpunish", "Наказания в чат"),
			item("ahp", "Здоровье"),
			item("afill", "Заполнить область"),
			item("pmode", "Читы игроков"),
			item("amode", "Читы администрации"),
			item("acommand", "Запрет команды"),
			item("chcommands", "Выключенные команды"),
			item("conchcommands", "Выключенные команды в чат"),
			item("abalance", "Баланс WC"),
			item("conabalance", "Баланс в чат"),
			item("setluck", "Удача"),
			item("aclear", "Очистить инвентарь"),
			item("setgamemode", "Режим игры игроку"),
			item("acreative", "Креатив себе"),
			item("asurvival", "Выживание себе"),
			item("aspectator", "Наблюдатель себе"),
			item("aadventure", "Приключение себе"),
			item("agm", "Режим бога"),
			item("event", "Мероприятие"),
			item("box", "Бокс"),
			item("unbox", "Отменить бокс"),
			item("adminlist", "Список всех админов"),
			item("plcheck", "Игроки на МП и тренировке"),
			item("scheck", "Статус МП / тренировок / бокса"),
			item("fullstop", "Стоп МП, тренировок и бокса"),
			item("offplayer", "Снять игрока с МП/тренировки"),
			item("setcommandadmin", "Команда одному админу"),
			item("allevent", "Мероприятие (админ играет)"),
			item("acoins", "История WC"),
			item("conacoins", "История WC в чат"),
			item("atime", "Время мира"),
			item("aweather", "Погода"),
			item("agive", "Выдать предмет"),
			item("permbanp", "Пермабан"),
			item("unpermbanp", "Снять пермабан"),
			item("add_admin", "Назначить админа"),
			item("set_admin", "Уровень админа"),
			item("add_blogger", "Выдать блогерскую админку"),
			item("un_admin", "Снять админа"),
			item("addnpc", "Создать NPC"),
			item("delnpc", "Удалить NPC"),
			item("npclist", "Список NPC"),
			item("connpclist", "Список NPC в чат"),
			item("npchere", "NPC к себе"),
			item("npc", "Команда от NPC"),
			item("control", "Управлять NPC"),
			item("uncontrol", "Стоп управления NPC"),
			item("setcasino", "Настройки казино"),
			item("tevent", "Тестовое МП"),
			item("getclan", "Просмотр клана"),
			item("congetclan", "Клан в чат"),
			item("getclanwh", "Склад клана"),
			item("congetclanwh", "Склад клана в чат"),
			item("resetclan", "Обнулить клан"),
			item("setworldspawn", "Точка появления мира"),
			item("all_kick", "Кик всех"),
			item("aserver", "Панель сервера"),
			item("stop_server", "Закрыть сервер"),
			item("start_server", "Открыть сервер"),
			item("add_wight", "В whitelist"),
			item("del_wight", "Из whitelist"),
			item("check_wight", "Показать whitelist"),
			item("banagm", "Запретить AGM"),
			item("unbanagm", "Разрешить AGM"),
			item("unagm", "Снять AGM"),
			item("plog", "Логи игрока"),
			item("koto", "Разбор античита"),
			item("conkoto", "Разбор античита в чат"),
			item("conplog", "Логи игрока в чат"),
			item("alog", "Логи администратора"),
			item("conalog", "Логи админа в чат"),
			item("clearlog", "Удалить логи игрока"),
			item("setprice", "Цены продажи"),
			item("setcheck", "Какие логи слать"),
			item("create_rules", "Создать rules.txt"),
			item("key_rules", "Зашифровать правила"),
			item("unkey_rules", "Расшифровать правила"),
			item("addcoin", "Выдать WC"),
			item("uncoin", "Забрать WC"),
			item("setpower", "Установить силу"),
			item("unprivate", "Снять приват дома"),
			item("acasino", "Казино игроку"),
			item("atsetspawn", "Спавн игрока сюда"),
			item("aclearlog", "Удалить админ-логи"),
			item("setnick", "Передать данные на ник"),
			item("areset", "Обнулить аккаунт"),
			item("aranks", "Настройка уровней админов"),
			item("aroles", "Создание уровней /aroles"),
			item("nickfilter", "Фильтр символов в никах"),
			item("dserver", "Полный контроль сервера"),
			item("devent", "Настройки и карты МП /devent"),
			item("dbuild", "Позвать на стройку карты МП /dbuild"),
			item("dbuildkick", "Убрать со стройки карты МП /dbuildkick"),
			item("all_clear", "Очистить инвентари всех"),
			item("all_unadm", "Снять всех админов"),
			item("all_permban", "Пермабан всех"),
			item("all_unpermban", "Снять масс. пермабан"),
			item("kill_server", "Убить процесс сервера"),
			item("revive_server", "Открыть сервер всем"),
			item("undev", "Снять с себя разраба"),
			item("cw", "Доступ к /cw"),
			item("cwwarn", "Устное предупреждение /cw warn"),
			item("fz", "Заморозка /fz"),
			item("unfz", "Разморозка /unfz"),
			item("cwserverstop", "Остановка сервера /cw server stop"),
			item("cwitem", "Запрет предметов /cw item"),
			item("cwmod", "Запрет модов /cw mod"),
			item("cwcommand", "Запрет команд /cw command"),
			item("cwboard", "Надпись табло /cw board"),
			item("cwpassword", "Система паролей /cw password"),
			item("cwdonate", "Ссылка доната /cw donate"),
			item("cwskill", "Ветки навыков /cw skill"),
			item("cwevent", "Блокировка МП /cw event"),
			item("aprivate", "Приваты: info и list /aprivate"),
			item("aprivateremove", "Снять приват /aprivate remove"),
			item("aprivaterestore", "Восстановить приват /aprivate restore"),
			item("plogall", "Общий лог игроков /plogall"),
			item("alogall", "Общий лог админов /alogall"),
			item("plogsearch", "Поиск по логам игроков /plogsearch"),
			item("alogsearch", "Поиск по логам админов /alogsearch"),
			item("aweatherlog", "Журнал погоды /aweatherlog"),
			item("aeventlog", "Журнал МП /aeventlog"),
			item("atraininglog", "Журнал тренировок /atraininglog"),
			item("aboxlog", "Журнал боксов /aboxlog"),
			item("clearweatherlog", "Очистить журнал погоды"),
			item("cleareventlog", "Очистить журнал МП"),
			item("cleartraininglog", "Очистить журнал тренировок"),
			item("clearboxlog", "Очистить журнал боксов"),
			item("eventstop", "Остановить любое МП /eventstop"),
			item("admstats", "Статистика админа /admstats"),
			item("astats", "Своя статистика /astats"),
			item("clanlog", "Логи любого клана /get clan"),
			item("banipp", "Бан по IP /banipp"),
			item("unbanipp", "Снять бан по IP /unbanipp"),
			item("ipcheck", "Совпадения IP /ipcheck"),
			item("filecheck", "Файлы игрока /filecheck"),
			item("buglist", "Баг-лист: баги и ошибки /buglist"),
			item("askills", "Дерево навыков игрока /askills"),
			item("askillinfo", "Баланс и прогресс навыков /askillinfo"),
			item("askilllist", "Список игроков с навыками /askilllist"),
			item("askillgive", "Выдать коины навыков /askillgive"),
			item("askilltake", "Забрать коины навыков /askilltake")
	);

	private RankPerms() {
	}

	private static Item item(String id, String label) {
		return new Item(id, label);
	}

	public static int defaultMin(String command) {
		return AdminRanks.defaultMinForCommand(command);
	}

	public static int minFor(String command) {
		if (command == null || command.isBlank()) {
			return AdminRanks.MOD;
		}
		migratePunishIds();
		String id = command.toLowerCase(Locale.ROOT);
		ModSettings settings = settings();
		if (settings != null && settings.commandMinRanks != null) {
			Integer stored = settings.commandMinRanks.get(id);
			if (stored == null) {
				String legacy = legacyPunishId(id);
				if (legacy != null) {
					stored = settings.commandMinRanks.get(legacy);
				}
			}
			if (stored != null) {
				if (stored > AdminRanks.DEV) {
					return stored;
				}
				int value = clampLevel(stored);
				if (gaOnly(id)) {
					return Math.max(AdminRanks.GA, value);
				}
				return value;
			}
		}
		int def = defaultMin(id);
		if (gaOnly(id)) {
			return Math.max(AdminRanks.GA, def);
		}
		return def;
	}

	/** Старые id без «p» → новые с «p» (настройки рангов / персональные флаги). */
	private static final Map<String, String> PUNISH_RENAMES = Map.ofEntries(
			Map.entry("ban", "banp"),
			Map.entry("unban", "unbanp"),
			Map.entry("warn", "warnp"),
			Map.entry("unwarn", "unwarnp"),
			Map.entry("jail", "jailp"),
			Map.entry("unjail", "unjailp"),
			Map.entry("akick", "akickp"),
			Map.entry("permban", "permbanp"),
			Map.entry("unpermban", "unpermbanp"),
			Map.entry("addnps", "addnpc"),
			Map.entry("delnps", "delnpc"),
			Map.entry("npslist", "npclist"),
			Map.entry("connpslist", "connpclist"),
			Map.entry("npshere", "npchere"),
			Map.entry("nps", "npc")
	);

	private static String legacyPunishId(String newId) {
		for (Map.Entry<String, String> e : PUNISH_RENAMES.entrySet()) {
			if (e.getValue().equals(newId)) {
				return e.getKey();
			}
		}
		return null;
	}

	private static boolean punishMigrated;

	private static void migratePunishIds() {
		if (punishMigrated) {
			return;
		}
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		punishMigrated = true;
		boolean changed = false;
		if (settings.commandMinRanks != null) {
			for (Map.Entry<String, String> e : PUNISH_RENAMES.entrySet()) {
				Integer old = settings.commandMinRanks.remove(e.getKey());
				if (old != null && !settings.commandMinRanks.containsKey(e.getValue())) {
					settings.commandMinRanks.put(e.getValue(), old);
					changed = true;
				}
			}
		}
		if (settings.adminCommandFlags != null) {
			for (Map<String, Boolean> map : settings.adminCommandFlags.values()) {
				if (map == null) {
					continue;
				}
				for (Map.Entry<String, String> e : PUNISH_RENAMES.entrySet()) {
					if (map.containsKey(e.getKey()) && !map.containsKey(e.getValue())) {
						map.put(e.getValue(), map.remove(e.getKey()));
						changed = true;
					} else {
						map.remove(e.getKey());
					}
				}
			}
		}
		if (settings.disabledPlayerCommands != null) {
			changed |= migrateDisabledList(settings.disabledPlayerCommands);
		}
		if (settings.disabledEveryoneCommands != null) {
			changed |= migrateDisabledList(settings.disabledEveryoneCommands);
		}
		if (changed) {
			save();
		}
	}

	private static boolean migrateDisabledList(List<String> list) {
		boolean changed = false;
		List<String> next = new ArrayList<>();
		for (String cmd : list) {
			if (cmd == null) {
				continue;
			}
			String low = cmd.toLowerCase(Locale.ROOT);
			String renamed = PUNISH_RENAMES.get(low);
			String use = renamed != null ? renamed : low;
			if (!next.contains(use)) {
				next.add(use);
			}
			if (renamed != null || !use.equals(cmd)) {
				changed = true;
			}
		}
		if (changed) {
			list.clear();
			list.addAll(next);
		}
		return changed;
	}

	/** Блогеру недоступны наказания (выдача и снятие), управление админами, настройки сервера и любые логи. */
	private static final java.util.Set<String> BLOGGER_DENY = java.util.Set.of(
			"plog", "conplog", "alog", "conalog", "koto", "conkoto", "punish", "conpunish", "acoins", "conacoins",
			"getclan", "congetclan", "plogall", "alogall", "plogsearch", "alogsearch", "aweatherlog",
			"aeventlog", "atraininglog", "aboxlog", "admstats", "clanlog", "banipp", "unbanipp", "ipcheck", "filecheck", "buglist",
			"clearweatherlog", "cleareventlog", "cleartraininglog", "clearboxlog",
			"add_admin", "un_admin", "set_admin", "add_blogger", "aranks", "aroles", "nickfilter", "setcommandadmin", "all_unadm",
			"banp", "unbanp", "permbanp", "unpermbanp", "warnp", "unwarnp", "jailp", "unjailp", "akickp",
			"all_kick", "all_permban", "all_unpermban", "cwwarn", "fz", "unfz", "ekick", "eventkick", "offplayer",
			"akill", "spawn", "banagm", "unbanagm", "unagm",
			"aclear", "adel", "all_clear",
			"aserver", "dserver", "devent", "dbuild", "dbuildkick", "setworldspawn", "atsetspawn", "setluck", "setpower", "addcoin", "uncoin",
			"acasino", "setcasino", "setprice", "setcheck", "acommand", "chcommands", "pmode", "amode",
			"stop_server", "start_server", "add_wight", "del_wight", "create_rules", "key_rules", "unkey_rules",
			"clearlog", "aclearlog", "setnick", "areset", "resetclan", "unprivate", "aprivateremove", "aprivaterestore",
			"cw", "cwserverstop", "cwitem", "cwmod", "cwcommand", "cwboard", "cwpassword", "cwdonate", "cwskill", "cwevent",
			"askillgive", "askilltake"
	);

	/** Ванильные команды наказаний и настроек сервера — для блогера, если ему доступны читы. */
	private static final java.util.Set<String> BLOGGER_VANILLA_DENY = java.util.Set.of(
			"kick", "ban", "ban-ip", "pardon", "pardon-ip", "op", "deop", "clear", "whitelist", "stop",
			"save-off", "save-on", "save-all", "gamerule", "difficulty", "setworldspawn", "defaultgamemode",
			"setidletimeout", "reload", "datapack", "publish", "worldborder", "perf", "jfr", "debug"
	);

	/** Имя запрещённой блогеру ванильной команды в строке (с учётом execute … run), иначе null. */
	public static String bloggerVanillaDenied(int rank, String raw) {
		if (!AdminRanks.isBlogger(rank) || raw == null || raw.isBlank()) {
			return null;
		}
		String[] parts = raw.trim().split("\\s+");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0 && !"run".equalsIgnoreCase(parts[i - 1])) {
				continue;
			}
			String name = parts[i].toLowerCase(Locale.ROOT);
			if (name.startsWith("/")) {
				name = name.substring(1);
			}
			if (name.startsWith("minecraft:")) {
				name = name.substring("minecraft:".length());
			}
			if (BLOGGER_VANILLA_DENY.contains(name)) {
				return name;
			}
		}
		return null;
	}

	public static boolean bloggerDenied(int rank, String command) {
		if (!AdminRanks.isBlogger(rank) || command == null) {
			return false;
		}
		Boolean override = bloggerFlag(command);
		if (override != null) {
			return !override;
		}
		return BLOGGER_DENY.contains(command.toLowerCase(Locale.ROOT));
	}

	/** Управление уровнями админов — не ниже ГА при любых настройках. */
	private static boolean gaOnly(String id) {
		return "aranks".equals(id) || "aroles".equals(id) || "nickfilter".equals(id);
	}

	/** Наказания, управление админами, настройки сервера и логи — их не добавляет автоподбор промежуточного уровня. */
	public static boolean sensitive(String command) {
		return command != null && BLOGGER_DENY.contains(command.toLowerCase(Locale.ROOT));
	}

	private static final java.util.Set<String> LISTED = new java.util.HashSet<>();

	static {
		for (Item item : COMMANDS) {
			LISTED.add(item.id);
		}
	}

	public static boolean listed(String command) {
		return command != null && LISTED.contains(command.toLowerCase(Locale.ROOT));
	}

	public static boolean allowed(int rank, String command) {
		ru.admincontrol.data.CustomRank custom = CustomRanks.byLevel(rank);
		if (custom != null && command != null) {
			String low = command.toLowerCase(Locale.ROOT);
			String id = PUNISH_RENAMES.getOrDefault(low, low);
			if (LISTED.contains(id)) {
				if (gaOnly(id) && rank < AdminRanks.GA) {
					return false;
				}
				return custom.commands != null && custom.commands.contains(id);
			}
		}
		if (AdminRanks.isBlogger(rank)) {
			Boolean override = bloggerFlag(command);
			if (override != null) {
				return override;
			}
			return !BLOGGER_DENY.contains(command.toLowerCase(Locale.ROOT))
					&& AdminRanks.CURATOR >= minFor(command);
		}
		return rank >= minFor(command);
	}

	/** Переопределение команды для блогера из /aranks; null — стандартные правила. */
	public static Boolean bloggerFlag(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		ModSettings settings = settings();
		if (settings == null || settings.bloggerCommandFlags == null) {
			return null;
		}
		return settings.bloggerCommandFlags.get(command.toLowerCase(Locale.ROOT));
	}

	public static Boolean adminCommandFlag(String nick, String command) {
		if (nick == null || command == null || command.isBlank()) {
			return null;
		}
		ModSettings settings = settings();
		if (settings == null || settings.adminCommandFlags == null) {
			return null;
		}
		Map<String, Boolean> map = settings.adminCommandFlags.get(nick.toLowerCase(Locale.ROOT));
		if (map == null) {
			return null;
		}
		return map.get(command.toLowerCase(Locale.ROOT));
	}

	public static void setAdminCommandFlag(String nick, String command, boolean enabled) {
		if (nick == null || command == null || command.isBlank()) {
			return;
		}
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		if (settings.adminCommandFlags == null) {
			settings.adminCommandFlags = new LinkedHashMap<>();
		}
		String who = nick.toLowerCase(Locale.ROOT);
		String id = command.toLowerCase(Locale.ROOT);
		Map<String, Boolean> map = settings.adminCommandFlags.computeIfAbsent(who, k -> new LinkedHashMap<>());
		map.put(id, enabled);
		save();
	}

	public static void toggleCommand(int rank, String command) {
		if (command == null || command.isBlank()) {
			return;
		}
		String id = command.toLowerCase(Locale.ROOT);
		ru.admincontrol.data.CustomRank custom = CustomRanks.byLevel(rank);
		if (custom != null) {
			if (custom.commands == null) {
				custom.commands = new ArrayList<>();
			}
			if (!custom.commands.remove(id) && LISTED.contains(id)) {
				custom.commands.add(id);
			}
			save();
			return;
		}
		if (AdminRanks.isBlogger(rank)) {
			ModSettings settings = settings();
			if (settings == null || !LISTED.contains(id)) {
				return;
			}
			if (settings.bloggerCommandFlags == null) {
				settings.bloggerCommandFlags = new LinkedHashMap<>();
			}
			boolean now = allowed(rank, id);
			settings.bloggerCommandFlags.put(id, !now);
			save();
			return;
		}
		int current = minFor(id);
		int next;
		if (current <= rank) {
			next = nextLevel(rank);
			if (next <= rank) {
				next = AdminRanks.DEV + 1;
			}
		} else {
			next = rank;
		}
		if (gaOnly(id)) {
			next = Math.max(AdminRanks.GA, next);
		}
		ensureMap().put(id, next);
		save();
	}

	public static int maxBanDays(int rank) {
		Integer stored = mapGet(settings() == null ? null : settings().rankBanDays, rank);
		if (stored != null) {
			return Math.max(1, Math.min(3650, stored));
		}
		if (rank >= AdminRanks.SENIOR) {
			return 3650;
		}
		if (rank >= AdminRanks.ADMIN) {
			return 9;
		}
		if (rank >= AdminRanks.SMOD) {
			return 4;
		}
		if (rank >= AdminRanks.MOD) {
			return 2;
		}
		return 0;
	}

	public static int maxJailMinutes(int rank) {
		Integer stored = mapGet(settings() == null ? null : settings().rankJailMinutes, rank);
		if (stored != null) {
			return Math.max(1, Math.min(525600, stored));
		}
		if (rank >= AdminRanks.SENIOR) {
			return 525600;
		}
		if (rank >= AdminRanks.ADMIN) {
			return 1440;
		}
		if (rank >= AdminRanks.SMOD) {
			return 180;
		}
		if (rank >= AdminRanks.MOD) {
			return 60;
		}
		return 0;
	}

	public static int punishStaffMin() {
		ModSettings settings = settings();
		if (settings != null && settings.punishStaffMin != null) {
			return clampLevel(settings.punishStaffMin);
		}
		return AdminRanks.CURATOR;
	}

	public static boolean canPunishStaff(int rank) {
		ru.admincontrol.data.CustomRank custom = CustomRanks.byLevel(rank);
		if (custom != null && custom.punishStaff != null) {
			return custom.punishStaff;
		}
		return rank >= punishStaffMin();
	}

	public static void shiftBanDays(int rank, int delta) {
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		if (settings.rankBanDays == null) {
			settings.rankBanDays = new LinkedHashMap<>();
		}
		int value = Math.max(1, Math.min(3650, maxBanDays(rank) + delta));
		settings.rankBanDays.put(key(rank), value);
		save();
	}

	public static void shiftJailMinutes(int rank, int delta) {
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		if (settings.rankJailMinutes == null) {
			settings.rankJailMinutes = new LinkedHashMap<>();
		}
		int value = Math.max(1, Math.min(525600, maxJailMinutes(rank) + delta));
		settings.rankJailMinutes.put(key(rank), value);
		save();
	}

	public static void togglePunishStaff(int rank) {
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		ru.admincontrol.data.CustomRank custom = CustomRanks.byLevel(rank);
		if (custom != null) {
			custom.punishStaff = !canPunishStaff(rank);
			save();
			return;
		}
		if (canPunishStaff(rank)) {
			int next = nextLevel(rank);
			if (next <= rank) {
				return;
			}
			settings.punishStaffMin = next;
		} else {
			settings.punishStaffMin = rank;
		}
		save();
	}

	public static void resetAll() {
		ModSettings settings = settings();
		if (settings == null) {
			return;
		}
		settings.commandMinRanks = new LinkedHashMap<>();
		settings.rankBanDays = new LinkedHashMap<>();
		settings.rankJailMinutes = new LinkedHashMap<>();
		settings.bloggerCommandFlags = new LinkedHashMap<>();
		settings.punishStaffMin = AdminRanks.CURATOR;
		save();
	}

	public static int nextLevel(int rank) {
		for (int i = 0; i < LEVELS.length - 1; i++) {
			if (LEVELS[i] == rank) {
				return LEVELS[i + 1];
			}
		}
		return AdminRanks.DEV;
	}

	public static int clampLevel(int raw) {
		int best = AdminRanks.MOD;
		int dist = Integer.MAX_VALUE;
		for (int level : LEVELS) {
			int d = Math.abs(level - raw);
			if (d < dist) {
				dist = d;
				best = level;
			}
		}
		return best;
	}

	private static Map<String, Integer> ensureMap() {
		ModSettings settings = settings();
		if (settings == null) {
			return new LinkedHashMap<>();
		}
		if (settings.commandMinRanks == null) {
			settings.commandMinRanks = new LinkedHashMap<>();
		}
		return settings.commandMinRanks;
	}

	private static Integer mapGet(Map<String, Integer> map, int rank) {
		return map == null ? null : map.get(key(rank));
	}

	private static String key(int rank) {
		return String.valueOf(rank);
	}

	private static ModSettings settings() {
		return DataManager.get() == null ? null : DataManager.get().settings();
	}

	private static void save() {
		if (DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	public static List<Row> rowsFor(int rank) {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("_ban", "Макс. бан, дни: " + maxBanDays(rank), true, true));
		rows.add(new Row("_jail", "Макс. jail, мин: " + maxJailMinutes(rank), true, true));
		rows.add(new Row("_punish", "Наказывать админов", canPunishStaff(rank), false));
		for (Item item : COMMANDS) {
			rows.add(new Row(item.id, item.label, allowed(rank, item.id), false));
		}
		return rows;
	}

	public static final class Item {
		public final String id;
		public final String label;

		public Item(String id, String label) {
			this.id = id;
			this.label = label;
		}
	}

	public static final class Row {
		public final String id;
		public final String label;
		public final boolean on;
		public final boolean pair;

		public Row(String id, String label, boolean on, boolean pair) {
			this.id = id;
			this.label = label;
			this.on = on;
			this.pair = pair;
		}
	}
}
