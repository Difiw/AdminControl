package ru.admincontrol.client;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanTags {
	private static final Map<String, String> BY_NICK = new ConcurrentHashMap<>();

	private ClanTags() {
	}

	public static void clear() {
		BY_NICK.clear();
	}

	public static void put(String nick, String clan) {
		if (nick == null || nick.isBlank()) {
			return;
		}
		String key = nick.toLowerCase(Locale.ROOT);
		if (clan == null || clan.isBlank()) {
			BY_NICK.remove(key);
			return;
		}
		BY_NICK.put(key, clan);
	}

	public static String of(String nick) {
		if (nick == null || nick.isBlank()) {
			return "";
		}
		return BY_NICK.getOrDefault(nick.toLowerCase(Locale.ROOT), "");
	}
}
