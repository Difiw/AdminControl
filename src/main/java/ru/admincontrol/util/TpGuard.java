package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;

import java.util.Locale;
import java.util.Set;

/**
 * Иерархия телепортов: нижестоящий не попадает к вышестоящему, игрок — только к админу 1 уровня.
 * Защитный радиус 100 блоков вокруг администратора, к которому нельзя телепортироваться.
 */
public final class TpGuard {
	public static final double RADIUS = 100.0;
	private static final Set<String> GUARDED = Set.of(
			"tp", "teleport", "tpi", "ttp", "home", "thome", "g", "sg", "gh", "sgh",
			"tpy", "tpaccept", "ok", "execute", "clan");
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<String> ROOT = new ThreadLocal<>();
	private static final ThreadLocal<ServerPlayerEntity> INITIATOR = new ThreadLocal<>();
	private static final ThreadLocal<Integer> BYPASS = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Integer> FORCED = ThreadLocal.withInitial(() -> 0);

	private TpGuard() {
	}

	private static int level(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		return player == null || data == null ? AdminRanks.NONE : data.getAdminLevel(player);
	}

	/** Может ли участник с уровнем mover телепортироваться к админу уровня admin. */
	public static boolean canReach(int mover, int admin) {
		if (admin < AdminRanks.MOD) {
			return true;
		}
		if (mover < AdminRanks.MOD) {
			return admin < AdminRanks.SMOD;
		}
		return mover >= admin;
	}

	public static boolean canReach(ServerPlayerEntity mover, ServerPlayerEntity admin) {
		if (mover == null || admin == null || mover.getUuid().equals(admin.getUuid())) {
			return true;
		}
		return canReach(level(mover), level(admin));
	}

	public static void enterCommand(ServerCommandSource source, String raw) {
		int depth = DEPTH.get();
		DEPTH.set(depth + 1);
		if (depth > 0) {
			return;
		}
		String text = raw == null ? "" : raw.trim();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		String name = text.isEmpty() ? "" : text.split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (name.contains(":")) {
			name = name.substring(name.indexOf(':') + 1);
		}
		ROOT.set(name);
		INITIATOR.set(source != null && source.getEntity() instanceof ServerPlayerEntity p ? p : null);
	}

	public static void exitCommand() {
		int depth = Math.max(0, DEPTH.get() - 1);
		DEPTH.set(depth);
		if (depth == 0) {
			ROOT.remove();
			INITIATOR.remove();
		}
	}

	public static void bypass(Runnable action) {
		BYPASS.set(BYPASS.get() + 1);
		try {
			action.run();
		} finally {
			BYPASS.set(BYPASS.get() - 1);
		}
	}

	/** Телепорт вне команды, который тоже должен проверяться (принятие /tpa, дом из меню). */
	public static void guarded(Runnable action) {
		FORCED.set(FORCED.get() + 1);
		try {
			action.run();
		} finally {
			FORCED.set(FORCED.get() - 1);
		}
	}

	private static boolean active() {
		if (BYPASS.get() > 0) {
			return false;
		}
		if (FORCED.get() > 0) {
			return true;
		}
		String root = ROOT.get();
		return root != null && GUARDED.contains(root);
	}

	/**
	 * true — телепорт отменён (игрок остаётся на прежних координатах).
	 */
	public static boolean blocks(ServerPlayerEntity mover, ServerWorld world, double x, double y, double z) {
		if (mover == null || world == null || mover.getServer() == null || !active()) {
			return false;
		}
		if (NpcService.isNpc(mover) || AuthService.pending(mover) || SpectateService.isSpectating(mover)
				|| EventService.member(mover) || TrainingService.active(mover) || FreezeService.frozen(mover)) {
			return false;
		}
		if (mover.getWorld() == world) {
			double dx = x - mover.getX();
			double dy = y - mover.getY();
			double dz = z - mover.getZ();
			if (dx * dx + dy * dy + dz * dz < 100.0) {
				return false;
			}
		}
		int moverLevel = level(mover);
		ServerPlayerEntity initiator = FORCED.get() > 0 && ROOT.get() == null ? null : INITIATOR.get();
		int initLevel = initiator == null || initiator == mover ? -1 : level(initiator);
		for (ServerPlayerEntity admin : mover.getServer().getPlayerManager().getPlayerList()) {
			if (admin == mover || admin.getWorld() != world || NpcService.isNpc(admin)) {
				continue;
			}
			int adminLevel = level(admin);
			if (canReach(moverLevel, adminLevel) || (initLevel >= 0 && initLevel >= adminLevel)) {
				continue;
			}
			double dx = admin.getX() - x;
			double dz = admin.getZ() - z;
			double dy = admin.getY() - y;
			if (dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS) {
				String msg = "Телепорт отменён: точка в радиусе " + (int) RADIUS
						+ " блоков от администратора более высокого уровня.";
				Chat.send(mover, Chat.red(msg));
				if (initiator != null && initiator != mover) {
					Chat.send(initiator, Chat.red(msg));
				}
				return true;
			}
		}
		return false;
	}
}
