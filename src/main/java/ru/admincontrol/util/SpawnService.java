package ru.admincontrol.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PlayerWaypoint;
import ru.admincontrol.data.SpawnChoice;

public final class SpawnService {
	private SpawnService() {
	}

	public static SpawnChoice effectiveChoice(ServerPlayerEntity player, PlayerRecord record) {
		SpawnChoice stored = SpawnChoice.from(record == null ? null : record.spawnChoice);
		if (stored == SpawnChoice.HOME && (record == null || record.home == null)) {
			stored = null;
		}
		if (stored == SpawnChoice.CLAN && !ClanService.canSpawnAtClan(player)) {
			stored = null;
		}
		if (stored == SpawnChoice.CUSTOM && (record == null || record.customSpawn == null)) {
			stored = null;
		}
		if (stored != null) {
			return stored;
		}
		if (record != null && record.home != null) {
			return SpawnChoice.HOME;
		}
		return SpawnChoice.BED;
	}

	public static boolean hasBed(ServerPlayerEntity player) {
		return player != null && player.getSpawnPointPosition() != null && player.getSpawnPointDimension() != null;
	}

	public static void teleportToChoice(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		PlayerRecord record = data.getOrCreate(player);
		SpawnChoice choice = effectiveChoice(player, record);
		if (choice == SpawnChoice.CUSTOM && record.customSpawn != null) {
			teleportHome(player, record.customSpawn);
			return;
		}
		if (choice == SpawnChoice.HOME && record.home != null) {
			teleportHome(player, record.home);
			return;
		}
		if (choice == SpawnChoice.CLAN) {
			String err = ClanService.goHome(player);
			if (err == null) {
				return;
			}
		}
		if (choice == SpawnChoice.BED && hasBed(player)) {
			teleportBed(player);
			return;
		}
		teleportWorld(player);
	}

	public static void teleportHome(ServerPlayerEntity player, PlayerWaypoint home) {
		ServerWorld world = resolveWorld(player.getServer(), home.dimension);
		if (WorldGate.locked(world)) {
			Chat.send(player, Chat.red("Мир «" + WorldGate.label(world.getRegistryKey()) + "» закрыт."));
			teleportWorld(player);
			return;
		}
		player.teleport(world, home.x, home.y, home.z, home.yaw, home.pitch);
	}

	public static void teleportBed(ServerPlayerEntity player) {
		BlockPos spawn = player.getSpawnPointPosition();
		ServerWorld world = player.getServer().getWorld(player.getSpawnPointDimension());
		if (spawn == null || world == null) {
			teleportWorld(player);
			return;
		}
		if (WorldGate.locked(world)) {
			Chat.send(player, Chat.red("Мир «" + WorldGate.label(world.getRegistryKey()) + "» закрыт."));
			teleportWorld(player);
			return;
		}
		player.teleport(world, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5, player.getSpawnAngle(), 0.0F);
	}

	public static void teleportWorld(ServerPlayerEntity player) {
		ServerWorld world = player.getServer().getOverworld();
		BlockPos spawn = world.getSpawnPos();
		player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, world.getSpawnAngle(), 0.0F);
	}

	public static ServerWorld resolveWorld(MinecraftServer server, String dimension) {
		if (server == null) {
			return null;
		}
		if (dimension == null || dimension.isBlank()) {
			return server.getOverworld();
		}
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return server.getOverworld();
		}
		ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		return world != null ? world : server.getOverworld();
	}
}
