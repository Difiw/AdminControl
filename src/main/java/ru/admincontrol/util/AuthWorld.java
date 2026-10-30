package ru.admincontrol.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;

public final class AuthWorld {
	public static final RegistryKey<World> KEY =
			RegistryKey.of(RegistryKeys.WORLD, new Identifier(AdminControlMod.MOD_ID, "auth"));

	private AuthWorld() {
	}

	public static ServerWorld get(MinecraftServer server) {
		if (server == null) {
			return null;
		}
		ServerWorld world = server.getWorld(KEY);
		return world != null ? world : server.getWorld(World.END);
	}

	public static double spawnX(ServerPlayerEntity player) {
		int slot = Math.abs(player.getUuid().hashCode() % 400);
		return slot * 10000.0;
	}

	public static double spawnY() {
		return 128.0;
	}

	public static double spawnZ() {
		return 0.0;
	}
}
