package ru.admincontrol.util;

import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.IpBan;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** IP-адреса игроков: совпадения и блокировка. Порт не учитывается — сравнивается только адрес. */
public final class IpService {
	private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

	private IpService() {
	}

	/** «/1.2.3.4:5555» → «1.2.3.4», «[::1]:25565» → «::1». */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String ip = raw.trim().toLowerCase(Locale.ROOT);
		if (ip.startsWith("/")) {
			ip = ip.substring(1);
		}
		if (ip.startsWith("[")) {
			int end = ip.indexOf(']');
			return end > 0 ? ip.substring(1, end) : ip.substring(1);
		}
		int colon = ip.indexOf(':');
		if (colon > 0 && colon == ip.lastIndexOf(':')) {
			ip = ip.substring(0, colon);
		}
		return ip;
	}

	public static boolean looksLikeIp(String raw) {
		String ip = normalize(raw);
		var m = IPV4.matcher(ip);
		if (m.matches()) {
			for (int i = 1; i <= 4; i++) {
				if (Integer.parseInt(m.group(i)) > 255) {
					return false;
				}
			}
			return true;
		}
		return ip.indexOf(':') >= 0 && ip.matches("[0-9a-f:.]+");
	}

	public static List<String> ipsOf(PlayerRecord record) {
		Set<String> out = new LinkedHashSet<>();
		if (record != null && record.loginIps != null) {
			for (String ip : record.loginIps) {
				String n = normalize(ip);
				if (!n.isBlank()) {
					out.add(n);
				}
			}
		}
		return new ArrayList<>(out);
	}

	/** Все игроки (не NPC), заходившие с этого IP. */
	public static List<PlayerRecord> playersWithIp(String ip) {
		String key = normalize(ip);
		List<PlayerRecord> out = new ArrayList<>();
		DataManager data = DataManager.get();
		if (data == null || key.isBlank()) {
			return out;
		}
		for (PlayerRecord record : data.records()) {
			if (record != null && !record.nps && ipsOf(record).contains(key)) {
				out.add(record);
			}
		}
		out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.nickname, b.nickname));
		return out;
	}

	private static List<IpBan> bans() {
		var s = DataManager.get().settings();
		if (s.ipBans == null) {
			s.ipBans = new ArrayList<>();
		}
		return s.ipBans;
	}

	public static Optional<IpBan> find(String ip) {
		String key = normalize(ip);
		if (key.isBlank() || DataManager.get() == null) {
			return Optional.empty();
		}
		for (IpBan ban : bans()) {
			if (ban != null && key.equals(normalize(ban.ip))) {
				return Optional.of(ban);
			}
		}
		return Optional.empty();
	}

	/** true — адрес заблокирован заново, false — уже был в бане. */
	public static boolean ban(String ip, String reason, String admin, String sourceNick) {
		String key = normalize(ip);
		if (key.isBlank() || find(key).isPresent()) {
			return false;
		}
		IpBan ban = new IpBan();
		ban.ip = key;
		ban.reason = reason == null ? "" : reason;
		ban.adminNick = admin == null ? "" : admin;
		ban.sourceNick = sourceNick == null ? "" : sourceNick;
		ban.createdAt = System.currentTimeMillis();
		bans().add(ban);
		DataManager.get().saveAll();
		return true;
	}

	public static boolean unban(String ip) {
		String key = normalize(ip);
		boolean removed = bans().removeIf(ban -> ban != null && key.equals(normalize(ban.ip)));
		if (removed) {
			DataManager.get().saveAll();
		}
		return removed;
	}
}
