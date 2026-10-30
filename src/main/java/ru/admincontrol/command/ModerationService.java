package ru.admincontrol.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.BanEntry;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentRecord;
import ru.admincontrol.data.PunishmentType;
import ru.admincontrol.util.AdminFeedback;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.PunishmentNotice;

import java.util.concurrent.TimeUnit;

public final class ModerationService {
	private ModerationService() {
	}

	public static PunishmentRecord addHistory(PlayerRecord record, PunishmentType type, boolean active,
			String reason, ServerCommandSource source, Integer days) {
		return addHistory(record, type, active, reason, source, days, null);
	}

	public static PunishmentRecord addHistory(PlayerRecord record, PunishmentType type, boolean active,
			String reason, ServerCommandSource source, Integer days, Integer minutes) {
		PunishmentRecord item = new PunishmentRecord();
		item.type = type;
		item.active = active;
		item.reason = reason;
		item.adminNick = CommandUtil.adminName(source);
		item.adminUuid = CommandUtil.adminUuid(source);
		item.createdAt = System.currentTimeMillis();
		item.durationDays = days;
		item.durationMinutes = minutes;
		if (days != null && days > 0 && type == PunishmentType.BAN) {
			item.expiresAt = item.createdAt + TimeUnit.DAYS.toMillis(days);
		}
		if (minutes != null && minutes > 0 && type == PunishmentType.JAIL) {
			item.expiresAt = item.createdAt + TimeUnit.MINUTES.toMillis(minutes);
		}
		record.history.add(item);
		return item;
	}

	public static boolean ban(ServerCommandSource source, PlayerRecord record, int days, String reason, boolean permanent) {
		DataManager data = DataManager.get();
		if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
			source.sendError(Chat.red("Игрок «" + record.nickname + "» уже заблокирован."));
			return false;
		}
		PunishmentType type = permanent ? PunishmentType.PERMABAN : PunishmentType.BAN;
		PunishmentRecord history = addHistory(record, type, true, reason, source, permanent ? null : days);
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.permanent = permanent;
		entry.reason = reason;
		entry.adminNick = CommandUtil.adminName(source);
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		data.addBan(entry);
		data.saveAll();

		MinecraftServer server = source.getServer();
		String admin = CommandUtil.adminName(source);
		String screenAdmin = PunishmentNotice.displayAdmin(source);
		CommandUtil.findOnline(server, record.nickname).ifPresent(player -> PunishmentNotice.disconnect(player,
				permanent ? PunishmentNotice.Kind.PERMBAN : PunishmentNotice.Kind.BAN,
				screenAdmin, reason,
				permanent ? PunishmentNotice.permDuration() : PunishmentNotice.banDuration(days, history.expiresAt),
				history.createdAt));

