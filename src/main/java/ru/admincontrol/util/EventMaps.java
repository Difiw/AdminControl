package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Карты МП, изменённые разработчиком в /devent: блоки всего объёма карты (с содержимым сундуков и т. п.)
 * хранятся в config/…/event_maps/&lt;ключ МП&gt;.nbt и ставятся вместо стандартной карты при запуске МП.
 * Черновик (недоделанная правка) — event_maps/drafts/&lt;ключ&gt;.nbt: на нём МП не запускается.
 */
public final class EventMaps {
	private static final Map<Path, Snapshot> CACHE = new HashMap<>();
	/** Черновики пишутся в фоне по одному: снимок блоков снимается в тике сервера, на диск — здесь. */
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AdminControl-EventMaps");
		t.setDaemon(true);
		return t;
	});

	private EventMaps() {
	}

	public static final class Snapshot {
		public final int sx;
		public final int sy;
		public final int sz;
		private final BlockState[] palette;
		private final int[] cells;
		private final Map<Integer, NbtCompound> entities;

		private Snapshot(int sx, int sy, int sz, BlockState[] palette, int[] cells, Map<Integer, NbtCompound> entities) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.palette = palette;
			this.cells = cells;
			this.entities = entities;
		}

		/** Клетки идут столбцами снизу вверх: индекс = (x * sz + z) * sy + y. */
		public BlockState state(int index) {
			int id = this.cells[index];
			return id >= 0 && id < this.palette.length ? this.palette[id] : Blocks.AIR.getDefaultState();
		}

		public NbtCompound blockEntity(int index) {
			NbtCompound nbt = this.entities.get(index);
			return nbt == null ? null : nbt.copy();
		}

		public boolean fits(int sx, int sy, int sz) {
			return this.sx == sx && this.sy == sy && this.sz == sz;
		}
	}

	private record Captured(NbtCompound root, Snapshot snapshot) {
	}

	private static Path dir() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("event_maps");
	}

	private static String safe(String key) {
		return key == null ? "_" : key.replaceAll("[^A-Za-z0-9_\\-]", "_");
	}

	private static Path file(String key) {
		Path dir = dir();
		return dir == null ? null : dir.resolve(safe(key) + ".nbt");
	}

	private static Path draftFile(String key) {
		Path dir = dir();
		return dir == null ? null : dir.resolve("drafts").resolve(safe(key) + ".nbt");
	}

	public static boolean exists(String key) {
		Path file = file(key);
		return file != null && Files.isRegularFile(file);
	}

	public static boolean hasDraft(String key) {
		Path file = draftFile(key);
		return file != null && (CACHE.containsKey(file) || Files.isRegularFile(file));
	}

	public static Snapshot get(String key) {
		return load(file(key));
	}

	public static Snapshot draft(String key) {
		return load(draftFile(key));
	}

	private static Snapshot load(Path file) {
		Snapshot cached = file == null ? null : CACHE.get(file);
		if (cached != null) {
			return cached;
		}
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(file)) {
			Snapshot snap = read(NbtIo.readCompressed(in));
			if (snap != null) {
				CACHE.put(file, snap);
			}
			return snap;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось прочитать карту МП {}", file, e);
			return null;
		}
	}

	private static Snapshot read(NbtCompound root) {
		int sx = root.getInt("sx");
		int sy = root.getInt("sy");
		int sz = root.getInt("sz");
		int[] cells = root.getIntArray("cells");
		if (sx <= 0 || sy <= 0 || sz <= 0 || cells.length != sx * sy * sz) {
			return null;
		}
		NbtList list = root.getList("palette", NbtElement.COMPOUND_TYPE);
		BlockState[] palette = new BlockState[list.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), list.getCompound(i));
		}
		Map<Integer, NbtCompound> entities = new HashMap<>();
		NbtList bes = root.getList("entities", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bes.size(); i++) {
			NbtCompound e = bes.getCompound(i);
			entities.put(e.getInt("i"), e.getCompound("nbt"));
		}
		return new Snapshot(sx, sy, sz, palette, cells, entities);
	}

	/** Снять блоки объёма карты из мира (в тике сервера). */
	private static Captured capture(String key, EventArena arena) {
		ServerWorld world = arena == null ? null : arena.world();
		if (world == null) {
			return null;
		}
		int sx = arena.maxX - arena.minX + 1;
		int sy = arena.maxY - arena.minY + 1;
		int sz = arena.maxZ - arena.minZ + 1;
		int[] cells = new int[sx * sy * sz];
		Map<BlockState, Integer> ids = new HashMap<>();
		java.util.List<BlockState> states = new java.util.ArrayList<>();
		Map<Integer, NbtCompound> entities = new HashMap<>();
		NbtList palette = new NbtList();
		NbtList bes = new NbtList();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int i = 0;
		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				for (int y = 0; y < sy; y++) {
					pos.set(arena.minX + x, arena.minY + y, arena.minZ + z);
					BlockState state = world.getBlockState(pos);
					Integer id = ids.get(state);
					if (id == null) {
						id = states.size();
						ids.put(state, id);
						states.add(state);
						palette.add(NbtHelper.fromBlockState(state));
					}
					cells[i] = id;
					BlockEntity be = world.getBlockEntity(pos);
					if (be != null) {
						NbtCompound nbt = be.createNbt();
						entities.put(i, nbt.copy());
						NbtCompound e = new NbtCompound();
						e.putInt("i", i);
						e.put("nbt", nbt);
						bes.add(e);
					}
					i++;
				}
			}
		}
		NbtCompound root = new NbtCompound();
		root.putInt("version", 1);
		root.putString("key", key);
		root.putInt("sx", sx);
		root.putInt("sy", sy);
		root.putInt("sz", sz);
		root.put("palette", palette);
		root.putIntArray("cells", cells);
		root.put("entities", bes);
		return new Captured(root, new Snapshot(sx, sy, sz, states.toArray(new BlockState[0]), cells, entities));
	}

	static boolean write(Path file, NbtCompound root) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (OutputStream out = Files.newOutputStream(tmp)) {
				NbtIo.writeCompressed(root, out);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось записать файл карты МП {}", file, e);
			return false;
		}
	}

	/** Опубликовать карту: МП с этим ключом запускается на ней. false — запись не удалась. */
	public static boolean save(String key, EventArena arena) {
		Path file = file(key);
		Captured c = file == null ? null : capture(key, arena);
		if (c == null) {
			return false;
		}
		flush();
		if (!write(file, c.root())) {
			return false;
		}
		CACHE.put(file, c.snapshot());
		return true;
	}

	/** Сохранить черновик правки; запись на диск идёт в фоне. */
	public static boolean saveDraft(String key, EventArena arena) {
		Path file = draftFile(key);
		Captured c = file == null ? null : capture(key, arena);
		if (c == null) {
			return false;
		}
		CACHE.put(file, c.snapshot());
		WRITER.execute(() -> write(file, c.root()));
		return true;
	}

	public static void deleteDraft(String key) {
		Path file = draftFile(key);
		if (file == null) {
			return;
		}
		CACHE.remove(file);
		WRITER.execute(() -> {
			try {
				Files.deleteIfExists(file);
			} catch (Exception e) {
				AdminControlMod.LOGGER.error("Не удалось удалить черновик карты МП {}", file, e);
			}
		});
	}

	/** Дождаться фоновых записей (перед выключением сервера и публикацией). */
	public static void flush() {
		try {
			WRITER.submit(() -> {
			}).get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("Ожидание записи карт МП: {}", e.toString());
		}
	}

	/** Вернуть стандартную карту. false — файла не было или его не удалось удалить. */
	public static boolean delete(String key) {
		Path file = file(key);
		if (file == null) {
			return false;
		}
		CACHE.remove(file);
		try {
			return Files.deleteIfExists(file);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось удалить карту МП {}", file, e);
			return false;
		}
	}

	/** Файл отката мира на время правки: если сервер упадёт, при запуске блоки под картой вернутся. */
	static Path restoreFile() {
		Path dir = dir();
		return dir == null ? null : dir.resolve("editor_restore.nbt");
	}
}
