package ru.admincontrol.item;

/** Инструменты для ремонта игрового автомата; порядок задаёт индексы в плане ремонта автомата. */
public enum RepairTool {
	HAMMER("hammer", "Молоток"),
	SOLDERING_IRON("soldering_iron", "Паяльник"),
	PLIERS("pliers", "Плоскогубцы"),
	SCREWDRIVER("screwdriver", "Отвёртка"),
	MULTIMETER("multimeter", "Мультиметр");

	public static final int COUNT = values().length;

	private final String id;
	private final String title;

	RepairTool(String id, String title) {
		this.id = id;
		this.title = title;
	}

	public String id() {
		return this.id;
	}

	public String title() {
		return this.title;
	}
}
