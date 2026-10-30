package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;

import java.util.Locale;
import java.util.Set;

/**
 * Ванильные «чит»-команды нельзя применять к более высокому рангу
 * и к администрации от 2 уровня (СМод) и выше — тем, кто ниже.
 */
public final class VanillaTargetGuard {
	private static final ThreadLocal<Boolean> CHEAT_CTX = ThreadLocal.withInitial(() -> false);

	private static final Set<String> TARGET_CHEATS = Set.of(
			"clear", "tp", "teleport", "kill", "gamemode", "gm",
			"give", "effect", "enchant", "experience", "xp",
			"attribute", "damage", "item", "data", "loot",
			"advancement", "recipe", "spawnpoint",
			"spectate", "ride", "op", "deop", "kick",
			"ban", "ban-ip", "pardon", "pardon-ip", "execute"
	);

	private VanillaTargetGuard() {
	}

	public static void begin(String command) {
		CHEAT_CTX.set(isTargetCheat(rootName(command)));
	}

	public static void end() {
		CHEAT_CTX.set(false);
	}

	public static boolean inCheatContext() {
		return Boolean.TRUE.equals(CHEAT_CTX.get());
	}

	private static String rootName(String command) {
		if (command == null || command.isBlank()) {
			return "";
		}
		String raw = command.trim();
		if (raw.startsWith("/")) {
			raw = raw.substring(1);
		}
		int sp = raw.indexOf(' ');
		return (sp < 0 ? raw : raw.substring(0, sp)).toLowerCase(Locale.ROOT);
	}

	public static boolean isTargetCheat(String commandName) {
		if (commandName == null || commandName.isBlank()) {
			return false;
		}
		String n = commandName.toLowerCase(Locale.ROOT);
		if (n.startsWith("/")) {
			n = n.substring(1);
		}
		return TARGET_CHEATS.contains(n);
	}

	public static boolean allow(ServerCommandSource source, ServerPlayerEntity target) {
		if (source == null || target == null) {
			return true;
		}
		if (!(source.getEntity() instanceof ServerPlayerEntity actor)) {
			return true;
		}
		if (actor.getUuid().equals(target.getUuid())) {
			return true;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return true;
		}
		int exec = data.getAdminLevel(actor);
		int tgt = data.getAdminLevel(target);
		if (tgt <= AdminRanks.NONE) {
			return true;
		}
		if (tgt >= AdminRanks.SMOD && exec < tgt) {
			return false;
		}
		return exec >= tgt;
	}

	public static boolean allowByNick(ServerCommandSource source, String nick) {
		if (source == null || nick == null || nick.isBlank() || nick.startsWith("@")) {
			return true;
		}
		return CommandUtil.findOnline(source.getServer(), nick)
				.map(p -> allow(source, p))
				.orElse(true);
	}

	public static boolean allowCommand(ServerCommandSource source, String rawCommand) {
		if (source == null || rawCommand == null || rawCommand.isBlank()) {
			return true;
		}
		String raw = rawCommand.trim();
		if (raw.startsWith("/")) {
			raw = raw.substring(1);
		}
		String[] parts = raw.split("\\s+");
		if (parts.length == 0 || !isTargetCheat(parts[0])) {
			return true;
		}
		if (!(source.getEntity() instanceof ServerPlayerEntity)) {
			return true;
		}
		for (int i = 1; i < parts.length; i++) {
			String token = parts[i];
			if (token == null || token.isBlank() || token.startsWith("@") || token.startsWith("~")
					|| token.startsWith("^") || looksLikeNumber(token) || looksLikeMode(token)) {
				continue;
			}
			if (token.contains(":")) {
				continue;
			}
			if (!allowByNick(source, token)) {
				return false;
			}
		}
		return true;
	}

	private static boolean looksLikeNumber(String t) {
		try {
			Double.parseDouble(t);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean looksLikeMode(String t) {
		String s = t.toLowerCase(Locale.ROOT);
		return s.equals("survival") || s.equals("creative") || s.equals("adventure")
				|| s.equals("spectator") || s.equals("easy") || s.equals("normal")
				|| s.equals("hard") || s.equals("peaceful") || s.equals("give")
				|| s.equals("clear") || s.equals("set") || s.equals("add")
				|| s.equals("remove") || s.equals("get") || s.equals("modify")
				|| s.equals("merge") || s.equals("replace") || s.equals("entity")
				|| s.equals("block") || s.equals("storage") || s.equals("run")
				|| s.equals("as") || s.equals("at") || s.equals("positioned")
				|| s.equals("rotated") || s.equals("facing") || s.equals("anchored")
				|| s.equals("in") || s.equals("align") || s.equals("if")
				|| s.equals("unless") || s.equals("store") || s.equals("true")
				|| s.equals("false");
	}

	public static void deny(ServerCommandSource source, String nick) {
		AdminNotify.denied(source, "попытался применить чит-команду к защищённому игроку"
				+ (nick == null || nick.isBlank() ? "" : " (" + nick + ")"));
	}
}
