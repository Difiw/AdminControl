package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Полный профиль игрока, сохраняемый в players.json.
 */
public class PlayerRecord {
	public String uuid = "";
	public String nickname = "";
	public long playtimeMillis;
	/** Миллисекунды epoch: последний вход на сервер. */
	public long lastJoinAt;
	/** Миллисекунды epoch: последний выход с сервера. */
	public long lastLeaveAt;
	/** Журнал входов/выходов (новые в конце; хранится ограниченное число). */
	public List<LoginSession> loginSessions = new ArrayList<>();
	public String dailyKey = "";
	public int dailyBroken;
	public int dailyKills;
	public boolean dailyJoinPaid;
	public boolean dailyBreakPaid;
	public boolean dailyKillPaid;
	public int dailyPlaced;
	public boolean dailyPlacePaid;
	public long dailyPlayMs;
	public boolean dailyPlayPaid;
	public int dailyChat;
	public boolean dailyChatPaid;
	public int dailyCasino;
	public boolean dailyCasinoPaid;
	public int dailyEat;
	public boolean dailyEatPaid;
	public int dailyPvp;
	public boolean dailyPvpPaid;
	/** Сколько тренировок начато сегодня (лимит — settings + навыки Sync). */
	public int dailyTrainings;
	public int extraHomeSlots;
	public List<PlayerWaypoint> extraHomes = new ArrayList<>();
	public String clanId = "";
	public String weekKey = "";
	public int weekBroken;
	public int weekKills;
	public long weekPlayMs;
	public int lastKnownLevel;
	public double x;
	public double y;
	public double z;
	public String dimension = "minecraft:overworld";
	public String gameMode = "survival";
	public boolean cheatsEnabled;
	public boolean teleportAllowed = true;
	public int activeWarns;
	public String inventoryNbt = "";
	public PlayerWaypoint home;
	public List<PlayerWaypoint> points = new ArrayList<>();
	public String spawnChoice = "";
	public PlayerWaypoint customSpawn;
	public boolean homePrivate;
	public boolean homePrivateOwned;
	public int homeSize = 3;
	public int maxHomeSize = 3;
	public int backpackLevel;
	public List<String> backpack = new ArrayList<>();
	public long backpackBoughtAt;
	public boolean workbenchOwned;
	public long workbenchBoughtAt;
	public int furnaceLevel;
	public long furnaceBoughtAt;
	/** Автопечь: [вход1, выход1, вход2, выход2]. */
	public List<String> furnaceItems = new ArrayList<>();
	public SkillData skills;
	/** 0 = не заморожен, 1–3 = уровень /fz. */
	public int freezeLevel;
	public double freezeX;
	public double freezeY;
	public double freezeZ;
	public float freezeYaw;
	public float freezePitch;
	public String freezeDim = "";
	public String freezeBy = "";
	public boolean agmEnabled;
	/** AGM был включён до входа на МП/тренировку — вернуть после выхода. */
	public boolean agmRestore;
	public boolean agmBanned;
	public List<String> loginIps = new ArrayList<>();
	public int pendingLapis;
	public int coins;
	public int pendingCoins;
	/** Суммарно получено WC за всё время (награды, продажи, переводы и т.д.). */
	public int coinsEarned;
	/** Суммарно потрачено WC за всё время (магазин, казино, переводы и т.д.). */
	public int coinsSpent;
	/** true после учёта истории логов, чтобы не суммировать повторно. */
	public boolean coinStatsReady;
	/** null = включено по умолчанию (общий рубильник, устаревший) */
	public Boolean actionFeed;
	/** Имена ActionKind, которые выключены в /setcheck */
	public java.util.List<String> actionFeedDisabled = new java.util.ArrayList<>();
	/** false = скрыть боковое табло баланса */
	public Boolean showBoard;
	/** Сила (множитель урона). 0 или меньше = 1.0 */
	public double power;
	/** Удача. По умолчанию 0. На руды влияет только если больше 2. */
	public double luck;
	public int casinoGames;
	public int casinoSpent;
	public int casinoWon;
	public int casinoMoneyGames;
	public int casinoItemGames;
	public int casinoItemWins;
	public int casinoItemLosses;
	/** Сколько предметов сгорело в предметном казино. */
	public int casinoItemLost;
	/** Сколько предметов выдано выигрышем в предметном казино. */
	public int casinoItemReceived;
	public int casinoBiggestWin;
	public long lastCasinoGameMs;
	public long lastCasinoMoneyMs;
	public long lastCasinoItemMs;
	/** item id → сколько штук получено выигрышем. */
	public java.util.Map<String, Integer> casinoItemGot = new java.util.LinkedHashMap<>();
	public long lastAutoclickWarnMs;
	public int anticheatStrikes;
	public java.util.List<AntiCheatCase> anticheatCases = new java.util.ArrayList<>();
	public boolean casinoBanned;
	public int blocksBroken;
	public int blocksPlaced;
	public int peacefulKills;
	public int monsterKills;
	public float lastHealth;
	public int lastHunger;
	public int deaths;
	public List<PunishmentRecord> history = new ArrayList<>();
	/** Служебный NPC: в файлы не пишется, живёт до /delnps. */
	public boolean nps;

