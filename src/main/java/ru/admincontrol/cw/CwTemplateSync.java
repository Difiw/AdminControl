package ru.admincontrol.cw;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Copies the authored builder map into an isolated story slot before a test starts. */
public final class CwTemplateSync {
	/** Только радиус поиска точки появления; сама карта не ограничена. */
	private static final int RADIUS = 128;
	private CwTemplateSync() {}

	public static Result copy(ServerWorld source, ServerWorld target) {
		if (source == null || target == null) return new Result(false, "Строительный или тестовый мир недоступен.", null, 0, null, null);
		// Карта безгранична: копируются все существующие чанки строительного (и уже занятые чанки тестового) мира.
		source.getChunkManager().save(true);
		target.getChunkManager().save(true);
		java.util.Set<net.minecraft.util.math.ChunkPos> sourceChunks = existingChunks(source);
		java.util.Set<net.minecraft.util.math.ChunkPos> chunks = new java.util.LinkedHashSet<>(sourceChunks);
		chunks.addAll(existingChunks(target));
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		BlockPos min = null, max = null;
		int changed = 0;
		for (net.minecraft.util.math.ChunkPos cp : chunks) {
			boolean fromSource = sourceChunks.contains(cp);
			int x0 = cp.getStartX(), z0 = cp.getStartZ();
			for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
				int top = source.getTopY();
				if (fromSource) top = Math.min(top, source.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) + 1);
				top = Math.max(top, Math.min(target.getTopY(), target.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) + 1));
				for (int y = source.getBottomY(); y <= top && y < source.getTopY(); y++) {
					cursor.set(x, y, z);
					BlockState state = source.getBlockState(cursor);
					if (!state.isAir()) {
						min = min == null ? new BlockPos(x, y, z) : new BlockPos(Math.min(min.getX(), x), Math.min(min.getY(), y), Math.min(min.getZ(), z));
						max = max == null ? new BlockPos(x, y, z) : new BlockPos(Math.max(max.getX(), x), Math.max(max.getY(), y), Math.max(max.getZ(), z));
					}
					if (!target.getBlockState(cursor).equals(state)) { target.setBlockState(cursor, state, 3); changed++; }
					// Содержимое контейнеров, таблички и прочие блочные сущности копируются вместе с блоком.
					net.minecraft.block.entity.BlockEntity sourceEntity = state.isAir() ? null : source.getBlockEntity(cursor);
					if (sourceEntity != null) {
						net.minecraft.block.entity.BlockEntity targetEntity = target.getBlockEntity(cursor);
						if (targetEntity != null) {
							targetEntity.readNbt(sourceEntity.createNbt());
							targetEntity.markDirty();
						}
					}
				}
			}
		}
		if (min == null || max == null) return new Result(false, "Строительная карта пуста: сначала поставьте хотя бы один твёрдый блок.", null, 0, null, null);
		BlockPos spawn = findSpawn(source, min, max);
		// The copy must be on disk before the instance is marked as seeded, otherwise a crash would leave a half-copied map that is never repaired.
		target.getChunkManager().save(true);
		return new Result(true, null, spawn, changed, min, max);
	}

	/** Bounds and entry point of the template without copying anything (used to adopt an instance that was seeded by an older version). */
	public static Result measure(ServerWorld source) {
		if (source == null) return new Result(false, "Строительный мир недоступен.", null, 0, null, null);
		source.getChunkManager().save(true);
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		BlockPos min = null, max = null;
		for (net.minecraft.util.math.ChunkPos cp : existingChunks(source)) {
			int x0 = cp.getStartX(), z0 = cp.getStartZ();
			for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
				int top = Math.min(source.getTopY(), source.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) + 1);
				for (int y = source.getBottomY(); y <= top && y < source.getTopY(); y++) {
					cursor.set(x, y, z);
					if (source.getBlockState(cursor).isAir()) continue;
					min = min == null ? new BlockPos(x, y, z) : new BlockPos(Math.min(min.getX(), x), Math.min(min.getY(), y), Math.min(min.getZ(), z));
					max = max == null ? new BlockPos(x, y, z) : new BlockPos(Math.max(max.getX(), x), Math.max(max.getY(), y), Math.max(max.getZ(), z));
				}
			}
		}
		if (min == null || max == null) return new Result(false, "Строительная карта пуста: сначала поставьте хотя бы один твёрдый блок.", null, 0, null, null);
		return new Result(true, null, findSpawn(source, min, max), 0, min, max);
	}

	/** Чанки мира, которые есть в region-файлах на диске, плюс уже загруженные в памяти. */
	private static java.util.Set<net.minecraft.util.math.ChunkPos> existingChunks(ServerWorld world) {
		java.util.Set<net.minecraft.util.math.ChunkPos> out = new java.util.LinkedHashSet<>();
		java.nio.file.Path root = net.minecraft.world.dimension.DimensionType.getSaveDirectory(world.getRegistryKey(),
				world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)).resolve("region");
		if (!java.nio.file.Files.isDirectory(root)) return out;
		try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(root)) {
			for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
				String[] parts = file.getFileName().toString().split("\\.");
				if (parts.length != 4 || !"mca".equals(parts[3])) continue;
				int rx, rz;
				try { rx = Integer.parseInt(parts[1]); rz = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { continue; }
				byte[] header = new byte[4096];
				try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
					if (in.readNBytes(header, 0, 4096) < 4096) continue;
				}
				for (int i = 0; i < 1024; i++) {
					if ((header[i * 4] | header[i * 4 + 1] | header[i * 4 + 2] | header[i * 4 + 3]) != 0) out.add(new net.minecraft.util.math.ChunkPos(rx * 32 + (i & 31), rz * 32 + (i >> 5)));
				}
			}
		} catch (java.io.IOException e) {
			ru.admincontrol.AdminControlMod.LOGGER.warn("[CW] region scan failed for {}", world.getRegistryKey().getValue(), e);
		}
		return out;
	}

	private static BlockPos findSpawn(ServerWorld world, BlockPos min, BlockPos max) {
		CwPointRegistry.Point newest = null;
		for (CwPointRegistry.Point point : CwPointRegistry.all().values()) {
			if (point != null && "start".equals(point.name) && CwBuilderWorld.KEY.getValue().toString().equals(point.dimension)
					&& (newest == null || point.updatedAt > newest.updatedAt)) newest = point;
		}
		if (newest != null) {
			BlockPos marked = new BlockPos(newest.blockX, newest.blockY + 1, newest.blockZ);
			if (safe(world, marked)) return marked;
		}
		for (int radius = 0; radius <= RADIUS; radius++) for (int x = -radius; x <= radius; x++) for (int z : new int[]{-radius, radius}) {
			int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos p = new BlockPos(x, y, z); if (safe(world, p)) return p;
		}
		return new BlockPos((min.getX()+max.getX())/2, max.getY()+2, (min.getZ()+max.getZ())/2);
	}

	private static boolean safe(ServerWorld world, BlockPos feet) {
		return world.getBlockState(feet.down()).isSolidBlock(world, feet.down()) && world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir();
	}

	public record Result(boolean ok, String error, BlockPos spawn, int changed, BlockPos min, BlockPos max) {}
}
