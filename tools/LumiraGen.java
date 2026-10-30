import ru.admincontrol.lumira.LumiraIds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Генератор игровых ресурсов «Рощи Люмиры»: текстуры (процедурные, отдельные PNG для каждого предмета и блока),
 * модели, состояния блоков, таблицы добычи, рецепты, теги и локализация.
 * Запуск: javac -d tmp src/main/java/ru/admincontrol/lumira/LumiraIds.java
 *         java -cp tmp tools/LumiraGen.java src/main/resources
 */
public class LumiraGen {
	static Path root;
	static final String NS = "admin_control";
	static Path assets, data, tex, models, states, loot, recipes;
	static final Map<String, String> RU = new LinkedHashMap<>();
	static final Map<String, String> EN = new LinkedHashMap<>();

	public static void main(String[] args) throws Exception {
		root = Path.of(args[0]);
		assets = root.resolve("assets/" + NS);
		data = root.resolve("data/" + NS);
		tex = assets.resolve("textures");
		models = assets.resolve("models");
		states = assets.resolve("blockstates");
		loot = data.resolve("loot_tables/blocks");
		recipes = data.resolve("recipes");
		for (Path p : new Path[]{tex.resolve("block"), tex.resolve("item"), tex.resolve("models/armor"),
				tex.resolve("entity/signs/hanging"), models.resolve("block"), models.resolve("item"), states, loot, recipes}) {
			Files.createDirectories(p);
		}
		ores();
		metals();
		trees();
		stations();
		items();
		toolsAndArmor();
		necklacesAndCoins();
		recipesAll();
		tags();
		lang();
		System.out.println("OK");
	}

	// ================= утилиты =================

	static void write(Path p, String s) throws Exception {
		Files.createDirectories(p.getParent());
		Files.writeString(p, s, StandardCharsets.UTF_8);
	}

	static void png(Path p, BufferedImage img) throws Exception {
		Files.createDirectories(p.getParent());
		ImageIO.write(img, "png", p.toFile());
	}

