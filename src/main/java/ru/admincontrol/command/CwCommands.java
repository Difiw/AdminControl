package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.BalanceBoard;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.CwService;
import ru.admincontrol.util.EventCatalog;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.FreezeService;
import ru.admincontrol.util.ServerStopService;
import ru.admincontrol.util.SkillTreeService;

import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CwCommands {
	private static final SuggestionProvider<ServerCommandSource> ITEMS = (ctx, builder) ->
			CommandSource.suggestIdentifiers(Registries.ITEM.getIds(), builder);
	private static final SuggestionProvider<ServerCommandSource> MODS = (ctx, builder) ->
			CommandSource.suggestMatching(FabricLoader.getInstance().getAllMods().stream()
					.map(m -> m.getMetadata().getId()).toList(), builder);
	private static final SuggestionProvider<ServerCommandSource> COMMANDS = (ctx, builder) ->
			CommandSource.suggestMatching(ctx.getSource().getServer().getCommandManager().getDispatcher()
					.getRoot().getChildren().stream().map(n -> n.getName()).toList(), builder);
	private static final SuggestionProvider<ServerCommandSource> LEVELS = (ctx, builder) ->
			CommandSource.suggestMatching(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), builder);

	private static final SuggestionProvider<ServerCommandSource> BRANCHES = (ctx, builder) -> {
		List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < SkillTree.BRANCHES.size(); i++) {
			ids.add(Integer.toString(i + 1));
			ids.add(SkillTree.BRANCHES.get(i).id());
		}
		return CommandSource.suggestMatching(ids, builder);
	};
	private static final String SKILL_USAGE = "/cw skill <номер|ветка> <true|false> [all|admins|players]";
	private static final String EVENT_USAGE = "/cw event <название мероприятия> <true|false>";
	private static final SuggestionProvider<ServerCommandSource> CW_SKINS = (ctx, builder) ->
			CommandSource.suggestMatching(ru.admincontrol.util.CwNpcService.skins(), builder);
	private static final SuggestionProvider<ServerCommandSource> CW_NPC_IDS = (ctx, builder) ->
			CommandSource.suggestMatching(ru.admincontrol.util.CwNpcService.ids(ctx.getSource().getServer()), builder);

	private static final SuggestionProvider<ServerCommandSource> EVENTS = (ctx, builder) -> {
		List<String> out = new java.util.ArrayList<>();
		for (int i = 0; i < EventCatalog.count(); i++) {
			String name = EventCatalog.name(i);
			boolean blocked = EventService.blocked(i);
			out.add(name + " " + !blocked);
			out.add(name + " " + blocked);
		}
		String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (String s : out) {
			if (s.toLowerCase(Locale.ROOT).startsWith(typed)) {
				builder.suggest(s);
			}
		}
		return builder.buildFuture();
	};

	private CwCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("cw").requires(CommandUtil::canUseCw)
				.executes(ctx -> help(ctx.getSource()))
				.then(rule("item", CwService.ITEM,
						argument("предмет", IdentifierArgumentType.identifier()).suggests(ITEMS)))
				.then(rule("mod", CwService.MOD,
						argument("мод", StringArgumentType.word()).suggests(MODS)))
				.then(rule("command", CwService.COMMAND,
						argument("команда", StringArgumentType.word()).suggests(COMMANDS)))
				.then(literal("list").executes(ctx -> list(ctx.getSource())))
				.then(literal("server")
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw server stop <причина>"))
						.then(literal("stop")
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw server stop <причина>"))
								.then(argument("причина", StringArgumentType.greedyString())
										.executes(CwCommands::serverStop))))
				.then(literal("warn")
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw warn <ник> <предложение>"))
						.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw warn <ник> <предложение>"))
								.then(argument("текст", StringArgumentType.greedyString())
										.executes(CwCommands::warn))))
				.then(literal("skill")
						.executes(ctx -> skillList(ctx.getSource()))
						.then(argument("ветка", StringArgumentType.word()).suggests(BRANCHES)
								.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), SKILL_USAGE))
								.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.BOOL)
										.executes(ctx -> setSkill(ctx, "all"))
										.then(literal("all").executes(ctx -> setSkill(ctx, "all")))
										.then(literal("admins").executes(ctx -> setSkill(ctx, "admins")))
										.then(literal("players").executes(ctx -> setSkill(ctx, "players"))))))
				.then(literal("event")
						.executes(ctx -> eventList(ctx.getSource()))
						.then(argument("мероприятие", StringArgumentType.greedyString()).suggests(EVENTS)
								.executes(CwCommands::setEvent)))
				.then(toggle("board", "cwboard"))
				.then(toggle("password", "cwpassword"))
				.then(literal("donate")
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw donate <ссылка>"))
						.then(argument("ссылка", StringArgumentType.greedyString()).executes(CwCommands::setDonate)))
				);

		dispatcher.register(literal("fz").requires(s -> CommandUtil.canUse(s, "fz"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/fz <ник> [1|2|3]"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> freeze(ctx, 1))
						.then(argument("степень", IntegerArgumentType.integer(1, 3))
								.executes(ctx -> freeze(ctx, IntegerArgumentType.getInteger(ctx, "степень"))))));
		dispatcher.register(literal("unfz").requires(s -> CommandUtil.canUse(s, "unfz"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unfz <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(CwCommands::unfreeze)));

		dispatcher.register(literal("donate").executes(CwCommands::donate));
	}

	private static ArgumentBuilder<ServerCommandSource, ?> rule(String name, String kind,
			ArgumentBuilder<ServerCommandSource, ?> keyArg) {
		String usage = "/cw " + name + " <" + name + "> <true|false> [players|admins|admin <ур.|ник>|player <ник>]";
		String perm = "cw" + name;
		return literal(name)
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage))
				.then(keyArg
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage))
						.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.BOOL)
								.executes(ctx -> setRule(ctx, perm, kind, null, null))
								.then(literal("players").executes(ctx -> setRule(ctx, perm, kind, "players", "")))
								.then(literal("admins").executes(ctx -> setRule(ctx, perm, kind, "admins", "")))
								.then(literal("admin")
										.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage))
										.then(argument("кто", StringArgumentType.word()).suggests(LEVELS)
												.executes(ctx -> {
													String who = StringArgumentType.getString(ctx, "кто");
													boolean lvl = AdminRanks.parseAssignable(who) != null;
													return setRule(ctx, perm, kind, lvl ? "level" : "nick", who);
												})))
								.then(literal("player")
										.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), usage))
										.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
												.executes(ctx -> setRule(ctx, perm, kind, "nick",
														StringArgumentType.getString(ctx, "ник")))))));
	}

	private static ArgumentBuilder<ServerCommandSource, ?> toggle(String name, String perm) {
		return literal(name)
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/cw " + name + " <true|false>"))
				.then(argument("режим", StringArgumentType.word()).suggests(CommandUtil.BOOL)
						.executes(ctx -> setToggle(ctx, name, perm)));
	}

	private static int help(ServerCommandSource source) {
		if (!CommandUtil.requireCw(source)) {
			return 0;
		}
		List<String> lines = List.of(
				"/cw item <предмет> <true|false> [цель]",
				"/cw mod <мод> <true|false> [цель]",
				"/cw command <команда> <true|false> [цель]",
				"  цель: players | admins | admin <ур.> | admin <ник> | player <ник>",
				"/cw list — текущие запреты",
				"/cw server stop <причина>",
				"/cw warn <ник> <предложение>",
				"/cw skill — ветки навыков; " + SKILL_USAGE,
				"/cw board <true|false>",
				"/cw password <true|false>",
				"/cw donate <ссылка>",
				"/cw event — список МП; " + EVENT_USAGE);
		source.sendFeedback(() -> Chat.gold("=== /cw ==="), false);
		for (String line : lines) {
			source.sendFeedback(() -> Chat.gray(line), false);
		}
		return 1;
	}

	private static int npcHelp(ServerCommandSource source) {
		if (!CommandUtil.requireCw(source)) return 0;
		for (String line : List.of("/cw npc create <skin>", "/cw npc remove <id>", "/cw npc clear",
				"/cw npc list", "/cw npc reload", "/cw npc info <skin>", "/cw npc help"))
			source.sendFeedback(() -> Chat.gray(line), false);
		return 1;
	}

	private static int npcList(ServerCommandSource source) {
		if (!CommandUtil.requireCw(source)) return 0;
		var skins = ru.admincontrol.util.CwNpcService.skins();
		if (skins.isEmpty()) { source.sendError(Chat.red(ru.admincontrol.util.CwNpcService.scanError())); return 0; }
		source.sendFeedback(() -> Chat.gold("=== PNG-скины (" + skins.size() + ") ==="), false);
		for (String skin : skins) source.sendFeedback(() -> Chat.gray(skin), false);
		return skins.size();
	}

	private static int npcReload(ServerCommandSource source) {
		if (!CommandUtil.requireCw(source)) return 0;
		String error = ru.admincontrol.util.CwNpcService.reload();
		if (error != null) { source.sendError(Chat.red(error)); return 0; }
		ru.admincontrol.util.CwNpcService.syncAll(source.getServer());
		source.sendFeedback(() -> Chat.green("Скины перечитаны: " + ru.admincontrol.util.CwNpcService.skins().size()), false);
		return 1;
	}

	private static int npcCreate(ServerCommandSource source, String skin) {
		if (!CommandUtil.requireCw(source)) return 0;
		if (!(source.getEntity() instanceof ServerPlayerEntity player)) { source.sendError(Chat.red("Команду может выполнить только игрок.")); return 0; }
		String result = ru.admincontrol.util.CwNpcService.create(player, skin);
		if (result != null) { source.sendError(Chat.red(result)); return 0; }
		source.sendFeedback(() -> Chat.green("NPC создан. ID: " + ru.admincontrol.util.CwNpcService.lastCreatedId()), false);
		return 1;
	}

	private static int npcRemove(ServerCommandSource source, String id) {
		if (!CommandUtil.requireCw(source)) return 0;
		var npc = ru.admincontrol.util.NpcService.findById(source.getServer(), id);
		if (npc == null || ru.admincontrol.util.NpcStore.get(npc.getGameProfile().getName()) == null
				|| ru.admincontrol.util.NpcStore.get(npc.getGameProfile().getName()).skinId == null
				|| ru.admincontrol.util.NpcStore.get(npc.getGameProfile().getName()).skinId.isBlank()) {
			source.sendError(Chat.red("Тестовый NPC с ID «" + id + "» не найден.")); return 0;
		}
		String error = ru.admincontrol.util.NpcService.remove(source.getServer(), id);
		if (error != null) { source.sendError(Chat.red(error)); return 0; }
		source.sendFeedback(() -> Chat.green("NPC удалён: " + id), false);
		return 1;
	}

	private static int npcClear(ServerCommandSource source) {
		if (!CommandUtil.requireCw(source)) return 0;
		int count = ru.admincontrol.util.CwNpcService.clear(source.getWorld());
		source.sendFeedback(() -> Chat.green("Удалено тестовых NPC в этом мире: " + count), false);
		return count;
	}

	private static int npcInfo(ServerCommandSource source, String skin) {
		if (!CommandUtil.requireCw(source)) return 0;
		String result = ru.admincontrol.util.CwNpcService.info(skin);
		if (result.startsWith("[CW NPC]")) { source.sendError(Chat.red(result)); return 0; }
		source.sendFeedback(() -> Chat.gray(result), false);
		return 1;
	}

	private static int list(ServerCommandSource source) {
		if (!CommandUtil.requireCommand(source, "cwitem")) {
			return 0;
		}
		List<String> lines = CwService.describe();
		source.sendFeedback(() -> Chat.gold("=== Правила /cw (" + lines.size() + ") ==="), false);
		if (lines.isEmpty()) {
			source.sendFeedback(() -> Chat.gray("Запретов нет."), false);
		}
		for (String line : lines) {
			source.sendFeedback(() -> Chat.gray(line), false);
		}
		return 1;
	}

	private static int setRule(CommandContext<ServerCommandSource> ctx, String perm, String kind,
			String scope, String arg) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, perm)) {
			return 0;
		}
		Boolean allow = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "режим"), source);
		if (allow == null) {
			return 0;
		}
		String raw = switch (kind) {
			case CwService.ITEM -> IdentifierArgumentType.getIdentifier(ctx, "предмет").toString();
			case CwService.MOD -> StringArgumentType.getString(ctx, "мод");
			default -> StringArgumentType.getString(ctx, "команда");
		};
		String key = CwService.normalizeKey(kind, raw);
		if (CwService.ITEM.equals(kind) && !Registries.ITEM.containsId(new Identifier(key))) {
			source.sendError(Chat.red("Предмет «" + raw + "» не найден."));
			return 0;
		}
		if (CwService.MOD.equals(kind)) {
			if (!FabricLoader.getInstance().isModLoaded(key)) {
				source.sendError(Chat.red("Мод «" + raw + "» не загружен."));
				return 0;
			}
			if (CwService.protectedMod(key)) {
				source.sendError(Chat.red("Мод «" + key + "» служебный — его нельзя запретить."));
				return 0;
			}
		}
		if (CwService.COMMAND.equals(kind)) {
			if ("cw".equals(key)) {
				source.sendError(Chat.red("Нельзя запретить саму /cw."));
				return 0;
			}
			if (source.getServer().getCommandManager().getDispatcher().getRoot().getChild(
					key.contains(":") ? key.substring(key.indexOf(':') + 1) : key) == null) {
				source.sendError(Chat.red("Команда /" + key + " не найдена."));
				return 0;
			}
		}
		if ("nick".equals(scope)) {
			if (DataManager.get().findByNick(arg).isEmpty()
					&& CommandUtil.findOnline(source.getServer(), arg).isEmpty()) {
				source.sendError(Chat.red("Игрок «" + arg + "» не найден."));
				return 0;
			}
		}
		int rank = CommandUtil.sourceRank(source);
		if (CwService.replacedRank(kind, key, scope, arg) > rank) {
			source.sendError(Chat.red("Это правило поставил старший по рангу — изменить его может только он или выше."));
			return 0;
		}
		CwService.set(kind, key, allow, scope, arg, rank);
		String what = switch (kind) {
			case CwService.ITEM -> "Предмет " + key;
			case CwService.MOD -> "Мод " + key;
			default -> "Команда /" + key;
		};
		String msg = what + " " + (allow ? "разрешён(а)" : "запрещён(а)") + " "
				+ CwService.scopeLabel(scope == null ? "all" : scope, arg) + ".";
		AdminFeedback.tell(source, msg);
		AdminNotify.record(source, "CW " + kind.toUpperCase(Locale.ROOT), key,
				(allow ? "true " : "false ") + CwService.scopeLabel(scope == null ? "all" : scope, arg));
		return 1;
	}

	private static int setToggle(CommandContext<ServerCommandSource> ctx, String name, String perm) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, perm)) {
			return 0;
		}
		Boolean on = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "режим"), source);
		if (on == null) {
			return 0;
		}
		var s = DataManager.get().settings();
		if ("board".equals(name)) {
			s.boardTitleEnabled = on;
			DataManager.get().saveAll();
			BalanceBoard.refreshAll(source.getServer());
			AdminFeedback.tell(source, "Надпись «Common World» над табло " + (on ? "включена." : "выключена."));
		} else {
			s.passwordEnabled = on;
			DataManager.get().saveAll();
			if (!on) {
				AuthService.releaseAll(source.getServer());
			}
			AdminFeedback.tell(source, on
					? "Система паролей включена: при следующем входе игроки вводят пароль."
					: "Система паролей выключена: запроса пароля больше нет.");
		}
		AdminNotify.record(source, "CW " + name.toUpperCase(Locale.ROOT), "-", String.valueOf(on));
		return 1;
	}

	/** Ветка по номеру (1…) или id (survival, craft…). */
	private static SkillTree.Branch branch(String raw) {
		for (int i = 0; i < SkillTree.BRANCHES.size(); i++) {
			SkillTree.Branch b = SkillTree.BRANCHES.get(i);
			if (b.id().equalsIgnoreCase(raw) || Integer.toString(i + 1).equals(raw)) {
				return b;
			}
		}
		return null;
	}

	private static String branchStatus(SkillTree.Branch b) {
		boolean players = SkillTreeService.branchOff(false, b);
		boolean admins = SkillTreeService.branchOff(true, b);
		if (players && admins) {
			return "§cотключена для всех";
		}
		if (players) {
			return "§eотключена для игроков";
		}
		return admins ? "§eотключена для администрации" : "§aвключена";
	}

	private static int skillList(ServerCommandSource source) {
		if (!CommandUtil.requireCommand(source, "cwskill")) {
			return 0;
		}
		source.sendFeedback(() -> Chat.gold("=== Ветки дерева навыков ==="), false);
		for (int i = 0; i < SkillTree.BRANCHES.size(); i++) {
			SkillTree.Branch b = SkillTree.BRANCHES.get(i);
			String line = (i + 1) + ". " + b.title() + " (" + b.id() + ") — " + branchStatus(b);
			source.sendFeedback(() -> Chat.white(line), false);
		}
		source.sendFeedback(() -> Chat.gray(SKILL_USAGE + " — false отключает, true включает."), false);
		return 1;
	}

	private static int setSkill(CommandContext<ServerCommandSource> ctx, String scope) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "cwskill")) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "ветка");
		SkillTree.Branch b = branch(raw);
		if (b == null) {
			source.sendError(Chat.red("Нет ветки «" + raw + "». Список: /cw skill"));
			return 0;
		}
		Boolean on = CommandUtil.parseBoolean(StringArgumentType.getString(ctx, "режим"), source);
		if (on == null) {
			return 0;
		}
		String error = SkillTreeService.setBranch(source.getServer(), b, on, scope, CommandUtil.sourceRank(source));
		if (error != null) {
			source.sendError(Chat.red(error));
			return 0;
		}
		String who = switch (scope) {
			case "admins" -> "для администрации";
			case "players" -> "для игроков";
			default -> "для всех";
		};
		String action = (on ? "включена " : "отключена ") + who;
		AdminFeedback.tell(source, "Ветка «" + b.title() + "» " + action + ". Сейчас: " + branchStatus(b).substring(2) + ".");
		AdminNotify.record(source, "CW SKILL", b.id(), on + " " + scope);
		return 1;
	}

	private static int eventList(ServerCommandSource source) {
		if (!CommandUtil.requireCommand(source, "cwevent")) {
			return 0;
		}
		source.sendFeedback(() -> Chat.gold("=== Мероприятия ==="), false);
		for (int i = 0; i < EventCatalog.count(); i++) {
			String line = (i + 1) + ". " + EventCatalog.name(i) + (EventCatalog.isCustom(i) ? " (своё)" : "") + " — "
					+ (EventService.blocked(i) ? "§cзаблокировано" : "§aдоступно");
			source.sendFeedback(() -> Chat.white(line), false);
		}
		source.sendFeedback(() -> Chat.gray(EVENT_USAGE + " — true блокирует, false снимает блокировку."), false);
		return 1;
	}

	private static int setEvent(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "cwevent")) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "мероприятие").trim();
		int space = raw.lastIndexOf(' ');
		if (space <= 0) {
			return CommandUtil.failSyntax(source, EVENT_USAGE);
		}
		Boolean block = CommandUtil.parseBoolean(raw.substring(space + 1), source);
		if (block == null) {
			return 0;
		}
		String name = raw.substring(0, space).trim();
		int type = eventType(name);
		if (type < 0) {
			source.sendError(Chat.red("Нет мероприятия «" + name + "». Список: /cw event"));
			return 0;
		}
		String title = EventCatalog.name(type);
		if (EventService.blocked(type) == block) {
			AdminFeedback.tell(source, "Мероприятие «" + title + "» уже " + (block ? "заблокировано." : "доступно."));
			return 1;
		}
		EventService.setBlocked(type, block);
		AdminFeedback.tell(source, "Мероприятие «" + title + "» " + (block ? "заблокировано." : "разблокировано."));
		if (block && EventService.active() && EventService.currentChoice() == type) {
			source.sendFeedback(() -> Chat.gray("Идущее сейчас МП «" + title + "» не остановлено — блокировка действует на новые запуски."), false);
		}
		AdminNotify.record(source, "CW EVENT", title, String.valueOf(block));
		return 1;
	}

	private static int eventType(String name) {
		int found = EventCatalog.byName(name);
		if (found >= 0) {
			return found;
		}
		try {
			int n = Integer.parseInt(name.trim());
			if (n >= 1 && n <= EventCatalog.count()) {
				return n - 1;
			}
		} catch (NumberFormatException ignored) {
		}
		return -1;
	}

	private static int setDonate(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "cwdonate")) {
			return 0;
		}
		String url = StringArgumentType.getString(ctx, "ссылка").trim();
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			source.sendError(Chat.red("Ссылка должна начинаться с http:// или https://"));
			return 0;
		}
		DataManager.get().settings().donateUrl = url;
		DataManager.get().saveAll();
		AdminFeedback.tell(source, "Ссылка /donate сохранена: " + url);
		AdminNotify.record(source, "CW DONATE", "-", url);
		return 1;
	}

	private static int donate(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String url = DataManager.get() == null ? "" : DataManager.get().settings().donateUrl;
		if (url == null || url.isBlank()) {
			source.sendFeedback(() -> Chat.yellow("Ссылка на донат пока не указана."), false);
			return 1;
		}
		Text link = Text.literal("[Открыть страницу доната]").styled(s -> s.withColor(0x55FF55).withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(url))));
		source.sendFeedback(() -> Chat.gold("Поддержать сервер: ").append(link), false);
		return 1;
	}

	private static int serverStop(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "cwserverstop")) {
			return 0;
		}
		String reason = StringArgumentType.getString(ctx, "причина");
		String err = ServerStopService.start(source.getServer(), reason);
		if (err != null) {
			source.sendError(Chat.red(err));
			return 0;
		}
		AdminNotify.record(source, "SERVER STOP", "-", reason);
		AdminFeedback.tell(source, "Остановка сервера через " + ServerStopService.COUNTDOWN + " секунд.");
		return 1;
	}

	private static int warn(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "cwwarn")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(source, nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(source, nick);
		if (record == null) {
			return 0;
		}
		ModerationService.verbalWarn(source, record, StringArgumentType.getString(ctx, "текст"));
		return 1;
	}

	private static int freeze(CommandContext<ServerCommandSource> ctx, int level) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "fz")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (source.getEntity() instanceof ServerPlayerEntity self && self.getGameProfile().getName().equalsIgnoreCase(nick)) {
			source.sendError(Chat.red("Нельзя заморозить самого себя."));
			return 0;
		}
		if (!CommandUtil.requireCanAffect(source, nick)) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.requireOnline(source, nick);
		if (target == null) {
			return 0;
		}
		String admin = CommandUtil.adminName(source);
		FreezeService.freeze(target, level, admin);
		String rules = switch (level) {
			case 2 -> "нельзя двигаться, телепортироваться и вращать камеру";
			case 3 -> "заблокировано всё, кроме чата";
			default -> "нельзя двигаться и телепортироваться";
		};
		Chat.send(target, Chat.red("Администратор " + admin + " заморозил вас (степень " + level + "): " + rules + "."));
		AdminFeedback.tell(source, "Игрок " + target.getGameProfile().getName() + " заморожен, степень " + level + ".");
		AdminNotify.record(source, "FREEZE", target.getGameProfile().getName(), "степень " + level);
		return 1;
	}

	private static int unfreeze(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		if (!CommandUtil.requireCommand(source, "unfz")) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		if (!CommandUtil.requireCanAffect(source, nick)) {
			return 0;
		}
		PlayerRecord record = CommandUtil.requireRecord(source, nick);
		if (record == null) {
			return 0;
		}
		ServerPlayerEntity target = CommandUtil.findOnline(source.getServer(), record.nickname).orElse(null);
		if (!FreezeService.unfreeze(target, record)) {
			source.sendError(Chat.red("Игрок «" + record.nickname + "» не заморожен."));
			return 0;
		}
		String admin = CommandUtil.adminName(source);
		if (target != null) {
			Chat.send(target, Chat.green("Администратор " + admin + " разморозил вас."));
		}
		AdminFeedback.tell(source, "Игрок " + record.nickname + " разморожен.");
		AdminNotify.record(source, "UNFREEZE", record.nickname, "-");
		return 1;
	}
}
