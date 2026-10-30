package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.admincontrol.AdminControlMod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Crash-safe JSON files for everything CW Expedition keeps on disk.
 *
 * <p>A write goes: payload -> envelope with format version and SHA-256 -> temporary file in the same folder -> fsync ->
 * the previous valid file is copied to {@code <name>.bak} -> atomic rename over the target -> best-effort fsync of the
 * folder. A read verifies the checksum; if the main file is damaged the backup is tried, and the damaged file is moved
 * aside as {@code <name>.corrupt-<timestamp>} (never deleted) so it can be analysed. Files written by earlier versions
 * (plain JSON without an envelope) stay readable and are upgraded on their next write.</p>
 */
public final class CwDurable {
	public static final int ENVELOPE_VERSION = 2;
	private static final String KEY_VERSION = "cwEnvelope", KEY_SUM = "sha256", KEY_PAYLOAD = "payload";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

	/** Where a successful read came from. */
	public enum Source { MAIN, BACKUP, LEGACY, MISSING, UNREADABLE }

	/** Result of {@link #read}: {@code json} is the payload text, or null when {@code source} is MISSING / UNREADABLE. */
	public record Loaded(String json, Source source, String note) {
		public boolean present() { return json != null; }
	}

	private CwDurable() {}

	public static Path backupOf(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }

