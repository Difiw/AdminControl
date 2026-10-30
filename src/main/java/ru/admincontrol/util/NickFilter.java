package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.ModerationService;
import ru.admincontrol.data.BanEntry;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Фильтр ников: запрещённые символы → авто-пермабан «Некорректный никнейм»;
 * белый список; снятие бана при ослаблении правил (/nickfilter).
 */
public final class NickFilter {
	public static final String REASON = "Некорректный никнейм";
	public static final String ADMIN = "Фильтр ников";
	/** Стандартный набор: точка, запятая, знаки, кавычки, скобки и прочая пунктуация. */
	public static final List<String> DEFAULT = List.of(
			".", ",", "!", "?", "@", "\"", "'", "`", "(", ")", "[", "]", "{", "}", "<", ">",
			"#", "$", "%", "^", "&", "*", "/", "\\", "|", "~", ";", ":", "+", "=", " "
	);

	private NickFilter() {
	}

	private static ModSettings settings() {
		return DataManager.get() == null ? null : DataManager.get().settings();
	}

	public static List<String> forbidden() {
		ModSettings s = settings();
		if (s == null) {
			return DEFAULT;
		}
		if (s.nickForbidden == null) {
			s.nickForbidden = new ArrayList<>(DEFAULT);
		}
		return s.nickForbidden;
	}

	public static List<String> whitelist() {
		ModSettings s = settings();
		if (s == null) {
			return List.of();
		}
		if (s.nickWhitelist == null) {
			s.nickWhitelist = new ArrayList<>();
		}
		return s.nickWhitelist;
	}

	public static boolean whitelisted(String nick) {
		if (nick == null || nick.isBlank()) {
			return false;
		}
		String want = nick.trim().toLowerCase(Locale.ROOT);
		for (String entry : whitelist()) {
			if (entry != null && want.equals(entry.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/** Какие запрещённые фрагменты есть в нике; пусто — ник допустим. */
	public static List<String> hits(String nick) {
		List<String> out = new ArrayList<>();
		if (nick == null || nick.isEmpty() || whitelisted(nick)) {
			return out;
		}
		for (String frag : forbidden()) {
			if (frag != null && !frag.isEmpty() && nick.contains(frag)) {
				out.add(frag);
			}
		}
		return out;
	}

	public static boolean allowed(String nick) {
		return hits(nick).isEmpty();
	}

	/** null — добавлен, иначе причина отказа. */
	public static String addForbidden(String raw) {
		String frag = normalizeFrag(raw);
		if (frag == null) {
			return "Укажите символ или фрагмент (например ! или []).";
		}
		List<String> list = forbidden();
		for (String existing : list) {
			if (frag.equals(existing)) {
				return "«" + display(frag) + "» уже в списке запрещённых.";
			}
		}
		list.add(frag);
		DataManager.get().saveAll();
		return null;
	}

	/** null — убран. После снятия вызывайте {@link #liftInvalidated()}. */
	public static String removeForbidden(String raw) {
		String frag = normalizeFrag(raw);
		if (frag == null) {
			return "Укажите символ или фрагмент из списка.";
		}
		List<String> list = forbidden();
		boolean removed = list.removeIf(frag::equals);
		if (!removed) {
			return "«" + display(frag) + "» нет в списке запрещённых.";
		}
		DataManager.get().saveAll();
		return null;
	}

	public static void resetForbidden() {
		ModSettings s = settings();
		if (s == null) {
			return;
		}
		s.nickForbidden = new ArrayList<>(DEFAULT);
		DataManager.get().saveAll();
	}

	/** null — добавлен в белый список. */
	public static String addWhitelist(String nick) {
		String n = nick == null ? "" : nick.trim();
		if (n.isEmpty() || n.length() > 32) {
			return "Укажите ник (до 32 символов).";
		}
		List<String> list = whitelist();
		for (String existing : list) {
			if (existing != null && existing.equalsIgnoreCase(n)) {
				return "«" + n + "» уже в белом списке.";
			}
		}
		list.add(n);
		DataManager.get().saveAll();
		return null;
	}

	public static String removeWhitelist(String nick) {
		String n = nick == null ? "" : nick.trim();
		if (n.isEmpty()) {
			return "Укажите ник.";
		}
		List<String> list = whitelist();
		boolean removed = list.removeIf(e -> e != null && e.equalsIgnoreCase(n));
		if (!removed) {
			return "«" + n + "» нет в белом списке.";
		}
		DataManager.get().saveAll();
		return null;
	}

	private static String normalizeFrag(String raw) {
		if (raw == null) {
			return null;
		}
		String frag = raw;
		if ((frag.startsWith("\"") && frag.endsWith("\"") && frag.length() >= 2)
				|| (frag.startsWith("'") && frag.endsWith("'") && frag.length() >= 2)) {
			frag = frag.substring(1, frag.length() - 1);
		}
		return frag.isEmpty() || frag.length() > 16 ? null : frag;
	}

	public static String display(String frag) {
		if (frag == null) {
			return "";
		}
		if (" ".equals(frag)) {
			return "пробел";
		}
		return frag;
	}

	public static String listLabel(List<String> frags) {
		if (frags == null || frags.isEmpty()) {
			return "пусто";
		}
		StringBuilder out = new StringBuilder();
		for (String frag : frags) {
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append('«').append(display(frag)).append('»');
		}
		return out.toString();
	}

	/**
	 * При входе: если ник с запрещёнными символами и не в белом списке — пермабан и кик.
	 * @return true — игрока отключили
	 */
	public static boolean enforce(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || NpcService.isNpc(player) || DataManager.get() == null) {
			return false;
		}
		String nick = player.getGameProfile().getName();
		if (allowed(nick)) {
			return false;
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		var existing = DataManager.get().findActiveBan(record.uuidOrNull(), record.nickname);
		if (existing.isPresent()) {
			BanEntry ban = existing.get();
			if (ban.nickFilter) {
				PunishmentNotice.disconnect(player, PunishmentNotice.Kind.PERMBAN,
						PunishmentNotice.displayAdmin(ADMIN, true), REASON,
						PunishmentNotice.permDuration(), ban.createdAt);
				return true;
			}
			return false;
		}
		return ModerationService.autoNickBan(player, record, REASON);
	}

	/** Снять nickFilter-баны, если ник снова допустим (после remove/reset/whitelist). */
	public static int liftInvalidated() {
		DataManager data = DataManager.get();
		if (data == null) {
			return 0;
		}
		int lifted = 0;
		for (BanEntry ban : data.nickFilterBans()) {
			if (ban.nickname == null || !allowed(ban.nickname)) {
				continue;
			}
			if (data.removeBan(ban.nickname, true, false).isEmpty()) {
				continue;
			}
			PlayerRecord record = data.findByNick(ban.nickname).orElse(null);
			if (record != null) {
				for (var item : record.history) {
					if (item.type == PunishmentType.PERMABAN && item.active
							&& (REASON.equals(item.reason) || ADMIN.equals(item.adminNick))) {
						item.active = false;
					}
				}
				ru.admincontrol.data.PunishmentRecord history = new ru.admincontrol.data.PunishmentRecord();
				history.type = PunishmentType.UNPERMABAN;
				history.active = false;
				history.reason = "Фильтр ников: символы снова разрешены";
				history.adminNick = ADMIN;
				record.history.add(history);
			}
			lifted++;
		}
		if (lifted > 0) {
			data.saveAll();
			ru.admincontrol.event.PlayerTracker.clearBanPass();
		}
		return lifted;
	}
}
