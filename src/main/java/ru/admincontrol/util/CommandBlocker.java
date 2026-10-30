package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;

import java.util.Locale;

public final class CommandBlocker {
	private CommandBlocker() {
	}

	public static boolean blocked(ServerPlayerEntity player, String command) {
		DataManager data = DataManager.get();
		if (data == null || command == null || command.isBlank()) {
			return false;
		}
		String name = command.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (name.startsWith("/")) {
			name = name.substring(1);
		}
		if ("acommand".equals(name) || "chcommands".equals(name) || "conchcommands".equals(name)
				|| "setcommandadmin".equals(name)) {
			return false;
		}
		boolean everyone = contains(data.settings().disabledEveryoneCommands, name);
		if (everyone) {
			return true;
		}
		if (data.getAdminLevel(player) >= AdminRanks.MOD) {
			return false;
		}
		return contains(data.settings().disabledPlayerCommands, name);
	}

	private static boolean contains(java.util.List<String> list, String name) {
		if (list == null) {
			return false;
		}
		return list.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(name));
	}
}
