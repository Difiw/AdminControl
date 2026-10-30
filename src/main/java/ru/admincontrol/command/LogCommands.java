package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.screen.AdminStatsFactory;
import ru.admincontrol.screen.LogFactory;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AdminStats;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ChatDump;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.JournalLog;
import ru.admincontrol.util.ScreenNav;
import ru.admincontrol.util.WeatherLog;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Общие логи, журналы погоды / МП / тренировок / боксов, /eventstop и статистика админов. */
public final class LogCommands {
	private LogCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("plogall").requires(s -> CommandUtil.canUse(s, "plogall"))
				.executes(ctx -> showAll(ctx, false, "plogall", "")));
		dispatcher.register(literal("alogall").requires(s -> CommandUtil.canUse(s, "alogall"))
				.executes(ctx -> showAll(ctx, true, "alogall", "")));
		dispatcher.register(literal("plogsearch").requires(s -> CommandUtil.canUse(s, "plogsearch"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/plogsearch <запрос>"))
				.then(argument("запрос", StringArgumentType.greedyString())
						.executes(ctx -> showAll(ctx, false, "plogsearch", StringArgumentType.getString(ctx, "запрос")))));
		dispatcher.register(literal("alogsearch").requires(s -> CommandUtil.canUse(s, "alogsearch"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/alogsearch <запрос>"))
				.then(argument("запрос", StringArgumentType.greedyString())
						.executes(ctx -> showAll(ctx, true, "alogsearch", StringArgumentType.getString(ctx, "запрос")))));
		dispatcher.register(literal("aweatherlog").requires(s -> CommandUtil.canUse(s, "aweatherlog"))
				.executes(ctx -> journal(ctx, "aweatherlog", "Журнал погоды", WeatherLog.lines())));
		dispatcher.register(literal("aeventlog").requires(s -> CommandUtil.canUse(s, "aeventlog"))
				.executes(ctx -> journal(ctx, "aeventlog", "Журнал МП", JournalLog.lines(JournalLog.EVENTS))));
		dispatcher.register(literal("atraininglog").requires(s -> CommandUtil.canUse(s, "atraininglog"))
				.executes(ctx -> journal(ctx, "atraininglog", "Журнал тренировок", JournalLog.lines(JournalLog.TRAINING))));
		dispatcher.register(literal("aboxlog").requires(s -> CommandUtil.canUse(s, "aboxlog"))
				.executes(ctx -> journal(ctx, "aboxlog", "Журнал боксов", JournalLog.lines(JournalLog.BOX))));
		dispatcher.register(literal("clearweatherlog").requires(s -> CommandUtil.canUse(s, "clearweatherlog"))
				.executes(ctx -> clear(ctx, "clearweatherlog", "погоды", null)));
		dispatcher.register(literal("cleareventlog").requires(s -> CommandUtil.canUse(s, "cleareventlog"))
				.executes(ctx -> clear(ctx, "cleareventlog", "МП", JournalLog.EVENTS)));
		dispatcher.register(literal("cleartraininglog").requires(s -> CommandUtil.canUse(s, "cleartraininglog"))
				.executes(ctx -> clear(ctx, "cleartraininglog", "тренировок", JournalLog.TRAINING)));
		dispatcher.register(literal("clearboxlog").requires(s -> CommandUtil.canUse(s, "clearboxlog"))
				.executes(ctx -> clear(ctx, "clearboxlog", "боксов", JournalLog.BOX)));
		dispatcher.register(literal("eventstop").requires(s -> CommandUtil.canUse(s, "eventstop"))
				.executes(LogCommands::eventStop));
		dispatcher.register(literal("admstats").requires(s -> CommandUtil.canUse(s, "admstats"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/admstats <ник админа>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> adminStats(ctx, StringArgumentType.getString(ctx, "ник")))));
		dispatcher.register(literal("astats").requires(s -> CommandUtil.canUse(s, "astats"))
				.executes(ctx -> adminStats(ctx, null)));
	}

	private static int showAll(CommandContext<ServerCommandSource> ctx, boolean admin, String command, String query) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, command) || !LogAccess.requireNotBlogger(src)) {
			return 0;
		}
		String q = query == null ? "" : query.trim();
		List<ActivityLog.Line> lines = LogAccess.visible(src, ActivityLog.readAll(admin));
		if (!q.isEmpty()) {
			lines = ActivityLog.filter(lines, q);
		}
		String title = (admin ? "Логи всех администраторов" : "Логи всех игроков")
				+ (q.isEmpty() ? "" : "  ·  поиск: " + (q.length() > 60 ? q.substring(0, 60) + "…" : q));
		if (src.getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, new LogFactory(title, lines, true, true, q).tracked());
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ChatDump.logLines(src, lines);
	}

	private static int journal(CommandContext<ServerCommandSource> ctx, String command, String title, List<String> lines) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, command) || !LogAccess.requireNotBlogger(src)) {
			return 0;
		}
		List<String> shown = lines.isEmpty() ? List.of("Записей пока нет.") : lines;
		if (src.getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, LogFactory.ofText(title, shown).tracked());
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ChatDump.lines(src, shown);
	}

	private static int clear(CommandContext<ServerCommandSource> ctx, String command, String what, String journal) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, command)) {
			return 0;
		}
		boolean had = journal == null ? WeatherLog.clear() : JournalLog.clear(journal);
		AdminFeedback.tell(src, had ? "Журнал " + what + " очищен." : "Журнал " + what + " уже пуст.");
		AdminNotify.record(src, command.toUpperCase(java.util.Locale.ROOT), "-", "очистка журнала " + what);
		return 1;
	}

	private static int eventStop(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "eventstop")) {
			return 0;
		}
		if (!EventService.active()) {
			src.sendError(Chat.red("Сейчас нет мероприятия."));
			return 0;
		}
		String name = EventService.currentName();
		String host = EventService.currentHost();
		String admin = CommandUtil.adminName(src);
		EventService.stop(src.getServer(), "остановил " + AdminRanks.title(CommandUtil.sourceRank(src)) + " " + admin + " (/eventstop)");
		AdminFeedback.tell(src, "Мероприятие «" + name + "» (организатор " + host + ") остановлено.");
		AdminNotify.record(src, "EVENTSTOP", host, "«" + name + "»");
		return 1;
	}

	private static int adminStats(CommandContext<ServerCommandSource> ctx, String nick) {
		ServerCommandSource src = ctx.getSource();
		if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
			src.sendError(Chat.red("Статистика открывается в игре."));
			return 0;
		}
		String target = nick == null ? player.getGameProfile().getName() : nick;
		if (nick != null) {
			if (!CommandUtil.requireCommand(src, "admstats")) {
				return 0;
			}
			PlayerRecord record = CommandUtil.requireRecord(src, nick);
			if (record == null) {
				return 0;
			}
			target = record.nickname;
			if (!AdminStats.known(target) && !DataManager.get().isAdmin(record.uuidOrNull(), target)) {
				src.sendError(Chat.red(target + " не администратор."));
				return 0;
			}
		} else if (!CommandUtil.requireCommand(src, "astats")) {
			return 0;
		}
		if (!AdminStatsFactory.allowed(src, target)) {
			src.sendError(Chat.red("Нельзя смотреть статистику администратора старше вас."));
			return 0;
		}
		ScreenNav.openRoot(player, new AdminStatsFactory(target, 0));
		return 1;
	}
}
