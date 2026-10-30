package ru.admincontrol.lumira.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.lumira.LumiraConfig;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.LumiraIds;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Административная генерация единственного биома «Роща Люмиры» в строительном измерении (/cw biome lumira).
 * Работает порциями по тикам. Повторная генерация поверх построек запрещена: нужна явная область, подтверждение
 * {@code force} и резервная копия блоков; области с блочными сущностями (сундуки) не перезаписываются вовсе.
 */
public final class LumiraBiomeGen {
	public static final Identifier BIOME_ID = new Identifier(AdminControlMod.MOD_ID, "lumira_grove");
	public static final int GRID_MIN = -8;
	public static final int GRID_MAX = 7;
	public static final int MAX_CHUNKS = 256;
	private static Job job;

	private LumiraBiomeGen() {
	}

	private enum Phase {
		TERRAIN, CAVES, RELIGHT, SETTLE, RESEND, TREES, DECOR, RESEND_FINAL, DONE
	}

	private static final class Worm {
		double x, y, z, yaw, pitch, radius;
		int steps;
		int floor;
	}

	private static final class Job {
		ServerWorld world;
		UUID starter;
		int radius;
		long seed;
		LumiraTerrain terrain;
		RegistryEntry<Biome> biome;
		List<ChunkPos> chunks = new ArrayList<>();
		int index;
		Phase phase = Phase.TERRAIN;
		List<Worm> worms = new ArrayList<>();
		int wormsDone;
		int treeCell;
		int decorCell;
		int settle;
		java.util.Set<Long> chunkSet = new java.util.HashSet<>();
		List<java.util.concurrent.CompletableFuture<?>> lights = new ArrayList<>();
		int ticks;
		long startedAt = System.currentTimeMillis();
		Map<String, Integer> ores = new LinkedHashMap<>();
		Map<String, Integer> trees = new LinkedHashMap<>();
		int carvedBlocks;
		int flowers;
		String backup = "";
		String result = "";
		boolean cancel;
	}

	public static synchronized boolean running() {
		return job != null && job.phase != Phase.DONE && !job.cancel;
	}

	public static synchronized String status() {
		if (job == null) {
			return "Генерация не запущена.";
		}
		Job j = job;
		return "Роща Люмиры: этап " + j.phase + ", чанков " + j.index + "/" + j.chunks.size() + ", пещер " + j.wormsDone + "/"
				+ j.worms.size() + ", деревьев " + j.trees.values().stream().mapToInt(Integer::intValue).sum();
	}

	public static synchronized String cancel() {
		if (job == null || job.phase == Phase.DONE) {
			return "Нечего отменять.";
		}
		job.cancel = true;
		return "Отмена принята: уже изменённые блоки остаются, резервная копия: " + (job.backup.isEmpty() ? "не требовалась" : job.backup);
	}

	public static synchronized String lastReport() {
		return job == null ? "" : job.result;
	}

	/** Сид рельефа хранится на диске: соседние запуски по чанкам стыкуются без швов. */
	private static long persistentSeed() {
		Path file = LumiraConfig.dir().resolve("lumira_seed.txt");
		try {
			if (Files.exists(file)) {
				return Long.parseLong(Files.readString(file).trim());
			}
			long seed = System.nanoTime() ^ 0x4C554D49524132L;
			Files.writeString(file, Long.toString(seed));
			return seed;
		} catch (Exception e) {
			return 0x4C554D49524132L;
		}
	}

