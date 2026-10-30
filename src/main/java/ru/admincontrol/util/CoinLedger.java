package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CoinLedger {
	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

	private CoinLedger() {
	}

	public static void credit(ServerPlayerEntity player, int amount, String reason) {
		if (player == null) {
			return;
		}
		write(player, player.getGameProfile().getName(), amount, reason, true);
	}

	public static void debit(ServerPlayerEntity player, int amount, String reason) {
		if (player == null) {
			return;
		}
		write(player, player.getGameProfile().getName(), amount, reason, false);
	}

	public static void credit(PlayerRecord record, int amount, String reason) {
		if (record == null) {
			return;
		}
		write(null, record.nickname, amount, reason, true);
	}

	public static void debit(PlayerRecord record, int amount, String reason) {
		if (record == null) {
			return;
		}
		write(null, record.nickname, amount, reason, false);
	}

	public static List<ActivityLog.Line> history(String nick) {
		List<ActivityLog.Line> lines = read(nick);
		Collections.reverse(lines);
		return lines;
	}

	public static List<String> transferTargets(ServerPlayerEntity from) {
		DataManager data = DataManager.get();
		List<String> nicks = new ArrayList<>();
		if (data == null) {
			return nicks;
		}
		String self = from == null ? "" : from.getGameProfile().getName();
		for (PlayerRecord record : data.records()) {
			if (record == null || record.nps || record.nickname == null || record.nickname.isBlank()) {
				continue;
			}
			if (record.nickname.equalsIgnoreCase(self)) {
				continue;
			}
			nicks.add(record.nickname);
		}
		nicks.sort(Comparator.comparing(String::toLowerCase));
		if (from != null && from.getServer() != null) {
			nicks.sort((a, b) -> {
				boolean ao = CommandUtil.findOnline(from.getServer(), a).isPresent();
				boolean bo = CommandUtil.findOnline(from.getServer(), b).isPresent();
				if (ao != bo) {
					return ao ? -1 : 1;
				}
				return a.compareToIgnoreCase(b);
			});
		}
		if (nicks.size() > 1500) {
			return new ArrayList<>(nicks.subList(0, 1500));
		}
		return nicks;
	}

	private static void write(ServerPlayerEntity player, String nick, int amount, String reason, boolean income) {
		if (amount <= 0 || nick == null || nick.isBlank() || reason == null || reason.isBlank()) {
			return;
		}
		if (player != null && NpcService.isIdleNpc(player)) {
			return;
		}
		applyTotals(nick, amount, income, reason);
		String sign = income ? "+" : "−";
		int bal = 0;
		if (DataManager.get() != null) {
			PlayerRecord rec = DataManager.get().findByNick(nick).orElse(null);
			if (rec != null) {
				bal = Math.max(0, rec.coins);
			}
		}
		String text = sign + Money.format(amount) + " WC — " + reason + " → баланс " + Money.wc(bal);
		if (player != null) {
			AdminNotify.playerAction(player, ActionKind.MONEY, text, true);
		} else {
			ActivityLog.player(nick, ActionKind.MONEY.label + ": " + text, true);
		}
		append(nick, text, income);
	}

	static void noteUnlogged(PlayerRecord record, int amount, boolean income) {
		if (record == null || amount <= 0 || record.nps) {
			return;
		}
		ensureTotals(record);
		addTotal(record, amount, income);
		if (DataManager.get() != null) {
			DataManager.get().markDirty();
		}
	}

	public static int earned(PlayerRecord record) {
		ensureTotals(record);
		return record == null ? 0 : Math.max(0, record.coinsEarned);
	}

	public static int spent(PlayerRecord record) {
		ensureTotals(record);
		return record == null ? 0 : Math.max(0, record.coinsSpent);
	}

	public static void ensureTotals(PlayerRecord record) {
		if (record == null || record.nps || record.coinStatsReady) {
			return;
		}
		record.coinStatsReady = true;
		if (record.coinsEarned != 0 || record.coinsSpent != 0) {
			if (DataManager.get() != null) {
				DataManager.get().markDirty();
			}
			return;
		}
		for (ActivityLog.Line line : read(record.nickname)) {
			Parsed parsed = parseAmount(line == null ? null : line.text);
			if (parsed != null) {
				addTotal(record, parsed.amount, parsed.income);
			}
		}
		if (DataManager.get() != null) {
			DataManager.get().markDirty();
		}
	}

	private static void applyTotals(String nick, int amount, boolean income, String reason) {
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		PlayerRecord record = data.findByNick(nick).orElse(null);
		if (record == null || record.nps) {
			return;
		}
		ensureTotals(record);
		String why = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
		if (why.contains("обнуление аккаунта")) {
			return;
		}
		addTotal(record, amount, income);
		data.markDirty();
	}

	private static void addTotal(PlayerRecord record, int amount, boolean income) {
		if (income) {
			record.coinsEarned = saturatingAdd(record.coinsEarned, amount);
		} else {
			record.coinsSpent = saturatingAdd(record.coinsSpent, amount);
		}
	}

	private static int saturatingAdd(int a, int b) {
		long sum = (long) Math.max(0, a) + Math.max(0, b);
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}

	private static final class Parsed {
		final int amount;
		final boolean income;

		Parsed(int amount, boolean income) {
			this.amount = amount;
			this.income = income;
		}
	}

	private static Parsed parseAmount(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		int wc = text.indexOf(" WC");
		if (wc <= 0) {
			return null;
		}
		int start = wc - 1;
		while (start >= 0) {
			char ch = text.charAt(start);
			if (Character.isDigit(ch) || ch == '.') {
				start--;
				continue;
			}
			break;
		}
		if (start + 1 >= wc) {
			return null;
		}
		int amount;
		try {
			amount = Integer.parseInt(text.substring(start + 1, wc).replace(".", ""));
		} catch (NumberFormatException e) {
			return null;
		}
		if (amount <= 0) {
			return null;
		}
		char sign = start >= 0 ? text.charAt(start) : '+';
		boolean income = sign != '−' && sign != '-';
		return new Parsed(amount, income);
	}

	private static void append(String nick, String text, boolean ok) {
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		Path file = file(data, nick);
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			String line = "[" + FORMAT.format(Instant.now()) + "] " + text
					+ (ok ? ActivityLog.OK_MARK : ActivityLog.FAIL_MARK) + System.lineSeparator();
			Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			ActionLog.error("Не удалось записать историю WC " + file, e);
		}
	}

	private static List<ActivityLog.Line> read(String nick) {
		DataManager data = DataManager.get();
		List<ActivityLog.Line> empty = new ArrayList<>();
		if (data == null || nick == null) {
			return empty;
		}
		Path file = file(data, nick);
		if (file == null || !Files.exists(file)) {
			return empty;
		}
		try {
			List<ActivityLog.Line> out = new ArrayList<>();
			for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				out.add(ActivityLog.parsePublic(raw));
			}
			return out;
		} catch (IOException e) {
			return empty;
		}
	}

	private static Path file(DataManager data, String nick) {
		String safe = nick.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return data.getConfigDir().resolve("logs").resolve("coins_" + safe + ".log");
	}
}
