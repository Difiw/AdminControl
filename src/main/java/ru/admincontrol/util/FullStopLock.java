package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

/** После /fullstop — пауза на старт МП и тренировок. Длительность в /dserver. */
public final class FullStopLock {
	private static long untilMs;

	private FullStopLock() {
	}

	public static void arm() {
		int sec = cooldownSec();
		if (sec == 0) {
			untilMs = 0L;
			return;
		}
		if (sec < 0) {
			untilMs = Long.MAX_VALUE;
			return;
		}
		untilMs = System.currentTimeMillis() + sec * 1000L;
	}

	public static boolean blocked() {
		if (untilMs <= 0L) {
			return false;
		}
		if (untilMs == Long.MAX_VALUE) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now >= untilMs) {
			untilMs = 0L;
			return false;
		}
		return true;
	}

	public static String denyMessage() {
		if (!blocked()) {
			return "";
		}
		if (untilMs == Long.MAX_VALUE) {
			return "После /fullstop МП и тренировки заблокированы (пауза ∞). Снимите в /dserver.";
		}
		long left = Math.max(1L, (untilMs - System.currentTimeMillis() + 999L) / 1000L);
		return "После /fullstop пауза ещё " + formatSec((int) Math.min(Integer.MAX_VALUE, left)) + ".";
	}

	public static boolean deny(ServerPlayerEntity player) {
		if (!blocked()) {
			return false;
		}
		if (player != null) {
			Chat.send(player, Chat.red(denyMessage()));
		}
		return true;
	}

	public static int cooldownSec() {
		if (DataManager.get() == null) {
			return 90;
		}
		return DataManager.get().settings().fullStopCooldownSec;
	}

	public static void setCooldownSec(int sec) {
		if (DataManager.get() == null) {
			return;
		}
		ModSettings s = DataManager.get().settings();
		if (sec < 0) {
			s.fullStopCooldownSec = -1;
		} else {
			s.fullStopCooldownSec = Math.min(Integer.MAX_VALUE / 4, sec);
		}
		DataManager.get().saveAll();
		if (s.fullStopCooldownSec == 0) {
			untilMs = 0L;
		} else if (s.fullStopCooldownSec < 0 && untilMs > 0L) {
			untilMs = Long.MAX_VALUE;
		}
	}

	public static void shiftCooldown(int delta) {
		int cur = cooldownSec();
		if (delta < 0) {
			if (cur < 0) {
				setCooldownSec(3600);
				return;
			}
			setCooldownSec(Math.max(0, cur + delta));
			return;
		}
		if (cur < 0) {
			return;
		}
		long next = (long) cur + delta;
		if (next >= 86_400L) {
			setCooldownSec(-1);
			return;
		}
		setCooldownSec((int) next);
	}

	public static String formatSec(int sec) {
		if (sec < 0) {
			return "∞";
		}
		if (sec == 0) {
			return "0 сек";
		}
		int m = sec / 60;
		int s = sec % 60;
		if (m > 0 && s == 0) {
			return m + " мин";
		}
		if (m > 0) {
			return m + "м " + s + "с";
		}
		return s + " сек";
	}

	public static String settingLabel() {
		return "Пауза после /fullstop: " + formatSec(cooldownSec());
	}
}
