package ru.admincontrol.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Частицы портала и звук в точке ухода и прибытия при телепортах мода (ивенты, тренировки, дом, спавн). */
public final class TeleportFx {
	private static final double MIN_DISTANCE = 8;

	private TeleportFx() {
	}

	public static void play(ServerPlayerEntity player, ServerWorld to, double x, double y, double z) {
		if (player == null || to == null || player.isSpectator() || player.isInvisible()
				|| NpcService.controlledNpc(player) != null) {
			return;
		}
		ServerWorld from = player.getServerWorld();
		if (from == to && player.squaredDistanceTo(x, y, z) < MIN_DISTANCE * MIN_DISTANCE) {
			return;
		}
		burst(from, player.getX(), player.getY(), player.getZ(), ParticleTypes.PORTAL, 0.9f);
		burst(to, x, y, z, ParticleTypes.REVERSE_PORTAL, 1.25f);
	}

	private static void burst(ServerWorld world, double x, double y, double z, ParticleEffect type, float pitch) {
		world.spawnParticles(type, x, y + 1, z, 50, 0.35, 0.9, 0.35, 0.45);
		world.spawnParticles(ParticleTypes.END_ROD, x, y + 1, z, 10, 0.3, 0.7, 0.3, 0.04);
		for (int i = 0; i < 16; i++) {
			double q = i * Math.PI * 2 / 16;
			world.spawnParticles(ParticleTypes.WITCH, x + Math.cos(q) * 0.8, y + 0.1, z + Math.sin(q) * 0.8, 1, 0, 0, 0, 0);
		}
		world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5f, pitch);
	}
}
