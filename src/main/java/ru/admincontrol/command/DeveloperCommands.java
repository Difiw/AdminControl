package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.BanEntry;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ScreenNav;
import ru.admincontrol.util.TabListColors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class DeveloperCommands {
	private DeveloperCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("all_clear").requires(s -> CommandUtil.canUse(s, "all_clear")).executes(DeveloperCommands::allClear));
		dispatcher.register(literal("all_unadm").requires(s -> CommandUtil.canUse(s, "all_unadm")).executes(DeveloperCommands::allUnadm));
		dispatcher.register(literal("all_permban").requires(s -> CommandUtil.canUse(s, "all_permban"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/all_permban <причина>"))
				.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS).executes(DeveloperCommands::allPermab)));
		dispatcher.register(literal("all_unpermban").requires(s -> CommandUtil.canUse(s, "all_unpermban")).executes(DeveloperCommands::allUnpermban));
		dispatcher.register(literal("kill_server").requires(s -> CommandUtil.canUse(s, "kill_server"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/kill_server <причина>"))
				.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS).executes(DeveloperCommands::killServer)));
		dispatcher.register(literal("revive_server").requires(s -> CommandUtil.canUse(s, "revive_server")).executes(DeveloperCommands::reviveServer));
		dispatcher.register(literal("undev").requires(s -> CommandUtil.canUse(s, "undev")).executes(DeveloperCommands::stepDownToGa));
		dispatcher.register(literal("dserver").requires(s -> CommandUtil.canUse(s, "dserver")).executes(DeveloperCommands::openDevServer));
		dispatcher.register(literal("devent").requires(s -> CommandUtil.canUse(s, "devent")).executes(DeveloperCommands::openDevEvent));
		dispatcher.register(literal("dbuild").requires(s -> CommandUtil.canUse(s, "dbuild"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/dbuild <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(DeveloperCommands::buildHelper)));
		dispatcher.register(literal("dbuildkick").requires(s -> CommandUtil.canUse(s, "dbuildkick"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/dbuildkick <ник>"))
				.then(argument("ник", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.command.CommandSource.suggestMatching(
								ru.admincontrol.util.EventMapEditor.helperNames(), builder))
						.executes(DeveloperCommands::kickBuildHelper)));
	}

	private static int buildHelper(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "dbuild") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		String error = ru.admincontrol.util.EventMapEditor.addHelper(CommandUtil.self(ctx), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "DBUILD", nick, "позван на стройку карты МП");
		return 1;
	}

	private static int kickBuildHelper(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "dbuildkick") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		String error = ru.admincontrol.util.EventMapEditor.kickHelper(CommandUtil.self(ctx), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), nick + " убран со стройки карты МП.");
		AdminNotify.record(ctx.getSource(), "DBUILDKICK", nick, "убран со стройки карты МП");
		return 1;
	}

	private static int openDevEvent(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "devent") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity self = CommandUtil.self(ctx);
		int type = ru.admincontrol.util.EventMapEditor.editing(self) ? ru.admincontrol.util.EventMapEditor.type() : -1;
		ScreenNav.openRoot(self, new ru.admincontrol.screen.DevEventFactory(type));
		return 1;
	}

	private static int allClear(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity self = ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? p : null;
		int n = 0;
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			if (self != null && player.getUuid().equals(self.getUuid())) {
				continue;
			}
			player.getInventory().clear();
			n++;
		}
		AdminFeedback.tell(ctx.getSource(), "Инвентари очищены: " + n);
		return 1;
	}

	private static int allUnadm(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		ServerPlayerEntity self = ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? p : null;
		for (PlayerRecord record : data.records()) {
			if (self != null && (self.getUuid().toString().equalsIgnoreCase(record.uuid)
					|| self.getGameProfile().getName().equalsIgnoreCase(record.nickname))) {
				continue;
			}
			if (data.isAdmin(record.uuidOrNull(), record.nickname)
					&& data.getAdminLevel(record.uuidOrNull(), record.nickname) < AdminRanks.DEV) {
				data.removeAdmin(record);
			}
		}
		data.settings().addAdminLockedToStaff = true;
		data.saveAll();
		CommandSync.refreshAll(ctx.getSource().getServer());
		TabListColors.refresh(ctx.getSource().getServer());
		AdminFeedback.tell(ctx.getSource(), "Админы сняты, /add_admin заблокирован для игроков.");
		return 1;
	}

	private static int allPermab(CommandContext<ServerCommandSource> ctx) {
		String reason = StringArgumentType.getString(ctx, "причина");
		DataManager data = DataManager.get();
		ServerPlayerEntity self = ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? p : null;
		for (PlayerRecord record : data.records()) {
			if (self != null && self.getGameProfile().getName().equalsIgnoreCase(record.nickname)) {
				continue;
			}
			if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
				continue;
			}
			BanEntry entry = new BanEntry();
			entry.uuid = record.uuid;
			entry.nickname = record.nickname;
			entry.permanent = true;
			entry.reason = reason;
			entry.fromAllPermab = true;
			entry.createdAt = System.currentTimeMillis();
			data.addBan(entry);
			CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname)
					.ifPresent(p -> ru.admincontrol.util.PunishmentNotice.disconnect(p,
							ru.admincontrol.util.PunishmentNotice.Kind.PERMBAN,
							ru.admincontrol.util.PunishmentNotice.displayAdmin(ctx.getSource()),
							reason, ru.admincontrol.util.PunishmentNotice.permDuration(), entry.createdAt));
		}
		AdminNotify.record(ctx.getSource(), "ALL_PERMAB", "-", reason);
		return 1;
	}

	private static int allUnpermban(CommandContext<ServerCommandSource> ctx) {
		int n = DataManager.get().removeMassPermabans();
		AdminFeedback.tell(ctx.getSource(), "Снято массовых пермабанов: " + n);
		return 1;
	}

	private static int killServer(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		data.settings().serverLockMode = "kill";
		data.settings().serverLocked = true;
		data.settings().serverLockReason = StringArgumentType.getString(ctx, "причина");
		data.saveAll();
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			if (!data.canJoinWhileLocked(player) && player != ctx.getSource().getEntity()) {
				player.networkHandler.disconnect(Chat.red("Сервер закрыт разработчиком: " + data.serverLockReason()));
			}
		}
		AdminFeedback.tell(ctx.getSource(), "Сервер закрыт для всех, кроме Разработчика.");
		return 1;
	}

	private static int reviveServer(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		data.settings().serverLockMode = "none";
		data.settings().serverLocked = false;
		data.saveAll();
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Сервер открыт разработчиком."));
		return 1;
	}

	private static int stepDownToGa(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		DataManager data = DataManager.get();
		if (data.getAdminLevel(player) < AdminRanks.DEV) {
			ctx.getSource().sendError(Chat.red("Команда только для Разработчика."));
			return 0;
		}
		data.setHeadAdmin(player);
		CommandSync.refreshAll(player.getServer());
		TabListColors.refresh(player.getServer());
		Chat.send(player, Chat.gold("Права разработчика сняты. Вы снова Главный админ (ГА)."));
		AdminNotify.record(ctx.getSource(), "UNDEV", player.getGameProfile().getName(), "стал ГА");
		return 1;
	}

	private static int openDevServer(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "dserver") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.ServerPanelFactory(true));
		return 1;
	}
}
