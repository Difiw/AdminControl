package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.TabListColors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SystemCommands {
	private static final String LEVEL_HELP =
			"Уровень: 1–9 (1 мод … 8 ГА, 9 разработчик), 4.5 — блогер; уровни из /aroles — по ключу (r1) или номеру (2.5)";

	private SystemCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("add_admin")
				.requires(SystemCommands::canUseAddAdmin)
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/add_admin <ник> [уровень]"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(SystemCommands::addAdmin)
						.then(argument("уровень", StringArgumentType.word()).suggests(CommandUtil.ADMIN_LEVELS)
								.executes(SystemCommands::addAdmin))));
		dispatcher.register(literal("add_blogger")
				.requires(source -> CommandUtil.canUse(source, "add_blogger"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/add_blogger <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(SystemCommands::addBlogger)));
		dispatcher.register(literal("set_admin")
				.requires(source -> CommandUtil.canUse(source, "set_admin"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/set_admin <ник> <уровень>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/set_admin <ник> <уровень>"))
						.then(argument("уровень", StringArgumentType.word()).suggests(CommandUtil.ADMIN_LEVELS)
								.executes(SystemCommands::setAdmin))));
	}

	private static int addBlogger(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "add_blogger")) {
			return 0;
		}
		DataManager data = DataManager.get();
		String nick = StringArgumentType.getString(ctx, "ник");
		ServerPlayerEntity online = CommandUtil.findOnline(src.getServer(), nick).orElse(null);
		PlayerRecord record = online != null ? data.getOrCreate(online) : CommandUtil.requireRecord(src, nick);
		if (record == null) {
			src.sendError(Chat.yellow("Игрок должен хотя бы раз зайти на сервер или быть онлайн."));
			return 0;
		}
		if (!canAssign(src, AdminRanks.BLOGGER, true, false)) {
			return 0;
		}
		boolean wasAdmin = data.isAdmin(record.uuidOrNull(), record.nickname);
		if (wasAdmin) {
			if (data.getAdminLevel(record.uuidOrNull(), record.nickname) == AdminRanks.BLOGGER) {
				src.sendError(Chat.red("Игрок «" + record.nickname + "» уже блогер."));
				return 0;
			}
			if (data.lockedDeveloper(record)) {
				AdminNotify.denied(src, "нельзя снять Разработчика — только /undev у себя");
				return 0;
			}
			boolean self = src.getEntity() instanceof ServerPlayerEntity executor
					&& executor.getGameProfile().getName().equalsIgnoreCase(record.nickname);
			if (self && CommandUtil.sourceRank(src) < AdminRanks.DEV) {
				AdminNotify.denied(src, "нельзя изменить свой уровень админ-прав");
				return 0;
			}
			if (!CommandUtil.requireCanAffect(src, record.nickname)) {
				return 0;
			}
			data.setAdminLevel(record, AdminRanks.BLOGGER);
		} else {
			if (data.settings().addAdminLockedToStaff && !CommandUtil.hasRank(src, AdminRanks.GA)) {
				AdminNotify.denied(src, "выдача админки закрыта: только ГА");
				return 0;
			}
			data.addAdmin(record, AdminRanks.BLOGGER);
		}
		AdminNotify.record(src, "ADD_BLOGGER", record.nickname, AdminRanks.display(AdminRanks.BLOGGER) + " "
				+ AdminRanks.title(AdminRanks.BLOGGER));
		final String name = record.nickname;
		src.sendFeedback(() -> Chat.green("Игрок " + name + " назначен: Блогер (уровень 4.5)."), false);
		if (online != null) {
			Chat.send(online, Chat.green("Вам выданы права: Блогер. /ahelp"));
			CommandSync.refresh(online);
		}
		CommandSync.refreshAll(src.getServer());
		TabListColors.refresh(src.getServer());
		return 1;
	}

	private static boolean canUseAddAdmin(ServerCommandSource source) {
		DataManager data = DataManager.get();
		if (data == null || !data.hasAnyAdmin()) {
			return true;
		}
		if (!ru.admincontrol.util.ModGate.on()) {
			return source.getEntity() == null || source.hasPermissionLevel(2);
		}
		if (data.settings().addAdminLockedToStaff) {
			return CommandUtil.hasRank(source, AdminRanks.GA);
		}
		return CommandUtil.canUse(source, "add_admin");
	}

	private static int addAdmin(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		if (data.hasAnyAdmin() && !canUseAddAdmin(ctx.getSource())) {
			ctx.getSource().sendError(Chat.red("У вас нет прав для этой команды"));
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		boolean openClaim = !data.hasAnyAdmin() || !ru.admincontrol.util.ModGate.on();
		int level = openClaim ? AdminRanks.GA : AdminRanks.MOD;
		try {
			Integer parsed = AdminRanks.parseAssignable(StringArgumentType.getString(ctx, "уровень"));
			if (parsed == null) {
				ctx.getSource().sendError(Chat.red(LEVEL_HELP));
				return 0;
			}
			level = parsed;
		} catch (IllegalArgumentException ignored) {
		}
		if (!canAssign(ctx.getSource(), level, false, openClaim)) {
			return 0;
		}
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), nick).orElse(null);
		PlayerRecord record;
		if (online != null) {
			record = data.getOrCreate(online);
		} else {
			record = CommandUtil.requireRecord(ctx.getSource(), nick);
			if (record == null) {
				ctx.getSource().sendError(Chat.yellow("Игрок должен хотя бы раз зайти на сервер или быть онлайн."));
				return 0;
			}
		}
		if (data.isAdmin(record.uuidOrNull(), record.nickname)) {
			if (!openClaim) {
				ctx.getSource().sendError(Chat.red("Игрок «" + record.nickname + "» уже является администратором."));
				return 0;
			}
			data.setAdminLevel(record, level);
		} else {
			data.addAdmin(record, level);
		}
		AdminNotify.record(ctx.getSource(), "ADD_ADMIN", record.nickname, AdminRanks.display(level) + " " + AdminRanks.title(level));
		final String assignedNick = record.nickname;
		final int assignedLevel = level;
		ctx.getSource().sendFeedback(() -> Chat.green("Игрок " + assignedNick + " назначен: " + AdminRanks.title(assignedLevel)
				+ " (уровень " + AdminRanks.display(assignedLevel) + ")."), false);
		if (online != null) {
			Chat.send(online, Chat.green("Вам выданы права: " + AdminRanks.title(level) + ". /ahelp"));
			CommandSync.refresh(online);
		}
		CommandSync.refreshAll(ctx.getSource().getServer());
		TabListColors.refresh(ctx.getSource().getServer());
		return 1;
	}

	private static int setAdmin(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "set_admin")) {
			return 0;
		}
		Integer parsed = AdminRanks.parseAssignable(StringArgumentType.getString(ctx, "уровень"));
		if (parsed == null) {
			ctx.getSource().sendError(Chat.red(LEVEL_HELP));
			return 0;
		}
		if (!canAssign(ctx.getSource(), parsed, true, false)) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		DataManager data = DataManager.get();
		if (!data.isAdmin(record.uuidOrNull(), record.nickname)) {
			ctx.getSource().sendError(Chat.red("Игрок «" + record.nickname + "» не является администратором."));
			return 0;
		}
		if (data.lockedDeveloper(record) && parsed < AdminRanks.DEV) {
			AdminNotify.denied(ctx.getSource(), "нельзя снять Разработчика — только /undev у себя");
			return 0;
		}
		boolean self = ctx.getSource().getEntity() instanceof ServerPlayerEntity executor
				&& executor.getGameProfile().getName().equalsIgnoreCase(record.nickname);
		if (self && CommandUtil.sourceRank(ctx.getSource()) < AdminRanks.DEV) {
			AdminNotify.denied(ctx.getSource(), "нельзя изменить свой уровень админ-прав");
			return 0;
		}
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		data.setAdminLevel(record, parsed);
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			Chat.send(online, Chat.green("Ваш уровень: " + AdminRanks.title(parsed) + " (" + AdminRanks.display(parsed) + ")."));
			CommandSync.refresh(online);
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Уровень " + record.nickname + ": " + AdminRanks.title(parsed)
				+ " (" + AdminRanks.display(parsed) + ")."), false);
		AdminNotify.record(ctx.getSource(), "SET_ADMIN", record.nickname, AdminRanks.display(parsed));
		CommandSync.refreshAll(ctx.getSource().getServer());
		TabListColors.refresh(ctx.getSource().getServer());
		return 1;
	}

	private static boolean canAssign(ServerCommandSource source, int level, boolean set, boolean openClaim) {
		int exec = CommandUtil.sourceRank(source);
		if (level >= AdminRanks.DEV) {
			AdminNotify.denied(source, "нельзя назначить Разработчика через /add_admin");
			return false;
		}
		if (level >= AdminRanks.GA && exec < AdminRanks.DEV) {
			if (openClaim && level == AdminRanks.GA) {
				return true;
			}
			AdminNotify.denied(source, "на должность ГА может ставить только Разработчик");
			return false;
		}
		if (level >= AdminRanks.TECH && exec < AdminRanks.GA) {
			AdminNotify.denied(source, "нельзя назначать Тех. администратора");
			return false;
		}
		if (level >= AdminRanks.ZGA && exec < AdminRanks.TECH) {
			AdminNotify.denied(source, "нельзя назначать ЗГА");
			return false;
		}
		if (level > exec && ru.admincontrol.util.CustomRanks.byLevel(level) != null) {
			AdminNotify.denied(source, "нельзя выдать уровень выше своего");
			return false;
		}
		if (level >= exec && exec < AdminRanks.GA && set) {
			AdminNotify.denied(source, "нельзя выдать уровень не ниже своего");
			return false;
		}
		return true;
	}
}
