package ru.admincontrol.lumira;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.lumira.gen.LumiraBiomeGen;
import ru.admincontrol.util.BalanceBoard;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /cw biome …, /cw economy … (администратор, ГА+) и игровые /lumira, /necklace.
 * Узлы /cw присоединяются к уже существующему корню «cw» без создания второго менеджера.
 */
public final class LumiraCommands {
	private LumiraCommands() {
	}

	private static boolean ga(ServerCommandSource s) {
		return CommandUtil.hasRank(s, AdminRanks.GA);
	}

	private static int ok(ServerCommandSource s, String m) {
		s.sendFeedback(() -> Text.literal("[CW] " + m), false);
		return 1;
	}

	private static int err(ServerCommandSource s, String m) {
		s.sendError(Text.literal("[CW] " + m));
		return 0;
	}

	private static UUID uuid(ServerCommandSource s) {
		try {
			return s.getPlayer().getUuid();
		} catch (Exception e) {
			return null;
		}
	}

	public static void register(CommandDispatcher<ServerCommandSource> d) {
		d.register(literal("cw").requires(CommandUtil::canUseCw)
				.then(literal("biome").requires(LumiraCommands::ga)
						.then(literal("lumira")
								.executes(c -> start(c.getSource(), LumiraConfig.get().biomeChunks))
								.then(argument("chunks", IntegerArgumentType.integer(1, LumiraBiomeGen.MAX_CHUNKS))
										.executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "chunks")))))
						.then(literal("status").executes(c -> ok(c.getSource(), LumiraBiomeGen.status())))
						.then(literal("cancel").executes(c -> ok(c.getSource(), LumiraBiomeGen.cancel())))
						)
				.then(literal("economy").requires(LumiraCommands::ga)
						.then(literal("show").then(argument("player", EntityArgumentType.player()).executes(c -> {
							ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
							return ok(c.getSource(), p.getGameProfile().getName() + ": лиры " + LumiraEconomy.lyres(p.getUuid())
									+ ", мирты " + LumiraEconomy.myrtles(p.getUuid()));
						})))
						.then(literal("lyres").then(argument("player", EntityArgumentType.player())
								.then(argument("delta", LongArgumentType.longArg(-1_000_000, 1_000_000)).executes(c -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
									boolean done = LumiraEconomy.addLyres(p.getUuid(), LongArgumentType.getLong(c, "delta"), "команда " + c.getSource().getName());
									BalanceBoard.refresh(p, true);
									return done ? ok(c.getSource(), "Лиры: " + LumiraEconomy.lyres(p.getUuid())) : err(c.getSource(), "Недостаточно лир.");
								}))))
						.then(literal("myrtles").then(argument("player", EntityArgumentType.player())
								.then(argument("delta", LongArgumentType.longArg(-1_000_000, 1_000_000)).executes(c -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
									boolean done = LumiraEconomy.addMyrtles(p.getUuid(), LongArgumentType.getLong(c, "delta"), "команда " + c.getSource().getName());
									BalanceBoard.refresh(p, true);
									return done ? ok(c.getSource(), "Мирты: " + LumiraEconomy.myrtles(p.getUuid())) : err(c.getSource(), "Мирт не может стать отрицательным.");
								}))))
						// Полное удаление накоплений — отдельная операция, не часть сброса прохождения.
						.then(literal("wipe").then(argument("player", EntityArgumentType.player())
								.then(literal("confirm").executes(c -> {
									ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
									LumiraEconomy.wipe(p.getUuid(), "wipe командой " + c.getSource().getName());
									BalanceBoard.refresh(p, true);
									return ok(c.getSource(), "Сюжетная экономика игрока удалена.");
								}))))));
		d.register(literal("lumira").executes(c -> {
			UUID id = uuid(c.getSource());
			if (id == null) {
				return err(c.getSource(), "Команда доступна только игроку.");
			}
			c.getSource().sendFeedback(() -> Text.literal("Лиры: " + LumiraEconomy.lyres(id) + "  •  Мирты: " + LumiraEconomy.myrtles(id)), false);
			return 1;
		}));
		d.register(literal("necklace").then(literal("remove").executes(c -> {
			try {
				ServerPlayerEntity p = c.getSource().getPlayer();
				if (LumiraNecklaces.unequip(p)) {
					p.sendMessage(Text.literal("Ожерелье снято"), true);
					return 1;
				}
				p.sendMessage(Text.literal("Слот ожерелья пуст"), true);
			} catch (Exception ignored) {
			}
			return 0;
		})));
	}

	private static int start(ServerCommandSource s, int chunks) {
		net.minecraft.util.math.BlockPos where = net.minecraft.util.math.BlockPos.ofFloored(s.getPosition());
		String msg = LumiraBiomeGen.start(s.getServer(), uuid(s), where, chunks);
		return msg.startsWith("Генерация «Рощи") ? ok(s, msg) : err(s, msg);
	}
}
