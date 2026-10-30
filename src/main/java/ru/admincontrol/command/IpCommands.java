package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.IpBan;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentRecord;
import ru.admincontrol.data.PunishmentType;
import ru.admincontrol.event.PlayerTracker;
import ru.admincontrol.screen.LogFactory;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ChatDump;
import ru.admincontrol.util.Durations;
import ru.admincontrol.util.IpService;
import ru.admincontrol.util.ScreenNav;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /banipp, /unbanipp и /ipcheck. */
public final class IpCommands {
	private IpCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("banipp").requires(s -> CommandUtil.canUse(s, "banipp"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/banipp <ник|IP> <причина>"))
				.then(argument("цель", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/banipp <ник|IP> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString()).suggests(CommandUtil.REASONS)
								.executes(ctx -> ban(ctx, true)))));
		dispatcher.register(literal("unbanipp").requires(s -> CommandUtil.canUse(s, "unbanipp"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unbanipp <ник|IP> <причина>"))
				.then(argument("цель", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/unbanipp <ник|IP> <причина>"))
						.then(argument("причина", StringArgumentType.greedyString())
								.executes(ctx -> ban(ctx, false)))));
		dispatcher.register(literal("ipcheck").requires(s -> CommandUtil.canUse(s, "ipcheck"))
				.executes(ctx -> CommandUtil.failSyntax(ctx.getSource(), "/ipcheck <ник|IP>"))
				.then(argument("цель", StringArgumentType.word()).suggests(CommandUtil.KNOWN_PLAYERS)
						.executes(IpCommands::check)));
	}

	/** Цель: IP или ник. Возвращает список IP или null (ошибка уже показана). */
	private static Target resolve(ServerCommandSource src, String raw) {
		Target target = new Target();
		if (IpService.looksLikeIp(raw)) {
			target.ips.add(IpService.normalize(raw));
			return target;
		}
		PlayerRecord record = CommandUtil.requireRecord(src, raw);
		if (record == null) {
			return null;
		}
		target.record = record;
		target.ips.addAll(IpService.ipsOf(record));
		if (target.ips.isEmpty()) {
			src.sendError(Chat.red("У игрока «" + record.nickname + "» нет сохранённых IP-адресов."));
			return null;
		}
		return target;
	}

	private static int ban(CommandContext<ServerCommandSource> ctx, boolean banning) {
		ServerCommandSource src = ctx.getSource();
		String command = banning ? "banipp" : "unbanipp";
		if (!CommandUtil.requireCommand(src, command)) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "цель");
		String reason = StringArgumentType.getString(ctx, "причина").trim();
		Target target = resolve(src, raw);
		if (target == null) {
			return 0;
		}
		if (target.record != null && !(banning ? CommandUtil.requireCanPunish(src, target.record.nickname)
				: CommandUtil.requireCanAffect(src, target.record.nickname))) {
			return 0;
		}
		Map<String, PlayerRecord> affected = new LinkedHashMap<>();
		for (String ip : target.ips) {
			for (PlayerRecord record : IpService.playersWithIp(ip)) {
				affected.putIfAbsent(record.nickname.toLowerCase(java.util.Locale.ROOT), record);
			}
		}
		String admin = CommandUtil.adminName(src);
		if (banning) {
			String self = src.getEntity() instanceof ServerPlayerEntity player ? player.getGameProfile().getName() : null;
			for (PlayerRecord record : affected.values()) {
				if (self != null && self.equalsIgnoreCase(record.nickname)) {
					src.sendError(Chat.red("С этого IP заходили вы сами — бан заблокирует и вас."));
					return 0;
				}
				if (!CommandUtil.requireCanPunish(src, record.nickname)) {
					return 0;
				}
			}
		}
		List<String> changed = new ArrayList<>();
		for (String ip : target.ips) {
			boolean done = banning
					? IpService.ban(ip, reason, admin, target.record == null ? "" : target.record.nickname)
					: IpService.unban(ip);
			if (done) {
				changed.add(ip);
			}
		}
		if (changed.isEmpty()) {
			src.sendError(Chat.red(banning ? "Эти IP-адреса уже заблокированы." : "Эти IP-адреса не заблокированы."));
			return 0;
		}
		for (PlayerRecord record : affected.values()) {
			if (banning) {
				ModerationService.addHistory(record, PunishmentType.IPBAN, true, reason, src, null);
			} else if (IpService.ipsOf(record).stream().noneMatch(ip -> IpService.find(ip).isPresent())) {
				for (PunishmentRecord item : record.history) {
					if (item.type == PunishmentType.IPBAN && item.active) {
						item.active = false;
					}
				}
				ModerationService.addHistory(record, PunishmentType.UNIPBAN, false, reason, src, null);
			}
		}
		DataManager.get().saveAll();
		List<String> nicks = new ArrayList<>();
		for (PlayerRecord record : affected.values()) {
			nicks.add(record.nickname);
		}
		String who = nicks.isEmpty() ? "—" : String.join(", ", nicks);
		String label = target.record != null ? target.record.nickname : "IP";
		if (banning) {
			PlayerTracker.clearBanPass();
			if (!nicks.isEmpty()) {
				Chat.broadcast(src.getServer(), Chat.red("Администратор " + admin + " заблокировал по IP: " + who
						+ " по причине: " + reason));
			}
			AdminFeedback.tell(src, "Заблокировано IP: " + String.join(", ", changed) + ". Затронуты игроки: " + who + ".");
		} else {
			AdminFeedback.tell(src, "Разблокировано IP: " + String.join(", ", changed) + ". Игроки: " + who + ".");
		}
		AdminNotify.record(src, banning ? "BANIPP" : "UNBANIPP", label, String.join(", ", changed) + ": " + reason);
		return 1;
	}

	private static int check(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		if (!CommandUtil.requireCommand(src, "ipcheck") || !LogAccess.requireNotBlogger(src)) {
			return 0;
		}
		String raw = StringArgumentType.getString(ctx, "цель");
		Target target = resolve(src, raw);
		if (target == null) {
			return 0;
		}
		if (target.record != null && !CommandUtil.requireCanAffect(src, target.record.nickname)) {
			return 0;
		}
		List<String> lines = new ArrayList<>();
		Set<String> all = new LinkedHashSet<>();
		String self = target.record == null ? null : target.record.nickname;
		if (self != null) {
			lines.add("Игрок " + self + " — IP-адресов: " + target.ips.size());
			lines.add("");
		}
		for (String ip : target.ips) {
			IpBan ban = IpService.find(ip).orElse(null);
			lines.add("IP " + ip + (ban == null ? "" : "  [бан IP: " + ban.reason + " / " + ban.adminNick
					+ " · " + Durations.until(ban.createdAt) + "]"));
			List<String> names = new ArrayList<>();
			for (PlayerRecord record : IpService.playersWithIp(ip)) {
				if (self == null || !self.equalsIgnoreCase(record.nickname)) {
					names.add(record.nickname);
					all.add(record.nickname);
				}
			}
			lines.add("   " + (names.isEmpty() ? "совпадений нет" : "ники: " + String.join(", ", names)));
		}
		lines.add("");
		lines.add(all.isEmpty() ? "Совпадений по IP нет." : "Все совпадения (" + all.size() + "): " + String.join(", ", all));
		String title = "Совпадения IP  " + (self != null ? self : target.ips.get(0));
		AdminNotify.record(src, "IPCHECK", self != null ? self : target.ips.get(0), "");
		if (src.getEntity() instanceof ServerPlayerEntity player) {
			ScreenNav.openRoot(player, LogFactory.ofText(title, lines));
			return 1;
		}
		src.sendFeedback(() -> Chat.gold("=== " + title + " ==="), false);
		return ChatDump.lines(src, lines);
	}

	private static final class Target {
		PlayerRecord record;
		final List<String> ips = new ArrayList<>();
	}
}
