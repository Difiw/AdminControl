package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PlayerWaypoint;
import ru.admincontrol.data.PunishmentRecord;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.InventoryUtil;
import ru.admincontrol.util.ScreenNav;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PlayerCommands {
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private PlayerCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("phelp").executes(ctx -> help(ctx, false, ""))
				.then(argument("поиск", StringArgumentType.greedyString())
						.executes(ctx -> help(ctx, false, StringArgumentType.getString(ctx, "поиск")))));
		dispatcher.register(literal("conphelp").executes(ctx -> help(ctx, true, ""))
				.then(argument("поиск", StringArgumentType.greedyString())
						.executes(ctx -> help(ctx, true, StringArgumentType.getString(ctx, "поиск")))));
		dispatcher.register(literal("admins").executes(PlayerCommands::listAdmins));
		dispatcher.register(literal("conadmins").executes(ctx -> ru.admincontrol.util.ConDump.admins(ctx.getSource())));
		dispatcher.register(literal("players").executes(PlayerCommands::listPlayers));
		dispatcher.register(literal("conplayers").executes(ctx -> ru.admincontrol.util.ConDump.players(ctx.getSource())));
		dispatcher.register(literal("mystats").executes(PlayerCommands::myStats));

		dispatcher.register(literal("addhome").executes(PlayerCommands::addHome));
		dispatcher.register(literal("delhome").executes(PlayerCommands::delHome));
		dispatcher.register(literal("sethome").executes(PlayerCommands::setHome));
		dispatcher.register(literal("thome").executes(PlayerCommands::thome));
		dispatcher.register(literal("home").executes(PlayerCommands::homeMenu));

		dispatcher.register(literal("addp")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/addp <название>"))
				.then(argument("название", StringArgumentType.word()).suggests(CommandUtil.POINT_NAMES)
						.executes(PlayerCommands::addPoint)));
		dispatcher.register(literal("delp")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/delp <название>"))
				.then(argument("название", StringArgumentType.word()).suggests(CommandUtil.POINT_NAMES)
						.executes(PlayerCommands::delPoint)));
		dispatcher.register(literal("listp").executes(PlayerCommands::checkPoints));
		dispatcher.register(literal("setspawn").executes(PlayerCommands::setSpawn));
		dispatcher.register(literal("msg")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/msg <ник> <сообщение>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/msg <ник> <сообщение>"))
						.then(argument("сообщение", StringArgumentType.greedyString())
								.executes(PlayerCommands::msg))));
		dispatcher.register(literal("gevent").executes(PlayerCommands::gevent));
		dispatcher.register(literal("cevent").executes(PlayerCommands::cevent));
		dispatcher.register(literal("ptime").executes(PlayerCommands::serverTime));
		dispatcher.register(literal("sell").executes(PlayerCommands::sell));
		dispatcher.register(literal("auction").executes(PlayerCommands::auction));
		dispatcher.register(literal("rules").executes(ctx -> rules(ctx, false)));
		dispatcher.register(literal("conrules").executes(ctx -> rules(ctx, true)));
		dispatcher.register(literal("balance").executes(PlayerCommands::balance));
		dispatcher.register(literal("coins").executes(PlayerCommands::coins));
		dispatcher.register(literal("training").executes(PlayerCommands::training));
		dispatcher.register(literal("off").executes(PlayerCommands::trainingOff));
		dispatcher.register(literal("board").executes(PlayerCommands::board));
		dispatcher.register(literal("pay")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/pay <ник> <кол-во>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/pay <ник> <кол-во>"))
						.then(argument("кол-во", IntegerArgumentType.integer(1, 1_000_000_000)).suggests(CommandUtil.COUNTS)
								.executes(PlayerCommands::pay))));
		dispatcher.register(literal("password")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/password <пароль> <повтор>"))
				.then(argument("пароль", StringArgumentType.word())
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/password <пароль> <повтор>"))
						.then(argument("повтор", StringArgumentType.word()).executes(PlayerCommands::password))));
		dispatcher.register(literal("l")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/l <ник> <пароль>"))
				.then(argument("ник", StringArgumentType.word())
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/l <ник> <пароль>"))
						.then(argument("пароль", StringArgumentType.word()).executes(PlayerCommands::loginCmd))));
		dispatcher.register(literal("setpassword").executes(PlayerCommands::setPasswordGui));
		dispatcher.register(literal("fam")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/fam <текст>"))
				.then(argument("текст", StringArgumentType.greedyString()).executes(PlayerCommands::fam)));

		dispatcher.register(literal("ttp")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/ttp <ник1> <ник2>"))
				.then(argument("ник1", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/ttp <ник1> <ник2>"))
						.then(argument("цель", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
								.executes(PlayerCommands::ttp))));

		dispatcher.register(literal("tpi")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/tpi <ник|название_метки|номер>"))
				.then(argument("цель", StringArgumentType.word()).suggests(CommandUtil.TPI_TARGETS)
						.executes(PlayerCommands::tpiTarget)));

		dispatcher.register(literal("tpa")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/tpa <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> tpa(ctx, false))));
		dispatcher.register(literal("tpahere")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/tpahere <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> tpa(ctx, true))));
		dispatcher.register(literal("tpaccept").executes(PlayerCommands::tpaccept));
		dispatcher.register(literal("tpy").executes(PlayerCommands::tpaccept));
		dispatcher.register(literal("ok").executes(PlayerCommands::tpaccept));
		dispatcher.register(literal("tpdeny").executes(PlayerCommands::tpdeny));
		dispatcher.register(literal("tpn").executes(PlayerCommands::tpdeny));
		dispatcher.register(literal("shop").executes(PlayerCommands::shop));
		dispatcher.register(literal("top").executes(ctx -> top(ctx, false)));
		dispatcher.register(literal("clanetop").executes(ctx -> clanTop(ctx, false)));
		dispatcher.register(literal("conclanetop").executes(ctx -> clanTop(ctx, true)));
		dispatcher.register(literal("contop").executes(ctx -> ru.admincontrol.util.ConDump.top(ctx.getSource())));
		dispatcher.register(literal("clan")
				.executes(PlayerCommands::clanGui)
				.then(literal("create").then(argument("имя", StringArgumentType.word()).executes(PlayerCommands::clanCreate)))
				.then(literal("invite").then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(PlayerCommands::clanInvite)))
				.then(literal("accept").executes(PlayerCommands::clanAccept))
				.then(literal("leave").executes(PlayerCommands::clanLeave))
				.then(literal("disband").executes(PlayerCommands::clanDisband))
				.then(literal("sethome").executes(PlayerCommands::clanSetHome))
				.then(literal("home").executes(PlayerCommands::clanHome))
				.then(literal("info").executes(PlayerCommands::clanInfo))
				.then(literal("list").executes(PlayerCommands::clanList))
				.then(literal("deposit").then(argument("кол-во", IntegerArgumentType.integer(1, 1_000_000_000))
						.executes(PlayerCommands::clanDeposit))));
		dispatcher.register(literal("clanwar")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/clanwar <клан>  или  /clanwar take [сумма]"))
				.then(literal("take")
						.executes(ctx -> clanWarTake(ctx, -1))
						.then(argument("кол-во", IntegerArgumentType.integer(-1, 1_000_000_000))
								.executes(ctx -> clanWarTake(ctx, IntegerArgumentType.getInteger(ctx, "кол-во")))))
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(PlayerCommands::clanWarChallenge)));
		dispatcher.register(literal("cwar")
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cwar <клан>"))
				.then(literal("take")
						.executes(ctx -> clanWarTake(ctx, -1))
						.then(argument("кол-во", IntegerArgumentType.integer(-1, 1_000_000_000))
								.executes(ctx -> clanWarTake(ctx, IntegerArgumentType.getInteger(ctx, "кол-во")))))
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(PlayerCommands::clanWarChallenge)));
	}

	private static int help(CommandContext<ServerCommandSource> ctx, boolean chat, String raw) {
		ServerCommandSource src = ctx.getSource();
		java.util.List<ru.admincontrol.util.HelpLists.Entry> entries = ru.admincontrol.util.HelpLists.playerEntries();
		String resolved = ru.admincontrol.util.HelpLists.resolveFilter(entries, raw);
		final String query = resolved == null ? (raw == null ? "" : raw.trim()) : "";
		final String filter = resolved == null ? ru.admincontrol.util.HelpLists.ALL : resolved;
		java.util.List<String> lines = ru.admincontrol.util.HelpLists.lines(entries, query, filter);
		if (!chat && src.getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, new ru.admincontrol.screen.HelpFactory(
					"Команды игрока", "поиск по команде или описанию  ·  фильтр", entries,
					raw == null ? "" : raw.trim()));
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== Команды игрока ==="), false);
		if (!query.isBlank()) {
			src.sendFeedback(() -> Chat.gray("Поиск: " + query), false);
		} else if (!ru.admincontrol.util.HelpLists.ALL.equals(filter)) {
			src.sendFeedback(() -> Chat.gray("Фильтр: " + filter), false);
		}
		if (lines.isEmpty()) {
			src.sendFeedback(() -> Chat.yellow("Ничего не найдено."), false);
			return 1;
		}
		return ru.admincontrol.util.ChatDump.lines(src, lines);
	}

	private static int setSpawn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		ScreenNav.openRoot(player, new ru.admincontrol.screen.SetSpawnFactory(player));
		return 1;
	}

	private static int listAdmins(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		var online = ctx.getSource().getServer().getPlayerManager().getPlayerList().stream()
				.filter(data::isAdmin)
				.sorted((a, b) -> Integer.compare(data.getAdminLevel(b), data.getAdminLevel(a)))
				.toList();
		if (online.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("Нет администраторов в сети."), false);
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("Админы в сети (" + online.size() + "):"), false);
		for (ServerPlayerEntity player : online) {
			String nick = player.getGameProfile().getName();
			int level = data.getAdminLevel(player);
			ctx.getSource().sendFeedback(() -> Text.empty()
					.append(Chat.nick("  " + nick, level))
					.append(Chat.white(" — " + AdminRanks.title(level))), false);
		}
		return 1;
	}

	private static int listPlayers(CommandContext<ServerCommandSource> ctx) {
		var list = ctx.getSource().getServer().getPlayerManager().getPlayerList();
		if (list.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("Сейчас никого нет в сети."), false);
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("Игроки в сети (" + list.size() + "):"), false);
		StringBuilder line = new StringBuilder();
		int n = 0;
		for (ServerPlayerEntity player : list) {
			if (ru.admincontrol.util.NpcService.isNpc(player)) {
				continue;
			}
			if (n > 0) {
				line.append(", ");
			}
			line.append(player.getGameProfile().getName());
			n++;
			if (n >= 8) {
				ctx.getSource().sendFeedback(() -> Chat.white(line.toString()), false);
				line.setLength(0);
				n = 0;
			}
		}
		if (n > 0) {
			ctx.getSource().sendFeedback(() -> Chat.white(line.toString()), false);
		}
		return 1;
	}

	private static int myStats(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().progress(player);
		InventoryUtil.snapshot(player, record);
		java.util.List<String> labels = new java.util.ArrayList<>();
		java.util.List<String> values = new java.util.ArrayList<>();
		ru.admincontrol.util.StatsView.fillCommon(record, labels, values);
		ScreenNav.openRoot(player, new ru.admincontrol.screen.StatsFactory("Статистика", labels, values));
		return 1;
	}

	private static boolean canTeleport(ServerCommandSource source, ServerPlayerEntity actor, String kind) {
		if (ru.admincontrol.util.ClanWarService.jailed(actor)) {
			source.sendError(Chat.red(ru.admincontrol.util.ClanWarService.jailMessage()));
			return false;
		}
		DataManager data = DataManager.get();
		if (data.isAdmin(actor)) {
			if (data.settings().adminCheats) {
				return true;
			}
			source.sendError(Chat.red("Для администраторов /ttp /tpi /home отключены. Включите: /amode true"));
			return false;
		}
		if (data.settings().playerCheats) {
			return true;
		}
		if ("home".equals(kind) && ru.admincontrol.util.PlayFeatures.s().thomeUnlocked) {
			return true;
		}
		source.sendError(Chat.red("Для игроков /ttp /tpi /home отключены. Включите: /pmode true"));
		return false;
	}

	private static PlayerWaypoint captureHere(ServerPlayerEntity player, String name) {
		PlayerWaypoint point = new PlayerWaypoint();
		point.name = name;
		point.x = player.getX();
		point.y = player.getY();
		point.z = player.getZ();
		point.yaw = player.getYaw();
		point.pitch = player.getPitch();
		point.dimension = player.getWorld().getRegistryKey().getValue().toString();
		return point;
	}

	private static ServerWorld resolveWorld(MinecraftServer server, String dimension) {
		if (dimension == null || dimension.isBlank()) {
			return server.getOverworld();
		}
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return server.getOverworld();
		}
		ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
		return world != null ? world : server.getOverworld();
	}

	private static void teleportTo(ServerPlayerEntity player, PlayerWaypoint point) {
		ServerWorld world = resolveWorld(player.getServer(), point.dimension);
		player.teleport(world, point.x, point.y, point.z, point.yaw, point.pitch);
	}

	private static int addHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.extraHomes == null) {
			record.extraHomes = new java.util.ArrayList<>();
		}
		if (ru.admincontrol.util.PlayFeatures.homesUsed(record) >= ru.admincontrol.util.PlayFeatures.homeLimit(record)) {
			ctx.getSource().sendError(Chat.red("Лимит домов: " + ru.admincontrol.util.PlayFeatures.homeLimit(record)
					+ ". Купите слот в /shop."));
			return 0;
		}
		PlayerWaypoint saved;
		if (record.home == null) {
			saved = captureHere(player, "home");
			record.home = saved;
		} else {
			saved = captureHere(player, "home" + (record.extraHomes.size() + 2));
			record.extraHomes.add(saved);
		}
		DataManager.get().saveAll();
		ru.admincontrol.util.HomeProtection.refresh();
		ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.HOME, "поставил метку дома");
		ctx.getSource().sendFeedback(() -> Chat.green("Дом сохранён: " + saved.coordsLabel() + "."), false);
		return 1;
	}

	private static int delHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.home == null) {
			ctx.getSource().sendError(Chat.red("У вас нет метки дома."));
			return 0;
		}
		record.home = null;
		DataManager.get().saveAll();
		ru.admincontrol.util.HomeProtection.refresh();
		ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.HOME, "удалил метку дома");
		ctx.getSource().sendFeedback(() -> Chat.green("Метка дома удалена."), false);
		return 1;
	}

	private static int setHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		record.home = captureHere(player, "home");
		DataManager.get().saveAll();
		ru.admincontrol.util.HomeProtection.refresh();
		ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.HOME, "изменил метку дома");
		ctx.getSource().sendFeedback(() -> Chat.green("Дом установлен: " + record.home.coordsLabel() + "."), false);
		return 1;
	}

	private static int thome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		if (!canTeleport(ctx.getSource(), player, "home")) {
			return 0;
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.home == null) {
			ctx.getSource().sendError(Chat.red("У вас нет метки дома. Используйте /addhome или /sethome."));
			return 0;
		}
		ru.admincontrol.util.ChunkPrewarm.holdPoint(player.getServer(), record.home);
		ru.admincontrol.util.TeleportWarmup.start(player, "/thome", () -> {
			PlayerRecord now = DataManager.get().getOrCreate(player);
			if (now.home == null) {
				Chat.send(player, Chat.red("У вас нет метки дома."));
				return;
			}
			teleportTo(player, now.home);
			Chat.send(player, Chat.green("Вы телепортированы домой."));
		});
		return 1;
	}

	private static int homeMenu(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.HomeFactory(CommandUtil.self(ctx)));
		return 1;
	}

	private static int addPoint(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.points == null) {
			record.points = new java.util.ArrayList<>();
		}
		String name = StringArgumentType.getString(ctx, "название");
		if (findPoint(record, name) != null) {
			ctx.getSource().sendError(Chat.red("Метка «" + name + "» уже есть."));
			return 0;
		}
		record.points.add(captureHere(player, name));
		DataManager.get().saveAll();
		ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.WAYPOINT, "создал метку " + name);
		ctx.getSource().sendFeedback(() -> Chat.green("Метка «" + name + "» #" + record.points.size() + " сохранена."), false);
		return 1;
	}

	private static int delPoint(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		String name = StringArgumentType.getString(ctx, "название");
		PlayerWaypoint point = findPoint(record, name);
		if (point == null) {
			ctx.getSource().sendError(Chat.red("Метка «" + name + "» не найдена."));
			return 0;
		}
		record.points.remove(point);
		DataManager.get().saveAll();
		ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.WAYPOINT, "удалил метку " + name);
		ctx.getSource().sendFeedback(() -> Chat.green("Метка «" + name + "» удалена."), false);
		return 1;
	}

	private static int checkPoints(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.points == null || record.points.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("У вас нет меток. Добавьте: /addp <название>"), false);
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("Ваши метки (" + record.points.size() + "):"), false);
		int index = 1;
		for (PlayerWaypoint point : record.points) {
			int shown = index;
			PlayerWaypoint waypoint = point;
			ctx.getSource().sendFeedback(() -> Chat.gray("  #" + shown + " " + waypoint.name + " — "
					+ waypoint.coordsLabel() + " [" + waypoint.dimension + "]"), false);
			index++;
		}
		return 1;
	}

	private static PlayerWaypoint findPoint(PlayerRecord record, String name) {
		if (record.points == null || name == null) {
			return null;
		}
		for (PlayerWaypoint point : record.points) {
			if (point != null && point.name != null && point.name.equalsIgnoreCase(name)) {
				return point;
			}
		}
		return null;
	}

	private static boolean playerCanTeleportTo(ServerCommandSource source, ServerPlayerEntity actor, ServerPlayerEntity target) {
		DataManager data = DataManager.get();
		int actorRank = data.getAdminLevel(actor);
		int targetRank = data.getAdminLevel(target);
		if (actorRank >= AdminRanks.MOD) {
			if (!CommandUtil.requireCanAffect(source, target.getGameProfile().getName())) {
				return false;
			}
			return true;
		}
		if (!ru.admincontrol.util.TpGuard.canReach(actorRank, targetRank)) {
			source.sendError(Chat.red("Игроки не могут телепортироваться к администраторам 2 уровня и выше."));
			return false;
		}
		return true;
	}

	private static boolean playerCanMove(ServerCommandSource source, ServerPlayerEntity actor, ServerPlayerEntity from) {
		DataManager data = DataManager.get();
		int actorRank = data.getAdminLevel(actor);
		int fromRank = data.getAdminLevel(from);
		if (actorRank >= AdminRanks.MOD) {
			return CommandUtil.requireCanAffect(source, from.getGameProfile().getName());
		}
		if (fromRank >= AdminRanks.MOD) {
			source.sendError(Chat.red("Администратора можно телепортировать только с его разрешения: отправьте /tpahere "
					+ from.getGameProfile().getName() + ", он примет /tpy или отклонит /tpn."));
			return false;
		}
		return true;
	}

	private static boolean sendAdminTpRequest(ServerCommandSource source, ServerPlayerEntity from, ServerPlayerEntity admin, boolean here) {
		String err = ru.admincontrol.util.TpaService.request(from, admin, here);
		if (err != null) {
			source.sendError(Chat.red(err));
			return false;
		}
		source.sendFeedback(() -> Chat.yellow("К администратору " + admin.getGameProfile().getName()
				+ " нельзя телепортироваться сразу. Запрос отправлен. Ждите /tpy или /tpn."), false);
		return false;
	}

	private static int ttp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity actor = CommandUtil.self(ctx);
		if (!canTeleport(ctx.getSource(), actor, "cheat")) {
			return 0;
		}
		String fromNick = StringArgumentType.getString(ctx, "ник1");
		String toNick = StringArgumentType.getString(ctx, "цель");
		ServerPlayerEntity from = CommandUtil.requireOnline(ctx.getSource(), fromNick);
		ServerPlayerEntity to = CommandUtil.requireOnline(ctx.getSource(), toNick);
		if (from == null || to == null) {
			return 0;
		}
		if (!playerCanMove(ctx.getSource(), actor, from) || !playerCanTeleportTo(ctx.getSource(), actor, to)) {
			return 0;
		}
		if (!DataManager.get().isAdmin(actor)
				&& !from.getUuid().equals(actor.getUuid())
				&& !to.getUuid().equals(actor.getUuid())) {
			ctx.getSource().sendError(Chat.red("Обычный игрок может телепортировать только себя."));
			return 0;
		}
		ServerCommandSource source = ctx.getSource();
		ru.admincontrol.util.ChunkPrewarm.holdPlayer(to);
		ru.admincontrol.util.TeleportWarmup.start(from, "/ttp к " + to.getGameProfile().getName(), () -> {
			if (to.isRemoved() || to.networkHandler == null) {
				Chat.send(from, Chat.red("Игрок " + to.getGameProfile().getName() + " вышел — телепорт отменён."));
				return;
			}
			from.teleport(to.getServerWorld(), to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
			source.sendFeedback(() -> Chat.green("Игрок " + from.getGameProfile().getName()
					+ " телепортирован к " + to.getGameProfile().getName() + "."), false);
		});
		return 1;
	}

	private static int tpiTarget(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity actor = CommandUtil.self(ctx);
		if (!canTeleport(ctx.getSource(), actor, "cheat")) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "цель");
		PlayerRecord record = DataManager.get().getOrCreate(actor);

		try {
			int number = Integer.parseInt(raw);
			if (record.points != null && number >= 1 && number <= record.points.size()) {
				PlayerWaypoint point = record.points.get(number - 1);
				ru.admincontrol.util.ChunkPrewarm.holdPoint(actor.getServer(), point);
				ru.admincontrol.util.TeleportWarmup.start(actor, "метка «" + point.name + "»", () -> {
					teleportTo(actor, point);
					ru.admincontrol.util.AdminNotify.playerAction(actor, ru.admincontrol.data.ActionKind.WAYPOINT, "телепортировался на метку «" + point.name + "»");
					Chat.send(actor, Chat.green("Вы телепортированы на метку #" + number + " «" + point.name + "»."));
				});
				return 1;
			}
		} catch (NumberFormatException ignored) {
		}

		PlayerWaypoint named = findPoint(record, raw);
		if (named != null) {
			ru.admincontrol.util.ChunkPrewarm.holdPoint(actor.getServer(), named);
			ru.admincontrol.util.TeleportWarmup.start(actor, "метка «" + named.name + "»", () -> {
				teleportTo(actor, named);
				ru.admincontrol.util.AdminNotify.playerAction(actor, ru.admincontrol.data.ActionKind.WAYPOINT, "телепортировался на метку «" + named.name + "»");
				Chat.send(actor, Chat.green("Вы телепортированы на метку «" + named.name + "»."));
			});
			return 1;
		}

		ServerPlayerEntity target = CommandUtil.findOnline(ctx.getSource().getServer(), raw).orElse(null);
		if (target != null) {
			if (!playerCanTeleportTo(ctx.getSource(), actor, target)) {
				return 0;
			}
			ru.admincontrol.util.ChunkPrewarm.holdPlayer(target);
			ru.admincontrol.util.TeleportWarmup.start(actor, "/tpi к " + target.getGameProfile().getName(), () -> {
				if (target.isRemoved() || target.networkHandler == null) {
					Chat.send(actor, Chat.red("Игрок " + target.getGameProfile().getName() + " вышел — телепорт отменён."));
					return;
				}
				actor.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
				Chat.send(actor, Chat.green("Вы телепортированы к " + target.getGameProfile().getName() + "."));
			});
			return 1;
		}

		ctx.getSource().sendError(Chat.red("Не найден игрок или метка «" + raw + "»."));
		return 0;
	}

	private static int msg(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity from = CommandUtil.self(ctx);
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), StringArgumentType.getString(ctx, "ник"));
		if (target == null) {
			return 0;
		}
		String text = StringArgumentType.getString(ctx, "сообщение");
		Chat.send(target, Chat.beige("[MSG " + from.getGameProfile().getName() + "] " + text));
		ctx.getSource().sendFeedback(() -> Chat.gray("→ " + target.getGameProfile().getName() + ": " + text), false);
		return 1;
	}

	private static int gevent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		if (!ru.admincontrol.util.EventService.active()) {
			ctx.getSource().sendError(Chat.red("Сейчас нет мероприятия."));
			return 0;
		}
		String err = ru.admincontrol.util.EventService.joinError(player);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ru.admincontrol.util.EventService.prewarmArena();
		ru.admincontrol.util.TeleportWarmup.start(player, "мероприятие", () -> {
			String late = ru.admincontrol.util.EventService.joinError(player);
			if (late != null) {
				Chat.send(player, Chat.red(late));
				return;
			}
			if (!ru.admincontrol.util.EventService.join(player)) {
				Chat.send(player, Chat.red("Не удалось записаться."));
				return;
			}
			Chat.send(player, Chat.green("Вы записались на мероприятие. Ждите старта."));
		});
		return 1;
	}

	private static int cevent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		if (ru.admincontrol.util.EventService.isHost(player) && ru.admincontrol.util.EventService.active()) {
			ru.admincontrol.util.EventService.stop(player.getServer(), "остановил организатор " + player.getGameProfile().getName());
			ctx.getSource().sendFeedback(() -> Chat.yellow("Мероприятие остановлено."), false);
			return 1;
		}
		if (ru.admincontrol.util.EventService.fighting()) {
			ctx.getSource().sendError(Chat.red("Во время боя нельзя выйти. /cevent недоступен."));
			return 0;
		}
		if (!ru.admincontrol.util.EventService.leave(player, true)) {
			ctx.getSource().sendError(Chat.red("Вы не на мероприятии."));
			return 0;
		}
		return 1;
	}

	/** Сколько секунд дата и время из /ptime видны поверх любого экрана. */
	private static final int PTIME_SECONDS = 60;
	private static final String[] WEEKDAYS = {"понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"};

	private static int serverTime(CommandContext<ServerCommandSource> ctx) {
		java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneId.systemDefault());
		String date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " (" + WEEKDAYS[now.getDayOfWeek().getValue() - 1] + ")";
		String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		boolean overlay = player != null
				&& net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ru.admincontrol.net.ModPackets.PTIME);
		ctx.getSource().sendFeedback(() -> Chat.yellow("Дата: " + date + "  •  Время: " + time
				+ (overlay ? "  (видно в углу экрана " + PTIME_SECONDS + " с)" : "")), false);
		if (overlay) {
			net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
			buf.writeLong(now.toInstant().toEpochMilli());
			buf.writeInt(now.getOffset().getTotalSeconds());
			buf.writeVarInt(PTIME_SECONDS);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, ru.admincontrol.net.ModPackets.PTIME, buf);
		}
		return 1;
	}

	private static int sell(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity self = CommandUtil.self(ctx);
		String blocked = ru.admincontrol.util.Economy.tradeBlocked(self);
		if (blocked != null) {
			ctx.getSource().sendError(Chat.red(blocked));
			return 0;
		}
		ScreenNav.openRoot(self, new ru.admincontrol.screen.SellFactory());
		return 1;
	}

	private static int auction(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity self = CommandUtil.self(ctx);
		String blocked = ru.admincontrol.util.Economy.tradeBlocked(self);
		if (blocked != null) {
			ctx.getSource().sendError(Chat.red(blocked));
			return 0;
		}
		ScreenNav.openRoot(self, new ru.admincontrol.screen.AuctionFactory());
		return 1;
	}

	private static int balance(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		int coins = ru.admincontrol.util.Economy.balance(CommandUtil.self(ctx));
		ctx.getSource().sendFeedback(() -> Chat.gold("Ваш баланс: " + ru.admincontrol.util.Money.wc(coins)), false);
		return 1;
	}

	private static int coins(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.CoinsFactory());
		return 1;
	}

	private static int training(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.TrainingFactory());
		return 1;
	}

	private static int trainingOff(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		if (!ru.admincontrol.util.TrainingService.active(player)) {
			ctx.getSource().sendError(Chat.red("Вы не на тренировке."));
			return 0;
		}
		ru.admincontrol.util.TrainingService.off(player);
		return 1;
	}

	private static int board(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ru.admincontrol.util.BalanceBoard.toggle(CommandUtil.self(ctx));
		return 1;
	}

	private static int pay(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity from = CommandUtil.self(ctx);
		String nick = StringArgumentType.getString(ctx, "ник");
		int amount = IntegerArgumentType.getInteger(ctx, "кол-во");
		if (from.getGameProfile().getName().equalsIgnoreCase(nick)) {
			ctx.getSource().sendError(Chat.red("Нельзя перевести себе."));
			return 0;
		}
		if (DataManager.get() != null && !DataManager.get().settings().playerTransfers) {
			ctx.getSource().sendError(Chat.red("Переводы между игроками выключены."));
			return 0;
		}
		PlayerRecord dest = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (dest == null) {
			return 0;
		}
		if (!ru.admincontrol.util.Economy.transfer(from, dest, amount)) {
			ctx.getSource().sendError(Chat.red("Недостаточно World coin."));
			return 0;
		}
		ServerPlayerEntity to = CommandUtil.findOnline(ctx.getSource().getServer(), dest.nickname).orElse(null);
		if (to != null) {
			Chat.send(to, Chat.green("Вы получили " + ru.admincontrol.util.Money.wc(amount) + " от " + from.getGameProfile().getName() + "."));
		}
		Chat.send(from, Chat.green("Передано " + ru.admincontrol.util.Money.wc(amount) + " игроку " + dest.nickname + "."));
		return 1;
	}

	private static int password(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String pass = StringArgumentType.getString(ctx, "пароль");
		String repeat = StringArgumentType.getString(ctx, "повтор");
		ru.admincontrol.util.AuthService.tryRegister(CommandUtil.self(ctx), pass, repeat);
		return 1;
	}

	private static int loginCmd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ru.admincontrol.util.AuthService.tryLogin(CommandUtil.self(ctx),
				StringArgumentType.getString(ctx, "ник"), StringArgumentType.getString(ctx, "пароль"));
		return 1;
	}

	private static int setPasswordGui(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		if (ru.admincontrol.util.AuthService.pending(CommandUtil.self(ctx))) {
			ctx.getSource().sendError(Chat.red("Сначала войдите: /l или /password."));
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.PasswordFactory());
		return 1;
	}

	private static int fam(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.chat(CommandUtil.self(ctx), StringArgumentType.getString(ctx, "текст"));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		return 1;
	}

	private static int rules(CommandContext<ServerCommandSource> ctx, boolean chat) {
		java.util.List<String> lines = java.util.List.of(ru.admincontrol.util.RulesService.read().split("\\R", -1));
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, ru.admincontrol.screen.LogFactory.ofText("Правила мира", lines));
			return 1;
		}
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int tpa(CommandContext<ServerCommandSource> ctx, boolean here) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity from = CommandUtil.self(ctx);
		ServerPlayerEntity to = CommandUtil.requireOnline(ctx.getSource(), StringArgumentType.getString(ctx, "ник"));
		if (to == null) {
			return 0;
		}
		if (!here && DataManager.get().isAdmin(to) && !DataManager.get().isAdmin(from)) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("К администратору телепорт только по запросу."), false);
		}
		String err = ru.admincontrol.util.TpaService.request(from, to, here);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Запрос отправлен игроку " + to.getGameProfile().getName() + "."), false);
		return 1;
	}

	private static int tpaccept(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String msg = ru.admincontrol.util.TpaService.accept(CommandUtil.self(ctx));
		if (msg != null && (msg.startsWith("Нет") || msg.startsWith("Игрок") || msg.startsWith("Только"))) {
			ctx.getSource().sendError(Chat.red(msg));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green(msg), false);
		return 1;
	}

	private static int tpdeny(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String msg = ru.admincontrol.util.TpaService.deny(CommandUtil.self(ctx));
		if (msg.startsWith("Нет")) {
			ctx.getSource().sendError(Chat.red(msg));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.yellow(msg), false);
		return 1;
	}

	private static int shop(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		if (!ru.admincontrol.util.PlayFeatures.s().shopEnabled) {
			ctx.getSource().sendError(Chat.red("Магазин выключен."));
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.ShopFactory());
		return 1;
	}

	private static int top(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		java.util.List<String> lines = ru.admincontrol.util.PlayFeatures.topLines();
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, ru.admincontrol.screen.LogFactory.ofText("Недельный топ", lines));
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Недельный топ ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int clanTop(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		java.util.List<String> lines = ru.admincontrol.util.ClanTop.lines();
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, ru.admincontrol.screen.LogFactory.ofText("Топ кланов", lines));
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Топ кланов ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int clanGui(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.ClanFactory());
		return 1;
	}

	private static int clanCreate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.create(CommandUtil.self(ctx), StringArgumentType.getString(ctx, "имя"));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Клан создан."), false);
		return 1;
	}

	private static int clanInvite(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.invite(CommandUtil.self(ctx), StringArgumentType.getString(ctx, "ник"));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Приглашение отправлено."), false);
		return 1;
	}

	private static int clanAccept(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.accept(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Вы вступили в клан."), false);
		return 1;
	}

	private static int clanLeave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.leave(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Вы покинули клан."), false);
		return 1;
	}

	private static int clanDisband(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.disband(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.yellow("Клан распущен."), false);
		return 1;
	}

	private static int clanSetHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.setHome(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Дом клана установлен."), false);
		return 1;
	}

	private static int clanHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.requestHome(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		return 1;
	}

	private static int clanInfo(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		java.util.List<String> lines = ru.admincontrol.util.ClanService.info(CommandUtil.self(ctx));
		ScreenNav.openRoot(CommandUtil.self(ctx), ru.admincontrol.screen.LogFactory.ofText("Клан", lines));
		return 1;
	}

	private static int clanList(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = ru.admincontrol.util.ClanService.list();
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Кланы ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int clanDeposit(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource()) || playLocked(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanService.deposit(CommandUtil.self(ctx),
				IntegerArgumentType.getInteger(ctx, "кол-во"));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("WC отправлены в казну клана."), false);
		return 1;
	}

	private static int clanWarChallenge(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanWarService.challenge(CommandUtil.self(ctx),
				StringArgumentType.getString(ctx, "клан"));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Заявка на битву кланов отправлена."), false);
		return 1;
	}

	private static int clanWarTake(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.ClanWarService.takeTreasury(CommandUtil.self(ctx), amount);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		return 1;
	}

	private static boolean playLocked(ServerCommandSource source) {
		if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
			return false;
		}
		if (!ru.admincontrol.util.ActivityLock.blocked(player)) {
			return false;
		}
		source.sendError(Chat.red("На МП и тренировке /clan закрыт."));
		return true;
	}
}
