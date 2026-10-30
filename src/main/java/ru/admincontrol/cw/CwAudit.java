package ru.admincontrol.cw;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class CwAudit {
	private CwAudit() {}
	public static synchronized void write(ServerCommandSource source, String command, String target, String result) {
		writeLine(source.getEntity() == null ? "CONSOLE" : source.getName(), AdminRanks.title(CommandUtil.sourceRank(source)), command, target, result);
	}
	public static synchronized void writeSystem(String actor, String action, String target, String result) {
		writeLine(actor == null ? "SYSTEM" : actor, "runtime", action, target, result);
	}
	private static void writeLine(String actor, String rank, String command, String target, String result) {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("admincontrol").resolve("logs");
			Files.createDirectories(dir);
			String line = Instant.now() + "\tactor=" + clean(actor) + "\trank=" + clean(rank)
					+ "\tcommand=" + clean(command) + "\ttarget=" + clean(target) + "\tresult=" + clean(result) + System.lineSeparator();
			Files.writeString(dir.resolve("cw-admin-audit.log"), line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {}
	}
	private static String clean(String value) { return value == null ? "-" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '); }
}
