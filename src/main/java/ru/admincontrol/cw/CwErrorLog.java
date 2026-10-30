package ru.admincontrol.cw;

import ru.admincontrol.data.DataManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Full stack traces of server errors, kept next to the bug list (whose entry only has the top frames) so the real cause of
 * an error such as a failed packet handler can be read even after the server log rotated. The file is
 * {@code <config>/cw-diagnostics/server-errors.log}, rotated at 2 MB.
 */
public final class CwErrorLog {
	private static final long MAX_BYTES = 2L * 1024 * 1024;

	private CwErrorLog() {}

	public static synchronized void append(String logger, String message, Throwable thrown) {
		DataManager data = DataManager.get();
		if (data == null || thrown == null) return;
		try {
			Path dir = data.getConfigDir().resolve("cw-diagnostics");
			Files.createDirectories(dir);
			Path file = dir.resolve("server-errors.log");
			if (Files.isRegularFile(file) && Files.size(file) > MAX_BYTES) Files.move(file, dir.resolve("server-errors.old.log"), StandardCopyOption.REPLACE_EXISTING);
			StringWriter text = new StringWriter();
			text.append(Instant.now().toString()).append(" [").append(logger == null ? "" : logger).append("] ").append(message == null ? "" : message).append('\n');
			thrown.printStackTrace(new PrintWriter(text));
			text.append('\n');
			Files.writeString(file, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
			// diagnostics must never raise errors of their own
		}
	}
}
