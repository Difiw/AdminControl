import ru.admincontrol.lumira.LumiraIds;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Детализированные текстуры «Рощи Люмиры» 32x32. Формы берутся из ванильных текстур (поэтому инструменты и броня
 * сидят в руке/на теле ровно как в Minecraft), увеличиваются алгоритмом EPX (без мыла), перекрашиваются по яркости в
 * палитры материалов и получают фаски/блики. Свои предметы (монеты, ожерелья, линзы) рисуются с нуля.
 *
 * Запуск:  java -cp tools/_scratch/cls tools/LumiraTex.java src/main/resources C:/.../minecraft-client.jar
 */
public class LumiraTex {
	static Path tex;
	static ZipFile mc;
	static final int OUT = 0xFF0E0C14;

	public static void main(String[] args) throws Exception {
		tex = Path.of(args[0]).resolve("assets/admin_control/textures");
		mc = new ZipFile(args[1]);
		ores();
		metalBlocks();
		trees();
		stations();
		metalItems();
		toolsAndArmor();
		gemsAndMisc();
		handmade();
		System.out.println("TEX OK");
	}

	// ======================= примитивы =======================
	static BufferedImage van(String rel) throws Exception {
		try (InputStream in = mc.getInputStream(mc.getEntry("assets/minecraft/textures/" + rel + ".png"))) {
			return ImageIO.read(in);
		}
	}

	static void save(String rel, BufferedImage im) throws Exception {
		Path p = tex.resolve(rel + ".png");
		Files.createDirectories(p.getParent());
		ImageIO.write(im, "png", p.toFile());
	}