	public UUID uuidOrNull() {
		try {
			return uuid == null || uuid.isBlank() ? null : UUID.fromString(uuid);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public String playtimeFormatted() {
		long totalSeconds = Math.max(0L, playtimeMillis) / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		return hours + " ч " + minutes + " мин " + seconds + " с";
	}

	public String lastJoinFormatted() {
		return formatSessionTime(lastJoinAt);
	}

	public String lastLeaveFormatted() {
		return formatSessionTime(lastLeaveAt);
	}

	private static String formatSessionTime(long at) {
		if (at <= 0L) {
			return "неизвестно";
		}
		return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
				.withZone(java.time.ZoneId.systemDefault())
				.format(java.time.Instant.ofEpochMilli(at));
	}

	private static final int MAX_LOGIN_SESSIONS = 400;

	/** Фиксирует вход; закрывает «висящую» сессию, если выход не записался. */
	public void recordLogin() {
		long now = System.currentTimeMillis();
		ensureSessions();
		if (!this.loginSessions.isEmpty()) {
			LoginSession last = this.loginSessions.get(this.loginSessions.size() - 1);
			if (last != null && last.open()) {
				last.leaveAt = Math.max(last.joinAt, now - 1000L);
			}
		}
		this.lastJoinAt = now;
		this.loginSessions.add(new LoginSession(now));
		trimSessions();
	}

	/** Фиксирует выход для последней открытой сессии. */
	public void recordLogout() {
		long now = System.currentTimeMillis();
		ensureSessions();
		this.lastLeaveAt = now;
		for (int i = this.loginSessions.size() - 1; i >= 0; i--) {
			LoginSession session = this.loginSessions.get(i);
			if (session != null && session.open()) {
				session.leaveAt = Math.max(session.joinAt, now);
				return;
			}
		}
		if (this.lastJoinAt > 0L) {
			LoginSession session = new LoginSession(this.lastJoinAt);
			session.leaveAt = now;
			this.loginSessions.add(session);
			trimSessions();
		}
	}

	public List<String> loginSessionLines() {
		ensureSessions();
		java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
				.ofPattern("dd.MM.yyyy HH:mm:ss")
				.withZone(java.time.ZoneId.systemDefault());
		List<String> out = new ArrayList<>();
		out.add("Журнал входов и выходов: " + (this.nickname == null || this.nickname.isBlank() ? "?" : this.nickname));
		out.add("Всего записей: " + this.loginSessions.size() + " (новые сверху)");
		out.add("");
		if (this.loginSessions.isEmpty()) {
			out.add("Пока нет сохранённых заходов — история пишется с момента обновления мода.");
			if (this.lastJoinAt > 0L) {
				out.add("Известен только последний вход: " + lastJoinFormatted());
			}
			return out;
		}
		for (int i = this.loginSessions.size() - 1; i >= 0; i--) {
			LoginSession session = this.loginSessions.get(i);
			if (session == null || session.joinAt <= 0L) {
				continue;
			}
			String join = fmt.format(java.time.Instant.ofEpochMilli(session.joinAt));
			if (session.leaveAt > 0L) {
				String leave = fmt.format(java.time.Instant.ofEpochMilli(session.leaveAt));
				out.add("Вход  " + join);
				out.add("Выход " + leave + "  (" + sessionDuration(session.joinAt, session.leaveAt) + ")");
			} else {
				out.add("Вход  " + join);
				out.add("Выход — ещё онлайн");
			}
			out.add("");
		}
		return out;
	}

	private void ensureSessions() {
		if (this.loginSessions == null) {
			this.loginSessions = new ArrayList<>();
		}
	}

	private void trimSessions() {
		ensureSessions();
		while (this.loginSessions.size() > MAX_LOGIN_SESSIONS) {
			this.loginSessions.remove(0);
		}
	}

	private static String sessionDuration(long joinAt, long leaveAt) {
		long sec = Math.max(0L, (leaveAt - joinAt) / 1000L);
		long h = sec / 3600L;
		long m = (sec % 3600L) / 60L;
		long s = sec % 60L;
		if (h > 0) {
			return h + " ч " + m + " мин";
		}
		if (m > 0) {
			return m + " мин " + s + " с";
		}
		return s + " с";
	}

	public long countByType(PunishmentType type) {
		return history.stream().filter(r -> r.type == type).count();
	}

	public long countActive(PunishmentType type) {
		return history.stream().filter(r -> r.type == type && r.active).count();
	}

	public boolean feedEnabled(ActionKind kind) {
		if (Boolean.FALSE.equals(actionFeed)) {
			return false;
		}
		if (kind == null) {
			return true;
		}
		if (actionFeedDisabled == null || actionFeedDisabled.isEmpty()) {
			return true;
		}
		return actionFeedDisabled.stream().noneMatch(name -> kind.name().equalsIgnoreCase(name));
	}

	public void setFeedEnabled(ActionKind kind, boolean on) {
		if (kind == null) {
			return;
		}
		actionFeed = null;
		if (actionFeedDisabled == null) {
			actionFeedDisabled = new java.util.ArrayList<>();
		}
		actionFeedDisabled.removeIf(name -> kind.name().equalsIgnoreCase(name));
		if (!on) {
			actionFeedDisabled.add(kind.name());
		}
	}

	public void setAllFeeds(boolean on) {
		actionFeed = null;
		if (actionFeedDisabled == null) {
			actionFeedDisabled = new java.util.ArrayList<>();
		}
		actionFeedDisabled.clear();
		if (!on) {
			for (ActionKind kind : ActionKind.values()) {
				actionFeedDisabled.add(kind.name());
			}
		}
	}

	public boolean boardVisible() {
		return !Boolean.FALSE.equals(showBoard);
	}

	public double power() {
		return power > 0 ? power : 1.0;
	}

	public double luck() {
		return luck < 0 ? 0 : luck;
	}

	public void addEventPower() {
		addEventPower(0.1);
	}

	public void addEventPower(double amount) {
		if (amount <= 0) {
			return;
		}
		this.power = Math.round((power() + amount) * 10.0) / 10.0;
	}
}
