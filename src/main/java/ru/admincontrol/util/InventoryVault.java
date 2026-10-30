package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Бэкап инвентаря и точки входа на диск при заходе на тренировку / МП.
 * Если игрок вылетел или сервер упал — при следующем входе всё возвращается.
 */
public final class InventoryVault {
	public static final String TRAINING = "training";
	public static final String EVENT = "event";
	public static final String EVENT_MAP = "event_map";
	private static final int HISTORY_PER_PLAYER = 20;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Set<UUID> LEAVING = ConcurrentHashMap.newKeySet();

	private InventoryVault() {
	}

	public static final class Backup {
		public String uuid;
		public String nick;
		public String kind;
		public long createdAt;
		public String createdAtText;
		public String dimension;
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		public String gameMode;
		public float health = -1f;
		public int food = -1;
		public float saturation = -1f;
		public String inventory;
	}

	/** Вернуть здоровье и сытость, которые были до входа. */
	public static void applyVitals(ServerPlayerEntity player, float health, int food, float saturation) {
		if (player == null) {
			return;
		}
		if (health > 0f) {
			player.setHealth(Math.min(player.getMaxHealth(), health));
		}
		if (food >= 0) {
			player.getHungerManager().setFoodLevel(Math.min(20, food));
		}
		if (saturation >= 0f) {
			player.getHungerManager().setSaturationLevel(Math.min(player.getHungerManager().getFoodLevel(), saturation));
		}
	}

