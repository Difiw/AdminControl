package ru.admincontrol.util;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Disconnect payload of the Common World: Expedition name blacklist. It is completely separate from
 * {@link PunishmentNotice} (own marker, own screen) and carries only what the player may see: kind, account name and
 * the stored listing time. The disconnect text is readable plain text for clients without the mod; the mod additionally
 * finds the marker in the hover event and shows the Expedition screen. The client treats the payload as untrusted:
 * every field is strictly validated and all other wording is fixed in the client.
 */
public final class CweNotice {
	public static final String MARKER = "CWE1\u001e";
	private static final char SEP = '\u001e';
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final long MIN_TIME = 946_684_800_000L, MAX_TIME = 4_102_444_800_000L; // 2000-01-01 .. 2100-01-01
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	/** RESERVED: the name is held for the story and no account exists. ACCOUNT: an existing account is blocked. */
	public enum Kind { RESERVED, ACCOUNT }
	public record Data(Kind kind, String name, long listedAt) {}

	private CweNotice() {}

	public static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

	public static String encode(Data d) { return d.kind().name() + SEP + d.name() + SEP + d.listedAt(); }

	/** Strict decoder: returns null for anything that is not exactly {@code KIND SEP name SEP millis}. */
	public static Data decode(String payload) {
		if (payload == null || payload.length() > 64) return null;
		String[] p = payload.split(String.valueOf(SEP), -1);
		if (p.length != 3 || !validName(p[1])) return null;
		Kind kind;
		long time;
		try { kind = Kind.valueOf(p[0]); time = Long.parseLong(p[2]); }
		catch (IllegalArgumentException e) { return null; }
		if (time < MIN_TIME || time > MAX_TIME) return null;
		return new Data(kind, p[1], time);
	}

	public static String formatDate(long millis) { return DATE.format(Instant.ofEpochMilli(millis)); }

	public static String fallback(Data d) {
		return "Аккаунт недоступен\n\n"
				+ "Имя аккаунта: " + d.name() + "\n"
				+ "Статус: " + (d.kind() == Kind.RESERVED ? "Имя зарезервировано (аккаунт не создан)" : "Аккаунт заблокирован") + "\n"
				+ "Причина: имя зарезервировано для сюжетного контента Common World: Expedition.\n"
				+ "Дата внесения: " + formatDate(d.listedAt()) + "\n\n"
				+ "Создание и вход под этим именем запрещены. Если вы считаете это ошибкой, обратитесь к администрации сервера.";
	}

	public static Text text(Data d) {
		return Text.literal(fallback(d)).styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(MARKER + encode(d)))));
	}

	/** Finds and validates the payload in a disconnect reason; null when this is not a blacklist notice. */
	public static Data parse(Text root) {
		if (root == null) return null;
		HoverEvent hover = root.getStyle().getHoverEvent();
		if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
			Text value = hover.getValue(HoverEvent.Action.SHOW_TEXT);
			String raw = value == null ? null : value.getString();
			if (raw != null && raw.startsWith(MARKER)) return decode(raw.substring(MARKER.length()));
		}
		for (Text child : root.getSiblings()) {
			Data found = parse(child);
			if (found != null) return found;
		}
		return null;
	}
}
