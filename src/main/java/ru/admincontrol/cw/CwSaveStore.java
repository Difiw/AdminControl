package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Story saves as compressed files: {@code <config>/cw-saves/<player uuid>/auto.zip} and {@code manual.zip}
 * (admin test sessions use {@code test-<uuid>}). Each archive holds {@code snapshot.json} (player profile, progress,
 * advancements, entities with their targets) and {@code blocks.bin} (every block and block entity of the story map).
 * A save is written atomically (temp file + move) the moment it is taken, and loading reads the archive back from
 * disk, so what is loaded is exactly what was saved. Plain {@code .json} saves of older versions are still readable.
 */
public final class CwSaveStore {
	public static final String AUTO = "auto", MANUAL = "manual";
	private static final String SNAPSHOT = "snapshot.json", BLOCKS = "blocks.bin";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private CwSaveStore() {}

	private static Path dir(UUID player, boolean test) {
		return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-saves").resolve((test ? "test-" : "") + player);
	}

	private static Path dir(ServerPlayerEntity p) { return dir(p.getUuid(), CwStoryService.inTest(p)); }

	public static boolean write(ServerPlayerEntity p, String slot, CwStoryService.Snapshot snapshot) {
		Path dir = dir(p);
		if (dir == null || snapshot == null) return false;
		try {
			Files.createDirectories(dir);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				zip.setLevel(6);
				zip.putNextEntry(new ZipEntry(SNAPSHOT));
				zip.write(GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
				if (snapshot.blocks != null) {
					zip.putNextEntry(new ZipEntry(BLOCKS));
					zip.write(snapshot.blocks);
					zip.closeEntry();
				}
			}
			Path file = dir.resolve(slot + ".zip");
			if (!CwDurable.writeRaw(file, bytes.toByteArray(), CwSaveStore::zipReadable)) return false;
			Files.deleteIfExists(dir.resolve(slot + ".json")); // an older plain save of this slot is superseded
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] save file write failed player={} slot={}", p.getUuidAsString(), slot, e);
			return false;
		}
	}

	/** Reads the slot from disk; null when there is no file or it is unreadable (logged). */
	public static CwStoryService.Snapshot read(ServerPlayerEntity p, String slot) {
		Path dir = dir(p);
		if (dir == null) return null;
		Path zipFile = dir.resolve(slot + ".zip"), jsonFile = dir.resolve(slot + ".json");
		try {
			if (Files.isRegularFile(zipFile)) {
				try { CwStoryService.Snapshot s = readZip(zipFile); if (s != null) return s; }
				catch (Exception damaged) { AdminControlMod.LOGGER.error("[CW] save archive damaged player={} slot={}; trying the previous copy", p.getUuidAsString(), slot, damaged); }
				Path backup = CwDurable.backupOf(zipFile);
				if (Files.isRegularFile(backup)) { CwDurable.quarantine(zipFile); return readZip(backup); }
			}
			if (Files.isRegularFile(jsonFile)) return GSON.fromJson(Files.readString(jsonFile, StandardCharsets.UTF_8), CwStoryService.Snapshot.class);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] save file unreadable player={} slot={}", p.getUuidAsString(), slot, e);
		}
		return null;
	}

	private static boolean zipReadable(Path file) {
		try { return readZip(file) != null; } catch (Exception e) { return false; }
	}

	private static CwStoryService.Snapshot readZip(Path file) throws Exception {
		CwStoryService.Snapshot snapshot = null;
		byte[] blocks = null;
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
				byte[] data = zip.readAllBytes();
				if (SNAPSHOT.equals(e.getName())) snapshot = GSON.fromJson(new String(data, StandardCharsets.UTF_8), CwStoryService.Snapshot.class);
				else if (BLOCKS.equals(e.getName())) blocks = data;
			}
		}
		if (snapshot != null) snapshot.blocks = blocks;
		return snapshot;
	}

	/** Base64 of every save archive of this account, for resets and backups. */
	public static Map<String, String> dump(UUID player) {
		Map<String, String> out = new LinkedHashMap<>();
		for (boolean test : new boolean[] {false, true}) {
			Path dir = dir(player, test);
			if (dir == null) continue;
			for (String slot : new String[] {AUTO, MANUAL}) {
				for (String ext : new String[] {".zip", ".json"}) {
					Path file = dir.resolve(slot + ext);
					try { if (Files.isRegularFile(file)) out.put((test ? "test-" : "") + slot + ext, Base64.getEncoder().encodeToString(Files.readAllBytes(file))); }
					catch (Exception e) { AdminControlMod.LOGGER.error("[CW] save dump failed player={} file={}", player, file.getFileName(), e); }
				}
			}
		}
		return out;
	}

	/** Deletes this account's save files (call only after a backup). */
	public static void deleteAll(UUID player) {
		for (boolean test : new boolean[] {false, true}) {
			Path dir = dir(player, test);
			if (dir == null) continue;
			for (String name : new String[] {"auto.zip", "manual.zip", "auto.json", "manual.json", "auto.zip.tmp", "manual.zip.tmp", "auto.zip.bak", "manual.zip.bak"}) {
				try { Files.deleteIfExists(dir.resolve(name)); } catch (Exception e) { AdminControlMod.LOGGER.error("[CW] save delete failed player={} file={}", player, name, e); }
			}
			try { Files.deleteIfExists(dir); } catch (Exception ignored) { /* directory not empty or already gone */ }
		}
	}
}
