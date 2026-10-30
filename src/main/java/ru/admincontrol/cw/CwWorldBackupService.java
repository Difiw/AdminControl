package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.StorageIoWorker;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.mixin.EntityChunkDataAccessAccessor;
import ru.admincontrol.mixin.RegionBasedStorageAccessor;
import ru.admincontrol.mixin.SerializingRegionBasedStorageAccessor;
import ru.admincontrol.mixin.ServerEntityManagerAccessor;
import ru.admincontrol.mixin.ServerWorldEntityManagerAccessor;
import ru.admincontrol.mixin.StorageIoWorkerAccessor;
import ru.admincontrol.mixin.VersionedChunkStorageAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Full file checkpoints of a player's personal story world.
 *
 * <p><b>Save</b>: the world is flushed through Minecraft (chunks, entities, POI), then every file of the dimension folder
 * (region, entities, poi, data) is copied into {@code cw-world-backups/<player uuid>/<instance id>/<id>.partial}, a manifest
 * with sizes and SHA-256 is written, and the folder is published by an atomic rename. The copy runs on the server thread
 * right after the flush, so no chunk write can interleave with it.</p>
 *
 * <p><b>Load</b>: a protective backup of the current state is taken, the player is moved to the waiting dimension, the job
 * waits until the world has no loaded chunks, flushes it, closes the region files and drops their caches, replaces the
 * dimension folder from the checked backup (a journal makes an interrupted swap resumable), and only then hands the player
 * back to the caller. A missing or damaged backup, a timeout or a disk error never touch the current world.</p>
 */
public final class CwWorldBackupService {
	public static final String MANIFEST = "manifest.json";
	private static final int WAIT_TICKS = 600, KEEP_AUTO = 3, KEEP_MANUAL = 10, KEEP_PROTECT = 3;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
	private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();

	public record FileEntry(String path, long size, String sha256) {}

	public static final class Manifest {
		public int version = 1;
		public String id, kind, owner, instanceId, world;
		public long createdAt;
		public List<FileEntry> files = new ArrayList<>();
	}

	private static final class Job {
		UUID player; CwStoryService.Snapshot snapshot; ServerWorld world; Path backup; Consumer<String> done; int waited;
	}

	private CwWorldBackupService() {}

	// ---- paths ----

	public static Path worldDir(MinecraftServer server, ServerWorld world) {
		return DimensionType.getSaveDirectory(world.getRegistryKey(), server.getSavePath(WorldSavePath.ROOT).normalize());
	}

	public static Path baseDir(MinecraftServer server, UUID owner, String instanceId) {
		return server.getSavePath(WorldSavePath.ROOT).normalize().resolve("cw-world-backups").resolve(owner.toString()).resolve(instanceId);
	}

	// ---- save ----

