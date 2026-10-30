package ru.admincontrol.data;

/** Настройки МП, с которыми оно запускается, если организатор их не менял (/devent). */
public final class EventDefaults {
	public int players = 8;
	public int reward = 16;
	public int power = 1;
	public boolean bumpers = true;
	public int prize1 = 30;
	public int prize2 = 20;
	public int prize3 = 10;
	public int luck;

	public static EventDefaults builtIn(int type) {
		EventDefaults d = new EventDefaults();
		d.players = type == 3 ? 2 : type == 14 ? 16 : 8;
		d.luck = type == 6 ? 5 : 0;
		return d;
	}

	public static EventDefaults of(int type) {
		return of(String.valueOf(type), type);
	}

	/** Сохранённые значения для МП с ключом key (правила base) или стандартные; всегда в допустимых пределах. */
	public static EventDefaults of(String key, int base) {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		EventDefaults stored = s == null || s.eventDefaults == null ? null : s.eventDefaults.get(key);
		EventDefaults d = builtIn(base);
		if (stored != null) {
			d.players = stored.players;
			d.reward = stored.reward;
			d.power = stored.power;
			d.bumpers = stored.bumpers;
			d.prize1 = stored.prize1;
			d.prize2 = stored.prize2;
			d.prize3 = stored.prize3;
			d.luck = stored.luck;
		}
		d.clamp(base);
		return d;
	}

	public static EventDefaults edit(int type) {
		return edit(String.valueOf(type), type);
	}

	/** Изменяемая запись для МП (создаётся из текущих значений). */
	public static EventDefaults edit(String key, int base) {
		ModSettings s = DataManager.get().settings();
		if (s.eventDefaults == null) {
			s.eventDefaults = new java.util.LinkedHashMap<>();
		}
		return s.eventDefaults.computeIfAbsent(key, k -> of(key, base));
	}

	public static void reset(int type) {
		reset(String.valueOf(type));
	}

	public static void reset(String key) {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		if (s != null && s.eventDefaults != null) {
			s.eventDefaults.remove(key);
		}
	}

	public void clamp(int type) {
		this.players = Math.max(2, Math.min(32, this.players));
		if (type == 3) {
			this.players = 2;
		}
		if (type == 11 && this.players % 2 != 0) {
			this.players--;
		}
		this.reward = Math.max(0, Math.min(10000, this.reward));
		this.power = Math.max(0, Math.min(50, this.power));
		this.prize1 = Math.max(0, Math.min(10000, this.prize1));
		this.prize2 = Math.max(0, Math.min(10000, this.prize2));
		this.prize3 = Math.max(0, Math.min(10000, this.prize3));
		this.luck = Math.max(0, Math.min(50, this.luck));
	}
}
