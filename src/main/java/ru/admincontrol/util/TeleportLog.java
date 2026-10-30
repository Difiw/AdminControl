package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportLog {
	private static final ThreadLocal<Boolean> SILENT = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<String> LABEL = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> REPORTED = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();

	private TeleportLog() {
	}

	private static final class Last {
		double x;
		double y;
		double z;
		long at;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			LAST.remove(player.getUuid());
		}
	}

	public static void remember(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		Last last = LAST.computeIfAbsent(player.getUuid(), u -> new Last());
		last.x = player.getX();
		last.y = player.getY();
		last.z = player.getZ();
		last.at = System.currentTimeMillis();
	}

	public static boolean silent() {
		return Boolean.TRUE.equals(SILENT.get());
	}

	public static void silent(Runnable action) {
		boolean prev = silent();
		SILENT.set(true);
		try {
			action.run();
		} finally {
			SILENT.set(prev);
			if (action != null) {
				// keep
			}
		}
	}

	public static void labeled(String label, Runnable action) {
		String prev = LABEL.get();
		boolean prevRep = Boolean.TRUE.equals(REPORTED.get());
		LABEL.set(label);
		REPORTED.set(false);
		try {
			action.run();
		} finally {
			if (prev == null) {
				LABEL.remove();
			} else {
				LABEL.set(prev);
			}
			REPORTED.set(prevRep);
		}
	}

	public static void reportCommand(ServerPlayerEntity player, String command) {
	}

	public static void beginCommand(String command) {
		DEPTH.set(DEPTH.get() + 1);
	}

	public static void finishCommand(ServerPlayerEntity player, boolean ok) {
		int depth = DEPTH.get();
		DEPTH.set(Math.max(0, depth - 1));
		if (player != null) {
			remember(player);
		}
	}

	public static void onTeleport(ServerPlayerEntity player, double x, double y, double z) {
		if (player == null || silent()) {
			return;
		}
		if (AuthService.pending(player) || SpectateService.isSpectating(player)) {
			return;
		}
		String label = LABEL.get();
		if (label != null && !label.isBlank()) {
			if (!Boolean.TRUE.equals(REPORTED.get())) {
				REPORTED.set(true);
				AdminNotify.playerAction(player, ActionKind.TELEPORT, label);
			}
			remember(player);
			return;
		}
		remember(player);
	}

	public static boolean onClientJump(ServerPlayerEntity player, double x, double y, double z) {
		if (player == null || silent() || AuthService.pending(player) || SpectateService.isSpectating(player)) {
			return true;
		}
		if (TrainingService.active(player)) {
			return true;
		}
		if (EventService.current() != null && EventService.current().members.containsKey(player.getUuid())) {
			return true;
		}
		if (LABEL.get() != null && !LABEL.get().isBlank()) {
			onTeleport(player, x, y, z);
			return true;
		}
		double dx = x - player.getX();
		double dy = y - player.getY();
		double dz = z - player.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 20) {
			return true;
		}
		boolean allowed = DataManager.get() != null && DataManager.get().settings().playerCheats;
		String where = (int) x + " " + (int) y + " " + (int) z;
		if (!allowed) {
			AdminNotify.playerAction(player, ActionKind.TELEPORT,
					"попытка ТП по карте/метке на " + where + " отменена (/pmode false)", false);
			return false;
		}
		AdminNotify.playerAction(player, ActionKind.TELEPORT, "телепортировался по карте/метке на " + where, true);
		return true;
	}
}
