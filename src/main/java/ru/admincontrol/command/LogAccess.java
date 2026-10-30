package ru.admincontrol.command;

import net.minecraft.server.command.ServerCommandSource;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.Chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Кто какие логи может смотреть: блогеру — никакие, младшим — не логи старших администраторов. */
public final class LogAccess {
	private LogAccess() {
	}

	public static boolean requireNotBlogger(ServerCommandSource source) {
		if (!AdminRanks.isBlogger(CommandUtil.sourceRank(source))) {
			return true;
		}
		source.sendError(Chat.red("Блогеру логи недоступны."));
		return false;
	}

	public static boolean requireCanSee(ServerCommandSource source, PlayerRecord target) {
		if (!requireNotBlogger(source)) {
			return false;
		}
		if (target == null || rankOf(target.nickname) <= CommandUtil.sourceRank(source)) {
			return true;
		}
		source.sendError(Chat.red("Нельзя смотреть логи администратора старше вас."));
		return false;
	}

	/** Убирает строки вида «[время] &lt;ник&gt; …» администраторов старше смотрящего. */
	public static List<ActivityLog.Line> visible(ServerCommandSource source, List<ActivityLog.Line> lines) {
		int viewer = CommandUtil.sourceRank(source);
		Map<String, Integer> ranks = new HashMap<>();
		List<ActivityLog.Line> out = new ArrayList<>(lines.size());
		for (ActivityLog.Line line : lines) {
			String nick = nickOf(line.text);
			if (nick == null || ranks.computeIfAbsent(nick.toLowerCase(Locale.ROOT), k -> rankOf(nick)) <= viewer) {
				out.add(line);
			}
		}
		return out;
	}

	private static String nickOf(String text) {
		if (text == null) {
			return null;
		}
		int open = text.indexOf("] <");
		int close = open < 0 ? -1 : text.indexOf('>', open + 3);
		return close < 0 ? null : text.substring(open + 3, close);
	}

	private static int rankOf(String nick) {
		DataManager data = DataManager.get();
		if (data == null || nick == null) {
			return AdminRanks.NONE;
		}
		PlayerRecord record = data.findByNick(nick).orElse(null);
		return data.getAdminLevel(record == null ? null : record.uuidOrNull(), nick);
	}
}
