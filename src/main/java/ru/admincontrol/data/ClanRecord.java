package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.List;

public class ClanRecord {
	public String id = "";
	public String name = "";
	public String ownerUuid = "";
	public String ownerNick = "";
	public List<String> memberUuids = new ArrayList<>();
	public List<String> memberNicks = new ArrayList<>();
	public PlayerWaypoint home;
	public int treasury;
	public boolean homeBought;
	public boolean homePrivate;
	public int homeSize = 9;
	public int hallLevel;
	public int warehouseLevel;
	public int bankLevel;
	public int treasuryLevel;
	public List<String> warehouse = new ArrayList<>();
	public String weekKey = "";
	public int weekPoints;
	public int weekMpWins;
	public int weekWarWins;
	public int totalPoints;
	public int totalMpWins;
	public int totalWarWins;
	/** Старые личные доступы (до уровней). Переносятся в memberRanks при первом обращении. */
	public java.util.Map<String, Integer> memberFlags = new java.util.LinkedHashMap<>();
	/** uuid или ник (lower) → уровень 1–4. Глава всегда уровень 5. */
	public java.util.Map<String, Integer> memberRanks = new java.util.LinkedHashMap<>();
	/** Доступы уровней: индекс = уровень (1–4), элемент 0 не используется. */
	public List<Integer> rankFlags = new ArrayList<>();

	public static final int LEADER = 5;
	public static final int DEPUTY = 4;

	public static final int TP = 1;
	public static final int BUILD = 2;
	public static final int USE = 4;
	public static final int WITHDRAW = 8;
	public static final int WH_TAKE = 16;
	public static final int DEPOSIT = 32;
	public static final int WH_PUT = 64;
	public static final int CHAT = 128;
	public static final int INVITE = 256;
	public static final int MANAGE = 512;
	public static final int GRANT = 1024;
	public static final int SPEND = 2048;
	public static final int ALL = 4095;

	/** Порядок строк в настройке доступов. */
	public static final int[] FLAG_ORDER = {
			SPEND, DEPOSIT, WITHDRAW, WH_PUT, WH_TAKE, CHAT, INVITE, MANAGE, GRANT, TP, BUILD, USE
	};

	public static String flagLabel(int flag) {
		return switch (flag) {
			case SPEND -> "Использовать казну";
			case DEPOSIT -> "Класть WC в казну";
			case WITHDRAW -> "Брать WC из казны";
			case WH_PUT -> "Класть на склад";
			case WH_TAKE -> "Брать со склада";
			case CHAT -> "Писать в чат клана";
			case INVITE -> "Приглашать в клан";
			case MANAGE -> "Повышать/понижать/кик";
			case GRANT -> "Настраивать доступы";
			case TP -> "ТП на дом клана";
			case BUILD -> "Строить на территории";
			case USE -> "Двери/сундуки на терр.";
			default -> "?";
		};
	}

	public static String rankLabel(int rank) {
		return switch (rank) {
			case LEADER -> "Глава";
			case DEPUTY -> "Заместитель";
			default -> "Уровень " + Math.max(1, rank);
		};
	}

	public static int defaultFlags(int rank) {
		int base = TP | USE | CHAT | DEPOSIT;
		return switch (rank) {
			case 1 -> base;
			case 2 -> base | BUILD | WH_PUT;
			case 3 -> base | BUILD | WH_PUT | WH_TAKE | INVITE;
			default -> ALL;
		};
	}
}
