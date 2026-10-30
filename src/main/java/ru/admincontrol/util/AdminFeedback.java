package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;

/**
 * Жёлтая обратная связь для администратора, выполнившего команду.
 */
public final class AdminFeedback {
	private AdminFeedback() {
	}

	public static void tell(ServerCommandSource source, String message) {
		if (source != null) {
			source.sendFeedback(() -> Chat.yellow(message), false);
		}
	}
}
