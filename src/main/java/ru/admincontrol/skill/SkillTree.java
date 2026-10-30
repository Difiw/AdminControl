package ru.admincontrol.skill;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Описание дерева навыков: общее для сервера и клиента (сервер решает, клиент только рисует). */
public final class SkillTree {
	public record Currency(String id, String title, String coin, int color, String alias) {
	}

	/** Ветка; {@code lanes} — названия линий (столбцов) внутри ветки, по индексу {@link Node#col}. */
	public record Branch(String id, String title, Currency currency, Item icon, String description, List<String> lanes) {
		public String lane(int col) {
			return col >= 0 && col < this.lanes.size() ? this.lanes.get(col) : "";
		}
	}

	/** Требование: узел {@code node} должен быть не ниже уровня {@code level}. */
	public record Req(String node, int level) {
	}

	/**
	 * Характеристика, которую усиливают узлы. Итог — сумма вкладов всех купленных узлов, но не выше {@code cap}.
	 * NONE — особые узлы (верстак, рюкзак, автопечь).
	 */
	public enum Stat {
		NONE(0),
		HEALTH(40),
		ARMOR(10),
		TOUGHNESS(8),
		REGEN(14),
		FALL(80),
		HUNGER(70),
		FIRE(75),
		MOB_GUARD(55),
		MINING(150),
		LUCK(12),
		SPEED(40),
		JUMP(8),
		DAMAGE(6),
		ATTACK_SPEED(30),
		KNOCKBACK(100),
		HUNTER(100),
		ARCHER(100),
		NAME(5),
		SNEAK_NAME(1),
		UNSEEN(70),
		XP(100),
		LEGACY(70),
		AIR(250),
		DROWN(80),
		BLAST(60),
		FREEZE(100),
		MAGIC(70),
		PRICKLY(100),
		PROJ_GUARD(40),
		PVP_GUARD(15),
		LAST_STAND(30),
		DODGE(15),
		CRIT(25),
		EXECUTE(40),
		FIRST_STRIKE(40),
		LIFESTEAL(15),
		THORNS(30),
		HEAL_BOOST(70),
		DURABILITY(60),
		FEAST(70),
		NIGHT_EYE(1),
		MACHINE_GUARD(60),
		REPAIR_SPEED(75),
		/** Доп. тренировки в сутки сверх базового лимита сервера. */
		TRAIN_LIMIT(5),
		/** Дольше действуют протеиновые батончики и коктейли, %. */
		PROTEIN_TIME(100),
		/** Больше WC за тренировку, %. */
		TRAIN_PAY(80);

		public final double cap;

		Stat(double cap) {
			this.cap = cap;
		}
	}

	public static final class Node {
		public final int index;
		public final String id;
		public final Branch branch;
		public final String title;
		public final Item icon;
		/** Линия (столбец) внутри ветки: улучшения одной линии идут цепочкой снизу вверх. */
		public final int col;
		/** Ряд снизу вверх: 0 — у корня ветки. */
		public final int row;
		public final int maxLevel;
		private final int[] costs;
		private final int costStep;
		public final List<Req> requires;
		public final Stat stat;
		/** Вклад в характеристику за один уровень. */
		public final double per;
		public final String description;

		private Node(int index, String id, Branch branch, String title, Item icon, int col, int row, int maxLevel,
				int[] costs, int costStep, List<Req> requires, Stat stat, double per, String description) {
			this.index = index;
			this.id = id;
			this.branch = branch;
			this.title = title;
			this.icon = icon;
			this.col = col;
			this.row = row;
			this.maxLevel = maxLevel;
			this.costs = costs;
			this.costStep = costStep;
			this.requires = requires;
			this.stat = stat;
			this.per = per;
			this.description = description;
		}

		/** Цена уровня {@code level} (с 1). */
		public int cost(int level) {
			int i = Math.max(1, level) - 1;
			if (i < this.costs.length) {
				return this.costs[i];
			}
			return this.costs[this.costs.length - 1] + (i - this.costs.length + 1) * this.costStep;
		}
	}

	/** Поэтапное одноразовое задание: этапы не повторяются. {@code divisor}/{@code unit} — только для показа. */
	public record Quest(String id, Currency currency, String title, String description, long[] goals, int[] rewards,
			int divisor, String unit) {
		public int stages() {
			return this.goals.length;
		}
	}

	/** Ежедневное задание: одна награда в сутки. */
	public record Daily(String id, Currency currency, String title, long goal, int reward, int divisor, String unit) {
	}

	public static final Currency LIFE = new Currency("life", "Жизнь", "коины жизни", 0xFF6B7A, "жизнь");
	public static final Currency ORE = new Currency("ore", "Руда", "коины руды", 0x7FC8FF, "руда");
	public static final Currency PATH = new Currency("path", "Путь", "коины пути", 0x7CE38B, "путь");
	public static final Currency FIGHT = new Currency("fight", "Бой", "коины боя", 0xFFA24C, "бой");
	public static final Currency SHADOW = new Currency("shadow", "Тень", "коины тени", 0xB98CFF, "тень");
	public static final Currency MIND = new Currency("mind", "Знание", "коины знания", 0x5CE1E6, "знание");
	public static final Currency CHIPS = new Currency("chips", "Фишки", "фишки", 0xFFD34D, "фишки");
	public static final Currency SYNC = new Currency("sync", "Ток", "коины тока", 0x4DE8FF, "ток");
	public static final List<Currency> CURRENCIES = List.of(LIFE, ORE, PATH, FIGHT, SHADOW, MIND, CHIPS, SYNC);

	public static final Branch SURVIVAL = new Branch("survival", "Выживание", LIFE, Items.GOLDEN_APPLE,
			"Здоровье, броня, восстановление, защита от падения, огня и голода.",
			List.of("Падение и огонь", "Восстановление", "Здоровье", "Броня", "Сытость", "Стихии", "Целитель"));
	public static final Branch CRAFT = new Branch("craft", "Добыча и ремесло", ORE, Items.IRON_PICKAXE,
			"Верстак, рюкзак и автопечь у инвентаря, скорость добычи и везение.",
			List.of("У инвентаря", "Скорость добычи", "Удача", "Жар", "Рудокоп", "Бережливость", "Глаза шахтёра"));
	public static final Branch MOVE = new Branch("move", "Передвижение", PATH, Items.LEATHER_BOOTS,
			"Скорость бега, высота прыжка, мягкое приземление и выносливость.",
			List.of("Прыжок", "Приземление", "Скорость", "Выносливость", "Пловец", "Тропы"));
	public static final Branch COMBAT = new Branch("combat", "Бой", FIGHT, Items.IRON_SWORD,
			"Урон, скорость атаки, стрельба, стойкость и защита от мобов.",
			List.of("Защита", "Ближний бой", "Скорость атаки", "Стрельба", "Охота", "Точность", "Кровь и шипы"));
	public static final Branch STEALTH = new Branch("stealth", "Скрытность", SHADOW, Items.PHANTOM_MEMBRANE,
			"Скрытый ник, незаметность для мобов и уклонение.",
			List.of("Скрытый ник", "Тихий шаг", "Незаметность", "Ловкость", "Противоядие", "Неуловимость"));
	public static final Branch WISDOM = new Branch("wisdom", "Мудрость", MIND, Items.EXPERIENCE_BOTTLE,
			"Опыт из сфер, сохранение опыта после смерти и удача.",
			List.of("Наследие", "Опыт", "Удача", "Ясный ум", "Знахарство", "Мастерство"));
	public static final Branch CASINO = new Branch("casino", "Казино", CHIPS, Items.GOLD_BLOCK,
			"Игровые автоматы: реже ломаются от ваших прокруток, быстрее чинятся вашими инструментами.",
			List.of("Надёжность", "Ремонт"));
	public static final Branch SYNC_TREE = new Branch("sync", "Sync", SYNC, Items.LIGHTNING_ROD,
			"Ток, тренировки и протеин: дневной лимит тренировок и спортивное питание.",
			List.of("Лимит", "Питание", "Ток"));
	public static final List<Branch> BRANCHES = List.of(SURVIVAL, CRAFT, MOVE, COMBAT, STEALTH, WISDOM, CASINO, SYNC_TREE);

	private static final List<Node> NODE_LIST = new ArrayList<>();