	static BufferedImage img(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	static int rgb(int c) {
		return 0xFF000000 | c;
	}

	static int shade(int c, double f) {
		int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * f));
		int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * f));
		int b = (int) Math.max(0, Math.min(255, (c & 255) * f));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	static double rnd(int x, int y, int seed) {
		long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	static int seedOf(String s) {
		return s.hashCode() & 0x7FFFFFFF;
	}

	static void json(Path p, String s) throws Exception {
		write(p, s + "\n");
	}

	static String q(String s) {
		return "\"" + s + "\"";
	}

	static String grid(String... rows) {
		return String.join("\n", rows);
	}

	// ================= руды и блоки металлов =================

	record OreArt(int base, int a, int b) {
	}

	static final Map<String, OreArt> ORE_ART = new HashMap<>();

	static {
		ORE_ART.put("spark_coal_ore", new OreArt(0x70675D, 0x15151A, 0xF0922B));
		ORE_ART.put("lumirite_ore", new OreArt(0x5A616C, 0x3FE0CE, 0xB8F5EE));
		ORE_ART.put("zaremed_ore", new OreArt(0x6C5645, 0xE88A2C, 0xFFD27A));
		ORE_ART.put("twilight_iron_ore", new OreArt(0x35313D, 0x8F7AC9, 0x4F4A66));
		ORE_ART.put("starsilver_ore", new OreArt(0x4D5B70, 0xE2EAF5, 0x4F7FE0));
		ORE_ART.put("dew_quartz_ore", new OreArt(0x8E9AA0, 0x9EF2D4, 0xE8FFF6));
		ORE_ART.put("echolite_ore", new OreArt(0x5E5971, 0xB28CF0, 0x6A3FB8));
		ORE_ART.put("heart_crystal_ore", new OreArt(0x2F2B31, 0xFF7FA8, 0xFFC857));
		ORE_ART.put("jarolite_ore", new OreArt(0x67635F, 0x8E1E1E, 0xF4E2B6));
	}

	static BufferedImage stoneTexture(int base, String seed, boolean deep) {
		BufferedImage im = img(16, 16);
		int s = seedOf(seed);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double n = rnd(x, y, s) * 0.5 + rnd(x / 2, y / 2, s + 5) * 0.5;
				im.setRGB(x, y, shade(base, 0.82 + n * 0.3 - (deep ? 0.12 : 0)));
			}
		}
		return im;
	}

	static void ores() throws Exception {
		for (LumiraIds.Ore o : LumiraIds.ORES) {
			OreArt art = ORE_ART.get(o.id());
			BufferedImage im = stoneTexture(art.base, o.id(), o.material().equals("deepslate"));
			int s = seedOf(o.id());
			boolean crystal = !o.raw() && (o.id().contains("quartz") || o.id().contains("echolite") || o.id().contains("heart"));
			for (int i = 0; i < 9; i++) {
				int cx = 1 + (int) (rnd(i, 1, s) * 13);
				int cy = 1 + (int) (rnd(i, 2, s) * 13);
				int len = 2 + (int) (rnd(i, 3, s) * 2);
				for (int d = 0; d < len; d++) {
					int px = cx + (crystal ? 0 : d % 2);
					int py = cy + d;
					int w = crystal ? 2 : 1;
					for (int k = 0; k < w; k++) {
						if (px + k < 16 && py < 16) {
							im.setRGB(px + k, py, rnd(px, py, s + 9) > 0.5 ? rgb(art.a) : rgb(art.b));
						}
					}
				}
			}
			png(tex.resolve("block/" + o.id() + ".png"), im);
			cubeAll(o.id(), "block/" + o.id());
			oreLoot(o.id(), o.drop(), true);
		}
	}

	static void cubeAll(String id, String texture) throws Exception {
		json(models.resolve("block/" + id + ".json"), "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":" + q(NS + ":" + texture) + "}}");
		json(states.resolve(id + ".json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/" + id) + "}}}");
		blockItem(id);
	}

	static void blockItem(String id) throws Exception {
		json(models.resolve("item/" + id + ".json"), "{\"parent\":" + q(NS + ":block/" + id) + "}");
	}

	static void metals() throws Exception {
		int[][] cols = {{0x58D3C7, 0xB9CBD2, 0x2C7C75}, {0xD27A3C, 0xF0B05A, 0x8A4A1E}, {0x3D3748, 0x7E6AB8, 0x211D29}, {0xD7E0EC, 0x4A78E0, 0x8E9BB0}};
		for (int i = 0; i < LumiraIds.METALS.size(); i++) {
			LumiraIds.Metal m = LumiraIds.METALS.get(i);
			BufferedImage im = img(16, 16);
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					boolean edge = x % 8 == 0 || y % 8 == 0;
					double n = rnd(x, y, seedOf(m.id()));
					int c = edge ? cols[i][2] : mix(cols[i][0], cols[i][1], (x + y) % 8 < 2 ? 0.5 : n * 0.25);
					im.setRGB(x, y, shade(c, 0.92 + n * 0.12));
				}
			}
			png(tex.resolve("block/" + m.id() + "_block.png"), im);
			cubeAll(m.id() + "_block", "block/" + m.id() + "_block");
			selfDrop(m.id() + "_block", null, null);
		}
	}

	// ================= деревья =================

	static BufferedImage bark(LumiraIds.Tree t, boolean stripped) {
		BufferedImage im = img(16, 16);
		int base = stripped ? mix(t.bark(), t.ring(), 0.45) : t.bark();
		int dark = stripped ? shade(base, 0.8) : t.barkDark();
		int s = seedOf(t.id() + stripped);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double stripe = rnd(x, 0, s) > 0.55 ? 1 : 0;
				double n = rnd(x, y / 3, s + 3);
				int c = stripe > 0 ? dark : base;
				if (t.id().equals("star_birch") && !stripped && rnd(x / 2, y / 2, s) > 0.78) {
					c = t.barkDark();
				}
				if (t.id().equals("crimson_elm") && !stripped && y % 5 == 2 && rnd(x, y, s) > 0.4) {
					c = t.barkDark();
				}
				if (t.id().equals("zarelist") && !stripped && rnd(x, y, s + 8) > 0.9) {
					c = t.ring();
				}
				im.setRGB(x, y, shade(c, 0.88 + n * 0.22));
			}
		}
		return im;
	}

	static BufferedImage rings(LumiraIds.Tree t, boolean stripped) {
		BufferedImage im = img(16, 16);
		int s = seedOf(t.id() + "ring" + stripped);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
				int c;
				if (d > 6.6) {
					c = stripped ? shade(t.bark(), 1.05) : t.barkDark();
				} else {
					int ring = (int) d;
					c = ring % 2 == 0 ? t.ring() : shade(t.ring(), 0.8);
					if (t.id().equals("lumira_willow") && ring % 2 == 1) {
						c = mix(t.ring(), 0x6FB6E8, 0.5);
					}
					if (t.id().equals("malachite_sequoia") && d > 5.6) {
						c = 0x4A8F52;
					}
				}
				im.setRGB(x, y, shade(c, 0.94 + rnd(x, y, s) * 0.1));
			}
		}
		return im;
	}

	static BufferedImage planks(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		int s = seedOf(t.id() + "pl");
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				boolean seam = y % 4 == 0;
				int c = seam ? t.planksDark() : t.planks();
				if (t.id().equals("star_birch") && rnd(x / 3, y, s) > 0.8) {
					c = 0x8FA7D6;
				}
				double n = rnd(x + (y / 4) * 5, y / 4, s);
				im.setRGB(x, y, shade(c, 0.9 + n * 0.2));
			}
		}
		return im;
	}

	static BufferedImage leaves(LumiraIds.Tree t, int variant) {
		BufferedImage im = img(16, 16);
		int s = seedOf(t.id() + "lf" + variant);
		double hole = t.id().equals("moon_ash") ? 0.22 : t.id().equals("lumira_willow") ? 0.18 : 0.1;
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double n = rnd(x, y, s);
				if (variant == 0 && n < hole) {
					continue;
				}
				if (variant == 2 && n < hole * 0.5) {
					continue;
				}
				int c = rnd(x / 2, y / 2, s + 4) > 0.5 ? t.leaf() : t.leafDark();
				if (t.id().equals("star_birch") && rnd(x, y, s + 6) > 0.93) {
					c = 0xFFFFFF;
				}
				if (variant == 1) {
					c = shade(c, 1.08);
				}
				im.setRGB(x, y, shade(c, 0.85 + n * 0.3));
			}
		}
		return im;
	}

	static BufferedImage sapling(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		int stem = t.bark();
		for (int y = 8; y < 16; y++) {
			im.setRGB(7, y, rgb(stem));
			im.setRGB(8, y, rgb(shade(stem, 0.8)));
		}
		int[][] pts;
		switch (t.id()) {
			case "lumira_willow" -> pts = new int[][]{{7, 7}, {6, 7}, {8, 7}, {5, 8}, {5, 9}, {5, 10}, {9, 8}, {10, 9}, {10, 10}, {10, 11}, {7, 6}, {7, 5}};
			case "twilight_cedar", "malachite_sequoia" -> pts = new int[][]{{7, 3}, {7, 4}, {6, 5}, {7, 5}, {8, 5}, {5, 7}, {6, 7}, {7, 7}, {8, 7}, {9, 7}, {4, 9}, {5, 9}, {6, 9}, {7, 9}, {8, 9}, {9, 9}, {10, 9}};
			case "star_birch" -> pts = new int[][]{{4, 5}, {5, 4}, {6, 5}, {5, 6}, {9, 5}, {10, 4}, {11, 5}, {10, 6}, {7, 7}, {8, 7}};
			case "moon_ash" -> pts = new int[][]{{5, 5}, {6, 4}, {6, 6}, {9, 5}, {10, 4}, {10, 6}, {7, 7}, {8, 3}, {8, 2}};
			default -> pts = new int[][]{{5, 5}, {6, 5}, {5, 6}, {6, 6}, {9, 5}, {10, 5}, {9, 6}, {10, 6}, {7, 8}, {8, 8}, {7, 9}, {8, 9}};
		}
		for (int[] p : pts) {
			im.setRGB(p[0], p[1], rgb((p[0] + p[1]) % 2 == 0 ? t.leaf() : t.leafDark()));
		}
		return im;
	}

	static BufferedImage doorHalf(LumiraIds.Tree t, boolean top) {
		BufferedImage im = planks(t);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				boolean frame = x < 2 || x > 13 || y < 2 || y > 13;
				if (frame) {
					im.setRGB(x, y, shade(t.planksDark(), 0.9 + rnd(x, y, 3) * 0.1));
				}
				if (top && y >= 4 && y <= 8 && x >= 4 && x <= 11 && (x == 7 || x == 8 || y == 6)) {
					im.setRGB(x, y, shade(t.planksDark(), 0.75));
				}
				if (!top && x == 12 && y == 3) {
					im.setRGB(x, y, rgb(0xC8C8C8));
				}
			}
		}
		return im;
	}

	static BufferedImage trapdoor(LumiraIds.Tree t) {
		BufferedImage im = planks(t);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				if (x < 2 || x > 13 || y < 2 || y > 13 || x == y || x + y == 15) {
					im.setRGB(x, y, shade(t.planksDark(), 0.85));
				}
			}
		}
		return im;
	}

	static void trees() throws Exception {
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			png(tex.resolve("block/" + s + "_log.png"), bark(t, false));
			png(tex.resolve("block/" + s + "_log_top.png"), rings(t, false));
			png(tex.resolve("block/stripped_" + s + "_log.png"), bark(t, true));
			png(tex.resolve("block/stripped_" + s + "_log_top.png"), rings(t, true));
			png(tex.resolve("block/" + s + "_planks.png"), planks(t));
			png(tex.resolve("block/" + s + "_leaves.png"), leaves(t, 0));
			png(tex.resolve("block/" + s + "_leaves_top.png"), leaves(t, 1));
			png(tex.resolve("block/" + s + "_leaves_bottom.png"), leaves(t, 2));
			BufferedImage sap = sapling(t);
			png(tex.resolve("block/" + s + "_sapling.png"), sap);
			png(tex.resolve("item/" + s + "_sapling.png"), sap);
			BufferedImage dt = doorHalf(t, true), db = doorHalf(t, false);
			png(tex.resolve("block/" + s + "_door_top.png"), dt);
			png(tex.resolve("block/" + s + "_door_bottom.png"), db);
			png(tex.resolve("block/" + s + "_trapdoor.png"), trapdoor(t));
			// предметы-иконки
			png(tex.resolve("item/" + s + "_door.png"), doorIcon(t));
			png(tex.resolve("item/" + s + "_sign.png"), signIcon(t, false));
			png(tex.resolve("item/" + s + "_hanging_sign.png"), signIcon(t, true));
			// сущности табличек
			BufferedImage board = img(64, 32);
			BufferedImage pl = planks(t);
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 32; y++) {
					board.setRGB(x, y, pl.getRGB(x % 16, y % 16));
				}
			}
			png(tex.resolve("entity/signs/" + s + ".png"), board);
			BufferedImage hang = img(16, 16);
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					hang.setRGB(x, y, pl.getRGB(x, y));
				}
			}
			png(tex.resolve("entity/signs/hanging/" + s + ".png"), hang);

			// модели/состояния
			String tn = NS + ":block/";
			// брёвна
			json(models.resolve("block/" + s + "_log.json"), "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":" + q(tn + s + "_log_top") + ",\"side\":" + q(tn + s + "_log") + "}}");
			json(models.resolve("block/stripped_" + s + "_log.json"), "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":" + q(tn + "stripped_" + s + "_log_top") + ",\"side\":" + q(tn + "stripped_" + s + "_log") + "}}");
			json(states.resolve(s + "_log.json"), axisState(NS + ":block/" + s + "_log"));
			json(states.resolve("stripped_" + s + "_log.json"), axisState(NS + ":block/stripped_" + s + "_log"));
			// древесина с корой со всех сторон
			json(models.resolve("block/" + s + "_wood.json"), "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":" + q(tn + s + "_log") + ",\"side\":" + q(tn + s + "_log") + "}}");
			json(models.resolve("block/stripped_" + s + "_wood.json"), "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":" + q(tn + "stripped_" + s + "_log") + ",\"side\":" + q(tn + "stripped_" + s + "_log") + "}}");
			json(states.resolve(s + "_wood.json"), axisState(NS + ":block/" + s + "_wood"));
			json(states.resolve("stripped_" + s + "_wood.json"), axisState(NS + ":block/stripped_" + s + "_wood"));
			for (String id : new String[]{s + "_log", "stripped_" + s + "_log", s + "_wood", "stripped_" + s + "_wood"}) {
				blockItem(id);
			}
			// доски и производные
			cubeAll(s + "_planks", "block/" + s + "_planks");
			// листва: верх, бок, низ
			String leafTex = "{\"particle\":" + q(tn + s + "_leaves") + ",\"down\":" + q(tn + s + "_leaves_bottom") + ",\"up\":" + q(tn + s + "_leaves_top")
					+ ",\"north\":" + q(tn + s + "_leaves") + ",\"south\":" + q(tn + s + "_leaves") + ",\"east\":" + q(tn + s + "_leaves") + ",\"west\":" + q(tn + s + "_leaves") + "}";
			json(models.resolve("block/" + s + "_leaves.json"), "{\"parent\":\"minecraft:block/cube\",\"textures\":" + leafTex + "}");
			json(states.resolve(s + "_leaves.json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/" + s + "_leaves") + "}}}");
			blockItem(s + "_leaves");
			// саженец
			json(models.resolve("block/" + s + "_sapling.json"), "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":" + q(tn + s + "_sapling") + "}}");
			json(states.resolve(s + "_sapling.json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/" + s + "_sapling") + "}}}");
			json(models.resolve("item/" + s + "_sapling.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + s + "_sapling") + "}}");
			woodShapes(s);
			// двери/таблички (предметы)
			json(models.resolve("item/" + s + "_door.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + s + "_door") + "}}");
			json(models.resolve("item/" + s + "_sign.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + s + "_sign") + "}}");
			json(models.resolve("item/" + s + "_hanging_sign.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + s + "_hanging_sign") + "}}");
			String signModel = "{\"textures\":{\"particle\":" + q(tn + s + "_planks") + "}}";
			for (String id : new String[]{s + "_sign", s + "_wall_sign", s + "_hanging_sign", s + "_wall_hanging_sign"}) {
				json(models.resolve("block/" + id + ".json"), signModel);
				json(states.resolve(id + ".json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/" + id) + "}}}");
			}
			treeLoot(s);
			String ruT = t.adj();
			String[] ids = {"log", "stripped_log", "wood", "stripped_wood", "planks", "leaves", "sapling", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "sign", "hanging_sign"};
			for (int i = 0; i < ids.length; i++) {
				String key = ids[i].startsWith("stripped") ? "stripped_" + s + "_" + ids[i].substring(9) : s + "_" + ids[i];
				name(key, LumiraIds.TREE_PARTS[i][1].formatted(ruT), enName(key));
			}
			name(s + "_wall_sign", "Настенная табличка " + ruT, enName(s + "_wall_sign"));
			name(s + "_wall_hanging_sign", "Настенная подвесная табличка " + ruT, enName(s + "_wall_hanging_sign"));
		}
	}

	static String axisState(String model) {
		return "{\"variants\":{\"axis=y\":{\"model\":" + q(model) + "},\"axis=z\":{\"model\":" + q(model) + ",\"x\":90},\"axis=x\":{\"model\":" + q(model) + ",\"x\":90,\"y\":90}}}";
	}

	static BufferedImage doorIcon(LumiraIds.Tree t) {
		BufferedImage im = img(16, 16);
		BufferedImage p = planks(t);
		for (int x = 4; x < 12; x++) {
			for (int y = 1; y < 15; y++) {
				boolean edge = x == 4 || x == 11 || y == 1 || y == 14 || y == 7 || y == 8;
				im.setRGB(x, y, edge ? shade(t.planksDark(), 0.8) : p.getRGB(x, y));
			}
		}
		im.setRGB(10, 8, rgb(0xDADADA));
		return im;
	}

	static BufferedImage signIcon(LumiraIds.Tree t, boolean hanging) {
		BufferedImage im = img(16, 16);
		BufferedImage p = planks(t);
		for (int x = 2; x < 14; x++) {
			for (int y = hanging ? 6 : 2; y < (hanging ? 13 : 9); y++) {
				boolean edge = x == 2 || x == 13 || y == (hanging ? 6 : 2) || y == (hanging ? 12 : 8);
				im.setRGB(x, y, edge ? shade(t.planksDark(), 0.8) : p.getRGB(x, y));
			}
		}
		if (hanging) {
			for (int y = 1; y < 6; y++) {
				im.setRGB(4, y, rgb(0x6A6A72));
				im.setRGB(11, y, rgb(0x6A6A72));
			}
		} else {
			for (int y = 9; y < 15; y++) {
				im.setRGB(7, y, rgb(t.bark()));
				im.setRGB(8, y, rgb(t.barkDark()));
			}
		}
		return im;
	}

	static void woodShapes(String s) throws Exception {
		String tex = NS + ":block/" + s + "_planks";
		String tp = "{\"bottom\":" + q(tex) + ",\"top\":" + q(tex) + ",\"side\":" + q(tex) + "}";
		for (String[] m : new String[][]{{"stairs", "stairs"}, {"stairs_inner", "inner_stairs"}, {"stairs_outer", "outer_stairs"}}) {
			String file = m[0].equals("stairs") ? s + "_stairs" : s + "_" + m[0];
			json(models.resolve("block/" + file + ".json"), "{\"parent\":\"minecraft:block/" + m[1] + "\",\"textures\":" + tp + "}");
		}
		json(models.resolve("block/" + s + "_slab.json"), "{\"parent\":\"minecraft:block/slab\",\"textures\":" + tp + "}");
		json(models.resolve("block/" + s + "_slab_top.json"), "{\"parent\":\"minecraft:block/slab_top\",\"textures\":" + tp + "}");
		json(models.resolve("block/" + s + "_fence_post.json"), "{\"parent\":\"minecraft:block/fence_post\",\"textures\":{\"texture\":" + q(tex) + "}}");
		json(models.resolve("block/" + s + "_fence_side.json"), "{\"parent\":\"minecraft:block/fence_side\",\"textures\":{\"texture\":" + q(tex) + "}}");
		json(models.resolve("block/" + s + "_fence_inventory.json"), "{\"parent\":\"minecraft:block/fence_inventory\",\"textures\":{\"texture\":" + q(tex) + "}}");
		for (String v : new String[]{"", "_open", "_wall", "_wall_open"}) {
			String parent = v.isEmpty() ? "template_fence_gate" : "template_fence_gate" + v;
			json(models.resolve("block/" + s + "_fence_gate" + v + ".json"), "{\"parent\":\"minecraft:block/" + parent + "\",\"textures\":{\"texture\":" + q(tex) + "}}");
		}
		String dt = NS + ":block/" + s + "_door_top", db = NS + ":block/" + s + "_door_bottom";
		for (String half : new String[]{"bottom", "top"}) {
			for (String hinge : new String[]{"left", "right"}) {
				for (String open : new String[]{"", "_open"}) {
					String name = half + "_" + hinge + open;
					json(models.resolve("block/" + s + "_door_" + name + ".json"), "{\"parent\":\"minecraft:block/door_" + name + "\",\"textures\":{\"top\":" + q(dt) + ",\"bottom\":" + q(db) + "}}");
				}
			}
		}
		String tr = NS + ":block/" + s + "_trapdoor";
		for (String v : new String[]{"bottom", "top", "open"}) {
			json(models.resolve("block/" + s + "_trapdoor_" + v + ".json"), "{\"parent\":\"minecraft:block/template_orientable_trapdoor_" + v + "\",\"textures\":{\"texture\":" + q(tr) + "}}");
		}
		for (String v : new String[]{"", "_pressed", "_inventory"}) {
			json(models.resolve("block/" + s + "_button" + v + ".json"), "{\"parent\":\"minecraft:block/button" + v + "\",\"textures\":{\"texture\":" + q(tex) + "}}");
		}
		json(models.resolve("block/" + s + "_pressure_plate.json"), "{\"parent\":\"minecraft:block/pressure_plate_up\",\"textures\":{\"texture\":" + q(tex) + "}}");
		json(models.resolve("block/" + s + "_pressure_plate_down.json"), "{\"parent\":\"minecraft:block/pressure_plate_down\",\"textures\":{\"texture\":" + q(tex) + "}}");
		// blockstates
		String m = NS + ":block/" + s;
		json(states.resolve(s + "_stairs.json"), stairsState(m + "_stairs", m + "_stairs_inner", m + "_stairs_outer"));
		json(states.resolve(s + "_slab.json"), "{\"variants\":{\"type=bottom\":{\"model\":" + q(m + "_slab") + "},\"type=top\":{\"model\":" + q(m + "_slab_top") + "},\"type=double\":{\"model\":" + q(m + "_planks") + "}}}");
		json(states.resolve(s + "_fence.json"), "{\"multipart\":[{\"apply\":{\"model\":" + q(m + "_fence_post") + "}}"
				+ ",{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":" + q(m + "_fence_side") + ",\"uvlock\":true}}"
				+ ",{\"when\":{\"east\":\"true\"},\"apply\":{\"model\":" + q(m + "_fence_side") + ",\"y\":90,\"uvlock\":true}}"
				+ ",{\"when\":{\"south\":\"true\"},\"apply\":{\"model\":" + q(m + "_fence_side") + ",\"y\":180,\"uvlock\":true}}"
				+ ",{\"when\":{\"west\":\"true\"},\"apply\":{\"model\":" + q(m + "_fence_side") + ",\"y\":270,\"uvlock\":true}}]}");
		json(models.resolve("item/" + s + "_fence.json"), "{\"parent\":" + q(m + "_fence_inventory") + "}");
		json(states.resolve(s + "_fence_gate.json"), gateState(m + "_fence_gate"));
		json(states.resolve(s + "_door.json"), doorState(m + "_door_"));
		json(states.resolve(s + "_trapdoor.json"), trapdoorState(m + "_trapdoor_"));
		json(states.resolve(s + "_button.json"), buttonState(m + "_button", m + "_button_pressed"));
		json(models.resolve("item/" + s + "_button.json"), "{\"parent\":" + q(m + "_button_inventory") + "}");
		json(states.resolve(s + "_pressure_plate.json"), "{\"variants\":{\"powered=false\":{\"model\":" + q(m + "_pressure_plate") + "},\"powered=true\":{\"model\":" + q(m + "_pressure_plate_down") + "}}}");
		for (String id : new String[]{"_stairs", "_slab", "_fence_gate", "_pressure_plate"}) {
			blockItem(s + id);
		}
		json(models.resolve("item/" + s + "_trapdoor.json"), "{\"parent\":" + q(m + "_trapdoor_bottom") + "}");
	}

	static int mod(int v) {
		return ((v % 360) + 360) % 360;
	}

	static String rot(String model, int x, int y, boolean uvlock) {
		StringBuilder sb = new StringBuilder("{\"model\":" + q(model));
		if (x != 0) {
			sb.append(",\"x\":").append(x);
		}
		if (y != 0) {
			sb.append(",\"y\":").append(mod(y));
		}
		if (uvlock) {
			sb.append(",\"uvlock\":true");
		}
		return sb.append("}").toString();
	}

	static String stairsState(String straight, String inner, String outer) {
		String[] facings = {"east", "south", "west", "north"};
		List<String> parts = new ArrayList<>();
		for (int f = 0; f < 4; f++) {
			int base = f * 90;
			for (String half : new String[]{"bottom", "top"}) {
				boolean top = half.equals("top");
				int x = top ? 180 : 0;
				String k = "facing=" + facings[f] + ",half=" + half + ",shape=";
				parts.add(q(k + "straight") + ":" + rot(straight, x, base, true));
				parts.add(q(k + "outer_right") + ":" + rot(outer, x, top ? base + 90 : base, true));
				parts.add(q(k + "outer_left") + ":" + rot(outer, x, top ? base : base - 90, true));
				parts.add(q(k + "inner_right") + ":" + rot(inner, x, top ? base + 90 : base, true));
				parts.add(q(k + "inner_left") + ":" + rot(inner, x, top ? base : base - 90, true));
			}
		}
		return "{\"variants\":{" + String.join(",", parts) + "}}";
	}

	static String gateState(String base) {
		String[] facings = {"south", "west", "north", "east"};
		List<String> parts = new ArrayList<>();
		for (int f = 0; f < 4; f++) {
			for (boolean wall : new boolean[]{false, true}) {
				for (boolean open : new boolean[]{false, true}) {
					String model = base + (wall ? "_wall" : "") + (open ? "_open" : "");
					parts.add(q("facing=" + facings[f] + ",in_wall=" + wall + ",open=" + open + ",powered=false") + ":" + rot(model, 0, f * 90, true));
					parts.add(q("facing=" + facings[f] + ",in_wall=" + wall + ",open=" + open + ",powered=true") + ":" + rot(model, 0, f * 90, true));
				}
			}
		}
		return "{\"variants\":{" + String.join(",", parts) + "}}";
	}

	static String doorState(String base) {
		String[] facings = {"east", "south", "west", "north"};
		List<String> parts = new ArrayList<>();
		for (int f = 0; f < 4; f++) {
			for (String half : new String[]{"lower", "upper"}) {
				for (String hinge : new String[]{"left", "right"}) {
					for (boolean open : new boolean[]{false, true}) {
						String model = base + (half.equals("lower") ? "bottom" : "top") + "_" + hinge + (open ? "_open" : "");
						int y = f * 90 + (open ? (hinge.equals("left") ? 90 : 270) : 0);
						for (String powered : new String[]{"false", "true"}) {
							parts.add(q("facing=" + facings[f] + ",half=" + half + ",hinge=" + hinge + ",open=" + open + ",powered=" + powered) + ":" + rot(model, 0, y, false));
						}
					}
				}
			}
		}
		return "{\"variants\":{" + String.join(",", parts) + "}}";
	}

	static String trapdoorState(String base) {
		String[] facings = {"north", "south", "east", "west"};
		int[] yClosed = {0, 0, 0, 0};
		int[] yOpen = {0, 180, 90, 270};
		int[] yOpenTop = {180, 0, 270, 90};
		List<String> parts = new ArrayList<>();
		for (int f = 0; f < 4; f++) {
			for (boolean open : new boolean[]{false, true}) {
				for (String half : new String[]{"bottom", "top"}) {
					String model = base + (open ? "open" : half);
					int x = open && half.equals("top") ? 180 : 0;
					int y = open ? (half.equals("top") ? yOpenTop[f] : yOpen[f]) : yClosed[f];
					for (String powered : new String[]{"false", "true"}) {
						parts.add(q("facing=" + facings[f] + ",half=" + half + ",open=" + open + ",powered=" + powered) + ":" + rot(model, x, y, false));
					}
				}
			}
		}
		return "{\"variants\":{" + String.join(",", parts) + "}}";
	}

	static String buttonState(String normal, String pressed) {
		List<String> parts = new ArrayList<>();
		String[] facings = {"north", "south", "east", "west"};
		int[] floorY = {0, 180, 90, 270};
		int[] ceilY = {180, 0, 270, 90};
		int[] wallY = {0, 180, 90, 270};
		for (int f = 0; f < 4; f++) {
			for (boolean powered : new boolean[]{false, true}) {
				String model = powered ? pressed : normal;
				parts.add(q("face=floor,facing=" + facings[f] + ",powered=" + powered) + ":" + rot(model, 0, floorY[f], false));
				parts.add(q("face=ceiling,facing=" + facings[f] + ",powered=" + powered) + ":" + rot(model, 180, ceilY[f], false));
				parts.add(q("face=wall,facing=" + facings[f] + ",powered=" + powered) + ":" + rot(model, 90, wallY[f], true));
			}
		}
		return "{\"variants\":{" + String.join(",", parts) + "}}";
	}

	// ================= станции =================

	static void stations() throws Exception {
		BufferedImage top = img(16, 16), side = img(16, 16), bottom = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				boolean grid = x % 4 == 0 || y % 4 == 0;
				top.setRGB(x, y, grid ? rgb(0x39D6C8) : shade(0x5A626B, 0.9 + rnd(x, y, 1) * 0.2));
				boolean frame = x < 2 || x > 13;
				boolean brace = (y == 4 || y == 11) && !frame;
				side.setRGB(x, y, frame ? shade(0xE2C58A, 0.9 + rnd(x, y, 2) * 0.15) : brace ? rgb(0xC9763E) : shade(0xD8BB7E, 0.85 + rnd(x, y, 3) * 0.2));
				if (y < 3) {
					side.setRGB(x, y, shade(0x5A626B, 0.85));
				}
				bottom.setRGB(x, y, shade(0xBFA06A, 0.85 + rnd(x, y, 4) * 0.2));
			}
		}
		png(tex.resolve("block/rune_workbench_top.png"), top);
		png(tex.resolve("block/rune_workbench_side.png"), side);
		png(tex.resolve("block/rune_workbench_bottom.png"), bottom);
		json(models.resolve("block/rune_workbench.json"), "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{\"top\":" + q(NS + ":block/rune_workbench_top") + ",\"bottom\":" + q(NS + ":block/rune_workbench_bottom") + ",\"side\":" + q(NS + ":block/rune_workbench_side") + "}}");
		json(states.resolve("rune_workbench.json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/rune_workbench") + "}}}");
		blockItem("rune_workbench");
		selfDrop("rune_workbench", null, null);
		BufferedImage body = img(16, 16), atop = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				body.setRGB(x, y, (x + y) % 8 == 0 ? rgb(0x39D6C8) : shade(0x4C545E, 0.85 + rnd(x, y, 5) * 0.25));
				boolean groove = x > 3 && x < 12 && y > 3 && y < 12;
				atop.setRGB(x, y, groove ? shade(0x2F353C, 0.9 + rnd(x, y, 6) * 0.2) : shade(0x6A737E, 0.9 + rnd(x, y, 7) * 0.2));
			}
		}
		atop.setRGB(7, 7, rgb(0x39D6C8));
		atop.setRGB(8, 8, rgb(0x39D6C8));
		png(tex.resolve("block/rune_anvil_body.png"), body);
		png(tex.resolve("block/rune_anvil_top.png"), atop);
		json(models.resolve("block/rune_anvil.json"), "{\"parent\":\"minecraft:block/template_anvil\",\"textures\":{\"body\":" + q(NS + ":block/rune_anvil_body") + ",\"top\":" + q(NS + ":block/rune_anvil_top") + "}}");
		json(states.resolve("rune_anvil.json"), "{\"variants\":{\"\":{\"model\":" + q(NS + ":block/rune_anvil") + "}}}");
		blockItem("rune_anvil");
		selfDrop("rune_anvil", null, null);
	}

	// ================= простые предметы =================

	static int[] palette(String id) {
		return switch (id) {
			case "lumirite" -> new int[]{0x58D3C7, 0xB9CBD2, 0x2C7C75};
			case "zaremed" -> new int[]{0xD27A3C, 0xF0B05A, 0x8A4A1E};
			case "twilight_iron" -> new int[]{0x4A4358, 0x9A86D6, 0x25212D};
			default -> new int[]{0xD7E0EC, 0x5C88F0, 0x8E9BB0};
		};
	}

	static void put(BufferedImage im, int x, int y, int c) {
		if (x >= 0 && x < im.getWidth() && y >= 0 && y < im.getHeight()) {
			im.setRGB(x, y, c);
		}
	}

	static BufferedImage ingotIcon(int[] p) {
		BufferedImage im = img(16, 16);
		for (int y = 5; y <= 11; y++) {
			for (int x = 2 + (11 - y) / 2; x <= 11 + (11 - y) / 2 - 1; x++) {
				int c = p[0];
				if (y == 5) c = p[1];
				else if (y == 11 || x == 2 + (11 - y) / 2) c = p[2];
				put(im, x, y, shade(c, 0.95 + rnd(x, y, 8) * 0.1));
			}
		}
		for (int x = 5; x < 10; x++) put(im, x, 8, rgb(p[1]));
		return im;
	}

	static BufferedImage rawIcon(int[] base, int core, String seed) {
		BufferedImage im = img(16, 16);
		int s = seedOf(seed);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double d = Math.hypot(x - 7.5, y - 8) + (rnd(x, y, s) - 0.5) * 3.2;
				if (d < 5.6) {
					int c = rnd(x / 2, y / 2, s + 1) > 0.5 ? base[0] : base[2];
					if (d < 2.4) c = core;
					put(im, x, y, shade(c, 0.85 + rnd(x, y, s + 2) * 0.3));
				}
			}
		}
		return im;
	}

	static BufferedImage crystalIcon(int main, int light, int dark) {
		BufferedImage im = img(16, 16);
		for (int y = 1; y < 15; y++) {
			double half = y < 8 ? 1 + (y - 1) * 0.65 : 1 + (14 - y) * 0.75;
			for (int x = (int) (8 - half); x <= (int) (7 + half); x++) {
				int c = x < 7 ? main : dark;
				if (x == (int) (8 - half) + 1 && y < 9) c = light;
				put(im, x, y, rgb(c));
			}
		}
		put(im, 6, 4, rgb(0xFFFFFF));
		return im;
	}

	static BufferedImage lumpIcon(int main, int accent, String seed) {
		BufferedImage im = rawIcon(new int[]{main, main, shade(main, 0.7)}, main, seed);
		int s = seedOf(seed);
		for (int i = 0; i < 6; i++) {
			put(im, 4 + (int) (rnd(i, 1, s) * 8), 4 + (int) (rnd(i, 2, s) * 8), rgb(accent));
		}
		return im;
	}

	static BufferedImage briquetteIcon(int main, int accent) {
		BufferedImage im = img(16, 16);
		for (int x = 3; x < 13; x++) {
			for (int y = 3; y < 13; y++) {
				boolean edge = x == 3 || x == 12 || y == 3 || y == 12;
				boolean cross = x == 7 || x == 8 || y == 7 || y == 8;
				put(im, x, y, edge ? shade(main, 0.6) : cross ? rgb(accent) : shade(main, 0.9 + rnd(x, y, 12) * 0.2));
			}
		}
		return im;
	}

	static BufferedImage lensIcon(int main, int light) {
		BufferedImage im = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)) + Math.hypot(x - 7.5, y - 7.5) * 0.35;
				if (d < 7) {
					put(im, x, y, d > 6 ? rgb(shade(main, 0.65)) : (x + y < 14 ? rgb(light) : rgb(main)));
				}
			}
		}
		return im;
	}

	static BufferedImage frameCrystal(int crystalMain, int crystalLight, int frame) {
		BufferedImage im = crystalIcon(crystalMain, crystalLight, shade(crystalMain, 0.6));
		for (int i = 1; i < 15; i++) {
			put(im, 1, i, rgb(frame));
			put(im, 14, i, rgb(frame));
		}
		for (int i = 1; i < 15; i++) {
			put(im, i, 0, rgb(frame));
			put(im, i, 15, rgb(frame));
		}
		return im;
	}

	static BufferedImage prismIcon() {
		BufferedImage im = img(16, 16);
		for (int y = 2; y < 14; y++) {
			int half = (y - 2) / 2 + 1;
			for (int x = 8 - half; x < 8 + half; x++) {
				put(im, x, y, rgb(x < 8 ? 0xFF7FA8 : 0xD9567F));
			}
		}
		for (int x = 2; x < 14; x++) put(im, x, 13, rgb(0xD7E0EC));
		put(im, 7, 5, rgb(0xFFE08A));
		return im;
	}

	static BufferedImage threadIcon() {
		BufferedImage im = img(16, 16);
		for (int x = 1; x < 15; x++) {
			put(im, x, 8 + (int) Math.round(3 * Math.sin(x * 0.7)), rgb(0xE8F4F2));
			put(im, x, 9 + (int) Math.round(3 * Math.sin(x * 0.7)), rgb(0x7FD9CF));
		}
		return im;
	}

	static BufferedImage staffIcon() {
		BufferedImage im = img(16, 16);
		for (int i = 0; i < 11; i++) {
			put(im, 3 + i, 13 - i, rgb(0x8B6A3E));
			put(im, 4 + i, 13 - i, rgb(0x6A4E2B));
		}
		for (int x = 10; x < 15; x++) for (int y = 1; y < 6; y++) put(im, x, y, (x + y) % 3 == 0 ? rgb(0xE8FFF6) : rgb(0x7FF0D4));
		put(im, 9, 6, rgb(0xC9763E));
		put(im, 10, 6, rgb(0xC9763E));
		return im;
	}

	static void simple(String id, BufferedImage im) throws Exception {
		png(tex.resolve("item/" + id + ".png"), im);
		json(models.resolve("item/" + id + ".json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + id) + "}}");
	}

	static void items() throws Exception {
		json(models.resolve("item/hidden_item.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"admin_control:item/hidden_item\"}}");
		simple("spark_coal", lumpIcon(0x1D1D22, 0xF0922B, "spark"));
		simple("spark_briquette", briquetteIcon(0x1F1F24, 0xE58A2A));
		simple("jarolite", lumpIcon(0x7A2020, 0xF4E2B6, "jarolite"));
		simple("jarolite_briquette", briquetteIcon(0x6E1C1C, 0xF4E2B6));
		simple("dew_quartz", crystalIcon(0x9EF2D4, 0xE8FFF6, 0x5CB89A));
		simple("echolite", crystalIcon(0xB28CF0, 0xE2CFFF, 0x6A3FB8));
		simple("heart_crystal", crystalIcon(0xFF7FA8, 0xFFE08A, 0xB8456C));
		simple("dew_lens", lensIcon(0x7FE8C6, 0xCFFFF0));
		simple("echo_focus", frameCrystal(0xB28CF0, 0xE2CFFF, 0xC9763E));
		simple("heart_prism", prismIcon());
		simple("rune_thread", threadIcon());
		simple("rune_staff", staffIcon());
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			int[] p = palette(m.id());
			simple(m.id() + "_ingot", ingotIcon(p));
			String raw = "raw_" + m.id();
			simple(raw, rawIcon(p, switch (m.id()) {
				case "lumirite" -> 0x39E0CE;
				case "zaremed" -> 0xFFC24A;
				case "twilight_iron" -> 0x8A76C8;
				default -> 0x4F7FE0;
			}, raw));
		}
	}

	// ================= инструменты и броня =================

	static final String[][] TOOL_GRIDS = {
			// sword
			{"..............hH", ".............hHd", "............hHd.", "...........hHd..", "..........hHd...", ".........hHd....", "..S.....hHd.....", "...S...hHd......", "....S.hHd.......", ".....SHHd.......", "......SSd.......", ".....SSS........", "....SS..S.......", "...SS...........", "..SS............", "................"},
			// axe
			{"................", ".......hhH......", "......hHHHd.....", ".....hHHHHHd....", ".....hHHHHHd....", "......HHHHd.....", ".....SSHHd......", "....SS..d.......", "...SS...........", "..SS............", ".SS.............", "SS..............", "S...............", "................", "................", "................"},
			// pickaxe
			{"................", "....hhHHHHhh....", "..hHHHHHHHHHHd..", ".hHHd......dHHd.", "hHd...S.....dHd.", "Hd...SS......dd.", "....SS..........", "...SS...........", "..SS............", ".SS.............", "SS..............", "S...............", "................", "................", "................", "................"},
			// shovel
			{"..........hHHd..", ".........hHHHHd.", ".........HHHHHd.", ".........hHHHdd.", "..........hHdd..", ".........SSd....", "........SS.......", ".......SS........", "......SS.........", ".....SS..........", "....SS...........", "...SS............", "..SS.............", "................", "................", "................"},
			// hoe
			{"................", "......hhHHHhh...", ".....hHHHHHHHd..", ".....hHd...dHHd.", "............dHd.", ".......SS....dd.", "......SS........", ".....SS.........", "....SS..........", "...SS...........", "..SS............", ".SS.............", "................", "................", "................", "................"}};

	static BufferedImage drawGrid(String[] rows, Map<Character, Integer> pal) {
		BufferedImage im = img(16, 16);
		for (int y = 0; y < 16; y++) {
			String r = y < rows.length ? rows[y] : "";
			for (int x = 0; x < 16 && x < r.length(); x++) {
				Integer c = pal.get(r.charAt(x));
				if (c != null) {
					put(im, x, y, c);
				}
			}
		}
		return im;
	}

	static final String[][] ARMOR_GRIDS = {
			// helmet
			{"................", "................", "....DDMMMMDD....", "...DMMLLLLMMD...", "..DMMLLLLLLMMD..", "..DMLLMMMMLLMD..", "..DMM.DDDD.MMD..", "..DMM.A..A.MMD..", "..DM........MD..", "..DD........DD..", "................", "................", "................", "................", "................", "................"},
			// chestplate
			{"................", "..DMMD....DMMD..", ".DMMMMD..DMMMMD.", ".DMLLMMDDMMLLMD.", ".DMLMMMAAMMMLMD.", ".DMMMMMAAMMMMMD.", "..DMMMMMMMMMMD..", "..DDMMMMMMMMDD..", "...DMMMMMMMMD...", "...DMMLMMLMMD...", "...DMMMMMMMMD...", "...DDDDDDDDDD...", "................", "................", "................", "................"},
			// leggings
			{"................", "................", "...DDDDDDDDDD...", "..DMMMMMMMMMMD..", "..DMLMMAAMMLMD..", "..DMMMMDDMMMMD..", "..DMMMD..DMMMD..", "..DMLMD..DMLMD..", "..DMMMD..DMMMD..", "..DMMMD..DMMMD..", "..DMLMD..DMLMD..", "..DDDDD..DDDDD..", "................", "................", "................", "................"},
			// boots
			{"................", "................", "................", "................", "................", "................", "..DDDD....DDDD..", "..DMMD....DMMD..", "..DMMD....DMMD..", ".DMLMD....DMLMD.", ".DMMMDDD..DMMMDD", ".DMMMMMMD.DMMMMM", ".DAAMMMMD.DAAMMM", ".DDDDDDDD.DDDDDD", "................", "................"}};

	static void toolsAndArmor() throws Exception {
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			int[] p = palette(m.id());
			Map<Character, Integer> pal = new HashMap<>();
			pal.put('H', rgb(p[0]));
			pal.put('h', rgb(p[1]));
			pal.put('d', rgb(p[2]));
			pal.put('S', rgb(0x8B6A3E));
			pal.put('s', rgb(0x5E4527));
			String[] kinds = LumiraIds.TOOL_KINDS;
			for (int i = 0; i < kinds.length; i++) {
				BufferedImage im = drawGrid(TOOL_GRIDS[i], pal);
				// узор металла на головке: полумесяц/ромб/трещина/звезда
				put(im, 8, 3, rgb(p[1]));
				String id = m.id() + "_" + kinds[i];
				png(tex.resolve("item/" + id + ".png"), im);
				json(models.resolve("item/" + id + ".json"), "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":" + q(NS + ":item/" + id) + "}}");
				name(id, toolRu(m, kinds[i]), enName(id));
			}
			if (m.hasArmor()) {
				Map<Character, Integer> ap = new HashMap<>();
				ap.put('M', rgb(p[0]));
				ap.put('L', rgb(p[1]));
				ap.put('D', rgb(p[2]));
				ap.put('A', rgb(m.id().equals("starsilver") ? 0x4F7FE0 : 0xA58BE0));
				for (int i = 0; i < 4; i++) {
					String id = m.id() + "_" + LumiraIds.ARMOR_KINDS[i];
					png(tex.resolve("item/" + id + ".png"), drawGrid(ARMOR_GRIDS[i], ap));
					json(models.resolve("item/" + id + ".json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":" + q(NS + ":item/" + id) + "}}");
					name(id, armorRu(m, LumiraIds.ARMOR_KINDS[i]), enName(id));
				}
				for (int layer = 1; layer <= 2; layer++) {
					BufferedImage w = img(64, 32);
					for (int x = 0; x < 64; x++) {
						for (int y = 0; y < 32; y++) {
							boolean seam = (x % 8 == 0) || (y % 8 == 0);
							boolean accent = (x + y) % 16 == 0;
							int c = seam ? p[2] : accent ? (m.id().equals("starsilver") ? 0x4F7FE0 : 0x8F7AC9) : mix(p[0], p[1], rnd(x / 2, y / 2, layer + seedOf(m.id())) * 0.4);
							w.setRGB(x, y, shade(c, 0.9 + rnd(x, y, 4) * 0.2));
						}
					}
					png(tex.resolve("models/armor/" + m.id() + "_layer_" + layer + ".png"), w);
				}
			}
		}
	}

	static String toolRu(LumiraIds.Metal m, String kind) {
		return switch (kind) {
			case "sword" -> m.adjM() + " меч";
			case "axe" -> m.adjM() + " топор";
			case "pickaxe" -> m.adjF() + " кирка";
			case "shovel" -> m.adjF() + " лопата";
			default -> m.adjF() + " мотыга";
		};
	}

	static String armorRu(LumiraIds.Metal m, String kind) {
		return switch (kind) {
			case "helmet" -> m.adjM() + " шлем";
			case "chestplate" -> m.adjM() + " нагрудник";
			case "leggings" -> m.adjF().replace("ая", "ые") + " поножи";
			default -> m.adjM().substring(0, m.adjM().length() - 1) + "е ботинки";
		};
	}

	// ================= ожерелья и монеты =================

	static void necklacesAndCoins() throws Exception {
		int[][] c = {{0x7FE8C6, 0xCFFFF0, 0xC9763E}, {0xB28CF0, 0xE2CFFF, 0xC9763E}, {0x3C3646, 0x8F7AC9, 0x6A6A78}, {0x4F7FE0, 0xA9C4FF, 0xD7E0EC}};
		for (int i = 0; i < 4; i++) {
			BufferedImage im = img(16, 16);
			for (int a = 0; a < 12; a++) {
				double ang = Math.PI + a * Math.PI / 11.0;
				put(im, 8 + (int) Math.round(6 * Math.cos(ang)), 2 + (int) Math.round(6 * -Math.sin(ang)) + 0, rgb(0xB9C6CE));
			}
			for (int x = 5; x < 11; x++) for (int y = 8; y < 14; y++) {
				double d = Math.abs(x - 7.5) + Math.abs(y - 10.5);
				if (d < 4) put(im, x, y, rgb(d > 3 ? c[i][2] : (x + y) % 3 == 0 ? c[i][1] : c[i][0]));
			}
			String id = "necklace_" + LumiraIds.NECKLACES[i];
			simple(id, im);
			name(id, LumiraIds.NECKLACE_RU[i], enName(id));
		}
		// Coin лиры — золотая круглая, Coin мирты — серебряная восьмиугольная
		BufferedImage lyra = img(16, 16), myrtle = img(16, 16);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				double d = Math.hypot(x - 7.5, y - 7.5);
				if (d < 6.6) {
					int col = d > 5.6 ? 0xB8860B : (d > 4.6 ? 0xFFE27A : 0xFFD34D);
					boolean moon = Math.hypot(x - 6.5, y - 7.5) < 3.2 && Math.hypot(x - 8.6, y - 7.5) > 2.6;
					put(lyra, x, y, rgb(moon ? 0x8FEDE3 : col));
				}
				double o = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)) + Math.hypot(x - 7.5, y - 7.5) * 0.3;
				if (o < 7.2) {
					int col = o > 6.2 ? 0x7A8590 : 0xD2DBE2;
					boolean leaf = Math.abs((x - 7.5) - (y - 7.5) * 0.6) < 0.9 && y > 3 && y < 12 || (y == 6 && x > 8 && x < 12) || (y == 9 && x < 7 && x > 3);
					put(myrtle, x, y, rgb(leaf ? 0x3E9B54 : col));
				}
			}
		}
		simple("coin_lyra", lyra);
		simple("coin_myrtle", myrtle);
	}

	// ================= таблицы добычи =================

	static final String SURVIVES = "{\"condition\":\"minecraft:survives_explosion\"}";

	static void selfDrop(String id, String extraCondition, String functions) throws Exception {
		String cond = extraCondition == null ? "[" + SURVIVES + "]" : "[" + SURVIVES + "," + extraCondition + "]";
		json(loot.resolve(id + ".json"), "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1.0,\"bonus_rolls\":0.0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":" + q(NS + ":" + id) + (functions == null ? "" : ",\"functions\":" + functions) + "}],\"conditions\":" + cond + "}]}");
	}

	static final String SILK = "{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"enchantments\":[{\"enchantment\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}";
	static final String SHEARS = "{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":[\"minecraft:shears\"]}}";

	static void oreLoot(String block, String drop, boolean fortune) throws Exception {
		String fn = fortune ? "[{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\",\"formula\":\"minecraft:ore_drops\"},{\"function\":\"minecraft:explosion_decay\"}]" : "[{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\",\"formula\":\"minecraft:ore_drops\"},{\"function\":\"minecraft:explosion_decay\"}]";
		json(loot.resolve(block + ".json"), "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1.0,\"bonus_rolls\":0.0,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":["
				+ "{\"type\":\"minecraft:item\",\"conditions\":[" + SILK + "],\"name\":" + q(NS + ":" + block) + "},"
				+ "{\"type\":\"minecraft:item\",\"name\":" + q(NS + ":" + drop) + ",\"functions\":" + fn + "}]}]}]}");
	}

	static void treeLoot(String s) throws Exception {
		for (String id : new String[]{s + "_log", "stripped_" + s + "_log", s + "_wood", "stripped_" + s + "_wood", s + "_planks", s + "_stairs", s + "_fence", s + "_fence_gate", s + "_trapdoor", s + "_button", s + "_pressure_plate", s + "_sign", s + "_hanging_sign", s + "_sapling"}) {
			selfDrop(id, null, null);
		}
		// настенные таблички падают как обычные (dropsLike), но таблица всё равно нужна на случай явной ссылки
		selfDrop(s + "_wall_sign", null, null);
		json(loot.resolve(s + "_wall_sign.json"), "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1.0,\"bonus_rolls\":0.0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":" + q(NS + ":" + s + "_sign") + "}],\"conditions\":[" + SURVIVES + "]}]}");
		json(loot.resolve(s + "_wall_hanging_sign.json"), "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1.0,\"bonus_rolls\":0.0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":" + q(NS + ":" + s + "_hanging_sign") + "}],\"conditions\":[" + SURVIVES + "]}]}");
		selfDrop(s + "_door", "{\"condition\":\"minecraft:block_state_property\",\"block\":" + q(NS + ":" + s + "_door") + ",\"properties\":{\"half\":\"lower\"}}", null);
		selfDrop(s + "_slab", null, "[{\"function\":\"minecraft:set_count\",\"conditions\":[{\"condition\":\"minecraft:block_state_property\",\"block\":" + q(NS + ":" + s + "_slab") + ",\"properties\":{\"type\":\"double\"}}],\"count\":2.0,\"add\":false},{\"function\":\"minecraft:explosion_decay\"}]");
		// листва: сама листва с ножницами/«Шёлковым касанием», иначе саженец 5% / 6,25% / 8,33% / 10% и палки 2%
		String leaves = NS + ":" + s + "_leaves";
		json(loot.resolve(s + "_leaves.json"), "{\"type\":\"minecraft:block\",\"pools\":["
				+ "{\"rolls\":1.0,\"bonus_rolls\":0.0,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":["
				+ "{\"type\":\"minecraft:item\",\"name\":" + q(leaves) + ",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[" + SHEARS + "," + SILK + "]}]},"
				+ "{\"type\":\"minecraft:item\",\"name\":" + q(NS + ":" + s + "_sapling") + ",\"conditions\":[" + SURVIVES + ",{\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:fortune\",\"chances\":[0.05,0.0625,0.083333336,0.1]}]}]}]},"
				+ "{\"rolls\":1.0,\"bonus_rolls\":0.0,\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:any_of\",\"terms\":[" + SHEARS + "," + SILK + "]}}],"
				+ "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.02}],\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":1.0,\"max\":2.0},\"add\":false},{\"function\":\"minecraft:explosion_decay\"}]}]}]}");
	}

	// ================= рецепты =================

	static int recipeCount = 0;

	static void shaped(String id, boolean rune, String[] pattern, Map<String, String> key, String result, int count) throws Exception {
		StringBuilder sb = new StringBuilder("{\"type\":" + q(rune ? NS + ":rune_shaped" : "minecraft:crafting_shaped") + ",\"category\":\"misc\",\"pattern\":[");
		for (int i = 0; i < pattern.length; i++) {
			sb.append(i == 0 ? "" : ",").append(q(pattern[i]));
		}
		sb.append("],\"key\":{");
		boolean first = true;
		for (Map.Entry<String, String> e : key.entrySet()) {
			sb.append(first ? "" : ",").append(q(e.getKey())).append(":").append(ingredient(e.getValue()));
			first = false;
		}
		sb.append("},\"result\":{\"item\":").append(q(result)).append(",\"count\":").append(count).append("}}");
		json(recipes.resolve(id + ".json"), sb.toString());
		recipeCount++;
	}

	static String ingredient(String v) {
		return v.startsWith("#") ? "{\"tag\":" + q(v.substring(1)) + "}" : "{\"item\":" + q(v) + "}";
	}

	static void shapeless(String id, List<String> ingredients, String result, int count, String group) throws Exception {
		StringBuilder sb = new StringBuilder("{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\"" + (group == null ? "" : ",\"group\":" + q(group)) + ",\"ingredients\":[");
		for (int i = 0; i < ingredients.size(); i++) {
			sb.append(i == 0 ? "" : ",").append(ingredient(ingredients.get(i)));
		}
		sb.append("],\"result\":{\"item\":").append(q(result)).append(",\"count\":").append(count).append("}}");
		json(recipes.resolve(id + ".json"), sb.toString());
		recipeCount++;
	}

	static void cook(String id, String type, String in, String out, double xp, int time) throws Exception {
		json(recipes.resolve(id + ".json"), "{\"type\":" + q("minecraft:" + type) + ",\"category\":\"misc\",\"ingredient\":{\"item\":" + q(in) + "},\"result\":" + q(out) + ",\"experience\":" + xp + ",\"cookingtime\":" + time + "}");
		recipeCount++;
	}

	static Map<String, String> k(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	static String n(String id) {
		return NS + ":" + id;
	}

	static void recipesAll() throws Exception {
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			String ingot = n(m.id() + "_ingot"), raw = n("raw_" + m.id()), block = n(m.id() + "_block");
			double xp = m.tier() <= 2 ? 0.7 : 1.0;
			cook(m.id() + "_ingot_smelting", "smelting", raw, ingot, xp, 200);
			cook(m.id() + "_ingot_blasting", "blasting", raw, ingot, xp, 100);
			shaped(m.id() + "_block", false, new String[]{"III", "III", "III"}, k("I", ingot), block, 1);
			shapeless(m.id() + "_ingot_from_block", List.of(block), ingot, 9, "ingots");
			boolean rune = false; // инструменты крафтятся и в обычном верстаке; броня и предметы рун — только в рунном
			String I = ingot;
			shaped(m.id() + "_sword", rune, new String[]{"I", "I", "S"}, k("I", I, "S", "minecraft:stick"), n(m.id() + "_sword"), 1);
			shaped(m.id() + "_axe", rune, new String[]{"II", "IS", " S"}, k("I", I, "S", "minecraft:stick"), n(m.id() + "_axe"), 1);
			shaped(m.id() + "_pickaxe", rune, new String[]{"III", " S ", " S "}, k("I", I, "S", "minecraft:stick"), n(m.id() + "_pickaxe"), 1);
			shaped(m.id() + "_shovel", rune, new String[]{"I", "S", "S"}, k("I", I, "S", "minecraft:stick"), n(m.id() + "_shovel"), 1);
			shaped(m.id() + "_hoe", rune, new String[]{"II", " S", " S"}, k("I", I, "S", "minecraft:stick"), n(m.id() + "_hoe"), 1);
			if (m.hasArmor()) {
				shaped(m.id() + "_helmet", true, new String[]{"III", "I I"}, k("I", I), n(m.id() + "_helmet"), 1);
				shaped(m.id() + "_chestplate", true, new String[]{"I I", "III", "III"}, k("I", I), n(m.id() + "_chestplate"), 1);
				shaped(m.id() + "_leggings", true, new String[]{"III", "I I", "I I"}, k("I", I), n(m.id() + "_leggings"), 1);
				shaped(m.id() + "_boots", true, new String[]{"I I", "I I"}, k("I", I), n(m.id() + "_boots"), 1);
			}
		}
		shaped("spark_briquette", false, new String[]{"CC", "CC"}, k("C", n("spark_coal")), n("spark_briquette"), 1);
		shaped("jarolite_briquette", false, new String[]{"JJJ", "JJJ", "JJJ"}, k("J", n("jarolite")), n("jarolite_briquette"), 1);
		shaped("dew_lens", true, new String[]{"QQ", "QQ"}, k("Q", n("dew_quartz")), n("dew_lens"), 1);
		shaped("echo_focus", true, new String[]{"EZE"}, k("E", n("echolite"), "Z", n("zaremed_ingot")), n("echo_focus"), 1);
		shaped("heart_prism", true, new String[]{" H ", "S S"}, k("H", n("heart_crystal"), "S", n("starsilver_ingot")), n("heart_prism"), 1);
		shapeless("rune_thread", List.of("minecraft:string", "minecraft:string"), n("rune_thread"), 1, null);
		// Рунный верстак делается из простых материалов (без железа): булыжник, доски и обычный верстак.
		shaped("rune_workbench", false, new String[]{"CCC", "PTP", "PPP"}, k("C", "minecraft:cobblestone", "P", "#minecraft:planks", "T", "minecraft:crafting_table"), n("rune_workbench"), 1);
		shaped("rune_anvil", true, new String[]{"BBB", "ZLZ", "LLL"}, k("B", n("lumirite_block"), "Z", n("zaremed_ingot"), "L", n("lumirite_ingot")), n("rune_anvil"), 1);
		shaped("rune_staff", true, new String[]{"LZ", "S ", "S "}, k("L", n("dew_lens"), "Z", n("zaremed_ingot"), "S", "minecraft:stick"), n("rune_staff"), 1);
		shaped("necklace_dew", true, new String[]{"ZTZ", " L "}, k("Z", n("zaremed_ingot"), "T", n("rune_thread"), "L", n("dew_lens")), n("necklace_dew"), 1);
		shaped("necklace_echo", true, new String[]{"ZTZ", " F "}, k("Z", n("zaremed_ingot"), "T", n("rune_thread"), "F", n("echo_focus")), n("necklace_echo"), 1);
		shaped("necklace_guard", true, new String[]{"III", " Q ", " T "}, k("I", n("twilight_iron_ingot"), "Q", n("dew_quartz"), "T", n("rune_thread")), n("necklace_guard"), 1);
		shaped("necklace_star", true, new String[]{"SPS", " T "}, k("S", n("starsilver_ingot"), "P", n("heart_prism"), "T", n("rune_thread")), n("necklace_star"), 1);
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			String planks = n(s + "_planks");
			for (String logId : new String[]{s + "_log", s + "_wood", "stripped_" + s + "_log", "stripped_" + s + "_wood"}) {
				shapeless(logId + "_to_planks", List.of(n(logId)), planks, 4, "planks");
			}
			shaped(s + "_wood", false, new String[]{"LL", "LL"}, k("L", n(s + "_log")), n(s + "_wood"), 3);
			shaped("stripped_" + s + "_wood", false, new String[]{"LL", "LL"}, k("L", n("stripped_" + s + "_log")), n("stripped_" + s + "_wood"), 3);
			shaped(s + "_stairs", false, new String[]{"P  ", "PP ", "PPP"}, k("P", planks), n(s + "_stairs"), 4);
			shaped(s + "_slab", false, new String[]{"PPP"}, k("P", planks), n(s + "_slab"), 6);
			shaped(s + "_fence", false, new String[]{"PSP", "PSP"}, k("P", planks, "S", "minecraft:stick"), n(s + "_fence"), 3);
			shaped(s + "_fence_gate", false, new String[]{"SPS", "SPS"}, k("P", planks, "S", "minecraft:stick"), n(s + "_fence_gate"), 1);
			shaped(s + "_door", false, new String[]{"PP", "PP", "PP"}, k("P", planks), n(s + "_door"), 3);
			shaped(s + "_trapdoor", false, new String[]{"PPP", "PPP"}, k("P", planks), n(s + "_trapdoor"), 2);
			shapeless(s + "_button", List.of(planks), n(s + "_button"), 1, null);
			shaped(s + "_pressure_plate", false, new String[]{"PP"}, k("P", planks), n(s + "_pressure_plate"), 1);
			shaped(s + "_sign", false, new String[]{"PPP", "PPP", " S "}, k("P", planks, "S", "minecraft:stick"), n(s + "_sign"), 3);
			shaped(s + "_hanging_sign", false, new String[]{"C C", "PPP", "PPP"}, k("C", "minecraft:chain", "P", n("stripped_" + s + "_log")), n(s + "_hanging_sign"), 6);
		}
		System.out.println("recipes: " + recipeCount);
	}

	// ================= теги =================

	static final Map<String, LinkedHashSet<String>> BLOCK_TAGS = new LinkedHashMap<>();
	static final Map<String, LinkedHashSet<String>> ITEM_TAGS = new LinkedHashMap<>();

	static void bt(String tag, String id) {
		BLOCK_TAGS.computeIfAbsent(tag, x -> new LinkedHashSet<>()).add(id.contains(":") ? id : n(id));
	}

	static void it(String tag, String id) {
		ITEM_TAGS.computeIfAbsent(tag, x -> new LinkedHashSet<>()).add(id.contains(":") ? id : n(id));
	}

	static void both(String tag, String id) {
		bt(tag, id);
		it(tag, id);
	}

	static void tags() throws Exception {
		bt("minecraft:mineable/pickaxe", "casino_machine");
		for (LumiraIds.Ore o : LumiraIds.ORES) {
			bt("minecraft:mineable/pickaxe", o.id());
			String tag = switch (o.level()) {
				case 1 -> "minecraft:needs_stone_tool";
				case 2 -> "minecraft:needs_iron_tool";
				default -> "fabric:needs_tool_level_" + (o.level() + 0);
			};
			// уровни: 3 — люмирит, 4 — заремедь, 5 — сумеречное железо, 6 — звёздносеребро
			if (o.level() >= 3) {
				tag = "fabric:needs_tool_level_" + o.level();
			}
			bt(tag, o.id());
		}
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			bt("minecraft:mineable/pickaxe", m.id() + "_block");
			bt("minecraft:needs_iron_tool", m.id() + "_block");
		}
		bt("minecraft:mineable/pickaxe", "rune_anvil");
		bt("minecraft:mineable/axe", "rune_workbench");
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			for (String id : new String[]{s + "_log", "stripped_" + s + "_log", s + "_wood", "stripped_" + s + "_wood"}) {
				both("minecraft:logs", id);
				both("minecraft:logs_that_burn", id);
				bt("minecraft:mineable/axe", id);
			}
			both("minecraft:planks", s + "_planks");
			both("minecraft:leaves", s + "_leaves");
			both("minecraft:saplings", s + "_sapling");
			both("minecraft:wooden_stairs", s + "_stairs");
			both("minecraft:wooden_slabs", s + "_slab");
			both("minecraft:wooden_fences", s + "_fence");
			both("minecraft:fence_gates", s + "_fence_gate");
			both("minecraft:wooden_doors", s + "_door");
			both("minecraft:wooden_trapdoors", s + "_trapdoor");
			both("minecraft:wooden_buttons", s + "_button");
			both("minecraft:wooden_pressure_plates", s + "_pressure_plate");
			both("minecraft:signs", s + "_sign");
			both("minecraft:hanging_signs", s + "_hanging_sign");
			bt("minecraft:standing_signs", s + "_sign");
			bt("minecraft:wall_signs", s + "_wall_sign");
			bt("minecraft:ceiling_hanging_signs", s + "_hanging_sign");
			bt("minecraft:wall_hanging_signs", s + "_wall_hanging_sign");
			bt("minecraft:all_signs", s + "_sign");
			bt("minecraft:all_signs", s + "_wall_sign");
			bt("minecraft:all_hanging_signs", s + "_hanging_sign");
			bt("minecraft:all_hanging_signs", s + "_wall_hanging_sign");
			for (String id : new String[]{s + "_planks", s + "_stairs", s + "_slab", s + "_fence", s + "_fence_gate", s + "_door", s + "_trapdoor", s + "_button", s + "_pressure_plate", s + "_sign", s + "_wall_sign", s + "_hanging_sign", s + "_wall_hanging_sign"}) {
				bt("minecraft:mineable/axe", id);
			}
			bt("minecraft:mineable/hoe", s + "_leaves");
			bt("minecraft:sword_efficient", s + "_leaves");
		}
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			it("minecraft:swords", m.id() + "_sword");
			it("minecraft:axes", m.id() + "_axe");
			it("minecraft:pickaxes", m.id() + "_pickaxe");
			it("minecraft:shovels", m.id() + "_shovel");
			it("minecraft:hoes", m.id() + "_hoe");
		}
		writeTags("blocks", BLOCK_TAGS);
		writeTags("items", ITEM_TAGS);
	}

	static void writeTags(String kind, Map<String, LinkedHashSet<String>> tags) throws Exception {
		for (Map.Entry<String, LinkedHashSet<String>> e : tags.entrySet()) {
			String full = e.getKey();
			String ns = full.substring(0, full.indexOf(':'));
			String path = full.substring(full.indexOf(':') + 1);
			StringBuilder sb = new StringBuilder("{\n\t\"replace\": false,\n\t\"values\": [\n");
			int i = 0;
			for (String v : e.getValue()) {
				sb.append("\t\t").append(q(v)).append(++i < e.getValue().size() ? ",\n" : "\n");
			}
			sb.append("\t]\n}");
			json(root.resolve("data/" + ns + "/tags/" + kind + "/" + path + ".json"), sb.toString());
		}
	}

	// ================= локализация =================

	static void name(String id, String ru, String en) {
		RU.put(id, ru);
		EN.put(id, en);
	}

	static String enName(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.split("_")) {
			sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	static boolean isBlock(String id) {
		return LumiraIds.simpleBlocks().containsKey(id) || LumiraIds.allTreeBlockIds().contains(id);
	}

	static void lang() throws Exception {
		for (Map.Entry<String, String> e : LumiraIds.simpleItems().entrySet()) {
			if (!RU.containsKey(e.getKey())) {
				name(e.getKey(), e.getValue(), enName(e.getKey()));
			}
		}
		for (Map.Entry<String, String> e : LumiraIds.simpleBlocks().entrySet()) {
			name(e.getKey(), e.getValue(), enName(e.getKey()));
		}
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			name("raw_" + m.id(), rawRu(m), enName("raw_" + m.id()));
		}
		name("hidden_item", "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u043f\u0440\u0435\u0434\u043c\u0435\u0442", "Unknown Item");
		appendLang("ru_ru", RU, "\u0420\u043e\u0449\u0430 \u041b\u044e\u043c\u0438\u0440\u044b");
		appendLang("en_us", EN, "Lumira Grove");
	}

	static String rawRu(LumiraIds.Metal m) {
		return switch (m.id()) {
			case "lumirite" -> "Сырой люмирит";
			case "zaremed" -> "Сырая заремедь";
			case "twilight_iron" -> "Сырое сумеречное железо";
			default -> "Сырое звёздносеребро";
		};
	}

	static void appendLang(String file, Map<String, String> names, String group) throws Exception {
		Path p = assets.resolve("lang/" + file + ".json");
		String text = Files.readString(p, StandardCharsets.UTF_8);
		int cut = text.indexOf("\"_lumira.begin\"");
		if (cut >= 0) {
			int comma = text.lastIndexOf(',', cut);
			text = text.substring(0, comma) + "\n}\n";
		}
		int close = text.lastIndexOf('}');
		String head = text.substring(0, close).stripTrailing();
		StringBuilder sb = new StringBuilder(head);
		sb.append(",\n  \"_lumira.begin\": \"\"");
		sb.append(",\n  \"itemGroup.admin_control.lumira\": ").append(jsonStr(group));
		for (Map.Entry<String, String> e : names.entrySet()) {
			String kind = isBlock(e.getKey()) ? "block" : "item";
			sb.append(",\n  ").append(jsonStr(kind + ".admin_control." + e.getKey())).append(": ").append(jsonStr(e.getValue()));
		}
		sb.append("\n}\n");
		Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
	}

	static String jsonStr(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
