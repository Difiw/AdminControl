package ru.admincontrol.data;

import java.util.Locale;

/**
 * Внутренние уровни: 10, 15, 20… Отображение 1–9.
 * Старый 3.5 (35) при загрузке становится старшим администратором (4).
 */
public final class AdminRanks {
	public static final int NONE = 0;
	public static final int MOD = 10;
	public static final int SMOD = 15;
	public static final int ADMIN = 20;
	public static final int SENIOR = 30;
	/** @deprecated нет отдельного ранга; то же, что {@link #SENIOR} */
	@Deprecated
	public static final int HIGHER = SENIOR;
	/**
	 * Блогер (4.5): старше старшего администратора, младше куратора. Команды — как у куратора,
	 * кроме наказаний, управления админами и настроек сервера ({@link ru.admincontrol.util.RankPerms#bloggerDenied}).
	 * Значение 35 занято миграцией старого ранга 3.5.
	 */
	public static final int BLOGGER = 37;
	public static final int CURATOR = 40;
	public static final int ZGA = 50;
	public static final int TECH = 60;
	public static final int GA = 70;
	public static final int DEV = 80;

	private AdminRanks() {
	}

	public static boolean isStaff(int level) {
		return level >= MOD;
	}

	public static String title(int level) {
		CustomRank custom = ru.admincontrol.util.CustomRanks.byLevel(level);
		if (custom != null) {
			return custom.title;
		}
		return switch (level) {
			case MOD -> "Модератор";
			case SMOD -> "Старший модератор";
			case ADMIN -> "Администратор";
			case SENIOR -> "Старший администратор";
			case BLOGGER -> "Блогер";
			case CURATOR -> "Куратор администрации";
			case ZGA -> "ЗГА";
			case TECH -> "Тех. Администратор";
			case GA -> "ГА";
			case DEV -> "Разработчик";
			default -> "Игрок";
		};
	}

	public static final String[] TAG_KEYS = {
			"mod", "smod", "admin", "senior", "curator", "zga", "tech", "ga", "dev", "blogger"
	};

	public static int levelOfTagKey(String key) {
		return switch (key == null ? "" : key.toLowerCase(Locale.ROOT)) {
			case "mod" -> MOD;
			case "smod" -> SMOD;
			case "admin" -> ADMIN;
			case "senior" -> SENIOR;
			case "blogger" -> BLOGGER;
			case "curator" -> CURATOR;
			case "zga" -> ZGA;
			case "tech" -> TECH;
			case "ga" -> GA;
			case "dev" -> DEV;
			default -> NONE;
		};
	}

	public static boolean isBlogger(int level) {
		return level == BLOGGER;
	}

	/** Куратор и выше либо блогер — для возможностей куратора, не связанных с наказаниями и настройками. */
	public static boolean curatorOrBlogger(int level) {
		return level >= CURATOR || level == BLOGGER;
	}

	public static String tagKey(int level) {
		return switch (level) {
			case MOD -> "mod";
			case SMOD -> "smod";
			case ADMIN -> "admin";
			case SENIOR -> "senior";
			case BLOGGER -> "blogger";
			case CURATOR -> "curator";
			case ZGA -> "zga";
			case TECH -> "tech";
			case GA -> "ga";
			case DEV -> "dev";
			default -> "";
		};
	}

	public static String defaultChatTag(int level) {
		return switch (level) {
			case MOD -> "Модератор";
			case SMOD -> "Старший модератор";
			case ADMIN -> "Админ";
			case SENIOR -> "Старший администратор";
			case BLOGGER -> "Блогер";
			case CURATOR -> "Куратор";
			case ZGA -> "ЗГА";
			case TECH -> "Тех";
			case GA -> "ГА";
			case DEV -> "Разработчик";
			default -> "";
		};
	}

