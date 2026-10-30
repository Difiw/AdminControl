package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.List;

/** Маркеры чата для приложения YourControl. */
public final class ConDump {
	private ConDump() {
	}

	public static void field(ServerCommandSource source, String prefix, String key, String value) {
		String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
		source.sendFeedback(() -> Text.literal(prefix + " " + key + "=" + safe), false);
	}

	public static int players(ServerCommandSource source) {
		var list = source.getServer().getPlayerManager().getPlayerList();
		source.sendFeedback(() -> Chat.gold("=== Игроки в сети (" + list.size() + ") ==="), false);
		source.sendFeedback(() -> Text.literal("ACPLAYERS begin " + list.size()), false);
		int n = 0;
		for (ServerPlayerEntity player : list) {
			if (NpcService.isNpc(player)) {
				continue;
			}
			n++;
			String nick = player.getGameProfile().getName();
			source.sendFeedback(() -> Text.literal("ACPLAYERS nick=" + nick), false);
			source.sendFeedback(() -> Chat.white(nick), false);
		}
		int shown = n;
		source.sendFeedback(() -> Text.literal("ACPLAYERS end " + shown), false);
		return 1;
	}

	public static int admins(ServerCommandSource source) {
		var list = source.getServer().getPlayerManager().getPlayerList();
		DataManager data = DataManager.get();
		source.sendFeedback(() -> Chat.gold("=== Админы в сети ==="), false);
		source.sendFeedback(() -> Text.literal("ACADMINS begin"), false);
		int n = 0;
		for (ServerPlayerEntity player : list) {
			if (data == null || NpcService.isNpc(player)) {
				continue;
			}
			int level = data.getAdminLevel(player);
			if (level < AdminRanks.MOD) {
				continue;
			}
			n++;
			String nick = player.getGameProfile().getName();
			String title = AdminRanks.title(level);
			String rank = AdminRanks.display(level);
			source.sendFeedback(() -> Text.literal("ACADMINS nick=" + nick + " rank=" + rank + " title=" + title), false);
			source.sendFeedback(() -> Chat.gold(nick + " — " + title + " (" + rank + ")"), false);
		}
		if (n == 0) {
			source.sendFeedback(() -> Text.literal("ACADMINS empty"), false);
			source.sendFeedback(() -> Chat.yellow("Нет администраторов в сети."), false);
		}
		source.sendFeedback(() -> Text.literal("ACADMINS end"), false);
		return 1;
	}

	public static int nps(ServerCommandSource source) {
		List<String> names = NpcService.names(source.getServer());
		source.sendFeedback(() -> Chat.gold("=== NPC (" + names.size() + ") ==="), false);
		source.sendFeedback(() -> Text.literal("ACNPS begin " + names.size()), false);
		if (names.isEmpty()) {
			source.sendFeedback(() -> Text.literal("ACNPS empty"), false);
			source.sendFeedback(() -> Chat.gray("NPC нет. /addnpc <ник>"), false);
		} else {
			for (String nick : names) {
				source.sendFeedback(() -> Text.literal("ACNPS nick=" + nick), false);
				source.sendFeedback(() -> Chat.white(nick), false);
			}
		}
		source.sendFeedback(() -> Text.literal("ACNPS end"), false);
		return 1;
	}

	public static int top(ServerCommandSource source) {
		List<String> lines = PlayFeatures.topLines();
		source.sendFeedback(() -> Chat.gold("=== Недельный топ ==="), false);
		source.sendFeedback(() -> Text.literal("ACTOP begin " + lines.size()), false);
		int i = 0;
		for (String line : lines) {
			int idx = ++i;
			String text = line == null ? "" : line.replace('\n', ' ');
			source.sendFeedback(() -> Text.literal("ACTOP i=" + idx + " " + text), false);
			source.sendFeedback(() -> Chat.gray(text), false);
		}
		source.sendFeedback(() -> Text.literal("ACTOP end"), false);
		return 1;
	}

	public static int balance(ServerCommandSource source, PlayerRecord record) {
		String nick = record == null || record.nickname == null ? "" : record.nickname;
		int coins = Economy.balanceOf(record);
		source.sendFeedback(() -> Chat.gold("=== Баланс " + nick + " ==="), false);
		source.sendFeedback(() -> Text.literal("ACBAL begin"), false);
		source.sendFeedback(() -> Text.literal("ACBAL nick=" + nick + " coins=" + coins), false);
		source.sendFeedback(() -> Chat.white(nick + ": " + Money.wc(coins)), false);
		source.sendFeedback(() -> Text.literal("ACBAL end"), false);
		return 1;
	}
}
