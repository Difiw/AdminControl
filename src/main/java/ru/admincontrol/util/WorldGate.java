package ru.admincontrol.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGate {
	private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();
	private static final ThreadLocal<Integer> ALLOW = ThreadLocal.withInitial(() -> 0);

	private WorldGate() {
	}

	public static boolean locked(ServerWorld world) {
		return world != null && locked(world.getRegistryKey());
	}

	public static boolean locked(RegistryKey<World> key) {
		if (key == null || World.OVERWORLD.equals(key) || AuthWorld.KEY.equals(key)) {
			return false;
		}
		return locked(key.getValue().toString());
	}

	public static boolean locked(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String want = id.toLowerCase(Locale.ROOT);
		if (want.equals("minecraft:overworld") || want.equals(AuthWorld.KEY.getValue().toString())) {
			return false;
		}
		ModSettings settings = DataManager.get() == null ? null : DataManager.get().settings();
		if (settings == null || settings.disabledWorlds == null) {
			return false;
		}
		return settings.disabledWorlds.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(want));
	}

	public static void allow(Runnable action) {
		if (action == null) {
			return;
		}
		ALLOW.set(ALLOW.get() + 1);
		try {
			action.run();
		} finally {
			int depth = ALLOW.get() - 1;
			if (depth <= 0) {
				ALLOW.remove();
			} else {
				ALLOW.set(depth);
			}
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			LAST_WARN.remove(player.getUuid());
		}
	}

	public static boolean blockTravel(ServerPlayerEntity player, ServerWorld destination) {
		if (player == null || destination == null) {
			return false;
		}
		if (ALLOW.get() > 0) {
			return false;
		}
		if (destination == player.getWorld()) {
			return false;
		}
		// Builder and every private/test story instance are capability-only worlds. Operator commands such as
		// /execute in <dimension> run tp must not be able to enter them; CW services call WorldGate.allow explicitly.
		if (protectedStoryWorld(destination.getRegistryKey())) {
			warn(player, "В сюжетные и строительные миры можно войти только через команды сюжетной системы.");
			return true;
		}
		if (ru.admincontrol.cw.CwBuilderWorld.inside(player)) {
			Chat.send(player, Chat.red("Покинуть строительную сессию можно только через /cw template leave или recovery."));
			return true;
		}
		if (AuthService.pending(player) && AuthWorld.KEY.equals(destination.getRegistryKey())) {
			return false;
		}
		if (!locked(destination)) {
			return false;
		}
		warn(player, "Мир «" + label(destination.getRegistryKey()) + "» закрыт.");
		return true;
	}

	private static boolean protectedStoryWorld(RegistryKey<World> key) {
		if (key == null) return false;
		String id = key.getValue().toString();
		return ru.admincontrol.cw.CwBuilderWorld.KEY.equals(key)
				|| ru.admincontrol.cw.CwStoryRules.isStoryWorldId(id)
				|| id.startsWith("admin_control:cw_story_");
	}

	private static void warn(ServerPlayerEntity player, String message) {
		long now = System.currentTimeMillis();
		Long prev = LAST_WARN.get(player.getUuid());
		if (prev == null || now - prev > 2000L) {
			LAST_WARN.put(player.getUuid(), now);
			Chat.send(player, Chat.red(message));
		}
	}

	public static void ejectIfLocked(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null || AuthService.pending(player)
				|| NpcService.isIdleNpc(player) || NpcService.isParked(player)) {
			return;
		}
		if (!locked(player.getServerWorld())) {
			return;
		}
		eject(player);
	}

	public static void eject(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		ServerWorld overworld = player.getServer().getOverworld();
		if (overworld == null) {
			return;
		}
		BlockPos spawn = overworld.getSpawnPos();
		Chat.send(player, Chat.red("Мир закрыт. Вы возвращены на спавн."));
		TeleportLog.labeled("возвращён из закрытого мира", () ->
				player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
						overworld.getSpawnAngle(), 0));
	}

	public static boolean toggle(MinecraftServer server, String id) {
		DataManager data = DataManager.get();
		if (data == null || id == null || id.isBlank()) {
			return false;
		}
		if (!locked(id) && (id.equalsIgnoreCase("minecraft:overworld")
				|| id.equalsIgnoreCase(AuthWorld.KEY.getValue().toString()))) {
			return false;
		}
		ModSettings settings = data.settings();
		if (settings.disabledWorlds == null) {
			settings.disabledWorlds = new ArrayList<>();
		}
		boolean nowLocked;
		if (locked(id)) {
			settings.disabledWorlds.removeIf(v -> v != null && v.equalsIgnoreCase(id));
			nowLocked = false;
		} else {
			settings.disabledWorlds.add(id.toLowerCase(Locale.ROOT));
			nowLocked = true;
		}
		data.saveAll();
		if (nowLocked && server != null) {
			for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
				if (player.getWorld().getRegistryKey().getValue().toString().equalsIgnoreCase(id)) {
					eject(player);
				}
			}
		}
		return nowLocked;
	}

	public static List<Entry> list(MinecraftServer server) {
		List<Entry> entries = new ArrayList<>();
		if (server == null) {
			return entries;
		}
		for (ServerWorld world : server.getWorlds()) {
			RegistryKey<World> key = world.getRegistryKey();
			if (World.OVERWORLD.equals(key) || AuthWorld.KEY.equals(key)) {
				continue;
			}
			String id = key.getValue().toString();
			entries.add(new Entry(id, label(key), locked(id)));
		}
		entries.sort(Comparator
				.comparingInt((Entry e) -> rank(e.id))
				.thenComparing(e -> e.label.toLowerCase(Locale.ROOT)));
		return entries;
	}

	public static String label(RegistryKey<World> key) {
		if (key == null) {
			return "мир";
		}
		return label(key.getValue().toString());
	}

	public static String label(String id) {
		if (id == null) {
			return "мир";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "minecraft:the_nether" -> "Незер";
			case "minecraft:the_end" -> "Энд";
			default -> {
				Identifier parsed = Identifier.tryParse(id);
				yield parsed == null ? id : parsed.getPath();
			}
		};
	}

	private static int rank(String id) {
		return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
			case "minecraft:the_nether" -> 0;
			case "minecraft:the_end" -> 1;
			default -> 2;
		};
	}

	public static final class Entry {
		public final String id;
		public final String label;
		public final boolean locked;

		public Entry(String id, String label, boolean locked) {
			this.id = id == null ? "" : id;
			this.label = label == null ? this.id : label;
			this.locked = locked;
		}
	}
}
