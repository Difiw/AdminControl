package ru.admincontrol.util;

import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

public final class NickColors {
	public static final int ADMIN = 0x3498DB;
	public static final int CURATOR = 0x6F0735;
	public static final int ZGA = 0xE91E63;
	public static final int GA = 0xDD1F40;
	public static final int BLOGGER = 0xFFD700;

	public static final int[] PALETTE = {
			ADMIN, CURATOR, ZGA, GA
	};

	private NickColors() {
	}

	public static int of(int level) {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		if (CustomRanks.byLevel(level) != null) {
			return CustomRanks.color(level, ADMIN);
		}
		if (level >= AdminRanks.GA) {
			return rgb(s == null ? GA : s.colorGa, GA);
		}
		if (level >= AdminRanks.ZGA) {
			return rgb(s == null ? ZGA : s.colorZga, ZGA);
		}
		if (AdminRanks.isBlogger(level)) {
			return blogger();
		}
		if (level >= AdminRanks.CURATOR) {
			return rgb(s == null ? CURATOR : s.colorCurator, CURATOR);
		}
		if (level >= AdminRanks.MOD) {
			return rgb(s == null ? ADMIN : s.colorAdmin, ADMIN);
		}
		return 0xFFFFFF;
	}

	public static int blogger() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return rgb(s == null ? BLOGGER : s.colorBlogger, BLOGGER);
	}

	public static String hex(int rgb) {
		return String.format("#%06x", rgb & 0xFFFFFF);
	}

	public static Integer parse(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
		if (t.startsWith("0x")) {
			t = t.substring(2);
		}
		if (t.startsWith("#")) {
			t = t.substring(1);
		}
		if (t.length() != 6) {
			return null;
		}
		for (int i = 0; i < 6; i++) {
			if (Character.digit(t.charAt(i), 16) < 0) {
				return null;
			}
		}
		return Integer.parseInt(t, 16);
	}

	private static int rgb(int value, int fallback) {
		int v = value & 0xFFFFFF;
		return v == 0 ? fallback : v;
	}
}
