package ru.admincontrol.cw;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.util.TpGuard;
import ru.admincontrol.util.WorldGate;

/**
 * The only way CW code moves a player between worlds. It loads the destination chunk completely before the player
 * arrives, leaves vehicles and screens, performs the vanilla dimension change under the mod's travel gates and then
 * verifies that the player really ended up registered in the destination world. Any failure is returned as text (and
 * logged with the transition id) instead of leaving the player half-moved.
 */
public final class CwSafeTeleport {
	private CwSafeTeleport() {}

	/** Returns null on success, otherwise a short reason. */
	public static String move(ServerPlayerEntity p, ServerWorld world, double x, double y, double z, float yaw, float pitch, String transitionId) {
		if (p == null || world == null) return "destination or player is missing";
		if (p.isRemoved() || p.networkHandler == null) return "player is not connected";
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return "non-finite coordinates";
		try {
			BlockPos target = BlockPos.ofFloored(x, y, z);
			int targetChunkX = target.getX() >> 4, targetChunkZ = target.getZ() >> 4;
			// Vanilla teleport adds POST_TELEPORT for the destination. Load the immediate view first as well: appearing on
			// a chunk edge used to leave the neighbour behind/in front temporarily absent on slower disks.
			for (int cx = targetChunkX - 1; cx <= targetChunkX + 1; cx++) {
				for (int cz = targetChunkZ - 1; cz <= targetChunkZ + 1; cz++) world.getChunk(cx, cz);
			}
			Identifier fromWorld = p.getServerWorld().getRegistryKey().getValue();
			int fromChunkX = p.getBlockX() >> 4, fromChunkZ = p.getBlockZ() >> 4;
			// Точка прибытия внутри блока (игрок застроил её) приводила к срабатыванию «Внутри блока»: поднимаем на самую верхнюю опору.
			net.minecraft.util.math.BlockPos feet = BlockPos.ofFloored(x, y, z);
			if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty() || !world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()) {
				y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, feet.getX(), feet.getZ());
			}
			final double fy = y;
			p.stopRiding();
			TpGuard.bypass(() -> WorldGate.allow(() -> p.teleport(world, x, fy, z, yaw, pitch)));
			AdminControlMod.LOGGER.info("[CW] teleport transition={} player={} from={} chunk={},{} to={} chunk={},{} pos={},{},{}",
					transitionId, p.getUuidAsString(), fromWorld, fromChunkX, fromChunkZ, world.getRegistryKey().getValue(),
					targetChunkX, targetChunkZ, x, y, z);
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.error("[CW] teleport failed transition={} player={} to={} {} {} {}", transitionId, p.getUuidAsString(), world.getRegistryKey().getValue(), x, y, z, e);
			return "teleport failed: " + e.getClass().getSimpleName();
		}
		if (p.getServerWorld() != world) {
			AdminControlMod.LOGGER.error("[CW] teleport did not change world transition={} player={} wanted={} actual={}", transitionId, p.getUuidAsString(),
					world.getRegistryKey().getValue(), p.getServerWorld().getRegistryKey().getValue());
			return "teleport was refused";
		}
		p.fallDistance = 0;
		return null;
	}
}