	// ---------- Выживание ----------
	public static final Node SOFT_FALL = node("soft_fall", SURVIVAL, "Мягкое приземление", Items.FEATHER, 0, 0, 3,
			new int[] {3, 5, 7}, List.of(), Stat.FALL, 10, "Меньше урона от падения.");
	public static final Node CAT_FALL = node("cat_fall", SURVIVAL, "Кошачья ловкость", Items.STRING, 0, 1, 3,
			new int[] {6, 9, 13}, List.of(req("soft_fall", 3)), Stat.FALL, 10, "Ещё меньше урона от падения.");
	public static final Node FIRE_SKIN = node("fire_skin", SURVIVAL, "Огнеупорность", Items.MAGMA_CREAM, 0, 2, 3,
			new int[] {4, 6, 9}, List.of(req("cat_fall", 1)), Stat.FIRE, 10, "Меньше урона от огня и лавы.");
	public static final Node REGEN = node("regen", SURVIVAL, "Восстановление", Items.GLISTERING_MELON_SLICE, 1, 0, 3,
			new int[] {4, 6, 9}, List.of(), Stat.REGEN, 1,
			"Здоровье само понемногу восполняется, когда сытость не ниже 18.");
	public static final Node SECOND_WIND = node("second_wind", SURVIVAL, "Второе дыхание", Items.GHAST_TEAR, 1, 1, 3,
			new int[] {9, 13, 18}, List.of(req("regen", 3)), Stat.REGEN, 1, "Восстановление здоровья быстрее.");
	public static final Node VITALITY = node("vitality", SURVIVAL, "Крепкое тело", Items.GOLDEN_APPLE, 2, 0, 4,
			new int[] {3, 5, 8, 12}, List.of(), Stat.HEALTH, 2, "Больше максимального здоровья.");
	public static final Node VITALITY2 = node("vitality2", SURVIVAL, "Крепкое тело II", Items.APPLE, 2, 1, 3,
			new int[] {10, 14, 18}, List.of(req("vitality", 4)), Stat.HEALTH, 2, "Ещё больше максимального здоровья.");
	public static final Node HEART = node("heart", SURVIVAL, "Сердце великана", Items.ENCHANTED_GOLDEN_APPLE, 2, 2, 3,
			new int[] {16, 22, 30}, List.of(req("vitality2", 3)), Stat.HEALTH, 2, "Большой запас здоровья.");
	public static final Node TOUGHNESS = node("toughness", SURVIVAL, "Закалка", Items.IRON_CHESTPLATE, 3, 0, 2,
			new int[] {8, 12}, List.of(), Stat.ARMOR, 1, "Дополнительная броня.");
	public static final Node PLATING = node("plating", SURVIVAL, "Прочная броня", Items.IRON_INGOT, 3, 1, 3,
			new int[] {9, 13, 18}, List.of(req("toughness", 2)), Stat.TOUGHNESS, 1,
			"Прочность брони: сильные удары пробивают броню хуже.");
	public static final Node IRONHIDE = node("ironhide", SURVIVAL, "Стальная кожа", Items.IRON_BLOCK, 3, 2, 3,
			new int[] {14, 19, 25}, List.of(req("plating", 1)), Stat.ARMOR, 1, "Ещё больше брони.");
	public static final Node SATIETY = node("satiety", SURVIVAL, "Сытость", Items.COOKED_BEEF, 4, 0, 3,
			new int[] {3, 5, 7}, List.of(), Stat.HUNGER, 10, "Голод расходуется медленнее.");
	public static final Node PANTRY = node("pantry", SURVIVAL, "Запасливость", Items.BREAD, 4, 1, 4,
			new int[] {6, 8, 11, 15}, List.of(req("satiety", 3)), Stat.HUNGER, 5, "Голод расходуется ещё медленнее.");
	public static final Node FIRE_WALK = node("fire_walk", SURVIVAL, "Хождение по углям", Items.BLAZE_POWDER, 0, 3, 3,
			new int[] {12, 17, 23}, List.of(req("fire_skin", 2)), Stat.FIRE, 5, "Огонь и лава почти не обжигают.");
	public static final Node VIGOR = node("vigor", SURVIVAL, "Бодрость", Items.GOLDEN_CARROT, 1, 2, 3,
			new int[] {15, 21, 28}, List.of(req("second_wind", 2)), Stat.REGEN, 1, "Ещё более быстрое восстановление.");
	public static final Node TITAN = node("titan", SURVIVAL, "Титан", Items.NETHER_STAR, 2, 3, 3,
			new int[] {26, 34, 44}, List.of(req("heart", 3)), Stat.HEALTH, 2, "Огромный запас здоровья.");
	public static final Node ADAMANT = node("adamant", SURVIVAL, "Адамант", Items.NETHERITE_INGOT, 3, 3, 3,
			new int[] {20, 27, 35}, List.of(req("ironhide", 2)), Stat.TOUGHNESS, 1, "Броня держит даже самые сильные удары.");
	public static final Node GOURMET = node("gourmet", SURVIVAL, "Здоровый аппетит", Items.PUMPKIN_PIE, 4, 2, 2,
			new int[] {14, 20}, List.of(req("pantry", 2)), Stat.HEALTH, 2, "Хорошее питание даёт больше здоровья.");

	// ---------- Добыча и ремесло ----------
	public static final Node WORKBENCH = node("workbench", CRAFT, "Карманный верстак", Items.CRAFTING_TABLE, 0, 0, 1,
			new int[] {4}, List.of(), Stat.NONE, 0, "Кнопка верстака у инвентаря (раньше продавался в /shop).");
	public static final Node BACKPACK = node("backpack", CRAFT, "Рюкзак", Items.CHEST, 0, 1, 5,
			new int[] {3, 5, 7, 10, 14}, List.of(req("workbench", 1)), Stat.NONE, 0,
			"Личный рюкзак: кнопка у инвентаря, +9 слотов за уровень. Содержимое сохраняется.");
	public static final Node FURNACE = node("furnace", CRAFT, "Автопечь", Items.FURNACE, 0, 2, 5,
			new int[] {4, 6, 8, 10, 12}, 2, List.of(req("backpack", 1)), Stat.NONE, 0,
			"Личная печь без топлива: кнопка у инвентаря.");
	public static final Node QUICK_PICK = node("quick_pick", CRAFT, "Быстрая кирка", Items.IRON_PICKAXE, 1, 0, 3,
			new int[] {3, 5, 8}, List.of(), Stat.MINING, 5, "Блоки ломаются быстрее любым инструментом.");
	public static final Node QUICK_PICK2 = node("quick_pick2", CRAFT, "Быстрая кирка II", Items.DIAMOND_PICKAXE, 1, 1, 4,
			new int[] {7, 10, 13, 17}, List.of(req("quick_pick", 3)), Stat.MINING, 5, "Добыча ещё быстрее.");
	public static final Node MASTER_MINE = node("master_mine", CRAFT, "Мастер добычи", Items.NETHERITE_PICKAXE, 1, 2, 5,
			new int[] {12, 15, 19, 24, 30}, List.of(req("quick_pick2", 4)), Stat.MINING, 5, "Самая быстрая добыча.");
	public static final Node ANGLER = node("angler", CRAFT, "Везение", Items.FISHING_ROD, 2, 0, 3,
			new int[] {5, 8, 12}, List.of(), Stat.LUCK, 1, "Удача: лучше добыча из сундуков в мире и с рыбалки.");
	public static final Node LUCKY_MINER = node("lucky_miner", CRAFT, "Удача шахтёра", Items.EMERALD, 2, 1, 2,
			new int[] {14, 20}, List.of(req("angler", 3)), Stat.LUCK, 1, "Ещё больше удачи.");
	public static final Node MINER_HEAT = node("miner_heat", CRAFT, "Жаростойкость", Items.LAVA_BUCKET, 3, 0, 2,
			new int[] {6, 10}, List.of(), Stat.FIRE, 10, "Меньше урона от огня и лавы в шахтах.");
	public static final Node FORGE_HEAT = node("forge_heat", CRAFT, "Жар горна", Items.BLAST_FURNACE, 3, 1, 2,
			new int[] {12, 17}, List.of(req("miner_heat", 2)), Stat.FIRE, 5, "Кузнечная закалка: огонь ещё слабее.");
	public static final Node DRILLER = node("driller", CRAFT, "Бур", Items.GOLDEN_PICKAXE, 1, 3, 5,
			new int[] {20, 25, 31, 38, 46}, List.of(req("master_mine", 3)), Stat.MINING, 5, "Камень поддаётся почти мгновенно.");
	public static final Node TREASURE = node("treasure", CRAFT, "Чутьё на клады", Items.DIAMOND, 2, 2, 2,
			new int[] {22, 30}, List.of(req("lucky_miner", 2)), Stat.LUCK, 1, "Ещё больше удачи в сундуках и на рыбалке.");
	public static final Node STRONG_BACK = node("strong_back", CRAFT, "Опыт рудокопа", Items.LAPIS_LAZULI, 4, 0, 3,
			new int[] {5, 8, 12}, List.of(), Stat.XP, 5, "Больше опыта из сфер опыта.");
	public static final Node FED_MINER = node("fed_miner", CRAFT, "Твёрдая рука", Items.RAW_IRON, 4, 1, 2,
			new int[] {10, 15}, List.of(req("strong_back", 2)), Stat.MINING, 5, "Добыча быстрее.");

	// ---------- Передвижение ----------
	public static final Node SPRING = node("spring", MOVE, "Пружина", Items.RABBIT_FOOT, 0, 0, 3,
			new int[] {4, 6, 9}, List.of(), Stat.JUMP, 1, "Прыжок выше.");
	public static final Node SPRING2 = node("spring2", MOVE, "Высокий прыжок", Items.SLIME_BALL, 0, 1, 2,
			new int[] {12, 18}, List.of(req("spring", 3)), Stat.JUMP, 1, "Прыжок ещё выше.");
	public static final Node SOFT_FEET = node("soft_feet", MOVE, "Мягкие стопы", Items.WHITE_WOOL, 1, 0, 3,
			new int[] {5, 7, 10}, List.of(), Stat.FALL, 5, "Меньше урона от падения.");
	public static final Node LIGHT_STEP = node("light_step", MOVE, "Лёгкий шаг", Items.SUGAR, 2, 0, 3,
			new int[] {3, 5, 8}, List.of(), Stat.SPEED, 3, "Скорость ходьбы и бега.");
	public static final Node SWIFT = node("swift", MOVE, "Быстрые ноги", Items.GOLDEN_BOOTS, 2, 1, 4,
			new int[] {7, 10, 13, 17}, List.of(req("light_step", 3)), Stat.SPEED, 2, "Ещё быстрее бег.");
	public static final Node SPRINTER = node("sprinter", MOVE, "Спринтер", Items.DIAMOND_BOOTS, 2, 2, 3,
			new int[] {14, 19, 25}, List.of(req("swift", 4)), Stat.SPEED, 2, "Максимальная скорость бега.");
	public static final Node ENDURANCE = node("endurance", MOVE, "Выносливость", Items.COOKIE, 3, 0, 3,
			new int[] {4, 6, 9}, List.of(), Stat.HUNGER, 5, "Бег и прыжки меньше расходуют голод.");
	public static final Node MARATHON = node("marathon", MOVE, "Марафонец", Items.HONEY_BOTTLE, 3, 1, 2,
			new int[] {12, 17}, List.of(req("endurance", 3)), Stat.HUNGER, 5, "Долгие походы почти без голода.");
	public static final Node GRASSHOPPER = node("grasshopper", MOVE, "Кузнечик", Items.SLIME_BLOCK, 0, 2, 2,
			new int[] {20, 28}, List.of(req("spring2", 2)), Stat.JUMP, 1, "Самый высокий прыжок.");
	public static final Node STEADY_FEET = node("steady_feet", MOVE, "Устойчивый шаг", Items.HAY_BLOCK, 1, 1, 2,
			new int[] {8, 12}, List.of(req("soft_feet", 3)), Stat.KNOCKBACK, 10, "Вас труднее сбить с ног.");
	public static final Node WIND = node("wind", MOVE, "Ветер", Items.NETHERITE_BOOTS, 2, 3, 3,
			new int[] {22, 29, 37}, List.of(req("sprinter", 3)), Stat.SPEED, 2, "Скорость ветра.");
	public static final Node WANDERER = node("wanderer", MOVE, "Закалённый путник", Items.COMPASS, 3, 2, 2,
			new int[] {16, 22}, List.of(req("marathon", 2)), Stat.HEALTH, 2, "Долгие странствия закаляют тело.");

