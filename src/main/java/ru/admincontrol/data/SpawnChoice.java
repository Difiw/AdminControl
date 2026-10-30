package ru.admincontrol.data;

import java.util.Locale;

public enum SpawnChoice {
	BED,
	WORLD,
	HOME,
	CLAN,
	CUSTOM;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String label() {
		return switch (this) {
			case BED -> "Спавн у кровати";
			case WORLD -> "Спавн на общей точке мира";
			case HOME -> "Спавн на метке дома";
			case CLAN -> "Спавн в доме клана";
			case CUSTOM -> "Индивидуальная точка (админ)";
		};
	}

	public static SpawnChoice from(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "bed", "кровать", "bedspawn" -> BED;
			case "world", "мир", "spawn" -> WORLD;
			case "home", "дом" -> HOME;
			case "clan", "клан" -> CLAN;
			case "custom", "admin" -> CUSTOM;
			default -> null;
		};
	}
}
