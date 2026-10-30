package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.SecretCommands;
import ru.admincontrol.data.ActionKind;

import java.util.Locale;

public final class CommandInputLog {
	private static final ThreadLocal<String> LAST = new ThreadLocal<>();

	private CommandInputLog() {
	}

	public static void typed(ServerPlayerEntity player, String command) {
		if (player == null || command == null) {
			return;
		}
		String raw = command.trim();
		if (raw.startsWith("/")) {
			raw = raw.substring(1);
		}
		if (raw.isBlank()) {
			return;
		}
		String logged = display(raw);
		String key = player.getUuid() + "|" + logged;
		if (key.equals(LAST.get())) {
			return;
		}
		LAST.set(key);
		AdminNotify.playerAction(player, ActionKind.COMMAND, "ввёл команду /" + logged, true);
	}

	public static void endCommand() {
		LAST.remove();
	}

	public static String display(String command) {
		if (command == null) {
			return "";
		}
		String raw = command.trim();
		if (raw.startsWith("/")) {
			raw = raw.substring(1);
		}
		if (raw.isBlank()) {
			return "";
		}
		String name = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (hidesArgs(name)) {
			return name;
		}
		if (SecretCommands.isSecret(raw)) {
			return "скрытая команда";
		}
		return raw;
	}

	public static boolean hidesArgs(String name) {
		if (name == null) {
			return false;
		}
		String n = name.toLowerCase(Locale.ROOT);
		if (n.startsWith("/")) {
			n = n.substring(1);
		}
		return "password".equals(n) || "l".equals(n) || "login".equals(n) || "setpassword".equals(n);
	}
}