	public static String defaultChatTagKey(String key) {
		if (key == null) {
			return "";
		}
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "mod" -> defaultChatTag(MOD);
			case "smod" -> defaultChatTag(SMOD);
			case "admin" -> defaultChatTag(ADMIN);
			case "senior" -> defaultChatTag(SENIOR);
			case "blogger" -> defaultChatTag(BLOGGER);
			case "curator" -> defaultChatTag(CURATOR);
			case "zga" -> defaultChatTag(ZGA);
			case "tech" -> defaultChatTag(TECH);
			case "ga" -> defaultChatTag(GA);
			case "dev" -> defaultChatTag(DEV);
			default -> "";
		};
	}

	public static String chatTag(int level) {
		CustomRank customRank = ru.admincontrol.util.CustomRanks.byLevel(level);
		if (customRank != null) {
			return customRank.role == null || customRank.role.isBlank() ? customRank.title : customRank.role;
		}
		String key = tagKey(level);
		if (key.isBlank()) {
			return "";
		}
		ModSettings settings = DataManager.get() == null ? null : DataManager.get().settings();
		if (settings != null && settings.rankChatTags != null) {
			String custom = settings.rankChatTags.get(key);
			if (custom != null && !custom.isBlank()) {
				return custom.trim();
			}
		}
		return defaultChatTag(level);
	}

	public static String display(int level) {
		CustomRank custom = ru.admincontrol.util.CustomRanks.byLevel(level);
		if (custom != null) {
			return custom.access;
		}
		return switch (level) {
			case MOD -> "1";
			case SMOD -> "2";
			case ADMIN -> "3";
			case SENIOR -> "4";
			case BLOGGER -> "4.5";
			case CURATOR -> "5";
			case ZGA -> "6";
			case TECH -> "7";
			case GA -> "8";
			case DEV -> "9";
			default -> "0";
		};
	}

	/** Штатный уровень или роль из /aroles (по ключу «r1» или уровню доступа «2.5»). */
	public static Integer parseAssignable(String raw) {
		Integer standard = parseStandard(raw);
		if (standard != null) {
			return standard;
		}
		CustomRank custom = ru.admincontrol.util.CustomRanks.byKey(raw);
		return custom == null ? null : custom.level;
	}

	public static Integer parseStandard(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim().replace(',', '.').toLowerCase(Locale.ROOT);
		return switch (value) {
			case "1" -> MOD;
			case "2" -> SMOD;
			case "3" -> ADMIN;
			case "3.5" -> SENIOR;
			case "4" -> SENIOR;
			case "4.5", "blogger", "блогер" -> BLOGGER;
			case "5" -> CURATOR;
			case "6" -> ZGA;
			case "7" -> TECH;
			case "8" -> GA;
			case "9" -> DEV;
			default -> null;
		};
	}

	public static int migrateStored(int raw) {
		if (raw > MOD && ru.admincontrol.util.CustomRanks.byLevel(raw) != null) {
			return raw;
		}
		return switch (raw) {
			case 0 -> TECH;
			case 1, MOD -> MOD;
			case SMOD -> SMOD;
			case 2, ADMIN -> ADMIN;
			case 3, SENIOR, 35 -> SENIOR;
			case 4, ZGA -> ZGA;
			case 5, GA -> GA;
			case 6, TECH -> TECH;
			case 7 -> GA;
			case 8, 9, DEV -> DEV;
			case CURATOR -> CURATOR;
			case BLOGGER -> BLOGGER;
			default -> {
				if (raw == MOD || raw == SMOD || raw == ADMIN || raw == SENIOR || raw == CURATOR
						|| raw == ZGA || raw == TECH || raw == GA || raw == DEV) {
					yield raw;
				}
				yield MOD;
			}
		};
	}

	public static int maxBanDays(int level) {
		return ru.admincontrol.util.RankPerms.maxBanDays(level);
	}

	public static int defaultMinForCommand(String literal) {
		if (literal == null || literal.isBlank()) {
			return MOD;
		}
		return switch (literal.toLowerCase(Locale.ROOT)) {
			case "all_clear", "all_unadm", "all_permban", "all_unpermban",
					"kill_server", "revive_server", "undev", "dserver", "devent", "dbuild", "dbuildkick",
					"duel", "beta", "bottraining" -> DEV;
			case "koto", "conkoto" -> MOD;
			case "all_kick", "banagm", "unbanagm", "unagm", "plog", "alog",
					"conplog", "conalog", "clearlog",
					"clearweatherlog", "cleareventlog", "cleartraininglog", "clearboxlog",
					"stop_server", "start_server", "add_wight", "del_wight", "check_wight",
					"setprice", "create_rules", "key_rules", "unkey_rules",
					"atserver", "setcheck", "addcoin", "uncoin", "setpower",
					"unprivate", "acasino" -> TECH;
			case "aclearlog", "setnick", "areset", "aranks", "aroles", "nickfilter", "resetclan" -> GA;
			case "aserver", "setworldspawn", "plcheck", "scheck", "fullstop", "offplayer", "eventstop", "admstats" -> CURATOR;
			case "setcommandadmin" -> GA;
			case "add_admin", "un_admin", "set_admin", "add_blogger",
					"addnpc", "delnpc", "npc", "npclist", "connpclist", "npchere", "control", "uncontrol", "tevent", "getclan", "congetclan", "getclanwh", "congetclanwh" -> CURATOR;
			case "setcasino", "anticheat" -> DEV;
			case "atime", "aweather", "agive", "permbanp", "unpermbanp", "banipp", "unbanipp" -> SENIOR;
			case "clanlog", "ipcheck", "filecheck", "buglist" -> ADMIN;
			case "aclear", "setgamemode", "acreative", "asurvival", "aspectator", "aadventure",
					"event", "allevent", "agm", "acoins", "conacoins", "cwarok", "cwardeny", "cwarstop", "ekick", "eventkick" -> SENIOR;
			case "getinv", "congetinv", "punish", "conpunish", "pmode", "amode", "ahp", "afill", "acommand", "chcommands",
					"conchcommands", "abalance", "conabalance", "setluck", "box", "unbox" -> ADMIN;
			case "getach", "congetach", "cw", "cwwarn", "fz", "unfz" -> SMOD;
			case "askills", "askillinfo", "askilllist" -> SMOD;
			case "askillgive", "askilltake" -> SENIOR;
			case "aprivate", "plogall", "alogall", "plogsearch", "alogsearch", "aweatherlog",
					"aeventlog", "atraininglog", "aboxlog" -> SMOD;
			case "aprivateremove", "aprivaterestore" -> ZGA;
			case "cwserverstop" -> TECH;
			case "cwitem", "cwmod", "cwcommand", "cwboard", "cwpassword", "cwdonate", "cwskill", "cwevent" -> ZGA;
			case "conahelp", "conget", "aplayer", "astats" -> MOD;
			default -> MOD;
		};
	}

	public static int minForCommand(String literal) {
		return ru.admincontrol.util.RankPerms.minFor(literal);
	}
}
