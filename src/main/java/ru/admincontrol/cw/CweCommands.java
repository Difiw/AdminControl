package ru.admincontrol.cw;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.CweNotice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /bl} (Expedition name blacklist) and {@code /delstory} (full reset of one player's story profile).
 * Access: GA and developer only; the console counts as developer, as everywhere else in the project.
 */
public final class CweCommands {
	private static final Set<UUID> RESETTING = ConcurrentHashMap.newKeySet();
	private static final SuggestionProvider<ServerCommandSource> LISTED = (ctx, b) ->
			CommandSource.suggestMatching(CweBlacklist.list().stream().map(e -> e.name).toList(), b);

	private CweCommands() {}

	private static boolean allowed(ServerCommandSource s) { return CommandUtil.hasRank(s, AdminRanks.GA); }

	public static void register(CommandDispatcher<ServerCommandSource> d) {
		CwMapResetService.register();
		d.register(literal("cwe").requires(s -> CommandUtil.hasRank(s, AdminRanks.DEV))
				.then(literal("del")
						.executes(c -> err(c.getSource(), "Опасная операция. Для сброса текущей CW-карты используйте точную команду /cwe del yes."))
						.then(literal("yes").executes(c -> resetMap(c.getSource())))));
		d.register(literal("bl").requires(CweCommands::allowed)
				.executes(c -> list(c.getSource(), 1))
				.then(argument("страница", IntegerArgumentType.integer(1)).executes(c -> list(c.getSource(), IntegerArgumentType.getInteger(c, "страница"))))
				.then(literal("add").then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS).executes(c -> add(c))))
				.then(literal("del").then(argument("ник", StringArgumentType.word()).suggests(LISTED).executes(c -> remove(c, "bl del"))))
				.then(literal("reserve").then(argument("ник", StringArgumentType.word()).executes(c -> reserve(c))))
				.then(literal("free").then(argument("ник", StringArgumentType.word()).suggests(LISTED).executes(c -> remove(c, "bl free")))));
		d.register(literal("storyload").executes(c -> { var p = c.getSource().getPlayer(); if (p == null) { c.getSource().sendError(Text.literal("Команда доступна только игроку.")); return 0; } String e = CwRespawnService.openSaves(p); if (e != null) c.getSource().sendError(Text.literal(e)); return e == null ? 1 : 0; }));
		d.register(literal("delaccinfo").requires(s -> CommandUtil.hasRank(s, AdminRanks.DEV))
				.executes(c -> err(c.getSource(), "Удаление данных заблокированных аккаунтов: /delaccinfo yes true (только разработчик)."))
				.then(literal("yes").executes(c -> err(c.getSource(), "Добавьте подтверждение: /delaccinfo yes true"))
						.then(literal("true").executes(CweCommands::approve))));
		d.register(literal("delstory").requires(CweCommands::allowed)
				.executes(c -> usage(c.getSource()))
				.then(argument("ник", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(c -> usage(c.getSource()))
						.then(argument("причина", StringArgumentType.greedyString()).executes(CweCommands::delStory))));
	}

	private static int resetMap(ServerCommandSource source) {
		String error = CwMapResetService.start(source);
		return error == null ? ok(source, "Безопасный сброс выбранной карты запущен; итоговый отчёт появится после очистки всех чанков.") : err(source, error);
	}

	private static int ok(ServerCommandSource s, String m) { s.sendFeedback(() -> Text.literal("[CWE] " + m), false); return 1; }
	private static int err(ServerCommandSource s, String m) { s.sendError(Text.literal("[CWE] " + m)); return 0; }
	private static int approve(CommandContext<ServerCommandSource> c) {
		ServerCommandSource s = c.getSource();
		int n = CweBlacklist.approveCleanup(actor(s));
		if (n < 0) return err(s, "Не удалось сохранить список; разрешение не выдано.");
		return ok(s, n == 0 ? "Нет аккаунтов, ожидающих удаления данных." : "Удаление данных разрешено для аккаунтов: " + n + ". Выполняется после отключения игроков, с резервной копией в cwe-backups.");
	}
	private static int usage(ServerCommandSource s) { return err(s, "/delstory <ник> <причина> — полный сброс сюжетного профиля. Пример: /delstory Aluya Тестовый сброс сюжетного прогресса"); }
	private static String actor(ServerCommandSource s) { return s.getEntity() == null ? "CONSOLE" : s.getName(); }

	private static int list(ServerCommandSource s, int page) {
		List<CweBlacklist.Entry> all = CweBlacklist.list();
		if (all.isEmpty()) return ok(s, "Чёрный список Expedition пуст. /bl add <ник> — заблокировать аккаунт, /bl reserve <ник> — зарезервировать имя.");
		int pages = CweBlacklist.pages(all.size()), p = Math.min(page, pages);
		ok(s, "Чёрный список Expedition — страница " + p + "/" + pages + " (всего " + all.size() + ")");
		for (CweBlacklist.Entry e : all.subList((p - 1) * CweBlacklist.PAGE_SIZE, Math.min(all.size(), p * CweBlacklist.PAGE_SIZE))) {
			String kind = "ACCOUNT".equals(e.kind) ? "аккаунт" : "резерв";
			String pending = e.cleanupPending ? (e.cleanupApproved ? ", очистка разрешена" : ", данные не удалены (ждут /delaccinfo yes true)") : (e.cleanupResult != null && e.cleanupResult.startsWith("FAILED") ? ", очистка НЕ выполнена" : "");
			s.sendFeedback(() -> Text.literal(" • " + e.name + " — " + kind + ", с " + CweNotice.formatDate(e.addedAt) + pending), false);
		}
		return 1;
	}

	private static int add(CommandContext<ServerCommandSource> c) {
		ServerCommandSource s = c.getSource();
		String nick = StringArgumentType.getString(c, "ник");
		if (s.getEntity() != null && s.getName().equalsIgnoreCase(nick)) return err(s, "Нельзя внести в список самого себя.");
		String e = CweBlacklist.add(s.getServer(), actor(s), nick);
		return e == null ? ok(s, nick + " внесён в чёрный список; игрок отключён. Данные аккаунта НЕ удаляются, пока разработчик не введёт /delaccinfo yes true (перед удалением делается резервная копия).") : err(s, e);
	}

	private static int reserve(CommandContext<ServerCommandSource> c) {
		ServerCommandSource s = c.getSource();
		String nick = StringArgumentType.getString(c, "ник");
		String e = CweBlacklist.reserve(s.getServer(), actor(s), nick);
		return e == null ? ok(s, "Имя " + nick + " зарезервировано: создание и вход запрещены.") : err(s, e);
	}

	private static int remove(CommandContext<ServerCommandSource> c, String command) {
		ServerCommandSource s = c.getSource();
		String nick = StringArgumentType.getString(c, "ник");
		String e = CweBlacklist.remove(actor(s), nick, command);
		return e == null ? ok(s, nick + " удалён из чёрного списка. Ранее очищенные данные не восстанавливаются.") : err(s, e);
	}

	private static int delStory(CommandContext<ServerCommandSource> c) {
		ServerCommandSource s = c.getSource();
		String nick = StringArgumentType.getString(c, "ник");
		String reason = StringArgumentType.getString(c, "причина").trim();
		if (reason.isEmpty()) return usage(s);
		ServerPlayerEntity online = CommandUtil.findOnline(s.getServer(), nick).orElse(null);
		PlayerRecord record = DataManager.get() == null ? null : DataManager.get().findByNick(nick).orElse(null);
		UUID uuid = online != null ? online.getUuid() : parse(record == null ? null : record.uuid);
		if (uuid == null) return err(s, "Игрок «" + nick + "» не найден: UUID не определён, ничего не удалено.");
		if (!RESETTING.add(uuid)) return err(s, "Сброс этого игрока уже выполняется.");
		try {
			Path backup = DataManager.get().getConfigDir().resolve("cwe-backups")
					.resolve("story-" + uuid + "-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(java.time.Instant.now()));
			try {
				Files.createDirectories(backup);
				Files.writeString(backup.resolve("story-data.json"), CwStoryWorldService.backupJson(uuid), StandardCharsets.UTF_8);
			} catch (Exception ex) {
				ru.admincontrol.AdminControlMod.LOGGER.error("[CWE] delstory backup failed", ex);
				CwAudit.write(s, "delstory", nick + " " + uuid, "ABORTED backup failed; reason=" + reason);
				return err(s, "Не удалось сделать резервную копию — сброс отменён, данные не тронуты.");
			}
			String error = online != null ? CwStoryWorldService.resetPlayer(online) : CwStoryWorldService.resetOffline(uuid);
			if (error != null) {
				CwAudit.write(s, "delstory", nick + " " + uuid, "FAILED " + error + "; reason=" + reason);
				return err(s, error);
			}
			CwStoryService.reset(uuid);
			if (online != null) { CwStoryEntryService.clear(online); CwDialogueService.clear(online); CwRespawnService.clear(online); }
			CwCheckpointService.clear(uuid);
			CwAudit.write(s, "delstory", nick + " " + uuid, "OK reason=" + reason + "; backup=" + backup.getFileName());
			return ok(s, "Сюжетный профиль " + nick + " полностью сброшен (резервная копия: " + backup.getFileName() + "). Основной профиль не затронут; Луя предложит «Начать».");
		} finally {
			RESETTING.remove(uuid);
		}
	}

	private static UUID parse(String raw) {
		try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
	}
}