	private static final java.util.concurrent.ConcurrentHashMap<Path, String> PENDING = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Path, Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "cw-durable-writer");
		t.setDaemon(true);
		return t;
	});

	private static Object lockFor(Path file) { return LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), k -> new Object()); }

	/**
	 * Non-blocking variant for the once-per-second state saves: the payload is already a string (taken on the server
	 * thread), the disk work happens on a writer thread. Only the newest payload per file is written; a later
	 * synchronous {@link #write} supersedes anything still queued, so an old payload can never overwrite a newer one.
	 */
	public static void writeAsync(Path file, String payloadJson) {
		if (file == null || payloadJson == null) return;
		Path key = file.toAbsolutePath().normalize();
		PENDING.put(key, payloadJson);
		try {
			WRITER.execute(() -> {
				synchronized (lockFor(key)) {
					String json = PENDING.remove(key);
					if (json != null) writeNow(key, json);
				}
			});
		} catch (java.util.concurrent.RejectedExecutionException e) {
			synchronized (lockFor(key)) { String json = PENDING.remove(key); if (json != null) writeNow(key, json); }
		}
	}

	/** Writes everything still queued; called when the server stops. */
	public static void drain() {
		for (Path key : java.util.List.copyOf(PENDING.keySet())) {
			synchronized (lockFor(key)) { String json = PENDING.remove(key); if (json != null) writeNow(key, json); }
		}
	}

	/** Writes {@code payloadJson} durably. Returns false (and logs) when it could not be stored; the old file is then untouched. */
	public static boolean write(Path file, String payloadJson) {
		if (file == null || payloadJson == null) return false;
		Path key = file.toAbsolutePath().normalize();
		synchronized (lockFor(key)) {
			PENDING.remove(key);
			return writeNow(file, payloadJson);
		}
	}

	private static boolean writeNow(Path file, String payloadJson) {
		try {
			JsonElement payload = JsonParser.parseString(payloadJson);
			JsonObject envelope = new JsonObject();
			envelope.addProperty(KEY_VERSION, ENVELOPE_VERSION);
			envelope.addProperty(KEY_SUM, checksum(payload));
			envelope.add(KEY_PAYLOAD, payload);
			byte[] bytes = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buf = ByteBuffer.wrap(bytes);
				while (buf.hasRemaining()) ch.write(buf);
				ch.force(true);
			}
			// Keep the previous version only if it is itself valid: a damaged main file must never overwrite a good backup.
			if (Files.isRegularFile(file) && isValid(file)) Files.copy(file, backupOf(file), StandardCopyOption.REPLACE_EXISTING);
			try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
			syncDirectory(dir);
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] durable write failed file={}", file, e);
			return false;
		}
	}

	/** Reads the payload text of {@code file}: main file, else backup, else legacy plain JSON. Corrupt files are quarantined. */
	public static Loaded read(Path file) {
		if (file == null) return new Loaded(null, Source.MISSING, "no path");
		Loaded main = readOne(file);
		if (main.present()) return main;
		Loaded backup = readOne(backupOf(file));
		boolean hadMain = Files.isRegularFile(file);
		if (hadMain) quarantine(file);
		if (backup.present()) {
			AdminControlMod.LOGGER.warn("[CW] {} was unreadable ({}); restored from backup", file.getFileName(), main.note());
			return new Loaded(backup.json(), Source.BACKUP, main.note());
		}
		if (hadMain) return new Loaded(null, Source.UNREADABLE, main.note());
		return new Loaded(null, Source.MISSING, "file does not exist");
	}

	private static Loaded readOne(Path file) {
		if (!Files.isRegularFile(file)) return new Loaded(null, Source.MISSING, "missing");
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (text.isBlank()) return new Loaded(null, Source.UNREADABLE, "empty file");
			JsonElement root = JsonParser.parseString(text);
			if (root.isJsonObject() && root.getAsJsonObject().has(KEY_VERSION) && root.getAsJsonObject().has(KEY_PAYLOAD)) {
				JsonObject envelope = root.getAsJsonObject();
				JsonElement payload = envelope.get(KEY_PAYLOAD);
				String want = envelope.has(KEY_SUM) ? envelope.get(KEY_SUM).getAsString() : "";
				if (!want.equals(checksum(payload))) return new Loaded(null, Source.UNREADABLE, "checksum mismatch");
				return new Loaded(GSON.toJson(payload), Source.MAIN, "ok");
			}
			// Gson parses leniently: a stray word would come back as a JSON string. Only an object or array can be a store written by an older version.
			if (!root.isJsonObject() && !root.isJsonArray()) return new Loaded(null, Source.UNREADABLE, "not a json object");
			return new Loaded(text, Source.LEGACY, "legacy plain json");
		} catch (Exception e) {
			return new Loaded(null, Source.UNREADABLE, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean isValid(Path file) { return readOne(file).present(); }

	/** Moves a damaged file out of the way (kept for analysis). */
	public static Path quarantine(Path file) {
		try {
			Path target = file.resolveSibling(file.getFileName() + ".corrupt-" + STAMP.format(Instant.now()));
			Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
			AdminControlMod.LOGGER.error("[CW] damaged file kept for analysis: {}", target);
			return target;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] could not quarantine {}", file, e);
			return null;
		}
	}

	public static String checksum(JsonElement payload) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void syncDirectory(Path dir) {
		try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) { ch.force(true); }
		catch (IOException | UnsupportedOperationException ignored) { /* Windows cannot open a folder for fsync; the rename itself is already atomic */ }
	}

	/** Durable write of an opaque file (a zip archive): fsynced temp file, previous version kept as .bak only if {@code valid} accepts it, atomic rename. */
	public static boolean writeRaw(Path file, byte[] data, java.util.function.Predicate<Path> valid) {
		try {
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buf = ByteBuffer.wrap(data);
				while (buf.hasRemaining()) ch.write(buf);
				ch.force(true);
			}
			if (Files.isRegularFile(file) && valid.test(file)) Files.copy(file, backupOf(file), StandardCopyOption.REPLACE_EXISTING);
			try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
			syncDirectory(dir);
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] durable raw write failed file={}", file, e);
			return false;
		}
	}

	/** Payload text of a durable file for callers that already treat an exception as "unreadable"; legacy plain JSON is accepted. */
	public static String text(Path file) throws IOException {
		Loaded loaded = read(file);
		if (!loaded.present()) throw new IOException(file.getFileName() + ": " + loaded.note());
		return loaded.json();
	}

	/** Appends one line to an append-only log and fsyncs it. */
	public static boolean appendLine(Path file, String line) {
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
				ByteBuffer buf = ByteBuffer.wrap((line + "\n").getBytes(StandardCharsets.UTF_8));
				while (buf.hasRemaining()) ch.write(buf);
				ch.force(false);
			}
			return true;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] journal append failed file={}", file, e);
			return false;
		}
	}
}
