package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Дерево навыков игрока: валюты, купленные уровни, прогресс заданий. Рюкзак, верстак и автопечь — в старых полях записи. */
public class SkillData {
	public Map<String, Integer> coins = new LinkedHashMap<>();
	public Map<String, Integer> earned = new LinkedHashMap<>();
	public Map<String, Integer> spent = new LinkedHashMap<>();
	public Map<String, Integer> levels = new LinkedHashMap<>();
	public Map<String, Long> progress = new LinkedHashMap<>();
	/** Сколько этапов задания уже выдано (этапы одноразовые). */
	public Map<String, Integer> stages = new LinkedHashMap<>();
	/** Уникальные значения для заданий «разные виды»: биомы, еда, руды, мобы, боссы. */
	public Map<String, List<String>> seen = new LinkedHashMap<>();
	/** Последние прочитанные значения ванильной статистики (чтобы считать только прирост). */
	public Map<String, Long> stats = new LinkedHashMap<>();
	public String day = "";
	/** Счётчики текущих суток: ежедневные задания и дневные лимиты против фарма. */
	public Map<String, Long> daily = new LinkedHashMap<>();
	public List<String> dailyDone = new ArrayList<>();
	public List<String> history = new ArrayList<>();
	/** Текущая серия активной игры без смерти, сек. */
	public long lifeSeconds;

	public void normalize() {
		if (this.coins == null) {
			this.coins = new LinkedHashMap<>();
		}
		if (this.earned == null) {
			this.earned = new LinkedHashMap<>();
		}
		if (this.spent == null) {
			this.spent = new LinkedHashMap<>();
		}
		if (this.levels == null) {
			this.levels = new LinkedHashMap<>();
		}
		if (this.progress == null) {
			this.progress = new LinkedHashMap<>();
		}
		if (this.stages == null) {
			this.stages = new LinkedHashMap<>();
		}
		if (this.seen == null) {
			this.seen = new LinkedHashMap<>();
		}
		if (this.stats == null) {
			this.stats = new LinkedHashMap<>();
		}
		if (this.day == null) {
			this.day = "";
		}
		if (this.daily == null) {
			this.daily = new LinkedHashMap<>();
		}
		if (this.dailyDone == null) {
			this.dailyDone = new ArrayList<>();
		}
		if (this.history == null) {
			this.history = new ArrayList<>();
		}
		this.coins.replaceAll((k, v) -> v == null ? 0 : Math.max(0, v));
		this.levels.replaceAll((k, v) -> v == null ? 0 : Math.max(0, v));
	}

	public int coins(String currency) {
		Integer v = this.coins.get(currency);
		return v == null ? 0 : Math.max(0, v);
	}

	public long progress(String key) {
		Long v = this.progress.get(key);
		return v == null ? 0L : v;
	}

	public int stage(String quest) {
		Integer v = this.stages.get(quest);
		return v == null ? 0 : v;
	}

	public long dailyValue(String key) {
		Long v = this.daily.get(key);
		return v == null ? 0L : v;
	}
}
