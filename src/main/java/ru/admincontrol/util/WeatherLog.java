package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LightningEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import ru.admincontrol.data.DataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Журнал погоды верхнего мира и ударов молний для /aweatherlog. */
public final class WeatherLog {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

	private static Store store = new Store();
	private static final List<PendingStrike> PENDING = new ArrayList<>();
	private static boolean dirty;
	private static int ticks;

	private WeatherLog() {
	}

	public static final class Period {
		public String type = "";
		public String note = "";
		public long start;
		public long end;
	}

	public static final class Strike {
		public long at;
		public String dimension = "";
		public int x;
		public int y;
		public int z;
		public String block = "";
		public boolean fire;
	}

	private static final class Store {
		List<Period> periods = new ArrayList<>();
		List<Strike> strikes = new ArrayList<>();
		Period weather;
		Period snow;
	}

	private static final class PendingStrike {
		final ServerWorld world;
		final LightningEntity bolt;
		final Strike strike;
		final BlockPos pos;
		int wait = 6;

		PendingStrike(ServerWorld world, LightningEntity bolt, Strike strike, BlockPos pos) {
			this.world = world;
			this.bolt = bolt;
			this.strike = strike;
			this.pos = pos;
		}
	}

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		ticks++;
		tickStrikes();
		if (ticks % 20 == 0) {
			ServerWorld overworld = server.getOverworld();
			if (overworld != null) {
				trackWeather(server, overworld);
			}
		}
		if (dirty && ticks % 600 == 0) {
			save();
		}
	}

	private static void trackWeather(MinecraftServer server, ServerWorld world) {
		float rain = world.getRainGradient(1.0f);
		String type;
		if (world.isThundering() && world.isRaining()) {
			type = "Гроза";
		} else if (world.isRaining() && rain >= 0.999f) {
			type = "Дождь";
		} else if (rain > 0.001f) {
			type = "Пасмурно";
		} else {
			type = "Ясно";
		}
		long now = System.currentTimeMillis();
		if (store.weather == null || !type.equals(store.weather.type)) {
			close(store.weather, now);
			Period p = new Period();
			p.type = type;
			p.start = now;
			store.weather = p;
			dirty = true;
		}
		Set<String> snowy = new TreeSet<>();
		if (world.isRaining()) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (player.getWorld() != world) {
					continue;
				}
				BlockPos pos = player.getBlockPos();
				RegistryEntry<Biome> biome = world.getBiome(pos);
				if (biome.value().getPrecipitation(pos) == Biome.Precipitation.SNOW) {
					biome.getKey().ifPresent(k -> snowy.add(k.getValue().getPath()));
				}
			}
		}
		if (!snowy.isEmpty() && store.snow == null) {
			Period p = new Period();
			p.type = "Снег";
			p.start = now;
			p.note = String.join(", ", snowy);
			store.snow = p;
			dirty = true;
		} else if (!snowy.isEmpty()) {
			Set<String> all = new TreeSet<>(List.of(store.snow.note.split(",\\s*")));
			all.addAll(snowy);
			all.remove("");
			String note = String.join(", ", all);
			if (!note.equals(store.snow.note)) {
				store.snow.note = note;
				dirty = true;
			}
		} else if (store.snow != null) {
			close(store.snow, now);
			store.snow = null;
		}
	}

	private static void close(Period p, long now) {
		if (p == null) {
			return;
		}
		p.end = now;
		store.periods.add(p);
		dirty = true;
	}

	public static void onLightning(ServerWorld world, LightningEntity bolt) {
		if (world == null || bolt == null || DataManager.get() == null) {
			return;
		}
		BlockPos at = bolt.getBlockPos();
		BlockPos hit = BlockPos.ofFloored(bolt.getX(), bolt.getY() - 1.0E-6, bolt.getZ());
		BlockState state = world.getBlockState(hit);
		if (state.isAir()) {
			hit = at.down();
			state = world.getBlockState(hit);
		}
		Strike s = new Strike();
		s.at = System.currentTimeMillis();
		s.dimension = world.getRegistryKey().getValue().toString();
		s.x = hit.getX();
		s.y = hit.getY();
		s.z = hit.getZ();
		s.block = Registries.BLOCK.getId(state.getBlock()).toString();
		PENDING.add(new PendingStrike(world, bolt, s, at));
	}

	private static void tickStrikes() {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<PendingStrike> it = PENDING.iterator();
		while (it.hasNext()) {
			PendingStrike p = it.next();
			if (--p.wait > 0) {
				continue;
			}
			it.remove();
			p.strike.fire = p.bolt.getBlocksSetOnFire() > 0 || fireNear(p.world, p.pos);
			store.strikes.add(p.strike);
			dirty = true;
		}
	}

	private static boolean fireNear(ServerWorld world, BlockPos center) {
		for (BlockPos pos : BlockPos.iterate(center.add(-2, -2, -2), center.add(2, 2, 2))) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
				return true;
			}
		}
		return false;
	}

	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		out.add("Погода (верхний мир):");
		if (store.weather != null) {
			out.add("Сейчас: " + store.weather.type + " с " + time(store.weather.start) + ", идёт "
					+ duration(now - store.weather.start));
		}
		if (store.snow != null) {
			out.add("Сейчас: Снег в биомах " + store.snow.note + " с " + time(store.snow.start) + ", идёт "
					+ duration(now - store.snow.start));
		}
		if (store.periods.isEmpty()) {
			out.add("Завершённых периодов пока нет.");
		}
		for (int i = store.periods.size() - 1; i >= 0; i--) {
			Period p = store.periods.get(i);
			out.add(p.type + (p.note.isBlank() ? "" : " (биомы: " + p.note + ")") + ": " + time(p.start) + " — "
					+ time(p.end) + ", длительность " + duration(p.end - p.start));
		}
		out.add("");
		out.add("Удары молний:");
		if (store.strikes.isEmpty()) {
			out.add("Ударов молний пока не было.");
		}
		for (int i = store.strikes.size() - 1; i >= 0; i--) {
			Strike s = store.strikes.get(i);
			out.add(time(s.at) + "  ·  " + s.x + ", " + s.y + ", " + s.z + " (" + s.dimension + ")  ·  блок "
					+ s.block + "  ·  " + (s.fire ? "возник пожар" : "без пожара"));
		}
		return out;
	}

	private static String time(long ms) {
		return ms <= 0 ? "—" : TIME.format(Instant.ofEpochMilli(ms));
	}

	private static String duration(long ms) {
		long sec = Math.max(0, ms / 1000L);
		long h = sec / 3600;
		long m = sec % 3600 / 60;
		long s = sec % 60;
		if (h > 0) {
			return h + " ч " + m + " мин " + s + " сек";
		}
		if (m > 0) {
			return m + " мин " + s + " сек";
		}
		return s + " сек";
	}

	private static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("weather_log.json");
	}

	public static void load() {
		store = new Store();
		PENDING.clear();
		dirty = false;
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Store loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
			if (loaded != null) {
				store.periods = loaded.periods == null ? new ArrayList<>() : loaded.periods;
				store.strikes = loaded.strikes == null ? new ArrayList<>() : loaded.strikes;
			}
		} catch (Exception e) {
			ActionLog.error("Не удалось прочитать weather_log.json", e);
		}
	}

	/** Удаляет завершённые периоды и удары молний; текущая погода продолжает отслеживаться. */
	public static boolean clear() {
		boolean had = !store.periods.isEmpty() || !store.strikes.isEmpty();
		store.periods.clear();
		store.strikes.clear();
		PENDING.clear();
		save();
		return had;
	}

	/** Открытые периоды закрываются временем выключения сервера. */
	public static void shutdown() {
		long now = System.currentTimeMillis();
		if (store.weather != null) {
			close(store.weather, now);
			store.weather = null;
		}
		if (store.snow != null) {
			close(store.snow, now);
			store.snow = null;
		}
		save();
	}

	private static void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("weather_log.json.tmp");
			Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить weather_log.json", e);
		}
	}
}