		if (permanent) {
			Chat.broadcast(server, Chat.red("Администратор " + admin + " навсегда заблокировал игрока "
					+ record.nickname + " по причине: " + reason));
			AdminFeedback.tell(source, "Игрок " + record.nickname + " навсегда заблокирован.");
		} else {
			Chat.broadcast(server, Chat.red("Администратор " + admin + " заблокировал игрока " + record.nickname
					+ " на " + days + " " + Chat.daysWord(days) + " по причине: " + reason));
			AdminFeedback.tell(source, "Игрок " + record.nickname + " заблокирован на " + days + " "
					+ Chat.daysWord(days) + ".");
		}
		AdminNotify.record(source, permanent ? "PERMABAN" : "BAN", record.nickname, reason);
		return true;
	}

	public static boolean unban(ServerCommandSource source, PlayerRecord record, String reason, boolean permanent) {
		DataManager data = DataManager.get();
		var removed = data.removeBan(record.nickname, permanent, !permanent);
		if (removed.isEmpty()) {
			source.sendError(Chat.red("У игрока «" + record.nickname + "» нет "
					+ (permanent ? "перманентного бана." : "временного бана.")));
			return false;
		}
		PunishmentType original = permanent ? PunishmentType.PERMABAN : PunishmentType.BAN;
		for (PunishmentRecord history : record.history) {
			if (history.type == original && history.active) {
				history.active = false;
			}
		}
		addHistory(record, permanent ? PunishmentType.UNPERMABAN : PunishmentType.UNBAN, false, reason, source, null);
		data.saveAll();
		MinecraftServer server = source.getServer();
		String admin = CommandUtil.adminName(source);
		CommandUtil.findOnline(server, record.nickname).ifPresent(player ->
				Chat.send(player, Chat.blue("Администратор " + admin + " снял с вас бан. Причина: " + reason)));
		Chat.broadcast(server, Chat.red("Администратор " + admin + " снял "
				+ (permanent ? "перманентный бан" : "бан") + " с игрока " + record.nickname
				+ " по причине: " + reason));
		AdminFeedback.tell(source, "Бан с игрока «" + record.nickname + "» снят.");
		AdminNotify.record(source, permanent ? "UNPERMABAN" : "UNBAN", record.nickname, reason);
		return true;
	}

	public static boolean jail(ServerCommandSource source, PlayerRecord record, int minutes, String reason) {
		DataManager data = DataManager.get();
		if (data.findActiveJail(record.uuidOrNull(), record.nickname).isPresent()) {
			source.sendError(Chat.red("Игрок «" + record.nickname + "» уже в jail."));
			return false;
		}
		PunishmentRecord history = addHistory(record, PunishmentType.JAIL, true, reason, source, null, minutes);
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.jail = true;
		entry.permanent = false;
		entry.reason = reason;
		entry.adminNick = CommandUtil.adminName(source);
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		data.addBan(entry);
		data.saveAll();

		MinecraftServer server = source.getServer();
		String admin = CommandUtil.adminName(source);
		CommandUtil.findOnline(server, record.nickname).ifPresent(player ->
				PunishmentNotice.disconnect(player, PunishmentNotice.Kind.JAIL,
						PunishmentNotice.displayAdmin(source), reason,
						PunishmentNotice.jailDuration(minutes, history.expiresAt), history.createdAt));
		Chat.broadcast(server, Chat.red("Администратор " + admin + " отправил игрока " + record.nickname
				+ " в jail на " + minutes + " " + Chat.minutesWord(minutes) + " по причине: " + reason));
		AdminFeedback.tell(source, "Игрок " + record.nickname + " отправлен в jail на " + minutes + " "
				+ Chat.minutesWord(minutes) + ".");
		AdminNotify.record(source, "JAIL", record.nickname, minutes + " мин: " + reason);
		return true;
	}

	public static boolean unjail(ServerCommandSource source, PlayerRecord record, String reason) {
		DataManager data = DataManager.get();
		var removed = data.removeJail(record.nickname);
		if (removed.isEmpty()) {
			source.sendError(Chat.red("У игрока «" + record.nickname + "» нет активного jail."));
			return false;
		}
		for (PunishmentRecord history : record.history) {
			if (history.type == PunishmentType.JAIL && history.active) {
				history.active = false;
			}
		}
		addHistory(record, PunishmentType.UNJAIL, false, reason, source, null);
		data.saveAll();
		String admin = CommandUtil.adminName(source);
		CommandUtil.findOnline(source.getServer(), record.nickname).ifPresent(player ->
				Chat.send(player, Chat.blue("Администратор " + admin + " снял с вас jail. Причина: " + reason)));
		Chat.broadcast(source.getServer(), Chat.red("Администратор " + admin + " снял jail с игрока "
				+ record.nickname + " по причине: " + reason));
		AdminFeedback.tell(source, "Jail с игрока «" + record.nickname + "» снят.");
		AdminNotify.record(source, "UNJAIL", record.nickname, reason);
		return true;
	}

	public static void warn(ServerCommandSource source, PlayerRecord record, String reason) {
		DataManager data = DataManager.get();
		record.activeWarns = Math.max(0, record.activeWarns) + 1;
		addHistory(record, PunishmentType.WARN, true, reason, source, null);
		data.removeWarn(record.nickname);
		data.saveAll();

		MinecraftServer server = source.getServer();
		String admin = CommandUtil.adminName(source);
		int warns = record.activeWarns;
		Chat.broadcast(server, Chat.red("Администратор " + admin + " выдал варн игроку " + record.nickname
				+ " по причине: " + reason + " (" + warns + "/3)"));
		AdminFeedback.tell(source, "Варн выдан игроку " + record.nickname + " (" + warns + "/3).");
		AdminNotify.record(source, "WARN", record.nickname, reason);

		if (warns >= 3) {
			for (PunishmentRecord item : record.history) {
				if (item.type == PunishmentType.WARN && item.active) {
					item.active = false;
				}
			}
			data.removeWarn(record.nickname);
			record.activeWarns = 0;
			data.saveAll();
			int autoBanDays = 10;
			Chat.broadcast(server, Chat.red("Игрок " + record.nickname
					+ " набрал 3/3 варнов: варны обнулены, бан на " + autoBanDays
					+ " " + Chat.daysWord(autoBanDays)));
			ban(source, record, autoBanDays, "3 предупреждения", false);
			return;
		}

		CommandUtil.findOnline(server, record.nickname).ifPresent(player ->
				PunishmentNotice.disconnect(player, PunishmentNotice.Kind.WARN,
						PunishmentNotice.displayAdmin(source), reason, PunishmentNotice.warnDuration(),
						System.currentTimeMillis()));
	}

	public static final int VERBAL_WARN_COLOR = 0xFF8C3A;

	public static void verbalWarn(ServerCommandSource source, PlayerRecord record, String sentence) {
		addHistory(record, PunishmentType.VERBAL_WARN, false, sentence, source, null);
		DataManager.get().saveAll();
		String admin = CommandUtil.adminName(source);
		boolean online = CommandUtil.findOnline(source.getServer(), record.nickname).map(player -> {
			Chat.send(player, Chat.color("Администратор " + admin + " дал вам устное предупреждение: "
					+ sentence, VERBAL_WARN_COLOR));
			return true;
		}).orElse(false);
		long total = record.history.stream().filter(p -> p.type == PunishmentType.VERBAL_WARN).count();
		AdminFeedback.tell(source, "Устное предупреждение выдано игроку " + record.nickname
				+ " (всего: " + total + ")" + (online ? "." : ". Игрок не в сети — увидит запись в истории."));
		AdminNotify.record(source, "VERBAL_WARN", record.nickname, sentence);
	}

	public static void unwarn(ServerCommandSource source, PlayerRecord record, String reason) {
		DataManager data = DataManager.get();
		PunishmentRecord last = null;
		for (int i = record.history.size() - 1; i >= 0; i--) {
			PunishmentRecord item = record.history.get(i);
			if (item.type == PunishmentType.WARN && item.active) {
				last = item;
				break;
			}
		}
		if (last == null && record.activeWarns <= 0 && data.findActiveWarn(record.uuidOrNull(), record.nickname).isEmpty()) {
			source.sendError(Chat.red("У игрока «" + record.nickname + "» нет активных варнов."));
			return;
		}
		if (last != null) {
			last.active = false;
		}
		record.activeWarns = Math.max(0, record.activeWarns - 1);
		data.removeWarn(record.nickname);
		addHistory(record, PunishmentType.UNWARN, false, reason, source, null);
		data.saveAll();
		String admin = CommandUtil.adminName(source);
		CommandUtil.findOnline(source.getServer(), record.nickname).ifPresent(player ->
				Chat.send(player, Chat.blue("Администратор " + admin + " снял с вас варн. Причина: " + reason)));
		Chat.broadcast(source.getServer(), Chat.red("Администратор " + admin + " снял варн с игрока "
				+ record.nickname + " по причине: " + reason));
		AdminFeedback.tell(source, "Варн с игрока «" + record.nickname + "» снят. Сейчас "
				+ record.activeWarns + "/3.");
		AdminNotify.record(source, "UNWARN", record.nickname, reason);
	}

	public static boolean buyUnwarn(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return false;
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		PunishmentRecord last = null;
		for (int i = record.history.size() - 1; i >= 0; i--) {
			PunishmentRecord item = record.history.get(i);
			if (item.type == PunishmentType.WARN && item.active) {
				last = item;
				break;
			}
		}
		if (last == null && record.activeWarns <= 0
				&& DataManager.get().findActiveWarn(record.uuidOrNull(), record.nickname).isEmpty()) {
			return false;
		}
		if (last != null) {
			last.active = false;
		}
		record.activeWarns = Math.max(0, record.activeWarns - 1);
		DataManager.get().removeWarn(record.nickname);
		addHistory(record, PunishmentType.UNWARN, false, "снятие за World coin", player.getCommandSource(), null);
		DataManager.get().saveAll();
		Chat.send(player, Chat.green("Варн снят. Сейчас " + record.activeWarns + "/3."));
		return true;
	}

	public static void kick(ServerCommandSource source, ServerPlayerEntity target, PlayerRecord record, String reason) {
		addHistory(record, PunishmentType.KICK, false, reason, source, null);
		DataManager.get().saveAll();
		String admin = CommandUtil.adminName(source);
		Chat.broadcast(source.getServer(), Chat.red("Администратор " + admin + " отключил игрока "
				+ record.nickname + " от сервера по причине: " + reason));
		AdminFeedback.tell(source, "Игрок " + record.nickname + " отключён.");
		AdminNotify.record(source, "KICK", record.nickname, reason);
		PunishmentNotice.disconnect(target, PunishmentNotice.Kind.KICK,
				PunishmentNotice.displayAdmin(source), reason, PunishmentNotice.kickDuration(), System.currentTimeMillis());
	}

	public static void autoKick(ServerPlayerEntity target, PlayerRecord record, String reason) {
		if (target == null || record == null || target.networkHandler == null) {
			return;
		}
		addHistory(record, PunishmentType.KICK, false, reason, target.getServer().getCommandSource(), null);
		record.history.get(record.history.size() - 1).adminNick = "Античит";
		DataManager.get().saveAll();
		Chat.broadcast(target.getServer(), Chat.red("Античит отключил игрока " + record.nickname
				+ " по причине: " + reason));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ANTICHEAT, "кик: " + reason, false);
		PunishmentNotice.disconnect(target, PunishmentNotice.Kind.KICK,
				PunishmentNotice.displayAdmin("Античит", true), reason, PunishmentNotice.kickDuration(),
				System.currentTimeMillis());
	}

	public static boolean autoJail(ServerPlayerEntity target, PlayerRecord record, int minutes, String reason) {
		if (record == null) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data.findActiveJail(record.uuidOrNull(), record.nickname).isPresent()) {
			if (target != null && target.networkHandler != null) {
				autoKick(target, record, reason);
			}
			return false;
		}
		net.minecraft.server.MinecraftServer server = target == null ? null : target.getServer();
		if (server == null) {
			return false;
		}
		PunishmentRecord history = addHistory(record, PunishmentType.JAIL, true, reason, server.getCommandSource(), null, minutes);
		history.adminNick = "Античит";
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.jail = true;
		entry.permanent = false;
		entry.reason = reason;
		entry.adminNick = "Античит";
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		data.addBan(entry);
		data.saveAll();
		Chat.broadcast(server, Chat.red("Античит отправил игрока " + record.nickname + " в jail на "
				+ minutes + " " + Chat.minutesWord(minutes) + " по причине: " + reason));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ANTICHEAT,
				"jail " + minutes + " мин: " + reason, false);
		PunishmentNotice.disconnect(target, PunishmentNotice.Kind.JAIL,
				PunishmentNotice.displayAdmin("Античит", true), reason,
				PunishmentNotice.jailDuration(minutes, history.expiresAt), history.createdAt);
		return true;
	}

	/** Варн от античита; на 3/3 — как у ручного варна: варны обнуляются и бан на 10 дней. */
	public static void autoWarn(ServerPlayerEntity target, PlayerRecord record, String reason) {
		if (target == null || record == null || target.getServer() == null) {
			return;
		}
		DataManager data = DataManager.get();
		MinecraftServer server = target.getServer();
		record.activeWarns = Math.max(0, record.activeWarns) + 1;
		PunishmentRecord history = addHistory(record, PunishmentType.WARN, true, reason, server.getCommandSource(), null);
		history.adminNick = "Античит";
		data.removeWarn(record.nickname);
		data.saveAll();
		int warns = record.activeWarns;
		Chat.broadcast(server, Chat.red("Античит выдал варн игроку " + record.nickname
				+ " по причине: " + reason + " (" + warns + "/3)"));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ANTICHEAT,
				"варн " + warns + "/3: " + reason, false);
		if (warns >= 3) {
			for (PunishmentRecord item : record.history) {
				if (item.type == PunishmentType.WARN && item.active) {
					item.active = false;
				}
			}
			data.removeWarn(record.nickname);
			record.activeWarns = 0;
			data.saveAll();
			int autoBanDays = 10;
			Chat.broadcast(server, Chat.red("Игрок " + record.nickname
					+ " набрал 3/3 варнов: варны обнулены, бан на " + autoBanDays + " " + Chat.daysWord(autoBanDays)));
			autoBan(target, record, autoBanDays, "3 предупреждения");
			return;
		}
		if (target.networkHandler != null) {
			PunishmentNotice.disconnect(target, PunishmentNotice.Kind.WARN,
					PunishmentNotice.displayAdmin("Античит", true), reason, PunishmentNotice.warnDuration(),
					System.currentTimeMillis());
		}
	}

	/** Пермабан фильтром ников; баны с nickFilter можно снять автоматически при ослаблении правил. */
	public static boolean autoNickBan(ServerPlayerEntity target, PlayerRecord record, String reason) {
		if (record == null) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
			return false;
		}
		MinecraftServer server = target == null ? null : target.getServer();
		if (server == null) {
			return false;
		}
		PunishmentRecord history = addHistory(record, PunishmentType.PERMABAN, true, reason, server.getCommandSource(), null);
		history.adminNick = ru.admincontrol.util.NickFilter.ADMIN;
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.permanent = true;
		entry.reason = reason;
		entry.adminNick = ru.admincontrol.util.NickFilter.ADMIN;
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		entry.nickFilter = true;
		data.addBan(entry);
		data.saveAll();
		Chat.broadcast(server, Chat.red("Фильтр ников навсегда заблокировал игрока " + record.nickname
				+ " по причине: " + reason));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ADMIN, "пермбан (ник): " + reason, false);
		if (target != null && target.networkHandler != null) {
			PunishmentNotice.disconnect(target, PunishmentNotice.Kind.PERMBAN,
					PunishmentNotice.displayAdmin(ru.admincontrol.util.NickFilter.ADMIN, true), reason,
					PunishmentNotice.permDuration(), history.createdAt);
		}
		return true;
	}

	public static boolean autoPermBan(ServerPlayerEntity target, PlayerRecord record, String reason) {
		if (record == null) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
			if (target != null && target.networkHandler != null) {
				PunishmentNotice.disconnect(target, PunishmentNotice.Kind.PERMBAN,
						PunishmentNotice.displayAdmin("Античит", true), reason,
						PunishmentNotice.permDuration(), System.currentTimeMillis());
			}
			return false;
		}
		MinecraftServer server = target == null ? null : target.getServer();
		if (server == null) {
			return false;
		}
		PunishmentRecord history = addHistory(record, PunishmentType.PERMABAN, true, reason, server.getCommandSource(), null);
		history.adminNick = "Античит";
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.permanent = true;
		entry.reason = reason;
		entry.adminNick = "Античит";
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		data.addBan(entry);
		data.saveAll();
		Chat.broadcast(server, Chat.red("Античит навсегда заблокировал игрока " + record.nickname
				+ " по причине: " + reason));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ANTICHEAT, "пермбан: " + reason, false);
		PunishmentNotice.disconnect(target, PunishmentNotice.Kind.PERMBAN,
				PunishmentNotice.displayAdmin("Античит", true), reason,
				PunishmentNotice.permDuration(), history.createdAt);
		return true;
	}

	public static boolean autoBan(ServerPlayerEntity target, PlayerRecord record, int days, String reason) {
		if (record == null) {
			return false;
		}
		DataManager data = DataManager.get();
		int banDays = Math.max(1, days);
		if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
			if (target != null && target.networkHandler != null) {
				PunishmentNotice.disconnect(target, PunishmentNotice.Kind.BAN,
						PunishmentNotice.displayAdmin("Античит", true), reason,
						PunishmentNotice.banDuration(banDays, null), System.currentTimeMillis());
			}
			return false;
		}
		net.minecraft.server.MinecraftServer server = target == null ? null : target.getServer();
		if (server == null) {
			return false;
		}
		PunishmentRecord history = addHistory(record, PunishmentType.BAN, true, reason, server.getCommandSource(), banDays);
		history.adminNick = "Античит";
		BanEntry entry = new BanEntry();
		entry.uuid = record.uuid;
		entry.nickname = record.nickname;
		entry.permanent = false;
		entry.reason = reason;
		entry.adminNick = "Античит";
		entry.createdAt = history.createdAt;
		entry.expiresAt = history.expiresAt;
		entry.recordId = history.id;
		data.addBan(entry);
		data.saveAll();
		Chat.broadcast(server, Chat.red("Античит заблокировал игрока " + record.nickname + " на "
				+ banDays + " " + Chat.daysWord(banDays) + " по причине: " + reason));
		AdminNotify.playerAction(target, ru.admincontrol.data.ActionKind.ANTICHEAT,
				"бан " + banDays + " дн: " + reason, false);
		PunishmentNotice.disconnect(target, PunishmentNotice.Kind.BAN,
				PunishmentNotice.displayAdmin("Античит", true), reason,
				PunishmentNotice.banDuration(banDays, history.expiresAt), history.createdAt);
		return true;
	}
}
