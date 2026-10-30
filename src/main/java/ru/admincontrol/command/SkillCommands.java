package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.SkillTreeService;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /skills — своё дерево навыков; /askills, /askillinfo, /askilllist, /askillgive, /askilltake — администрирование. */
public final class SkillCommands {
	private static final int MAX_AMOUNT = 100_000;

	private static final SuggestionProvider<ServerCommandSource> CURRENCIES = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (SkillTree.Currency c : SkillTree.CURRENCIES) {
			ids.add(c.id());
			ids.add(c.alias());
		}
		return CommandSource.suggestMatching(ids, builder);
	};

	private SkillCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("skills").executes(SkillCommands::own));
		dispatcher.register(literal("quests").executes(SkillCommands::quests));
		dispatcher.register(literal("задания").executes(SkillCommands::quests));
		dispatcher.register(literal("askills")
				.requires(s -> CommandUtil.canUse(s, "askills"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/askills <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(SkillCommands::view)));
		dispatcher.register(literal("askillinfo")
				.requires(s -> CommandUtil.canUse(s, "askillinfo"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/askillinfo <ник>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(SkillCommands::info)));
		dispatcher.register(literal("askilllist")
				.requires(s -> CommandUtil.canUse(s, "askilllist"))
				.executes(SkillCommands::list));
		dispatcher.register(literal("askillgive")
				.requires(s -> CommandUtil.canUse(s, "askillgive"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/askillgive <ник> <валюта> <кол-во>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.then(argument("валюта", StringArgumentType.word()).suggests(CURRENCIES)
								.then(argument("кол-во", IntegerArgumentType.integer(1, MAX_AMOUNT))
										.executes(ctx -> adjust(ctx, false))))));
		dispatcher.register(literal("askilltake")
				.requires(s -> CommandUtil.canUse(s, "askilltake"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/askilltake <ник> <валюта> <кол-во>"))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.then(argument("валюта", StringArgumentType.word()).suggests(CURRENCIES)
								.then(argument("кол-во", IntegerArgumentType.integer(1, MAX_AMOUNT))
										.executes(ctx -> adjust(ctx, true))))));
	}

	private static int own(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requirePlayer(src)) {
			return 0;
		}
		SkillTreeService.open((ServerPlayerEntity) src.getEntity());
		return 1;
	}

	private static int quests(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requirePlayer(src)) {
			return 0;
		}
		SkillTreeService.openQuests((ServerPlayerEntity) src.getEntity());
		return 1;
	}

	private static PlayerRecord target(ServerCommandSource src, String nick) {
		PlayerRecord rec = DataManager.get() == null ? null : DataManager.get().findByNick(nick).orElse(null);
		if (rec == null) {
			src.sendError(Chat.red("Игрок или NPC «" + nick + "» не найден."));
			return null;
		}
		return rec;
	}

	private static int view(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "askills") || !CommandUtil.requirePlayer(src)) {
			return 0;
		}
		PlayerRecord rec = target(src, StringArgumentType.getString(ctx, "ник"));
		if (rec == null) {
			return 0;
		}
		SkillTreeService.openView((ServerPlayerEntity) src.getEntity(), rec);
		ActivityLog.admin(CommandUtil.adminName(src), "Дерево навыков: просмотр дерева игрока " + rec.nickname);
		return 1;
	}

	private static int info(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "askillinfo")) {
			return 0;
		}
		PlayerRecord rec = target(src, StringArgumentType.getString(ctx, "ник"));
		if (rec == null) {
			return 0;
		}
		ActivityLog.admin(CommandUtil.adminName(src), "Дерево навыков: просмотр баланса и прогресса игрока " + rec.nickname);
		List<String> lines = SkillTreeService.summary(rec);
		src.sendFeedback(() -> Chat.gold("=== " + lines.get(0) + " ==="), false);
		return ru.admincontrol.util.ChatDump.lines(src, lines.subList(1, lines.size()));
	}

	private static int list(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "askilllist")) {
			return 0;
		}
		List<String> lines = SkillTreeService.list();
		if (lines.isEmpty()) {
			lines = List.of("Пока никто не получил коинов и не купил навыков.");
		}
		if (src.getEntity() instanceof ServerPlayerEntity player) {
			ru.admincontrol.util.ScreenNav.openRoot(player,
					ru.admincontrol.screen.LogFactory.ofText("Дерево навыков — игроки", lines));
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== Дерево навыков — игроки ==="), false);
		return ru.admincontrol.util.ChatDump.lines(src, lines);
	}

	private static int adjust(CommandContext<ServerCommandSource> ctx, boolean take) {
		ServerCommandSource src = ctx.getSource();
		String command = take ? "askilltake" : "askillgive";
		if (!CommandUtil.requireCommand(src, command)) {
			return 0;
		}
		String nick = StringArgumentType.getString(ctx, "ник");
		PlayerRecord rec = target(src, nick);
		if (rec == null || !CommandUtil.requireCanAffect(src, rec.nickname)) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "валюта");
		SkillTree.Currency cur = SkillTree.currency(raw);
		if (cur == null) {
			List<String> names = new ArrayList<>();
			for (SkillTree.Currency c : SkillTree.CURRENCIES) {
				names.add(c.id() + " (" + c.title() + ")");
			}
			src.sendError(Chat.red("Нет валюты «" + raw + "». Доступны: " + String.join(", ", names) + "."));
			return 0;
		}
		int amount = IntegerArgumentType.getInteger(ctx, "кол-во");
		String error = SkillTreeService.adjust(src.getServer(), CommandUtil.adminName(src), rec, cur, amount, take);
		if (error != null) {
			src.sendError(Chat.red(error));
			return 0;
		}
		int now = SkillTreeService.data(rec).coins(cur.id());
		src.sendFeedback(() -> Chat.green((take ? "Забрано " : "Выдано ") + amount + " " + cur.coin() + " — "
				+ rec.nickname + ". Теперь: " + now + "."), false);
		return 1;
	}
}
