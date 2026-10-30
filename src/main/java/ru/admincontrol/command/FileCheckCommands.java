package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.screen.AntiCheatFactory;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.PlayerFileService;
import ru.admincontrol.util.ScreenNav;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /filecheck <ник> — файлы игры игрока (mods, resourcepacks, shaderpacks, config) со скачиванием на компьютер администратора. */
public final class FileCheckCommands {
	private FileCheckCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("filecheck")
				.requires(s -> CommandUtil.hasRank(s, AdminRanks.ADMIN) && CommandUtil.canUse(s, "filecheck"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/filecheck <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(FileCheckCommands::check)));
		dispatcher.register(literal("anticheat").requires(s -> CommandUtil.hasRank(s, AdminRanks.DEV))
				.executes(FileCheckCommands::anticheat));
		dispatcher.register(literal("actest")
				.requires(s -> s.getEntity() == null || CommandUtil.hasRank(s, AdminRanks.ADMIN))
				.executes(FileCheckCommands::actest));
		dispatcher.register(literal("buglist").requires(s -> CommandUtil.canUse(s, "buglist"))
				.executes(FileCheckCommands::buglist));
	}

	private static int actest(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requirePlayer(src)) {
			return 0;
		}
		if (!CommandUtil.hasRank(src, AdminRanks.ADMIN)) {
			src.sendError(Chat.red("/actest доступен с администратора."));
			return 0;
		}
		ServerPlayerEntity admin = (ServerPlayerEntity) src.getEntity();
		boolean on = ru.admincontrol.util.AntiCheatService.toggleTest(admin);
		ActivityLog.admin(admin.getGameProfile().getName(), on ? "Включил /actest" : "Выключил /actest");
		return 1;
	}

	private static int buglist(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "buglist") || !CommandUtil.requirePlayer(src)) {
			return 0;
		}
		ScreenNav.openRoot((ServerPlayerEntity) src.getEntity(), new ru.admincontrol.screen.BugListFactory());
		return 1;
	}

	private static int anticheat(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requirePlayer(src)) {
			return 0;
		}
		if (!CommandUtil.hasRank(src, AdminRanks.DEV)) {
			src.sendError(Chat.red("Настройки античита доступны только разработчику."));
			return 0;
		}
		ScreenNav.openRoot((ServerPlayerEntity) src.getEntity(), new AntiCheatFactory());
		return 1;
	}

	private static int check(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "filecheck") || !CommandUtil.requirePlayer(src)) {
			return 0;
		}
		if (!CommandUtil.hasRank(src, AdminRanks.ADMIN)) {
			src.sendError(Chat.red("Проверка файлов доступна с администратора."));
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		ServerPlayerEntity target = CommandUtil.findOnline(src.getServer(), nick).orElse(null);
		if (target == null) {
			src.sendError(Chat.red("Игрок «" + nick + "» не в сети. Проверить файлы можно только у онлайн-игрока."));
			return 0;
		}
		if (!CommandUtil.requireCanAffect(src, target.getGameProfile().getName())) {
			return 0;
		}
		ServerPlayerEntity admin = (ServerPlayerEntity) src.getEntity();
		String error = PlayerFileService.open(admin, target);
		if (error != null) {
			src.sendError(Chat.red(error));
			return 0;
		}
		ActivityLog.admin(CommandUtil.adminName(src), "Проверка файлов игрока " + target.getGameProfile().getName());
		return 1;
	}
}
