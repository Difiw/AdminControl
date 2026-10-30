import ru.admincontrol.lumira.LumiraIds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Чёткая пиксель-графика для «Рощи Люмиры»: три тона вместо шума, 1-пиксельная обводка, правильный наклон
 * головок инструментов (как в ванильном Minecraft). Запускается ПОСЛЕ LumiraGen и перезаписывает текстуры.
 * java -cp tmp tools/LumiraArt.java src/main/resources
 */
public class LumiraArt {
	static Path tex;
	static final int OUT = 0xFF12121A;

	public static void main(String[] args) throws Exception {
		tex = Path.of(args[0]).resolve("assets/admin_control/textures");
		if (args.length > 1 && args[1].equals("armor")) {
			toolsAndArmor();
			System.out.println("ART OK (armor)");
			return;
		}
		ores();
		metalBlocks();
		trees();
		stations();
		items();
		toolsAndArmor();
		necklacesAndCoins();
		tokens();
		hiddenIcon();
		System.out.println("ART OK");
	}

	// ---------- примитивы ----------
	static BufferedImage img(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	static void save(String rel, BufferedImage im) throws Exception {
		Path p = tex.resolve(rel + ".png");
		Files.createDirectories(p.getParent());
		ImageIO.write(im, "png", p.toFile());
	}

	static int c(int rgb) {
		return 0xFF000000 | rgb;
	}

	static int shade(int rgb, double f) {
		int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	static double n(int x, int y, int s) {
		long h = x * 374761393L + y * 668265263L + s * 2246822519L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	static int seed(String s) {
		return s.hashCode() & 0x7FFFFFFF;
	}

	/** Тон из трёх: тёмный, основной, светлый — по «блочному» шуму, чтобы текстура читалась чётко. */
	static int tone(int base, double v) {
		return v < 0.28 ? shade(base, 0.78) : v < 0.78 ? c(base) : shade(base, 1.18);
	}

	static double blockNoise(int x, int y, int cell, int s) {
		double a = n(x / cell, y / cell, s), b = n((x + 3) / cell, (y + 5) / cell, s + 7);
		return a * 0.6 + b * 0.4;
	}

	static void put(BufferedImage im, int x, int y, int col) {
		if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
			im.setRGB(x, y, col);
		}
	}

	static boolean[][] mask() {
		return new boolean[16][16];
	}

	static void add(boolean[][] m, int... xy) {
		for (int i = 0; i + 1 < xy.length; i += 2) {
			if (xy[i] >= 0 && xy[i] < 16 && xy[i + 1] >= 0 && xy[i + 1] < 16) {
				m[xy[i]][xy[i + 1]] = true;
			}
		}
	}

	static boolean has(boolean[][] m, int x, int y) {
		return x >= 0 && y >= 0 && x < 16 && y < 16 && m[x][y];
	}

	/** Рисует маску с фаской (свет сверху-слева, тень снизу-справа) и тёмной внешней обводкой. */
	static void layer(BufferedImage im, boolean[][] m, int main, int light, int dark, int outline) {
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				if (!m[x][y] && (has(m, x - 1, y) || has(m, x + 1, y) || has(m, x, y - 1) || has(m, x, y + 1))) {
					put(im, x, y, outline);
				}
			}
		}
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				if (m[x][y]) {
					int col = main;
					if (!has(m, x - 1, y) || !has(m, x, y - 1)) col = light;
					else if (!has(m, x + 1, y) || !has(m, x, y + 1)) col = dark;
					put(im, x, y, col);
				}
			}
		}
	}

	static BufferedImage stone(int base, String sd, boolean deep) {
		BufferedImage im = img(16, 16);
		int s = seed(sd);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double v = blockNoise(x, y, 3, s);
				int col = deep ? shade(base, 0.8) : base;
				im.setRGB(x, y, tone(col & 0xFFFFFF, v));
			}
		}
		return im;
	}

	// ---------- руды ----------
	static final Map<String, int[]> ORE = new HashMap<>();

	static {
		ORE.put("spark_coal_ore", new int[]{0x74695E, 0x141418, 0x3A3A44, 0xF29A2E});
		ORE.put("lumirite_ore", new int[]{0x5A616C, 0x39D9C8, 0x1E8F86, 0xC2FFF8});
		ORE.put("zaremed_ore", new int[]{0x6E5847, 0xE8883A, 0x9A4E1A, 0xFFD27A});
		ORE.put("twilight_iron_ore", new int[]{0x36323F, 0x8F78D0, 0x4A3F78, 0xC9B8FF});
		ORE.put("starsilver_ore", new int[]{0x4C5A70, 0xE4ECF8, 0x4F86FF, 0xFFFFFF});
		ORE.put("dew_quartz_ore", new int[]{0x8E9AA0, 0x9EF2D4, 0x4FB896, 0xF2FFFA});
		ORE.put("echolite_ore", new int[]{0x5E5971, 0xB48EF2, 0x6A3FB8, 0xE6D4FF});
		ORE.put("heart_crystal_ore", new int[]{0x2F2B31, 0xFF7FA8, 0xB8456C, 0xFFD27A});
		ORE.put("jarolite_ore", new int[]{0x67635F, 0xB02A2A, 0x6E1414, 0xF4E2B6});
	}

	static void ores() throws Exception {
		int[][] blobs = {{2, 2, 3, 2}, {9, 1, 3, 3}, {5, 6, 3, 2}, {11, 7, 3, 3}, {1, 10, 3, 3}, {7, 11, 4, 2}, {12, 12, 3, 2}, {3, 13, 2, 2}};
		for (LumiraIds.Ore o : LumiraIds.ORES) {
			int[] p = ORE.get(o.id());
			BufferedImage im = stone(p[0], o.id(), o.material().equals("deepslate"));
			int s = seed(o.id());
			for (int[] b : blobs) {
				int bx = Math.max(0, Math.min(16 - b[2], b[0] + (int) (n(b[0], b[1], s) * 2) - 1));
				int by = Math.max(0, Math.min(16 - b[3], b[1] + (int) (n(b[1], b[0], s + 1) * 2) - 1));
				boolean[][] m = mask();
				for (int dx = 0; dx < b[2]; dx++) {
					for (int dy = 0; dy < b[3]; dy++) {
						if (!((dx == 0 || dx == b[2] - 1) && (dy == 0 || dy == b[3] - 1) && b[2] > 2 && b[3] > 2)) {
							add(m, bx + dx, by + dy);
						}
					}
				}
				for (int x = 0; x < 16; x++) {
					for (int y = 0; y < 16; y++) {
						if (m[x][y]) {
							int col = p[1];
							if (!has(m, x - 1, y) || !has(m, x, y - 1)) col = p[3];
							else if (!has(m, x + 1, y) || !has(m, x, y + 1)) col = p[2];
							put(im, x, y, c(col));
						}
					}
				}
			}
			save("block/" + o.id(), im);
		}
	}

	static void metalBlocks() throws Exception {
		int[][] cols = {{0x58D3C7, 0xC8FFFA, 0x2C7C75}, {0xD27A3C, 0xFFC476, 0x8A4A1E}, {0x4A4358, 0x9B8BC8, 0x25212D}, {0xDCE6F2, 0xFFFFFF, 0x8E9BB0}};
		for (int i = 0; i < 4; i++) {
			BufferedImage im = img(16, 16);
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					int col;
					if (x == 0 || y == 0 || x == 15 || y == 15) col = c(cols[i][2]);
					else if (x == 1 || y == 1) col = c(cols[i][1]);
					else if (x == 14 || y == 14) col = shade(cols[i][2], 1.3);
					else if (x == 7 || x == 8 || y == 7 || y == 8) col = c(cols[i][2]);
					else col = (x + y) % 7 == 0 ? c(cols[i][1]) : c(cols[i][0]);
					im.setRGB(x, y, col);
				}
			}
			save("block/" + LumiraIds.METALS.get(i).id() + "_block", im);
		}
	}

	// ---------- деревья ----------
	static BufferedImage bark(LumiraIds.Tree t, boolean stripped) {
		BufferedImage im = img(16, 16);
		int base = stripped ? blend(t.bark(), t.ring(), 0.5) : t.bark();
		int dark = stripped ? shade(base, 0.82) : t.barkDark();
		int light = shade(base, 1.2);
		int s = seed(t.id() + stripped);
		for (int x = 0; x < 16; x++) {
			boolean groove = n(x / 1, 0, s) > 0.62;
			for (int y = 0; y < 16; y++) {
				int col = groove ? dark : base;
				if (!groove && n(x, y / 4, s + 2) > 0.8) col = light;
				if (groove && n(x, y, s + 4) > 0.85) col = base;
				if (!stripped && t.id().equals("star_birch") && n(x / 2, y / 2, s + 6) > 0.8) col = t.barkDark();
				if (!stripped && t.id().equals("crimson_elm") && y % 5 == 2 && n(x, y, s) > 0.35) col = t.barkDark();
				if (!stripped && t.id().equals("zarelist") && n(x, y, s + 8) > 0.92) col = t.ring();
				im.setRGB(x, y, c(col & 0xFFFFFF));
			}
		}
		return im;
	}

	static int blend(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	static BufferedImage rings(LumiraIds.Tree t, boolean stripped) {
		BufferedImage im = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				int d = (int) Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
				int col;
				if (d >= 7) col = stripped ? shade(t.bark(), 1.05) : t.barkDark();
				else if (d == 6) col = shade(t.ring(), 0.75);
				else col = d % 2 == 0 ? t.ring() : shade(t.ring(), 0.82);
				if (t.id().equals("lumira_willow") && d < 6 && d % 2 == 1) col = blend(t.ring(), 0x6FB6E8, 0.55);
				if (t.id().equals("malachite_sequoia") && d == 6) col = 0x3F8F4F;
				if (d == 0 || (d == 1 && (x + y) % 2 == 0)) col = shade(t.ring(), 0.6);
				im.setRGB(x, y, c(col & 0xFFFFFF));
			}
		}
		return im;
	}

	static BufferedImage planks(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		int s = seed(t.id() + "pl");
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				int row = y / 4;
				int col = t.planks();
				if (y % 4 == 3) col = t.planksDark();
				else if (y % 4 == 0) col = shade(t.planks(), 1.15);
				int seam = (row % 2 == 0) ? 5 : 11;
				if (x == seam && y % 4 != 3) col = t.planksDark();
				if (y % 4 == 1 && n(x / 3 + row, row, s) > 0.8) col = shade(col, 0.9);
				if (t.id().equals("star_birch") && y % 4 == 2 && n(x / 2, row, s) > 0.75) col = 0x8FA7D6;
				im.setRGB(x, y, c(col & 0xFFFFFF));
			}
		}
		return im;
	}

	static BufferedImage leaves(LumiraIds.Tree t, int variant) {
		BufferedImage im = img(16, 16);
		int s = seed(t.id() + "lf" + variant);
		double hole = t.id().equals("moon_ash") ? 0.2 : t.id().equals("lumira_willow") ? 0.16 : 0.08;
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double v = blockNoise(x, y, 2, s);
				if (variant != 1 && n(x, y, s + 9) < hole && v < 0.45) continue;
				int base = v < 0.35 ? t.leafDark() : t.leaf();
				int col = v > 0.8 ? (int) (shade(base, 1.22) & 0xFFFFFF) : base;
				if (variant == 1) col = v > 0.5 ? (int) (shade(t.leaf(), 1.1) & 0xFFFFFF) : t.leaf();
				if (t.id().equals("star_birch") && n(x, y, s + 6) > 0.94) col = 0xFFFFFF;
				im.setRGB(x, y, c(col));
			}
		}
		return im;
	}

	static BufferedImage sapling(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		boolean[][] stem = mask();
		for (int y = 8; y < 16; y++) add(stem, 7, y, 8, y);
		boolean[][] leaf = mask();
		int[][] pts;
		switch (t.id()) {
			case "lumira_willow" -> pts = new int[][]{{6, 6}, {7, 6}, {8, 6}, {9, 6}, {5, 7}, {10, 7}, {5, 8}, {5, 9}, {10, 8}, {10, 9}, {10, 10}, {7, 5}, {8, 5}};
			case "twilight_cedar", "malachite_sequoia" -> pts = new int[][]{{7, 2}, {8, 2}, {6, 4}, {7, 4}, {8, 4}, {9, 4}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6}, {10, 6}, {4, 8}, {5, 8}, {6, 8}, {7, 8}, {8, 8}, {9, 8}, {10, 8}, {11, 8}};
			case "star_birch" -> pts = new int[][]{{3, 5}, {4, 4}, {5, 4}, {4, 6}, {5, 6}, {10, 5}, {11, 4}, {12, 4}, {11, 6}, {12, 6}, {6, 7}, {7, 7}, {8, 7}, {9, 7}};
			case "moon_ash" -> pts = new int[][]{{4, 5}, {5, 4}, {6, 5}, {5, 6}, {10, 5}, {11, 4}, {12, 5}, {11, 6}, {7, 2}, {8, 2}, {7, 3}, {8, 3}};
			default -> pts = new int[][]{{4, 4}, {5, 4}, {4, 5}, {5, 5}, {5, 6}, {10, 4}, {11, 4}, {10, 5}, {11, 5}, {10, 6}, {6, 7}, {7, 7}, {8, 7}, {9, 7}};
		}
		for (int[] p : pts) add(leaf, p[0], p[1]);
		layer(im, stem, t.bark(), shade(t.bark(), 1.2) & 0xFFFFFF | 0xFF000000, shade(t.barkDark(), 1.0), shade(t.barkDark(), 0.5));
		layer(im, leaf, c(t.leaf()), shade(t.leaf(), 1.25), c(t.leafDark()), shade(t.leafDark(), 0.5));
		return im;
	}

	static BufferedImage doorHalf(LumiraIds.Tree t, boolean top) {
		BufferedImage im = planks(t);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				boolean frame = x < 2 || x > 13 || y < 1 || y > 14;
				if (frame) im.setRGB(x, y, x < 1 || x > 14 || y < 1 || y > 14 ? shade(t.planksDark(), 0.7) : c(t.planksDark()));
				else if (top && x >= 4 && x <= 11 && y >= 3 && y <= 10 && (x == 7 || x == 8 || y == 6)) im.setRGB(x, y, shade(t.planksDark(), 0.75));
				else if (!top && x >= 4 && x <= 11 && y >= 4 && y <= 12 && (x == 4 || x == 11 || y == 4 || y == 12)) im.setRGB(x, y, shade(t.planksDark(), 0.85));
			}
		}
		if (!top) {
			im.setRGB(12, 7, c(0xE6E6EE));
			im.setRGB(12, 8, c(0x9A9AA8));
		}
		return im;
	}

	static BufferedImage trapdoor(LumiraIds.Tree t) {
		BufferedImage im = planks(t);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				if (x < 2 || x > 13 || y < 2 || y > 13) im.setRGB(x, y, c(t.planksDark()));
				if (x == y || x + y == 15) im.setRGB(x, y, shade(t.planksDark(), 0.8));
				if (x == 0 || y == 0 || x == 15 || y == 15) im.setRGB(x, y, shade(t.planksDark(), 0.65));
			}
		}
		return im;
	}

	static void trees() throws Exception {
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			save("block/" + s + "_log", bark(t, false));
			save("block/" + s + "_log_top", rings(t, false));
			save("block/stripped_" + s + "_log", bark(t, true));
			save("block/stripped_" + s + "_log_top", rings(t, true));
			save("block/" + s + "_planks", planks(t));
			save("block/" + s + "_leaves", leaves(t, 0));
			save("block/" + s + "_leaves_top", leaves(t, 1));
			save("block/" + s + "_leaves_bottom", leaves(t, 2));
			BufferedImage sap = sapling(t);
			save("block/" + s + "_sapling", sap);
			save("item/" + s + "_sapling", sap);
			save("block/" + s + "_door_top", doorHalf(t, true));
			save("block/" + s + "_door_bottom", doorHalf(t, false));
			save("block/" + s + "_trapdoor", trapdoor(t));
			save("item/" + s + "_door", doorIcon(t));
			save("item/" + s + "_sign", signIcon(t, false));
			save("item/" + s + "_hanging_sign", signIcon(t, true));
			BufferedImage pl = planks(t);
			BufferedImage board = img(64, 32);
			for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) board.setRGB(x, y, pl.getRGB(x % 16, y % 16));
			save("entity/signs/" + s, board);
			BufferedImage hang = img(16, 16);
			for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) hang.setRGB(x, y, pl.getRGB(x, y));
			save("entity/signs/hanging/" + s, hang);
		}
	}

	static BufferedImage doorIcon(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		BufferedImage p = planks(t);
		boolean[][] m = mask();
		for (int x = 4; x < 12; x++) for (int y = 1; y < 15; y++) add(m, x, y);
		layer(im, m, c(t.planks()), shade(t.planks(), 1.2), c(t.planksDark()), shade(t.planksDark(), 0.45));
		for (int x = 5; x < 11; x++) {
			put(im, x, 7, c(t.planksDark()));
			put(im, x, 8, c(t.planksDark()));
		}
		for (int x = 5; x < 11; x++) for (int y : new int[]{3, 4, 10, 11, 12}) put(im, x, y, p.getRGB(x, y));
		put(im, 10, 8, c(0xE6E6EE));
		return im;
	}

	static BufferedImage signIcon(LumiraIds.Tree t, boolean hanging) {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		int y0 = hanging ? 6 : 2, y1 = hanging ? 12 : 8;
		for (int x = 2; x < 14; x++) for (int y = y0; y <= y1; y++) add(m, x, y);
		layer(im, m, c(t.planks()), shade(t.planks(), 1.2), c(t.planksDark()), shade(t.planksDark(), 0.45));
		for (int x = 4; x < 12; x += 2) put(im, x, (y0 + y1) / 2, c(t.planksDark()));
		if (hanging) {
			for (int y = 1; y < 6; y++) {
				put(im, 4, y, c(0x8E8E9C));
				put(im, 11, y, c(0x8E8E9C));
			}
		} else {
			boolean[][] st = mask();
			for (int y = 9; y < 15; y++) add(st, 7, y, 8, y);
			layer(im, st, c(t.bark()), shade(t.bark(), 1.2), c(t.barkDark()), shade(t.barkDark(), 0.5));
		}
		return im;
	}

	// ---------- станции ----------
	static void stations() throws Exception {
		BufferedImage top = img(16, 16), side = img(16, 16), bottom = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				boolean grid = x % 5 == 0 || y % 5 == 0;
				top.setRGB(x, y, grid ? c(0x3FE0D0) : tone(0x5C646E, blockNoise(x, y, 3, 3)));
				if (x == 0 || y == 0 || x == 15 || y == 15) top.setRGB(x, y, c(0x24292F));
				boolean frame = x < 2 || x > 13;
				boolean brace = (y == 5 || y == 11) && !frame;
				int col = frame ? c(0xE6C98E) : brace ? c(0xC9763E) : tone(0xD8BB7E, blockNoise(x, y, 4, 4));
				if (y < 3) col = c(0x4C545E);
				if (y == 3) col = c(0x39D6C8);
				side.setRGB(x, y, col);
				bottom.setRGB(x, y, tone(0xBFA06A, blockNoise(x, y, 4, 5)));
			}
		}
		save("block/rune_workbench_top", top);
		save("block/rune_workbench_side", side);
		save("block/rune_workbench_bottom", bottom);
		BufferedImage body = img(16, 16), atop = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				body.setRGB(x, y, (x + y) % 8 == 0 ? c(0x39D6C8) : tone(0x4C545E, blockNoise(x, y, 3, 6)));
				boolean groove = x > 3 && x < 12 && y > 3 && y < 12;
				atop.setRGB(x, y, groove ? tone(0x2F353C, blockNoise(x, y, 3, 7)) : tone(0x7A838E, blockNoise(x, y, 3, 8)));
			}
		}
		for (int i = 6; i < 10; i++) atop.setRGB(i, 7, c(0x3FE0D0));
		for (int i = 6; i < 10; i++) atop.setRGB(7, i, c(0x3FE0D0));
		save("block/rune_anvil_body", body);
		save("block/rune_anvil_top", atop);
	}

	// ---------- предметы ----------
	static int[] pal(String metal) {
		return switch (metal) {
			case "lumirite" -> new int[]{0x7FDCD2, 0xD8FFFA, 0x2E8F8A, 0x1FB8B0};
			case "zaremed" -> new int[]{0xD9803A, 0xFFC06B, 0x8F4A1C, 0xFFE08A};
			case "twilight_iron" -> new int[]{0x4A4358, 0x8A7FA8, 0x26222F, 0xB08CFF};
			default -> new int[]{0xDCE6F2, 0xFFFFFF, 0x8FA0B8, 0x4F86FF};
		};
	}

	static void ingot(String id, int[] p) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		for (int y = 5; y <= 11; y++) for (int x = 2 + (11 - y) / 2; x <= 10 + (11 - y) / 2; x++) add(m, x, y);
		layer(im, m, c(p[0]), c(p[1]), c(p[2]), OUT);
		for (int x = 6; x < 10; x++) put(im, x, 8, c(p[1]));
		put(im, 7, 6, c(0xFFFFFF));
		save("item/" + id, im);
	}

	static void raw(String id, int[] p, int core) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		int[][] rows = {{6, 9}, {4, 11}, {3, 12}, {3, 12}, {2, 12}, {3, 12}, {3, 11}, {4, 10}, {5, 9}};
		for (int i = 0; i < rows.length; i++) for (int x = rows[i][0]; x <= rows[i][1]; x++) add(m, x, 3 + i);
		layer(im, m, c(p[0]), c(p[1]), c(p[2]), OUT);
		int s = seed(id);
		for (int i = 0; i < 6; i++) {
			int x = 4 + (int) (n(i, 1, s) * 7), y = 5 + (int) (n(i, 2, s) * 5);
			if (m[x][y]) put(im, x, y, c(core));
		}
		put(im, 8, 7, c(core));
		put(im, 9, 7, c(core));
		save("item/" + id, im);
	}

	static void crystal(String id, int main, int light, int dark) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		int[][] rows = {{7, 8}, {6, 9}, {5, 10}, {4, 11}, {4, 11}, {4, 11}, {4, 11}, {4, 11}, {5, 10}, {5, 10}, {6, 9}, {6, 9}, {7, 8}};
		for (int i = 0; i < rows.length; i++) for (int x = rows[i][0]; x <= rows[i][1]; x++) add(m, x, 1 + i);
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (m[x][y]) put(im, x, y, x < 7 ? c(light) : x < 9 ? c(main) : c(dark));
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (!m[x][y] && (has(m, x - 1, y) || has(m, x + 1, y) || has(m, x, y - 1) || has(m, x, y + 1))) put(im, x, y, OUT);
		put(im, 6, 4, c(0xFFFFFF));
		put(im, 6, 5, c(0xFFFFFF));
		save("item/" + id, im);
	}

	static void lump(String id, int main, int accent) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		int[][] rows = {{6, 10}, {4, 11}, {3, 12}, {3, 12}, {3, 12}, {4, 11}, {5, 10}};
		for (int i = 0; i < rows.length; i++) for (int x = rows[i][0]; x <= rows[i][1]; x++) add(m, x, 4 + i);
		layer(im, m, c(main), shade(main, 1.8), shade(main, 0.6), OUT);
		for (int[] p : new int[][]{{5, 6}, {9, 5}, {7, 8}, {10, 8}, {6, 9}}) if (m[p[0]][p[1]]) put(im, p[0], p[1], c(accent));
		save("item/" + id, im);
	}

	static void briquette(String id, int main, int accent) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		for (int x = 3; x < 13; x++) for (int y = 3; y < 13; y++) add(m, x, y);
		layer(im, m, c(main), shade(main, 1.5), shade(main, 0.6), OUT);
		for (int i = 4; i < 12; i++) {
			put(im, 7, i, c(accent));
			put(im, 8, i, c(accent));
			put(im, i, 7, c(accent));
			put(im, i, 8, c(accent));
		}
		save("item/" + id, im);
	}

	static void items() throws Exception {
		lump("spark_coal", 0x24242C, 0xF29A2E);
		briquette("spark_briquette", 0x26262E, 0xF29A2E);
		lump("jarolite", 0x8A2424, 0xF4E2B6);
		briquette("jarolite_briquette", 0x7A1E1E, 0xF4E2B6);
		crystal("dew_quartz", 0x9EF2D4, 0xE2FFF6, 0x4FB896);
		crystal("echolite", 0xB48EF2, 0xE6D4FF, 0x6A3FB8);
		crystal("heart_crystal", 0xFF7FA8, 0xFFD2A0, 0xB8456C);
		// линза
		BufferedImage lens = img(16, 16);
		boolean[][] lm = mask();
		for (int y = 3; y < 13; y++) for (int x = 3; x < 13; x++) if (Math.abs(x - 7.5) + Math.abs(y - 7.5) < 7) add(lm, x, y);
		layer(lens, lm, c(0x7FE8C6), c(0xD8FFF0), c(0x3FA088), OUT);
		for (int i = 5; i < 8; i++) put(lens, i, 6, c(0xFFFFFF));
		save("item/dew_lens", lens);
		// эхофокус: фиолетовый кристалл в медной рамке
		BufferedImage focus = img(16, 16);
		boolean[][] fm = mask();
		for (int x = 2; x < 14; x++) for (int y = 2; y < 14; y++) add(fm, x, y);
		layer(focus, fm, c(0xC9763E), c(0xFFB46A), c(0x7A4220), OUT);
		for (int x = 5; x < 11; x++) for (int y = 4; y < 12; y++) if (Math.abs(x - 7.5) * 1.3 + Math.abs(y - 7.5) < 5.2) put(focus, x, y, x < 8 ? c(0xD2B2FF) : c(0x8A5AD8));
		put(focus, 6, 6, c(0xFFFFFF));
		save("item/echo_focus", focus);
		// сердечная призма
		BufferedImage prism = img(16, 16);
		boolean[][] pm = mask();
		for (int y = 2; y < 13; y++) for (int x = 8 - (y - 2) / 2 - 1; x <= 8 + (y - 2) / 2; x++) add(pm, x, y);
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (pm[x][y]) put(prism, x, y, x < 8 ? c(0xFF8FB4) : c(0xD0507A));
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (!pm[x][y] && (has(pm, x - 1, y) || has(pm, x + 1, y) || has(pm, x, y - 1) || has(pm, x, y + 1))) put(prism, x, y, OUT);
		for (int x = 2; x < 14; x++) {
			put(prism, x, 13, c(0xDCE6F2));
			put(prism, x, 14, OUT);
		}
		put(prism, 7, 6, c(0xFFE9A0));
		save("item/heart_prism", prism);
		// нить
		BufferedImage th = img(16, 16);
		for (int x = 1; x < 15; x++) {
			int y = 8 + (int) Math.round(3 * Math.sin(x * 0.75));
			put(th, x, y - 1, OUT);
			put(th, x, y + 1, OUT);
			put(th, x, y, c(0xEAF8F6));
		}
		save("item/rune_thread", th);
		// посох
		BufferedImage st = img(16, 16);
		boolean[][] sm = mask();
		for (int i = 0; i < 10; i++) add(sm, 3 + i, 13 - i);
		layer(st, sm, c(0x8B5A2B), c(0xB07A3E), c(0x5A3A1A), OUT);
		boolean[][] gm = mask();
		for (int x = 10; x < 15; x++) for (int y = 1; y < 6; y++) if (Math.abs(x - 12) + Math.abs(y - 3) < 4) add(gm, x, y);
		layer(st, gm, c(0x7FF0D4), c(0xE8FFF6), c(0x2FB09A), OUT);
		put(st, 9, 6, c(0xC9763E));
		put(st, 10, 6, c(0xC9763E));
		save("item/rune_staff", st);
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			int[] p = pal(m.id());
			ingot(m.id() + "_ingot", p);
			raw("raw_" + m.id(), new int[]{blend(p[0], 0x3A3A44, 0.45), blend(p[1], 0xAAAAAA, 0.5), shade(blend(p[2], 0x222222, 0.4), 1.0) & 0xFFFFFF}, p[3]);
		}
	}

	// ---------- инструменты и броня ----------
	static final int HANDLE = 0x8B5A2B;

	static boolean[][] handle() {
		boolean[][] m = mask();
		for (int i = 0; i < 10; i++) add(m, 2 + i, 13 - i);
		return m;
	}

	static boolean[][] head(String kind) {
		boolean[][] m = mask();
		switch (kind) {
			case "pickaxe" -> {
				int[][] arc = {{5, 3}, {6, 2}, {7, 2}, {8, 2}, {9, 2}, {10, 2}, {11, 2}, {12, 3}, {13, 4}, {13, 5}, {14, 6}, {6, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3}, {11, 3}, {12, 4}, {12, 5}, {13, 6}, {4, 4}, {5, 4}, {4, 5}, {13, 7}, {14, 7}};
				for (int[] p : arc) add(m, p[0], p[1]);
			}
			case "axe" -> {
				int[][] a = new int[40][];
				int k = 0;
				int[][] rows = {{8, 12, 2}, {6, 13, 3}, {6, 12, 4}, {7, 11, 5}, {8, 10, 6}};
				for (int[] r : rows) for (int x = r[0]; x <= r[1]; x++) add(m, x, r[2]);
			}
			case "shovel" -> {
				int[][] rows = {{12, 14, 0}, {11, 15, 1}, {11, 15, 2}, {11, 15, 3}, {12, 14, 4}, {12, 14, 5}};
				for (int[] r : rows) for (int x = r[0]; x <= r[1]; x++) add(m, x, r[2]);
			}
			case "hoe" -> {
				int[][] a = {{7, 2}, {8, 2}, {9, 2}, {10, 2}, {11, 2}, {12, 3}, {7, 3}, {8, 3}, {11, 3}, {7, 4}, {8, 4}, {7, 5}};
				for (int[] p : a) add(m, p[0], p[1]);
			}
			default -> {
				// меч: клинок по диагонали, гарда и навершие
				for (int i = 0; i < 9; i++) add(m, 13 - i, 2 + i, 12 - i, 2 + i);
				add(m, 14, 1, 13, 1, 14, 2);
				add(m, 4, 8, 5, 9, 6, 10, 7, 11, 8, 12);
				add(m, 5, 8, 6, 9, 7, 10, 8, 11);
			}
		}
		return m;
	}

	static void toolIcon(String id, String kind, int[] p) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] h = handle();
		if (kind.equals("sword")) {
			h = mask();
			add(h, 5, 11, 4, 12, 3, 13);
			add(h, 2, 14);
		}
		layer(im, h, c(HANDLE), c(0xB07A3E), c(0x5A3A1A), OUT);
		boolean[][] hd = head(kind);
		layer(im, hd, c(p[0]), c(p[1]), c(p[2]), OUT);
		if (kind.equals("sword")) {
			for (int i = 0; i < 8; i++) put(im, 12 - i, 3 + i, c(p[1]));
			put(im, 6, 10, c(p[3]));
			put(im, 2, 14, c(p[2]));
			put(im, 3, 13, c(shadeI(HANDLE, 0.8)));
		} else {
			int ax = switch (kind) {
				case "pickaxe" -> 9;
				case "axe" -> 10;
				case "shovel" -> 13;
				default -> 9;
			};
			int ay = switch (kind) {
				case "pickaxe" -> 3;
				case "axe" -> 4;
				case "shovel" -> 3;
				default -> 2;
			};
			put(im, ax, ay, c(p[3]));
		}
		save("item/" + id, im);
	}

	static int shadeI(int rgb, double f) {
		return shade(rgb, f);
	}

	static final String[][] ARMOR_GRIDS = {
			{"................", "................", "....DDDDDDDD....", "...DMMMMMMMMD...", "..DMLLLLLLLLMD..", "..DMLMMMMMMLMD..", "..DML......LMD..", "..DMM..AA..MMD..", "..DMM......MMD..", "..DDD......DDD..", "................", "................", "................", "................", "................", "................"},
			{"................", "..DDDD....DDDD..", ".DMMMMD..DMMMMD.", ".DMLLMMDDMMLLMD.", ".DMLMMMAAMMMLMD.", ".DMMMMMAAMMMMMD.", "..DMMMMMMMMMMD..", "..DDMMMMMMMMDD..", "...DMLMMMMLMD...", "...DMMMMMMMMD...", "...DMMMMMMMMD...", "...DDDDDDDDDD...", "................", "................", "................", "................"},
			{"................", "................", "...DDDDDDDDDD...", "..DMMMMMMMMMMD..", "..DMLMMAAMMLMD..", "..DMMMMDDMMMMD..", "..DMMMD..DMMMD..", "..DMLMD..DMLMD..", "..DMMMD..DMMMD..", "..DMMMD..DMMMD..", "..DMLMD..DMLMD..", "..DDDDD..DDDDD..", "................", "................", "................", "................"},
			{"................", "................", "................", "................", "................", "................", "..DDDD....DDDD..", "..DMMD....DMMD..", "..DMMD....DMMD..", ".DMLMD....DMLMD.", ".DMMMDDD..DMMMDD", ".DMMMMMMD.DMMMMM", ".DAAMMMMD.DAAMMM", ".DDDDDDDD.DDDDDD", "................", "................"}};

	static void armorIcon(String id, String[] rows, int[] p) throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (rows[y].charAt(x) != '.') m[x][y] = true;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (!m[x][y] && (has(m, x - 1, y) || has(m, x + 1, y) || has(m, x, y - 1) || has(m, x, y + 1))) put(im, x, y, OUT);
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char ch = rows[y].charAt(x);
				if (ch == '.') continue;
				int col = switch (ch) {
					case 'L' -> p[1];
					case 'D' -> p[2];
					case 'A' -> p[3];
					default -> p[0];
				};
				put(im, x, y, c(col));
			}
		}
		save("item/" + id, im);
	}

	static void toolsAndArmor() throws Exception {
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			int[] p = pal(m.id());
			for (String k : LumiraIds.TOOL_KINDS) toolIcon(m.id() + "_" + k, k, p);
			if (!m.hasArmor()) continue;
			for (int i = 0; i < 4; i++) armorIcon(m.id() + "_" + LumiraIds.ARMOR_KINDS[i], ARMOR_GRIDS[i], p);
			for (int layer = 1; layer <= 2; layer++) save("models/armor/" + m.id() + "_layer_" + layer, armorLayer(layer, p));
		}
	}

	// ---------- слои брони (UV стандартной модели 64x32) ----------
	/** Грань: светлый верх/лево, тёмный низ/право, пластины внутри; rows — сколько верхних строк непрозрачно. */
	static void armorFace(BufferedImage im, int x0, int y0, int w, int rows, int[] p, boolean accent) {
		for (int y = 0; y < rows; y++) {
			for (int x = 0; x < w; x++) {
				int col = p[0];
				if (y == 0 || x == 0) col = p[1];
				if (y == rows - 1 || x == w - 1) col = p[2];
				if (y == 0 && x == w - 1) col = p[0];
				if (y > 1 && y < rows - 2 && x > 0 && x < w - 1 && (x + y * 2) % 7 == 0) col = p[2];
				put(im, x0 + x, y0 + y, c(col));
			}
		}
		if (accent && w >= 4 && rows >= 4) {
			int cx = x0 + w / 2 - 1, cy = y0 + rows / 2 - 1;
			for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) put(im, cx + dx, cy + dy, c(p[3]));
		}
	}

	/**
	 * layer 1 (внешний): шлем (голова), нагрудник (тело+руки), ботинки (низ ног).
	 * layer 2 (внутренний): поножи (пояс на теле + ноги). Глаза (строки 12-13 лица) остаются прозрачными.
	 */
	static BufferedImage armorLayer(int layer, int[] p) {
		BufferedImage im = img(64, 32);
		if (layer == 1) {
			armorFace(im, 8, 0, 8, 8, p, false);
			armorFace(im, 0, 8, 8, 5, p, false);
			armorFace(im, 16, 8, 8, 5, p, false);
			armorFace(im, 24, 8, 8, 7, p, false);
			armorFace(im, 8, 8, 8, 4, p, false);
			put(im, 11, 12, c(p[1]));
			put(im, 12, 12, c(p[1]));
			put(im, 11, 13, c(p[2]));
			put(im, 12, 13, c(p[2]));
			put(im, 11, 9, c(p[3]));
			put(im, 12, 9, c(p[3]));
			armorFace(im, 20, 20, 8, 12, p, true);
			armorFace(im, 32, 20, 8, 12, p, false);
			armorFace(im, 16, 20, 4, 12, p, false);
			armorFace(im, 28, 20, 4, 12, p, false);
			armorFace(im, 20, 16, 8, 4, p, false);
			for (int ax : new int[]{40, 44, 48, 52}) armorFace(im, ax, 20, 4, 12, p, ax == 44);
			armorFace(im, 44, 16, 4, 4, p, false);
			armorFace(im, 48, 16, 4, 4, p, false);
			for (int lx : new int[]{0, 4, 8, 12}) armorFace(im, lx, 28, 4, 4, p, false);
			armorFace(im, 8, 16, 4, 4, p, false);
		} else {
			armorFace(im, 20, 20, 8, 4, p, false);
			armorFace(im, 32, 20, 8, 4, p, false);
			armorFace(im, 16, 20, 4, 4, p, false);
			armorFace(im, 28, 20, 4, 4, p, false);
			put(im, 23, 21, c(p[3]));
			put(im, 24, 21, c(p[3]));
			for (int lx : new int[]{0, 4, 8, 12}) armorFace(im, lx, 20, 4, 9, p, lx == 4);
			armorFace(im, 4, 16, 4, 4, p, false);
		}
		return im;
	}

	// ---------- ожерелья и монеты ----------
	static void necklacesAndCoins() throws Exception {
		int[][] cc = {{0x7FE8C6, 0xD8FFF0, 0x3FA088, 0xC9763E}, {0xB48EF2, 0xE6D4FF, 0x6A3FB8, 0xC9763E}, {0x3C3646, 0x8F7AC9, 0x1E1A26, 0x8E8E9C}, {0x4F86FF, 0xBCD2FF, 0x2A56B8, 0xDCE6F2}};
		for (int i = 0; i < 4; i++) {
			BufferedImage im = img(16, 16);
			boolean[][] chain = mask();
			for (int a = 0; a <= 12; a++) {
				double ang = Math.PI * a / 12.0;
				add(chain, 8 + (int) Math.round(6 * Math.cos(ang)), 1 + (int) Math.round(7 * Math.sin(ang)));
			}
			layer(im, chain, c(0xC4CED6), c(0xFFFFFF), c(0x7E8A94), OUT);
			boolean[][] pend = mask();
			for (int x = 4; x < 12; x++) for (int y = 8; y < 15; y++) if (Math.abs(x - 7.5) + Math.abs(y - 11) < 4.2) add(pend, x, y);
			layer(im, pend, c(cc[i][0]), c(cc[i][1]), c(cc[i][2]), OUT);
			put(im, 7, 10, c(0xFFFFFF));
			put(im, 7, 7, c(cc[i][3]));
			put(im, 8, 7, c(cc[i][3]));
			save("item/necklace_" + LumiraIds.NECKLACES[i], im);
		}
		BufferedImage lyra = img(16, 16), myr = img(16, 16);
		boolean[][] cm = mask(), om = mask();
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			if (Math.hypot(x - 7.5, y - 7.5) < 6.6) add(cm, x, y);
			if (Math.abs(x - 7.5) + Math.abs(y - 7.5) < 9 && Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)) < 6.6) add(om, x, y);
		}
		layer(lyra, cm, c(0xFFD34D), c(0xFFF0A0), c(0xC08A10), OUT);
		for (int x = 4; x < 12; x++) for (int y = 4; y < 12; y++) {
			boolean moon = Math.hypot(x - 6.8, y - 7.5) < 3.3 && Math.hypot(x - 8.9, y - 7.5) > 2.7;
			if (moon) put(lyra, x, y, c(0x6FE8DC));
		}
		layer(myr, om, c(0xD2DBE2), c(0xFFFFFF), c(0x7A8590), OUT);
		for (int i = 4; i < 12; i++) put(myr, i, 12 - i + 3 < 13 ? 12 - i + 3 : 12, c(0x3E9B54));
		for (int[] p : new int[][]{{9, 5}, {10, 6}, {5, 9}, {6, 10}, {9, 6}, {6, 9}}) put(myr, p[0], p[1], c(0x62C97A));
		save("item/coin_lyra", lyra);
		save("item/coin_myrtle", myr);
	}

	/** Вместо зашифрованной картинки: тёмная руническая плитка с вопросом. */
	static void hiddenIcon() throws Exception {
		BufferedImage im = img(16, 16);
		boolean[][] m = mask();
		for (int x = 2; x < 14; x++) for (int y = 2; y < 14; y++) add(m, x, y);
		layer(im, m, c(0x4B2E83), c(0x8A6AD6), c(0x2A1850), OUT);
		int[][] q = {{6, 4}, {7, 4}, {8, 4}, {9, 5}, {9, 6}, {8, 7}, {7, 8}, {7, 9}, {7, 11}};
		for (int[] p : q) put(im, p[0], p[1], c(0xD8C8FF));
		put(im, 4, 12, c(0x3FE0D0));
		put(im, 11, 3, c(0x3FE0D0));
		save("item/hidden_item", im);
	}

	// ---------- свои текстуры вместо чужих ----------
	static void tokens() throws Exception {
		// Печать пути: сургучная печать с руной
		BufferedImage seal = img(16, 16);
		boolean[][] m = mask();
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			double d = Math.hypot(x - 7.5, y - 7.5) + (n(x, y, 77) - 0.5) * 1.6;
			if (d < 6.4) add(m, x, y);
		}
		layer(seal, m, c(0x7C2BD6), c(0xB68CFF), c(0x45148F), OUT);
		boolean[][] rune = mask();
		for (int y = 4; y < 12; y++) add(rune, 7, y, 8, y);
		add(rune, 5, 6, 6, 6, 9, 6, 10, 6, 5, 9, 6, 9, 9, 9, 10, 9, 6, 5, 9, 5, 6, 11, 9, 11);
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if (rune[x][y]) put(seal, x, y, c(0x7FF3FF));
		for (int x = 0; x < 16; x++) put(seal, x, 15, 0);
		// ленточка-хвост печати
		for (int y = 12; y < 16; y++) {
			put(seal, 11, y, c(0x45148F));
			put(seal, 12, y, c(0x7C2BD6));
		}
		save("item/story_save_token", seal);
		// Якорь возрождения: якорь с бирюзовым свечением
		BufferedImage anchor = img(16, 16);
		boolean[][] a = mask();
		for (int y = 3; y < 13; y++) add(a, 7, y, 8, y);
		add(a, 6, 2, 7, 2, 8, 2, 9, 2, 6, 3, 9, 3);
		add(a, 4, 6, 5, 6, 6, 6, 9, 6, 10, 6, 11, 6);
		add(a, 3, 10, 4, 11, 5, 12, 6, 13, 7, 13, 8, 13, 9, 13, 10, 13, 11, 12, 12, 11, 4, 9, 11, 9, 3, 9, 12, 10);
		layer(anchor, a, c(0xB8C4CE), c(0xFFFFFF), c(0x6A7682), OUT);
		for (int y = 4; y < 12; y += 2) put(anchor, 7, y, c(0x3FE0D0));
		put(anchor, 7, 2, c(0x3FE0D0));
		put(anchor, 8, 2, c(0x3FE0D0));
		save("item/story_respawn_token", anchor);
	}
}
