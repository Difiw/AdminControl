package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClanTop {
	private ClanTop() {
	}

	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		ensureWeeks();
		int prize = prize();
		out.add("Неделя " + PlayFeatures.weekKey()
				+ "  (победа в МП = 10, победа в битве кланов = 30)");
		out.add("Награда клану-победителю в казну: " + Money.wc(prize)
				+ (prize == 0 ? "  (выкл)" : ""));
		out.add("");
		List<ClanRecord> ranked = ranked();
		int n = 0;
		for (ClanRecord clan : ranked) {
			n++;
			out.add(n + ". " + clan.name + "  —  " + score(clan)
					+ "  (МП " + clan.weekMpWins + ", битвы " + clan.weekWarWins + ")  ·  всего " + total(clan));
			if (n >= 10) {
				break;
			}
		}
		if (n == 0) {
			out.add("Пока никого нет.");
		}
		return out;
	}

	public static void mpWin(MinecraftServer server, ServerPlayerEntity player, Set<String> once) {
		ClanRecord clan = ClanService.of(player);
		if (clan == null) {
			return;
		}
		if (once != null && !once.add(clan.id)) {
			return;
		}
		ensureWeek(clan);
		clan.weekMpWins++;
		clan.weekPoints += 10;
		clan.totalMpWins++;
		clan.totalPoints += 10;
		pay(server, clan, "победа в МП");
	}

	public static void warWin(MinecraftServer server, ClanRecord clan) {
		if (clan == null) {
			return;
		}
		ensureWeek(clan);
		clan.weekWarWins++;
		clan.weekPoints += 30;
		clan.totalWarWins++;
		clan.totalPoints += 30;
		pay(server, clan, "победа в битве кланов");
	}

	public static void ensureWeeks() {
		if (DataManager.get() == null) {
			return;
		}
		for (ClanRecord clan : ClanService.clans()) {
			ensureWeek(clan);
		}
	}

	private static void pay(MinecraftServer server, ClanRecord clan, String why) {
		int want = prize();
		int added = 0;
		if (want > 0) {
			int cap = ClanService.treasuryCap(clan);
			added = Math.min(want, Math.max(0, cap - clan.treasury));
			clan.treasury += added;
		}
		if (DataManager.get() != null) {
			DataManager.get().markDirty();
			DataManager.get().saveIfDirty();
		}
		if (server != null) {
			String extra = added > 0 ? "  +" + Money.wc(added) + " в казну" : (want > 0 ? "  (казна полна)" : "");
			Chat.broadcast(server, Chat.gold("Клан «" + clan.name + "»: " + why + extra
					+ ". Очки недели: " + score(clan) + "."));
		}
	}

	private static int prize() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return s == null ? 5000 : Math.max(0, s.clanWinWc);
	}

	private static void ensureWeek(ClanRecord clan) {
		if (clan == null) {
			return;
		}
		if (clan.totalPoints < clan.weekPoints) {
			clan.totalPoints = clan.weekPoints;
			clan.totalMpWins = Math.max(clan.totalMpWins, clan.weekMpWins);
			clan.totalWarWins = Math.max(clan.totalWarWins, clan.weekWarWins);
		}
		String key = PlayFeatures.weekKey();
		if (key.equals(clan.weekKey)) {
			return;
		}
		clan.weekKey = key;
		clan.weekPoints = 0;
		clan.weekMpWins = 0;
		clan.weekWarWins = 0;
	}

	public static int score(ClanRecord clan) {
		return clan == null ? 0 : Math.max(0, clan.weekPoints);
	}

	public static int total(ClanRecord clan) {
		return clan == null ? 0 : Math.max(0, clan.totalPoints);
	}

	/** Кланы с очками за всё время, по убыванию. */
	public static List<ClanRecord> rankedTotal() {
		ensureWeeks();
		List<ClanRecord> list = new ArrayList<>();
		for (ClanRecord clan : ClanService.clans()) {
			if (clan != null && total(clan) > 0) {
				list.add(clan);
			}
		}
		list.sort(Comparator.comparingInt(ClanTop::total).reversed());
		return list;
	}

	public static List<ClanRecord> rankedWeek() {
		ensureWeeks();
		return ranked();
	}

	private static List<ClanRecord> ranked() {
		List<ClanRecord> list = new ArrayList<>();
		String key = PlayFeatures.weekKey();
		for (ClanRecord clan : ClanService.clans()) {
			if (clan == null || !key.equals(clan.weekKey) || score(clan) <= 0) {
				continue;
			}
			list.add(clan);
		}
		list.sort(Comparator.comparingInt(ClanTop::score).reversed());
		return list;
	}

	public static Set<String> newOnce() {
		return new HashSet<>();
	}
}
