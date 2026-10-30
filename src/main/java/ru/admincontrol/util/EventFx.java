package ru.admincontrol.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Звук и частицы к титрам мероприятий: отсчёт, старт, выбывание, прогресс, победа. */
public final class EventFx {
	private EventFx() {
	}

	public static boolean isCountdown(String big) {
		return big != null && !big.isEmpty() && big.chars().allMatch(Character::isDigit);
	}

	public static void onTitle(ServerPlayerEntity player, String big, String small) {
		if (player == null || big == null) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		if (isCountdown(big)) {
			int sec = Integer.parseInt(big.length() > 3 ? "9" : big);
			float pitch = sec <= 1 ? 2.0f : sec == 2 ? 1.6f : sec == 3 ? 1.3f : 1.0f;
			player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 0.9f, pitch);
			ring(world, player, ParticleTypes.END_ROD, x, y + 0.2, z, 1.1, 18, 0.02);
			return;
		}
		if (big.startsWith("ПОБЕДА") || big.endsWith("МЕСТО")) {
			player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.0f);
			world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 1.0f, 1.0f);
			world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.2, z, 90, 0.6, 1.0, 0.6, 0.5);
			ring(world, null, ParticleTypes.FIREWORK, x, y + 0.3, z, 0.6, 28, 0.25);
			return;
		}
		if (big.startsWith("ВЫБЫЛ") || big.startsWith("ПАДЕНИЕ")) {
			player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.MASTER, 1.0f, 0.6f);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 24, 0.4, 0.6, 0.4, 0.02);
			return;
		}
		if (big.startsWith("САМОЦВЕТ ")) {
			player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.7f, 1.4f);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.2, z, 16, 0.4, 0.5, 0.4, 0.2);
			return;
		}
		if (small != null && small.startsWith("Ожидание")) {
			player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.2f);
			ring(world, player, ParticleTypes.END_ROD, x, y + 0.2, z, 1.2, 20, 0.03);
			return;
		}
		player.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.8f, 1.2f);
		world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 0.8f, 1.0f);
		ring(world, null, ParticleTypes.FIREWORK, x, y + 0.3, z, 0.5, 32, 0.3);
		world.spawnParticles(ParticleTypes.FLASH, x, y + 1.5, z, 1, 0, 0, 0, 0);
	}

	/** Кольцо частиц, разлетающихся наружу; {@code viewer == null} — видно всем рядом. */
	private static void ring(ServerWorld world, ServerPlayerEntity viewer, ParticleEffect type, double x, double y, double z,
			double radius, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double q = i * Math.PI * 2 / count;
			double cx = Math.cos(q);
			double cz = Math.sin(q);
			if (viewer != null) {
				world.spawnParticles(viewer, type, false, x + cx * radius, y, z + cz * radius, 0, cx, 0.15, cz, speed);
			} else {
				world.spawnParticles(type, x + cx * radius, y, z + cz * radius, 0, cx, 0.15, cz, speed);
			}
		}
	}
}
