package ru.admincontrol.util;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/**
 * Прогрев чанков назначения на время ожидания телепорта: к моменту прыжка чанки уже загружены и сервер не лагает.
 * Билет сам истекает через {@link #TTL_TICKS}.
 */
public final class ChunkPrewarm {
	private static final int TTL_TICKS = 20 * 40;
	private static final ChunkTicketType<Long> TICKET = ChunkTicketType.create("admin_control_prewarm", Long::compareTo, TTL_TICKS);

	private ChunkPrewarm() {
	}

	public static void holdPoint(net.minecraft.server.MinecraftServer server, ru.admincontrol.data.PlayerWaypoint point) {
		if (server != null && point != null) {
			hold(SpawnService.resolveWorld(server, point.dimension), point.x, point.z, 3);
		}
	}

	public static void holdPlayer(net.minecraft.server.network.ServerPlayerEntity target) {
		if (target != null && target.getWorld() instanceof ServerWorld w) {
			hold(w, target.getX(), target.getZ(), 3);
		}
	}

	/** Держит прогруженными чанки вокруг точки (radius — в чанках, 1–4). */
	public static void hold(ServerWorld world, double x, double z, int radius) {
		if (world == null) {
			return;
		}
		ChunkPos center = new ChunkPos(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
		int r = Math.max(1, Math.min(4, radius));
		try {
			world.getChunkManager().addTicket(TICKET, center, r + 1, center.toLong());
		} catch (RuntimeException e) {
			ru.admincontrol.AdminControlMod.LOGGER.warn("Не удалось прогреть чанки", e);
		}
	}
}