	// ---------- Бой ----------
	public static final Node STEADFAST = node("steadfast", COMBAT, "Стойкость", Items.SHIELD, 0, 0, 2,
			new int[] {4, 7}, List.of(), Stat.KNOCKBACK, 10, "Меньше отбрасывания от ударов.");
	public static final Node UNBREAKABLE = node("unbreakable", COMBAT, "Несокрушимый", Items.ANVIL, 0, 1, 3,
			new int[] {9, 13, 18}, List.of(req("steadfast", 2)), Stat.KNOCKBACK, 10, "Вас почти невозможно отбросить.");
	public static final Node ARMORED = node("armored", COMBAT, "Бронированный", Items.CHAINMAIL_CHESTPLATE, 0, 2, 4,
			new int[] {7, 10, 14, 19}, List.of(req("unbreakable", 1)), Stat.MOB_GUARD, 5, "Меньше урона от мобов.");
	public static final Node BATTLE_HARDENED = node("battle_hardened", COMBAT, "Боевая закалка", Items.DIAMOND_CHESTPLATE,
			0, 3, 2, new int[] {18, 26}, List.of(req("armored", 2)), Stat.ARMOR, 1, "Дополнительная броня бойца.");
	public static final Node STRENGTH = node("strength", COMBAT, "Сила удара", Items.IRON_SWORD, 1, 0, 3,
			new int[] {4, 7, 11}, List.of(), Stat.DAMAGE, 0.5, "Урон в ближнем бою (и по игрокам).");
	public static final Node STRENGTH2 = node("strength2", COMBAT, "Мощный удар", Items.DIAMOND_SWORD, 1, 1, 3,
			new int[] {10, 14, 19}, List.of(req("strength", 3)), Stat.DAMAGE, 0.5, "Ещё больше урона в ближнем бою.");
	public static final Node FURY = node("fury", COMBAT, "Ярость", Items.NETHERITE_SWORD, 1, 2, 3,
			new int[] {18, 25, 33}, List.of(req("strength2", 3)), Stat.DAMAGE, 0.5, "Максимальный урон в ближнем бою.");
	public static final Node RHYTHM = node("rhythm", COMBAT, "Боевой ритм", Items.GOLDEN_SWORD, 2, 0, 2,
			new int[] {5, 9}, List.of(), Stat.ATTACK_SPEED, 3, "Оружие быстрее восстанавливается между ударами.");
	public static final Node RHYTHM2 = node("rhythm2", COMBAT, "Быстрый клинок", Items.IRON_AXE, 2, 1, 3,
			new int[] {9, 13, 18}, List.of(req("rhythm", 2)), Stat.ATTACK_SPEED, 3, "Удары восстанавливаются ещё быстрее.");
	public static final Node ARCHER = node("archer", COMBAT, "Меткий стрелок", Items.BOW, 3, 0, 3,
			new int[] {4, 7, 10}, List.of(), Stat.ARCHER, 5, "Больше урона стрелами и снарядами.");
	public static final Node SNIPER = node("sniper", COMBAT, "Снайпер", Items.CROSSBOW, 3, 1, 4,
			new int[] {10, 14, 19, 25}, List.of(req("archer", 3)), Stat.ARCHER, 5, "Ещё больше урона стрелами.");
	public static final Node HUNTER = node("hunter", COMBAT, "Охотник", Items.BONE, 4, 0, 3,
			new int[] {5, 8, 12}, List.of(), Stat.HUNTER, 5, "Больше урона по мобам (не по игрокам).");
	public static final Node SLAYER = node("slayer", COMBAT, "Истребитель", Items.WITHER_SKELETON_SKULL, 4, 1, 4,
			new int[] {10, 14, 19, 25}, List.of(req("hunter", 3)), Stat.HUNTER, 5, "Ещё больше урона по мобам.");
	public static final Node BERSERK = node("berserk", COMBAT, "Берсерк", Items.NETHERITE_AXE, 1, 3, 3,
			new int[] {28, 37, 48}, List.of(req("fury", 3)), Stat.DAMAGE, 0.5, "Сокрушительный урон в ближнем бою.");
	public static final Node BLADE_STORM = node("blade_storm", COMBAT, "Буря клинков", Items.DIAMOND_AXE, 2, 2, 3,
			new int[] {16, 22, 29}, List.of(req("rhythm2", 3)), Stat.ATTACK_SPEED, 3, "Удар за ударом без пауз.");
	public static final Node EAGLE_EYE = node("eagle_eye", COMBAT, "Орлиный глаз", Items.SPECTRAL_ARROW, 3, 2, 4,
			new int[] {18, 23, 29, 36}, List.of(req("sniper", 3)), Stat.ARCHER, 5, "Стрелы бьют больнее всего.");
	public static final Node MONSTER_BANE = node("monster_bane", COMBAT, "Гроза чудовищ", Items.DRAGON_HEAD, 4, 2, 4,
			new int[] {18, 23, 29, 36}, List.of(req("slayer", 3)), Stat.HUNTER, 5, "Мобы падают от пары ударов.");
	public static final Node LIVING_SHIELD = node("living_shield", COMBAT, "Живой щит", Items.NETHERITE_CHESTPLATE,
			0, 4, 3, new int[] {24, 31, 39}, List.of(req("battle_hardened", 1)), Stat.MOB_GUARD, 5,
			"Мобы почти не пробивают защиту.");

	// ---------- Скрытность ----------
	public static final Node SHADE = node("shade", STEALTH, "Тень", Items.NAME_TAG, 0, 0, 3,
			new int[] {4, 7, 11}, List.of(), Stat.NAME, 1, "Ваш ник над головой виден с меньшего расстояния.");
	public static final Node SHADE2 = node("shade2", STEALTH, "Глубокая тень", Items.BLACK_DYE, 0, 1, 2,
			new int[] {14, 20}, List.of(req("shade", 3)), Stat.NAME, 1, "Ник виден только совсем рядом.");
	public static final Node QUIET_STEP = node("quiet_step", STEALTH, "Тихий шаг", Items.LEATHER_BOOTS, 1, 0, 1,
			new int[] {8}, List.of(), Stat.SNEAK_NAME, 1, "Крадучись ник виден только вблизи.");
	public static final Node SILENT_FALL = node("silent_fall", STEALTH, "Бесшумное падение", Items.PHANTOM_MEMBRANE, 1, 1, 2,
			new int[] {6, 9}, List.of(req("quiet_step", 1)), Stat.FALL, 10, "Меньше урона от падения.");
	public static final Node SHADOW_STEP = node("shadow_step", STEALTH, "Скользящий шаг", Items.CHAINMAIL_BOOTS, 1, 2, 2,
			new int[] {9, 13}, List.of(req("silent_fall", 1)), Stat.SPEED, 2, "Немного быстрее передвижение.");
	public static final Node UNSEEN = node("unseen", STEALTH, "Незаметность", Items.ENDER_EYE, 2, 0, 3,
			new int[] {4, 6, 9}, List.of(), Stat.UNSEEN, 10, "Враждебные мобы замечают вас с меньшего расстояния.");
	public static final Node GHOST = node("ghost", STEALTH, "Призрак", Items.ENDER_PEARL, 2, 1, 2,
			new int[] {12, 17}, List.of(req("unseen", 3)), Stat.UNSEEN, 10, "Мобы замечают вас ещё позже.");
	public static final Node EVASION = node("evasion", STEALTH, "Уклонение", Items.ELYTRA, 2, 2, 2,
			new int[] {9, 13}, List.of(req("ghost", 1)), Stat.MOB_GUARD, 5, "Меньше урона от мобов.");
	public static final Node INVISIBLE = node("invisible", STEALTH, "Невидимка", Items.FERMENTED_SPIDER_EYE, 2, 3, 2,
			new int[] {18, 25}, List.of(req("evasion", 1)), Stat.UNSEEN, 10, "Мобы замечают вас только вплотную.");
	public static final Node DODGE = node("dodge", STEALTH, "Увёртливость", Items.RABBIT_HIDE, 3, 0, 2,
			new int[] {6, 10}, List.of(), Stat.MOB_GUARD, 5, "Удары мобов чаще проходят вскользь.");
	public static final Node ACROBAT = node("acrobat", STEALTH, "Акробат", Items.SCAFFOLDING, 3, 1, 1,
			new int[] {14}, List.of(req("dodge", 1)), Stat.JUMP, 1, "Прыжок выше.");
	public static final Node WIND_SHADOW = node("wind_shadow", STEALTH, "Тень ветра", Items.GRAY_DYE, 3, 2, 2,
			new int[] {12, 17}, List.of(req("acrobat", 1)), Stat.SPEED, 2, "Быстрое и незаметное передвижение.");