	/** Writes a full file checkpoint. Returns its id, or null when it could not be completed (nothing half-written is kept). */
	public static String snapshot(MinecraftServer server, ServerWorld world, UUID owner, String instanceId, String kind) {
		if (server == null || world == null || owner == null || instanceId == null) return null;
		Path partial = null;
		try {
			world.save(null, true, false); // consistent state on disk before the copy
			String id = kind + "-" + STAMP.format(Instant.now());
			Path base = baseDir(server, owner, instanceId);
			Files.createDirectories(base);
			partial = base.resolve(id + ".partial");
			Manifest m = new Manifest();
			m.id = id; m.kind = kind; m.owner = owner.toString(); m.instanceId = instanceId;
			m.world = world.getRegistryKey().getValue().toString(); m.createdAt = System.currentTimeMillis();
			Path src = worldDir(server, world);
			Path dstWorld = partial.resolve("world");
			Files.createDirectories(dstWorld);
			if (Files.isDirectory(src)) m.files.addAll(copyTree(src, dstWorld));
			Files.writeString(partial.resolve(MANIFEST), GSON.toJson(m), StandardCharsets.UTF_8);
			List<String> bad = verifyDir(partial);
			if (!bad.isEmpty()) throw new IOException("verification failed: " + bad);
			Path published = base.resolve(id);
			try { Files.move(partial, published, StandardCopyOption.ATOMIC_MOVE); }
			catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(partial, published); }
			prune(base, kind);
			AdminControlMod.LOGGER.info("[CW] world checkpoint {} written owner={} files={}", id, owner, m.files.size());
			return id;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] world checkpoint failed owner={} instance={}", owner, instanceId, e);
			if (partial != null) deleteTree(partial);
			return null;
		}
	}

	private static List<FileEntry> copyTree(Path src, Path dst) throws IOException {
		List<FileEntry> out = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(src)) {
			for (Path p : (Iterable<Path>) walk.sorted()::iterator) {
				Path rel = src.relativize(p);
				String name = rel.getFileName() == null ? "" : rel.getFileName().toString();
				if (name.equals("session.lock")) continue;
				Path target = dst.resolve(rel.toString());
				if (Files.isDirectory(p)) { Files.createDirectories(target); continue; }
				Files.createDirectories(target.getParent());
				Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
				out.add(new FileEntry(rel.toString().replace('\\', '/'), Files.size(target), sha(target)));
			}
		}
		return out;
	}

	// ---- verification ----

	/** Problems of a published (or partial) checkpoint folder; empty when complete and intact. */
	public static List<String> verifyDir(Path dir) {
		List<String> problems = new ArrayList<>();
		try {
			Path mf = dir.resolve(MANIFEST);
			if (!Files.isRegularFile(mf)) return List.of("manifest missing");
			Manifest m = GSON.fromJson(Files.readString(mf, StandardCharsets.UTF_8), Manifest.class);
			if (m == null || m.files == null) return List.of("manifest unreadable");
			Path world = dir.resolve("world");
			java.util.Set<String> listed = new java.util.HashSet<>();
			for (FileEntry f : m.files) {
				listed.add(f.path());
				Path file = world.resolve(f.path());
				if (!Files.isRegularFile(file)) problems.add("missing " + f.path());
				else if (Files.size(file) != f.size() || !sha(file).equals(f.sha256())) problems.add("damaged " + f.path());
			}
			if (Files.isDirectory(world)) {
				try (Stream<Path> walk = Files.walk(world)) {
					for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
						String rel = world.relativize(p).toString().replace('\\', '/');
						if (!listed.contains(rel)) problems.add("unlisted " + rel);
					}
				}
			} else problems.add("world folder missing");
		} catch (Exception e) {
			problems.add("unreadable: " + e.getMessage());
		}
		return problems;
	}

	public static boolean exists(MinecraftServer server, UUID owner, String instanceId, String id) {
		return server != null && id != null && !id.contains("..") && !id.contains("/") && !id.contains("\\")
				&& Files.isDirectory(baseDir(server, owner, instanceId).resolve(id));
	}

	// ---- load ----

	/** Test hook: the same job as a player restore, without a player (the world must empty by itself). */
	public static void startJob(ServerWorld world, Path backup, Consumer<String> done) {
		Job job = new Job();
		job.player = UUID.randomUUID(); job.world = world; job.backup = backup; job.done = done;
		JOBS.put(job.player, job);
	}

	public static boolean busy(UUID player) { return JOBS.containsKey(player); }

	/**
	 * Starts loading the given checkpoint. {@code done} gets null on success or an error text; on any error the current
	 * world files are unchanged (or, after an interrupted swap, completed from the same backup by {@link #recover}).
	 */
	public static void restore(ServerPlayerEntity p, CwStoryService.Snapshot s, Consumer<String> done) {
		MinecraftServer server = p.getServer();
		CwInstanceRegistry.Instance inst = CwStoryWorldService.instanceOf(p);
		if (server == null || inst == null || s == null || s.worldBackupId == null) { done.accept("нет файлового снимка"); return; }
		UUID owner = p.getUuid();
		if (JOBS.containsKey(owner)) { done.accept("загрузка уже выполняется"); return; }
		net.minecraft.registry.RegistryKey<net.minecraft.world.World> key = net.minecraft.registry.RegistryKey.of(
				net.minecraft.registry.RegistryKeys.WORLD, new net.minecraft.util.Identifier(inst.worldKey));
		ServerWorld world = server.getWorld(key);
		if (world == null) { done.accept("мир сюжета недоступен"); return; }
		if (!exists(server, owner, inst.instanceId, s.worldBackupId)) { done.accept("копия мира отсутствует"); return; }
		Path backup = baseDir(server, owner, inst.instanceId).resolve(s.worldBackupId);
		List<String> problems = verifyDir(backup);
		if (!problems.isEmpty()) { done.accept("копия мира повреждена: " + problems.get(0)); return; }
		if (snapshot(server, world, owner, inst.instanceId, "protect") == null) { done.accept("не удалось сохранить защитную копию"); return; }
		if (!CwRespawnService.inLimbo(p) && !CwRespawnService.sendToLimbo(p)) { done.accept("зона ожидания недоступна"); return; }
		Job job = new Job();
		job.player = owner; job.snapshot = s; job.world = world; job.backup = backup; job.done = done;
		JOBS.put(owner, job);
	}

	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) return;
		for (Job job : List.copyOf(JOBS.values())) {
			ServerChunkManager cm = job.world.getChunkManager();
			boolean empty = cm.getLoadedChunkCount() == 0 && job.world.getPlayers().isEmpty();
			if (!empty && ++job.waited < WAIT_TICKS) continue;
			JOBS.remove(job.player);
			if (!empty) { job.done.accept("мир не освободился за " + WAIT_TICKS / 20 + " с"); continue; }
			String error = swap(server, job);
			job.done.accept(error);
		}
	}

	private static String swap(MinecraftServer server, Job job) {
		Path dir = worldDir(server, job.world);
		Path journal = job.backup.resolveSibling("journal.json");
		try {
			Files.writeString(journal, job.backup.getFileName().toString() + "\n" + dir, StandardCharsets.UTF_8);
			releaseFiles(job.world);
			replaceTree(dir, job.backup.resolve("world"));
			Files.deleteIfExists(journal);
			AdminControlMod.LOGGER.info("[CW] world files restored from {}", job.backup.getFileName());
			return null;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] world file swap failed; the journal stays so it can be completed on the next start", e);
			return "не удалось заменить файлы мира";
		}
	}

	/** Flushes the world and closes every region file it holds, dropping the caches so they are re-opened on demand. */
	public static void releaseFiles(ServerWorld world) throws IOException {
		world.save(null, true, false);
		ServerChunkManager cm = world.getChunkManager();
		release(((VersionedChunkStorageAccessor) (Object) cm.threadedAnvilChunkStorage).adminControl$worker());
		release(((SerializingRegionBasedStorageAccessor) (Object) cm.getPointOfInterestStorage()).adminControl$worker());
		Object access = ((ServerEntityManagerAccessor) (Object) ((ServerWorldEntityManagerAccessor) (Object) world).adminControl$entityManager()).adminControl$dataAccess();
		if (access instanceof net.minecraft.world.storage.EntityChunkDataAccess) {
			EntityChunkDataAccessAccessor a = (EntityChunkDataAccessAccessor) access;
			release(a.adminControl$worker());
			a.adminControl$emptyChunks().clear();
		}
	}

	private static void release(StorageIoWorker worker) throws IOException {
		worker.completeAll(true).join();
		net.minecraft.world.storage.RegionBasedStorage storage = ((StorageIoWorkerAccessor) worker).adminControl$storage();
		storage.close();
		((RegionBasedStorageAccessor) (Object) storage).adminControl$cached().clear();
	}

	static void replaceTree(Path dir, Path from) throws IOException {
		if (Files.isDirectory(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
					if (p.equals(dir) || p.getFileName().toString().equals("session.lock")) continue;
					Files.deleteIfExists(p);
				}
			}
		}
		Files.createDirectories(dir);
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : (Iterable<Path>) walk.sorted()::iterator) {
				Path target = dir.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else { Files.createDirectories(target.getParent()); Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING); }
			}
		}
	}

	/** Server start: finishes a swap that was interrupted by a crash, from the same (verified) backup. Chunks are not loaded yet. */
	public static void recover(MinecraftServer server) {
		Path root = server.getSavePath(WorldSavePath.ROOT).normalize().resolve("cw-world-backups");
		if (!Files.isDirectory(root)) return;
		try (Stream<Path> walk = Files.walk(root, 3)) {
			for (Path journal : walk.filter(p -> p.getFileName().toString().equals("journal.json")).toList()) {
				List<String> lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
				if (lines.size() < 2) continue;
				Path backup = journal.resolveSibling(lines.get(0));
				Path dir = Path.of(lines.get(1));
				if (!verifyDir(backup).isEmpty()) { AdminControlMod.LOGGER.error("[CW] interrupted restore cannot be completed: backup {} is damaged", backup); continue; }
				replaceTree(dir, backup.resolve("world"));
				Files.deleteIfExists(journal);
				AdminControlMod.LOGGER.warn("[CW] interrupted world restore completed from {}", backup.getFileName());
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] world restore recovery failed", e);
		}
	}

	// ---- housekeeping ----

	private static void prune(Path base, String kind) {
		int keep = kind.equals("auto") ? KEEP_AUTO : kind.equals("manual") ? KEEP_MANUAL : KEEP_PROTECT;
		try (Stream<Path> list = Files.list(base)) {
			List<Path> mine = list.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith(kind + "-")
					&& !p.getFileName().toString().endsWith(".partial")).sorted().toList();
			for (int i = 0; i < mine.size() - keep; i++) deleteTree(mine.get(i));
		} catch (IOException e) {
			AdminControlMod.LOGGER.warn("[CW] checkpoint pruning failed: {}", e.getMessage());
		}
	}

	static void deleteTree(Path root) {
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		} catch (IOException ignored) { /* leftover partial folders are harmless and ignored by listings */ }
	}

	private static String sha(Path file) throws IOException {
		MessageDigest md;
		try { md = MessageDigest.getInstance("SHA-256"); }
		catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}
}