	private static Path dir() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("inventory_backups");
	}

	private static Path activeFile(UUID uuid) {
		Path dir = dir();
		return dir == null ? null : dir.resolve(uuid + ".json");
	}

	/** Сохранить до выдачи кита / телепорта. false — запись не удалась, вход надо отменить. */
	public static boolean save(ServerPlayerEntity player, String kind, String inventoryNbt, String dimension,
			double x, double y, double z, float yaw, float pitch, GameMode mode,
			float health, int food, float saturation) {
		Path file = player == null ? null : activeFile(player.getUuid());
		if (file == null) {
			return false;
		}
		Backup b = new Backup();
		b.uuid = player.getUuid().toString();
		b.nick = player.getGameProfile().getName();
		b.kind = kind;
		b.createdAt = System.currentTimeMillis();
		b.createdAtText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
		b.dimension = dimension;
		b.x = x;
		b.y = y;
		b.z = z;
		b.yaw = yaw;
		b.pitch = pitch;
		b.gameMode = mode == null ? GameMode.SURVIVAL.getName() : mode.getName();
		b.health = health;
		b.food = food;
		b.saturation = saturation;
		b.inventory = inventoryNbt == null ? "" : inventoryNbt;
		String json = GSON.toJson(b);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			writeHistory(b, json);
			ActivityLog.player(b.nick, "Бэкап: инвентарь сохранён в файл перед входом на " + kindLabel(kind)
					+ ", точка входа " + where(b) + ", здоровье " + vitals(b), true);
			return true;
		} catch (IOException e) {
			AdminControlMod.LOGGER.error("Не удалось сохранить бэкап инвентаря {}", b.nick, e);
			return false;
		}
	}

	private static void writeHistory(Backup b, String json) {
		Path dir = dir();
		if (dir == null) {
			return;
		}
		Path history = dir.resolve("history");
		try {
			Files.createDirectories(history);
			String safeNick = b.nick == null ? "unknown" : b.nick.replaceAll("[^A-Za-z0-9_\\-]", "_");
			Files.writeString(history.resolve(safeNick + "_" + b.uuid + "_" + LocalDateTime.now().format(STAMP)
					+ "_" + b.kind + ".json"), json, StandardCharsets.UTF_8);
			List<Path> mine;
			try (Stream<Path> files = Files.list(history)) {
				mine = files.filter(p -> p.getFileName().toString().contains("_" + b.uuid + "_"))
						.sorted(Comparator.comparing(p -> p.getFileName().toString()))
						.toList();
			}
			for (int i = 0; i < mine.size() - HISTORY_PER_PLAYER; i++) {
				Files.deleteIfExists(mine.get(i));
			}
		} catch (IOException e) {
			AdminControlMod.LOGGER.warn("История бэкапов инвентаря: {}", e.toString());
		}
	}

	/** Инвентарь уже возвращён штатно. При выходе с сервера файл оставляем — его подтвердит следующий вход. */
	public static void clear(ServerPlayerEntity player) {
		if (player == null || LEAVING.contains(player.getUuid())) {
			return;
		}
		Path file = activeFile(player.getUuid());
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			AdminControlMod.LOGGER.warn("Не удалось удалить бэкап инвентаря {}: {}", player.getGameProfile().getName(), e.toString());
		}
	}

	public static void leaving(ServerPlayerEntity player, Runnable action) {
		if (player == null) {
			action.run();
			return;
		}
		LEAVING.add(player.getUuid());
		try {
			action.run();
		} finally {
			LEAVING.remove(player.getUuid());
		}
	}

	public static Backup read(UUID uuid) {
		Path file = uuid == null ? null : activeFile(uuid);
		if (file == null || !Files.exists(file)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Backup.class);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Повреждён бэкап инвентаря {}", file, e);
			return null;
		}
	}

	/** При входе: если остался незакрытый бэкап — вернуть инвентарь, режим и телепортировать на точку входа. */
	public static void restoreOnJoin(ServerPlayerEntity player) {
		if (player == null || NpcService.isNpc(player) || TrainingService.active(player) || EventService.member(player)) {
			return;
		}
		GodModeService.resume(player);
		Backup b = read(player.getUuid());
		if (b == null) {
			return;
		}
		player.closeHandledScreen();
		InventoryUtil.restore(player, b.inventory);
		GameMode mode = GameMode.byName(b.gameMode, null);
		if (mode != null && player.interactionManager.getGameMode() != mode) {
			player.changeGameMode(mode);
		}
		ServerWorld world = SpawnService.resolveWorld(player.getServer(), b.dimension);
		TpGuard.bypass(() -> TeleportLog.labeled("возвращён на точку входа после вылета", () ->
				WorldGate.allow(() -> player.teleport(world, b.x, b.y, b.z, b.yaw, b.pitch))));
		player.fallDistance = 0;
		player.extinguish();
		player.setVelocity(0, 0, 0);
		applyVitals(player, b.health, b.food, b.saturation);
		ActivityLock.sync(player);
		Path file = activeFile(player.getUuid());
		try {
			if (file != null) {
				Files.deleteIfExists(file);
			}
		} catch (IOException e) {
			AdminControlMod.LOGGER.warn("Не удалось удалить бэкап инвентаря {}: {}", b.nick, e.toString());
		}
		if (DataManager.get() != null) {
			InventoryUtil.snapshot(player, DataManager.get().getOrCreate(player));
			DataManager.get().saveAll();
		}
		String during = TRAINING.equals(b.kind) ? "тренировки" : EVENT_MAP.equals(b.kind) ? "правки карты МП" : "мероприятия";
		Chat.send(player, Chat.green("Вы вышли во время " + during
				+ ": инвентарь восстановлен, вы возвращены на точку входа."));
		AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.WORLD,
				"после вылета с " + during + ": инвентарь восстановлен из файла (сохранён " + b.createdAtText
						+ "), телепорт на точку входа " + where(b) + ", здоровье " + vitals(b) + ", файл удалён", true);
	}

	private static String vitals(Backup b) {
		return String.format(java.util.Locale.US, "%.1f/20, сытость %d/20", b.health, b.food);
	}

	private static String kindLabel(String kind) {
		return TRAINING.equals(kind) ? "тренировку" : EVENT_MAP.equals(kind) ? "правку карты МП" : "мероприятие";
	}

	private static String where(Backup b) {
		return String.format(java.util.Locale.US, "%.1f %.1f %.1f [%s]", b.x, b.y, b.z, b.dimension);
	}
}
