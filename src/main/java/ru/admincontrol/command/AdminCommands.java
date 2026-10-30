package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentRecord;
import ru.admincontrol.data.PunishmentType;
import ru.admincontrol.inventory.InspectedPlayerInventory;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.InventoryUtil;
import ru.admincontrol.util.ScreenNav;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminCommands {
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
	private static final int MAX_FILL = 32768;

	private AdminCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
		dispatcher.register(literal("ahelp").requires(CommandUtil::isAdminSource)
				.executes(ctx -> help(ctx, false, ""))
				.then(argument("поиск", StringArgumentType.greedyString())
						.executes(ctx -> help(ctx, false, StringArgumentType.getString(ctx, "поиск")))));
		dispatcher.register(literal("conahelp").requires(CommandUtil::isAdminSource)
				.executes(ctx -> help(ctx, true, ""))
				.then(argument("поиск", StringArgumentType.greedyString())
						.executes(ctx -> help(ctx, true, StringArgumentType.getString(ctx, "поиск")))));

		dispatcher.register(adminRoot("g", "/g <ник>")
				.then(nickArg().executes(ctx -> gotoPlayer(ctx, CommandUtil.nick(ctx), true))));
		dispatcher.register(adminRoot("gh", "/gh <ник>")
				.then(nickArg().executes(ctx -> bringPlayer(ctx, CommandUtil.nick(ctx), true))));
		dispatcher.register(adminRoot("sg", "/sg <ник>")
				.then(nickArg().executes(ctx -> gotoPlayer(ctx, CommandUtil.nick(ctx), false))));
		dispatcher.register(adminRoot("sgh", "/sgh <ник>")
				.then(nickArg().executes(ctx -> bringPlayer(ctx, CommandUtil.nick(ctx), false))));

		dispatcher.register(adminRoot("banp", "/banp <ник> <дни> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/banp <ник> <дни> <причина>"))
						.then(argument("дни", IntegerArgumentType.integer(1, 3650)).suggests(CommandUtil.DAYS)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/banp <ник> <дни> <причина>"))
								.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
										.executes(ctx -> ban(ctx, false))))));

		dispatcher.register(adminRoot("unbanp", "/unbanp <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unbanp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(ctx -> unban(ctx, false)))));

		dispatcher.register(rankRoot("permbanp", "/permbanp <ник> <причина>", s -> CommandUtil.hasRank(s, AdminRanks.SENIOR))
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/permbanp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(ctx -> ban(ctx, true)))));

		dispatcher.register(rankRoot("unpermbanp", "/unpermbanp <ник> <причина>", s -> CommandUtil.hasRank(s, AdminRanks.SENIOR))
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unpermbanp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(ctx -> unban(ctx, true)))));

		dispatcher.register(adminRoot("akickp", "/akickp <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/akickp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::kick))));

		dispatcher.register(adminRoot("warnp", "/warnp <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/warnp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::warn))));

		dispatcher.register(adminRoot("unwarnp", "/unwarnp <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unwarnp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::unwarn))));

		dispatcher.register(adminRoot("jailp", "/jailp <ник> <минуты> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/jailp <ник> <минуты> <причина>"))
						.then(argument("минуты", IntegerArgumentType.integer(1, 525600)).suggests(CommandUtil.MINUTES)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/jailp <ник> <минуты> <причина>"))
								.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
										.executes(AdminCommands::jail)))));
		dispatcher.register(adminRoot("unjailp", "/unjailp <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unjailp <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::unjail))));

		dispatcher.register(adminRoot("spawn", "/spawn <ник> <причина>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/spawn <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::spawn))));
		dispatcher.register(adminRoot("akill", "/akill <ник>").then(nickArg().executes(AdminCommands::killPlayer)));
		dispatcher.register(literal("get").requires(s -> CommandUtil.canUse(s, "get") || CommandUtil.canUse(s, "getclan")
						|| CommandUtil.canUse(s, "clanlog"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/get <ник>  или  /get clan <клан>"))
				.then(literal("clan").requires(s -> CommandUtil.canUse(s, "clanlog") || CommandUtil.canUse(s, "getclanwh"))
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(),
								"/get clan <название клана>  или  /get clan info <название клана>"))
						.then(literal("info")
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/get clan info <название клана>"))
								.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
										.executes(ctx -> getClanWarehouse(ctx, false))))
						.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
								.executes(ctx -> getClanLogs(ctx, false))))
				.then(nickArg().requires(s -> CommandUtil.canUse(s, "get"))
						.executes(ctx -> getStats(ctx, false))));
		dispatcher.register(adminRoot("koto", "/koto <ник>").then(nickArg().executes(ctx -> showKoto(ctx, false))));
		dispatcher.register(rankRoot("conkoto", "/conkoto <ник>", CommandUtil::isAdminSource)
				.then(nickArg().executes(ctx -> showKoto(ctx, true))));
		dispatcher.register(rankRoot("getach", "/getach <ник>", s -> CommandUtil.hasRank(s, ru.admincontrol.data.AdminRanks.SMOD))
				.then(nickArg().executes(AdminCommands::getAch)));
		dispatcher.register(rankRoot("congetach", "/congetach <ник>", s -> CommandUtil.hasRank(s, ru.admincontrol.data.AdminRanks.SMOD))
				.then(nickArg().executes(AdminCommands::conGetAch)));
		dispatcher.register(adminRoot("aplayer", "/aplayer <ник>").then(nickArg().executes(AdminCommands::openAplayer)));
		dispatcher.register(rankRoot("conget", "/conget <ник>", CommandUtil::isAdminSource)
				.then(nickArg().executes(ctx -> getStats(ctx, true))));
		dispatcher.register(rankRoot("getclan", "/getclan <клан>", CommandUtil::isCuratorSource)
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(ctx -> getClan(ctx, false))));
		dispatcher.register(rankRoot("congetclan", "/congetclan <клан>", CommandUtil::isCuratorSource)
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(ctx -> getClan(ctx, true))));
		dispatcher.register(rankRoot("getclanwh", "/getclanwh <клан>", CommandUtil::isCuratorSource)
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(ctx -> getClanWarehouse(ctx, false))));
		dispatcher.register(rankRoot("congetclanwh", "/congetclanwh <клан>", CommandUtil::isCuratorSource)
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(ctx -> getClanWarehouse(ctx, true))));
		dispatcher.register(rankRoot("punish", "/punish <ник>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> punishHistory(ctx, false))));
		dispatcher.register(rankRoot("conpunish", "/conpunish <ник>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> punishHistory(ctx, true))));
		dispatcher.register(adminRoot("amsg", "/amsg <ник> <сообщение>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/amsg <ник> <сообщение>"))
						.then(argument("сообщение", StringArgumentType.greedyString())
								.executes(AdminCommands::privateMessage))));
		dispatcher.register(rankRoot("getinv", "/getinv <ник>", CommandUtil::isRank2Source).then(nickArg().executes(ctx -> getInv(ctx, false))));
		dispatcher.register(rankRoot("congetinv", "/congetinv <ник>", CommandUtil::isRank2Source).then(nickArg().executes(ctx -> getInv(ctx, true))));

		dispatcher.register(rankRoot("setgamemode", "/setgamemode <ник> <creative|survival|spectator|adventure>", CommandUtil::isSeniorSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setgamemode <ник> <creative|survival|spectator|adventure>"))
						.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.GAME_MODES)
								.executes(AdminCommands::setGameMode))));

		registerSelfGamemode(dispatcher, "acreative", GameMode.CREATIVE);
		registerSelfGamemode(dispatcher, "asurvival", GameMode.SURVIVAL);
		registerSelfGamemode(dispatcher, "aspectator", GameMode.SPECTATOR);
		registerSelfGamemode(dispatcher, "aadventure", GameMode.ADVENTURE);

		dispatcher.register(adminRoot("adel", "/adel <ник> <предмет> <количество|all>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/adel <ник> <предмет> <количество|all>"))
						.then(argument("предмет", ItemStackArgumentType.itemStack(access))
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/adel <ник> <предмет> <количество|all>"))
								.then(argument("количество", StringArgumentType.word()).suggests(CommandUtil.AMOUNT_OR_ALL)
										.executes(AdminCommands::deleteItem)))));

		dispatcher.register(rankRoot("aclear", "/aclear <ник>", CommandUtil::isSeniorSource).then(nickArg().executes(AdminCommands::clearInv)));

		dispatcher.register(rankRoot("agive", "/agive <ник> <предмет> <количество>", s -> CommandUtil.hasRank(s, AdminRanks.SENIOR))
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/agive <ник> <предмет> <количество>"))
						.then(argument("предмет", ItemStackArgumentType.itemStack(access))
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/agive <ник> <предмет> <количество>"))
								.then(argument("количество", IntegerArgumentType.integer(1, 2304)).suggests(CommandUtil.COUNTS)
										.executes(AdminCommands::giveItem)))));

		dispatcher.register(rankRoot("atime", "/atime <set|add|query> <значение>", s -> CommandUtil.hasRank(s, AdminRanks.SENIOR))
				.then(literal("set").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/atime set <day|noon|night|midnight|число>"))
						.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.TIME_SET)
								.executes(AdminCommands::timeSet)))
				.then(literal("add").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/atime add <значение>"))
						.then(argument("значение", IntegerArgumentType.integer()).suggests(CommandUtil.TIME_ADD).executes(AdminCommands::timeAdd)))
				.then(literal("query").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/atime query <daytime|gametime|day>"))
						.then(argument("тип", StringArgumentType.word()).suggests(CommandUtil.TIME_QUERY)
								.executes(AdminCommands::timeQuery))));

		dispatcher.register(rankRoot("afill", "/afill <от> <до> <блок>", CommandUtil::isRank2Source)
				.then(argument("от", BlockPosArgumentType.blockPos())
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/afill <x1 y1 z1> <x2 y2 z2> <блок>"))
						.then(argument("до", BlockPosArgumentType.blockPos())
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/afill <x1 y1 z1> <x2 y2 z2> <блок>"))
								.then(argument("блок", BlockStateArgumentType.blockState(access))
										.executes(AdminCommands::fill)))));

		dispatcher.register(rankRoot("amode", "/amode <true|false>", CommandUtil::isRank2Source)
				.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.BOOL)
						.executes(ctx -> setCheatMode(ctx, "admin"))));
		dispatcher.register(rankRoot("pmode", "/pmode <true|false>", CommandUtil::isRank2Source)
				.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.BOOL)
						.executes(ctx -> setCheatMode(ctx, "player"))));
		dispatcher.register(adminRoot("nmode", "/nmode <ник> <true|false>")
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/nmode <ник> <true|false>"))
						.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.BOOL)
								.executes(AdminCommands::setPlayerCheats))));

		dispatcher.register(rankRoot("un_admin", "/un_admin <ник> <причина>", s -> CommandUtil.hasRank(s, AdminRanks.CURATOR))
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/un_admin <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::removeAdmin))));
		dispatcher.register(rankRoot("ahp", "/ahp <ник> <0-20>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/ahp <ник> <0-20>"))
						.then(argument("здоровье", IntegerArgumentType.integer(0, 20)).suggests(CommandUtil.HEALTH)
								.executes(AdminCommands::setHealth))));
		dispatcher.register(rankRoot("aeat", "/aeat <ник> <0-20>", CommandUtil::isAdminSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/aeat <ник> <0-20>"))
						.then(argument("сытость", IntegerArgumentType.integer(0, 20)).suggests(CommandUtil.HEALTH)
								.executes(AdminCommands::atEat))));
		dispatcher.register(rankRoot("aweather", "/aweather <clear|rain|thunder> [секунды]", s -> CommandUtil.hasRank(s, AdminRanks.SENIOR))
				.then(literal("clear").executes(ctx -> weather(ctx, "clear"))
						.then(argument("длительность", IntegerArgumentType.integer(1, 1000000)).suggests(CommandUtil.WEATHER_TIME)
								.executes(ctx -> weather(ctx, "clear"))))
				.then(literal("rain").executes(ctx -> weather(ctx, "rain"))
						.then(argument("длительность", IntegerArgumentType.integer(1, 1000000)).suggests(CommandUtil.WEATHER_TIME)
								.executes(ctx -> weather(ctx, "rain"))))
				.then(literal("thunder").executes(ctx -> weather(ctx, "thunder"))
						.then(argument("длительность", IntegerArgumentType.integer(1, 1000000)).suggests(CommandUtil.WEATHER_TIME)
								.executes(ctx -> weather(ctx, "thunder")))));
		dispatcher.register(literal("setworldspawn").requires(s -> CommandUtil.canUse(s, "setworldspawn")).executes(AdminCommands::setWorldSpawn));

		dispatcher.register(rankRoot("sp", "/sp <ник>", CommandUtil::isAdminSource).then(nickArg().executes(AdminCommands::spectate)));
		dispatcher.register(literal("spoff").requires(s -> CommandUtil.canUse(s, "spoff")).executes(AdminCommands::spectateOff));
		dispatcher.register(rankRoot("addnpc", "/addnpc <ник>", CommandUtil::isCuratorSource)
				.then(nickArg().executes(AdminCommands::addNps)));
		dispatcher.register(rankRoot("delnpc", "/delnpc <ник>", CommandUtil::isCuratorSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.NPC_NAMES)
						.executes(AdminCommands::delNps)));
		dispatcher.register(literal("npclist").requires(s -> CommandUtil.canUse(s, "npclist")).executes(AdminCommands::listNps));
		dispatcher.register(literal("connpclist").requires(s -> CommandUtil.canUse(s, "connpclist")).executes(AdminCommands::conListNps));
		dispatcher.register(literal("connpslist").requires(s -> CommandUtil.canUse(s, "connpclist")).executes(AdminCommands::conListNps));
		dispatcher.register(rankRoot("npchere", "/npchere <ник>", CommandUtil::isCuratorSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.NPC_NAMES)
						.executes(AdminCommands::npcHere)));
		dispatcher.register(rankRoot("npc", "/npc <ник> <команда|текст>", CommandUtil::isCuratorSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.NPC_NAMES)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/npc <ник> <команда|текст>"))
						.then(argument("действие", StringArgumentType.greedyString())
								.executes(AdminCommands::npcAct))));
		dispatcher.register(rankRoot("control", "/control <ник>", CommandUtil::isCuratorSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.NPC_NAMES)
						.executes(AdminCommands::controlNps)));
		dispatcher.register(literal("uncontrol").requires(s -> CommandUtil.canUse(s, "uncontrol")).executes(AdminCommands::uncontrolNps));
		dispatcher.register(rankRoot("agm", "/agm [ник]", CommandUtil::isSeniorSource)
				.executes(AdminCommands::toggleAgm)
				.then(nickArg().executes(AdminCommands::toggleAgm)));
		dispatcher.register(rankRoot("event", "/event", CommandUtil::isSeniorSource).executes(AdminCommands::openEvent));
		dispatcher.register(rankRoot("allevent", "/allevent", CommandUtil::isSeniorSource).executes(AdminCommands::openAllEvent));
		dispatcher.register(rankRoot("tevent", "/tevent", CommandUtil::isCuratorSource).executes(AdminCommands::openTestEvent));
		dispatcher.register(rankRoot("box", "/box", CommandUtil::isRank2Source).executes(AdminCommands::startBox));
		dispatcher.register(rankRoot("unbox", "/unbox", CommandUtil::isRank2Source).executes(AdminCommands::stopBox));
		dispatcher.register(rankRoot("plcheck", "/plcheck", CommandUtil::isCuratorSource).executes(AdminCommands::plCheck));
		dispatcher.register(rankRoot("scheck", "/scheck", CommandUtil::isCuratorSource).executes(AdminCommands::sCheck));
		dispatcher.register(rankRoot("fullstop", "/fullstop", CommandUtil::isCuratorSource).executes(AdminCommands::fullStop));
		dispatcher.register(rankRoot("offplayer", "/offplayer <ник>", CommandUtil::isCuratorSource)
				.then(nickArg().executes(AdminCommands::offPlayer)));
		dispatcher.register(rankRoot("setcommandadmin", "/setcommandadmin <команда> <true|false> <ник>", s -> CommandUtil.hasRank(s, AdminRanks.GA))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setcommandadmin <команда> <true|false> <ник>"))
				.then(argument("команда", StringArgumentType.word()).suggests(CommandUtil.STAFF_COMMAND_NAMES)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setcommandadmin <команда> <true|false> <ник>"))
						.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.BOOL)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setcommandadmin <команда> <true|false> <ник>"))
								.then(nickArg().executes(AdminCommands::setCommandAdmin)))));
		dispatcher.register(adminRoot("adminlist", "/adminlist").executes(AdminCommands::adminList));
		dispatcher.register(rankRoot("cwarok", "/cwarok [id]", CommandUtil::isAdminSource)
				.executes(ctx -> clanWarDecide(ctx, true, ""))
				.then(argument("id", StringArgumentType.word()).executes(ctx ->
						clanWarDecide(ctx, true, StringArgumentType.getString(ctx, "id")))));
		dispatcher.register(rankRoot("cwardeny", "/cwardeny [id]", CommandUtil::isAdminSource)
				.executes(ctx -> clanWarDecide(ctx, false, ""))
				.then(argument("id", StringArgumentType.word()).executes(ctx ->
						clanWarDecide(ctx, false, StringArgumentType.getString(ctx, "id")))));
		dispatcher.register(rankRoot("cwarstop", "/cwarstop", CommandUtil::isAdminSource).executes(AdminCommands::clanWarStop));
		dispatcher.register(rankRoot("ekick", "/ekick <ник>", CommandUtil::isSeniorSource)
				.then(nickArg().executes(AdminCommands::eventKick)));
		dispatcher.register(rankRoot("eventkick", "/eventkick <ник>", CommandUtil::isSeniorSource)
				.then(nickArg().executes(AdminCommands::eventKick)));
		dispatcher.register(rankRoot("acommand", "/acommand <команда> <off|on> [all]", CommandUtil::isRank2Source)
				.then(argument("команда", StringArgumentType.word()).suggests(CommandUtil.PLAYER_COMMANDS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/acommand <команда> <off|on> [all]"))
						.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.ON_OFF)
								.executes(AdminCommands::toggleCommand)
								.then(argument("область", StringArgumentType.word()).suggests(CommandUtil.SCOPE_ALL)
										.executes(AdminCommands::toggleCommand)))));
		dispatcher.register(rankRoot("chcommands", "/chcommands", CommandUtil::isRank2Source).executes(ctx -> listBlockedCommands(ctx, false)));
		dispatcher.register(rankRoot("conchcommands", "/conchcommands", CommandUtil::isRank2Source).executes(ctx -> listBlockedCommands(ctx, true)));
		dispatcher.register(rankRoot("all_kick", "/all_kick <причина>", CommandUtil::isGaOrTechSource)
				.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS).executes(AdminCommands::allKick)));
		dispatcher.register(rankRoot("unagm", "/unagm <ник>", CommandUtil::isGaOrTechSource).then(nickArg().executes(AdminCommands::unAgm)));
		dispatcher.register(rankRoot("banagm", "/banagm <ник>", CommandUtil::isGaOrTechSource).then(nickArg().executes(AdminCommands::banAgm)));
		dispatcher.register(rankRoot("unbanagm", "/unbanagm <ник>", CommandUtil::isGaOrTechSource).then(nickArg().executes(AdminCommands::unbanAgm)));
		dispatcher.register(rankRoot("stop_server", "/stop_server <причина>", CommandUtil::isGaOrTechSource)
				.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS).executes(AdminCommands::stopServer)));
		dispatcher.register(literal("start_server").requires(s -> CommandUtil.canUse(s, "start_server")).executes(AdminCommands::startServer));
		dispatcher.register(rankRoot("add_wight", "/add_wight <ник>", CommandUtil::isGaOrTechSource).then(nickArg().executes(AdminCommands::addWight)));
		dispatcher.register(rankRoot("del_wight", "/del_wight <ник>", CommandUtil::isGaOrTechSource).then(nickArg().executes(AdminCommands::delWight)));
		dispatcher.register(literal("check_wight").requires(s -> CommandUtil.canUse(s, "check_wight")).executes(AdminCommands::checkWight));
		dispatcher.register(rankRoot("plog", "/plog <ник> [поиск]", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> showLog(ctx, false, false, ""))
						.then(argument("поиск", StringArgumentType.greedyString())
								.executes(ctx -> showLog(ctx, false, false, StringArgumentType.getString(ctx, "поиск"))))));
		dispatcher.register(rankRoot("alog", "/alog <ник> [поиск]", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> showLog(ctx, true, false, ""))
						.then(argument("поиск", StringArgumentType.greedyString())
								.executes(ctx -> showLog(ctx, true, false, StringArgumentType.getString(ctx, "поиск"))))));
		dispatcher.register(rankRoot("conplog", "/conplog <ник> [поиск]", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> showLog(ctx, false, true, ""))
						.then(argument("поиск", StringArgumentType.greedyString())
								.executes(ctx -> showLog(ctx, false, true, StringArgumentType.getString(ctx, "поиск"))))));
		dispatcher.register(rankRoot("conalog", "/conalog <ник> [поиск]", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> showLog(ctx, true, true, ""))
						.then(argument("поиск", StringArgumentType.greedyString())
								.executes(ctx -> showLog(ctx, true, true, StringArgumentType.getString(ctx, "поиск"))))));
		dispatcher.register(rankRoot("clearlog", "/clearlog <ник|all>", CommandUtil::isGaOrTechSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> clearLog(ctx, false))));
		dispatcher.register(rankRoot("aclearlog", "/aclearlog <ник|all>", CommandUtil::isHeadAdminSource)
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> clearLog(ctx, true))));
		dispatcher.register(rankRoot("setnick", "/setnick <ник1> <ник2> <причина>", CommandUtil::isHeadAdminSource)
				.then(argument("ник1", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setnick <ник1> <ник2> <причина>"))
						.then(argument("ник2", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setnick <ник1> <ник2> <причина>"))
								.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
										.executes(AdminCommands::setNick)))));
		dispatcher.register(rankRoot("areset", "/areset <ник> <причина>", CommandUtil::isHeadAdminSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/areset <ник> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::resetAccount))));
		dispatcher.register(rankRoot("resetclan", "/resetclan <клан>", CommandUtil::isHeadAdminSource)
				.then(argument("клан", StringArgumentType.word()).suggests(CommandUtil.CLANS)
						.executes(AdminCommands::resetClan)));
		dispatcher.register(literal("setprice").requires(s -> CommandUtil.canUse(s, "setprice")).executes(AdminCommands::openSetPrice));
		dispatcher.register(literal("create_rules").requires(s -> CommandUtil.canUse(s, "create_rules")).executes(AdminCommands::createRules));
		dispatcher.register(literal("key_rules").requires(s -> CommandUtil.canUse(s, "key_rules")).executes(AdminCommands::keyRules));
		dispatcher.register(literal("unkey_rules").requires(s -> CommandUtil.canUse(s, "unkey_rules")).executes(AdminCommands::unkeyRules));
		dispatcher.register(literal("aserver").requires(s -> CommandUtil.canUse(s, "aserver"))
				.executes(AdminCommands::openServerPanel)
				.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.BOOL)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/aserver <true|false> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(AdminCommands::atServerLock))));
		dispatcher.register(rankRoot("atsetspawn", "/atsetspawn <ник>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(AdminCommands::atSetSpawn)));
		dispatcher.register(literal("setcheck").requires(s -> CommandUtil.canUse(s, "setcheck")).executes(AdminCommands::openCheck));
		dispatcher.register(rankRoot("abalance", "/abalance <ник>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> adminBalance(ctx, false))));
		dispatcher.register(rankRoot("conabalance", "/conabalance <ник>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> adminBalance(ctx, true))));
		dispatcher.register(rankRoot("acoins", "/acoins <ник>", CommandUtil::isSeniorSource)
				.then(nickArg().executes(ctx -> showCoinHistory(ctx, false))));
		dispatcher.register(rankRoot("conacoins", "/conacoins <ник>", CommandUtil::isSeniorSource)
				.then(nickArg().executes(ctx -> showCoinHistory(ctx, true))));
		dispatcher.register(rankRoot("addcoin", "/addcoin <ник> <кол-во>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/addcoin <ник> <кол-во>"))
						.then(argument("кол-во", IntegerArgumentType.integer(1, 1_000_000_000)).suggests(CommandUtil.COUNTS)
								.executes(AdminCommands::addCoin))));
		dispatcher.register(rankRoot("uncoin", "/uncoin <ник> <кол-во>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/uncoin <ник> <кол-во>"))
						.then(argument("кол-во", IntegerArgumentType.integer(1, 1_000_000_000)).suggests(CommandUtil.COUNTS)
								.executes(AdminCommands::unCoin))));
		dispatcher.register(rankRoot("setpower", "/setpower <ник> <сила>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setpower <ник> <сила>"))
						.then(argument("сила", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1, 1000.0))
								.suggests(CommandUtil.POWER)
								.executes(AdminCommands::setPower))));
		dispatcher.register(rankRoot("unprivate", "/unprivate <ник>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(AdminCommands::unprivate)));
		dispatcher.register(rankRoot("acasino", "/acasino <ник> <true|false>", CommandUtil::isGaOrTechSource)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/acasino <ник> <true|false>"))
						.then(argument("значение", StringArgumentType.word()).suggests(CommandUtil.BOOL)
								.executes(AdminCommands::setPlayerCasino))));
		dispatcher.register(rankRoot("setluck", "/setluck <ник> <значение>", CommandUtil::isRank2Source)
				.then(nickArg().executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/setluck <ник> <значение>"))
						.then(argument("значение", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1000.0))
								.suggests(CommandUtil.LUCK)
								.executes(AdminCommands::setLuck))));
		dispatcher.register(literal("setcasino").requires(s -> CommandUtil.canUse(s, "setcasino")).executes(AdminCommands::openCasino));
		dispatcher.register(rankRoot("aranks", "/aranks", s -> CommandUtil.canUse(s, "aranks")).executes(AdminCommands::openARanks));
		dispatcher.register(rankRoot("aroles", "/aroles", s -> CommandUtil.canUse(s, "aroles")).executes(AdminCommands::openARoles));
		dispatcher.register(rankRoot("nickfilter", "/nickfilter", s -> CommandUtil.canUse(s, "nickfilter"))
				.executes(AdminCommands::nickFilterList)
				.then(literal("list").executes(AdminCommands::nickFilterList))
				.then(literal("add").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/nickfilter add <символ>"))
						.then(argument("символ", StringArgumentType.greedyString()).executes(AdminCommands::nickFilterAdd)))
				.then(literal("remove").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/nickfilter remove <символ>"))
						.then(argument("символ", StringArgumentType.greedyString()).executes(AdminCommands::nickFilterRemove)))
				.then(literal("reset").executes(AdminCommands::nickFilterReset))
				.then(literal("allow").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/nickfilter allow <ник>"))
						.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
								.executes(AdminCommands::nickFilterAllow)))
				.then(literal("deny").executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/nickfilter deny <ник>"))
						.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
								.executes(AdminCommands::nickFilterDeny)))
				.then(literal("whitelist").executes(AdminCommands::nickFilterWhitelist)));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> adminRoot(String name, String usage) {
		return literal(name).requires(s -> CommandUtil.canUse(s, name))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> rankRoot(String name, String usage,
			Predicate<ServerCommandSource> requirement) {
		return literal(name).requires(s -> CommandUtil.canUse(s, name))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, String> nickArg() {
		return argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS);
	}

	private static void registerSelfGamemode(CommandDispatcher<ServerCommandSource> dispatcher, String name, GameMode mode) {
		dispatcher.register(literal(name).requires(s -> CommandUtil.canUse(s, name)).executes(ctx -> selfGameMode(ctx, mode)));
	}

	private static boolean guardTarget(CommandContext<ServerCommandSource> ctx, String nick) {
		return CommandUtil.requireAdmin(ctx.getSource()) && CommandUtil.requireCanAffect(ctx.getSource(), nick);
	}

	private static int help(CommandContext<ServerCommandSource> ctx, boolean chat, String raw) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		ServerCommandSource src = ctx.getSource();
		java.util.List<ru.admincontrol.util.HelpLists.Entry> entries = ru.admincontrol.util.HelpLists.adminEntries(src);
		String resolved = ru.admincontrol.util.HelpLists.resolveFilter(entries, raw);
		final String query = resolved == null ? (raw == null ? "" : raw.trim()) : "";
		final String filter = resolved == null ? ru.admincontrol.util.HelpLists.ALL : resolved;
		java.util.List<String> lines = ru.admincontrol.util.HelpLists.lines(entries, query, filter);
		if (!chat && src.getEntity() instanceof ServerPlayerEntity player) {
			String subtitle = AdminRanks.title(CommandUtil.sourceRank(src))
					+ "  ур. " + AdminRanks.display(CommandUtil.sourceRank(src))
					+ "  ·  поиск и фильтр";
			ScreenNav.openRoot(player, new ru.admincontrol.screen.HelpFactory(
					"Команды администрации", subtitle, entries, raw == null ? "" : raw.trim()));
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== Команды (" + AdminRanks.title(CommandUtil.sourceRank(src))
				+ ", ур. " + AdminRanks.display(CommandUtil.sourceRank(src)) + ") ==="), false);
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

	private static void send(ServerCommandSource src, String line) {
		src.sendFeedback(() -> Chat.gray(line), false);
	}

	private static int gotoPlayer(CommandContext<ServerCommandSource> ctx, String nick, boolean notify) throws CommandSyntaxException {
		if (!guardTarget(ctx, nick) || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		admin.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
		if (notify) {
			Chat.send(target, Chat.blue("Администратор " + admin.getGameProfile().getName() + " телепортировался к вам"));
		}
		AdminFeedback.tell(ctx.getSource(), "Вы телепортированы к " + target.getGameProfile().getName() + ".");
		AdminNotify.record(ctx.getSource(), "GOTO", target.getGameProfile().getName(), notify ? "g" : "sg");
		return 1;
	}

	private static int bringPlayer(CommandContext<ServerCommandSource> ctx, String nick, boolean notify) throws CommandSyntaxException {
		if (!guardTarget(ctx, nick) || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		target.teleport(admin.getServerWorld(), admin.getX(), admin.getY(), admin.getZ(), admin.getYaw(), admin.getPitch());
		if (notify) {
			Chat.send(target, Chat.blue("Администратор " + admin.getGameProfile().getName() + " телепортировал вас к себе"));
		}
		AdminFeedback.tell(ctx.getSource(), "Игрок " + target.getGameProfile().getName() + " телепортирован к вам.");
		AdminNotify.record(ctx.getSource(), "BRING", target.getGameProfile().getName(), notify ? "gh" : "sgh");
		return 1;
	}

	private static int ban(CommandContext<ServerCommandSource> ctx, boolean permanent) {
		String nick = StringArgumentType.getString(ctx, "ник");
		if (permanent) {
			if (!CommandUtil.requireCommand(ctx.getSource(), "permbanp") || !CommandUtil.requireCanPunish(ctx.getSource(), nick)) {
				return 0;
			}
		} else if (!guardTarget(ctx, nick) || !CommandUtil.requireCanPunish(ctx.getSource(), nick)) {
			return 0;
		}
		String reason = StringArgumentType.getString(ctx, "причина");
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		int days = permanent ? 0 : IntegerArgumentType.getInteger(ctx, "дни");
		int max = AdminRanks.maxBanDays(CommandUtil.sourceRank(ctx.getSource()));
		if (!permanent && days > max) {
			AdminNotify.denied(ctx.getSource(), "ваш уровень позволяет бан не более чем на " + max + " дн.");
			return 0;
		}
		return ModerationService.ban(ctx.getSource(), record, days, reason, permanent) ? 1 : 0;
	}

	private static int unban(CommandContext<ServerCommandSource> ctx, boolean permanent) {
		if (permanent) {
			if (!CommandUtil.requireCommand(ctx.getSource(), "unpermbanp")) {
				return 0;
			}
		} else if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		String reason = StringArgumentType.getString(ctx, "причина");
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		return ModerationService.unban(ctx.getSource(), record, reason, permanent) ? 1 : 0;
	}

	private static int kick(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanPunish(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		PlayerRecord record = DataManager.get().getOrCreate(target);
		ModerationService.kick(ctx.getSource(), target, record, StringArgumentType.getString(ctx, "причина"));
		return 1;
	}

	private static int warn(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanPunish(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		ModerationService.warn(ctx.getSource(), record, StringArgumentType.getString(ctx, "причина"));
		return 1;
	}

	private static int unwarn(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		ModerationService.unwarn(ctx.getSource(), record, StringArgumentType.getString(ctx, "причина"));
		return 1;
	}

	private static int spawn(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		ru.admincontrol.util.SpawnService.teleportToChoice(target);
		String reason = StringArgumentType.getString(ctx, "причина");
		Chat.send(target, Chat.blue("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " телепортировал вас на точку возрождения. Причина: " + reason));
		AdminFeedback.tell(ctx.getSource(), "Игрок " + target.getGameProfile().getName()
				+ " телепортирован на точку возрождения.");
		AdminNotify.record(ctx.getSource(), "SPAWN", target.getGameProfile().getName(), reason);
		return 1;
	}

	private static int killPlayer(CommandContext<ServerCommandSource> ctx) {
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCommand(ctx.getSource(), "akill")
				|| !CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		target.kill();
		Chat.send(target, Chat.blue("Администратор " + CommandUtil.adminName(ctx.getSource()) + " убил вас"));
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " убил игрока " + target.getGameProfile().getName()));
		AdminFeedback.tell(ctx.getSource(), "Игрок " + target.getGameProfile().getName() + " убит.");
		AdminNotify.record(ctx.getSource(), "AKILL", target.getGameProfile().getName(), "убийство");
		return 1;
	}

	private static int setWorldSpawn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "setworldspawn") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		ServerWorld world = admin.getServerWorld();
		world.setSpawnPos(admin.getBlockPos(), admin.getYaw());
		if (world != ctx.getSource().getServer().getOverworld()) {
			AdminFeedback.tell(ctx.getSource(), "Точка спавна установлена в текущем мире.");
		}
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + admin.getGameProfile().getName()
				+ " изменил общую точку появления в мире"));
		AdminFeedback.tell(ctx.getSource(), "Общая точка спавна: "
				+ admin.getBlockPos().getX() + " " + admin.getBlockPos().getY() + " " + admin.getBlockPos().getZ() + ".");
		AdminNotify.record(ctx.getSource(), "ASETWORLDSPAWN", "-", admin.getBlockPos().toShortString());
		return 1;
	}

	private static int openAplayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireAdmin(ctx.getSource()) || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.AplayerFactory(record.nickname));
		return 1;
	}

	private static int getStats(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			InventoryUtil.snapshot(online, record);
		}
		java.util.List<String> labels = new java.util.ArrayList<>();
		java.util.List<String> values = new java.util.ArrayList<>();
		ru.admincontrol.util.StatsView.fillCommon(record, labels, values);
		ru.admincontrol.util.StatsView.fillAdmin(record, online, labels, values);
		java.util.List<String> lines = ru.admincontrol.util.ChatDump.zip(labels, values);
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.StatsFactory("Игрок  " + record.nickname, labels, values));
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Игрок " + record.nickname + " ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int getClanLogs(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!LogAccess.requireNotBlogger(ctx.getSource()) || !CommandUtil.requireCommand(ctx.getSource(), "clanlog")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String query = StringArgumentType.getString(ctx, "клан");
		var clan = ru.admincontrol.util.ClanService.lookup(query);
		if (clan == null) {
			ctx.getSource().sendError(Chat.red(ru.admincontrol.util.ClanService.notFound(query)));
			return 0;
		}
		if (!LogAccess.requireNotBlogger(ctx.getSource())) {
			return 0;
		}
		var lines = ru.admincontrol.util.ActivityLog.readClan(clan.name);
		String title = "Клан  " + clan.name;
		if (lines.isEmpty()) {
			java.util.List<String> fallback = ru.admincontrol.util.ClanService.inspectLines(clan);
			if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
				java.util.List<ru.admincontrol.util.ActivityLog.Line> shown = new java.util.ArrayList<>();
				shown.add(new ru.admincontrol.util.ActivityLog.Line("Пока нет событий. Текущее состояние:", true,
						ru.admincontrol.data.ActionKind.CLAN));
				for (String line : fallback) {
					shown.add(new ru.admincontrol.util.ActivityLog.Line(line, true, ru.admincontrol.data.ActionKind.CLAN));
				}
				ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.LogFactory(title, shown, true, true, ""));
				return 1;
			}
			return ru.admincontrol.util.ClanService.dumpClan(ctx.getSource(), clan);
		}
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.LogFactory(title, lines, true, true, "").tracked());
			return 1;
		}
		ru.admincontrol.util.AdminStats.logToChat(ctx.getSource(), title);
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Логи клана " + clan.name + " ==="), false);
		return ru.admincontrol.util.ChatDump.logLines(ctx.getSource(), lines);
	}

	private static int getClan(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), chat ? "congetclan" : "getclan")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String query = StringArgumentType.getString(ctx, "клан");
		var clan = ru.admincontrol.util.ClanService.lookup(query);
		if (clan == null) {
			ctx.getSource().sendError(Chat.red(ru.admincontrol.util.ClanService.notFound(query)));
			return 0;
		}
		java.util.List<String> lines = ru.admincontrol.util.ClanService.inspectLines(clan);
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			java.util.List<String> labels = new java.util.ArrayList<>();
			java.util.List<String> values = new java.util.ArrayList<>();
			for (String line : lines) {
				int split = line.indexOf(':');
				if (split > 0 && split < line.length() - 1) {
					labels.add(line.substring(0, split).trim());
					values.add(line.substring(split + 1).trim());
				} else {
					labels.add(line);
					values.add("");
				}
			}
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.StatsFactory("Клан  " + clan.name, labels, values));
			return 1;
		}
		return ru.admincontrol.util.ClanService.dumpClan(ctx.getSource(), clan);
	}

	private static int getClanWarehouse(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), chat ? "congetclanwh" : "getclanwh")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String query = StringArgumentType.getString(ctx, "клан");
		var clan = ru.admincontrol.util.ClanService.lookup(query);
		if (clan == null) {
			ctx.getSource().sendError(Chat.red(ru.admincontrol.util.ClanService.notFound(query)));
			return 0;
		}
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			if (clan.warehouseLevel <= 0) {
				ctx.getSource().sendError(Chat.red("У клана «" + clan.name + "» склад ещё не куплен."));
				return 0;
			}
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.ClanWarehouseFactory(clan));
			return 1;
		}
		return ru.admincontrol.util.ClanService.dumpWarehouse(ctx.getSource(), clan);
	}

	private static int getAch(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "getach") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), StringArgumentType.getString(ctx, "ник"));
		if (record == null) {
			return 0;
		}
		java.util.UUID uuid = record.uuidOrNull();
		if (uuid == null) {
			ctx.getSource().sendError(Chat.red("У игрока нет UUID — достижения недоступны."));
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.AchievementsFactory(record.nickname, uuid));
		return 1;
	}

	private static int conGetAch(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "congetach")) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), StringArgumentType.getString(ctx, "ник"));
		if (record == null) {
			return 0;
		}
		java.util.UUID uuid = record.uuidOrNull();
		if (uuid == null) {
			ctx.getSource().sendError(Chat.red("У игрока нет UUID — достижения недоступны."));
			return 0;
		}
		java.util.List<ru.admincontrol.screen.AchievementsScreenHandler.Row> rows =
				ru.admincontrol.util.Achievements.rows(ctx.getSource().getServer(), uuid);
		if (rows.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("Достижений нет (или игрок ещё не заходил в этот мир)."), false);
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Достижения " + record.nickname + " ==="), false);
		for (var row : rows) {
			ctx.getSource().sendFeedback(() -> Chat.gold((row.done ? "[+] " : "[ ] ") + row.title), false);
		}
		return 1;
	}

	private static int punishHistory(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "punish")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null || !LogAccess.requireNotBlogger(ctx.getSource())) {
			return 0;
		}
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("Баны: активные " + record.countActive(PunishmentType.BAN)
				+ ", перм. " + record.countActive(PunishmentType.PERMABAN)
				+ ", всего " + (record.countByType(PunishmentType.BAN) + record.countByType(PunishmentType.PERMABAN)));
		lines.add("Jail: активные " + record.countActive(PunishmentType.JAIL)
				+ ", всего " + record.countByType(PunishmentType.JAIL));
		lines.add("Варны: активные " + record.activeWarns + "/3, всего " + record.countByType(PunishmentType.WARN));
		lines.add("Кики: " + record.countByType(PunishmentType.KICK));
		lines.add("Устные предупреждения: " + record.countByType(PunishmentType.VERBAL_WARN));
		long now = System.currentTimeMillis();
		DataManager.get().findActiveBan(record.uuidOrNull(), record.nickname).ifPresent(ban ->
				lines.add("Сейчас: " + (ban.permanent ? "перманентный бан" : "бан" + untilLabel(ban.expiresAt, now))));
		DataManager.get().findActiveJail(record.uuidOrNull(), record.nickname).ifPresent(jail ->
				lines.add("Сейчас: jail" + untilLabel(jail.expiresAt, now)));
		lines.add("");
		if (record.history == null || record.history.isEmpty()) {
			lines.add("История пуста.");
		} else {
			for (PunishmentRecord item : record.history) {
				boolean timed = item.type == PunishmentType.BAN || item.type == PunishmentType.JAIL
						|| item.type == PunishmentType.PERMABAN;
				String state;
				if (item.type == PunishmentType.VERBAL_WARN) {
					state = "";
				} else if (timed && item.active && item.isExpired(now)) {
					state = "  ○ истёк";
				} else {
					state = item.active ? "  ● активно" : "  ○ снято";
				}
				lines.add("[" + DATE.format(java.time.Instant.ofEpochMilli(item.createdAt)) + "] "
						+ item.type.russianName() + state
						+ (timed ? "  —  " + termLabel(item, now) : "")
						+ "  —  " + item.reason + "  /  " + item.adminNick);
			}
		}
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), ru.admincontrol.screen.LogFactory.ofText("Наказания  " + record.nickname, lines).tracked());
			return 1;
		}
		ru.admincontrol.util.AdminStats.logToChat(ctx.getSource(), "Наказания " + record.nickname);
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Наказания " + record.nickname + " ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static String untilLabel(Long expiresAt, long now) {
		if (expiresAt == null || expiresAt <= 0) {
			return "";
		}
		return " до " + ru.admincontrol.util.Durations.until(expiresAt)
				+ " (осталось " + ru.admincontrol.util.Durations.human(expiresAt - now) + ")";
	}

	/** «срок: 10 дней (1 нед. 3 дн.) · до 20.10.2026 18:00 · осталось 2 дн. 5 ч». */
	private static String termLabel(PunishmentRecord item, long now) {
		if (item.type == PunishmentType.PERMABAN) {
			return "срок: навсегда";
		}
		StringBuilder out = new StringBuilder("срок: ").append(item.durationLabel());
		long length = 0;
		if (item.expiresAt != null && item.expiresAt > 0) {
			length = item.expiresAt - item.createdAt;
		} else if (item.type == PunishmentType.JAIL && item.durationMinutes != null) {
			length = item.durationMinutes * 60_000L;
		} else if (item.durationDays != null) {
			length = item.durationDays * 86_400_000L;
		}
		boolean regroup = item.type == PunishmentType.JAIL
				? item.durationMinutes != null && item.durationMinutes >= 60
				: item.durationDays != null && item.durationDays >= 7;
		if (length > 0 && regroup) {
			out.append(" (").append(ru.admincontrol.util.Durations.human(length)).append(")");
		}
		if (item.expiresAt != null && item.expiresAt > 0) {
			out.append(" · до ").append(ru.admincontrol.util.Durations.until(item.expiresAt));
			if (item.active && item.expiresAt > now) {
				out.append(" · осталось ").append(ru.admincontrol.util.Durations.human(item.expiresAt - now));
			}
		}
		return out.toString();
	}

	private static int privateMessage(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		String text = StringArgumentType.getString(ctx, "сообщение");
		String admin = CommandUtil.adminName(ctx.getSource());
		Chat.send(target, Chat.beige("[MSG " + admin + "] " + text));
		AdminFeedback.tell(ctx.getSource(), "Сообщение отправлено игроку " + target.getGameProfile().getName() + ".");
		AdminNotify.record(ctx.getSource(), "MSG", target.getGameProfile().getName(), text);
		return 1;
	}

	private static int getInv(CommandContext<ServerCommandSource> ctx, boolean console) throws CommandSyntaxException {
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCommand(ctx.getSource(), console ? "congetinv" : "getinv")) {
			return 0;
		}
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		boolean asPlayer = !console && ctx.getSource().getEntity() instanceof ServerPlayerEntity;
		if (asPlayer && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (asPlayer) {
			ServerPlayerEntity admin = CommandUtil.self(ctx);
			if (target != null) {
				InventoryUtil.snapshot(target, record);
				ScreenNav.openRoot(admin, new ru.admincontrol.screen.InspectInventoryFactory(target));
				AdminNotify.record(ctx.getSource(), "GETINV", target.getGameProfile().getName(), "открытие инвентаря");
			} else {
				ScreenNav.openRoot(admin, new ru.admincontrol.screen.InspectInventoryFactory(record));
				AdminNotify.record(ctx.getSource(), "GETINV", record.nickname, "инвентарь оффлайн");
			}
			return 1;
		}
		if (target != null) {
			InventoryUtil.snapshot(target, record);
			InventoryUtil.dumpTo(ctx.getSource(), record.nickname, target.getInventory());
		} else {
			InventoryUtil.dumpTo(ctx.getSource(), record.nickname, InventoryUtil.inventoryFromNbt(record.inventoryNbt));
		}
		AdminNotify.record(ctx.getSource(), "GETINV", record.nickname, "инвентарь в консоль");
		return 1;
	}

	private static int setGameMode(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		GameMode mode = parseGameMode(StringArgumentType.getString(ctx, "режим"));
		if (mode == null) {
			return CommandUtil.failSyntax(ctx.getSource(), "/setgamemode <ник> <creative|survival|spectator|adventure>");
		}
		target.changeGameMode(mode);
		DataManager.get().getOrCreate(target).gameMode = mode.getName();
		DataManager.get().saveAll();
		ctx.getSource().sendFeedback(() -> Chat.green("Игровой режим " + target.getGameProfile().getName() + " изменён на " + mode.getName() + "."), false);
		AdminNotify.record(ctx.getSource(), "SETGAMEMODE", target.getGameProfile().getName(), mode.getName());
		return 1;
	}

	private static int selfGameMode(CommandContext<ServerCommandSource> ctx, GameMode mode) throws CommandSyntaxException {
		if (!CommandUtil.requireAdmin(ctx.getSource()) || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		admin.changeGameMode(mode);
		ctx.getSource().sendFeedback(() -> Chat.green("Ваш игровой режим: " + mode.getName() + "."), false);
		AdminNotify.record(ctx.getSource(), "AGAMEMODE", admin.getGameProfile().getName(), mode.getName());
		return 1;
	}

	private static int deleteItem(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		Item item;
		try {
			item = ItemStackArgumentType.getItemStackArgument(ctx, "предмет").createStack(1, false).getItem();
		} catch (CommandSyntaxException e) {
			ctx.getSource().sendError(Chat.red("Предмет не найден. Пример: diamond или minecraft:diamond"));
			return 0;
		}
		String amountRaw = StringArgumentType.getString(ctx, "количество");
		int removed = 0;
		if (amountRaw.equalsIgnoreCase("all")) {
			for (int i = 0; i < target.getInventory().size(); i++) {
				ItemStack stack = target.getInventory().getStack(i);
				if (stack.isOf(item)) {
					removed += stack.getCount();
					target.getInventory().setStack(i, ItemStack.EMPTY);
				}
			}
		} else {
			int left;
			try {
				left = Integer.parseInt(amountRaw);
			} catch (NumberFormatException e) {
				return CommandUtil.failSyntax(ctx.getSource(), "/adel <ник> <предмет> <количество|all>");
			}
			if (left <= 0) {
				ctx.getSource().sendError(Chat.red("Количество должно быть положительным или all."));
				return 0;
			}
			for (int i = 0; i < target.getInventory().size() && left > 0; i++) {
				ItemStack stack = target.getInventory().getStack(i);
				if (!stack.isOf(item)) {
					continue;
				}
				int take = Math.min(left, stack.getCount());
				stack.decrement(take);
				left -= take;
				removed += take;
			}
		}
		target.getInventory().markDirty();
		int removedCount = removed;
		ctx.getSource().sendFeedback(() -> Chat.green("Удалено " + removedCount + " шт. у игрока " + target.getGameProfile().getName() + "."), false);
		AdminNotify.record(ctx.getSource(), "ADEL", target.getGameProfile().getName(), amountRaw);
		return 1;
	}

	private static int clearInv(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		target.getInventory().clear();
		Chat.send(target, Chat.blue("Администратор " + CommandUtil.adminName(ctx.getSource()) + " очистил ваш инвентарь"));
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " очистил инвентарь игрока " + target.getGameProfile().getName()));
		AdminFeedback.tell(ctx.getSource(), "Инвентарь игрока " + target.getGameProfile().getName() + " очищен.");
		AdminNotify.record(ctx.getSource(), "ACLEAR", target.getGameProfile().getName(), "очистка инвентаря");
		return 1;
	}

	private static int giveItem(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "agive")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		ItemStackArgument itemArg = ItemStackArgumentType.getItemStackArgument(ctx, "предмет");
		int count = IntegerArgumentType.getInteger(ctx, "количество");
		ItemStack stack = itemArg.createStack(count, false);
		ItemStack copy = stack.copy();
		boolean ok = target.getInventory().insertStack(stack);
		if (!ok || !stack.isEmpty()) {
			target.dropItem(stack, false);
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Выдано " + count + " × " + copy.getName().getString()
				+ " игроку " + target.getGameProfile().getName() + "."), false);
		AdminNotify.record(ctx.getSource(), "AGIVE", target.getGameProfile().getName(),
				copy.getName().getString() + " x" + count);
		return 1;
	}

	private static int timeSet(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "atime")) {
			return 0;
		}
		String value = StringArgumentType.getString(ctx, "значение").toLowerCase(Locale.ROOT);
		long time = switch (value) {
			case "day" -> 1000L;
			case "noon" -> 6000L;
			case "night" -> 13000L;
			case "midnight" -> 18000L;
			default -> {
				try {
					yield Long.parseLong(value);
				} catch (NumberFormatException e) {
					CommandUtil.failSyntax(ctx.getSource(), "/atime set <day|noon|night|midnight|число>");
					yield -1L;
				}
			}
		};
		if (time < 0) {
			return 0;
		}
		for (ServerWorld world : ctx.getSource().getServer().getWorlds()) {
			world.setTimeOfDay(time);
		}
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " изменил время в мире"));
		AdminFeedback.tell(ctx.getSource(), "Время мира установлено: " + value + ".");
		AdminNotify.record(ctx.getSource(), "ATIME", "-", "set " + value);
		return 1;
	}

	private static int timeAdd(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "atime")) {
			return 0;
		}
		int value = IntegerArgumentType.getInteger(ctx, "значение");
		for (ServerWorld world : ctx.getSource().getServer().getWorlds()) {
			world.setTimeOfDay(world.getTimeOfDay() + value);
		}
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " изменил время в мире"));
		AdminFeedback.tell(ctx.getSource(), "К времени мира добавлено " + value + ".");
		AdminNotify.record(ctx.getSource(), "ATIME", "-", "add " + value);
		return 1;
	}

	private static int timeQuery(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "atime")) {
			return 0;
		}
		ServerWorld world = (ServerWorld) ctx.getSource().getWorld();
		String type = StringArgumentType.getString(ctx, "тип").toLowerCase(Locale.ROOT);
		long result = switch (type) {
			case "daytime" -> world.getTimeOfDay() % 24000L;
			case "gametime" -> world.getTime();
			case "day" -> world.getTimeOfDay() / 24000L;
			default -> -1L;
		};
		if (result < 0) {
			return CommandUtil.failSyntax(ctx.getSource(), "/atime query <daytime|gametime|day>");
		}
		ctx.getSource().sendFeedback(() -> Chat.green("Значение " + type + ": " + result), false);
		return 1;
	}

	private static int fill(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		BlockPos from = BlockPosArgumentType.getLoadedBlockPos(ctx, "от");
		BlockPos to = BlockPosArgumentType.getLoadedBlockPos(ctx, "до");
		BlockStateArgument blockArg = BlockStateArgumentType.getBlockState(ctx, "блок");
		BlockState state = blockArg.getBlockState();
		BlockBox box = BlockBox.create(from, to);
		int volume = box.getBlockCountX() * box.getBlockCountY() * box.getBlockCountZ();
		if (volume > MAX_FILL) {
			ctx.getSource().sendError(Chat.red("Слишком большая область (" + volume + " блоков). Максимум " + MAX_FILL + "."));
			return 0;
		}
		ServerWorld world = (ServerWorld) ctx.getSource().getWorld();
		int changed = 0;
		for (BlockPos pos : BlockPos.iterate(from, to)) {
			if (world.setBlockState(pos, state, 2)) {
				changed++;
			}
		}
		int changedCount = changed;
		ctx.getSource().sendFeedback(() -> Chat.green("Заполнено блоков: " + changedCount + "."), false);
		AdminNotify.record(ctx.getSource(), "AFILL", "-", state.toString());
		return changed;
	}

	private static int setCheatMode(CommandContext<ServerCommandSource> ctx, String scope) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		Boolean value = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "значение"), ctx.getSource());
		if (value == null) {
			return 0;
		}
		if ("admin".equals(scope)) {
			DataManager.get().settings().adminCheats = value;
		} else {
			DataManager.get().settings().playerCheats = value;
		}
		DataManager.get().saveAll();
		String admin = CommandUtil.adminName(ctx.getSource());
		if ("player".equals(scope)) {
			Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + admin
					+ " " + (value ? "включил" : "выключил") + " /pmode для игроков"));
			AdminFeedback.tell(ctx.getSource(), "Читы игроков: " + (value ? "включены" : "выключены") + ".");
		} else {
			AdminFeedback.tell(ctx.getSource(), "Читы администраторов: " + (value ? "включены" : "выключены") + ".");
		}
		AdminNotify.record(ctx.getSource(), "admin".equals(scope) ? "AMODE" : "PMODE", "-", String.valueOf(value));
		return 1;
	}

	private static int setPlayerCheats(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		Boolean value = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "значение"), ctx.getSource());
		if (value == null) {
			return 0;
		}
		record.cheatsEnabled = value;
		DataManager.get().saveAll();
		ctx.getSource().sendFeedback(() -> Chat.green("Чит-команды для " + record.nickname + ": "
				+ (value ? "включены" : "выключены") + "."), false);
		AdminNotify.record(ctx.getSource(), "NMODE", record.nickname, String.valueOf(value));
		return 1;
	}

	private static int removeAdmin(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "un_admin")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		DataManager data = DataManager.get();
		boolean self = ctx.getSource().getEntity() instanceof ServerPlayerEntity executor
				&& executor.getGameProfile().getName().equalsIgnoreCase(nick);
		if (self && CommandUtil.sourceRank(ctx.getSource()) < AdminRanks.GA) {
			AdminNotify.denied(ctx.getSource(), "нельзя снять себя с должности");
			return 0;
		}
		int exec = CommandUtil.sourceRank(ctx.getSource());
		if (exec < AdminRanks.GA && !CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		if (data.lockedDeveloper(record)) {
			AdminNotify.denied(ctx.getSource(), "нельзя снять Разработчика — только /undev у себя");
			return 0;
		}
		if (!data.isAdmin(record.uuidOrNull(), record.nickname)) {
			ctx.getSource().sendError(Chat.red("Игрок «" + record.nickname + "» не является администратором."));
			return 0;
		}
		if (data.isHeadAdmin(record.uuidOrNull(), record.nickname)
				&& exec < AdminRanks.DEV
				&& (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity executor) || !data.isHeadAdmin(executor))) {
			AdminNotify.denied(ctx.getSource(), "попытался снять Главного админа");
			return 0;
		}
		String reason = "снятие прав";
		try {
			reason = StringArgumentType.getString(ctx, "причина");
		} catch (IllegalArgumentException ignored) {
		}
		boolean othersRemain = data.removeAdmin(record);
		AdminNotify.record(ctx.getSource(), "UN_ADMIN", record.nickname, reason);
		if (othersRemain) {
			ctx.getSource().sendFeedback(() -> Chat.green("Права администратора сняты с " + record.nickname + "."), false);
		} else {
			ctx.getSource().sendFeedback(() -> Chat.yellow("Это был последний администратор. Команда /add_admin снова доступна всем игрокам."), false);
			CommandSync.refreshAllExcept(ctx.getSource().getServer(), record.nickname);
		}
		CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).ifPresent(player ->
				player.networkHandler.disconnect(Chat.red("С вас сняты права администратора. При следующем входе вы будете обычным игроком.")));
		CommandSync.refreshAll(ctx.getSource().getServer());
		ru.admincontrol.util.TabListColors.refresh(ctx.getSource().getServer());
		return 1;
	}

	private static int setHealth(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "ahp")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		int health = IntegerArgumentType.getInteger(ctx, "здоровье");
		target.setHealth(health);
		Chat.send(target, Chat.blue("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " изменил ваше здоровье: " + health + "/20"));
		AdminFeedback.tell(ctx.getSource(), "Здоровье игрока " + target.getGameProfile().getName()
				+ " установлено: " + health + "/20.");
		AdminNotify.record(ctx.getSource(), "AHP", target.getGameProfile().getName(), String.valueOf(health));
		return 1;
	}

	private static int weather(CommandContext<ServerCommandSource> ctx, String type) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "aweather")) {
			return 0;
		}
		int durationTicks = 6000;
		try {
			durationTicks = IntegerArgumentType.getInteger(ctx, "длительность") * 20;
		} catch (IllegalArgumentException ignored) {
		}
		durationTicks = Math.min(Math.max(durationTicks, 1), 1000000);
		boolean raining = !"clear".equals(type);
		boolean thundering = "thunder".equals(type);
		int clear = raining ? 0 : durationTicks;
		int rain = raining ? durationTicks : 0;
		for (ServerWorld world : ctx.getSource().getServer().getWorlds()) {
			world.setWeather(clear, rain, raining, thundering);
		}
		String label = switch (type) {
			case "clear" -> "ясно";
			case "rain" -> "дождь";
			default -> "гроза";
		};
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " изменил погоду в мире"));
		AdminFeedback.tell(ctx.getSource(), "Погода: " + label + " (" + (durationTicks / 20) + " сек).");
		AdminNotify.record(ctx.getSource(), "AWEATHER", "-", type + " " + (durationTicks / 20) + "s");
		return 1;
	}

	private static int jail(CommandContext<ServerCommandSource> ctx) {
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!guardTarget(ctx, nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		int minutes = IntegerArgumentType.getInteger(ctx, "минуты");
		int maxJail = ru.admincontrol.util.RankPerms.maxJailMinutes(CommandUtil.sourceRank(ctx.getSource()));
		if (minutes > maxJail) {
			AdminNotify.denied(ctx.getSource(), "ваш уровень позволяет jail не более чем на " + maxJail + " мин.");
			return 0;
		}
		String reason = StringArgumentType.getString(ctx, "причина");
		return ModerationService.jail(ctx.getSource(), record, minutes, reason) ? 1 : 0;
	}

	private static int unjail(CommandContext<ServerCommandSource> ctx) {
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!guardTarget(ctx, nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		return ModerationService.unjail(ctx.getSource(), record, StringArgumentType.getString(ctx, "причина")) ? 1 : 0;
	}

	private static int atServerLock(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "atserver")) {
			return 0;
		}
		Boolean open = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "значение"), ctx.getSource());
		if (open == null) {
			return 0;
		}
		String reason = StringArgumentType.getString(ctx, "причина");
		DataManager data = DataManager.get();
		data.settings().serverLockMode = open ? "none" : "stop";
		data.settings().serverLocked = !open;
		data.settings().serverLockReason = reason;
		data.saveAll();
		String admin = CommandUtil.adminName(ctx.getSource());
		if (open) {
			Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + admin + " открыл сервер: " + reason));
			AdminFeedback.tell(ctx.getSource(), "Сервер открыт для игроков.");
		} else {
			Chat.broadcast(ctx.getSource().getServer(), Chat.red("Администратор " + admin + " закрыл сервер: " + reason));
			AdminFeedback.tell(ctx.getSource(), "Сервер закрыт. Новые игроки будут отключены.");
			DataManager dm = DataManager.get();
			for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
				if (!dm.canJoinWhileLocked(player)) {
					player.networkHandler.disconnect(Chat.red("Сервер закрыт: " + reason));
				}
			}
		}
		AdminNotify.record(ctx.getSource(), "ATSERVER", open ? "open" : "closed", reason);
		return 1;
	}

	private static int atSetSpawn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "atsetspawn")
				|| !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		PlayerRecord record = DataManager.get().getOrCreate(target);
		record.customSpawn = captureWaypoint(admin, "custom");
		record.spawnChoice = ru.admincontrol.data.SpawnChoice.CUSTOM.id();
		DataManager.get().saveAll();
		Chat.send(target, Chat.blue("Администратор " + admin.getGameProfile().getName()
				+ " изменил вашу точку появления"));
		AdminFeedback.tell(ctx.getSource(), "Точка появления игрока " + target.getGameProfile().getName() + " установлена.");
		AdminNotify.record(ctx.getSource(), "ATSETSPAWN", target.getGameProfile().getName(), record.customSpawn.coordsLabel());
		return 1;
	}

	private static int atEat(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireAdmin(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		int food = IntegerArgumentType.getInteger(ctx, "сытость");
		target.getHungerManager().setFoodLevel(food);
		Chat.send(target, Chat.blue("Администратор " + CommandUtil.adminName(ctx.getSource())
				+ " изменил вашу сытость: " + food + "/20"));
		AdminFeedback.tell(ctx.getSource(), "Сытость игрока " + target.getGameProfile().getName() + ": " + food + "/20.");
		AdminNotify.record(ctx.getSource(), "ATEAT", target.getGameProfile().getName(), String.valueOf(food));
		return 1;
	}

	private static ru.admincontrol.data.PlayerWaypoint captureWaypoint(ServerPlayerEntity player, String name) {
		ru.admincontrol.data.PlayerWaypoint point = new ru.admincontrol.data.PlayerWaypoint();
		point.name = name;
		point.x = player.getX();
		point.y = player.getY();
		point.z = player.getZ();
		point.yaw = player.getYaw();
		point.pitch = player.getPitch();
		point.dimension = player.getWorld().getRegistryKey().getValue().toString();
		return point;
	}

	private static int addNps(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "addnpc") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		String error = ru.admincontrol.util.NpcService.spawn(CommandUtil.self(ctx), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "NPC «" + nick + "» создан перед вами. Управление: /control " + nick
				+ "  ·  к себе: /npchere " + nick + "  ·  удалить: /delnpc " + nick);
		return 1;
	}

	private static int listNps(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "npclist")) {
			return 0;
		}
		var names = ru.admincontrol.util.NpcService.names(ctx.getSource().getServer());
		if (names.isEmpty()) {
			AdminFeedback.tell(ctx.getSource(), "NPC нет. Создать: /addnpc <ник>");
			return 1;
		}
		AdminFeedback.tell(ctx.getSource(), "NPC (" + names.size() + "): " + String.join(", ", names)
				+ ".  /control <ник>  ·  /npchere <ник>  ·  /delnpc <ник>");
		return 1;
	}

	private static int conListNps(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "connpclist")) {
			return 0;
		}
		return ru.admincontrol.util.ConDump.nps(ctx.getSource());
	}

	private static int npcHere(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "npchere") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		String error = ru.admincontrol.util.NpcService.bringHere(CommandUtil.self(ctx), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "NPC «" + nick + "» перемещён к вам.");
		return 1;
	}

	private static int delNps(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "delnpc")) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		String error = ru.admincontrol.util.NpcService.remove(ctx.getSource().getServer(), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "NPC «" + nick + "» удалён.");
		return 1;
	}

	private static int npcAct(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "npc") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		String action = StringArgumentType.getString(ctx, "действие");
		String error = ru.admincontrol.util.NpcService.act(CommandUtil.self(ctx), nick, action);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		if (action != null && action.trim().startsWith("/")) {
			AdminFeedback.tell(ctx.getSource(), "Команда выполнена от имени NPC «" + nick + "».");
		}
		return 1;
	}

	private static int controlNps(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "control") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		String error = ru.admincontrol.util.NpcService.startControl(CommandUtil.self(ctx), nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "Вы играете за NPC «" + nick + "»: его баланс, HP, опыт и чат. Выход: /uncontrol");
		return 1;
	}

	private static int uncontrolNps(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "uncontrol") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity self = ru.admincontrol.util.NpcService.sessionAdmin(CommandUtil.self(ctx));
		if (!ru.admincontrol.util.NpcService.stopControl(self)) {
			ctx.getSource().sendError(Chat.red("Вы ни кем не управляете."));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "Управление NPC прекращено.");
		return 1;
	}

	private static int spectate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireAdmin(ctx.getSource()) || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(ctx.getSource(), nick);
		if (target == null) {
			return 0;
		}
		ServerPlayerEntity self = CommandUtil.self(ctx);
		if (ru.admincontrol.util.SpectatorGuard.blockSelfSp(self, target)) {
			return 0;
		}
		ru.admincontrol.util.SpectateService.start(self, target);
		AdminFeedback.tell(ctx.getSource(), "Слежка за " + target.getGameProfile().getName());
		return 1;
	}

	private static int spectateOff(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		if (!ru.admincontrol.util.SpectateService.stop(CommandUtil.self(ctx))) {
			ctx.getSource().sendError(Chat.red("Вы не в слежке."));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "Слежка выключена.");
		return 1;
	}

	private static int toggleAgm(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "agm")) {
			return 0;
		}
		ServerPlayerEntity target;
		try {
			String nick = StringArgumentType.getString(ctx, "ник");
			if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
				return 0;
			}
			target = CommandUtil.findOnline(ctx.getSource().getServer(), nick).orElse(null);
			if (target == null) {
				PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
				if (record == null) {
					return 0;
				}
				if (record.agmBanned) {
					ctx.getSource().sendError(Chat.red("AGM заблокирован для " + record.nickname + "."));
					return 0;
				}
				record.agmEnabled = !record.agmEnabled;
				record.agmRestore = false;
				DataManager.get().saveAll();
				AdminFeedback.tell(ctx.getSource(), record.agmEnabled
						? "AGM включён для " + record.nickname + "."
						: "AGM выключен для " + record.nickname + ".");
				return 1;
			}
		} catch (IllegalArgumentException ignored) {
			if (!CommandUtil.requirePlayer(ctx.getSource())) {
				return 0;
			}
			target = CommandUtil.self(ctx);
		}
		if (DataManager.get().getOrCreate(target).agmBanned) {
			ctx.getSource().sendError(Chat.red("Команда /agm заблокирована для " + target.getGameProfile().getName() + "."));
			return 0;
		}
		boolean self = ctx.getSource().getEntity() == target;
		if (ru.admincontrol.util.EventService.busy(target)) {
			ctx.getSource().sendError(Chat.red(self ? "Во время МП /agm недоступен."
					: target.getGameProfile().getName() + " сейчас на МП — /agm недоступен."));
			return 0;
		}
		if (ru.admincontrol.util.TrainingService.active(target)) {
			ctx.getSource().sendError(Chat.red(self ? "Во время тренировки /agm недоступен."
					: target.getGameProfile().getName() + " сейчас на тренировке — /agm недоступен."));
			return 0;
		}
		ru.admincontrol.util.GodModeService.toggle(target);
		boolean on = DataManager.get().getOrCreate(target).agmEnabled;
		AdminFeedback.tell(ctx.getSource(), on
				? "AGM включён для " + target.getGameProfile().getName() + "."
				: "AGM выключен для " + target.getGameProfile().getName() + ".");
		return 1;
	}

	private static int openEvent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "event") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.EventFactory());
		return 1;
	}

	private static int openAllEvent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "allevent") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), ru.admincontrol.screen.EventFactory.playingHost());
		return 1;
	}

	private static int openTestEvent(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "tevent") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.EventFactory(true));
		return 1;
	}

	private static int clanWarDecide(CommandContext<ServerCommandSource> ctx, boolean ok, String id)
			throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		String err = ok ? ru.admincontrol.util.ClanWarService.accept(admin, id)
				: ru.admincontrol.util.ClanWarService.deny(admin, id);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), ok ? "CWAROK" : "CWARDENY", id == null || id.isBlank() ? "-" : id, "");
		ctx.getSource().sendFeedback(() -> Chat.green(ok ? "Битва кланов разрешена." : "Заявка отклонена."), false);
		return 1;
	}

	private static int clanWarStop(CommandContext<ServerCommandSource> ctx) {
		String err = ru.admincontrol.util.ClanWarService.stop(ctx.getSource().getServer(), "админ");
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "CWARSTOP", "-", "остановка");
		ctx.getSource().sendFeedback(() -> Chat.green("Битва кланов остановлена."), false);
		return 1;
	}

	private static int eventKick(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity admin = CommandUtil.self(ctx);
		String nick = StringArgumentType.getString(ctx, "ник");
		String err = ru.admincontrol.util.EventService.kick(admin, nick);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "EKICK", nick, "кик с МП");
		ctx.getSource().sendFeedback(() -> Chat.green("Игрок " + nick + " исключён с мероприятия."), false);
		return 1;
	}

	private static int toggleCommand(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "acommand")) {
			return 0;
		}
		String command = StringArgumentType.getString(ctx, "команда").toLowerCase(Locale.ROOT);
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		final String commandName = command;
		if ("acommand".equals(commandName)) {
			ctx.getSource().sendError(Chat.red("Нельзя отключить /acommand."));
			return 0;
		}
		Boolean enable = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "режим").equalsIgnoreCase("on")
				? "true" : (StringArgumentType.getString(ctx, "режим").equalsIgnoreCase("off") ? "false"
				: StringArgumentType.getString(ctx, "режим")), ctx.getSource());
		if (enable == null) {
			return 0;
		}
		boolean all = false;
		try {
			all = StringArgumentType.getString(ctx, "область").equalsIgnoreCase("all");
		} catch (IllegalArgumentException ignored) {
		}
		var list = all ? DataManager.get().settings().disabledEveryoneCommands
				: DataManager.get().settings().disabledPlayerCommands;
		if (enable) {
			list.removeIf(v -> v.equalsIgnoreCase(commandName));
		} else if (list.stream().noneMatch(v -> v.equalsIgnoreCase(commandName))) {
			list.add(commandName);
		}
		DataManager.get().saveAll();
		AdminFeedback.tell(ctx.getSource(), "/" + commandName + (enable ? " включена" : " выключена")
				+ (all ? " для всех" : " для игроков") + ".");
		return 1;
	}

	private static int listBlockedCommands(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "chcommands")) {
			return 0;
		}
		var s = DataManager.get().settings();
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("Игроки: " + (s.disabledPlayerCommands == null || s.disabledPlayerCommands.isEmpty()
				? "нет" : String.join(", ", s.disabledPlayerCommands)));
		lines.add("Все: " + (s.disabledEveryoneCommands == null || s.disabledEveryoneCommands.isEmpty()
				? "нет" : String.join(", ", s.disabledEveryoneCommands)));
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, new ru.admincontrol.screen.ChCommandsFactory());
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("=== Выключенные команды ==="), false);
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int allKick(CommandContext<ServerCommandSource> ctx) {
		String reason = StringArgumentType.getString(ctx, "причина");
		String admin = CommandUtil.adminName(ctx.getSource());
		int kicked = 0;
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			if (player == ctx.getSource().getEntity()) {
				continue;
			}
			player.networkHandler.disconnect(ru.admincontrol.util.PunishmentNotice.text(
					ru.admincontrol.util.PunishmentNotice.Kind.KICK,
					player.getGameProfile().getName(),
					ru.admincontrol.util.PunishmentNotice.displayAdmin(ctx.getSource()),
					reason, ru.admincontrol.util.PunishmentNotice.kickDuration(), System.currentTimeMillis()));
			kicked++;
		}
		AdminFeedback.tell(ctx.getSource(), "Отключено игроков: " + kicked + ".");
		AdminNotify.record(ctx.getSource(), "ALL_KICK", "-", reason);
		return kicked;
	}

	private static int unAgm(CommandContext<ServerCommandSource> ctx) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null || !CommandUtil.requireCanAffect(ctx.getSource(), record.nickname)) {
			return 0;
		}
		record.agmEnabled = false;
		record.agmRestore = false;
		DataManager.get().saveAll();
		AdminFeedback.tell(ctx.getSource(), "AGM снят с " + record.nickname);
		return 1;
	}

	private static int banAgm(CommandContext<ServerCommandSource> ctx) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null || !CommandUtil.requireCanAffect(ctx.getSource(), record.nickname)) {
			return 0;
		}
		ru.admincontrol.util.GodModeService.setBanned(record, true);
		AdminFeedback.tell(ctx.getSource(), "AGM заблокирован для " + record.nickname);
		return 1;
	}

	private static int unbanAgm(CommandContext<ServerCommandSource> ctx) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		ru.admincontrol.util.GodModeService.setBanned(record, false);
		AdminFeedback.tell(ctx.getSource(), "Блокировка AGM снята с " + record.nickname);
		return 1;
	}

	private static int stopServer(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		data.settings().serverLockMode = "stop";
		data.settings().serverLocked = true;
		data.settings().serverLockReason = StringArgumentType.getString(ctx, "причина");
		data.saveAll();
		for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
			if (!data.canJoinWhileLocked(player) && player != ctx.getSource().getEntity()) {
				player.networkHandler.disconnect(Chat.red("Сервер закрыт: " + data.serverLockReason()));
			}
		}
		AdminFeedback.tell(ctx.getSource(), "Сервер закрыт для игроков и админов 1–2.");
		return 1;
	}

	private static int startServer(CommandContext<ServerCommandSource> ctx) {
		DataManager data = DataManager.get();
		data.settings().serverLockMode = "none";
		data.settings().serverLocked = false;
		data.saveAll();
		Chat.broadcast(ctx.getSource().getServer(), Chat.red("Сервер снова открыт."));
		return 1;
	}

	private static int addWight(CommandContext<ServerCommandSource> ctx) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		DataManager.get().addWight(record);
		AdminFeedback.tell(ctx.getSource(), record.nickname + " добавлен в whitelist закрытого сервера.");
		return 1;
	}

	private static int delWight(CommandContext<ServerCommandSource> ctx) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		DataManager.get().removeWight(record);
		AdminFeedback.tell(ctx.getSource(), record.nickname + " удалён из whitelist.");
		return 1;
	}

	private static int checkWight(CommandContext<ServerCommandSource> ctx) {
		var s = DataManager.get().settings();
		ctx.getSource().sendFeedback(() -> Chat.gold("Whitelist: " + (s.wightNicks.isEmpty() ? "пусто" : String.join(", ", s.wightNicks))), false);
		ctx.getSource().sendFeedback(() -> Chat.gray("По умолчанию также заходят админы 3+."), false);
		return 1;
	}

	private static int showKoto(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireRank(ctx.getSource(), AdminRanks.MOD, chat ? "conkoto" : "koto")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null || !LogAccess.requireCanSee(ctx.getSource(), record)) {
			return 0;
		}
		var lines = ru.admincontrol.util.AntiCheatService.kotoLines(record);
		String title = "Anti-Cheat /koto " + record.nickname;
		if (lines.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("У игрока " + record.nickname
					+ " нет записей античита (автокликер / x-ray)."), false);
			return 1;
		}
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.LogFactory(title, lines, true, true, "").tracked());
			return 1;
		}
		ru.admincontrol.util.AdminStats.logToChat(ctx.getSource(), title);
		ctx.getSource().sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ru.admincontrol.util.ChatDump.logLines(ctx.getSource(), lines);
	}

	private static int showLog(CommandContext<ServerCommandSource> ctx, boolean admin, boolean chat, String query) throws CommandSyntaxException {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), admin ? "alog" : "plog")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null || !LogAccess.requireCanSee(ctx.getSource(), record)) {
			return 0;
		}
		String nick = record.nickname;
		var lines = admin ? ru.admincontrol.util.ActivityLog.readAdmin(nick) : ru.admincontrol.util.ActivityLog.readPlayer(nick);
		String q = query == null ? "" : query.trim();
		String title = (admin ? "Админ-логи " : "Логи игрока ") + nick;
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.LogFactory(title, lines, true, true, q).tracked());
			return 1;
		}
		ru.admincontrol.util.AdminStats.logToChat(ctx.getSource(), title);
		var shown = ru.admincontrol.util.ActivityLog.filter(lines, q);
		ctx.getSource().sendFeedback(() -> Chat.gold("=== " + title
				+ (q.isBlank() ? "" : ("  ·  поиск: " + q)) + " ==="), false);
		return ru.admincontrol.util.ChatDump.logLines(ctx.getSource(), shown);
	}

	private static int clearLog(CommandContext<ServerCommandSource> ctx, boolean adminLogs) {
		int min = adminLogs ? AdminRanks.GA : AdminRanks.TECH;
		if (!CommandUtil.requireRank(ctx.getSource(), min, adminLogs ? "aclearlog" : "clearlog")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (nick.equalsIgnoreCase("all")) {
			int n = adminLogs ? ru.admincontrol.util.ActivityLog.clearAllAdmins()
					: ru.admincontrol.util.ActivityLog.clearAllPlayers();
			AdminFeedback.tell(ctx.getSource(), "Удалено файлов логов: " + n + ".");
			AdminNotify.record(ctx.getSource(), adminLogs ? "ACLEARLOG" : "CLEARLOG", "all", "очистка");
			return 1;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		boolean ok = adminLogs ? ru.admincontrol.util.ActivityLog.clearAdmin(record.nickname)
				: ru.admincontrol.util.ActivityLog.clearPlayer(record.nickname);
		AdminFeedback.tell(ctx.getSource(), ok
				? ("Логи " + record.nickname + " удалены.")
				: ("Файла логов у " + record.nickname + " не было."));
		AdminNotify.record(ctx.getSource(), adminLogs ? "ACLEARLOG" : "CLEARLOG", record.nickname, "очистка");
		return 1;
	}

	private static int openCheck(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "setcheck") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.CheckFeedFactory());
		return 1;
	}

	private static int openServerPanel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "aserver") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.ServerPanelFactory(false));
		return 1;
	}

	private static int openARanks(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "aranks") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.RankPermsFactory(-1));
		return 1;
	}

	private static int openARoles(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "aroles") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.RoleEditorFactory(""));
		return 1;
	}

	private static int nickFilterList(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("Запрещённые символы в никах: "
				+ ru.admincontrol.util.NickFilter.listLabel(ru.admincontrol.util.NickFilter.forbidden())), false);
		ctx.getSource().sendFeedback(() -> Chat.yellow("Белый список: "
				+ (ru.admincontrol.util.NickFilter.whitelist().isEmpty() ? "пусто"
				: String.join(", ", ru.admincontrol.util.NickFilter.whitelist()))), false);
		ctx.getSource().sendFeedback(() -> Chat.gray("/nickfilter add|remove <символ> · reset · allow|deny <ник> · whitelist"), false);
		return 1;
	}

	private static int nickFilterAdd(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "символ");
		String error = ru.admincontrol.util.NickFilter.addForbidden(raw);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "В запрещённые символы добавлено «"
				+ ru.admincontrol.util.NickFilter.display(raw) + "».");
		return 1;
	}

	private static int nickFilterRemove(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "символ");
		String error = ru.admincontrol.util.NickFilter.removeForbidden(raw);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		int lifted = ru.admincontrol.util.NickFilter.liftInvalidated();
		AdminFeedback.tell(ctx.getSource(), "«" + ru.admincontrol.util.NickFilter.display(raw)
				+ "» убран из запрещённых."
				+ (lifted > 0 ? " Снято авто-банов фильтра: " + lifted + "." : ""));
		return 1;
	}

	private static int nickFilterReset(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		ru.admincontrol.util.NickFilter.resetForbidden();
		int lifted = ru.admincontrol.util.NickFilter.liftInvalidated();
		AdminFeedback.tell(ctx.getSource(), "Список запрещённых символов сброшен к стандартному."
				+ (lifted > 0 ? " Снято авто-банов фильтра: " + lifted + "." : ""));
		return 1;
	}

	private static int nickFilterAllow(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		String error = ru.admincontrol.util.NickFilter.addWhitelist(nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		int lifted = ru.admincontrol.util.NickFilter.liftInvalidated();
		AdminFeedback.tell(ctx.getSource(), "«" + nick + "» в белом списке фильтра ников."
				+ (lifted > 0 ? " Бан фильтра снят." : ""));
		return 1;
	}

	private static int nickFilterDeny(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		String error = ru.admincontrol.util.NickFilter.removeWhitelist(nick);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "«" + nick + "» убран из белого списка. При следующем входе фильтр снова сработает.");
		return 1;
	}

	private static int nickFilterWhitelist(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "nickfilter")) {
			return 0;
		}
		var list = ru.admincontrol.util.NickFilter.whitelist();
		ctx.getSource().sendFeedback(() -> Chat.gold("Белый список ников: "
				+ (list.isEmpty() ? "пусто" : String.join(", ", list))), false);
		return 1;
	}

	private static int showCoinHistory(CommandContext<ServerCommandSource> ctx, boolean chat) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "acoins")) {
			return 0;
		}
		if (!chat && !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null || !LogAccess.requireCanSee(ctx.getSource(), record)) {
			return 0;
		}
		String nick = record.nickname;
		var lines = ru.admincontrol.util.CoinLedger.history(nick);
		String title = "История World coin " + nick;
		if (!chat && ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
			ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.LogFactory(title, lines).tracked());
			return 1;
		}
		ru.admincontrol.util.AdminStats.logToChat(ctx.getSource(), title);
		ctx.getSource().sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ru.admincontrol.util.ChatDump.logLines(ctx.getSource(), lines);
	}

	private static int adminBalance(CommandContext<ServerCommandSource> ctx, boolean chat) {
		if (!CommandUtil.requireCommand(ctx.getSource(), chat ? "conabalance" : "abalance")) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		if (!chat) {
			AdminFeedback.tell(ctx.getSource(), record.nickname + ": "
					+ ru.admincontrol.util.Money.wc(ru.admincontrol.util.Economy.balanceOf(record)));
			return 1;
		}
		return ru.admincontrol.util.ConDump.balance(ctx.getSource(), record);
	}

	private static int addCoin(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "addcoin")) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		int amount = IntegerArgumentType.getInteger(ctx, "кол-во");
		ru.admincontrol.util.Economy.addCoins(record, amount, "начисление администратором");
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			Chat.send(online, Chat.green("Вам начислено " + ru.admincontrol.util.Money.wc(amount) + "."));
		}
		AdminFeedback.tell(ctx.getSource(), "Начислено " + ru.admincontrol.util.Money.wc(amount) + " игроку " + record.nickname + ".");
		AdminNotify.record(ctx.getSource(), "ADDCOIN", record.nickname, String.valueOf(amount));
		return 1;
	}

	private static int unCoin(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "uncoin")) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		int amount = IntegerArgumentType.getInteger(ctx, "кол-во");
		ru.admincontrol.util.Economy.removeCoins(record, amount, "снятие администратором");
		AdminFeedback.tell(ctx.getSource(), "Снято World coin у " + record.nickname + ". Баланс: "
				+ ru.admincontrol.util.Money.wc(ru.admincontrol.util.Economy.balanceOf(record)) + ".");
		AdminNotify.record(ctx.getSource(), "UNCOIN", record.nickname, String.valueOf(amount));
		return 1;
	}

	private static int setPower(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "setpower")) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		double power = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "сила");
		double from = record.power();
		record.power = Math.round(power * 10.0) / 10.0;
		DataManager.get().saveAll();
		ru.admincontrol.util.ProgressLog.power(record, from, record.power(), ru.admincontrol.util.ProgressLog.ADMIN);
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			ru.admincontrol.util.BalanceBoard.refresh(online, true);
			Chat.send(online, Chat.yellow("Ваша сила: " + String.format(java.util.Locale.US, "%.1f", record.power())));
		}
		AdminFeedback.tell(ctx.getSource(), "Сила " + record.nickname + " = "
				+ String.format(java.util.Locale.US, "%.1f", record.power()) + ".");
		AdminNotify.record(ctx.getSource(), "SETPOWER", record.nickname, String.valueOf(record.power()));
		return 1;
	}

	private static int unprivate(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "unprivate")
				|| !CommandUtil.requireCanAffect(ctx.getSource(), CommandUtil.nick(ctx))) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		if (!record.homePrivate) {
			AdminFeedback.tell(ctx.getSource(), "У " + record.nickname + " приват дома уже выключен.");
			return 1;
		}
		record.homePrivate = false;
		DataManager.get().saveAll();
		ru.admincontrol.util.HomeProtection.refresh();
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			Chat.send(online, Chat.yellow("Администратор снял приват вашего дома."));
		}
		AdminFeedback.tell(ctx.getSource(), "Приват дома снят у " + record.nickname + ".");
		AdminNotify.record(ctx.getSource(), "UNPRIVATE", record.nickname, "");
		return 1;
	}

	private static int setPlayerCasino(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireGaOrTech(ctx.getSource(), "acasino")
				|| !CommandUtil.requireCanAffect(ctx.getSource(), CommandUtil.nick(ctx))) {
			return 0;
		}
		Boolean on = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "значение"), ctx.getSource());
		if (on == null) {
			return 0;
		}
		return setCasinoAllowed(ctx, CommandUtil.nick(ctx), on);
	}

	private static int setCasinoAllowed(CommandContext<ServerCommandSource> ctx, String nick, boolean allow) {
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		record.casinoBanned = !allow;
		DataManager.get().saveAll();
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			Chat.send(online, allow
					? Chat.green("Вам снова разрешено играть в казино.")
					: Chat.red("Вам запрещено играть в казино."));
		}
		AdminFeedback.tell(ctx.getSource(), allow
				? "Казино включено для " + record.nickname + "."
				: "Казино выключено для " + record.nickname + ".");
		AdminNotify.record(ctx.getSource(), allow ? "CASINOALLOW" : "CASINOBAN", record.nickname, "");
		return 1;
	}

	private static int setLuck(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "setluck")
				|| !CommandUtil.requireCanAffect(ctx.getSource(), CommandUtil.nick(ctx))) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), CommandUtil.nick(ctx));
		if (record == null) {
			return 0;
		}
		double luck = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "значение");
		double from = record.luck();
		record.luck = Math.round(Math.max(0.0, luck) * 10.0) / 10.0;
		DataManager.get().saveAll();
		ru.admincontrol.util.ProgressLog.luck(record, from, record.luck(), ru.admincontrol.util.ProgressLog.ADMIN);
		ServerPlayerEntity online = CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).orElse(null);
		if (online != null) {
			ru.admincontrol.util.BalanceBoard.refresh(online, true);
			Chat.send(online, Chat.yellow("Ваша удача: " + String.format(java.util.Locale.US, "%.1f", record.luck())));
		}
		AdminFeedback.tell(ctx.getSource(), "Удача " + record.nickname + " = "
				+ String.format(java.util.Locale.US, "%.1f", record.luck()) + ".");
		AdminNotify.record(ctx.getSource(), "SETLUCK", record.nickname, String.valueOf(record.luck()));
		return 1;
	}

	private static int setNick(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "setnick")) {
			return 0;
		}
		String fromNick = StringArgumentType.getString(ctx, "ник1");
		String toNick = StringArgumentType.getString(ctx, "ник2");
		String reason = StringArgumentType.getString(ctx, "причина");
		if (fromNick.equalsIgnoreCase(toNick)) {
			ctx.getSource().sendError(Chat.red("Ники должны быть разными."));
			return 0;
		}
		if (!CommandUtil.requireCanAffect(ctx.getSource(), fromNick)
				|| !CommandUtil.requireCanAffect(ctx.getSource(), toNick)) {
			return 0;
		}
		PlayerRecord from = CommandUtil.requireRecord(ctx.getSource(), fromNick);
		PlayerRecord to = CommandUtil.requireRecord(ctx.getSource(), toNick);
		if (from == null || to == null) {
			return 0;
		}
		DataManager.get().rememberPlayerNick(from.nickname);
		DataManager.get().rememberPlayerNick(to.nickname);
		var server = ctx.getSource().getServer();
		ServerPlayerEntity fromOnline = CommandUtil.findOnline(server, from.nickname).orElse(null);
		ServerPlayerEntity toOnline = CommandUtil.findOnline(server, to.nickname).orElse(null);
		if (fromOnline != null) {
			InventoryUtil.snapshot(fromOnline, from);
		}
		if (toOnline != null) {
			InventoryUtil.snapshot(toOnline, to);
		}
		String inv = from.inventoryNbt == null ? "" : from.inventoryNbt;
		int level = fromOnline != null ? fromOnline.experienceLevel : from.lastKnownLevel;
		float progress = fromOnline != null ? fromOnline.experienceProgress : 0f;
		int totalXp = fromOnline != null ? fromOnline.totalExperience : 0;
		int coins = Math.max(0, from.coins) + Math.max(0, from.pendingCoins);
		double power = from.power;
		double luck = from.luck();
		if (toOnline != null) {
			InventoryUtil.restore(toOnline, inv);
			InventoryUtil.applyExperience(toOnline, level, progress, totalXp);
			if (fromOnline != null) {
				InventoryUtil.copyEnder(fromOnline, toOnline);
			}
		} else {
			to.inventoryNbt = inv;
			to.lastKnownLevel = level;
		}
		double toPowerFrom = to.power();
		double toLuckFrom = to.luck();
		double fromPowerFrom = from.power;
		double fromLuckFrom = from.luck();
		to.coins = Math.max(0, to.coins) + coins;
		to.pendingCoins = 0;
		to.power = power;
		to.luck = luck;
		from.power = 0;
		from.luck = 0;
		ru.admincontrol.util.ProgressLog.power(to, toPowerFrom, to.power, ru.admincontrol.util.ProgressLog.ADMIN);
		ru.admincontrol.util.ProgressLog.luck(to, toLuckFrom, to.luck(), ru.admincontrol.util.ProgressLog.ADMIN);
		ru.admincontrol.util.ProgressLog.power(from, fromPowerFrom, 0, ru.admincontrol.util.ProgressLog.ADMIN);
		ru.admincontrol.util.ProgressLog.luck(from, fromLuckFrom, 0, ru.admincontrol.util.ProgressLog.ADMIN);
		if (fromOnline != null) {
			fromOnline.getInventory().clear();
			fromOnline.getInventory().markDirty();
			if (fromOnline.playerScreenHandler != null) {
				fromOnline.playerScreenHandler.sendContentUpdates();
			}
			InventoryUtil.applyExperience(fromOnline, 0, 0f, 0);
		}
		from.inventoryNbt = "";
		from.lastKnownLevel = 0;
		from.coins = 0;
		from.pendingCoins = 0;
		from.power = 0;
		from.luck = 0;
		if (coins > 0) {
			ru.admincontrol.util.CoinLedger.debit(from, coins, "setnick → " + to.nickname + ": " + reason);
			ru.admincontrol.util.CoinLedger.credit(to, coins, "setnick от " + from.nickname + ": " + reason);
		}
		DataManager.get().saveAll();
		if (fromOnline != null) {
			ru.admincontrol.util.BalanceBoard.refresh(fromOnline, true);
			Chat.send(fromOnline, Chat.red("Администратор передал ваши вещи, опыт, WC, силу и удачу игроку "
					+ to.nickname + ". Причина: " + reason));
		}
		if (toOnline != null) {
			ru.admincontrol.util.BalanceBoard.refresh(toOnline, true);
			Chat.send(toOnline, Chat.green("Вам переданы инвентарь, опыт, WC, сила и удача игрока "
					+ from.nickname + ". Причина: " + reason));
		}
		AdminFeedback.tell(ctx.getSource(), "Передано с " + from.nickname + " на " + to.nickname
				+ ": инвентарь, опыт " + level + ", " + ru.admincontrol.util.Money.wc(coins) + ", сила "
				+ String.format(java.util.Locale.US, "%.1f", to.power()) + ", удача "
				+ String.format(java.util.Locale.US, "%.1f", to.luck()) + ".");
		AdminNotify.record(ctx.getSource(), "SETNICK", from.nickname + " → " + to.nickname, reason);
		return 1;
	}

	private static int resetClan(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "resetclan")) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "клан");
		String err = ru.admincontrol.util.ClanService.resetClan(name);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "RESETCLAN", name, "обнуление клана");
		AdminFeedback.tell(ctx.getSource(), "Клан «" + name + "» обнулён.");
		if (ctx.getSource().getServer() != null) {
			ru.admincontrol.util.ClanNametags.refreshAll(ctx.getSource().getServer());
		}
		return 1;
	}

	private static int resetAccount(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "areset")) {
			return 0;
		}
		String nick = CommandUtil.nick(ctx);
		String reason = StringArgumentType.getString(ctx, "причина");
		if (!CommandUtil.requireCanAffect(ctx.getSource(), nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		if (DataManager.get().lockedDeveloper(record)) {
			AdminNotify.denied(ctx.getSource(), "нельзя обнулить Разработчика");
			return 0;
		}
		String error = ru.admincontrol.util.AccountReset.apply(ctx.getSource().getServer(), record, reason);
		if (error != null) {
			ctx.getSource().sendError(Chat.red(error));
			return 0;
		}
		AdminFeedback.tell(ctx.getSource(), "Аккаунт " + record.nickname + " обнулён. Наказания сохранены. Причина: " + reason);
		AdminNotify.record(ctx.getSource(), "ARESET", record.nickname, reason);
		return 1;
	}

	private static int openCasino(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "setcasino") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.CasinoFactory());
		return 1;
	}

	private static int openSetPrice(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ScreenNav.openRoot(CommandUtil.self(ctx), new ru.admincontrol.screen.SetPriceFactory());
		return 1;
	}

	private static int createRules(CommandContext<ServerCommandSource> ctx) {
		ru.admincontrol.util.RulesService.createDefault();
		AdminFeedback.tell(ctx.getSource(), "Создан файл rules.txt");
		return 1;
	}

	private static int keyRules(CommandContext<ServerCommandSource> ctx) {
		ru.admincontrol.util.RulesService.encrypt();
		AdminFeedback.tell(ctx.getSource(), "rules.txt зашифрован.");
		return 1;
	}

	private static int unkeyRules(CommandContext<ServerCommandSource> ctx) {
		ru.admincontrol.util.RulesService.decrypt();
		AdminFeedback.tell(ctx.getSource(), "rules.txt расшифрован.");
		return 1;
	}

	private static GameMode parseGameMode(String raw) {
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "creative", "c", "1" -> GameMode.CREATIVE;
			case "survival", "s", "0" -> GameMode.SURVIVAL;
			case "adventure", "a", "2" -> GameMode.ADVENTURE;
			case "spectator", "sp", "3" -> GameMode.SPECTATOR;
			default -> null;
		};
	}

	private static Item resolveItem(String raw) {
		Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return null;
		}
		return Registries.ITEM.get(id);
	}

	private static int startBox(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "box") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = CommandUtil.self(ctx);
		String err = ru.admincontrol.util.BoxEventService.start(ctx.getSource().getServer(), player);
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "BOX", player.getGameProfile().getName(), "старт");
		AdminFeedback.tell(ctx.getSource(), "Бокс объявлен. /unbox — отмена до появления (вы или тех. админ+).");
		return 1;
	}

	private static int stopBox(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!CommandUtil.requireCommand(ctx.getSource(), "unbox") || !CommandUtil.requirePlayer(ctx.getSource())) {
			return 0;
		}
		String err = ru.admincontrol.util.BoxEventService.cancel(CommandUtil.self(ctx));
		if (err != null) {
			ctx.getSource().sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(ctx.getSource(), "UNBOX", CommandUtil.adminName(ctx.getSource()), "отмена");
		return 1;
	}

	private static int plCheck(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "plcheck")) {
			return 0;
		}
		java.util.List<String> lines = ru.admincontrol.util.ActivityCheck.players(ctx.getSource().getServer());
		if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, ru.admincontrol.screen.LogFactory.ofText("МП и тренировки", lines));
			return 1;
		}
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int sCheck(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "scheck")) {
			return 0;
		}
		java.util.List<String> lines = ru.admincontrol.util.ActivityCheck.status(ctx.getSource().getServer());
		if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, ru.admincontrol.screen.LogFactory.ofText("Статус активностей", lines));
			return 1;
		}
		return ru.admincontrol.util.ChatDump.lines(ctx.getSource(), lines);
	}

	private static int fullStop(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "fullstop")) {
			return 0;
		}
		java.util.List<String> lines = ru.admincontrol.util.ActivityCheck.fullStop(ctx.getSource().getServer());
		AdminNotify.record(ctx.getSource(), "FULLSTOP", "-", String.join("; ", lines));
		for (String line : lines) {
			AdminFeedback.tell(ctx.getSource(), line);
		}
		return 1;
	}

	private static int offPlayer(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "offplayer")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		ServerPlayerEntity target = CommandUtil.findOnline(ctx.getSource().getServer(), nick).orElse(null);
		if (target == null) {
			ctx.getSource().sendError(Chat.red("Игрок «" + nick + "» не в сети."));
			return 0;
		}
		boolean train = ru.admincontrol.util.TrainingService.active(target);
		boolean event = ru.admincontrol.util.EventService.member(target);
		if (!train && !event) {
			ctx.getSource().sendError(Chat.red(target.getGameProfile().getName() + " не на МП и не на тренировке."));
			return 0;
		}
		if (train) {
			ru.admincontrol.util.TrainingService.off(target);
		}
		if (event) {
			ru.admincontrol.util.EventService.forceLeave(target);
		}
		Chat.send(target, Chat.red("Вас отключили от активности для обновления сервера."));
		AdminFeedback.tell(ctx.getSource(), "Игрок " + target.getGameProfile().getName() + " снят с "
				+ (train && event ? "тренировки и МП" : train ? "тренировки" : "МП") + ".");
		AdminNotify.record(ctx.getSource(), "OFFPLAYER", target.getGameProfile().getName(), "-");
		return 1;
	}

	private static int setCommandAdmin(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "setcommandadmin")) {
			return 0;
		}
		String command = StringArgumentType.getString(ctx, "команда");
		Boolean value = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "режим"), ctx.getSource());
		if (value == null) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		PlayerRecord record = CommandUtil.requireRecord(ctx.getSource(), nick);
		if (record == null) {
			return 0;
		}
		DataManager data = DataManager.get();
		if (!data.isAdmin(record.uuidOrNull(), record.nickname)) {
			ctx.getSource().sendError(Chat.red("Игрок «" + record.nickname + "» не администратор."));
			return 0;
		}
		int exec = CommandUtil.sourceRank(ctx.getSource());
		int targetRank = data.getAdminLevel(record.uuidOrNull(), record.nickname);
		if (targetRank >= exec && exec < AdminRanks.DEV) {
			AdminNotify.denied(ctx.getSource(), "нельзя менять команды равного или старшего админа");
			return 0;
		}
		if (targetRank >= AdminRanks.DEV && exec < AdminRanks.DEV) {
			AdminNotify.denied(ctx.getSource(), "нельзя менять команды Разработчика");
			return 0;
		}
		String id = command.toLowerCase(java.util.Locale.ROOT);
		if (id.startsWith("/")) {
			id = id.substring(1);
		}
		ru.admincontrol.util.RankPerms.setAdminCommandFlag(record.nickname, id, value);
		CommandUtil.findOnline(ctx.getSource().getServer(), record.nickname).ifPresent(
				ru.admincontrol.command.CommandSync::refresh);
		final String cmd = id;
		final boolean on = value;
		ctx.getSource().sendFeedback(() -> Chat.green("Команда /" + cmd + " для " + record.nickname + ": "
				+ (on ? "включена" : "выключена") + "."), false);
		AdminNotify.record(ctx.getSource(), "SETCOMMANDADMIN", record.nickname, cmd + "=" + on);
		return 1;
	}

	private static int adminList(CommandContext<ServerCommandSource> ctx) {
		if (!CommandUtil.requireCommand(ctx.getSource(), "adminlist")) {
			return 0;
		}
		var list = DataManager.get().listAllAdmins(ctx.getSource().getServer());
		if (list.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Chat.yellow("В списке администрации никого нет."), false);
			return 1;
		}
		ctx.getSource().sendFeedback(() -> Chat.gold("Администрация (" + list.size() + "), включая оффлайн:"), false);
		for (var entry : list) {
			final var row = entry;
			ctx.getSource().sendFeedback(() -> Text.empty()
					.append(Chat.nick("  " + row.nick, row.level))
					.append(Chat.white(" — " + AdminRanks.title(row.level)
							+ " (" + AdminRanks.display(row.level) + ") "))
					.append(row.online ? Chat.green("онлайн") : Chat.gray("оффлайн")), false);
		}
		return 1;
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
