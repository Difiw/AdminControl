package ru.admincontrol.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PlayerWaypoint;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class CommandUtil {
	private static final Set<String> STAFF_COMMANDS = Set.copyOf(List.of(
			"ahelp", "g", "gh", "sg", "sgh", "banp", "unbanp", "permbanp", "unpermbanp",
			"akickp", "warnp", "unwarnp", "jailp", "unjailp", "spawn", "akill", "get", "punish",
			"amsg", "getinv", "congetinv", "setgamemode", "acreative", "asurvival", "aspectator", "aadventure",
			"adel", "aclear", "agive", "atime", "afill", "amode", "pmode", "nmode", "un_admin",
			"ahp", "aeat", "aweather", "setworldspawn", "sp", "spoff", "addnpc", "delnpc", "npc",
			"npclist", "connpclist", "npchere", "control", "uncontrol", "agm", "event", "ekick", "eventkick", "cwarok", "cwardeny", "cwarstop", "acommand", "chcommands", "conchcommands",
			"conahelp", "conget", "conpunish", "conplog", "conalog", "congetclan", "congetclanwh", "all_kick", "unagm",
			"banagm", "unbanagm", "stop_server", "start_server", "add_wight", "del_wight", "check_wight",
			"plog", "koto", "conkoto", "alog", "clearlog", "aclearlog", "setprice", "create_rules", "key_rules", "unkey_rules",
			"aserver", "atsetspawn", "setcheck", "abalance", "conabalance", "acoins", "conacoins", "addcoin", "uncoin", "setpower",
			"unprivate", "acasino", "setluck", "setcasino", "setnick", "areset", "tevent", "allevent", "aplayer", "aranks", "aroles", "nickfilter",
			"add_admin", "set_admin", "add_blogger", "all_clear", "all_unadm", "all_permban", "all_unpermban",
			"kill_server", "revive_server", "undev", "dserver", "devent", "dbuild", "dbuildkick", "getach", "congetach", "getclan", "getclanwh", "resetclan",
			"adminlist", "box", "unbox", "plcheck", "scheck", "fullstop", "offplayer", "setcommandadmin",
			"cw", "fz", "unfz",
			"aprivate", "aprivateremove", "aprivaterestore", "plogall", "alogall", "plogsearch", "alogsearch", "aweatherlog",
			"aeventlog", "atraininglog", "aboxlog", "clearweatherlog", "cleareventlog", "cleartraininglog", "clearboxlog",
			"eventstop", "admstats", "astats", "banipp", "unbanipp", "ipcheck", "filecheck", "anticheat", "actest", "buglist",
			"askills", "askillinfo", "askilllist", "askillgive", "askilltake", "bottraining", "duel", "beta"
	));

	private CommandUtil() {
	}

	public static final SuggestionProvider<ServerCommandSource> NPC_NAMES = (ctx, builder) ->
			CommandSource.suggestMatching(ru.admincontrol.util.NpcService.names(ctx.getSource().getServer()), builder);

	public static final SuggestionProvider<ServerCommandSource> KNOWN_PLAYERS = (ctx, builder) -> {
		Set<String> names = new LinkedHashSet<>();
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			names.add(player.getGameProfile().getName());
		}
		DataManager data = DataManager.get();
		if (data != null) {
			names.addAll(data.allNicknames());
		}
		return CommandSource.suggestMatching(names, builder);
	};

	public static final SuggestionProvider<ServerCommandSource> CLANS = (ctx, builder) -> {
		Set<String> names = new LinkedHashSet<>();
		if (DataManager.get() != null) {
			for (var clan : ru.admincontrol.util.ClanService.clans()) {
				if (clan.name != null && !clan.name.isBlank()) {
					names.add(clan.name);
				}
			}
		}
		return CommandSource.suggestMatching(names, builder);
	};

	public static final SuggestionProvider<ServerCommandSource> GAME_MODES = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("survival", "creative", "adventure", "spectator"), builder);

	public static final SuggestionProvider<ServerCommandSource> BOOL = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("true", "false"), builder);

	public static final SuggestionProvider<ServerCommandSource> ON_OFF = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("on", "off", "true", "false"), builder);

	public static final SuggestionProvider<ServerCommandSource> DAYS = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("1", "3", "7", "10", "30", "90"), builder);

	public static final SuggestionProvider<ServerCommandSource> MINUTES = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("5", "10", "30", "60", "1440"), builder);

	public static final SuggestionProvider<ServerCommandSource> COUNTS = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("1", "8", "16", "32", "64", "128"), builder);

	public static final SuggestionProvider<ServerCommandSource> HEALTH = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("0", "10", "20"), builder);

	public static final SuggestionProvider<ServerCommandSource> REASONS = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("гриф", "чит", "спам", "нарушение"), builder);

	public static final SuggestionProvider<ServerCommandSource> TIME_ADD = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("1000", "6000", "12000"), builder);

	public static final SuggestionProvider<ServerCommandSource> WEATHER_TIME = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("600", "1800", "6000"), builder);

	public static final SuggestionProvider<ServerCommandSource> STAFF_COMMAND_NAMES = (ctx, builder) -> {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (var item : ru.admincontrol.util.RankPerms.COMMANDS) {
			names.add(item.id);
		}
		return CommandSource.suggestMatching(names, builder);
	};

	public static final SuggestionProvider<ServerCommandSource> PLAYER_COMMANDS = (ctx, builder) ->
			CommandSource.suggestMatching(List.of(
					"home", "addhome", "delhome", "sethome", "thome", "tpi", "ttp",
					"addp", "delp", "listp", "msg", "pay", "sell", "auction",
					"tpa", "tpahere", "tpaccept", "tpdeny", "tpy", "tpn", "ok", "shop", "clan", "clanwar", "cwar", "top",
					"balance", "coins", "training", "off", "board", "rules", "setspawn", "gevent", "cevent", "ptime",
					"players", "admins", "mystats", "password", "setpassword", "l", "fam", "phelp", "conphelp", "rules", "conrules", "donate"), builder);

	public static final SuggestionProvider<ServerCommandSource> SCOPE_ALL = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("all"), builder);

	public static final SuggestionProvider<ServerCommandSource> LUCK = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("0", "1", "2", "5", "10"), builder);

	public static final SuggestionProvider<ServerCommandSource> POWER = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("1", "2", "5", "10"), builder);

	public static final SuggestionProvider<ServerCommandSource> TIME_SET = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("day", "noon", "night", "midnight"), builder);

	public static final SuggestionProvider<ServerCommandSource> TIME_QUERY = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("daytime", "gametime", "day"), builder);

	public static final SuggestionProvider<ServerCommandSource> AMOUNT_OR_ALL = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("all", "1", "16", "64"), builder);

	public static final SuggestionProvider<ServerCommandSource> WEATHER = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("clear", "rain", "thunder"), builder);

	public static final SuggestionProvider<ServerCommandSource> ADMIN_LEVELS = (ctx, builder) -> {
		List<String> levels = new ArrayList<>(standardLevels(ctx.getSource()));
		levels.addAll(ru.admincontrol.util.CustomRanks.assignableKeys(sourceRank(ctx.getSource())));
		return CommandSource.suggestMatching(levels, builder);
	};

	private static List<String> standardLevels(ServerCommandSource source) {
		if (CommandUtil.hasRank(source, AdminRanks.DEV)) {
			return List.of("1", "2", "3", "4", "4.5", "5", "6", "7", "8");
		}
		if (CommandUtil.hasRank(source, AdminRanks.GA)) {
			return List.of("1", "2", "3", "4", "4.5", "5", "6", "7");
		}
		if (CommandUtil.hasRank(source, AdminRanks.TECH)) {
			return List.of("1", "2", "3", "4", "4.5", "5", "6");
		}
		if (CommandUtil.hasRank(source, AdminRanks.ZGA)) {
			return List.of("1", "2", "3", "4", "4.5", "5");
		}
		if (CommandUtil.hasRank(source, AdminRanks.CURATOR)) {
			return List.of("1", "2", "3", "4", "4.5");
		}
		return List.of("1", "2", "3");
	}

	public static final SuggestionProvider<ServerCommandSource> POINT_NAMES = (ctx, builder) -> {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
			return builder.buildFuture();
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return builder.buildFuture();
		}
		PlayerRecord record = data.getOrCreate(player);
		Set<String> names = new LinkedHashSet<>();
		if (record.points != null) {
			for (PlayerWaypoint point : record.points) {
				if (point != null && point.name != null && !point.name.isBlank()) {
					names.add(point.name);
				}
			}
		}
		return CommandSource.suggestMatching(names, builder);
	};

	public static final SuggestionProvider<ServerCommandSource> TPI_TARGETS = (ctx, builder) -> {
		Set<String> names = new LinkedHashSet<>();
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			names.add(player.getGameProfile().getName());
		}
		if (ctx.getSource().getEntity() instanceof ServerPlayerEntity self) {
			DataManager data = DataManager.get();
			if (data != null) {
				PlayerRecord record = data.getOrCreate(self);
				if (record.points != null) {
					int index = 1;
					for (PlayerWaypoint point : record.points) {
						if (point != null && point.name != null && !point.name.isBlank()) {
							names.add(point.name);
							names.add(String.valueOf(index));
						}
						index++;
					}
				}
			}
		}
		return CommandSource.suggestMatching(names, builder);
	};

	public static int sourceRank(ServerCommandSource source) {
		Entity entity = source.getEntity();
		if (!(entity instanceof ServerPlayerEntity player)) {
			return AdminRanks.DEV;
		}
		DataManager data = DataManager.get();
		return data == null ? AdminRanks.NONE : data.getAdminLevel(player);
	}

	public static boolean canUse(ServerCommandSource source, String command) {
		if ("cw".equalsIgnoreCase(command)) {
			return canUseCw(source);
		}
		if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
			return true;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		String name = command == null ? "" : command.toLowerCase(Locale.ROOT);
		if ("add_admin".equals(name) && !data.hasAnyAdmin()) {
			return true;
		}
		if ("add_admin".equals(name) && data.settings().addAdminLockedToStaff) {
			return hasRank(source, AdminRanks.GA);
		}
		if (!data.isAdmin(player) && isStaffCommand(name)) {
			return false;
		}
		Boolean flag = ru.admincontrol.util.RankPerms.adminCommandFlag(player.getGameProfile().getName(), name);
		if (Boolean.FALSE.equals(flag)) {
			return false;
		}
		if (ru.admincontrol.util.RankPerms.bloggerDenied(sourceRank(source), name)) {
			return false;
		}
		if (Boolean.TRUE.equals(flag) && data.isAdmin(player)) {
			return true;
		}
		return ru.admincontrol.util.RankPerms.allowed(sourceRank(source), name);
	}

	/** CW diagnostics start at Senior; individual mutating subcommands enforce stricter ranks. */
	public static boolean canUseCw(ServerCommandSource source) {
		int rank = sourceRank(source);
		return rank >= AdminRanks.SENIOR && rank != AdminRanks.BLOGGER;
	}

	public static boolean requireCw(ServerCommandSource source) {
		if (canUseCw(source)) return true;
		AdminNotify.denied(source, "попытался использовать /cw без роли Senior+");
		return false;
	}

	public static boolean requireCommand(ServerCommandSource source, String command) {
		// Keep the execution check identical to the Brigadier visibility check.
		// Otherwise a command granted through an individual override is visible,
		// but silently denied as soon as it is executed (notably /dserver).
		if (canUse(source, command)) {
			return true;
		}
		AdminNotify.denied(source, "попытался использовать команду не по статусу (" + command + ")");
		return false;
	}

	public static boolean hasRank(ServerCommandSource source, int min) {
		return sourceRank(source) >= min;
	}

	public static boolean requireRank(ServerCommandSource source, int min, String what) {
		if (hasRank(source, min)) {
			return true;
		}
		AdminNotify.denied(source, "попытался использовать команду не по статусу (" + what + ")");
		return false;
	}

	public static boolean commandDeniedByRank(ServerPlayerEntity player, String command) {
		DataManager data = DataManager.get();
		if (player == null || data == null) {
			return false;
		}
		String name = command.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (name.startsWith("/")) {
			name = name.substring(1);
		}
		if ("add_admin".equals(name)) {
			if (!data.hasAnyAdmin()) {
				return false;
			}
			if (data.settings().addAdminLockedToStaff) {
				return data.getAdminLevel(player) < AdminRanks.GA;
			}
		}
		if (!data.isAdmin(player) && isStaffCommand(name)) {
			return true;
		}
		Boolean flag = ru.admincontrol.util.RankPerms.adminCommandFlag(player.getGameProfile().getName(), name);
		if (Boolean.FALSE.equals(flag)) {
			return true;
		}
		int rank = data.getAdminLevel(player);
		if (ru.admincontrol.util.RankPerms.bloggerDenied(rank, name)) {
			return true;
		}
		if (Boolean.TRUE.equals(flag) && data.isAdmin(player)) {
			return ru.admincontrol.util.CommandBlocker.blocked(player, name);
		}
		if (data.isAdmin(player) && !ru.admincontrol.util.RankPerms.allowed(rank, name)) {
			return true;
		}
		return ru.admincontrol.util.CommandBlocker.blocked(player, name);
	}

	public static boolean isStaffCommand(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		return STAFF_COMMANDS.contains(name.toLowerCase(Locale.ROOT));
	}

	public static boolean isRank2Source(ServerCommandSource source) {
		return hasRank(source, AdminRanks.ADMIN);
	}

	public static boolean isZgaSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.ZGA);
	}

	public static boolean isSeniorSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.SENIOR);
	}

	public static boolean isCuratorSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.CURATOR);
	}

	public static boolean isAdminSource(ServerCommandSource source) {
		Entity entity = source.getEntity();
		if (!(entity instanceof ServerPlayerEntity player)) {
			return true;
		}
		DataManager data = DataManager.get();
		return data != null && data.isAdmin(player);
	}

	public static boolean requireAdmin(ServerCommandSource source) {
		if (isAdminSource(source)) {
			return true;
		}
		source.sendError(Chat.red("У вас нет прав для этой команды"));
		return false;
	}

	public static boolean isHeadAdminSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.GA);
	}

	public static boolean isGaOrTechSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.TECH);
	}

	public static boolean isDevSource(ServerCommandSource source) {
		return hasRank(source, AdminRanks.DEV);
	}

	public static boolean requireGaOrTech(ServerCommandSource source, String what) {
		return requireCommand(source, what);
	}

	public static boolean requireHeadAdmin(ServerCommandSource source) {
		return requireRank(source, AdminRanks.GA, "только Главный админ");
	}

	public static boolean requireZga(ServerCommandSource source) {
		return requireRank(source, AdminRanks.ZGA, "только ЗГА и выше");
	}

	public static boolean requireSenior(ServerCommandSource source) {
		return requireRank(source, AdminRanks.SENIOR, "только Старший администратор и выше");
	}

	public static boolean requireCanAffect(ServerCommandSource source, String nick) {
		return requireCanAffect(source, nick, false);
	}

	public static boolean requireCanPunish(ServerCommandSource source, String nick) {
		return requireCanAffect(source, nick, true);
	}

	public static boolean requireCanAffect(ServerCommandSource source, String nick, boolean punishment) {
		if (nick == null || nick.isBlank()) {
			return true;
		}
		int exec = sourceRank(source);
		DataManager data = DataManager.get();
		if (data == null) {
			return true;
		}
		boolean self = source.getEntity() instanceof ServerPlayerEntity player
				&& player.getGameProfile().getName().equalsIgnoreCase(nick);
		int target = findOnline(source.getServer(), nick).map(data::getAdminLevel)
				.orElseGet(() -> data.findByNick(nick).map(record -> data.getAdminLevel(record.uuidOrNull(), record.nickname)).orElse(AdminRanks.NONE));
		if (punishment && self && exec < AdminRanks.GA) {
			AdminNotify.denied(source, "попытался выдать наказание самому себе");
			return false;
		}
		if (punishment && target >= AdminRanks.MOD && !ru.admincontrol.util.RankPerms.canPunishStaff(exec)) {
			AdminNotify.denied(source, "администраторы ниже 5 уровня не могут наказывать администрацию");
			return false;
		}
		if (target > exec) {
			AdminNotify.denied(source, "попытался применить админ-команду к более высокому рангу (" + nick
					+ ", " + AdminRanks.title(target) + ")");
			return false;
		}
		return true;
	}

	public static boolean requirePlayer(ServerCommandSource source) {
		if (source.getEntity() instanceof ServerPlayerEntity) {
			return true;
		}
		source.sendError(Chat.red("Эту команду может выполнить только игрок."));
		return false;
	}

	public static String adminName(ServerCommandSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof ServerPlayerEntity player) {
			return player.getGameProfile().getName();
		}
		return "Консоль";
	}

	public static String adminUuid(ServerCommandSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof ServerPlayerEntity player) {
			return player.getUuid().toString();
		}
		return "console";
	}

	public static Optional<ServerPlayerEntity> findOnline(MinecraftServer server, String nick) {
		ServerPlayerEntity exact = server.getPlayerManager().getPlayer(nick);
		if (exact != null) {
			return Optional.of(exact);
		}
		return server.getPlayerManager().getPlayerList().stream()
				.filter(p -> p.getGameProfile().getName().equalsIgnoreCase(nick))
				.findFirst();
	}

	public static PlayerRecord requireRecord(ServerCommandSource source, String nick) {
		DataManager data = DataManager.get();
		if (data == null) {
			source.sendError(Chat.red("Данные ещё не загружены."));
			return null;
		}
		Optional<PlayerRecord> record = data.findByNick(nick);
		if (record.isPresent()) {
			return record.get();
		}
		Optional<ServerPlayerEntity> online = findOnline(source.getServer(), nick);
		if (online.isPresent()) {
			return data.getOrCreate(online.get());
		}
		source.sendError(Chat.red("Игрок «" + nick + "» не найден."));
		return null;
	}

	public static ServerPlayerEntity requireOnline(ServerCommandSource source, String nick) {
		return findOnline(source.getServer(), nick).orElseGet(() -> {
			source.sendError(Chat.red("Игрок «" + nick + "» не в сети."));
			return null;
		});
	}

	public static Boolean parseBoolean(String raw, ServerCommandSource source) {
		String value = raw.toLowerCase(Locale.ROOT);
		if (value.equals("true") || value.equals("on") || value.equals("1") || value.equals("yes") || value.equals("да")) {
			return true;
		}
		if (value.equals("false") || value.equals("off") || value.equals("0") || value.equals("no") || value.equals("нет")) {
			return false;
		}
		source.sendError(Chat.red("Неверное значение. Используйте true или false."));
		return null;
	}

	public static int failSyntax(ServerCommandSource source, String usage) {
		source.sendError(Chat.yellow("Неправильный синтаксис. Использование: " + usage));
		if (source.getEntity() instanceof ServerPlayerEntity player) {
			ru.admincontrol.util.AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.COMMAND_FAIL,
					"неполная команда: " + usage, false);
			ru.admincontrol.util.CommandLog.mark();
		}
		return 0;
	}

	public static String nick(CommandContext<ServerCommandSource> ctx) {
		return StringArgumentType.getString(ctx, "ник");
	}

	public static ServerPlayerEntity self(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayer();
	}
}
