package ru.admincontrol.lumira;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.admincontrol.AdminControlMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сюжетная экономика игрока: «Лиры» (деньги) и «Мирты» (репутация). Хранится на сервере отдельно от личных
 * сюжетных измерений и от сюжетных сохранений, поэтому откат сюжета её не затрагивает. Разовые награды
 * фиксируются по идентификатору и не выдаются повторно. Сброс прохождения экономику не стирает.
 */
public final class LumiraEconomy {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int JOURNAL_LIMIT = 200;
	private static Map<String, Account> accounts;

	private LumiraEconomy() {
	}

	public static final class Entry {
		public long at;
		public String kind;
		public long delta;
		public String reason;
	}

	public static final class Account {
		public long lyres;
		public long myrtles;
		/** Идентификаторы уже выданных разовых наград. */
		public Set<String> claimed = new HashSet<>();
		public List<Entry> journal = new ArrayList<>();
	}

	private static Path file() {
		return LumiraConfig.dir().resolve("lumira_economy.json");
	}

	private static synchronized Map<String, Account> load() {
		if (accounts == null) {
			accounts = new LinkedHashMap<>();
			try {
				Path f = file();
				if (Files.exists(f)) {
					Map<String, Account> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
							new TypeToken<LinkedHashMap<String, Account>>() {
							}.getType());
					if (read != null) {
						accounts = read;
					}
				}
			} catch (Exception e) {
				AdminControlMod.LOGGER.error("Lumira: экономика не прочитана, копия файла сохранена как .broken", e);
				try {
					Files.copy(file(), file().resolveSibling("lumira_economy.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {
				}
			}
		}
		return accounts;
	}

	private static synchronized void save() {
		try {
			Path f = file();
			Path tmp = f.resolveSibling("lumira_economy.json.tmp");
			Files.writeString(tmp, GSON.toJson(load()), StandardCharsets.UTF_8);
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Lumira: экономика не сохранена", e);
		}
	}

	private static Account account(UUID id) {
		return load().computeIfAbsent(id.toString(), k -> new Account());
	}

	public static synchronized long lyres(UUID id) {
		Account a = load().get(id.toString());
		return a == null ? 0 : a.lyres;
	}

	public static synchronized long myrtles(UUID id) {
		Account a = load().get(id.toString());
		return a == null ? 0 : a.myrtles;
	}

	/** Начисление (или списание при отрицательном значении). false — отказ, ничего не изменено. */
	public static synchronized boolean addLyres(UUID id, long delta, String reason) {
		Account a = account(id);
		if (a.lyres + delta < 0) {
			return false;
		}
		a.lyres += delta;
		journal(a, "lyres", delta, reason);
		save();
		return true;
	}

	/** Репутацию можно менять административно; проверка репутации в диалоге её не списывает. */
	public static synchronized boolean addMyrtles(UUID id, long delta, String reason) {
		Account a = account(id);
		if (a.myrtles + delta < 0) {
			return false;
		}
		a.myrtles += delta;
		journal(a, "myrtles", delta, reason);
		save();
		return true;
	}

	/** Разовая награда: повторный вызов с тем же rewardId ничего не выдаёт (защита от выдачи через откат сюжета). */
	public static synchronized boolean claimReward(UUID id, String rewardId, long lyres, long myrtles) {
		Account a = account(id);
		if (!a.claimed.add(rewardId)) {
			return false;
		}
		a.lyres += lyres;
		a.myrtles += myrtles;
		journal(a, "reward:" + rewardId, lyres + myrtles, "разовая награда");
		save();
		return true;
	}

	public static synchronized boolean claimed(UUID id, String rewardId) {
		Account a = load().get(id.toString());
		return a != null && a.claimed.contains(rewardId);
	}

	/** Полное удаление накоплений — отдельная административная операция, не часть сброса прохождения. */
	public static synchronized void wipe(UUID id, String reason) {
		Account a = account(id);
		journal(a, "wipe", -(a.lyres + a.myrtles), reason);
		a.lyres = 0;
		a.myrtles = 0;
		a.claimed.clear();
		save();
	}

	private static void journal(Account a, String kind, long delta, String reason) {
		Entry e = new Entry();
		e.at = System.currentTimeMillis();
		e.kind = kind;
		e.delta = delta;
		e.reason = reason == null ? "" : reason;
		a.journal.add(e);
		while (a.journal.size() > JOURNAL_LIMIT) {
			a.journal.remove(0);
		}
	}
}
