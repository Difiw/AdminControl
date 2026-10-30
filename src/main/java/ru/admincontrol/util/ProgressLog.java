package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.Locale;

/** Логи изменений силы и удачи с указанием способа. */
public final class ProgressLog {
	private ProgressLog() {
	}

	public static final String EVENT = "МП";
	public static final String TRAINING = "тренировка";
	public static final String SHOP = "через магазин /shop";
	public static final String COMMAND = "через команду";
	public static final String ADMIN = "изменил админ";
	public static final String CASINO = "казино";

	public static void power(PlayerRecord record, double from, double to, String how) {
		log(record, ActionKind.POWER, "сила", from, to, how);
	}

	public static void luck(PlayerRecord record, double from, double to, String how) {
		log(record, ActionKind.LUCK, "удача", from, to, how);
	}

	public static void power(ServerPlayerEntity player, double from, double to, String how) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		power(DataManager.get().getOrCreate(player), from, to, how);
	}

	public static void luck(ServerPlayerEntity player, double from, double to, String how) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		luck(DataManager.get().getOrCreate(player), from, to, how);
	}

	private static void log(PlayerRecord record, ActionKind kind, String stat, double from, double to, String how) {
		if (record == null || kind == null) {
			return;
		}
		double a = Math.round(from * 10.0) / 10.0;
		double b = Math.round(to * 10.0) / 10.0;
		if (Math.abs(a - b) < 0.05) {
			return;
		}
		String nick = record.nickname == null ? "?" : record.nickname;
		String text = stat + " " + fmt(a) + " → " + fmt(b) + " (" + (how == null || how.isBlank() ? COMMAND : how) + ")";
		ActivityLog.player(nick, kind.label + ": " + text, true);
	}

	private static String fmt(double v) {
		return String.format(Locale.US, "%.1f", v);
	}
}
