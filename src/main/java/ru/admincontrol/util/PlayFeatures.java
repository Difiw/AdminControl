package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PlayFeatures {
	private PlayFeatures() {
	}

	public static ModSettings s() {
		return DataManager.get() == null ? new ModSettings() : DataManager.get().settings();
	}

	public static String dayKey() {
		return LocalDate.now().toString();
	}

	public static String weekKey() {
		LocalDate d = LocalDate.now();
		WeekFields w = WeekFields.ISO;
		return d.get(w.weekBasedYear()) + "-W" + String.format(Locale.ROOT, "%02d", d.get(w.weekOfWeekBasedYear()));
	}

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		rolloverWeek(server);
		ClanTop.ensureWeeks();
		TpaService.tick(server);
		AutoEventService.tick(server);
	}

	public static void onJoin(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player) || AuthService.pending(player)) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ensureDay(record);
		ensureWeek(record);
		if (taskOn(s().dailyJoinOn) && !record.dailyJoinPaid) {
			pay(player, record, "ежедневный вход", "Ежедневка: +" + Money.wc(Math.max(0, s().dailyJoinWc)) + " за вход.",
					s().dailyJoinWc);
			record.dailyJoinPaid = true;
			DataManager.get().markDirty();
		}
	}

	public static void onBreak(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ensureDay(record);
		ensureWeek(record);
		record.dailyBroken++;
		record.weekBroken++;
		if (taskOn(s().dailyBreakOn) && !record.dailyBreakPaid && record.dailyBroken >= Math.max(1, s().dailyBreakGoal)) {
			record.dailyBreakPaid = true;
			pay(player, record, "ежедневка: блоки", "Ежедневка: сломано блоков. +" + Money.wc(Math.max(0, s().dailyBreakWc)) + ".",
					s().dailyBreakWc);
		}
		DataManager.get().markDirty();
	}

	public static void onKill(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ensureDay(record);
		ensureWeek(record);
		record.dailyKills++;
		record.weekKills++;
		if (taskOn(s().dailyKillOn) && !record.dailyKillPaid && record.dailyKills >= Math.max(1, s().dailyKillGoal)) {
			record.dailyKillPaid = true;
			pay(player, record, "ежедневка: мобы", "Ежедневка: убийства мобов. +" + Money.wc(Math.max(0, s().dailyKillWc)) + ".",
					s().dailyKillWc);
		}
		DataManager.get().markDirty();
	}

	public static void onPlace(ServerPlayerEntity player) {
		countTask(player, rec -> rec.dailyPlaced++, rec -> rec.dailyPlacePaid,
				(rec, v) -> rec.dailyPlacePaid = v, s().dailyPlaceOn, s().dailyPlaceGoal, s().dailyPlaceWc,
				"ежедневка: постройка", "Ежедневка: поставлено блоков.");
	}

	public static void onChat(ServerPlayerEntity player) {
		countTask(player, rec -> rec.dailyChat++, rec -> rec.dailyChatPaid,
				(rec, v) -> rec.dailyChatPaid = v, s().dailyChatOn, s().dailyChatGoal, s().dailyChatWc,
				"ежедневка: чат", "Ежедневка: сообщения в чат.");
	}

	public static void onCasino(ServerPlayerEntity player) {
		countTask(player, rec -> rec.dailyCasino++, rec -> rec.dailyCasinoPaid,
				(rec, v) -> rec.dailyCasinoPaid = v, s().dailyCasinoOn, s().dailyCasinoGoal, s().dailyCasinoWc,
				"ежедневка: казино", "Ежедневка: игры в казино.");
	}

	public static void onEat(ServerPlayerEntity player) {
		countTask(player, rec -> rec.dailyEat++, rec -> rec.dailyEatPaid,
				(rec, v) -> rec.dailyEatPaid = v, s().dailyEatOn, s().dailyEatGoal, s().dailyEatWc,
				"ежедневка: еда", "Ежедневка: приёмы пищи.");
	}

	public static void onPvpKill(ServerPlayerEntity player) {
		countTask(player, rec -> rec.dailyPvp++, rec -> rec.dailyPvpPaid,
				(rec, v) -> rec.dailyPvpPaid = v, s().dailyPvpOn, s().dailyPvpGoal, s().dailyPvpWc,
				"ежедневка: PvP", "Ежедневка: убийство игрока.");
	}

	public static void addWeekPlay(PlayerRecord record, long ms) {
		if (record == null || ms <= 0) {
			return;
		}
		ensureWeek(record);
		record.weekPlayMs += ms;
	}

	public static void addDayPlay(ServerPlayerEntity player, long ms) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player) || ms <= 0) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ensureDay(record);
		record.dailyPlayMs += ms;
		int minutes = (int) (record.dailyPlayMs / 60_000L);
		if (taskOn(s().dailyPlayOn) && !record.dailyPlayPaid && minutes >= Math.max(1, s().dailyPlayMin)) {
			record.dailyPlayPaid = true;
			pay(player, record, "ежедневка: онлайн", "Ежедневка: время в игре. +" + Money.wc(Math.max(0, s().dailyPlayWc)) + ".",
					s().dailyPlayWc);
		}
		DataManager.get().markDirty();
	}

	public static void fillStats(PlayerRecord record, List<String> labels, List<String> values) {
		if (record == null) {
			return;
		}
		ensureDay(record);
		ensureWeek(record);
		ModSettings s = s();
		if (s.dailyEnabled) {
			labels.add("Ежедневка: вход");
			values.add(!s.dailyJoinOn ? "выкл" : (record.dailyJoinPaid ? "получено" : "ждёт"));
			labels.add("Ежедневка: блоки");
			values.add(!s.dailyBreakOn ? "выкл" : (record.dailyBroken + "/" + Math.max(1, s.dailyBreakGoal)
					+ (record.dailyBreakPaid ? " ✓" : "")));
			labels.add("Ежедневка: мобы");
			values.add(!s.dailyKillOn ? "выкл" : (record.dailyKills + "/" + Math.max(1, s.dailyKillGoal)
					+ (record.dailyKillPaid ? " ✓" : "")));
			labels.add("Ежедневка: постройка");
			values.add(!s.dailyPlaceOn ? "выкл" : (record.dailyPlaced + "/" + Math.max(1, s.dailyPlaceGoal)
					+ (record.dailyPlacePaid ? " ✓" : "")));
			labels.add("Ежедневка: онлайн");
			values.add(!s.dailyPlayOn ? "выкл" : ((int) (record.dailyPlayMs / 60_000L) + "/" + Math.max(1, s.dailyPlayMin)
					+ " мин" + (record.dailyPlayPaid ? " ✓" : "")));
			labels.add("Ежедневка: чат");
			values.add(!s.dailyChatOn ? "выкл" : (record.dailyChat + "/" + Math.max(1, s.dailyChatGoal)
					+ (record.dailyChatPaid ? " ✓" : "")));
			labels.add("Ежедневка: казино");
			values.add(!s.dailyCasinoOn ? "выкл" : (record.dailyCasino + "/" + Math.max(1, s.dailyCasinoGoal)
					+ (record.dailyCasinoPaid ? " ✓" : "")));
			labels.add("Ежедневка: еда");
			values.add(!s.dailyEatOn ? "выкл" : (record.dailyEat + "/" + Math.max(1, s.dailyEatGoal)
					+ (record.dailyEatPaid ? " ✓" : "")));
			labels.add("Ежедневка: PvP");
			values.add(!s.dailyPvpOn ? "выкл" : (record.dailyPvp + "/" + Math.max(1, s.dailyPvpGoal)
					+ (record.dailyPvpPaid ? " ✓" : "")));
		}
		if (s.weeklyTopEnabled) {
			labels.add("Неделя: очки");
			values.add(String.valueOf(score(record)));
		}
		labels.add("Клан");
		if (record.clanId != null && !record.clanId.isBlank()) {
			String name = ClanService.nameOf(record.clanId);
			values.add(name == null || name.isBlank() || "—".equals(name) ? "нет" : name);
		} else {
			values.add("нет");
		}
	}

	public static List<String> topLines() {
		List<String> lines = new ArrayList<>();
		if (!s().weeklyTopEnabled) {
			lines.add("Недельный топ выключен в /aserver.");
			return lines;
		}
		ensureAllWeeks();
		List<PlayerRecord> ranked = ranked();
		lines.add("Неделя " + weekKey() + "  (1 сломанный блок = 1, 1 убийство моба = 3, 1 минута онлайн = 1)");
		lines.add("Награда 1 места: " + Money.wc(Math.max(0, s().weeklyTopWc)));
		lines.add("");
		int n = 0;
		for (PlayerRecord record : ranked) {
			n++;
			lines.add(n + ". " + record.nickname + "  —  " + score(record));
			if (n >= 10) {
				break;
			}
		}
		if (n == 0) {
			lines.add("Пока никого нет.");
		}
		return lines;
	}

	public static boolean buy(ServerPlayerEntity player, String id) {
		if (player == null || DataManager.get() == null) {
			return false;
		}
		if (!s().shopEnabled) {
			Chat.send(player, Chat.red("Магазин выключен."));
			return false;
		}
		PlayerRecord record = DataManager.get().progress(player);
		int price;
		String reason;
		if (id != null && id.startsWith("private")) {
			int level;
			try {
				level = Integer.parseInt(id.substring("private".length()));
			} catch (NumberFormatException e) {
				return false;
			}
			if (level < 1 || level > PrivateService.LEVELS) {
				return false;
			}
			price = s().privatePrice(level);
			if (!Economy.takeCurrency(player, price, "магазин: приват ур." + level)) {
				Chat.send(player, Chat.red("Нужно " + Money.wc(price) + "."));
				return false;
			}
			net.minecraft.item.ItemStack stack = PrivateService.item(level);
			if (!player.getInventory().insertStack(stack)) {
				player.dropItem(stack, false);
			}
			player.playerScreenHandler.sendContentUpdates();
			DataManager.get().markDirty();
			AdminNotify.playerAction(player, ActionKind.MONEY, "купил блок привата " + PrivateService.levelLabel(level)
					+ " за " + Money.wc(price), true);
			Chat.send(player, Chat.green("Блок привата " + PrivateService.levelLabel(level) + " куплен за "
					+ Money.wc(price) + ". Поставьте его, чтобы создать приват."));
			return true;
		}
		switch (id) {
			case "home" -> {
				price = Math.max(0, s().shopHomePrice);
				if (record.extraHomeSlots >= 3) {
					Chat.send(player, Chat.red("Максимум дополнительных домов куплен."));
					return false;
				}
				reason = "магазин: слот дома";
				if (!Economy.takeCurrency(player, price, reason)) {
					Chat.send(player, Chat.red("Нужно " + Money.wc(price) + "."));
					return false;
				}
				record.extraHomeSlots++;
				DataManager.get().markDirty();
				Chat.send(player, Chat.green("Куплен слот дома (" + (1 + record.extraHomeSlots)
						+ "). Поставьте /addhome."));
				return true;
			}
			case "luck" -> {
				price = Math.max(0, s().shopLuckPrice);
				reason = "магазин: удача";
				if (!Economy.takeCurrency(player, price, reason)) {
					Chat.send(player, Chat.red("Нужно " + Money.wc(price) + "."));
					return false;
				}
				double from = record.luck();
				record.luck = Math.round((from + 0.1) * 10.0) / 10.0;
				DataManager.get().markDirty();
				ProgressLog.luck(record, from, record.luck(), ProgressLog.SHOP);
				Chat.send(player, Chat.green("Удача +" + String.format(Locale.US, "%.1f", record.luck())
						+ " за " + Money.wc(price) + "."));
				return true;
			}
			case "power" -> {
				price = Math.max(0, s().shopPowerPrice);
				reason = "магазин: сила";
				if (!Economy.takeCurrency(player, price, reason)) {
					Chat.send(player, Chat.red("Нужно " + Money.wc(price) + "."));
					return false;
				}
				double from = record.power();
				record.addEventPower(0.1);
				DataManager.get().markDirty();
				ProgressLog.power(record, from, record.power(), ProgressLog.SHOP);
				Chat.send(player, Chat.green("Сила " + String.format(Locale.US, "%.1f", record.power())
						+ " за " + Money.wc(price) + "."));
				return true;
			}
			case "backpack", "workbench", "furnace" -> {
				Chat.send(player, Chat.red("Рюкзак, верстак и автопечь теперь в дереве навыков: /skills, ветка «Добыча и ремесло»."));
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	public static int homeLimit(PlayerRecord record) {
		return 1 + Math.max(0, record == null ? 0 : record.extraHomeSlots);
	}

	public static int homesUsed(PlayerRecord record) {
		if (record == null) {
			return 0;
		}
		int n = record.home == null ? 0 : 1;
		if (record.extraHomes != null) {
			n += record.extraHomes.size();
		}
		return n;
	}

	/** Сброс дневных счётчиков при смене суток (в т.ч. лимит тренировок). */
	public static void touchDay(PlayerRecord record) {
		ensureDay(record);
	}

	private static void ensureDay(PlayerRecord record) {
		if (record == null) {
			return;
		}
		String key = dayKey();
		if (key.equals(record.dailyKey)) {
			return;
		}
		record.dailyKey = key;
		record.dailyBroken = 0;
		record.dailyKills = 0;
		record.dailyJoinPaid = false;
		record.dailyBreakPaid = false;
		record.dailyKillPaid = false;
		record.dailyPlaced = 0;
		record.dailyPlacePaid = false;
		record.dailyPlayMs = 0;
		record.dailyPlayPaid = false;
		record.dailyChat = 0;
		record.dailyChatPaid = false;
		record.dailyCasino = 0;
		record.dailyCasinoPaid = false;
		record.dailyEat = 0;
		record.dailyEatPaid = false;
		record.dailyPvp = 0;
		record.dailyPvpPaid = false;
		record.dailyTrainings = 0;
	}

	private static boolean taskOn(boolean flag) {
		return s().dailyEnabled && flag;
	}

	private static void pay(ServerPlayerEntity player, PlayerRecord record, String reason, String message, int coins) {
		int wc = Math.max(0, coins);
		if (wc > 0 && player != null) {
			Economy.giveCurrency(player, wc, reason);
			Chat.send(player, Chat.green(message.contains("+") ? message : (message + " +" + Money.wc(wc) + ".")));
		}
	}

	private static void countTask(ServerPlayerEntity player, java.util.function.Consumer<PlayerRecord> bump,
			java.util.function.Predicate<PlayerRecord> paid, java.util.function.BiConsumer<PlayerRecord, Boolean> setPaid,
			boolean enabled, int goal, int wc, String reason, String prefix) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		ensureDay(record);
		bump.accept(record);
		if (taskOn(enabled) && !paid.test(record)) {
			int progress = switch (reason) {
				case "ежедневка: постройка" -> record.dailyPlaced;
				case "ежедневка: чат" -> record.dailyChat;
				case "ежедневка: казино" -> record.dailyCasino;
				case "ежедневка: еда" -> record.dailyEat;
				case "ежедневка: PvP" -> record.dailyPvp;
				default -> 0;
			};
			if (progress >= Math.max(1, goal)) {
				setPaid.accept(record, true);
				pay(player, record, reason, prefix, wc);
			}
		}
		DataManager.get().markDirty();
	}

	private static void ensureWeek(PlayerRecord record) {
		if (record == null) {
			return;
		}
		String key = weekKey();
		if (key.equals(record.weekKey)) {
			return;
		}
		record.weekKey = key;
		record.weekBroken = 0;
		record.weekKills = 0;
		record.weekPlayMs = 0;
	}

	private static void ensureAllWeeks() {
		if (DataManager.get() == null) {
			return;
		}
		for (PlayerRecord record : DataManager.get().records()) {
			ensureWeek(record);
		}
	}

	private static int score(PlayerRecord record) {
		if (record == null) {
			return 0;
		}
		return record.weekBroken + record.weekKills * 3 + (int) (Math.max(0L, record.weekPlayMs) / 60_000L);
	}

	private static List<PlayerRecord> ranked() {
		List<PlayerRecord> list = new ArrayList<>();
		if (DataManager.get() == null) {
			return list;
		}
		String key = weekKey();
		for (PlayerRecord record : DataManager.get().records()) {
			if (record == null || record.nps || !key.equals(record.weekKey)) {
				continue;
			}
			if (score(record) <= 0) {
				continue;
			}
			list.add(record);
		}
		list.sort(Comparator.comparingInt(PlayFeatures::score).reversed());
		return list;
	}

	private static void rolloverWeek(MinecraftServer server) {
		ModSettings s = s();
		String now = weekKey();
		if (s.weeklyKey == null || s.weeklyKey.isBlank()) {
			s.weeklyKey = now;
			return;
		}
		if (now.equals(s.weeklyKey)) {
			return;
		}
		if (s.weeklyTopEnabled) {
			List<PlayerRecord> old = new ArrayList<>();
			for (PlayerRecord record : DataManager.get().records()) {
				if (record != null && !record.nps && s.weeklyKey.equals(record.weekKey) && score(record) > 0) {
					old.add(record);
				}
			}
			old.sort(Comparator.comparingInt(PlayFeatures::score).reversed());
			int reward = Math.max(0, s.weeklyTopWc);
			if (!old.isEmpty() && reward > 0) {
				PlayerRecord winner = old.get(0);
				Economy.addCoins(winner, reward, "недельный топ");
				Chat.broadcast(server, Chat.gold("Недельный топ: " + winner.nickname + " (" + score(winner)
						+ "). Награда: " + Money.wc(reward) + "."));
			}
		}
		s.weeklyKey = now;
		for (PlayerRecord record : DataManager.get().records()) {
			ensureWeek(record);
		}
		DataManager.get().markDirty();
	}
}