	// ---------- Мудрость ----------
	public static final Node LEGACY = node("legacy", WISDOM, "Наследие", Items.ENCHANTED_BOOK, 0, 0, 3,
			new int[] {4, 7, 10}, List.of(), Stat.LEGACY, 10, "После смерти часть опыта остаётся.");
	public static final Node ANCESTORS = node("ancestors", WISDOM, "Память предков", Items.TOTEM_OF_UNDYING, 0, 1, 2,
			new int[] {13, 19}, List.of(req("legacy", 3)), Stat.LEGACY, 10, "После смерти остаётся больше опыта.");
	public static final Node SCHOLAR = node("scholar", WISDOM, "Ученик", Items.EXPERIENCE_BOTTLE, 1, 0, 3,
			new int[] {3, 5, 8}, List.of(), Stat.XP, 5, "Больше опыта из сфер опыта.");
	public static final Node SAGE = node("sage", WISDOM, "Мудрец", Items.BOOK, 1, 1, 4,
			new int[] {7, 10, 13, 17}, List.of(req("scholar", 3)), Stat.XP, 5, "Ещё больше опыта из сфер.");
	public static final Node ENLIGHTENED = node("enlightened", WISDOM, "Просветление", Items.BEACON, 1, 2, 3,
			new int[] {15, 21, 28}, List.of(req("sage", 4)), Stat.XP, 5, "Максимум опыта из сфер.");
	public static final Node FORTUNE = node("fortune", WISDOM, "Фортуна", Items.RABBIT_FOOT, 2, 0, 2,
			new int[] {6, 10}, List.of(), Stat.LUCK, 1, "Удача: лучше добыча из сундуков и рыбалки.");
	public static final Node CLEAR_MIND = node("clear_mind", WISDOM, "Ясный ум", Items.AMETHYST_SHARD, 3, 0, 2,
			new int[] {10, 15}, List.of(), Stat.REGEN, 1, "Здоровье восполняется быстрее, когда вы сыты.");
	public static final Node THRIFT = node("thrift", WISDOM, "Бережливость", Items.BOWL, 3, 1, 2,
			new int[] {14, 20}, List.of(req("clear_mind", 1)), Stat.HUNGER, 5, "Голод расходуется медленнее.");
	public static final Node ETERNAL_MEMORY = node("eternal_memory", WISDOM, "Вечная память", Items.RECOVERY_COMPASS, 0, 2, 2,
			new int[] {24, 32}, List.of(req("ancestors", 2)), Stat.LEGACY, 10, "После смерти сохраняется почти весь опыт.");
	public static final Node AGES_WISDOM = node("ages_wisdom", WISDOM, "Мудрость веков", Items.LECTERN, 1, 3, 4,
			new int[] {26, 32, 39, 47}, List.of(req("enlightened", 3)), Stat.XP, 5, "Опыт течёт рекой.");
	public static final Node LUCKY_STAR = node("lucky_star", WISDOM, "Счастливая звезда", Items.GOLD_NUGGET, 2, 1, 2,
			new int[] {14, 20}, List.of(req("fortune", 2)), Stat.LUCK, 1, "Удача улыбается чаще.");
	public static final Node MEDITATION = node("meditation", WISDOM, "Медитация", Items.CANDLE, 3, 2, 2,
			new int[] {20, 27}, List.of(req("thrift", 1)), Stat.REGEN, 1, "Спокойствие ускоряет восстановление.");

	// ---------- Выживание: стихии и целитель ----------
	public static final Node FEAST = node("feast", SURVIVAL, "Пир", Items.CAKE, 4, 3, 3,
			new int[] {16, 22, 29}, List.of(req("gourmet", 1)), Stat.FEAST, 10, "Еда насыщает сильнее.");
	public static final Node AQUA_LUNGS = node("aqua_lungs", SURVIVAL, "Жабры", Items.TURTLE_HELMET, 5, 0, 4,
			new int[] {4, 6, 9, 13}, List.of(), Stat.AIR, 25, "Дольше можно не дышать под водой.");
	public static final Node DROWN_GUARD = node("drown_guard", SURVIVAL, "Морская закалка", Items.PRISMARINE_SHARD, 5, 1, 4,
			new int[] {8, 11, 15, 20}, List.of(req("aqua_lungs", 2)), Stat.DROWN, 15, "Меньше урона, когда тонете.");
	public static final Node FROST_SKIN = node("frost_skin", SURVIVAL, "Морозостойкость", Items.POWDER_SNOW_BUCKET, 5, 2, 3,
			new int[] {10, 14, 19}, List.of(req("drown_guard", 1)), Stat.FREEZE, 20, "Меньше урона от рыхлого снега и мороза.");
	public static final Node BLAST_GUARD = node("blast_guard", SURVIVAL, "Взрывоустойчивость", Items.TNT, 5, 3, 4,
			new int[] {14, 19, 25, 32}, List.of(req("frost_skin", 2)), Stat.BLAST, 10, "Меньше урона от криперов и взрывов.");
	public static final Node HEALER = node("healer", SURVIVAL, "Целитель", Items.HONEYCOMB, 6, 0, 3,
			new int[] {5, 8, 12}, List.of(), Stat.HEAL_BOOST, 10, "Любое лечение восстанавливает больше здоровья.");
	public static final Node ANTIDOTE = node("antidote", SURVIVAL, "Противоядие", Items.MILK_BUCKET, 6, 1, 3,
			new int[] {9, 13, 18}, List.of(req("healer", 2)), Stat.MAGIC, 10, "Яд, иссушение и магия ранят слабее.");
	public static final Node LAST_STAND = node("last_stand", SURVIVAL, "Последний рубеж", Items.CRYING_OBSIDIAN, 6, 2, 3,
			new int[] {14, 19, 25}, List.of(req("antidote", 1)), Stat.LAST_STAND, 10,
			"На последнем издыхании вы получаете меньше урона.");
	public static final Node REBIRTH = node("rebirth", SURVIVAL, "Возрождение", Items.END_CRYSTAL, 6, 3, 2,
			new int[] {24, 32}, List.of(req("last_stand", 2)), Stat.HEAL_BOOST, 10, "Лечение ещё сильнее.");

	// ---------- Добыча: бережливость и глаза шахтёра ----------
	public static final Node FROST_MINER = node("frost_miner", CRAFT, "Ледяная шахта", Items.BLUE_ICE, 3, 2, 1,
			new int[] {16}, List.of(req("forge_heat", 1)), Stat.FREEZE, 20, "Меньше урона от мороза.");
	public static final Node CAREFUL_HANDS = node("careful_hands", CRAFT, "Бережные руки", Items.SHEARS, 5, 0, 4,
			new int[] {4, 6, 9, 13}, List.of(), Stat.DURABILITY, 5, "Инструменты и броня иногда не тратят прочность.");
	public static final Node TINKER = node("tinker", CRAFT, "Мастер-ремонтник", Items.GRINDSTONE, 5, 1, 4,
			new int[] {10, 13, 17, 22}, List.of(req("careful_hands", 3)), Stat.DURABILITY, 5, "Вещи изнашиваются ещё медленнее.");
	public static final Node ETERNAL_TOOL = node("eternal_tool", CRAFT, "Вечный инструмент", Items.NETHERITE_SCRAP, 5, 2, 2,
			new int[] {24, 32}, List.of(req("tinker", 3)), Stat.DURABILITY, 5, "Инструмент служит почти вечно.");
	public static final Node MINER_EYES = node("miner_eyes", CRAFT, "Глаза шахтёра", Items.GLOW_BERRIES, 6, 0, 1,
			new int[] {12}, List.of(), Stat.NIGHT_EYE, 1, "В темноте включается ночное зрение.");
	public static final Node BLAST_MINER = node("blast_miner", CRAFT, "Взрывник", Items.GUNPOWDER, 6, 1, 2,
			new int[] {9, 13}, List.of(req("miner_eyes", 1)), Stat.BLAST, 10, "Меньше урона от взрывов.");
	public static final Node CAVE_AIR = node("cave_air", CRAFT, "Пещерный воздух", Items.GLASS_BOTTLE, 6, 2, 2,
			new int[] {10, 14}, List.of(req("blast_miner", 1)), Stat.AIR, 25, "Больше воздуха в затопленных шахтах.");

	// ---------- Передвижение: пловец и тропы ----------
	public static final Node SWIMMER = node("swimmer", MOVE, "Пловец", Items.COD, 4, 0, 2,
			new int[] {4, 7}, List.of(), Stat.AIR, 25, "Больше воздуха под водой.");
	public static final Node DEEP_DIVER = node("deep_diver", MOVE, "Ныряльщик", Items.HEART_OF_THE_SEA, 4, 1, 2,
			new int[] {10, 15}, List.of(req("swimmer", 2)), Stat.AIR, 25, "Долгие погружения.");
	public static final Node DROWN_PROOF = node("drown_proof", MOVE, "Морской волк", Items.NAUTILUS_SHELL, 4, 2, 2,
			new int[] {14, 20}, List.of(req("deep_diver", 1)), Stat.DROWN, 10, "Меньше урона от утопления.");
	public static final Node THORN_WALKER = node("thorn_walker", MOVE, "Сквозь колючки", Items.SWEET_BERRIES, 5, 0, 4,
			new int[] {3, 5, 7, 10}, List.of(), Stat.PRICKLY, 25, "Кактусы и ягодные кусты почти не ранят.");
	public static final Node SNOW_WALKER = node("snow_walker", MOVE, "Снежный путник", Items.SNOWBALL, 5, 1, 2,
			new int[] {8, 12}, List.of(req("thorn_walker", 2)), Stat.FREEZE, 10, "Меньше урона от мороза.");
	public static final Node ROAD_RATIONS = node("road_rations", MOVE, "Дорожный паёк", Items.DRIED_KELP, 5, 2, 2,
			new int[] {12, 17}, List.of(req("snow_walker", 1)), Stat.FEAST, 10, "Еда в пути насыщает сильнее.");