	/**
	 * Запуск. count — количество ЧАНКОВ (1 = один чанк 16×16). Генерация ищет пустые чанки (только воздух), начиная
	 * с ближайшего к origin, и растёт по соседним пустым; занятые чанки не трогаются вообще. Если путь упирается в
	 * занятый чанк, берётся следующий ближайший пустой. Ограничения размера карты нет: каждый новый запуск продолжает
	 * поиск наружу от указанной точки. За один запуск по-прежнему меняется не более {@link #MAX_CHUNKS} чанков.
	 */
	public static synchronized String start(MinecraftServer server, UUID starter, BlockPos origin, int count) {
		if (job != null && job.phase != Phase.DONE && !job.cancel) {
			return "Генерация уже идёт: " + status();
		}
		ServerWorld world = CwBuilderWorld.world(server);
		if (world == null) {
			return "Строительное измерение недоступно.";
		}
		count = Math.max(1, Math.min(MAX_CHUNKS, count));
		int pcx = Math.floorDiv(origin.getX(), 16);
		int pcz = Math.floorDiv(origin.getZ(), 16);
		List<ChunkPos> picked = new ArrayList<>();
		// Square-ring scan has no artificial map border. Minecraft's own world border remains the only hard limit.
		for (int ring = 0; picked.size() < count && ring < 1_800_000; ring++) {
			for (int dx = -ring; dx <= ring && picked.size() < count; dx++) {
				for (int dz : ring == 0 ? new int[]{0} : new int[]{-ring, ring}) {
					if (picked.size() >= count) break;
					addIfEmpty(world, pcx + dx, pcz + dz, picked);
				}
			}
			for (int dz = -ring + 1; dz < ring && picked.size() < count; dz++) {
				addIfEmpty(world, pcx - ring, pcz + dz, picked);
				if (picked.size() < count) addIfEmpty(world, pcx + ring, pcz + dz, picked);
			}
		}
		if (picked.isEmpty()) return "Не удалось найти пустые чанки в пределах стандартной границы Minecraft.";
		Job j = new Job();
		j.world = world;
		j.starter = starter;
		// No mod-imposed story-map edge; stop only at Minecraft's native coordinate limit.
		j.radius = 29_999_000;
		j.seed = persistentSeed();
		LumiraConfig cfg = LumiraConfig.get();
		j.terrain = new LumiraTerrain(j.seed, cfg, j.radius);
		RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, BIOME_ID);
		j.biome = world.getRegistryManager().get(RegistryKeys.BIOME).getEntry(key).orElse(null);
		if (j.biome == null) {
			return "Биом " + BIOME_ID + " не найден в реестре.";
		}
		j.chunks = picked;
		for (ChunkPos cp : picked) {
			j.chunkSet.add(cp.toLong());
		}
		prepareWorms(j);
		job = j;
		ChunkPos first = picked.get(0);
		return "Генерация «Рощи Люмиры» запущена: чанков " + picked.size() + (picked.size() < count ? " (свободных нашлось меньше, чем запрошено)" : "")
				+ ", первый чанк " + first.x + ", " + first.z + ". Занятые чанки не затрагиваются. Прогресс: /cw biome status";
	}

	private static void addIfEmpty(ServerWorld world, int cx, int cz, List<ChunkPos> picked) {
		WorldChunk chunk = world.getChunk(cx, cz);
		if (chunk.getBlockEntityPositions().isEmpty() && cleanChunk(chunk)) picked.add(new ChunkPos(cx, cz));
	}

	private static boolean cleanChunk(WorldChunk chunk) {
		int solid = 0;
		for (ChunkSection section : chunk.getSectionArray()) {
			if (section == null || section.isEmpty()) {
				continue;
			}
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						BlockState s = section.getBlockState(x, y, z);
						if (!s.isAir()) {
							if (!s.isOf(Blocks.BEDROCK) || ++solid > 4) {
								return false;
							}
						}
					}
				}
			}
		}
		return true;
	}

	// ---- резервная копия ----

	private static Path backupDir() {
		Path dir = LumiraConfig.dir().resolve("lumira_backups");
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
		}
		return dir;
	}

	private static String writeBackup(ServerWorld world, List<ChunkPos> chunks, int radius) throws Exception {
		String name = "lumira-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".bin.gz";
		Path file = backupDir().resolve(name);
		Map<String, Integer> palette = new LinkedHashMap<>();
		java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(body)) {
			out.writeInt(chunks.size());
			for (ChunkPos cp : chunks) {
				WorldChunk chunk = world.getChunk(cp.x, cp.z);
				out.writeInt(cp.x);
				out.writeInt(cp.z);
				int bottom = world.getBottomY();
				int top = world.getTopY();
				int runId = -1;
				int run = 0;
				for (int y = bottom; y < top; y++) {
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							BlockState s = chunk.getBlockState(new BlockPos(cp.getStartX() + x, y, cp.getStartZ() + z));
							int id = palette.computeIfAbsent(BlockArgumentParser.stringifyBlockState(s), k -> palette.size());
							if (id == runId) {
								run++;
							} else {
								if (run > 0) {
									out.writeInt(runId);
									out.writeInt(run);
								}
								runId = id;
								run = 1;
							}
						}
					}
				}
				out.writeInt(runId);
				out.writeInt(run);
			}
		}
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
			out.writeInt(radius);
			out.writeInt(world.getBottomY());
			out.writeInt(world.getTopY());
			out.writeInt(palette.size());
			for (String s : palette.keySet()) {
				out.writeUTF(s);
			}
			out.write(body.toByteArray());
		}
		return name;
	}

	public static List<String> backups() {
		List<String> list = new ArrayList<>();
		try (var stream = Files.list(backupDir())) {
			stream.map(p -> p.getFileName().toString()).sorted().forEach(list::add);
		} catch (Exception ignored) {
		}
		return list;
	}

	/** Возвращает блоки области из резервной копии (блочные сущности не сохранялись). */
	public static String restore(MinecraftServer server, String name) {
		if (job != null && job.phase != Phase.DONE && !job.cancel) {
			return "Дождитесь окончания генерации.";
		}
		ServerWorld world = CwBuilderWorld.world(server);
		if (world == null || !name.matches("[A-Za-z0-9_.-]+")) {
			return "Недопустимое имя копии или нет строительного измерения.";
		}
		Path file = backupDir().resolve(name);
		if (!Files.exists(file)) {
			return "Копия не найдена: " + name;
		}
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
			in.readInt();
			int bottom = in.readInt();
			int top = in.readInt();
			int n = in.readInt();
			BlockState[] palette = new BlockState[n];
			for (int i = 0; i < n; i++) {
				palette[i] = BlockArgumentParser.block(world.createCommandRegistryWrapper(RegistryKeys.BLOCK),
						in.readUTF(), false).blockState();
			}
			int chunkCount = in.readInt();
			for (int c = 0; c < chunkCount; c++) {
				int cx = in.readInt();
				int cz = in.readInt();
				int total = (top - bottom) * 256;
				int done = 0;
				int[] ids = new int[total];
				while (done < total) {
					int id = in.readInt();
					int len = in.readInt();
					for (int i = 0; i < len; i++) {
						ids[done++] = id;
					}
				}
				WorldChunk chunk = world.getChunk(cx, cz);
				int k = 0;
				for (int y = bottom; y < top; y++) {
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							setRaw(chunk, cx * 16 + x, y, cz * 16 + z, palette[ids[k++]]);
						}
					}
				}
				finishChunk(chunk);
				resend(world, new ChunkPos(cx, cz));
			}
			return "Область восстановлена из " + name + ". Чанки отправлены игрокам.";
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Lumira: восстановление не удалось", e);
			return "Восстановление не удалось: " + e.getMessage();
		}
	}

	// ---- тик ----

	public static void tick(MinecraftServer server) {
		Job j = job;
		if (j == null || j.phase == Phase.DONE) {
			return;
		}
		if (j.cancel) {
			j.phase = Phase.DONE;
			j.result = "Генерация отменена администратором.";
			notifyStarter(server, j, j.result);
			return;
		}
		j.ticks++;
		if (j.ticks % 10 == 0) {
			progress(server, j);
		}
		try {
			switch (j.phase) {
				case TERRAIN -> stepTerrain(j);
				case CAVES -> stepCaves(j);
				case RELIGHT -> stepRelight(j);
				case SETTLE -> {
					if (--j.settle <= 0 && j.lights.stream().allMatch(java.util.concurrent.CompletableFuture::isDone)) {
						j.lights.clear();
						j.phase = Phase.RESEND;
						j.index = 0;
					}
				}
				case RESEND -> stepResend(j, Phase.TREES);
				case RESEND_FINAL -> stepResend(j, Phase.DONE);
				case TREES -> stepTrees(j);
				case DECOR -> stepDecor(j);
				default -> {
				}
			}
			if (j.phase == Phase.DONE) {
				j.result = report(j);
				AdminControlMod.LOGGER.info("Lumira: {}", j.result.replace('\n', ' '));
				notifyStarter(server, j, j.result);
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Lumira: ошибка генерации", e);
			j.phase = Phase.DONE;
			j.result = "Генерация остановлена из-за ошибки: " + e;
			notifyStarter(server, j, j.result);
		}
	}

	private static void notifyStarter(MinecraftServer server, Job j, String text) {
		if (j.starter == null) {
			return;
		}
		ServerPlayerEntity p = server.getPlayerManager().getPlayer(j.starter);
		if (p != null) {
			for (String line : text.split("\n")) {
				p.sendMessage(Text.literal("[CW] " + line));
			}
		}
	}

	private static String report(Job j) {
		StringBuilder sb = new StringBuilder("Роща Люмиры готова: " + j.chunks.size() + " чанков, "
				+ (System.currentTimeMillis() - j.startedAt) / 1000 + " с.");
		sb.append("\nРуды: ").append(j.ores);
		int total = Math.max(1, j.trees.values().stream().mapToInt(Integer::intValue).sum());
		sb.append("\nДеревья (").append(total).append("): ");
		for (Map.Entry<String, Integer> e : j.trees.entrySet()) {
			sb.append(e.getKey()).append(' ').append(Math.round(100.0 * e.getValue() / total)).append("% ");
		}
		sb.append("\nЧанки уже отправлены игрокам, перезаходить не нужно.");
		return sb.toString();
	}

	// ---- вспомогательные операции с чанками ----

	private static void setRaw(Chunk chunk, int x, int y, int z, BlockState state) {
		int idx = chunk.getSectionIndex(y);
		if (idx < 0 || idx >= chunk.getSectionArray().length) {
			return;
		}
		chunk.getSection(idx).setBlockState(x & 15, y & 15, z & 15, state, false);
	}

	private static BlockState getRaw(Chunk chunk, int x, int y, int z) {
		int idx = chunk.getSectionIndex(y);
		if (idx < 0 || idx >= chunk.getSectionArray().length) {
			return Blocks.AIR.getDefaultState();
		}
		return chunk.getSection(idx).getBlockState(x & 15, y & 15, z & 15);
	}

	private static void finishChunk(Chunk chunk) {
		Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.WORLD_SURFACE, Heightmap.Type.MOTION_BLOCKING,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR));
		chunk.setNeedsSaving(true);
	}

	private static long chunkSeed(Job j, ChunkPos cp, int salt) {
		return j.seed ^ (cp.x * 341873128712L) ^ (cp.z * 132897987541L) ^ (salt * 0x9E3779B97F4A7C15L);
	}

	// ---- этап 1: рельеф, биом, руды ----

	private static void stepTerrain(Job j) {
		for (int n = 0; n < 2 && j.index < j.chunks.size(); n++) {
			ChunkPos cp = j.chunks.get(j.index++);
			terrainChunk(j, cp);
		}
		if (j.index >= j.chunks.size()) {
			j.phase = Phase.CAVES;
			j.index = 0;
		}
	}

	private static void terrainChunk(Job j, ChunkPos cp) {
		WorldChunk chunk = j.world.getChunk(cp.x, cp.z);
		chunk.populateBiomes((x, y, z, sampler) -> j.biome, null);
		int sx = cp.getStartX();
		int sz = cp.getStartZ();
		LumiraTerrain t = j.terrain;
		int[][] heights = new int[18][18];
		LumiraTerrain.Column[][] cols = new LumiraTerrain.Column[16][16];
		for (int x = -1; x <= 16; x++) {
			for (int z = -1; z <= 16; z++) {
				LumiraTerrain.Column c = t.sample(sx + x, sz + z);
				heights[x + 1][z + 1] = c.height();
				if (x >= 0 && x < 16 && z >= 0 && z < 16) {
					cols[x][z] = c;
				}
			}
		}
		int bottom = j.world.getBottomY();
		int sea = t.sea();
		Block bedrock = Blocks.BEDROCK;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int wx = sx + x;
				int wz = sz + z;
				if (Math.abs(wx) > j.radius || Math.abs(wz) > j.radius) {
					continue;
				}
				LumiraTerrain.Column c = cols[x][z];
				int h = c.height();
				int slope = Math.max(Math.max(Math.abs(heights[x + 2][z + 1] - h), Math.abs(heights[x][z + 1] - h)),
						Math.max(Math.abs(heights[x + 1][z + 2] - h), Math.abs(heights[x + 1][z] - h)));
				BlockState top;
				BlockState filler;
				double patch = t.noise().value(wx * 0.18, wz * 0.18, 21);
				if (h <= sea + 1) {
					boolean sand = t.noise().value(wx * 0.05, wz * 0.05, 22) > -0.1;
					top = sand ? Blocks.SAND.getDefaultState() : Blocks.GRAVEL.getDefaultState();
					if (h < sea - 2 && patch > 0.55) {
						top = Blocks.CLAY.getDefaultState();
					}
					filler = top;
				} else if (c.mountain() > 0.55 || slope >= 3 || h > j.world.getBottomY() + 200) {
					top = patch > 0.5 ? Blocks.GRAVEL.getDefaultState() : Blocks.STONE.getDefaultState();
					filler = Blocks.STONE.getDefaultState();
				} else {
					top = Blocks.GRASS_BLOCK.getDefaultState();
					filler = Blocks.DIRT.getDefaultState();
				}
				for (int y = bottom; y <= h; y++) {
					BlockState s;
					if (y <= bottom + 2 && (y == bottom || ((wx * 31 + wz * 17 + y * 7) & 3) == 0)) {
						s = bedrock.getDefaultState();
					} else if (y == h) {
						s = top;
					} else if (y >= h - 3) {
						s = filler;
					} else if (y < 0 || (y < 8 && ((wx * 7 + wz * 13 + y * 3) & 7) < (8 - y))) {
						s = Blocks.DEEPSLATE.getDefaultState();
					} else {
						s = Blocks.STONE.getDefaultState();
					}
					setRaw(chunk, wx, y, wz, s);
				}
				for (int y = h + 1; y <= sea; y++) {
					setRaw(chunk, wx, y, wz, Blocks.WATER.getDefaultState());
				}
			}
		}
		placeOres(j, chunk, cp, heights);
		finishChunk(chunk);
	}

	private record OreSpec(String name, BlockState stone, BlockState deep, int yMin, int yMax, int vMin, int vMax, int attempts) {
	}

	private static List<OreSpec> oreSpecs() {
		List<OreSpec> list = new ArrayList<>();
		list.add(new OreSpec("уголь", Blocks.COAL_ORE.getDefaultState(), Blocks.DEEPSLATE_COAL_ORE.getDefaultState(), 0, 128, 6, 14, 16));
		list.add(new OreSpec("железо", Blocks.IRON_ORE.getDefaultState(), Blocks.DEEPSLATE_IRON_ORE.getDefaultState(), -32, 80, 4, 9, 10));
		for (LumiraIds.Ore ore : LumiraIds.ORES) {
			BlockState s = LumiraContent.block(ore.id()).getDefaultState();
			list.add(new OreSpec(ore.id(), s, s, ore.yMin(), ore.yMax(), ore.veinMin(), ore.veinMax(), ore.perChunk()));
		}
		return list;
	}

	private static boolean host(BlockState s) {
		return s.isOf(Blocks.STONE) || s.isOf(Blocks.DEEPSLATE);
	}

	private static void placeOres(Job j, WorldChunk chunk, ChunkPos cp, int[][] heights) {
		Random r = Random.create(chunkSeed(j, cp, 77));
		int sx = cp.getStartX();
		int sz = cp.getStartZ();
		for (OreSpec spec : oreSpecs()) {
			int placed = 0;
			for (int a = 0; a < spec.attempts(); a++) {
				int x = r.nextInt(16);
				int z = r.nextInt(16);
				int h = heights[x + 1][z + 1];
				int ymax = Math.min(spec.yMax(), h - 3);
				if (ymax <= spec.yMin()) {
					continue;
				}
				int y = spec.yMin() + r.nextInt(ymax - spec.yMin() + 1);
				int size = spec.vMin() + r.nextInt(spec.vMax() - spec.vMin() + 1);
				int cx = x, cy = y, cz = z;
				for (int i = 0; i < size; i++) {
					if (cx >= 0 && cx < 16 && cz >= 0 && cz < 16 && cy > chunk.getBottomY() + 3) {
						BlockState current = getRaw(chunk, sx + cx, cy, sz + cz);
						if (host(current)) {
							setRaw(chunk, sx + cx, cy, sz + cz, cy < 0 ? spec.deep() : spec.stone());
							placed++;
						}
					}
					cx += r.nextInt(3) - 1;
					cy += r.nextInt(3) - 1;
					cz += r.nextInt(3) - 1;
				}
			}
			if (placed > 0) {
				j.ores.merge(spec.name(), placed, Integer::sum);
			}
		}
	}

	// ---- этап 2: пещеры ----

	private static void prepareWorms(Job j) {
		for (ChunkPos cp : j.chunks) {
			Random r = Random.create(chunkSeed(j, cp, 31));
			if (r.nextInt(8) != 0) {
				continue;
			}
			int x = cp.getStartX() + 3 + r.nextInt(10);
			int z = cp.getStartZ() + 3 + r.nextInt(10);
			LumiraTerrain.Column c = j.terrain.sample(x, z);
			boolean rocky = c.hill() > 0.2 || c.mountain() > 0.15 || r.nextInt(5) == 0;
			if (c.underwater() || !rocky) {
				continue;
			}
			Worm w = new Worm();
			w.x = x;
			w.z = z;
			w.y = c.height() - 1;
			w.yaw = r.nextDouble() * Math.PI * 2;
			w.pitch = -0.45;
			w.radius = 1.8 + r.nextDouble() * 1.4;
			w.steps = 90 + r.nextInt(90);
			w.floor = -55 + r.nextInt(40);
			j.worms.add(w);
		}
	}

	private static void stepCaves(Job j) {
		Random r = Random.create(j.seed ^ (j.wormsDone * 1315423911L));
		int budget = 4;
		while (budget-- > 0 && j.wormsDone < j.worms.size()) {
			Worm w = j.worms.get(j.wormsDone++);
			runWorm(j, w, r, 0);
		}
		if (j.wormsDone >= j.worms.size()) {
			j.phase = Phase.RELIGHT;
			j.index = 0;
		}
	}

	private static void runWorm(Job j, Worm w, Random r, int depth) {
		double x = w.x, y = w.y, z = w.z, yaw = w.yaw, pitch = w.pitch, radius = w.radius;
		int bottom = j.world.getBottomY() + 4;
		for (int step = 0; step < w.steps; step++) {
			yaw += (r.nextDouble() - 0.5) * 0.55;
			double bias = y < w.floor ? 0.07 : (y > w.floor + 14 && step < w.steps * 0.65 ? -0.05 : 0);
			pitch = pitch * 0.92 + (r.nextDouble() - 0.5) * 0.3 + bias;
			pitch = Math.max(-0.7, Math.min(0.6, pitch));
			double cp = Math.cos(pitch);
			x += Math.cos(yaw) * cp;
			z += Math.sin(yaw) * cp;
			y += Math.sin(pitch);
			if (!j.chunkSet.contains(ChunkPos.toLong((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) || y < bottom) {
				break;
			}
			double rr = radius + 0.7 * Math.sin(step * 0.21);
			carve(j, (int) Math.round(x), (int) Math.round(y), (int) Math.round(z), rr, rr * 0.85);
			if (step % 45 == 44) {
				// безопасный карман: широкая каверна с ровным полом
				double cr = 4.0 + r.nextInt(4);
				carve(j, (int) Math.round(x), (int) Math.round(y) + 1, (int) Math.round(z), cr, cr * 0.55);
			}
			if (depth < 2 && r.nextInt(70) == 0) {
				Worm b = new Worm();
				b.x = x;
				b.y = y;
				b.z = z;
				b.yaw = yaw + (r.nextBoolean() ? 1.2 : -1.2);
				b.pitch = -0.2;
				b.radius = Math.max(1.4, radius - 0.3);
				b.steps = 50 + r.nextInt(70);
				b.floor = Math.max(-58, w.floor - 6 - r.nextInt(14));
				runWorm(j, b, r, depth + 1);
			}
		}
	}

	private static void carve(Job j, int cx, int cy, int cz, double rh, double rv) {
		int ih = (int) Math.ceil(rh);
		int iv = (int) Math.ceil(rv);
		Map<Long, WorldChunk> cache = new java.util.HashMap<>();
		for (int dx = -ih; dx <= ih; dx++) {
			for (int dz = -ih; dz <= ih; dz++) {
				for (int dy = -iv; dy <= iv; dy++) {
					double d = (dx * dx + dz * dz) / (rh * rh) + (dy * dy) / (rv * rv);
					if (d > 1.0) {
						continue;
					}
					int x = cx + dx, y = cy + dy, z = cz + dz;
					long key = ChunkPos.toLong(x >> 4, z >> 4);
					if (!j.chunkSet.contains(key)) {
						continue;
					}
					WorldChunk chunk = cache.computeIfAbsent(key, k -> j.world.getChunk(x >> 4, z >> 4));
					BlockState s = getRaw(chunk, x, y, z);
					if (s.isAir() || s.isOf(Blocks.BEDROCK) || s.isOf(Blocks.WATER) || s.getFluidState().isStill()) {
						continue;
					}
					// не вскрывать озёра и ручьи: рядом с водой копать нельзя
					if (getRaw(chunk, x, y + 1, z).isOf(Blocks.WATER)) {
						continue;
					}
					setRaw(chunk, x, y, z, Blocks.AIR.getDefaultState());
					j.carvedBlocks++;
					chunk.setNeedsSaving(true);
				}
			}
		}
	}

	// ---- этап 3: освещение ----

	/** Рельеф записан прямо в секции чанков, поэтому клиентам он сам не уходит: пересылаем чанки наблюдателям. */
	private static void stepResend(Job j, Phase next) {
		for (int n = 0; n < 24 && j.index < j.chunks.size(); n++) {
			resend(j.world, j.chunks.get(j.index++));
		}
		if (j.index >= j.chunks.size()) {
			j.phase = next;
			j.index = 0;
			j.treeCell = 0;
		}
	}

	static void resend(ServerWorld world, ChunkPos cp) {
		WorldChunk chunk = world.getChunk(cp.x, cp.z);
		java.util.List<ServerPlayerEntity> watchers = world.getChunkManager().threadedAnvilChunkStorage.getPlayersWatchingChunk(cp, false);
		if (watchers.isEmpty()) {
			return;
		}
		net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket packet = new net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null);
		for (ServerPlayerEntity p : watchers) {
			p.networkHandler.sendPacket(packet);
		}
	}

	/** Процент выполнения по этапам, показывается над хотбаром всем в строительном измерении. */
	private static void progress(MinecraftServer server, Job j) {
		double total = Math.max(1, j.chunks.size());
		int trees = Math.max(1, j.chunks.size());
		int decor = trees;
		double pct;
		String stage;
		switch (j.phase) {
			case TERRAIN -> { pct = 30 * j.index / total; stage = "рельеф и руды"; }
			case CAVES -> { pct = 30 + 10.0 * j.wormsDone / Math.max(1, j.worms.size()); stage = "пещеры"; }
			case RELIGHT -> { pct = 40 + 5 * j.index / total; stage = "освещение"; }
			case SETTLE -> { pct = 45; stage = "освещение"; }
			case RESEND -> { pct = 45 + 5 * j.index / total; stage = "отправка чанков игрокам"; }
			case TREES -> { pct = 50 + 35.0 * j.treeCell / trees; stage = "деревья"; }
			case DECOR -> { pct = 85 + 10.0 * j.decorCell / decor; stage = "трава и цветы"; }
			default -> { pct = 95 + 5 * j.index / total; stage = "финальная отправка"; }
		}
		Text text = Text.literal("Роща Люмиры: " + (int) pct + "% • " + stage);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld() == j.world || p.getUuid().equals(j.starter)) {
				p.sendMessage(text, true);
			}
		}
	}

	private static void stepRelight(Job j) {
		ServerLightingProvider lighting = (ServerLightingProvider) j.world.getChunkManager().getLightingProvider();
		for (int n = 0; n < 6 && j.index < j.chunks.size(); n++) {
			ChunkPos cp = j.chunks.get(j.index++);
			WorldChunk chunk = j.world.getChunk(cp.x, cp.z);
			finishChunk(chunk);
			ChunkSection[] sections = chunk.getSectionArray();
			for (int i = 0; i < sections.length; i++) {
				lighting.setSectionStatus(net.minecraft.util.math.ChunkSectionPos.from(cp, chunk.sectionIndexToCoord(i)), sections[i].isEmpty());
			}
			lighting.setColumnEnabled(cp, true);
			j.lights.add(lighting.light(chunk, false));
			// Свет в толще породы остался от пустого мира: пересчитываем сверху вниз по каждой колонке (как при обычной правке блока).
			for (int lx = 0; lx < 16; lx++) {
				for (int lz = 0; lz < 16; lz++) {
					int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
					lighting.checkBlock(new BlockPos(cp.getStartX() + lx, top - 1, cp.getStartZ() + lz));
				}
			}
		}
		if (j.index >= j.chunks.size()) {
			j.phase = Phase.SETTLE;
			j.index = 0;
			j.settle = 100;
		}
	}

	// ---- этап 4: деревья ----

	private static final int CELL = 6;

	private static String[] speciesIds() {
		String[] ids = new String[LumiraIds.TREES.size() + 2];
		for (int i = 0; i < LumiraIds.TREES.size(); i++) {
			ids[i] = LumiraIds.TREES.get(i).id();
		}
		ids[LumiraIds.TREES.size()] = "oak";
		ids[LumiraIds.TREES.size() + 1] = "birch";
		return ids;
	}

	/** Вес породы в точке: целевая доля × соответствие влажности, высоте и плотности. */
	private static double weight(String id, LumiraTerrain.Column c, double elev, double density) {
		double wet = c.moisture();
		return switch (id) {
			case "lumira_willow" -> 16 * (0.25 + 1.6 * wet);
			case "zarelist" -> 14 * (0.6 + 0.8 * (1 - Math.abs(density - 0.45) * 1.5)) * (0.7 + 0.5 * c.hill());
			case "twilight_cedar" -> 10 * (0.2 + 2.0 * Math.max(c.hill(), c.mountain()));
			case "star_birch" -> 10 * (0.4 + 1.2 * elev + 0.6 * (1 - density));
			case "crimson_elm" -> 10 * (0.7 + 0.6 * (1 - Math.abs(elev - 0.4) * 2));
			case "moon_ash" -> 8 * (0.4 + 1.0 * (1 - density) + 0.6 * wet);
			case "malachite_sequoia" -> 4 * Math.pow(density, 2.5) * (1 - c.mountain());
			case "oak" -> 15 * (0.8 + 0.4 * density);
			case "birch" -> 10 * (0.5 + 1.0 * (1 - density));
			default -> 3 * (0.2 + 2.0 * c.hill());
		};
	}

	private static String pick(Job j, int x, int z, LumiraTerrain.Column c, double density, Random r) {
		String[] ids = speciesIds();
		double elev = LumiraNoise.clamp01((c.height() - 62) / 90.0);
		// естественные группы: доминирующая порода ячейки 14×14 + смешанные края
		int gx = Math.floorDiv(x, 14);
		int gz = Math.floorDiv(z, 14);
		Random cell = Random.create(j.seed ^ (gx * 73856093L) ^ (gz * 19349663L));
		LumiraTerrain.Column cc = j.terrain.sample(gx * 14 + 7, gz * 14 + 7);
		String dominant = weighted(ids, cc, elev, density, cell);
		if (r.nextInt(100) < 62) {
			return dominant;
		}
		return weighted(ids, c, elev, density, r);
	}

	private static String weighted(String[] ids, LumiraTerrain.Column c, double elev, double density, Random r) {
		double total = 0;
		double[] w = new double[ids.length];
		for (int i = 0; i < ids.length; i++) {
			w[i] = Math.max(0.01, weight(ids[i], c, elev, density));
			total += w[i];
		}
		double v = r.nextDouble() * total;
		for (int i = 0; i < ids.length; i++) {
			v -= w[i];
			if (v <= 0) {
				return ids[i];
			}
		}
		return ids[ids.length - 1];
	}

	private static void stepTrees(Job j) {
		int budget = 6;
		java.util.function.Predicate<BlockPos> allowed = p -> j.chunkSet.contains(ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4));
		while (budget-- > 0 && j.treeCell < j.chunks.size()) {
			ChunkPos cp = j.chunks.get(j.treeCell++);
			Random r = Random.create(chunkSeed(j, cp, 0xF00D));
			for (int ci = 0; ci < 3; ci++) {
				for (int cj = 0; cj < 3; cj++) {
					int x = cp.getStartX() + ci * 5 + r.nextInt(5);
					int z = cp.getStartZ() + cj * 5 + r.nextInt(5);
					LumiraTerrain.Column c = j.terrain.sample(x, z);
					if (c.underwater() || c.height() <= j.terrain.sea() + 1 || c.mountain() > 0.7) {
						continue;
					}
					double density = j.terrain.forestDensity(c, x, z);
					if (r.nextDouble() > density * 0.95) {
						continue;
					}
					BlockPos ground = new BlockPos(x, c.height(), z);
					if (!j.world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK)) {
						continue;
					}
					String species = pick(j, x, z, c, density, r);
					BlockPos base = ground.up();
					boolean ok;
					LumiraIds.Tree tree = LumiraTreeGen.find(species);
					if (tree != null) {
						ok = LumiraTreeGen.generate(j.world, base, tree, r, allowed);
					} else {
						LumiraTreeGen.Plan plan = LumiraTreeGen.vanilla(species, base, r);
						ok = plan.allInside(allowed) && LumiraTreeGen.fitsStrict(j.world, plan);
						if (ok) {
							LumiraTreeGen.place(j.world, plan);
						}
					}
					if (ok) {
						j.trees.merge(species, 1, Integer::sum);
					}
				}
			}
		}
		if (j.treeCell >= j.chunks.size()) {
			j.phase = Phase.DECOR;
			j.decorCell = 0;
		}
	}

	// ---- этап 5: трава и цветы ----

	private static void stepDecor(Job j) {
		int budget = 8;
		Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY};
		while (budget-- > 0 && j.decorCell < j.chunks.size()) {
			ChunkPos cp = j.chunks.get(j.decorCell++);
			Random r = Random.create(chunkSeed(j, cp, 0xBEEF));
			for (int dx = 0; dx < 16; dx += 2) {
				for (int dz = 0; dz < 16; dz += 2) {
					int x = cp.getStartX() + dx + r.nextInt(2);
					int z = cp.getStartZ() + dz + r.nextInt(2);
					LumiraTerrain.Column c = j.terrain.sample(x, z);
					if (c.underwater() || c.height() <= j.terrain.sea() + 1) {
						continue;
					}
					BlockPos ground = new BlockPos(x, c.height(), z);
					if (!j.world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK) || !j.world.getBlockState(ground.up()).isAir()) {
						continue;
					}
					if (r.nextDouble() < 0.10 + 0.30 * c.meadow()) {
						boolean flower = r.nextInt(100) < (c.meadow() > 0.4 ? 22 : 6);
						Block plant = flower ? flowers[r.nextInt(flowers.length)] : Blocks.GRASS;
						j.world.setBlockState(ground.up(), plant.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
						if (flower) {
							j.flowers++;
						}
					}
				}
			}
			replaceAlbionTerrain(j, j.world.getChunk(cp.x, cp.z), cp);
		}
		if (j.decorCell >= j.chunks.size()) {
			j.phase = Phase.RESEND_FINAL;
			j.index = 0;
		}
	}

	/** Final new-chunk pass: catches terrain, feature and structure blocks without ever touching saved chunks later. */
	private static void replaceAlbionTerrain(Job j, WorldChunk chunk, ChunkPos cp) {
		int sx = cp.getStartX(), sz = cp.getStartZ();
		for (int y = chunk.getBottomY(); y < chunk.getTopY(); y++) {
			for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
				int x = sx + lx, z = sz + lz;
				BlockState state = getRaw(chunk, x, y, z);
				BlockState replacement = null;
				if (state.isOf(Blocks.GRASS_BLOCK)) replacement = LumiraContent.ALBION_GRASS.getDefaultState();
				else if (state.isOf(Blocks.DIRT)) replacement = LumiraContent.ALBION_DIRT.getDefaultState();
				else if (state.isOf(Blocks.COBBLESTONE)) replacement = LumiraContent.ALBION_COBBLESTONE.getDefaultState();
				else if (state.isOf(Blocks.STONE) && albionStone(j.seed, x, y, z)) replacement = LumiraContent.ALBION_STONE.getDefaultState();
				if (replacement != null) setRaw(chunk, x, y, z, replacement);
			}
		}
		finishChunk(chunk);
	}

	/** Deterministic 75/25 choice. Y participates only in the avalanche hash, never as a threshold or layer. */
	static boolean albionStone(long seed, int x, int y, int z) {
		long v = seed ^ ((long)x * 0x9E3779B97F4A7C15L) ^ ((long)y * 0xC2B2AE3D27D4EB4FL) ^ ((long)z * 0x165667B19E3779F9L);
		v ^= v >>> 30; v *= 0xBF58476D1CE4E5B9L; v ^= v >>> 27; v *= 0x94D049BB133111EBL; v ^= v >>> 31;
		return Long.remainderUnsigned(v, 4) != 0;
	}

	public static Set<String> speciesSet() {
		return new HashSet<>(List.of(speciesIds()));
	}
}
