package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.DuelService;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Developer-only command surface for beta flags and reward-free duels. */
public final class DuelCommands {
	private DuelCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> d) {
		d.register(literal("duel").requires(s -> CommandUtil.canUse(s, "duel"))
				.executes(c -> fail(c.getSource(), "Использование: /duel <ник>, /duel accept, /duel deny"))
				.then(literal("accept").executes(c -> result(c.getSource(), DuelService.accept(CommandUtil.self(c)))))
				.then(literal("deny").executes(c -> result(c.getSource(), DuelService.deny(CommandUtil.self(c)))))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS).executes(c -> {
					ServerPlayerEntity self = CommandUtil.self(c);
					ServerPlayerEntity target = c.getSource().getServer().getPlayerManager().getPlayer(StringArgumentType.getString(c, "ник"));
					return result(c.getSource(), DuelService.invite(self, target));
				})));
		d.register(literal("beta").requires(s -> CommandUtil.canUse(s, "beta"))
				.executes(c -> beta(c.getSource(), null))
				.then(literal("on").executes(c -> beta(c.getSource(), true)))
				.then(literal("off").executes(c -> beta(c.getSource(), false))));
	}

	private static int result(ServerCommandSource source, String error) {
		if (error == null) return 1;
		source.sendError(Chat.red(error));
		return 0;
	}
	private static int fail(ServerCommandSource source, String message) { source.sendError(Chat.yellow(message)); return 0; }
	private static int beta(ServerCommandSource source, Boolean value) {
		if (DataManager.get() == null) return 0;
		if (value != null) {
			DataManager.get().settings().betaFeaturesEnabled = value;
			DataManager.get().saveAll();
		}
		boolean on = DataManager.get().settings().betaFeaturesEnabled;
		source.sendFeedback(() -> Chat.yellow("Экспериментальные функции: " + (on ? "ВКЛЮЧЕНЫ" : "выключены")), false);
		return 1;
	}
}