	// ---------- Бой: точность, кровь и шипы ----------
	public static final Node DUELIST = node("duelist", COMBAT, "Дуэлянт", Items.GOLDEN_HELMET, 2, 3, 3,
			new int[] {20, 27, 35}, List.of(req("blade_storm", 1)), Stat.PVP_GUARD, 5, "Меньше урона от других игроков.");
	public static final Node ARROW_WARD = node("arrow_ward", COMBAT, "Щит от стрел", Items.TARGET, 3, 3, 4,
			new int[] {14, 19, 25, 32}, List.of(req("eagle_eye", 1)), Stat.PROJ_GUARD, 10, "Стрелы и снаряды ранят слабее.");
	public static final Node CRIT = node("crit", COMBAT, "Критический удар", Items.FLINT, 5, 0, 4,
			new int[] {5, 8, 12, 17}, List.of(), Stat.CRIT, 3, "Шанс нанести сокрушительный удар (+50% урона).");
	public static final Node CRIT2 = node("crit2", COMBAT, "Смертельный удар", Items.POINTED_DRIPSTONE, 5, 1, 3,
			new int[] {16, 22, 29}, List.of(req("crit", 4)), Stat.CRIT, 3, "Критические удары случаются чаще.");
	public static final Node EXECUTIONER = node("executioner", COMBAT, "Палач", Items.STONE_AXE, 5, 2, 3,
			new int[] {14, 19, 25}, List.of(req("crit2", 1)), Stat.EXECUTE, 10, "Добивает раненых врагов.");
	public static final Node FIRST_BLOOD = node("first_blood", COMBAT, "Первая кровь", Items.REDSTONE, 5, 3, 3,
			new int[] {18, 24, 31}, List.of(req("executioner", 1)), Stat.FIRST_STRIKE, 10, "Первый удар по целому врагу сильнее.");
	public static final Node VAMPIRE = node("vampire", COMBAT, "Вампиризм", Items.CRIMSON_FUNGUS, 6, 0, 3,
			new int[] {8, 12, 17}, List.of(), Stat.LIFESTEAL, 3, "Часть урона в ближнем бою лечит вас.");
	public static final Node THORNS = node("thorns", COMBAT, "Шипы", Items.CACTUS, 6, 1, 3,
			new int[] {10, 14, 19}, List.of(req("vampire", 1)), Stat.THORNS, 5, "Часть урона возвращается нападающему.");
	public static final Node BLOODLUST = node("bloodlust", COMBAT, "Жажда крови", Items.NETHER_WART, 6, 2, 2,
			new int[] {20, 27}, List.of(req("thorns", 2)), Stat.LIFESTEAL, 3, "Удары лечат ещё сильнее.");
	public static final Node IRON_THORNS = node("iron_thorns", COMBAT, "Железные шипы", Items.IRON_BARS, 6, 3, 3,
			new int[] {22, 29, 37}, List.of(req("bloodlust", 1)), Stat.THORNS, 5, "Нападающие ранят сами себя.");

	// ---------- Скрытность: противоядие и неуловимость ----------
	public static final Node VENOM_GUARD = node("venom_guard", STEALTH, "Иммунитет к ядам", Items.SPIDER_EYE, 4, 0, 2,
			new int[] {6, 10}, List.of(), Stat.MAGIC, 10, "Яд и иссушение ранят слабее.");
	public static final Node NIGHT_HUNTER = node("night_hunter", STEALTH, "Ночной охотник", Items.GLOW_INK_SAC, 4, 1, 1,
			new int[] {14}, List.of(req("venom_guard", 1)), Stat.EXECUTE, 10, "Раненых врагов добиваете легче.");
	public static final Node SLIP = node("slip", STEALTH, "Неуловимость", Items.COBWEB, 5, 0, 3,
			new int[] {7, 11, 16}, List.of(), Stat.DODGE, 3, "Шанс полностью увернуться от удара моба.");
	public static final Node MIRAGE = node("mirage", STEALTH, "Мираж", Items.GLASS_PANE, 5, 1, 2,
			new int[] {18, 25}, List.of(req("slip", 2)), Stat.DODGE, 3, "Мобы промахиваются ещё чаще.");
	public static final Node BACKSTAB = node("backstab", STEALTH, "Удар в спину", Items.STONE_SWORD, 5, 2, 1,
			new int[] {20}, List.of(req("mirage", 1)), Stat.FIRST_STRIKE, 10, "Внезапный первый удар сильнее.");

	// ---------- Мудрость: знахарство и мастерство ----------
	public static final Node JACKPOT = node("jackpot", WISDOM, "Сорвать куш", Items.EMERALD_BLOCK, 2, 2, 1,
			new int[] {28}, List.of(req("lucky_star", 2)), Stat.LUCK, 1, "Ещё немного удачи.");
	public static final Node HERBALIST = node("herbalist", WISDOM, "Травник", Items.POPPY, 4, 0, 2,
			new int[] {6, 10}, List.of(), Stat.HEAL_BOOST, 10, "Лечение сильнее.");
	public static final Node CLEANSING = node("cleansing", WISDOM, "Очищение", Items.HONEY_BOTTLE, 4, 1, 2,
			new int[] {12, 17}, List.of(req("herbalist", 2)), Stat.MAGIC, 10, "Яд и магия ранят слабее.");
	public static final Node BOOKWORM = node("bookworm", WISDOM, "Книжный червь", Items.BOOKSHELF, 5, 0, 3,
			new int[] {6, 9, 13}, List.of(), Stat.XP, 5, "Больше опыта из сфер.");
	public static final Node ASCETIC = node("ascetic", WISDOM, "Аскет", Items.SUSPICIOUS_STEW, 5, 1, 2,
			new int[] {12, 17}, List.of(req("bookworm", 2)), Stat.FEAST, 10, "Даже простая еда насыщает сильнее.");
	public static final Node NEAT_HANDS = node("neat_hands", WISDOM, "Аккуратность", Items.BRUSH, 5, 2, 2,
			new int[] {16, 22}, List.of(req("ascetic", 1)), Stat.DURABILITY, 5, "Вещи изнашиваются медленнее.");

	// ---------- Казино ----------
	public static final Node CAREFUL_PLAYER = node("careful_player", CASINO, "Аккуратный игрок", Items.COMPARATOR, 0, 0, 3,
			new int[] {3, 5, 8}, List.of(), Stat.MACHINE_GUARD, 10, "Автомат реже ломается после ваших прокруток.");
	public static final Node MAINTENANCE = node("maintenance", CASINO, "Техобслуживание", Items.OBSERVER, 0, 1, 2,
			new int[] {10, 14}, List.of(req("careful_player", 3)), Stat.MACHINE_GUARD, 10, "Ещё меньше поломок.");
	public static final Node GOLDEN_TOUCH = node("golden_touch", CASINO, "Лёгкая рука", Items.GOLD_INGOT, 0, 2, 1,
			new int[] {20}, List.of(req("maintenance", 2)), Stat.MACHINE_GUARD, 10, "Автомат почти не ломается от ваших прокруток.");
	public static final Node MECHANIC = node("mechanic", CASINO, "Механик", Items.TRIPWIRE_HOOK, 1, 0, 3,
			new int[] {3, 5, 8}, List.of(), Stat.REPAIR_SPEED, 15, "Каждое действие инструментом чинит больше.");
	public static final Node ENGINEER = node("engineer", CASINO, "Инженер", Items.PISTON, 1, 1, 2,
			new int[] {10, 14}, List.of(req("mechanic", 3)), Stat.REPAIR_SPEED, 15, "Ремонт ещё быстрее.");

	// ---------- Sync (ток) ----------
	public static final Node TRAIN_SLOT = node("train_slot", SYNC_TREE, "Лишняя тренировка", Items.IRON_SWORD, 0, 0, 1,
			new int[] {4}, List.of(), Stat.TRAIN_LIMIT, 1, "+1 тренировка в сутки к лимиту сервера.");
	public static final Node TRAIN_SLOT2 = node("train_slot2", SYNC_TREE, "Двойная смена", Items.GOLDEN_SWORD, 0, 1, 2,
			new int[] {8, 12}, List.of(req("train_slot", 1)), Stat.TRAIN_LIMIT, 1, "Ещё больше тренировок в день.");
	public static final Node TRAIN_MARATHON = node("train_marathon", SYNC_TREE, "Марафон зала", Items.NETHERITE_SWORD, 0, 2, 2,
			new int[] {16, 22}, List.of(req("train_slot2", 2)), Stat.TRAIN_LIMIT, 1, "Максимум дополнительных тренировок.");
	public static final Node PROTEIN_CORE = node("protein_core", SYNC_TREE, "Питание", Items.COOKED_BEEF, 1, 0, 3,
			new int[] {4, 7, 10}, List.of(), Stat.PROTEIN_TIME, 10, "Протеиновые бафы длятся дольше.");
	public static final Node PROTEIN_PLUS = node("protein_plus", SYNC_TREE, "Спортивное питание", Items.GOLDEN_CARROT, 1, 1, 2,
			new int[] {12, 18}, List.of(req("protein_core", 3)), Stat.PROTEIN_TIME, 15, "Батончики и коктейли действуют ещё дольше.");
	public static final Node TRAIN_BONUS = node("train_bonus", SYNC_TREE, "Стипендия зала", Items.GOLD_NUGGET, 1, 2, 3,
			new int[] {6, 10, 14}, List.of(req("protein_core", 1)), Stat.TRAIN_PAY, 10, "Больше WC за пройденную тренировку.");
	public static final Node TRAIN_SPONSOR = node("train_sponsor", SYNC_TREE, "Спонсор", Items.EMERALD, 1, 3, 2,
			new int[] {16, 24}, List.of(req("train_bonus", 3)), Stat.TRAIN_PAY, 15, "Ещё больше WC с тренировок.");
	public static final Node CURRENT = node("current", SYNC_TREE, "Ток", Items.REDSTONE, 2, 0, 3,
			new int[] {5, 8, 12}, List.of(), Stat.TRAIN_PAY, 5, "Ток усиливает отдачу от тренировок.");
	public static final Node VOLTAGE = node("voltage", SYNC_TREE, "Напряжение", Items.REDSTONE_BLOCK, 2, 1, 2,
			new int[] {14, 20}, List.of(req("current", 3)), Stat.PROTEIN_TIME, 10, "Напряжение продлевает протеиновые бафы.");
	public static final Node AMPERE = node("ampere", SYNC_TREE, "Ампер", Items.LIGHTNING_ROD, 2, 2, 2,
			new int[] {18, 26}, List.of(req("voltage", 1)), Stat.TRAIN_LIMIT, 1, "Больше тренировок за счёт тока.");

