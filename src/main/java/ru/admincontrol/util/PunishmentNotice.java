package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.BanEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PunishmentNotice {
	public static final String MARKER = "ACP1\u001e";
	private static final char SEP = '\u001e';
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	public enum Kind {
		WARN, KICK, BAN, PERMBAN, JAIL
	}

	public final Kind kind;
	public final String player;
	public final String admin;
	public final String reason;
	public final String duration;
	public final String issued;
	public final boolean reconnect;

	public PunishmentNotice(Kind kind, String player, String admin, String reason, String duration,
			String issued, boolean reconnect) {
		this.kind = kind;
		this.player = player == null ? "" : player;
		this.admin = admin == null ? "" : admin;
		this.reason = reason == null ? "" : reason;
		this.duration = duration == null ? "" : duration;
		this.issued = issued == null ? "" : issued;
		this.reconnect = reconnect;
	}

	public String title() {
		return switch (this.kind) {
			case WARN -> "Варн";
			case KICK -> "Кик";
			case BAN -> "Бан";
			case PERMBAN -> "Перманентный бан";
			case JAIL -> "Jail";
		};
	}

	public boolean showDuration() {
		return true;
	}

	public static String formatDate(Long millis) {
		if (millis == null || millis <= 0L) {
			return "—";
		}
		return DATE.format(Instant.ofEpochMilli(millis));
	}

	public static String displayAdmin(ServerCommandSource source) {
		return displayAdmin(CommandUtil.adminName(source), false);
	}

	public static String displayAdmin(String raw, boolean anticheat) {
		if (anticheat) {
			return "Античит (сервер)";
		}
		if (raw == null || raw.isBlank()) {
			return "сервер консол";
		}
		String t = raw.trim();
		if (t.equalsIgnoreCase("console") || t.equalsIgnoreCase("Консоль") || t.equalsIgnoreCase("Server")
				|| t.equalsIgnoreCase("сервер консол")) {
			return "сервер консол";
		}
		if (t.equalsIgnoreCase("Античит") || t.toLowerCase().startsWith("anticheat")) {
			return "Античит (сервер)";
		}
		return t;
	}

	public static String warnDuration() {
		return "0 секунд";
	}

	public static String permDuration() {
		return "никогда";
	}

	public static String kickDuration() {
		return "—";
	}

	public static String jailDuration(int minutes, Long expiresAt) {
		int n = Math.max(1, minutes);
		return n + " " + Chat.minutesWord(n) + " (до " + formatDate(expiresAt) + ")";
	}

	public static String jailDuration(BanEntry entry) {
		if (entry == null) {
			return "—";
		}
		int minutes = 1;
		if (entry.createdAt > 0L && entry.expiresAt != null && entry.expiresAt > entry.createdAt) {
			minutes = (int) Math.max(1L, Math.round((entry.expiresAt - entry.createdAt) / 60_000.0));
		}
		return jailDuration(minutes, entry.expiresAt);
	}

	public static String banDuration(int days, Long expiresAt) {
		int n = Math.max(1, days);
		return n + " " + Chat.daysWord(n) + " (до " + formatDate(expiresAt) + ")";
	}

	public static String banDuration(BanEntry entry) {
		if (entry == null) {
			return permDuration();
		}
		if (entry.permanent) {
			return permDuration();
		}
		int days = 1;
		if (entry.createdAt > 0L && entry.expiresAt != null && entry.expiresAt > entry.createdAt) {
			days = (int) Math.max(1L, Math.round((entry.expiresAt - entry.createdAt) / 86_400_000.0));
		}
		return banDuration(days, entry.expiresAt);
	}

	public static Text text(Kind kind, String player, String admin, String reason, String duration, long issuedAt) {
		boolean reconnect = kind == Kind.WARN || kind == Kind.KICK;
		String dur = duration == null || duration.isBlank() ? kickDuration() : duration;
		String issued = formatDate(issuedAt <= 0L ? System.currentTimeMillis() : issuedAt);
		PunishmentNotice notice = new PunishmentNotice(kind, safe(player, 32), displayAdmin(admin, false),
				safe(reason, 400), safe(dur, 80), issued, reconnect);
		MutableText visible = Chat.red(fallback(notice));
		return visible.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				Text.literal(MARKER + notice.encode()))));
	}

	public static Text fromEntry(String playerNick, BanEntry entry) {
		if (entry == null) {
			return Chat.red("Вам выдано наказание.");
		}
		Kind kind;
		String duration;
		if (entry.jail) {
			kind = Kind.JAIL;
			duration = jailDuration(entry);
		} else if (entry.permanent) {
			kind = Kind.PERMBAN;
			duration = permDuration();
		} else {
			kind = Kind.BAN;
			duration = banDuration(entry);
		}
		long issued = entry.createdAt > 0L ? entry.createdAt : System.currentTimeMillis();
		return text(kind, playerNick, displayAdmin(entry.adminNick, false), entry.reason, duration, issued);
	}

	public static void disconnect(ServerPlayerEntity player, Kind kind, String admin, String reason,
			String duration, long issuedAt) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		String nick = player.getGameProfile().getName();
		player.networkHandler.disconnect(text(kind, nick, admin, reason, duration, issuedAt));
	}

	public static void disconnectBan(ServerPlayerEntity player, BanEntry entry) {
		if (player == null || player.networkHandler == null || entry == null) {
			return;
		}
		player.networkHandler.disconnect(fromEntry(player.getGameProfile().getName(), entry));
	}

	public static PunishmentNotice parse(Text root) {
		if (root == null) {
			return null;
		}
		HoverEvent hover = root.getStyle().getHoverEvent();
		if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
			Text value = hover.getValue(HoverEvent.Action.SHOW_TEXT);
			if (value != null) {
				String raw = value.getString();
				if (raw != null && raw.startsWith(MARKER)) {
					return decode(raw.substring(MARKER.length()));
				}
			}
		}
		for (Text child : root.getSiblings()) {
			PunishmentNotice found = parse(child);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private String encode() {
		return this.kind.name()
				+ SEP + clean(this.player)
				+ SEP + clean(this.admin)
				+ SEP + clean(this.reason)
				+ SEP + clean(this.duration)
				+ SEP + clean(this.issued)
				+ SEP + (this.reconnect ? "1" : "0");
	}

	private static PunishmentNotice decode(String payload) {
		if (payload == null || payload.isBlank()) {
			return null;
		}
		String[] p = payload.split(String.valueOf(SEP), -1);
		if (p.length < 7) {
			return null;
		}
		Kind kind;
		try {
			kind = Kind.valueOf(p[0]);
		} catch (IllegalArgumentException e) {
			return null;
		}
		return new PunishmentNotice(kind, p[1], p[2], p[3], p[4], p[5], "1".equals(p[6]));
	}

	private static String fallback(PunishmentNotice n) {
		return switch (n.kind) {
			case WARN -> "Вы получили предупреждение. Причина: " + n.reason;
			case KICK -> "Вас отключили от сервера. Причина: " + n.reason;
			case BAN -> "Вы заблокированы. " + n.duration + ". Причина: " + n.reason;
			case PERMBAN -> "Вы навсегда заблокированы. Причина: " + n.reason;
			case JAIL -> "Вас отправили в jail. " + n.duration + ". Причина: " + n.reason;
		};
	}

	private static String safe(String value, int max) {
		String t = value == null ? "" : value.trim();
		if (t.length() > max) {
			return t.substring(0, max);
		}
		return t;
	}

	private static String clean(String value) {
		return value == null ? "" : value.replace(SEP, ' ').replace('\n', ' ');
	}
}
