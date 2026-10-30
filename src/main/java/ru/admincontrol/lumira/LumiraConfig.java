package ru.admincontrol.lumira;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.admincontrol.AdminControlMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Баланс «Рощи Люмиры». Все значения — стартовые предложения из каталога v2, не отдельно согласованные числа;
 * меняются в config/admin_control/lumira.json (перезапуск сервера).
 */
public final class LumiraConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static LumiraConfig instance;

	public int coinLyraValue = 1;
	public int coinMyrtleValue = 1;
	public int storyBaseHealth = 20;
	public int eventMaxHealth = 20;

	public int necklaceCharges = 256;
	public int necklaceRepairCharges = 64;
	/** Роса: сколько секунд вне боя до лечения и интервал лечения. */
	public int dewIdleSeconds = 10;
	public int dewIntervalSeconds = 8;
	public double dewHeal = 1.0;
	public double dewSpeedPenalty = 0.05;
	public double echoDealt = 0.10;
	public double echoTaken = 0.10;
	public double guardReduction = 0.10;
	public double guardSpeedPenalty = 0.08;
	public double starDealt = 0.15;
	public double starTaken = 0.15;

	/** Рунная наковальня: доля прочности за операцию и «энергия» топлива в операциях. */
	public int anvilRepairPercent = 25;
	public int fuelCoal = 8;
	public int fuelSparkCoal = 8;
	public int fuelSparkBriquette = 32;
	public int fuelJarolite = 24;
	public int fuelJaroliteBriquette = 216;
	/** Одна операция печи в тиках горения (200 тиков = 1 предмет). */
	public int burnTicksPerOperation = 200;

	public List<String> hostileMobs = new ArrayList<>(List.of(
			"minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:skeleton", "minecraft:stray",
			"minecraft:spider", "minecraft:cave_spider", "minecraft:creeper", "minecraft:witch", "minecraft:pillager",
			"minecraft:vindicator", "minecraft:slime", "minecraft:phantom", "minecraft:silverfish", "minecraft:enderman",
			"minecraft:zombie_villager"));

	/** Генерация биома. */
	/** Сколько чанков генерировать, если количество в команде не указано. */
	public int biomeChunks = 16;
	public int biomeSeaLevel = 62;
	public int biomeBaseY = 72;
	public int biomeHillY = 118;
	public int biomeMountainY = 150;

	public static synchronized LumiraConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path dir() {
		String override = System.getProperty("admin_control.lumira.dir");
		Path dir = override != null && !override.isBlank() ? Path.of(override) : FabricLoader.getInstance().getConfigDir().resolve("admin_control");
		try {
			Files.createDirectories(dir);
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("Lumira: не создан каталог {}", dir, e);
		}
		return dir;
	}

	private static LumiraConfig load() {
		Path file = dir().resolve("lumira.json");
		LumiraConfig cfg = new LumiraConfig();
		try {
			if (Files.exists(file)) {
				LumiraConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LumiraConfig.class);
				if (read != null) {
					cfg = read;
				}
			}
			Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("Lumira: конфигурация не прочитана, используются значения по умолчанию", e);
		}
		return cfg;
	}

	public boolean isHostile(net.minecraft.entity.Entity entity) {
		return entity != null
				&& hostileMobs.contains(net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString());
	}
}