	// ---------- Новые вершины веток ----------
	public static final Node TITAN_HEART = node("titan_heart", SURVIVAL, "Сердце титана", Items.NETHER_STAR, 7, 0, 3,
			new int[] {24, 34, 46}, List.of(req("rebirth", 2)), Stat.HEALTH, 2, "Дополнительные сердца для самых опасных приключений.");
	public static final Node MASTER_TOOL = node("master_tool", CRAFT, "Инструмент мастера", Items.NETHERITE_PICKAXE, 7, 0, 3,
			new int[] {22, 31, 42}, List.of(req("eternal_tool", 2)), Stat.DURABILITY, 5, "Лучшие инструменты служат ещё дольше.");
	public static final Node HORIZON = node("horizon", MOVE, "За горизонт", Items.SPYGLASS, 6, 0, 3,
			new int[] {20, 28, 38}, List.of(req("road_rations", 2)), Stat.SPEED, 2, "Награда опытному путешественнику — постоянная скорость.");
	public static final Node WARLORD = node("warlord", COMBAT, "Воевода", Items.NETHERITE_HELMET, 7, 0, 3,
			new int[] {28, 38, 50}, List.of(req("iron_thorns", 2)), Stat.ARMOR, 1, "Закалка ветерана даёт дополнительную броню.");
	public static final Node VOID_GHOST = node("void_ghost", STEALTH, "Призрак пустоты", Items.ECHO_SHARD, 6, 0, 3,
			new int[] {22, 31, 42}, List.of(req("backstab", 1)), Stat.UNSEEN, 10, "Враждебным мобам ещё сложнее вас заметить.");
	public static final Node GRAND_SAGE = node("grand_sage", WISDOM, "Великий мудрец", Items.WRITABLE_BOOK, 6, 0, 3,
			new int[] {24, 34, 46}, List.of(req("neat_hands", 2)), Stat.XP, 5, "Любые сферы опыта приносят больше знаний.");
	public static final Node CASINO_MASTER = node("casino_master", CASINO, "Мастер зала", Items.NETHERITE_INGOT, 2, 0, 3,
			new int[] {16, 24, 34}, List.of(req("engineer", 2)), Stat.REPAIR_SPEED, 15, "Игровые автоматы чинятся заметно быстрее.");
	public static final Node OVERCHARGE = node("overcharge", SYNC_TREE, "Перегрузка", Items.GLOWSTONE_DUST, 3, 0, 3,
			new int[] {20, 29, 40}, List.of(req("ampere", 2)), Stat.TRAIN_PAY, 10, "Тренировки приносят ещё больше WC.");

	// ---------- 32 дополнительные прокачки: по четыре на каждую ветку ----------
	public static final Node LIFE_WARDEN = node("life_warden", SURVIVAL, "Хранитель жизни", Items.GOLDEN_APPLE, 8, 0, 3,
			new int[] {18, 26, 36}, List.of(req("titan_heart", 1)), Stat.HEAL_BOOST, 10, "Лечение восстанавливает больше здоровья.");
	public static final Node LIFE_BASTION = node("life_bastion", SURVIVAL, "Живой бастион", Items.SHIELD, 8, 1, 3,
			new int[] {24, 33, 44}, List.of(req("life_warden", 2)), Stat.MOB_GUARD, 5, "Враждебные существа наносят меньше урона.");
	public static final Node LIFE_PHOENIX = node("life_phoenix", SURVIVAL, "Кровь феникса", Items.BLAZE_POWDER, 9, 0, 3,
			new int[] {22, 31, 42}, List.of(req("life_warden", 2)), Stat.FIRE, 10, "Огонь и лава становятся менее опасными.");
	public static final Node LIFE_COLOSSUS = node("life_colossus", SURVIVAL, "Колосс", Items.HEART_OF_THE_SEA, 9, 1, 3,
			new int[] {30, 42, 56}, List.of(req("life_bastion", 3), req("life_phoenix", 3)), Stat.HEALTH, 2, "Высшая ступень запаса здоровья.");

	public static final Node ORE_PRECISION = node("ore_precision", CRAFT, "Точная работа", Items.DIAMOND_PICKAXE, 8, 0, 3,
			new int[] {18, 26, 36}, List.of(req("master_tool", 1)), Stat.MINING, 5, "Блоки добываются быстрее.");
	public static final Node ORE_TEMPER = node("ore_temper", CRAFT, "Закалка металла", Items.IRON_BLOCK, 8, 1, 3,
			new int[] {24, 33, 44}, List.of(req("ore_precision", 2)), Stat.DURABILITY, 5, "Инструменты медленнее теряют прочность.");
	public static final Node ORE_DEEP_EYE = node("ore_deep_eye", CRAFT, "Зрение глубин", Items.SEA_LANTERN, 9, 0, 1,
			new int[] {28}, List.of(req("ore_precision", 3)), Stat.NIGHT_EYE, 1, "Подземная темнота больше не мешает работе.");
	public static final Node ORE_LEGEND = node("ore_legend", CRAFT, "Легенда шахт", Items.NETHERITE_BLOCK, 9, 1, 3,
			new int[] {32, 44, 58}, List.of(req("ore_temper", 3), req("ore_deep_eye", 1)), Stat.MINING, 5, "Предельная скорость опытного шахтёра.");

	public static final Node PATH_SCOUT = node("path_scout", MOVE, "Разведчик", Items.MAP, 7, 0, 3,
			new int[] {18, 26, 36}, List.of(req("horizon", 1)), Stat.SPEED, 2, "Быстрее исследовать незнакомые земли.");
	public static final Node PATH_FEATHER = node("path_feather", MOVE, "Лёгкость пера", Items.FEATHER, 7, 1, 3,
			new int[] {22, 31, 42}, List.of(req("path_scout", 2)), Stat.FALL, 10, "Падения причиняют меньше урона.");
	public static final Node PATH_TIDE = node("path_tide", MOVE, "Покоритель глубин", Items.TRIDENT, 8, 0, 3,
			new int[] {24, 34, 46}, List.of(req("path_scout", 2)), Stat.AIR, 25, "Под водой можно дышать дольше.");
	public static final Node PATH_COMET = node("path_comet", MOVE, "Комета", Items.FIREWORK_ROCKET, 8, 1, 3,
			new int[] {32, 44, 58}, List.of(req("path_feather", 3), req("path_tide", 3)), Stat.SPEED, 2, "Высшая скорость путешественника.");

	public static final Node FIGHT_VANGUARD = node("fight_vanguard", COMBAT, "Авангард", Items.IRON_CHESTPLATE, 8, 0, 3,
			new int[] {20, 28, 38}, List.of(req("warlord", 1)), Stat.ARMOR, 1, "Дополнительная броня в затяжном бою.");
	public static final Node FIGHT_CRUSHER = node("fight_crusher", COMBAT, "Сокрушитель", Items.NETHERITE_AXE, 8, 1, 3,
			new int[] {25, 35, 47}, List.of(req("fight_vanguard", 2)), Stat.DAMAGE, 0.5, "Ближние атаки становятся сильнее.");
	public static final Node FIGHT_MARKSMAN = node("fight_marksman", COMBAT, "Мастер прицела", Items.ARROW, 9, 0, 3,
			new int[] {22, 31, 42}, List.of(req("fight_vanguard", 2)), Stat.ARCHER, 5, "Снаряды наносят больше урона.");
	public static final Node FIGHT_CHAMPION = node("fight_champion", COMBAT, "Чемпион", Items.DRAGON_BREATH, 9, 1, 3,
			new int[] {34, 46, 60}, List.of(req("fight_crusher", 3), req("fight_marksman", 3)), Stat.CRIT, 3, "Высшая ступень боевого мастерства.");

	public static final Node SHADOW_MIST = node("shadow_mist", STEALTH, "Туман", Items.GRAY_STAINED_GLASS, 7, 0, 3,
			new int[] {18, 26, 36}, List.of(req("void_ghost", 1)), Stat.UNSEEN, 10, "Мобы замечают вас с меньшего расстояния.");
	public static final Node SHADOW_REFLEX = node("shadow_reflex", STEALTH, "Рефлекс", Items.RABBIT_FOOT, 7, 1, 3,
			new int[] {23, 32, 43}, List.of(req("shadow_mist", 2)), Stat.DODGE, 3, "Повышается шанс увернуться от удара моба.");
	public static final Node SHADOW_FANG = node("shadow_fang", STEALTH, "Клык тени", Items.IRON_SWORD, 8, 0, 3,
			new int[] {24, 34, 46}, List.of(req("shadow_mist", 2)), Stat.FIRST_STRIKE, 10, "Первый внезапный удар становится сильнее.");
	public static final Node SHADOW_PHANTOM = node("shadow_phantom", STEALTH, "Фантом", Items.PHANTOM_MEMBRANE, 8, 1, 3,
			new int[] {32, 44, 58}, List.of(req("shadow_reflex", 3), req("shadow_fang", 3)), Stat.UNSEEN, 10, "Вершина искусства незаметности.");

