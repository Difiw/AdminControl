package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwDurableTest {
	// Created in the constructor so the tests also run under the project's MiniRunner (no JUnit extension model needed).
	final Path dir = tempDir();
	private static Path tempDir() { try { return Files.createTempDirectory("cwtest"); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }

	@Test void roundTripKeepsPayloadAndWritesChecksummedEnvelope() throws Exception {
		Path file = dir.resolve("state.json");
		assertTrue(CwDurable.write(file, "{\"a\":1,\"nbt\":\"{Inventory:[{id:\\\"minecraft:stone\\\",Count:3b}]}\"}"));
		String raw = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(raw.contains("\"sha256\"") && raw.contains("\"cwEnvelope\""));
		CwDurable.Loaded loaded = CwDurable.read(file);
		assertEquals(CwDurable.Source.MAIN, loaded.source());
		assertTrue(loaded.json().contains("minecraft:stone"));
	}

	@Test void legacyPlainJsonIsStillReadable() throws Exception {
		Path file = dir.resolve("old.json");
		Files.writeString(file, "{\"player\":{\"phase\":\"ACTIVE\"}}", StandardCharsets.UTF_8);
		CwDurable.Loaded loaded = CwDurable.read(file);
		assertEquals(CwDurable.Source.LEGACY, loaded.source());
		assertTrue(loaded.json().contains("ACTIVE"));
	}

	@Test void secondWriteKeepsPreviousVersionAsBackup() throws Exception {
		Path file = dir.resolve("p.json");
		assertTrue(CwDurable.write(file, "{\"v\":1}"));
		assertTrue(CwDurable.write(file, "{\"v\":2}"));
		assertTrue(CwDurable.read(file).json().contains("2"));
		assertTrue(CwDurable.read(CwDurable.backupOf(file)).json().contains("1"));
	}

	@Test void tamperedFileFallsBackToBackupAndIsQuarantinedNotDeleted() throws Exception {
		Path file = dir.resolve("p.json");
		CwDurable.write(file, "{\"v\":1}");
		CwDurable.write(file, "{\"v\":2}");
		String tampered = Files.readString(file, StandardCharsets.UTF_8).replace("\"v\": 2", "\"v\": 3");
		Files.writeString(file, tampered, StandardCharsets.UTF_8);
		CwDurable.Loaded loaded = CwDurable.read(file);
		assertEquals(CwDurable.Source.BACKUP, loaded.source());
		assertTrue(loaded.json().contains("1"));
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "damaged file must be kept for analysis");
		}
	}

	@Test void truncatedFileWithoutBackupIsReportedUnreadableNeverEmptyState() throws Exception {
		Path file = dir.resolve("p.json");
		CwDurable.write(file, "{\"v\":1}");
		assertFalse(Files.exists(CwDurable.backupOf(file)), "a first write has no previous version to back up");
		String text = Files.readString(file, StandardCharsets.UTF_8);
		Files.writeString(file, text.substring(0, text.length() / 2), StandardCharsets.UTF_8);
		CwDurable.Loaded loaded = CwDurable.read(file);
		assertFalse(loaded.present());
		assertEquals(CwDurable.Source.UNREADABLE, loaded.source());
	}

	@Test void damagedMainDoesNotOverwriteGoodBackupOnNextWrite() throws Exception {
		Path file = dir.resolve("p.json");
		CwDurable.write(file, "{\"v\":1}");
		CwDurable.write(file, "{\"v\":2}");
		Files.writeString(file, "garbage", StandardCharsets.UTF_8);
		assertTrue(CwDurable.write(file, "{\"v\":3}"));
		assertTrue(CwDurable.read(CwDurable.backupOf(file)).json().contains("1"), "backup of a damaged file must not replace the last good copy");
	}

	@Test void leftoverTempFileFromACrashIsIgnored() throws Exception {
		Path file = dir.resolve("p.json");
		CwDurable.write(file, "{\"v\":1}");
		Files.writeString(file.resolveSibling("p.json.tmp"), "{half written", StandardCharsets.UTF_8);
		assertEquals(CwDurable.Source.MAIN, CwDurable.read(file).source());
		assertTrue(CwDurable.write(file, "{\"v\":2}"));
		assertTrue(CwDurable.read(file).json().contains("2"));
	}

	@Test void missingFileIsMissingNotUnreadable() {
		assertEquals(CwDurable.Source.MISSING, CwDurable.read(dir.resolve("nope.json")).source());
	}

	@Test void rawWriteKeepsOnlyValidPreviousVersion() throws Exception {
		Path file = dir.resolve("save.zip");
		byte[] first = {1, 2, 3}, second = {4, 5, 6};
		assertTrue(CwDurable.writeRaw(file, first, p -> true));
		assertTrue(CwDurable.writeRaw(file, second, p -> true));
		assertArrayEquals(second, Files.readAllBytes(file));
		assertArrayEquals(first, Files.readAllBytes(CwDurable.backupOf(file)));
		assertTrue(CwDurable.writeRaw(file, new byte[] {7}, p -> false)); // previous judged invalid: backup stays at the last good copy
		assertArrayEquals(first, Files.readAllBytes(CwDurable.backupOf(file)));
	}

	@Test void appendLineAccumulatesJournal() throws Exception {
		Path file = dir.resolve("journal/t.jsonl");
		assertTrue(CwDurable.appendLine(file, "{\"n\":1}"));
		assertTrue(CwDurable.appendLine(file, "{\"n\":2}"));
		assertEquals(2, Files.readAllLines(file).size());
		assertNotNull(Files.readString(file));
	}
}
