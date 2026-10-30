package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.EventService;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Developer controls for the opt-in bot-training beta. */
public final class BotTrainingCommands {
	private BotTrainingCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> d) {
		d.register(literal("bottraining").requires(s -> CommandUtil.canUse(s, "bottraining"))
				.executes(c -> status(c.getSource()))
				.then(literal("on").executes(c -> global(c.getSource(), true)))
				.then(literal("off").executes(c -> global(c.getSource(), false)))
				.then(literal("event").then(argument("id", IntegerArgumentType.integer(0, EventService.TYPES.length - 1))
						.then(literal("enable").executes(c -> enabled(c.getSource(), id(c), true)))
						.then(literal("disable").executes(c -> enabled(c.getSource(), id(c), false)))
						.then(literal("allies").then(argument("count", IntegerArgumentType.integer(0, 16))
								.executes(c -> number(c.getSource(), id(c), "allies", IntegerArgumentType.getInteger(c, "count")))))
						.then(literal("enemies").then(argument("count", IntegerArgumentType.integer(1, 32))
								.executes(c -> number(c.getSource(), id(c), "enemies", IntegerArgumentType.getInteger(c, "count")))))
						.then(literal("reward").then(argument("wc", IntegerArgumentType.integer(0, 1000000))
								.executes(c -> number(c.getSource(), id(c), "reward", IntegerArgumentType.getInteger(c, "wc")))))
						.then(literal("difficulty").then(argument("level", StringArgumentType.word())
								.suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(java.util.List.of("easy", "normal", "hard"), b))
								.executes(c -> difficulty(c.getSource(), id(c), StringArgumentType.getString(c, "level"))))))));
	}

	private static int id(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c) {
		return IntegerArgumentType.getInteger(c, "id");
	}

	private static ModSettings settings() { return DataManager.get().settings(); }
	private static ModSettings.BotTrainingConfig event(int id) {
		return settings().botTrainingEvents.computeIfAbsent(Integer.toString(id), k -> new ModSettings.BotTrainingConfig());
	}
	private static int save(ServerCommandSource source, String message) {
		DataManager.get().saveAll();
		source.sendFeedback(() -> Chat.green(message), false);
		return 1;
	}
	private static int global(ServerCommandSource source, boolean on) {
		if (on && !settings().betaFeaturesEnabled) {
			source.sendError(Chat.red("Сначала включите экспериментальные функции: /beta on"));
			return 0;
		}
		settings().botTrainingEnabled = on;
		return save(source, "Тренировки с ботами: " + (on ? "ВКЛЮЧЕНЫ" : "выключены"));
	}
	private static int enabled(ServerCommandSource source, int id, boolean on) {
		event(id).enabled = on;
		return save(source, EventService.TYPES[id] + ": " + (on ? "доступна" : "закрыта"));
	}
	private static int number(ServerCommandSource source, int id, String field, int value) {
		ModSettings.BotTrainingConfig e = event(id);
		if (field.equals("allies")) e.allies = value;
		else if (field.equals("enemies")) e.enemies = value;
		else e.reward = value;
		return save(source, EventService.TYPES[id] + ": " + field + " = " + value);
	}
	private static int difficulty(ServerCommandSource source, int id, String raw) {
		int level = switch (raw.toLowerCase(java.util.Locale.ROOT)) { case "easy" -> 0; case "hard" -> 2; default -> 1; };
		event(id).difficulty = level;
		return save(source, EventService.TYPES[id] + ": сложность = " + new String[]{"простая", "средняя", "сложная"}[level]);
	}
	private static int status(ServerCommandSource source) {
		ModSettings s = settings();
		long available = s.botTrainingEvents.values().stream().filter(e -> e != null && e.enabled).count();
		source.sendFeedback(() -> Chat.yellow("Тренировки с ботами: " + (s.botTrainingEnabled ? "ВКЛ" : "ВЫКЛ")
				+ ", доступных карт: " + available + "/" + EventService.TYPES.length), false);
		return 1;
	}
}
