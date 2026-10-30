package ru.admincontrol.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.TrainingKitItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrainingConfig {
	public static final int[] MINUTES = {1, 3, 5, 7, 10, 15, 20};
	public static final String[] TYPES = {"zombie", "skeleton", "spider"};
	public static final String[] TYPE_LABELS = {"Зомби", "Скелет", "Пауки"};

	private TrainingConfig() {
	}

	public static void ensure(ModSettings s) {
		if (s == null) {
			return;
		}
		if (s.trainingCoins == null) {
			s.trainingCoins = new LinkedHashMap<>();
		}
		if (s.trainingPowerTenths == null) {
			s.trainingPowerTenths = new LinkedHashMap<>();
		}
		if (s.trainingKit == null || s.trainingKit.isEmpty()) {
			s.trainingKit = defaultKit();
		} else {
			for (TrainingKitItem item : s.trainingKit) {
				if (item == null || item.id == null || !item.id.contains("iron_sword")) {
					continue;
				}
				if (item.enchants == null) {
					item.enchants = new LinkedHashMap<>();
				}
				int sharp = item.enchants.getOrDefault("sharpness", 0);
				if (sharp == 1) {
					item.enchants.put("sharpness", 2);
				}
			}
		}
		if (s.eventKits == null) {
			s.eventKits = new LinkedHashMap<>();
		}
		s.eventKits.putIfAbsent("0", defaultEventKit(0));
		s.eventKits.putIfAbsent("1", defaultEventKit(1));
		s.eventKits.putIfAbsent("2", defaultEventKit(2));
		s.eventKits.putIfAbsent("3", defaultEventKit(3));
		s.eventKits.putIfAbsent("4", defaultEventKit(4));
		s.eventKits.putIfAbsent("5", defaultEventKit(5));
		s.eventKits.putIfAbsent("6", defaultEventKit(6));
		s.eventKits.putIfAbsent("7", defaultEventKit(7));
		s.eventKits.putIfAbsent("8", defaultEventKit(8));
		s.eventKits.putIfAbsent("9", defaultEventKit(9));
		s.eventKits.putIfAbsent("10", defaultEventKit(10));
		s.eventKits.putIfAbsent("11", defaultEventKit(11));
		s.eventKits.putIfAbsent("13", defaultEventKit(13));
		s.eventKits.putIfAbsent("14", defaultEventKit(14));
		for (int i = 0; i < 12; i++) {
			if (s.eventKits.get(String.valueOf(i)) == null || s.eventKits.get(String.valueOf(i)).isEmpty()) {
				s.eventKits.put(String.valueOf(i), defaultEventKit(i));
			}
		}
		if (s.boxKits == null) {
			s.boxKits = new LinkedHashMap<>();
		}
		if (s.boxKits.isEmpty()) {
			s.boxKits.put("box_0", defaultBoxKit(0));
			s.boxKits.put("box_1", defaultBoxKit(1));
		}
		if (s.boxRadius < 16) {
			s.boxRadius = 100;
		}
		if (s.boxWarnMinutes < 1) {
			s.boxWarnMinutes = 5;
		}
		if (s.boxClearSeconds < 15) {
			s.boxClearSeconds = 90;
		}
		if (s.boxLifetimeMinutes < 1) {
			s.boxLifetimeMinutes = 10;
		}
		if (s.boxCooldownMinutes < 0) {
			s.boxCooldownMinutes = 30;
		}
		if (s.autoBoxMinutes < 10) {
			s.autoBoxMinutes = 180;
		}
		s.boxKitEditIndex = Math.max(0, Math.min(Math.max(0, s.boxKits.size() - 1), s.boxKitEditIndex));
		if (s.eventHuntKills < 1) {
			s.eventHuntKills = 15;
		}
		if (s.eventHuntSeconds < 30) {
			s.eventHuntSeconds = 180;
		}
		for (String type : TYPES) {
			int weight = weight(type);
			for (int min : MINUTES) {
				s.trainingCoins.putIfAbsent(key(type, min), Math.max(1, min * weight));
				s.trainingPowerTenths.putIfAbsent(key(type, min), Math.max(1, (min * weight + 4) / 5));
			}
		}
		if (s.clanWinWc < 0) {
			s.clanWinWc = 5000;
		}
		if ((s.colorAdmin & 0xFFFFFF) == 0) {
			s.colorAdmin = ru.admincontrol.util.NickColors.ADMIN;
		}
		if ((s.colorCurator & 0xFFFFFF) == 0) {
			s.colorCurator = ru.admincontrol.util.NickColors.CURATOR;
		}
		if ((s.colorZga & 0xFFFFFF) == 0) {
			s.colorZga = ru.admincontrol.util.NickColors.ZGA;
		}
		if ((s.colorGa & 0xFFFFFF) == 0) {
			s.colorGa = ru.admincontrol.util.NickColors.GA;
		}
		if (s.warnBuyPrice < 1) {
			s.warnBuyPrice = 100;
		}
	}

	public static String key(String type, int minutes) {
		return type + "_" + minutes;
	}

	public static int weight(String type) {
		return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
			case "skeleton" -> 3;
			case "spider" -> 4;
			default -> 2;
		};
	}

	public static String label(String type) {
		if (type != null && type.startsWith("bot:")) {
			try {
				return "Боты: " + BotMatch.NAMES[Integer.parseInt(type.substring(4))];
			} catch (RuntimeException ignored) {
				return "Боты";
			}
		}
		for (int i = 0; i < TYPES.length; i++) {
			if (TYPES[i].equals(type)) {
				return TYPE_LABELS[i];
			}
		}
		return type;
	}

	public static int coins(String type, int minutes) {
		ModSettings s = settings();
		ensure(s);
		Integer v = s.trainingCoins.get(key(type, minutes));
		return v == null ? Math.max(1, minutes * weight(type)) : Math.max(0, v);
	}

	public static int powerTenths(String type, int minutes) {
		ModSettings s = settings();
		ensure(s);
		Integer v = s.trainingPowerTenths.get(key(type, minutes));
		return v == null ? 1 : Math.max(0, v);
	}

	public static void addCoins(String type, int minutes, int delta) {
		ModSettings s = settings();
		ensure(s);
		int next = Math.max(0, Math.min(1_000_000, coins(type, minutes) + delta));
		s.trainingCoins.put(key(type, minutes), next);
		DataManager.get().saveAll();
	}

	public static void addPower(String type, int minutes, int delta) {
		ModSettings s = settings();
		ensure(s);
		int next = Math.max(0, Math.min(1000, powerTenths(type, minutes) + delta));
		s.trainingPowerTenths.put(key(type, minutes), next);
		DataManager.get().saveAll();
	}

	public static final String[] CATALOG = {
			"minecraft:shield",
			"minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots",
			"minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots",
			"minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots",
			"minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:netherite_sword",
			"minecraft:iron_axe", "minecraft:diamond_axe",
			"minecraft:iron_shovel", "minecraft:diamond_shovel",
			"minecraft:bow", "minecraft:crossbow",
			"minecraft:golden_apple", "minecraft:enchanted_golden_apple",
			"minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
			"minecraft:arrow", "minecraft:tipped_arrow", "minecraft:potion"
	};

	public static List<TrainingKitItem> kit() {
		return kitOf("training");
	}

	public static List<TrainingKitItem> kitOf(String key) {
		ModSettings s = settings();
		ensure(s);
		if ("training".equals(key) || key == null || key.isBlank()) {
			return s.trainingKit;
		}
		if (key.startsWith("box_")) {
			if (s.boxKits == null) {
				s.boxKits = new LinkedHashMap<>();
			}
			return s.boxKits.computeIfAbsent(key, k -> defaultBoxKit(parseBox(k)));
		}
		return s.eventKits.computeIfAbsent(key, k -> defaultEventKit(parseEvent(k)));
	}

	public static int boxCount() {
		ensure(settings());
		return Math.max(1, settings().boxKits.size());
	}

	public static String boxKey(int index) {
		return "box_" + Math.max(0, index);
	}

	public static List<TrainingKitItem> randomBoxKit() {
		ModSettings s = settings();
		ensure(s);
		List<String> keys = new ArrayList<>(s.boxKits.keySet());
		if (keys.isEmpty()) {
			return defaultBoxKit(0);
		}
		if (keys.size() > 1 && s.boxLastKitKey != null && !s.boxLastKitKey.isBlank()) {
			keys.remove(s.boxLastKitKey);
		}
		String key = keys.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(keys.size()));
		s.boxLastKitKey = key;
		if (DataManager.get() != null) DataManager.get().markDirty();
		return kitOf(key);
	}

	public static void addBoxKit() {
		ModSettings s = settings();
		ensure(s);
		int n = s.boxKits.size();
		s.boxKits.put(boxKey(n), defaultBoxKit(n));
		s.boxKitEditIndex = n;
		if (DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	public static void removeBoxKit() {
		ModSettings s = settings();
		ensure(s);
		if (s.boxKits.size() <= 1) {
			s.boxKits.clear();
			s.boxKits.put("box_0", defaultBoxKit(0));
			s.boxKitEditIndex = 0;
			if (DataManager.get() != null) {
				DataManager.get().saveAll();
			}
			return;
		}
		List<List<TrainingKitItem>> all = new ArrayList<>(s.boxKits.values());
		int idx = Math.max(0, Math.min(all.size() - 1, s.boxKitEditIndex));
		all.remove(idx);
		s.boxKits.clear();
		for (int i = 0; i < all.size(); i++) {
			s.boxKits.put(boxKey(i), all.get(i));
		}
		s.boxKitEditIndex = Math.max(0, Math.min(s.boxKits.size() - 1, idx));
		if (DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	private static int parseBox(String key) {
		try {
			return Integer.parseInt(key.substring(4));
		} catch (Exception ignored) {
			return 0;
		}
	}

	public static String kitTitle(String key) {
		if (key != null && key.startsWith("box_")) {
			return "Лут бокса: набор " + (parseBox(key) + 1);
		}
		if (key != null && key.startsWith("c")) {
			int choice = EventCatalog.byKey(key);
			return "Набор МП: " + (choice >= 0 ? EventCatalog.name(choice) : key);
		}
		return switch (key == null ? "" : key) {
			case "0" -> "Набор МП: бой насмерть";
			case "1" -> "Набор МП: сокровище";
			case "2" -> "Набор МП: обвал";
			case "3" -> "Набор МП: охота";
			case "4" -> "Набор МП: дуэль на динамите";
			case "5" -> "Набор МП: дуэль";
			case "6" -> "Набор МП: гонка";
			case "7" -> "Набор МП: мясорубка";
			case "8" -> "Набор МП: кольцо лавы";
			case "9" -> "Набор МП: самоцветы";
			case "10" -> "Набор МП: отскок";
			case "11" -> "Набор МП: футбол";
			case "12" -> "Набор МП: страйкбол";
			case "13" -> "Набор МП: царь горы";
			case "14" -> "Набор МП: цветной пол";
			default -> "Набор тренировки";
		};
	}

	public static void shiftItem(String key, int index, int delta) {
		List<TrainingKitItem> kit = kitOf(key);
		if (index < 0 || index >= kit.size()) {
			return;
		}
		TrainingKitItem item = kit.get(index);
		int at = 0;
		for (int i = 0; i < CATALOG.length; i++) {
			if (CATALOG[i].equalsIgnoreCase(item.id)) {
				at = i;
				break;
			}
		}
		int next = Math.floorMod(at + delta, CATALOG.length);
		item.id = CATALOG[next];
		DataManager.get().saveAll();
	}

	public static void shiftCount(String key, int index, int delta) {
		List<TrainingKitItem> kit = kitOf(key);
		if (index < 0 || index >= kit.size()) {
			return;
		}
		kit.get(index).count = Math.max(0, Math.min(64, kit.get(index).count + delta));
		DataManager.get().saveAll();
	}

	public static void shiftCount(int index, int delta) {
		shiftCount("training", index, delta);
	}

	public static void shiftEnchant(String key, int index, String enchant, int delta) {
		List<TrainingKitItem> kit = kitOf(key);
		if (index < 0 || index >= kit.size() || enchant == null) {
			return;
		}
		TrainingKitItem item = kit.get(index);
		if (item.enchants == null) {
			item.enchants = new LinkedHashMap<>();
		}
		int next = Math.max(0, Math.min(5, item.enchants.getOrDefault(enchant, 0) + delta));
		if (next <= 0) {
			item.enchants.remove(enchant);
		} else {
			item.enchants.put(enchant, next);
		}
		DataManager.get().saveAll();
	}

	public static void shiftEnchant(int index, String enchant, int delta) {
		shiftEnchant("training", index, enchant, delta);
	}

	public static void giveKit(ServerPlayerEntity player) {
		giveKit(player, kit());
	}

	public static void giveEventKit(ServerPlayerEntity player, int typeIndex) {
		giveKit(player, kitOf(String.valueOf(Math.max(0, Math.min(EventService.TYPES.length - 1, typeIndex)))));
	}

	public static void giveKit(ServerPlayerEntity player, List<TrainingKitItem> kit) {
		if (player == null || kit == null) {
			return;
		}
		player.getInventory().clear();
		for (TrainingKitItem spec : kit) {
			ItemStack stack = stackOf(spec);
			if (stack.isEmpty()) {
				continue;
			}
			place(player, spec.slot, stack);
		}
		player.getInventory().markDirty();
		player.playerScreenHandler.sendContentUpdates();
	}

	private static void place(ServerPlayerEntity player, int slot, ItemStack stack) {
		if (slot >= 0 && slot <= 8) {
			player.getInventory().setStack(slot, stack);
			return;
		}
		if (slot >= 9 && slot <= 35) {
			player.getInventory().setStack(slot, stack);
			return;
		}
		if (slot == 40) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, stack);
			return;
		}
		if (slot == 100) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, stack);
			return;
		}
		if (slot == 101) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, stack);
			return;
		}
		if (slot == 102) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, stack);
			return;
		}
		if (slot == 103) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, stack);
			return;
		}
		Item item = stack.getItem();
		if (item instanceof net.minecraft.item.ArmorItem armor) {
			player.equipStack(armor.getSlotType(), stack);
			return;
		}
		if (item == Items.SHIELD) {
			player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, stack);
			return;
		}
		if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) {
			player.dropItem(stack, false);
		}
	}

	public static final int[] SLOT_CYCLE = {
			-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 40, 100, 101, 102, 103
	};

	public static String slotLabel(int slot) {
		if (slot >= 0 && slot <= 8) {
			return "Пояс " + (slot + 1);
		}
		if (slot >= 9 && slot <= 35) {
			return "Рюкзак";
		}
		return switch (slot) {
			case 40 -> "Левая рука";
			case 100 -> "Шлем";
			case 101 -> "Нагрудник";
			case 102 -> "Поножи";
			case 103 -> "Ботинки";
			default -> "Авто";
		};
	}

	public static String enchantLabel(String id) {
		if (id == null) {
			return "нет";
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "sharpness" -> "Острота";
			case "protection" -> "Защита";
			case "unbreaking" -> "Прочность";
			case "efficiency" -> "Эффективность";
			case "power" -> "Сила";
			case "infinity" -> "Бесконечность";
			case "piercing" -> "Пронзающая";
			case "mending" -> "Починка";
			case "fortune" -> "Удача";
			case "silk_touch" -> "Шёлковое касание";
			case "knockback" -> "Отдача";
			case "fire_aspect" -> "Заговор огня";
			case "looting" -> "Добыча";
			case "feather_falling" -> "Невесомость";
			case "thorns" -> "Шипы";
			case "aqua_affinity" -> "Подводник";
			case "respiration" -> "Подводное дыхание";
			case "depth_strider" -> "Подводная ходьба";
			case "soul_speed" -> "Скорость души";
			case "sweeping" -> "Разящий клинок";
			case "loyalty" -> "Верность";
			case "riptide" -> "Тягун";
			case "channeling" -> "Громовержец";
			case "quick_charge" -> "Быстрая перезарядка";
			case "multishot" -> "Тройной выстрел";
			case "punch" -> "Откидывание";
			case "flame" -> "Воспламенение";
			case "lure" -> "Приманка";
			case "luck_of_the_sea" -> "Везучий рыбак";
			default -> id;
		};
	}

	public static final String[] ENCHANTS = {
			"sharpness", "protection", "unbreaking", "efficiency", "power", "infinity", "piercing", "mending",
			"fortune", "silk_touch", "knockback", "fire_aspect", "looting", "feather_falling", "thorns",
			"aqua_affinity", "respiration", "depth_strider", "soul_speed", "sweeping", "loyalty", "riptide",
			"channeling", "quick_charge", "multishot", "punch", "flame", "lure", "luck_of_the_sea"
	};

	public static String enchantId(int index) {
		if (index < 0) {
			return ENCHANTS[0];
		}
		return ENCHANTS[index % ENCHANTS.length];
	}

	public static int enchantIndex(String id) {
		if (id == null) {
			return 0;
		}
		for (int i = 0; i < ENCHANTS.length; i++) {
			if (ENCHANTS[i].equalsIgnoreCase(id)) {
				return i;
			}
		}
		return 0;
	}

	public static String primaryEnchant(String itemId) {
		if (itemId == null) {
			return "unbreaking";
		}
		String id = itemId.toLowerCase(Locale.ROOT);
		if (id.contains("sword") || id.contains("axe")) {
			return "sharpness";
		}
		if (id.contains("helmet") || id.contains("chest") || id.contains("legging") || id.contains("boot")) {
			return "protection";
		}
		if (id.contains("shovel") || id.contains("pickaxe") || id.contains("hoe")) {
			return "efficiency";
		}
		if (id.contains("bow") && !id.contains("cross")) {
			return "power";
		}
		if (id.contains("crossbow")) {
			return "piercing";
		}
		return "unbreaking";
	}

	public static void shiftSlot(String key, int index, int delta) {
		List<TrainingKitItem> kit = kitOf(key);
		if (index < 0 || index >= kit.size()) {
			return;
		}
		int cur = kit.get(index).slot;
		int at = 0;
		for (int i = 0; i < SLOT_CYCLE.length; i++) {
			if (SLOT_CYCLE[i] == cur) {
				at = i;
				break;
			}
		}
		kit.get(index).slot = SLOT_CYCLE[Math.floorMod(at + delta, SLOT_CYCLE.length)];
		DataManager.get().saveAll();
	}

	public static void removeAt(String key, int index) {
		List<TrainingKitItem> kit = kitOf(key);
		if (index < 0 || index >= kit.size()) {
			return;
		}
		kit.remove(index);
		DataManager.get().saveAll();
	}

	public static int storedSlot(int invIndex) {
		return switch (invIndex) {
			case 36 -> 103;
			case 37 -> 102;
			case 38 -> 101;
			case 39 -> 100;
			default -> invIndex;
		};
	}

	public static int kitInvIndex(int storedSlot) {
		if (storedSlot >= 0 && storedSlot <= 35) {
			return storedSlot;
		}
		return switch (storedSlot) {
			case 40 -> 40;
			case 100 -> 39;
			case 101 -> 38;
			case 102 -> 37;
			case 103 -> 36;
			default -> -1;
		};
	}

	public static void readKitInventory(String key, SimpleInventory inv) {
		if (inv == null) {
			return;
		}
		for (int i = 0; i < inv.size(); i++) {
			inv.setStack(i, ItemStack.EMPTY);
		}
		List<TrainingKitItem> pending = new ArrayList<>();
		for (TrainingKitItem spec : kitOf(key)) {
			ItemStack stack = stackOf(spec);
			if (stack.isEmpty()) {
				continue;
			}
			int idx = kitInvIndex(spec.slot);
			if (idx >= 0 && idx < inv.size() && inv.getStack(idx).isEmpty()) {
				inv.setStack(idx, stack);
			} else {
				pending.add(spec);
			}
		}
		for (TrainingKitItem spec : pending) {
			autoPlace(inv, stackOf(spec));
		}
	}

	public static void writeKitInventory(String key, SimpleInventory inv) {
		if (inv == null || DataManager.get() == null) {
			return;
		}
		List<TrainingKitItem> kit = kitOf(key);
		kit.clear();
		for (int i = 0; i < Math.min(41, inv.size()); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			kit.add(fromStack(stack, storedSlot(i)));
		}
		DataManager.get().saveAll();
	}

	public static TrainingKitItem fromStack(ItemStack stack, int storedSlot) {
		TrainingKitItem item = new TrainingKitItem();
		Identifier id = Registries.ITEM.getId(stack.getItem());
		item.id = id == null ? "minecraft:air" : id.toString();
		item.count = Math.max(1, Math.min(64, stack.getCount()));
		item.slot = storedSlot;
		item.enchants = new LinkedHashMap<>();
		for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.get(stack).entrySet()) {
			if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
				continue;
			}
			Identifier eid = Registries.ENCHANTMENT.getId(e.getKey());
			if (eid != null) {
				item.enchants.put(eid.getPath(), e.getValue());
			}
		}
		if (stack.hasNbt()) {
			net.minecraft.nbt.NbtCompound tag = stack.getNbt().copy();
			tag.remove("Enchantments");
			item.nbt = tag.isEmpty() ? "" : tag.asString();
		}
		return item;
	}

	public static void applyEnchant(ItemStack stack, String enchantId, int delta) {
		Enchantment enc = enchant(enchantId);
		if (stack == null || stack.isEmpty() || enc == null) {
			return;
		}
		Map<Enchantment, Integer> map = new LinkedHashMap<>(EnchantmentHelper.get(stack));
		int cap = Math.max(5, enc.getMaxLevel());
		int next = Math.max(0, Math.min(cap, map.getOrDefault(enc, 0) + delta));
		if (next <= 0) {
			map.remove(enc);
		} else {
			map.put(enc, next);
		}
		EnchantmentHelper.set(map, stack);
	}

	private static void autoPlace(SimpleInventory inv, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		Item item = stack.getItem();
		if (item instanceof ArmorItem armor) {
			int idx = armorIndex(armor.getSlotType());
			if (idx >= 0 && idx < inv.size() && inv.getStack(idx).isEmpty()) {
				inv.setStack(idx, stack);
				return;
			}
		}
		if (item == Items.SHIELD && inv.size() > 40 && inv.getStack(40).isEmpty()) {
			inv.setStack(40, stack);
			return;
		}
		for (int i = 0; i < Math.min(36, inv.size()); i++) {
			if (inv.getStack(i).isEmpty()) {
				inv.setStack(i, stack);
				return;
			}
		}
		if (inv.size() > 40 && inv.getStack(40).isEmpty()) {
			inv.setStack(40, stack);
		}
	}

	private static int armorIndex(EquipmentSlot slot) {
		if (slot == EquipmentSlot.HEAD) {
			return 39;
		}
		if (slot == EquipmentSlot.CHEST) {
			return 38;
		}
		if (slot == EquipmentSlot.LEGS) {
			return 37;
		}
		if (slot == EquipmentSlot.FEET) {
			return 36;
		}
		return -1;
	}

	public static void putStack(String key, int index, ItemStack stack) {
		if (stack == null || stack.isEmpty() || DataManager.get() == null) {
			return;
		}
		List<TrainingKitItem> kit = kitOf(key);
		TrainingKitItem item = new TrainingKitItem();
		Identifier id = Registries.ITEM.getId(stack.getItem());
		item.id = id == null ? "minecraft:air" : id.toString();
		item.count = Math.max(1, Math.min(64, stack.getCount()));
		item.slot = index >= 0 && index <= 8 ? index : -1;
		item.enchants = new LinkedHashMap<>();
		if (index < 0 || index >= kit.size()) {
			if (kit.size() >= 27) {
				return;
			}
			kit.add(item);
		} else {
			TrainingKitItem old = kit.get(index);
			item.slot = old.slot;
			kit.set(index, item);
		}
		DataManager.get().saveAll();
	}

	public static ItemStack stackOf(TrainingKitItem spec) {
		if (spec == null || spec.count <= 0 || spec.id == null) {
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(spec.id);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return ItemStack.EMPTY;
		}
		Item item = Registries.ITEM.get(id);
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item, Math.min(item.getMaxCount(), spec.count));
		if (spec.nbt != null && !spec.nbt.isBlank()) {
			try {
				net.minecraft.nbt.NbtCompound tag = net.minecraft.nbt.StringNbtReader.parse(spec.nbt);
				tag.remove("Enchantments");
				if (!tag.isEmpty()) {
					stack.setNbt(tag);
				}
			} catch (Exception ignored) {
			}
		}
		if (spec.enchants != null) {
			for (Map.Entry<String, Integer> e : spec.enchants.entrySet()) {
				if (e.getValue() == null || e.getValue() <= 0) {
					continue;
				}
				Enchantment enc = enchant(e.getKey());
				if (enc != null) {
					stack.addEnchantment(enc, Math.min(enc.getMaxLevel(), e.getValue()));
				}
			}
		}
		return stack;
	}

	/**
	 * Предмет с таким же ID и чарами явно разрешён одним из серверных наборов.
	 * Это нужно античиту: нельзя наказывать игрока за палку Knockback + Unbreaking,
	 * которую сам сервер выдал на МП.
	 */
	public static boolean allowedKitEnchantments(ItemStack stack) {
		if (stack == null || stack.isEmpty() || DataManager.get() == null) return false;
		Identifier id = Registries.ITEM.getId(stack.getItem());
		if (id == null) return false;
		Map<Enchantment, Integer> actual = EnchantmentHelper.get(stack);
		for (List<TrainingKitItem> kit : allConfiguredKits()) {
			if (kit == null) continue;
			for (TrainingKitItem spec : kit) {
				if (spec == null || !id.toString().equals(spec.id)) continue;
				ItemStack configured = stackOf(spec);
				if (!configured.isEmpty() && EnchantmentHelper.get(configured).equals(actual)) return true;
			}
		}
		return false;
	}

	private static List<List<TrainingKitItem>> allConfiguredKits() {
		ModSettings s = DataManager.get().settings();
		List<List<TrainingKitItem>> out = new ArrayList<>();
		out.add(s.trainingKit);
		if (s.eventKits != null) out.addAll(s.eventKits.values());
		if (s.boxKits != null) out.addAll(s.boxKits.values());
		return out;
	}

	public static Enchantment enchant(String name) {
		if (name == null) {
			return null;
		}
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "sharpness", "minecraft:sharpness" -> Enchantments.SHARPNESS;
			case "protection", "minecraft:protection" -> Enchantments.PROTECTION;
			case "unbreaking", "minecraft:unbreaking" -> Enchantments.UNBREAKING;
			case "efficiency", "minecraft:efficiency" -> Enchantments.EFFICIENCY;
			case "power", "minecraft:power" -> Enchantments.POWER;
			case "infinity", "minecraft:infinity" -> Enchantments.INFINITY;
			case "piercing", "minecraft:piercing" -> Enchantments.PIERCING;
			case "mending", "minecraft:mending" -> Enchantments.MENDING;
			default -> {
				Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
				yield id == null ? null : Registries.ENCHANTMENT.get(id);
			}
		};
	}

	private static int parseEvent(String key) {
		try {
			return Integer.parseInt(key);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static List<TrainingKitItem> defaultEventKit(int type) {
		List<TrainingKitItem> kit = new ArrayList<>();
		if (type == 12) {
			return kit;
		}
		if (type == 13) {
			kit.add(item("minecraft:shield", 1, Map.of()));
			kit.add(item("minecraft:iron_helmet", 1, Map.of("protection", 2, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("protection", 2, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("protection", 2, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("protection", 2, "feather_falling", 2, "unbreaking", 3)));
			kit.add(item("minecraft:iron_sword", 1, Map.of("sharpness", 2, "unbreaking", 3)));
			kit.add(item("minecraft:stick", 1, Map.of("knockback", 2, "unbreaking", 3)));
			kit.add(item("minecraft:golden_apple", 2, Map.of()));
			kit.add(item("minecraft:cooked_beef", 16, Map.of()));
			return kit;
		}
		if (type == 14) {
			kit.add(item("minecraft:stick", 1, Map.of("knockback", 1, "unbreaking", 3)));
			return kit;
		}
		if (type == 6) {
			kit.add(item("minecraft:oak_boat", 1, Map.of()));
			return kit;
		}
		if (type == 7) {
			kit.add(item("minecraft:iron_sword", 1, Map.of("sharpness", 3, "unbreaking", 2)));
			kit.add(item("minecraft:shield", 1, Map.of()));
			kit.add(item("minecraft:cooked_beef", 16, Map.of()));
			return kit;
		}
		if (type == 8) {
			return defaultEventKit(0);
		}
		if (type == 9) {
			kit.add(item("minecraft:diamond_pickaxe", 1, Map.of("efficiency", 5, "unbreaking", 3)));
			kit.add(item("minecraft:iron_sword", 1, Map.of("sharpness", 3, "unbreaking", 2)));
			kit.add(item("minecraft:cooked_beef", 16, Map.of()));
			return kit;
		}
		if (type == 11) {
			kit.add(item("minecraft:leather_helmet", 1, Map.of()));
			kit.add(item("minecraft:leather_chestplate", 1, Map.of()));
			kit.add(item("minecraft:leather_leggings", 1, Map.of()));
			kit.add(item("minecraft:leather_boots", 1, Map.of()));
			kit.add(item("minecraft:stick", 1, Map.of("knockback", 2, "unbreaking", 3)));
			kit.add(item("minecraft:cooked_beef", 8, Map.of()));
			return kit;
		}
		if (type == 2) {
			kit.add(item("minecraft:diamond_shovel", 1, Map.of("efficiency", 5, "unbreaking", 3)));
			kit.add(item("minecraft:cooked_beef", 16, Map.of()));
			return kit;
		}
		if (type == 3) {
			return defaultHuntKit();
		}
		if (type == 4) {
			kit.add(item("minecraft:bow", 1, Map.of("infinity", 1, "flame", 1, "unbreaking", 3)));
			kit.add(item("minecraft:arrow", 1, Map.of()));
			return kit;
		}
		if (type == 5) {
			return defaultEventKit(0);
		}
		kit.add(item("minecraft:shield", 1, Map.of()));
		kit.add(item("minecraft:diamond_helmet", 1, Map.of("protection", 4, "unbreaking", 3, "mending", 1)));
		kit.add(item("minecraft:diamond_chestplate", 1, Map.of("protection", 4, "unbreaking", 3, "mending", 1)));
		kit.add(item("minecraft:diamond_leggings", 1, Map.of("protection", 4, "unbreaking", 3, "mending", 1)));
		kit.add(item("minecraft:diamond_boots", 1, Map.of("protection", 4, "unbreaking", 3, "mending", 1)));
		kit.add(item("minecraft:diamond_sword", 1, Map.of("sharpness", 5, "unbreaking", 3, "mending", 1)));
		kit.add(item("minecraft:bow", 1, Map.of("power", 5, "infinity", 1)));
		kit.add(item("minecraft:crossbow", 1, Map.of("piercing", 4)));
		kit.add(item("minecraft:tipped_arrow", 64, Map.of()));
		kit.add(item("minecraft:cooked_beef", 64, Map.of()));
		kit.add(item("minecraft:potion", 2, Map.of()));
		return kit;
	}

	public static void refreshHuntDefaultKit() {
		ModSettings s = settings();
		ensure(s);
		s.eventKits.put("3", defaultHuntKit());
		if (DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	private static List<TrainingKitItem> defaultHuntKit() {
		List<TrainingKitItem> kit = new ArrayList<>();
		int mob = EventService.huntMob();
		kit.add(item("minecraft:shield", 1, Map.of()));
		if (mob == 1) {
			kit.add(item("minecraft:iron_helmet", 1, Map.of("projectile_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("projectile_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("projectile_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("projectile_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_axe", 1, Map.of("sharpness", 4, "unbreaking", 3)));
		} else if (mob == 2) {
			kit.add(item("minecraft:iron_helmet", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_sword", 1, Map.of("bane_of_arthropods", 5, "unbreaking", 3)));
		} else if (mob == 3) {
			kit.add(item("minecraft:iron_helmet", 1, Map.of("blast_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("blast_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("blast_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("blast_protection", 4, "unbreaking", 3)));
			kit.add(item("minecraft:iron_axe", 1, Map.of("sharpness", 4, "unbreaking", 3)));
		} else if (mob == 4) {
			kit.add(item("minecraft:iron_helmet", 1, Map.of("protection", 3, "respiration", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_sword", 1, Map.of("sharpness", 4, "unbreaking", 3)));
		} else {
			kit.add(item("minecraft:iron_helmet", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_chestplate", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_leggings", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_boots", 1, Map.of("protection", 3, "unbreaking", 3)));
			kit.add(item("minecraft:iron_sword", 1, Map.of("smite", 5, "unbreaking", 3)));
		}
		kit.add(item("minecraft:cooked_beef", 16, Map.of()));
		return kit;
	}

	private static List<TrainingKitItem> defaultBoxKit(int variant) {
		List<TrainingKitItem> kit = new ArrayList<>();
		if (variant % 3 == 1) {
			kit.add(item("minecraft:diamond", 8, Map.of()));
			kit.add(item("minecraft:golden_apple", 4, Map.of()));
			kit.add(item("minecraft:ender_pearl", 8, Map.of()));
			kit.add(item("minecraft:cooked_beef", 32, Map.of()));
			return kit;
		}
		if (variant % 3 == 2) {
			kit.add(item("minecraft:netherite_ingot", 1, Map.of()));
			kit.add(item("minecraft:diamond", 16, Map.of()));
			kit.add(item("minecraft:enchanted_golden_apple", 1, Map.of()));
			kit.add(item("minecraft:arrow", 64, Map.of()));
			return kit;
		}
		kit.add(item("minecraft:iron_ingot", 32, Map.of()));
		kit.add(item("minecraft:gold_ingot", 16, Map.of()));
		kit.add(item("minecraft:diamond", 4, Map.of()));
		kit.add(item("minecraft:golden_apple", 2, Map.of()));
		kit.add(item("minecraft:cooked_beef", 16, Map.of()));
		return kit;
	}

	private static List<TrainingKitItem> defaultKit() {
		List<TrainingKitItem> kit = new ArrayList<>();
		kit.add(item("minecraft:shield", 1, Map.of()));
		kit.add(item("minecraft:iron_helmet", 1, Map.of()));
		kit.add(item("minecraft:iron_chestplate", 1, Map.of()));
		kit.add(item("minecraft:iron_leggings", 1, Map.of()));
		kit.add(item("minecraft:iron_boots", 1, Map.of()));
		kit.add(item("minecraft:iron_sword", 1, Map.of("sharpness", 2)));
		kit.add(item("minecraft:golden_apple", 32, Map.of()));
		kit.add(item("minecraft:bread", 64, Map.of()));
		kit.add(item("minecraft:cooked_beef", 64, Map.of()));
		kit.add(item("minecraft:iron_axe", 1, Map.of()));
		return kit;
	}

	private static TrainingKitItem item(String id, int count, Map<String, Integer> enchants) {
		TrainingKitItem item = new TrainingKitItem();
		item.id = id;
		item.count = count;
		item.enchants = new LinkedHashMap<>(enchants);
		return item;
	}

	private static ModSettings settings() {
		DataManager data = DataManager.get();
		return data == null ? new ModSettings() : data.settings();
	}
}
