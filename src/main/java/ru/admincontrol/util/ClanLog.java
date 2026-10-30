package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.ClanRecord;

public final class ClanLog {
	private ClanLog() {
	}

	public static void action(ClanRecord clan, ServerPlayerEntity who, String text) {
		if (clan == null || clan.name == null || clan.name.isBlank() || text == null || text.isBlank()) {
			return;
		}
		String nick = who == null ? "?" : who.getGameProfile().getName();
		ActivityLog.clan(clan.name, ActionKind.CLAN.label + ": " + nick + " — " + text, true);
	}

	public static void fail(ClanRecord clan, ServerPlayerEntity who, String text) {
		if (clan == null || clan.name == null || clan.name.isBlank() || text == null || text.isBlank()) {
			return;
		}
		String nick = who == null ? "?" : who.getGameProfile().getName();
		ActivityLog.clan(clan.name, ActionKind.CLAN.label + ": " + nick + " — " + text, false);
	}
}
