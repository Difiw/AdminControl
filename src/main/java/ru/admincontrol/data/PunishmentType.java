package ru.admincontrol.data;

import java.util.Locale;

/**
 * Тип наказания или связанного действия. Снятые наказания не удаляются — меняется флаг active.
 */
public enum PunishmentType {
	BAN,
	PERMABAN,
	WARN,
	KICK,
	UNBAN,
	UNPERMABAN,
	UNWARN,
	JAIL,
	UNJAIL,
	VERBAL_WARN,
	IPBAN,
	UNIPBAN;

	public String russianName() {
		return switch (this) {
			case BAN -> "бан";
			case PERMABAN -> "перманентный бан";
			case WARN -> "варн";
			case KICK -> "кик";
			case UNBAN -> "снятие бана";
			case UNPERMABAN -> "снятие перманентного бана";
			case UNWARN -> "снятие варна";
			case JAIL -> "jail";
			case UNJAIL -> "снятие jail";
			case VERBAL_WARN -> "устное предупреждение";
			case IPBAN -> "бан по IP";
			case UNIPBAN -> "снятие бана по IP";
		};
	}

	public static PunishmentType from(String raw) {
		return valueOf(raw.trim().toUpperCase(Locale.ROOT));
	}
}