	public static final Node MIND_ARCHIVIST = node("mind_archivist", WISDOM, "Архивариус", Items.CHISELED_BOOKSHELF, 7, 0, 3,
			new int[] {18, 26, 36}, List.of(req("grand_sage", 1)), Stat.XP, 5, "Сферы опыта дают больше знаний.");
	public static final Node MIND_ALCHEMIST = node("mind_alchemist", WISDOM, "Алхимик", Items.BREWING_STAND, 7, 1, 3,
			new int[] {23, 32, 43}, List.of(req("mind_archivist", 2)), Stat.MAGIC, 10, "Магический урон и отрицательные эффекты слабее.");
	public static final Node MIND_PROPHET = node("mind_prophet", WISDOM, "Провидец", Items.ENDER_EYE, 8, 0, 3,
			new int[] {24, 34, 46}, List.of(req("mind_archivist", 2)), Stat.LUCK, 1, "Удача чаще сопровождает ваши находки.");
	public static final Node MIND_ORACLE = node("mind_oracle", WISDOM, "Оракул", Items.ENCHANTING_TABLE, 8, 1, 3,
			new int[] {32, 44, 58}, List.of(req("mind_alchemist", 3), req("mind_prophet", 3)), Stat.LEGACY, 10, "После смерти сохраняется больше опыта.");

	public static final Node CHIPS_TECHNICIAN = node("chips_technician", CASINO, "Техник", Items.REPEATER, 3, 0, 3,
			new int[] {14, 21, 30}, List.of(req("casino_master", 1)), Stat.REPAIR_SPEED, 15, "Каждое действие ремонта эффективнее.");
	public static final Node CHIPS_INSURANCE = node("chips_insurance", CASINO, "Страховка", Items.PAPER, 3, 1, 3,
			new int[] {18, 26, 36}, List.of(req("chips_technician", 2)), Stat.MACHINE_GUARD, 10, "Автомат реже ломается после прокрутки.");
	public static final Node CHIPS_FOREMAN = node("chips_foreman", CASINO, "Прораб", Items.SMITHING_TABLE, 4, 0, 3,
			new int[] {22, 31, 42}, List.of(req("chips_technician", 2)), Stat.REPAIR_SPEED, 15, "Ремонт игровых автоматов ускоряется.");
	public static final Node CHIPS_MAGNATE = node("chips_magnate", CASINO, "Магнат", Items.DIAMOND, 4, 1, 3,
			new int[] {30, 42, 56}, List.of(req("chips_insurance", 3), req("chips_foreman", 3)), Stat.MACHINE_GUARD, 10, "Максимальная защита игровых автоматов.");

	public static final Node SYNC_DISCIPLINE = node("sync_discipline", SYNC_TREE, "Дисциплина", Items.CLOCK, 4, 0, 3,
			new int[] {16, 23, 32}, List.of(req("overcharge", 1)), Stat.TRAIN_LIMIT, 1, "Больше доступных тренировок в сутки.");
	public static final Node SYNC_METABOLISM = node("sync_metabolism", SYNC_TREE, "Метаболизм", Items.GOLDEN_CARROT, 4, 1, 3,
			new int[] {20, 29, 40}, List.of(req("sync_discipline", 2)), Stat.PROTEIN_TIME, 10, "Протеиновые усиления действуют дольше.");
	public static final Node SYNC_POWER = node("sync_power", SYNC_TREE, "Сила импульса", Items.PISTON, 5, 0, 3,
			new int[] {22, 31, 42}, List.of(req("sync_discipline", 2)), Stat.TRAIN_PAY, 10, "Успешные тренировки приносят больше WC.");
	public static final Node SYNC_OLYMPIAN = node("sync_olympian", SYNC_TREE, "Олимпиец", Items.BEACON, 5, 1, 3,
			new int[] {32, 44, 58}, List.of(req("sync_metabolism", 3), req("sync_power", 3)), Stat.TRAIN_PAY, 10, "Вершина развития спортсмена.");

	public static final List<Node> NODES = Collections.unmodifiableList(NODE_LIST);

	public static final int NAME_DISTANCE = 64;
	public static final int SNEAK_DISTANCE = 32;
	private static final int[] SHADE_DISTANCE = {64, 48, 32, 20, 16, 12};
	public static final int QUIET_SNEAK_DISTANCE = 8;

	public static final List<Quest> QUESTS = List.of(
			new Quest("life_undead", LIFE, "Охота на нежить", "Убить нежить: зомби, скелеты, утопленники и т.п.",
					new long[] {10, 30, 80, 200}, new int[] {3, 4, 6, 9}, 1, ""),
			new Quest("life_food", LIFE, "Разнообразная еда", "Съесть разные виды еды.",
					new long[] {3, 6, 10, 15}, new int[] {2, 3, 5, 7}, 1, " видов"),
			new Quest("life_alive", LIFE, "Живучесть", "Активно играть без смерти (лучшая серия, АФК не считается).",
					new long[] {1800, 5400, 14400}, new int[] {3, 5, 8}, 60, " мин"),
			new Quest("ore_stone", ORE, "Каменотёс", "Добыть природный камень, глубинный сланец, незерак.",
					new long[] {250, 1000, 3000}, new int[] {2, 4, 7}, 1, ""),
			new Quest("ore_ores", ORE, "Рудокоп", "Добыть руду (поставленная игроком не считается).",
					new long[] {20, 60, 150, 400}, new int[] {3, 4, 6, 9}, 1, ""),
			new Quest("ore_types", ORE, "Знаток руд", "Добыть разные виды руды.",
					new long[] {3, 6, 9}, new int[] {3, 5, 7}, 1, " видов"),
			new Quest("ore_logs", ORE, "Лесоруб", "Добыть брёвна.",
					new long[] {64, 256, 1000}, new int[] {2, 3, 5}, 1, ""),
			new Quest("path_walk", PATH, "Путник", "Пройти пешком или бегом (АФК не считается).",
					new long[] {200_000, 1_000_000, 4_000_000}, new int[] {3, 5, 8}, 100, " м"),
			new Quest("path_biomes", PATH, "Исследователь", "Побывать в разных биомах.",
					new long[] {5, 12, 25}, new int[] {3, 5, 8}, 1, " биомов"),
			new Quest("path_far", PATH, "Дальний поход", "Уйти пешком от точки спавна мира (телепорты не считаются).",
					new long[] {1000, 3000, 6000}, new int[] {2, 4, 6}, 1, " бл."),
			new Quest("path_jump", PATH, "Прыгун", "Прыгать (не больше 300 прыжков в день).",
					new long[] {300, 1500}, new int[] {1, 3}, 1, ""),
			new Quest("fight_kills", FIGHT, "Боец", "Убить враждебных мобов (до 40 одного вида в день).",
					new long[] {25, 100, 300}, new int[] {2, 4, 7}, 1, ""),
			new Quest("fight_types", FIGHT, "Универсал", "Убить разные виды враждебных мобов.",
					new long[] {4, 8, 14}, new int[] {3, 5, 8}, 1, " видов"),
			new Quest("fight_elite", FIGHT, "Сильный противник",
					"Убить опасных мобов: эндермен, ифрит, ведьма, визер-скелет, разоритель и т.п.",
					new long[] {5, 20, 50}, new int[] {3, 5, 8}, 1, ""),
			new Quest("fight_boss", FIGHT, "Боссы", "Победить разных боссов: иссушитель, дракон, хранитель.",
					new long[] {1, 2, 3}, new int[] {6, 8, 10}, 1, ""),
			new Quest("shadow_sneak", SHADOW, "Тихие шаги", "Пройти крадучись (до 400 м в день).",
					new long[] {20_000, 100_000, 300_000}, new int[] {2, 4, 7}, 100, " м"),
			new Quest("shadow_strike", SHADOW, "Удар из тени", "Убить враждебного моба, сидя на шифте.",
					new long[] {10, 40, 100}, new int[] {3, 5, 7}, 1, ""),
			new Quest("shadow_night", SHADOW, "Ночной странник", "Активно провести время ночью под открытым небом.",
					new long[] {1200, 3600, 9000}, new int[] {2, 4, 6}, 60, " мин"),
			new Quest("mind_enchant", MIND, "Зачарователь", "Зачаровать предметы на столе зачаровывания.",
					new long[] {3, 10, 30}, new int[] {3, 5, 8}, 1, ""),
			new Quest("mind_trade", MIND, "Торговец", "Совершить сделки с жителями.",
					new long[] {10, 40, 120}, new int[] {2, 4, 7}, 1, ""),
			new Quest("mind_level", MIND, "Опытный", "Достичь уровня опыта.",
					new long[] {10, 20, 30}, new int[] {2, 4, 6}, 1, " ур."),
			new Quest("chips_spins", CHIPS, "Игрок", "Крутить «Рулетку удачи» со ставкой от 100 WC.",
					new long[] {10, 50, 200}, new int[] {2, 4, 7}, 1, ""),
			new Quest("chips_repair", CHIPS, "Ремонтник", "Чинить сломанные игровые автоматы (участие в ремонте).",
					new long[] {1, 5, 20}, new int[] {3, 5, 8}, 1, ""),
			new Quest("sync_train", SYNC, "Зал", "Успешно пройти тренировки.",
					new long[] {3, 10, 30}, new int[] {3, 5, 8}, 1, ""),
			new Quest("life_hunter", LIFE, "Защитник мира", "Убивать любых враждебных мобов.",
					new long[] {50, 200, 600}, new int[] {3, 5, 9}, 1, ""),
			new Quest("ore_miner", ORE, "Шахтёрская смена", "Добывать природную руду, камень и брёвна.",
					new long[] {500, 2000, 6000}, new int[] {3, 5, 9}, 1, " блоков"),
			new Quest("path_marathon", PATH, "Большой марафон", "Преодолевать расстояние пешком или бегом.",
					new long[] {500_000, 2_000_000, 8_000_000}, new int[] {3, 6, 10}, 100, " м"),
			new Quest("fight_undead", FIGHT, "Истребитель нежити", "Побеждать зомби, скелетов, утопленников и другую нежить.",
					new long[] {25, 100, 300}, new int[] {3, 6, 10}, 1, ""),
			new Quest("shadow_master", SHADOW, "Мастер скрытности", "Передвигаться крадучись, оставаясь незаметным.",
					new long[] {50_000, 200_000, 600_000}, new int[] {3, 6, 10}, 100, " м"),
			new Quest("mind_study", MIND, "Практические знания", "Зачаровывать предметы и торговать с жителями.",
					new long[] {15, 60, 180}, new int[] {3, 6, 10}, 1, " действий"),
			new Quest("chips_regular", CHIPS, "Завсегдатай", "Крутить рулетку или помогать с ремонтом автоматов.",
					new long[] {25, 100, 300}, new int[] {3, 6, 10}, 1, " действий"),
			new Quest("sync_athlete", SYNC, "Опытный атлет", "Регулярно и успешно проходить тренировки.",
					new long[] {10, 35, 100}, new int[] {4, 7, 12}, 1, "")
	);

