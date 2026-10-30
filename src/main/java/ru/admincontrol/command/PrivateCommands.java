package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.screen.LogFactory;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ChatDump;
import ru.admincontrol.util.PrivateService;
import ru.admincontrol.util.ScreenNav;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /aprivate — просмотр, снятие и восстановление приватов игроков. */
public final class PrivateCommands {
	private static final String USAGE = "/aprivate info <ник> | list | list players | remove <ник> | restore <ник>";

	private PrivateCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("aprivate").requires(s -> CommandUtil.canUse(s, "aprivate"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), USAGE))
				.then(literal("info")
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/aprivate info <ник>"))
						.then(nickArg().executes(PrivateCommands::info)))
				.then(literal("list")
						.executes(PrivateCommands::list)
						.then(literal("players").executes(PrivateCommands::listPlayers)))
				.then(literal("remove").requires(s -> CommandUtil.canUse(s, "aprivateremove"))
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/aprivate remove <ник>"))
						.then(nickArg().executes(PrivateCommands::remove)))
				.then(literal("restore").requires(s -> CommandUtil.canUse(s, "aprivaterestore"))
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/aprivate restore <ник>"))
						.then(nickArg().executes(PrivateCommands::restore))));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, String> nickArg() {
		return argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS);
	}

	private static int info(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "aprivate")) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		PrivateService.Priv active = PrivateService.activeOf(nick);
		PrivateService.Priv removed = PrivateService.removedOf(nick);
		List<String> lines = new ArrayList<>();
		String owner = nick;
		if (active != null) {
			owner = active.ownerNick;
			lines.addAll(PrivateService.info(active, false));
		} else {
			lines.add("У игрока " + nick + " нет привата.");
		}
		if (removed != null) {
			owner = removed.ownerNick;
			lines.add("");
			lines.add("Приват, снятый администратором:");
			lines.addAll(PrivateService.info(removed, true));
		}
		return show(src, "Приват игрока " + owner, lines);
	}

	private static int list(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "aprivate")) {
			return 0;
		}
		return show(ctx.getSource(), "Приваты: уровни и статистика", PrivateService.levels());
	}

	private static int listPlayers(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "aprivate")) {
			return 0;
		}
		List<String> lines = PrivateService.players();
		if (lines.isEmpty()) {
			lines = List.of("Ни у одного игрока нет привата.");
		}
		return show(ctx.getSource(), "Приваты игроков", lines);
	}

	private static int remove(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "aprivateremove")) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		ServerPlayerEntity admin = src.getEntity() instanceof ServerPlayerEntity p ? p : null;
		String owner = PrivateService.remove(admin, CommandUtil.adminName(src), nick, src.getServer());
		if (owner == null) {
			src.sendError(Chat.red("У игрока " + nick + " нет привата."));
			return 0;
		}
		AdminNotify.record(src, "PRIVATE_REMOVE", owner, "-");
		src.sendFeedback(() -> Chat.green("Приват игрока " + owner + " снят. Восстановить: /aprivate restore " + owner), false);
		return 1;
	}

	private static int restore(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "aprivaterestore")) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		PrivateService.Priv removed = PrivateService.removedOf(nick);
		String owner = removed == null ? nick : removed.ownerNick;
		String error = PrivateService.restore(CommandUtil.adminName(src), nick, src.getServer());
		if (error != null) {
			src.sendError(Chat.red(error));
			return 0;
		}
		AdminNotify.record(src, "PRIVATE_RESTORE", owner, "-");
		src.sendFeedback(() -> Chat.green("Приват игрока " + owner + " восстановлен."), false);
		return 1;
	}

	static int show(ServerCommandSource src, String title, List<String> lines) {
		if (src.getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, LogFactory.ofText(title, lines));
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ChatDump.lines(src, lines);
	}
}
