package ru.admincontrol.util;

import ru.admincontrol.data.AntiCheatRule;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

/** Какое наказание античит выдаёт за каждое нарушение (/anticheat → «Наказания»). */
public final class AntiCheatRules {
	public static final int NONE = 0;
	public static final int NOTICE = 1;
	public static final int WARN = 2;
	public static final int KICK = 3;
	public static final int JAIL = 4;
	public static final int BAN = 5;
	public static final int PERMBAN = 6;
	public static final String[] KIND_LABELS = {
			"только админам", "предупреждение", "варн", "кик", "jail", "бан", "пермбан"
	};

	public static final String AFK = "afk";
	public static final String CLICK1 = "click1";
	public static final String CLICK2 = "click2";
	public static final String CLICK3 = "click3";
	public static final String XRAY = "xray";
	public static final String XRAY_PACK = "xraypack";
	public static final String OVERLAY = "overlay";
	public static final String ILLEGAL_ITEM = "illegal_item";
	public static final String PACKET_ABUSE = "packet_abuse";
	public static final String ENCHANT_EXPLOIT = "enchant_exploit";
	public static final String[] KEYS = {AFK, CLICK1, CLICK2, CLICK3, XRAY, XRAY_PACK, OVERLAY,
			ILLEGAL_ITEM, PACKET_ABUSE, ENCHANT_EXPLOIT};
	public static final String[] LABELS = {
			"АФК",
			"Автокликер, 1-й раз",
			"Автокликер, 2-й раз",
			"Автокликер, 3-й и далее",
			"X-Ray: скрытая руда",
			"X-Ray: ресурс-пак",
			"Подсветка (MiniHUD, BBOR)",
			"Невозможный предмет / NBT",
			"Поддельные или спам-пакеты",
			"Невозможное зачарование"
	};

	public static final int MAX_DAYS = 365;
	public static final int MAX_MINUTES = 1440;

	private AntiCheatRules() {
	}

	public static int index(String key) {
		for (int i = 0; i < KEYS.length; i++) {
			if (KEYS[i].equals(key)) {
				return i;
			}
		}
		return -1;
	}

	/** Правило нарушения; если его ещё не настраивали — то, что античит делал раньше. */
	public static AntiCheatRule rule(String key) {
		ModSettings s = DataManager.get().settings();
		if (s.anticheatRules == null) {
			s.anticheatRules = new java.util.LinkedHashMap<>();
		}
		AntiCheatRule rule = s.anticheatRules.computeIfAbsent(key, k -> defaults(s, k));
		rule.kind = Math.max(NONE, Math.min(PERMBAN, rule.kind));
		rule.days = Math.max(1, Math.min(MAX_DAYS, rule.days));
		rule.minutes = Math.max(1, Math.min(MAX_MINUTES, rule.minutes));
		return rule;
	}

	private static AntiCheatRule defaults(ModSettings s, String key) {
		int jail = Math.max(1, s.anticheatJailMinutes);
		int cheatDays = Math.max(1, s.anticheatXrayBanDays);
		return switch (key) {
			case AFK -> new AntiCheatRule(KICK, 1, jail);
			case CLICK1 -> new AntiCheatRule(NOTICE, 1, jail);
			case CLICK2 -> new AntiCheatRule(KICK, 1, jail);
			case CLICK3 -> new AntiCheatRule(BAN, Math.max(1, s.anticheatClickBanDays), jail);
			case ILLEGAL_ITEM, ENCHANT_EXPLOIT -> new AntiCheatRule(PERMBAN, cheatDays, jail);
			case PACKET_ABUSE -> new AntiCheatRule(KICK, 1, jail);
			// Эвристика X-Ray/клиентские отчёты не являются доказанным фактом.
			default -> new AntiCheatRule(NONE, cheatDays, jail);
		};
	}

	/** «бан 30 дн», «jail 15 мин», «кик»… */
	public static String verdict(AntiCheatRule rule) {
		return switch (rule.kind) {
			case NONE -> "только сообщение админам";
			case JAIL -> "jail " + rule.minutes + " мин";
			case BAN -> "бан " + rule.days + " дн";
			default -> KIND_LABELS[Math.max(NONE, Math.min(PERMBAN, rule.kind))];
		};
	}

	public static void cycleKind(String key, boolean forward) {
		AntiCheatRule rule = rule(key);
		int n = KIND_LABELS.length;
		rule.kind = ((rule.kind + (forward ? 1 : -1)) % n + n) % n;
		syncLegacy(key, rule);
	}

	/** Срок: у бана — дни, у jail — минуты; у остальных наказаний срока нет. */
	public static boolean shiftAmount(String key, boolean up) {
		AntiCheatRule rule = rule(key);
		if (rule.kind == BAN) {
			rule.days = Math.max(1, Math.min(MAX_DAYS, rule.days + step(rule.days, up)));
		} else if (rule.kind == JAIL) {
			rule.minutes = Math.max(1, Math.min(MAX_MINUTES, rule.minutes + step(rule.minutes, up)));
		} else {
			return false;
		}
		syncLegacy(key, rule);
		return true;
	}

	/** 1 до 10, по 5 до 60, дальше по 30. */
	private static int step(int value, boolean up) {
		if (up) {
			return value < 10 ? 1 : value < 60 ? 5 : 30;
		}
		return -(value <= 10 ? 1 : value <= 60 ? 5 : 30);
	}

	/** Старые поля «бан за читы» и «бан автокликера» показывает /aserver — держим их равными правилам. */
	private static void syncLegacy(String key, AntiCheatRule rule) {
		ModSettings s = DataManager.get().settings();
		if (rule.kind != BAN) {
			return;
		}
		if (CLICK3.equals(key)) {
			s.anticheatClickBanDays = Math.min(30, rule.days);
		} else if (XRAY.equals(key) || XRAY_PACK.equals(key) || OVERLAY.equals(key)) {
			s.anticheatXrayBanDays = Math.min(90, rule.days);
		}
	}

	/** Срок бана из /aserver: для читов — X-Ray и подсветка, иначе — 3-й раз автокликера. */
	public static void setBanDays(boolean cheats, int days) {
		String[] keys = cheats ? new String[] {XRAY, XRAY_PACK, OVERLAY} : new String[] {CLICK3};
		for (String key : keys) {
			AntiCheatRule rule = rule(key);
			rule.days = Math.max(1, Math.min(MAX_DAYS, days));
		}
	}
}
