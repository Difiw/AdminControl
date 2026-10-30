package ru.admincontrol.lumira;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Единый список идентификаторов контента «Рощи Люмиры» (без классов Minecraft): по нему регистрируется мод
 * и генерируются ресурсы (tools/LumiraGen.java).
 */
public final class LumiraIds {
	private LumiraIds() {
	}

	/** Новый металл: четыре штуки, у каждого руда, сырьё, слиток, блок и пять инструментов. */
	public record Metal(String id, String ru, String adjM, String adjF, String adjN, int tier, int level, float speed,
						int durability, float swordDmg, float swordSpeed, float axeDmg, float axeSpeed,
						float pickDmg, float shovelDmg, float hoeDmg, float pickSpeed, float hoeSpeed,
						int enchant, boolean hasArmor) {
	}

	public static final List<Metal> METALS = List.of(
			new Metal("lumirite", "Люмирит", "Люмиритовый", "Люмиритовая", "Люмиритовое", 1, 3, 8f, 400,
					6, 1.6f, 9, 0.9f, 4, 4.5f, 1, 1.2f, 3f, 14, false),
			new Metal("zaremed", "Заремедь", "Заремедный", "Заремедная", "Заремедное", 2, 4, 10f, 550,
					6, 1.6f, 9, 1.0f, 4, 4.5f, 1, 1.2f, 3f, 12, false),
			new Metal("twilight_iron", "Сумеречное железо", "Сумеречный", "Сумеречная", "Сумеречное", 3, 5, 11f, 1000,
					7, 1.6f, 10, 1.0f, 5, 5.5f, 1, 1.2f, 4f, 10, true),
			new Metal("starsilver", "Звёздносеребро", "Звёздносеребряный", "Звёздносеребряная", "Звёздносеребряное", 4, 6, 12f, 1700,
					8, 1.6f, 11, 1.0f, 6, 6.5f, 1, 1.2f, 4f, 16, true));

	public static final String[] TOOL_KINDS = {"sword", "axe", "pickaxe", "shovel", "hoe"};
	public static final String[] ARMOR_KINDS = {"helmet", "chestplate", "leggings", "boots"};
	/** Защита и прочность частей брони: шлем, нагрудник, поножи, ботинки. */
	public static final int[] TWILIGHT_PROT = {3, 7, 6, 2};
	public static final int[] TWILIGHT_DUR = {286, 416, 390, 338};
	public static final int[] STAR_PROT = {3, 8, 6, 3};
	public static final int[] STAR_DUR = {407, 592, 555, 481};
	/** Слитков на часть брони: 5 / 8 / 7 / 4. */
	public static final int[] ARMOR_INGOTS = {5, 8, 7, 4};

	/** Прочие руды: id блока, id выпадающего предмета, русские названия, уровень добычи (0 дерево … 6), опыт. */
	public record Ore(String id, String drop, String ruOre, String ruDrop, int level, int xpMin, int xpMax,
					  int veinMin, int veinMax, int yMin, int yMax, int perChunk, boolean raw, String material) {
	}

	public static final List<Ore> ORES = List.of(
			new Ore("spark_coal_ore", "spark_coal", "Искроугольная руда", "Искроуголь", 1, 0, 2, 6, 12, 24, 96, 9, false, "stone"),
			new Ore("lumirite_ore", "raw_lumirite", "Люмиритовая руда", "Сырой люмирит", 2, 0, 0, 6, 10, -8, 64, 9, true, "stone"),
			new Ore("zaremed_ore", "raw_zaremed", "Заремедная руда", "Сырая заремедь", 3, 0, 0, 5, 9, -24, 32, 7, true, "stone"),
			new Ore("twilight_iron_ore", "raw_twilight_iron", "Сумеречная железная руда", "Сырое сумеречное железо", 4, 0, 0, 3, 6, -48, 0, 4, true, "deepslate"),
			new Ore("starsilver_ore", "raw_starsilver", "Звёздносеребряная руда", "Сырое звёздносеребро", 5, 0, 0, 2, 4, -56, -24, 2, true, "deepslate"),
			new Ore("dew_quartz_ore", "dew_quartz", "Руда росного кварца", "Росный кварц", 2, 2, 4, 3, 7, 8, 48, 4, false, "stone"),
			new Ore("echolite_ore", "echolite", "Эхолитовая руда", "Эхолит", 4, 3, 6, 2, 5, -32, 0, 3, false, "deepslate"),
			new Ore("heart_crystal_ore", "heart_crystal", "Руда сердечного кристалла", "Сердечный кристалл", 6, 5, 8, 1, 2, -60, -40, 1, false, "deepslate"),
			new Ore("jarolite_ore", "jarolite", "Жаролитовая руда", "Жаролит", 2, 1, 3, 3, 6, -32, 16, 4, false, "stone"));

	/** Деревья-породы. */
	public record Tree(String id, String ru, String adj, int hMin, int hMax, int crown, int share,
					   int bark, int barkDark, int ring, int planks, int planksDark, int leaf, int leafDark) {
	}