	static int a(int argb) { return argb >>> 24; }
	static int r(int c) { return (c >> 16) & 255; }
	static int g(int c) { return (c >> 8) & 255; }
	static int b(int c) { return c & 255; }
	static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	static int c(int rgb) { return 0xFF000000 | rgb; }
	static double lum(int c) { return (0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c)) / 255.0; }

	static int mix(int c1, int c2, double t) {
		t = Math.max(0, Math.min(1, t));
		return rgb((int) Math.round(r(c1) + (r(c2) - r(c1)) * t), (int) Math.round(g(c1) + (g(c2) - g(c1)) * t),
				(int) Math.round(b(c1) + (b(c2) - b(c1)) * t));
	}

	static int shade(int c, double f) {
		return rgb((int) (r(c) * f), (int) (g(c) * f), (int) (b(c) * f));
	}

	static int lighten(int c, double t) { return mix(c, 0xFFFFFF, t); }

	static double hash(int x, int y, int s) {
		long h = x * 374761393L + y * 668265263L + s * 2246822519L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	static int seed(String s) { return s.hashCode() & 0x7FFFFFFF; }

	/** Рампа цветов, t в 0..1; квантуется, чтобы сохранить «пиксельный» вид. */
	static int ramp(int[] stops, double t) {
		t = Math.max(0, Math.min(1, t));
		int levels = stops.length * 2 - 1;
		t = Math.round(t * (levels - 1)) / (double) (levels - 1);
		double pos = t * (stops.length - 1);
		int i = Math.min(stops.length - 2, (int) pos);
		return mix(stops[i], stops[i + 1], pos - i);
	}

	static int[] ramp(int... s) {
		int[] o = new int[s.length];
		for (int i = 0; i < s.length; i++) o[i] = c(s[i]);
		return o;
	}

	/** EPX / Scale2x: гладкое удвоение пиксель-арта без размытия. */
	static BufferedImage epx(BufferedImage s) {
		int w = s.getWidth(), h = s.getHeight();
		BufferedImage o = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = s.getRGB(x, y);
				int up = y > 0 ? s.getRGB(x, y - 1) : p, dn = y < h - 1 ? s.getRGB(x, y + 1) : p;
				int lf = x > 0 ? s.getRGB(x - 1, y) : p, rt = x < w - 1 ? s.getRGB(x + 1, y) : p;
				int p1 = p, p2 = p, p3 = p, p4 = p;
				if (lf == up && lf != dn && up != rt) p1 = up;
				if (up == rt && up != lf && rt != dn) p2 = rt;
				if (dn == lf && dn != rt && lf != up) p3 = lf;
				if (rt == dn && rt != up && dn != lf) p4 = dn;
				o.setRGB(2 * x, 2 * y, p1);
				o.setRGB(2 * x + 1, 2 * y, p2);
				o.setRGB(2 * x, 2 * y + 1, p3);
				o.setRGB(2 * x + 1, 2 * y + 1, p4);
			}
		}
		return o;
	}

	/** Границы яркости (2%..98%) по непрозрачным пикселям. */
	static double[] range(BufferedImage im) {
		List<Double> v = new ArrayList<>();
		for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) if (a(im.getRGB(x, y)) > 128) v.add(lum(im.getRGB(x, y)));
		Collections.sort(v);
		if (v.isEmpty()) return new double[]{0, 1};
		double lo = v.get((int) (v.size() * 0.02)), hi = v.get(Math.min(v.size() - 1, (int) (v.size() * 0.98)));
		if (hi - lo < 0.05) hi = lo + 0.05;
		return new double[]{lo, hi};
	}

	static BufferedImage copy(BufferedImage s) {
		BufferedImage o = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
		o.getGraphics().drawImage(s, 0, 0, null);
		return o;
	}

	/** Перекраска по яркости: вся картинка (блоки, дерево). */
	static BufferedImage toRamp(BufferedImage src, int[] stops, double grain, int sd) {
		BufferedImage im = epx(src);
		double[] rg = range(im);
		for (int y = 0; y < im.getHeight(); y++) {
			for (int x = 0; x < im.getWidth(); x++) {
				int p = im.getRGB(x, y);
				if (a(p) < 128) { im.setRGB(x, y, 0); continue; }
				double t = (lum(p) - rg[0]) / (rg[1] - rg[0]) + (hash(x, y, sd) - 0.5) * grain;
				im.setRGB(x, y, ramp(stops, t));
			}
		}
		return im;
	}

	static int[] woodRamp(LumiraIds.Tree t) {
		return new int[]{shade(c(t.planksDark()), 0.55), shade(c(t.planksDark()), 0.82), c(t.planksDark()), c(t.planks()), lighten(c(t.planks()), 0.28)};
	}

	// ======================= руды =======================
	static final Map<String, Object[]> ORE = new HashMap<>();

	static {
		// id -> {ванильная руда, основа, рампа}
		ORE.put("spark_coal_ore", new Object[]{"coal_ore", "stone", ramp(0x07070A, 0x141418, 0x3A3A44, 0xF29A2E, 0xFFD27A)});
		ORE.put("lumirite_ore", new Object[]{"iron_ore", "stone", ramp(0x0E3B3A, 0x1E8F86, 0x39D9C8, 0x8FFAEF, 0xE6FFFC)});
		ORE.put("zaremed_ore", new Object[]{"copper_ore", "stone", ramp(0x4A1E0A, 0x9A4E1A, 0xE8883A, 0xFFB866, 0xFFE9B8)});
		ORE.put("twilight_iron_ore", new Object[]{"deepslate_gold_ore", "deepslate", ramp(0x1C1730, 0x3B2F66, 0x6F58B8, 0xA68DF0, 0xDCCFFF)});
		ORE.put("starsilver_ore", new Object[]{"deepslate_diamond_ore", "deepslate", ramp(0x1F3A7A, 0x4F86FF, 0xA9C6FF, 0xEAF1FF, 0xFFFFFF)});
		ORE.put("dew_quartz_ore", new Object[]{"emerald_ore", "stone", ramp(0x1F6B58, 0x4FB896, 0x9EF2D4, 0xD8FFF0, 0xFFFFFF)});
		ORE.put("echolite_ore", new Object[]{"deepslate_lapis_ore", "deepslate", ramp(0x2A1560, 0x6A3FB8, 0xB48EF2, 0xE0CDFF, 0xFFFFFF)});
		ORE.put("heart_crystal_ore", new Object[]{"deepslate_redstone_ore", "deepslate", ramp(0x5A1830, 0xB8456C, 0xFF7FA8, 0xFFC1D6, 0xFFF0F5)});
		ORE.put("jarolite_ore", new Object[]{"redstone_ore", "stone", ramp(0x3A0A0A, 0x8A1C1C, 0xC93A2A, 0xF26B3A, 0xF4E2B6)});
	}

	static void ores() throws Exception {
		for (LumiraIds.Ore o : LumiraIds.ORES) {
			Object[] d = ORE.get(o.id());
			BufferedImage ore = van("block/" + d[0]), base = van("block/" + d[1]);
			int[] stops = (int[]) d[2];
			// маска руды: пиксели, отличающиеся от камня-основы
			BufferedImage maskImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			double[] lm = new double[256];
			for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
				int p = ore.getRGB(x, y), q = base.getRGB(x, y);
				int dist = Math.abs(r(p) - r(q)) + Math.abs(g(p) - g(q)) + Math.abs(b(p) - b(q));
				maskImg.setRGB(x, y, dist > 40 ? 0xFFFFFFFF : 0xFF000000);
			}
			BufferedImage m32 = epx(maskImg), c32 = epx(ore);
			BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			double lo = 1, hi = 0;
			for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if ((m32.getRGB(x, y) & 255) > 128) {
				double l = lum(c32.getRGB(x, y));
				lo = Math.min(lo, l); hi = Math.max(hi, l);
			}
			if (hi - lo < 0.1) hi = lo + 0.1;
			int sd = seed(o.id());
			for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
				boolean in = (m32.getRGB(x, y) & 255) > 128;
				int p = c32.getRGB(x, y);
				if (!in) {
					// лёгкая зернистость камня, чтобы 32 px не выглядели мылом
					double j = (hash(x, y, sd) - 0.5) * 0.06;
					out.setRGB(x, y, rgb((int) (r(p) * (1 + j)), (int) (g(p) * (1 + j)), (int) (b(p) * (1 + j))));
					continue;
				}
				double t = (lum(p) - lo) / (hi - lo);
				boolean up = y == 0 || (m32.getRGB(x, y - 1) & 255) <= 128, lf = x == 0 || (m32.getRGB(x - 1, y) & 255) <= 128;
				boolean dn = y == 31 || (m32.getRGB(x, y + 1) & 255) <= 128, rt = x == 31 || (m32.getRGB(x + 1 > 31 ? 31 : x + 1, y) & 255) <= 128;
				if (up || lf) t = Math.min(1, t * 0.5 + 0.62);
				else if (dn || rt) t = Math.max(0, t * 0.4 - 0.05);
				else t = Math.max(0.28, Math.min(0.85, t * 0.7 + 0.2 + (hash(x, y, sd + 3) - 0.5) * 0.25));
				out.setRGB(x, y, ramp(stops, t));
			}
			save("block/" + o.id(), out);
		}
	}

	static void metalBlocks() throws Exception {
		Map<String, int[]> pal = new LinkedHashMap<>();
		pal.put("lumirite", ramp(0x0A2E30, 0x17706C, 0x2FC4B6, 0x74EBDD, 0xD2FFF9));
		pal.put("zaremed", ramp(0x3A1608, 0x8A3F14, 0xDB7A30, 0xFFB35E, 0xFFE6B0));
		pal.put("twilight_iron", ramp(0x100C1A, 0x241C3A, 0x41366C, 0x7B66C4, 0xBFAEFF));
		pal.put("starsilver", ramp(0x33436A, 0x7487B0, 0xBFD0EE, 0xEAF1FF, 0xFFFFFF));
		BufferedImage iron = van("block/iron_block");
		for (var e : pal.entrySet()) {
			BufferedImage im = toRamp(iron, e.getValue(), 0.06, seed(e.getKey()));
			// угловые заклёпки с мягким свечением
			int acc = e.getValue()[4];
			for (int[] p : new int[][]{{3, 3}, {28, 3}, {3, 28}, {28, 28}}) {
				im.setRGB(p[0], p[1], acc);
			}
			save("block/" + e.getKey() + "_block", im);
		}
	}

	// ======================= деревья =======================
	static void trees() throws Exception {
		BufferedImage oakPlanks = van("block/oak_planks"), oakLog = van("block/oak_log"), oakLogTop = van("block/oak_log_top"),
				strLog = van("block/stripped_oak_log"), strTop = van("block/stripped_oak_log_top"), leaves = van("block/oak_leaves"),
				doorTop = van("block/oak_door_top"), doorBot = van("block/oak_door_bottom"), trap = van("block/oak_trapdoor"),
				sapling = van("block/oak_sapling");
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			int sd = seed(s);
			int[] wood = woodRamp(t);
			int[] bark = {shade(c(t.barkDark()), 0.5), shade(c(t.barkDark()), 0.85), c(t.barkDark()), c(t.bark()), lighten(c(t.bark()), 0.3)};
			int[] top = {shade(c(t.barkDark()), 0.8), c(t.bark()), c(t.planks()), mix(c(t.planks()), c(t.ring()), 0.5), c(t.ring())};
			int[] stripped = {shade(c(t.planksDark()), 0.8), c(t.planksDark()), c(t.planks()), mix(c(t.planks()), c(t.ring()), 0.55), c(t.ring())};
			int[] lf = {shade(c(t.leafDark()), 0.55), c(t.leafDark()), mix(c(t.leafDark()), c(t.leaf()), 0.55), c(t.leaf()), lighten(c(t.leaf()), 0.32)};
			save("block/" + s + "_planks", toRamp(oakPlanks, wood, 0.05, sd));
			save("block/" + s + "_log", toRamp(oakLog, bark, 0.08, sd + 1));
			save("block/" + s + "_log_top", toRamp(oakLogTop, top, 0.05, sd + 2));
			save("block/stripped_" + s + "_log", toRamp(strLog, stripped, 0.05, sd + 3));
			save("block/stripped_" + s + "_log_top", toRamp(strTop, stripped, 0.04, sd + 4));
			BufferedImage lv = leavesTex(leaves, lf, sd + 5, 0);
			save("block/" + s + "_leaves", lv);
			save("block/" + s + "_leaves_top", leavesTex(leaves, lf, sd + 6, 1));
			save("block/" + s + "_leaves_bottom", leavesTex(leaves, lf, sd + 7, -1));
			save("block/" + s + "_door_top", toRamp(doorTop, wood, 0.04, sd + 8));
			save("block/" + s + "_door_bottom", toRamp(doorBot, wood, 0.04, sd + 9));
			save("block/" + s + "_trapdoor", toRamp(trap, wood, 0.04, sd + 10));
			BufferedImage sap = saplingTex(sapling, lf, bark, sd + 11);
			save("block/" + s + "_sapling", sap);
			save("item/" + s + "_sapling", sap);
			save("item/" + s + "_door", woodItem(van("item/oak_door"), wood));
			save("item/" + s + "_sign", woodItem(van("item/oak_sign"), wood));
			save("item/" + s + "_hanging_sign", woodItem(van("item/oak_hanging_sign"), wood));
			save("entity/signs/" + s, signEntity(van("entity/signs/oak"), wood));
			save("entity/signs/hanging/" + s, signEntity(van("entity/signs/hanging/oak"), wood));
		}
	}

	/** Листва: прозрачные «дырки» ваниль-листвы, перекраска и мерцающие искры. variant: 0 бок, 1 верх (светлее), -1 низ (темнее). */
	static BufferedImage leavesTex(BufferedImage src, int[] stops, int sd, int variant) {
		BufferedImage im = epx(src);
		// листва ванили серая и без альфы — делаем «дырки» шумом, как в её рисунке
		double[] rg = range(im);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
			int p = im.getRGB(x, y);
			double t = (lum(p) - rg[0]) / (rg[1] - rg[0]);
			t += (hash(x, y, sd) - 0.5) * 0.18 + variant * 0.12;
			int col = ramp(stops, t);
			if (a(p) < 128) col = 0;
			// светлые точки-«росинки» на листьях
			else if (hash(x / 2, y / 2, sd + 40) > 0.93 && variant >= 0) col = stops[stops.length - 1];
			im.setRGB(x, y, col);
		}
		return im;
	}

	static BufferedImage saplingTex(BufferedImage src, int[] leaf, int[] bark, int sd) {
		BufferedImage im = epx(src);
		double[] rg = range(im);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
			int p = im.getRGB(x, y);
			if (a(p) < 128) { im.setRGB(x, y, 0); continue; }
			double t = (lum(p) - rg[0]) / (rg[1] - rg[0]);
			boolean brown = r(p) > g(p) + 8 || (Math.abs(r(p) - g(p)) < 12 && b(p) < g(p) - 20);
			im.setRGB(x, y, ramp(brown ? bark : leaf, brown ? t * 0.8 + 0.2 : t));
		}
		return im;
	}

	/** Предметы-иконки из дерева: коричневые пиксели красим палитрой, серые (цепи) оставляем. */
	static BufferedImage woodItem(BufferedImage src, int[] wood) {
		BufferedImage im = epx(src);
		double[] rg = range(im);
		for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
			int p = im.getRGB(x, y);
			if (a(p) < 128) continue;
			int sat = Math.max(r(p), Math.max(g(p), b(p))) - Math.min(r(p), Math.min(g(p), b(p)));
			if (sat < 14) continue;
			im.setRGB(x, y, ramp(wood, (lum(p) - rg[0]) / (rg[1] - rg[0])));
		}
		return im;
	}

	static BufferedImage signEntity(BufferedImage src, int[] wood) {
		BufferedImage im = copy(src);
		double[] rg = range(im);
		for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
			int p = im.getRGB(x, y);
			if (a(p) < 128) continue;
			int sat = Math.max(r(p), Math.max(g(p), b(p))) - Math.min(r(p), Math.min(g(p), b(p)));
			if (sat < 10 && lum(p) < 0.5) continue; // цепи
			im.setRGB(x, y, ramp(wood, (lum(p) - rg[0]) / (rg[1] - rg[0])));
		}
		return im;
	}

	// ======================= станции =======================
	static void stations() throws Exception {
		int[] purple = ramp(0x14101F, 0x2E2450, 0x5A47A0, 0x8F78D8, 0xD2C6FF);
		BufferedImage top = toRamp(van("block/crafting_table_top"), purple, 0.06, 11);
		BufferedImage side = toRamp(van("block/crafting_table_side"), purple, 0.06, 12);
		BufferedImage bottom = toRamp(van("block/oak_planks"), purple, 0.06, 13);
		int glow = c(0x3FE0D0);
		for (int[] p : new int[][]{{4, 4}, {5, 4}, {4, 5}, {26, 4}, {27, 4}, {27, 5}, {4, 26}, {4, 27}, {5, 27}, {27, 26}, {26, 27}, {27, 27}, {15, 15}, {16, 15}, {15, 16}, {16, 16}}) {
			top.setRGB(p[0], p[1], glow);
		}
		for (int[] p : new int[][]{{15, 6}, {16, 6}, {15, 7}, {16, 7}, {6, 20}, {7, 20}, {24, 20}, {25, 20}}) {
			side.setRGB(p[0], p[1], glow);
		}
		save("block/rune_workbench_top", top);
		save("block/rune_workbench_side", side);
		save("block/rune_workbench_bottom", bottom);
		int[] steel = ramp(0x101A26, 0x2A4258, 0x4F7C9C, 0x8EC9E4, 0xE4FCFF);
		BufferedImage body = toRamp(van("block/anvil"), steel, 0.05, 14);
		BufferedImage atop = toRamp(van("block/anvil_top"), steel, 0.05, 15);
		for (int[] p : new int[][]{{14, 10}, {15, 10}, {16, 10}, {17, 10}, {14, 22}, {17, 22}}) {
			body.setRGB(p[0], p[1], glow);
		}
		for (int x = 8; x < 24; x += 2) atop.setRGB(x, 16, glow);
		save("block/rune_anvil_body", body);
		save("block/rune_anvil_top", atop);
	}

	// ======================= металлы: слитки и сырьё =======================
	static final Map<String, int[]> METAL = new LinkedHashMap<>();

	static {
		METAL.put("lumirite", ramp(0x0A2E30, 0x17706C, 0x2FC4B6, 0x74EBDD, 0xD2FFF9));
		METAL.put("zaremed", ramp(0x3A1608, 0x8A3F14, 0xDB7A30, 0xFFB35E, 0xFFE6B0));
		METAL.put("twilight_iron", ramp(0x100C1A, 0x241C3A, 0x41366C, 0x7B66C4, 0xBFAEFF));
		METAL.put("starsilver", ramp(0x33436A, 0x7487B0, 0xBFD0EE, 0xEAF1FF, 0xFFFFFF));
	}

	static final Map<String, Integer> ACCENT = Map.of("lumirite", 0xC2FFF8, "zaremed", 0xFFE08A, "twilight_iron", 0xB48EFF, "starsilver", 0x6FA0FF);

	/** Перекраска серого предмета: серые пиксели -> рампа, коричневые (рукоять) оставляем с лёгким тоном. */
	static BufferedImage metalize(BufferedImage src, String metal, int sd, boolean handleTint) {
		return metalize(src, metal, sd, handleTint, true);
	}

	static BufferedImage metalize(BufferedImage src, String metal, int sd, boolean handleTint, boolean keepHandle) {
		int[] stops = METAL.get(metal);
		BufferedImage im = epx(src);
		double[] rg = range(im);
		int acc = c(ACCENT.get(metal));
		for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
			int p = im.getRGB(x, y);
			if (a(p) < 128) continue;
			int sat = Math.max(r(p), Math.max(g(p), b(p))) - Math.min(r(p), Math.min(g(p), b(p)));
			boolean handle = keepHandle && sat > 16 && r(p) > b(p) + 12;
			if (handle) {
				if (handleTint) im.setRGB(x, y, mix(p, stops[2], 0.10));
				continue;
			}
			double t = (lum(p) - rg[0]) / (rg[1] - rg[0]);
			int col = ramp(stops, t);
			// редкие искры металла и «жилка» света
			if (t > 0.45 && t < 0.8 && hash(x, y, sd) > 0.955) col = acc;
			im.setRGB(x, y, col);
		}
		return im;
	}

	static void metalItems() throws Exception {
		for (String m : METAL.keySet()) {
			save("item/" + m + "_ingot", metalize(van("item/iron_ingot"), m, seed(m), false));
			save("item/raw_" + m, metalize(van("item/raw_iron"), m, seed(m) + 1, false, false));
		}
	}

	static void toolsAndArmor() throws Exception {
		String[][] tools = {{"sword", "iron_sword"}, {"axe", "iron_axe"}, {"pickaxe", "iron_pickaxe"}, {"shovel", "iron_shovel"}, {"hoe", "iron_hoe"}};
		for (String m : METAL.keySet()) {
			for (String[] t : tools) {
				save("item/" + m + "_" + t[0], metalize(van("item/" + t[1]), m, seed(m + t[0]), true));
			}
		}
		for (String m : new String[]{"twilight_iron", "starsilver"}) {
			for (String part : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
				save("item/" + m + "_" + part, metalize(van("item/iron_" + part), m, seed(m + part), false));
			}
			for (int layer = 1; layer <= 2; layer++) {
				BufferedImage armor = metalize(van("models/armor/iron_layer_" + layer), m, seed(m) + layer, false);
				save("models/armor/" + m + "_layer_" + layer, armor);
			}
		}
	}

	// ======================= самоцветы =======================
	static void gemsAndMisc() throws Exception {
		save("item/spark_coal", metalizeRamp(van("item/coal"), ramp(0x050507, 0x15151B, 0x2E2E38, 0xE8862A, 0xFFD27A), 21));
		save("item/jarolite", metalizeRamp(van("item/charcoal"), ramp(0x2A0808, 0x7A1818, 0xC93A2A, 0xF26B3A, 0xFFE2B0), 22));
		save("item/dew_quartz", metalizeRamp(van("item/quartz"), ramp(0x1F6B58, 0x4FB896, 0x9EF2D4, 0xD8FFF0, 0xFFFFFF), 23));
		save("item/echolite", metalizeRamp(van("item/amethyst_shard"), ramp(0x2A1560, 0x6A3FB8, 0xB48EF2, 0xE0CDFF, 0xFFFFFF), 24));
		save("item/heart_crystal", metalizeRamp(van("item/emerald"), ramp(0x5A1830, 0xB8456C, 0xFF7FA8, 0xFFC1D6, 0xFFF0F5), 25));
		save("item/spark_briquette", metalizeRamp(van("item/brick"), ramp(0x120A0A, 0x2E2428, 0x5A4A50, 0xF29A2E, 0xFFD27A), 26));
		save("item/jarolite_briquette", metalizeRamp(van("item/brick"), ramp(0x2A0808, 0x6E1414, 0xB02A2A, 0xE0583A, 0xF4E2B6), 27));
		save("item/rune_thread", metalizeRamp(van("item/string"), ramp(0x2A1560, 0x5A47A0, 0x9F86F0, 0x6FF3E6, 0xE0FFFC), 28));
	}

	static BufferedImage metalizeRamp(BufferedImage src, int[] stops, int sd) {
		BufferedImage im = epx(src);
		double[] rg = range(im);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
			int p = im.getRGB(x, y);
			if (a(p) < 128) continue;
			im.setRGB(x, y, ramp(stops, (lum(p) - rg[0]) / (rg[1] - rg[0]) + (hash(x, y, sd) - 0.5) * 0.06));
		}
		return im;
	}

	// ======================= рисунок с нуля: форма -> объём =======================
	static boolean[][] mask(java.util.function.Consumer<Graphics2D> draw) {
		BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = im.createGraphics();
		g.setColor(Color.WHITE);
		draw.accept(g);
		g.dispose();
		boolean[][] m = new boolean[32][32];
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) m[x][y] = a(im.getRGB(x, y)) > 100;
		return m;
	}

	static boolean has(boolean[][] m, int x, int y) { return x >= 0 && y >= 0 && x < 32 && y < 32 && m[x][y]; }

	/** Объёмная заливка маски: глубина до края -> «высота», освещение сверху-слева, затем тёмная обводка. */
	static void volume(BufferedImage im, boolean[][] m, int[] stops, double bulge, boolean outline) {
		double[][] d = new double[32][32];
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
			if (!m[x][y]) continue;
			double best = 8;
			for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 6; dy++) {
				if (!has(m, x + dx, y + dy)) best = Math.min(best, Math.hypot(dx, dy));
			}
			d[x][y] = best;
		}
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
			if (!m[x][y]) continue;
			double hx = (d[Math.min(31, x + 1)][y] - d[Math.max(0, x - 1)][y]) * 0.5, hy = (d[x][Math.min(31, y + 1)] - d[x][Math.max(0, y - 1)]) * 0.5;
			double light = -(hx * 0.75 + hy * 0.75) * bulge;
			double t = 0.5 + light * 0.45 + Math.min(1, d[x][y] / 6.0) * 0.12 - 0.04;
			im.setRGB(x, y, ramp(stops, t));
		}
		if (outline) {
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
				if (!m[x][y] && (has(m, x - 1, y) || has(m, x + 1, y) || has(m, x, y - 1) || has(m, x, y + 1))) {
					im.setRGB(x, y, OUT);
				}
			}
		}
	}

	static BufferedImage blank() { return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB); }

	static void handmade() throws Exception {
		coins();
		necklaces();
		optics();
		skillCoins();
		staff();
		tokens();
		hidden();
	}

	static void disc(Graphics2D g, double cx, double cy, double rad) {
		g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
	}

	static void coins() throws Exception {
		int[] gold = ramp(0x5A3A06, 0xB07A12, 0xF2C230, 0xFFE27A, 0xFFF7C8);
		int[] silver = ramp(0x2C3640, 0x6C7A88, 0xB6C4D0, 0xE6EEF4, 0xFFFFFF);
		for (int k = 0; k < 2; k++) {
			BufferedImage im = blank();
			int[] metal = k == 0 ? gold : silver;
			boolean[][] outer = mask(g -> disc(g, 16, 16, 14.5));
			volume(im, outer, metal, 1.0, true);
			// канавка и утопленный диск
			boolean[][] inner = mask(g -> disc(g, 16, 16, 11.2));
			boolean[][] ring = new boolean[32][32];
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) ring[x][y] = outer[x][y] && !inner[x][y] && Math.hypot(x - 15.5, y - 15.5) < 12.4;
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (ring[x][y]) im.setRGB(x, y, shade(metal[1], 0.9));
			boolean[][] disc = mask(g -> disc(g, 16, 16, 10.4));
			BufferedImage face = blank();
			volume(face, disc, metal, -0.7, false);
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (disc[x][y]) im.setRGB(x, y, face.getRGB(x, y));
			if (k == 0) {
				// лира: полумесяц и звезда
				boolean[][] moon = mask(g -> {
					Area a = new Area(new Ellipse2D.Double(8.5, 8.5, 15, 15));
					a.subtract(new Area(new Ellipse2D.Double(12.5, 7.5, 14, 14)));
					g.fill(a);
				});
				BufferedImage sym = blank();
				volume(sym, moon, ramp(0x0B4B48, 0x1E9C94, 0x4FE6D8, 0xA8FFF4, 0xFFFFFF), 1.2, false);
				for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (moon[x][y]) im.setRGB(x, y, sym.getRGB(x, y));
				for (int[] p : new int[][]{{20, 12}, {19, 13}, {20, 13}, {21, 13}, {20, 14}}) im.setRGB(p[0], p[1], c(0xFFFFFF));
			} else {
				// мирта: листик с жилкой
				boolean[][] leaf = mask(g -> {
					Path2D.Double p = new Path2D.Double();
					p.moveTo(9, 23);
					p.curveTo(8, 12, 16, 7, 23, 8);
					p.curveTo(24, 16, 19, 24, 9, 23);
					g.fill(p);
				});
				BufferedImage sym = blank();
				volume(sym, leaf, ramp(0x0C3A1C, 0x1F7A3C, 0x3FB85C, 0x8AF0A0, 0xE0FFE6), 1.2, false);
				for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (leaf[x][y]) im.setRGB(x, y, sym.getRGB(x, y));
				for (int i = 0; i < 11; i++) im.setRGB(10 + i, 22 - i, c(0xD8FFE0));
			}
			save("item/" + (k == 0 ? "coin_lyra" : "coin_myrtle"), im);
		}
	}

	static void necklaces() throws Exception {
		int[][] gems = {
				{0x1F6B58, 0x4FB896, 0x9EF2D4, 0xD8FFF0, 0xFFFFFF},
				{0x2A1560, 0x6A3FB8, 0xB48EF2, 0xE0CDFF, 0xFFFFFF},
				{0x100C1A, 0x2C2640, 0x4A3F78, 0x8F78D0, 0xDCCFFF},
				{0x1F3A7A, 0x4F86FF, 0xA9C6FF, 0xEAF1FF, 0xFFFFFF}};
		int[] chain = ramp(0x3A4650, 0x7E8A94, 0xC4CED6, 0xF0F6FA, 0xFFFFFF);
		for (int i = 0; i < 4; i++) {
			BufferedImage im = blank();
			final int fi = i;
			// цепочка: звенья вдоль параболы
			boolean[][] links = mask(g -> {
				for (int s = 0; s <= 22; s++) {
					double t = s / 22.0, x = 4 + t * 24, y = 3 + 14 * Math.pow(2 * t - 1, 2) * 0 + 17 * (1 - Math.pow(2 * t - 1, 2)) * 0.55 - 0;
					y = 3 + 13 * (1 - (1 - Math.pow(2 * t - 1, 2)));
					AffineTransform at = g.getTransform();
					g.rotate(Math.atan2(26 * Math.abs(2 * t - 1) * (t < 0.5 ? -1 : 1), 24) * 0.0 + (s % 2 == 0 ? 0.6 : -0.6), x, y);
					g.fill(new Ellipse2D.Double(x - 1.6, y - 1.0, 3.2, 2.0));
					g.setTransform(at);
				}
			});
			volume(im, links, chain, 1.0, true);
			// кулон
			Polygon gem = new Polygon(new int[]{16, 22, 22, 16, 10, 10}, new int[]{15, 18, 24, 29, 24, 18}, 6);
			boolean[][] pend = mask(g -> g.fill(gem));
			BufferedImage pi = blank();
			volume(pi, pend, gems[fi], 1.4, true);
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (pend[x][y] || pi.getRGB(x, y) == OUT) im.setRGB(x, y, pi.getRGB(x, y));
			// грани
			for (int y = 18; y < 26; y++) im.setRGB(16, y, gems[fi][3]);
			for (int x = 11; x < 21; x++) im.setRGB(x, 20, gems[fi][1]);
			im.setRGB(14, 19, c(0xFFFFFF));
			// колечко
			for (int[] p : new int[][]{{15, 12}, {16, 12}, {14, 13}, {17, 13}, {15, 14}, {16, 14}}) im.setRGB(p[0], p[1], chain[3]);
			save("item/necklace_" + LumiraIds.NECKLACES[i], im);
		}
	}

	static void optics() throws Exception {
		// Росная линза: латунное кольцо и стекло
		BufferedImage lens = blank();
		boolean[][] rim = mask(g -> disc(g, 16, 16, 13));
		volume(lens, rim, ramp(0x3A2410, 0x8A5A24, 0xC98A3C, 0xF0C070, 0xFFEBB8), 1.0, true);
		boolean[][] glass = mask(g -> disc(g, 16, 16, 9.8));
		BufferedImage gi = blank();
		volume(gi, glass, ramp(0x1F6B58, 0x3FA88C, 0x7FE8C8, 0xC8FFF0, 0xFFFFFF), -0.6, false);
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (glass[x][y]) lens.setRGB(x, y, gi.getRGB(x, y));
		for (int[] p : new int[][]{{10, 11}, {11, 10}, {12, 10}, {10, 12}, {11, 11}}) lens.setRGB(p[0], p[1], c(0xFFFFFF));
		save("item/dew_lens", lens);
		// Эхофокус: фиолетовый ограненный ромб
		BufferedImage foc = blank();
		Polygon dia = new Polygon(new int[]{16, 27, 16, 5}, new int[]{2, 15, 29, 15}, 4);
		boolean[][] dm = mask(g -> g.fill(dia));
		volume(foc, dm, ramp(0x241048, 0x5A32A0, 0x9A6EE0, 0xD2B4FF, 0xFFFFFF), 1.1, true);
		for (int i = 0; i < 12; i++) { foc.setRGB(16, 4 + i, c(0xE6D4FF)); }
		for (int x = 8; x < 25; x++) foc.setRGB(x, 15, c(0x7A4FC8));
		for (int i = 0; i < 6; i++) { foc.setRGB(10 + i, 8 + i * 1, c(0xB48EF2)); foc.setRGB(22 - i, 22 - i, c(0x4A2A88)); }
		save("item/echo_focus", foc);
		// Сердечная призма: розовая трёхгранная призма
		BufferedImage pr = blank();
		Polygon tri = new Polygon(new int[]{16, 28, 4}, new int[]{3, 27, 27}, 3);
		boolean[][] tm = mask(g -> g.fill(tri));
		volume(pr, tm, ramp(0x5A1030, 0xB02A66, 0xFF5E98, 0xFFA8C6, 0xFFEEF4), 1.0, true);
		for (int i = 0; i < 18; i++) pr.setRGB(16, 8 + i, c(0xFFC8DC));
		for (int x = 8; x < 25; x++) pr.setRGB(x, 21, c(0xE04888));
		save("item/heart_prism", pr);
	}

	static void skillCoins() throws Exception {
		String[] ids = {"skill_coin_chips", "skill_coin_fight", "skill_coin_life", "skill_coin_mind", "skill_coin_ore", "skill_coin_path", "skill_coin_shadow", "skill_coin_sync", "world_coin"};
		int[] cols = {0xC3A53C, 0xD48F45, 0xD45F6D, 0x53C1C5, 0x6AA7D1, 0x6EC27B, 0x9A75D1, 0xC29423, 0xC29423};
		for (int k = 0; k < ids.length; k++) {
			int base = cols[k];
			int[] metal = {shade(base, 0.35), shade(base, 0.65), base, lighten(base, 0.4), lighten(base, 0.8)};
			BufferedImage im = blank();
			boolean[][] outer = mask(g -> disc(g, 16, 16, 14.5));
			volume(im, outer, metal, 1.0, true);
			boolean[][] disc = mask(g -> disc(g, 16, 16, 10.6));
			BufferedImage face = blank();
			volume(face, disc, metal, -0.7, false);
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
				if (outer[x][y] && !disc[x][y] && Math.hypot(x - 15.5, y - 15.5) < 12.6) im.setRGB(x, y, shade(metal[1], 0.9));
				if (disc[x][y]) im.setRGB(x, y, face.getRGB(x, y));
			}
			BufferedImage q = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = q.createGraphics();
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
			g.setColor(Color.WHITE);
			g.drawString("W", 7, 22);
			g.dispose();
			for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
				if (a(q.getRGB(x, y)) > 140) im.setRGB(x, y, metal[0]);
				else if (x > 0 && y > 0 && a(q.getRGB(x - 1, y - 1)) > 140 && disc[x][y]) im.setRGB(x, y, metal[4]);
			}
			save("item/" + ids[k], im);
		}
	}

	static void staff() throws Exception {
		BufferedImage stick = epx(van("item/stick"));
		BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		im.getGraphics().drawImage(stick, 0, 0, null);
		// оправа и сфера на вершине
		boolean[][] claw = mask(g -> disc(g, 23, 9, 6.6));
		volume(im, claw, ramp(0x2A3038, 0x5A6672, 0x98A6B2, 0xD6E0E8, 0xFFFFFF), 1.0, true);
		boolean[][] orb = mask(g -> disc(g, 23, 9, 4.6));
		BufferedImage oi = blank();
		volume(oi, orb, ramp(0x0B4B48, 0x1E9C94, 0x4FE6D8, 0xA8FFF4, 0xFFFFFF), 1.4, false);
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (orb[x][y]) im.setRGB(x, y, oi.getRGB(x, y));
		im.setRGB(21, 7, c(0xFFFFFF));
		save("item/rune_staff", im);
	}

	static void tokens() throws Exception {
		// Печать пути: фиолетовый сургуч с руной
		BufferedImage seal = blank();
		boolean[][] m = mask(g -> {
			disc(g, 16, 15, 12.5);
			g.fill(new Rectangle2D.Double(21, 22, 4, 8));
		});
		volume(seal, m, ramp(0x2A0F52, 0x5A22A8, 0x8C52E6, 0xBE96FF, 0xEBDDFF), 1.0, true);
		boolean[][] rune = mask(g -> {
			g.fill(new Rectangle2D.Double(15, 7, 2, 17));
			g.fill(new Rectangle2D.Double(10, 11, 12, 2));
			g.fill(new Rectangle2D.Double(10, 18, 12, 2));
			g.fill(new Rectangle2D.Double(11, 7, 2, 2));
			g.fill(new Rectangle2D.Double(19, 7, 2, 2));
		});
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (rune[x][y]) seal.setRGB(x, y, c(0x7FF3FF));
		save("item/story_save_token", seal);
		// Якорь возрождения
		BufferedImage anchor = blank();
		boolean[][] a = mask(g -> {
			g.fill(new Rectangle2D.Double(14.5, 6, 3, 21));
			g.fill(new Rectangle2D.Double(9, 10, 14, 3));
			g.draw(new Ellipse2D.Double(13, 2, 6, 6));
			g.setStroke(new BasicStroke(3f));
			g.draw(new Arc2D.Double(5, 12, 22, 16, 200, 140, Arc2D.OPEN));
		});
		volume(anchor, a, ramp(0x2A3440, 0x5A6A7C, 0x9AAAB8, 0xDCE6EE, 0xFFFFFF), 1.0, true);
		for (int y = 11; y < 26; y += 3) anchor.setRGB(16, y, c(0x3FE0D0));
		save("item/story_respawn_token", anchor);
	}

	static void hidden() throws Exception {
		BufferedImage im = blank();
		boolean[][] m = mask(g -> g.fill(new RoundRectangle2D.Double(3, 3, 26, 26, 5, 5)));
		volume(im, m, ramp(0x1C0F38, 0x3C2278, 0x5E3CB0, 0x8A6AD6, 0xC8B4FF), 0.8, true);
		BufferedImage q = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = q.createGraphics();
		g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
		g.setColor(Color.WHITE);
		g.drawString("?", 10, 25);
		g.dispose();
		for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if (a(q.getRGB(x, y)) > 140) im.setRGB(x, y, c(0xE4D8FF));
		for (int[] p : new int[][]{{6, 25}, {7, 25}, {25, 6}, {24, 6}, {25, 7}}) im.setRGB(p[0], p[1], c(0x3FE0D0));
		save("item/hidden_item", im);
	}
}