	public static final List<Daily> DAILIES = List.of(
			new Daily("d_life", LIFE, "Сегодня: 30 минут активной игры", 1800, 2, 60, " мин"),
			new Daily("d_ore", ORE, "Сегодня: добыть 32 руды", 32, 2, 1, ""),
			new Daily("d_path", PATH, "Сегодня: пройти 1500 м", 150_000, 2, 100, " м"),
			new Daily("d_fight", FIGHT, "Сегодня: убить 20 враждебных мобов", 20, 2, 1, ""),
			new Daily("d_shadow", SHADOW, "Сегодня: пройти крадучись 150 м", 15_000, 2, 100, " м"),
			new Daily("d_mind", MIND, "Сегодня: 5 сделок с жителями", 5, 2, 1, ""),
			new Daily("d_chips", CHIPS, "Сегодня: помочь починить игровой автомат", 1, 2, 1, ""),
			new Daily("d_sync", SYNC, "Сегодня: пройти 1 тренировку", 1, 2, 1, "")
	);

	private SkillTree() {
	}

	private static Req req(String node, int level) {
		return new Req(node, level);
	}

	private static Node node(String id, Branch branch, String title, Item icon, int col, int row, int maxLevel,
			int[] costs, List<Req> requires, Stat stat, double per, String description) {
		return node(id, branch, title, icon, col, row, maxLevel, costs, 0, requires, stat, per, description);
	}

	private static Node node(String id, Branch branch, String title, Item icon, int col, int row, int maxLevel,
			int[] costs, int costStep, List<Req> requires, Stat stat, double per, String description) {
		Node node = new Node(NODE_LIST.size(), id, branch, title, icon, col, row, maxLevel, costs, costStep, requires,
				stat, per, description);
		NODE_LIST.add(node);
		return node;
	}

	public static Node node(String id) {
		if (id == null) {
			return null;
		}
		for (Node n : NODE_LIST) {
			if (n.id.equals(id)) {
				return n;
			}
		}
		return null;
	}

	public static List<Node> nodesOf(Branch branch) {
		List<Node> out = new ArrayList<>();
		for (Node n : NODE_LIST) {
			if (n.branch == branch) {
				out.add(n);
			}
		}
		return out;
	}

	public static Currency currency(String raw) {
		if (raw == null) {
			return null;
		}
		String low = raw.trim().toLowerCase(Locale.ROOT);
		for (Currency c : CURRENCIES) {
			if (c.id.equals(low) || c.alias.equals(low) || c.title.toLowerCase(Locale.ROOT).equals(low)) {
				return c;
			}
		}
		return null;
	}

	public static Quest quest(String id) {
		for (Quest q : QUESTS) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		return null;
	}

	public static Daily daily(String id) {
		for (Daily d : DAILIES) {
			if (d.id.equals(id)) {
				return d;
			}
		}
		return null;
	}

	public static Branch branchOf(Currency c) {
		for (Branch b : BRANCHES) {
			if (b.currency == c) {
				return b;
			}
		}
		return null;
	}

	// ---------- характеристики ----------

	/** Итог каждой характеристики по уровням узлов (индекс — {@link Node#index}), с потолками. */
	public static double[] totals(int[] levels) {
		double[] out = new double[Stat.values().length];
		if (levels == null) {
			return out;
		}
		for (Node n : NODE_LIST) {
			if (n.stat != Stat.NONE && n.index < levels.length && levels[n.index] > 0) {
				out[n.stat.ordinal()] += n.per * levels[n.index];
			}
		}
		for (Stat s : Stat.values()) {
			out[s.ordinal()] = Math.min(s.cap, out[s.ordinal()]);
		}
		return out;
	}

	public static int nameDistance(double points) {
		int i = (int) Math.floor(points);
		return SHADE_DISTANCE[Math.max(0, Math.min(SHADE_DISTANCE.length - 1, i))];
	}

	public static int sneakDistance(double namePoints, double sneakPoints) {
		return Math.min(nameDistance(namePoints), sneakPoints > 0 ? QUIET_SNEAK_DISTANCE : SNEAK_DISTANCE);
	}

	/** Множитель к урону, «уменьшенному на pct%». */
	public static double less(double pct) {
		return Math.max(0.0, 1.0 - pct / 100.0);
	}

	/** Множитель к величине, «увеличенной на pct%». */
	public static double more(double pct) {
		return 1.0 + pct / 100.0;
	}

	/** Прибавка к скорости прыжка за очки «Прыжка». */
	public static float jumpBonus(double points) {
		return (float) (0.033 * points);
	}

	/** Описание вклада характеристики {@code value}. */
	public static String describe(Stat stat, double value) {
		int pct = (int) Math.round(value);
		return switch (stat) {
			case HEALTH -> "+" + fmt(value) + " ед. здоровья (+" + fmt(value / 2) + " сердц.)";
			case ARMOR -> "+" + fmt(value) + " брони";
			case TOUGHNESS -> "+" + fmt(value) + " прочности брони";
			case REGEN -> "+" + fmt(value) + " ед. здоровья в минуту при сытости от 18";
			case FALL -> "−" + pct + "% урона от падения";
			case HUNGER -> "−" + pct + "% расхода голода";
			case FIRE -> "−" + pct + "% урона от огня и лавы";
			case MOB_GUARD -> "−" + pct + "% урона от мобов";
			case MINING -> "+" + pct + "% скорости добычи";
			case LUCK -> "+" + fmt(value) + " удачи (сундуки и рыбалка)";
			case SPEED -> "+" + pct + "% скорости";
			case JUMP -> "прыжок ≈ +" + String.format(Locale.US, "%.2f", value * 0.17) + " блока";
			case DAMAGE -> "+" + fmt(value) + " урона";
			case ATTACK_SPEED -> "+" + pct + "% скорости атаки";
			case KNOCKBACK -> "−" + pct + "% отбрасывания";
			case HUNTER -> "+" + pct + "% урона по мобам";
			case ARCHER -> "+" + pct + "% урона стрелами и снарядами";
			case NAME -> "ник виден до " + nameDistance(value) + " бл. (обычно " + NAME_DISTANCE + ")";
			case SNEAK_NAME -> "крадучись ник виден до " + QUIET_SNEAK_DISTANCE + " бл. (обычно " + SNEAK_DISTANCE + ")";
			case UNSEEN -> "мобы замечают на " + pct + "% ближе";
			case XP -> "+" + pct + "% опыта из сфер";
			case LEGACY -> "после смерти остаётся " + pct + "% опыта";
			case AIR -> "+" + pct + "% запаса воздуха под водой";
			case DROWN -> "−" + pct + "% урона от утопления";
			case BLAST -> "−" + pct + "% урона от взрывов";
			case FREEZE -> "−" + pct + "% урона от мороза";
			case MAGIC -> "−" + pct + "% урона от яда, иссушения и магии";
			case PRICKLY -> "−" + pct + "% урона от кактусов и ягодных кустов";
			case PROJ_GUARD -> "−" + pct + "% урона от стрел и снарядов";
			case PVP_GUARD -> "−" + pct + "% урона от игроков";
			case LAST_STAND -> "−" + pct + "% урона, когда здоровья меньше 30%";
			case DODGE -> pct + "% шанс увернуться от удара моба";
			case CRIT -> pct + "% шанс нанести +50% урона в ближнем бою";
			case EXECUTE -> "+" + pct + "% урона по целям со здоровьем ниже 35%";
			case FIRST_STRIKE -> "+" + pct + "% урона по целям с полным здоровьем";
			case LIFESTEAL -> pct + "% урона в ближнем бою лечит вас";
			case THORNS -> pct + "% урона в ближнем бою возвращается нападающему";
			case HEAL_BOOST -> "+" + pct + "% к любому лечению";
			case DURABILITY -> pct + "% шанс, что инструмент или броня не потратит прочность";
			case FEAST -> "+" + pct + "% сытости от еды";
			case NIGHT_EYE -> "ночное зрение в темноте";
			case MACHINE_GUARD -> "−" + pct + "% шанса поломки автомата после вашей прокрутки";
			case REPAIR_SPEED -> "+" + pct + "% ремонта за одно действие инструментом";
			case TRAIN_LIMIT -> "+" + fmt(value) + " тренировок в сутки";
			case PROTEIN_TIME -> "+" + pct + "% длительности протеиновых бафов";
			case TRAIN_PAY -> "+" + pct + "% WC за тренировку";
			case NONE -> "";
		};
	}

	/** Эффект узла на уровне {@code level} (0 — нет эффекта). Автопечь описывает сервер. */
	public static String effect(Node node, int level) {
		if (level <= 0) {
			return "нет";
		}
		if (node.stat == Stat.NONE) {
			return switch (node.id) {
				case "workbench" -> "верстак открыт";
				case "backpack" -> (level * 9) + " слотов рюкзака";
				case "furnace" -> "автопечь ур. " + level;
				default -> "уровень " + level;
			};
		}
		if (node.stat == Stat.NAME) {
			return "−" + level + " ступ. видимости ника";
		}
		return describe(node.stat, node.per * level);
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
	}

	public static String format(long value, int divisor, String unit) {
		long shown = divisor <= 1 ? value : value / divisor;
		return shown + unit;
	}
}