	public static final List<Tree> TREES = List.of(
			new Tree("lumira_willow", "Люмирская ива", "люмирской ивы", 8, 11, 8, 16,
					0xAFB9C4, 0x7C8996, 0xDCEBF2, 0xA9BFCB, 0x8AA3B3, 0x7FE0C4, 0x4DB89C),
			new Tree("zarelist", "Зарелист", "зарелиста", 6, 9, 6, 14,
					0x8B5A32, 0x5E3A1E, 0xF3E3BC, 0xD9923C, 0xB8731F, 0xE8A62F, 0xBF7A12),
			new Tree("twilight_cedar", "Сумеречный кедр", "сумеречного кедра", 12, 16, 6, 10,
					0x3A3340, 0x241F2A, 0xA79EB6, 0x5C5468, 0x453E52, 0x4B3F9A, 0x2F2570),
			new Tree("star_birch", "Звёздная берёза", "звёздной берёзы", 9, 12, 6, 10,
					0xE8EAEE, 0x2B3550, 0xDCE8F5, 0xEEF0F4, 0xC3D0E4, 0xA9D3F0, 0x7FB3DD),
			new Tree("crimson_elm", "Багряный вяз", "багряного вяза", 7, 10, 8, 10,
					0x7A3B2E, 0x522219, 0xF1C9CF, 0x9B4A3A, 0x7A3427, 0xA3202F, 0x741420),
			new Tree("moon_ash", "Лунный ясень", "лунного ясеня", 10, 13, 7, 8,
					0x7C8089, 0xB8BCC6, 0xE6DCEB, 0x9A94A8, 0x7D7690, 0xB9A6D9, 0x8C77BE),
			new Tree("malachite_sequoia", "Малахитовая секвойя", "малахитовой секвойи", 16, 21, 8, 4,
					0x5E5A33, 0x3B3820, 0xE8B54C, 0x8C7A3E, 0x6E5F2A, 0x1E9E5A, 0x0F6B3C));

	/** Части древесного семейства: суффикс id, русский шаблон названия. */
	public static final String[][] TREE_PARTS = {
			{"log", "Бревно %s"}, {"stripped_log", "Обтёсанное бревно %s"}, {"wood", "Древесина %s"},
			{"stripped_wood", "Обтёсанная древесина %s"}, {"planks", "Доски %s"}, {"leaves", "Листва %s"},
			{"sapling", "Саженец %s"}, {"stairs", "Ступени %s"}, {"slab", "Плита %s"}, {"fence", "Забор %s"},
			{"fence_gate", "Калитка %s"}, {"door", "Дверь %s"}, {"trapdoor", "Люк %s"}, {"button", "Кнопка %s"},
			{"pressure_plate", "Нажимная плита %s"}, {"sign", "Табличка %s"}, {"hanging_sign", "Подвесная табличка %s"}};

	/** Родительный падеж названий пород для шаблонов «Бревно %s». */
	public static String treeGenitive(Tree t) {
		return t.adj();
	}

	public static final String[] NECKLACES = {"dew", "echo", "guard", "star"};
	public static final String[] NECKLACE_RU = {"Ожерелье Росы", "Ожерелье Эха", "Ожерелье Стража", "Ожерелье Звезды"};

	/** Простые предметы (не блоки): id -> русское название. */
	public static Map<String, String> simpleItems() {
		Map<String, String> m = new LinkedHashMap<>();
		for (Ore o : ORES) {
			m.put(o.drop(), o.ruDrop());
		}
		for (Metal mt : METALS) {
			m.put(mt.id() + "_ingot", mt.adjM() + " слиток");
		}
		m.put("spark_briquette", "Искробрикет");
		m.put("jarolite_briquette", "Жаролитовый брикет");
		m.put("dew_lens", "Росная линза");
		m.put("echo_focus", "Эхофокус");
		m.put("heart_prism", "Сердечная призма");
		m.put("rune_thread", "Рунная нить");
		m.put("rune_staff", "Базовый посох");
		m.put("coin_lyra", "Coin лиры");
		m.put("coin_myrtle", "Coin мирты");
		return m;
	}

	/** Блоки без семейств: id -> название. */
	public static Map<String, String> simpleBlocks() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("albion_grass_block", "Магический дёрн Альбиона");
		m.put("albion_dirt", "Магическая земля Альбиона");
		m.put("albion_stone", "Камень Альбиона");
		m.put("albion_cobblestone", "Булыжник Альбиона");
		for (Ore o : ORES) {
			m.put(o.id(), o.ruOre());
		}
		for (Metal mt : METALS) {
			m.put(mt.id() + "_block", "Блок: " + mt.ru().toLowerCase(java.util.Locale.ROOT));
		}
		m.put("rune_workbench", "Рунный верстак");
		m.put("rune_anvil", "Рунная наковальня");
		return m;
	}

	public static List<String> allTreeBlockIds() {
		List<String> ids = new ArrayList<>();
		for (Tree t : TREES) {
			for (String[] part : TREE_PARTS) {
				ids.add(part[0].startsWith("stripped_") ? "stripped_" + t.id() + "_" + part[0].substring(9) : t.id() + "_" + part[0]);
			}
			ids.add(t.id() + "_wall_sign");
			ids.add(t.id() + "_wall_hanging_sign");
		}
		return ids;
	}
}
