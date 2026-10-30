package ru.admincontrol.emi;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.TrainingKitItem;
import ru.admincontrol.util.TrainingConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Способы получения предмета без рецепта: добыча из блока (с указанием минимального инструмента), выпадение из существа,
 * находка в сундуках, рыбалка, торговля, игровые механики, бокс админов. Данные получаются пробными розыгрышами реальных
 * таблиц добычи сервера, поэтому работают и для предметов других модов. Если способа нет — запись «creative» (есть во
 * вкладке творческого режима), «creative_op» (только операторская вкладка) или «command» (только команда /give).
 */
public final class EmiSources {
	private static final int MAX_PER_ITEM = 24;
	public static final int TOOL_NORMAL = 1, TOOL_SILK = 2, TOOL_SHEARS = 4;

	/** Инструменты от слабого к сильному: первый подходящий для блока и есть «минимальный инструмент». */
	private static final Item[] TOOL_LADDER = {
			Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
			Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
			Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
			Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE,
			Items.WOODEN_SWORD, Items.SHEARS};

	private EmiSources() {
	}

	private static void add(Map<Item, Map<String, EmiRecipe>> map, Item item, String note, String detail, ItemStack icon, int bit, ItemStack tool) {
		if (item == Items.AIR) {
			return;
		}
		Map<String, EmiRecipe> list = map.computeIfAbsent(item, k -> new LinkedHashMap<>());
		String key = note + "|" + detail;
		EmiRecipe existing = list.get(key);
		if (existing != null) {
			existing.time |= bit;
			return;
		}
		if (list.size() >= MAX_PER_ITEM) {
			return;
		}
		EmiRecipe r = new EmiRecipe();
		r.kind = EmiRecipe.SOURCE;
		r.note = note;
		r.typeId = detail.length() > 95 ? detail.substring(0, 95) : detail;
		r.time = bit;
		r.station = tool == null ? ItemStack.EMPTY : tool;
		r.inputs.add(icon.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(icon.copy())));
		r.outputs.add(new ItemStack(item));
		list.put(key, r);
	}

	private static void add(Map<Item, Map<String, EmiRecipe>> map, Item item, String note, String detail, ItemStack icon, int bit) {
		add(map, item, note, detail, icon, bit, ItemStack.EMPTY);
	}

	/** Самый слабый инструмент, которым блок можно добыть с выпадением (учитывает уровень и тип инструмента, в т.ч. модовые блоки). */
	private static ItemStack cheapestTool(BlockState state) {
		for (Item t : TOOL_LADDER) {
			ItemStack s = new ItemStack(t);
			if (s.isSuitableFor(state)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static LootContextParameterSet blockCtx(ServerWorld w, Vec3d origin, BlockState state, ItemStack tool) {
		return new LootContextParameterSet.Builder(w)
				.add(LootContextParameters.ORIGIN, origin)
				.add(LootContextParameters.BLOCK_STATE, state)
				.add(LootContextParameters.TOOL, tool)
				.build(LootContextTypes.BLOCK);
	}

	private static String id(Block block) {
		return Registries.BLOCK.getId(block).toString();
	}

	public static List<EmiRecipe> compute(MinecraftServer server, List<EmiRecipe> recipes) {
		Map<Item, Map<String, EmiRecipe>> map = new LinkedHashMap<>();
		ServerWorld w = server.getOverworld();
		Vec3d origin = Vec3d.ofCenter(w.getSpawnPos());
		long t0 = System.currentTimeMillis();
		blocks(server, w, origin, map);
		AdminControlMod.LOGGER.info("[CW EMI] блоки: {} мс", System.currentTimeMillis() - t0);
		entities(server, w, origin, map);
		AdminControlMod.LOGGER.info("[CW EMI] мобы: {} мс", System.currentTimeMillis() - t0);
		chestsAndFishing(server, w, origin, map);
		AdminControlMod.LOGGER.info("[CW EMI] сундуки: {} мс", System.currentTimeMillis() - t0);
		gameplay(server, w, origin, map);
		trades(w, map);
		AdminControlMod.LOGGER.info("[CW EMI] торговля: {} мс", System.currentTimeMillis() - t0);
		for (EmiRecipe r : EmiMechanics.curated()) {
			add(map, r.outputs.get(0).getItem(), r.note, r.typeId, r.inputs.get(0).isEmpty() ? ItemStack.EMPTY : r.inputs.get(0).get(0), TOOL_NORMAL);
		}
		Set<Item> covered = new HashSet<>(map.keySet());
		for (EmiRecipe r : recipes) {
			if (r.kind == EmiRecipe.ANVIL) {
				continue; // ремонт не создаёт предмет
			}
			for (ItemStack o : r.outputs) {
				covered.add(o.getItem());
			}
		}
		for (Item item : boxItems()) {
			covered.add(item);
		}
		Map<Item, String> tabOf = new HashMap<>();
		Set<Item> opOnly = new HashSet<>();
		boolean haveTabs = creativeTabs(server, w, tabOf, opOnly);
		for (Item item : Registries.ITEM) {
			if (item == Items.AIR || covered.contains(item)) {
				continue;
			}
			String itemId = Registries.ITEM.getId(item).toString();
			if (!haveTabs) {
				add(map, item, "creative", itemId, ItemStack.EMPTY, TOOL_NORMAL);
			} else if (tabOf.containsKey(item)) {
				add(map, item, "creative", itemId + "|" + tabOf.get(item), ItemStack.EMPTY, TOOL_NORMAL);
			} else if (opOnly.contains(item)) {
				add(map, item, "creative_op", itemId, ItemStack.EMPTY, TOOL_NORMAL);
			} else {
				add(map, item, "command", itemId, ItemStack.EMPTY, TOOL_NORMAL);
			}
		}
		List<EmiRecipe> out = new ArrayList<>();
		for (Map<String, EmiRecipe> list : map.values()) {
			for (EmiRecipe r : list.values()) {
				if (r.note.equals("block") && (r.time & TOOL_NORMAL) == 0) {
					r.note = (r.time & TOOL_SILK) != 0 ? ((r.time & TOOL_SHEARS) != 0 ? "block_silk_shears" : "block_silk") : "block_shears";
				}
				out.add(r);
			}
		}
		return out;
	}

	private static void blocks(MinecraftServer server, ServerWorld w, Vec3d origin, Map<Item, Map<String, EmiRecipe>> map) {
		ItemStack shears = new ItemStack(Items.SHEARS);
		for (Block block : Registries.BLOCK) {
			try {
				Identifier lootId = block.getLootTableId();
				if (lootId == null || lootId.equals(LootTables.EMPTY)) {
					continue;
				}
				BlockState state = block.getDefaultState();
				if (state.getHardness(w, BlockPos.ORIGIN) < 0) {
					continue; // бедрок, барьер и т.п.: добыть нельзя
				}
				LootTable table = server.getLootManager().getLootTable(lootId);
				ItemStack icon = new ItemStack(block.asItem());
				boolean required = state.isToolRequired();
				ItemStack normalTool = required ? cheapestTool(state) : ItemStack.EMPTY;
				String detail = id(block) + (required && !normalTool.isEmpty() ? "|" + Registries.ITEM.getId(normalTool.getItem()) : "");
				if (!required || !normalTool.isEmpty()) {
					for (int i = 0; i < 8; i++) {
						for (ItemStack drop : table.generateLoot(blockCtx(w, origin, state, normalTool))) {
							add(map, drop.getItem(), "block", detail, icon, TOOL_NORMAL, normalTool);
						}
					}
				}
				ItemStack silk = required ? normalTool.copy() : new ItemStack(Items.NETHERITE_PICKAXE);
				if (!silk.isEmpty()) {
					silk.addEnchantment(Enchantments.SILK_TOUCH, 1);
					for (int i = 0; i < 4; i++) {
						for (ItemStack drop : table.generateLoot(blockCtx(w, origin, state, silk))) {
							add(map, drop.getItem(), "block", detail, icon, TOOL_SILK, normalTool);
						}
					}
				}
				if (!required || shears.isSuitableFor(state)) {
					for (int i = 0; i < 4; i++) {
						for (ItemStack drop : table.generateLoot(blockCtx(w, origin, state, shears))) {
							add(map, drop.getItem(), "block", detail, icon, TOOL_SHEARS, ItemStack.EMPTY);
						}
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static void entities(MinecraftServer server, ServerWorld w, Vec3d origin, Map<Item, Map<String, EmiRecipe>> map) {
		ServerPlayerEntity killer = null;
		try {
			killer = FakePlayer.get(w);
		} catch (RuntimeException | LinkageError e) {
			AdminControlMod.LOGGER.warn("[CW EMI] не удалось создать игрока для розыгрыша добычи с мобов: {}", e.toString());
		}
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			try {
				if (!type.isSummonable()) {
					continue;
				}
				Entity e = type.create(w);
				if (!(e instanceof LivingEntity)) {
					continue;
				}
				LootTable table = server.getLootManager().getLootTable(type.getLootTableId());
				SpawnEggItem egg = SpawnEggItem.forEntity(type);
				ItemStack icon = egg == null ? ItemStack.EMPTY : new ItemStack(egg);
				for (int i = 0; i < 120; i++) {
					LootContextParameterSet.Builder b = new LootContextParameterSet.Builder(w)
							.add(LootContextParameters.THIS_ENTITY, e)
							.add(LootContextParameters.ORIGIN, origin)
							.add(LootContextParameters.DAMAGE_SOURCE, killer == null ? w.getDamageSources().generic() : w.getDamageSources().playerAttack(killer));
					if (killer != null) {
						b.addOptional(LootContextParameters.KILLER_ENTITY, killer)
								.addOptional(LootContextParameters.DIRECT_KILLER_ENTITY, killer)
								.addOptional(LootContextParameters.LAST_DAMAGE_PLAYER, killer);
					}
					for (ItemStack drop : table.generateLoot(b.build(LootContextTypes.ENTITY))) {
						add(map, drop.getItem(), "entity", Registries.ENTITY_TYPE.getId(type).toString(), icon, TOOL_NORMAL);
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static void chestsAndFishing(MinecraftServer server, ServerWorld w, Vec3d origin, Map<Item, Map<String, EmiRecipe>> map) {
		for (Identifier id : server.getLootManager().getIds(LootDataType.LOOT_TABLES)) {
			if (!id.getPath().startsWith("chests/")) {
				continue;
			}
			try {
				LootTable table = server.getLootManager().getLootTable(id);
				for (int i = 0; i < 20; i++) {
					LootContextParameterSet.Builder b = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, origin);
					for (ItemStack drop : table.generateLoot(b.build(LootContextTypes.CHEST))) {
						add(map, drop.getItem(), "chest", id.toString(), new ItemStack(Items.CHEST), TOOL_NORMAL);
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
		try {
			LootTable fishing = server.getLootManager().getLootTable(LootTables.FISHING_GAMEPLAY);
			for (int i = 0; i < 200; i++) {
				LootContextParameterSet.Builder b = new LootContextParameterSet.Builder(w)
						.add(LootContextParameters.ORIGIN, origin)
						.add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD));
				for (ItemStack drop : fishing.generateLoot(b.build(LootContextTypes.FISHING))) {
					add(map, drop.getItem(), "fishing", "", new ItemStack(Items.FISHING_ROD), TOOL_NORMAL);
				}
			}
		} catch (RuntimeException ignored) {
		}
	}

	/** Игровые механики: обмен с пиглинами, подарки жителей/кошек, раскопки сниффера, археология. */
	private static void gameplay(MinecraftServer server, ServerWorld w, Vec3d origin, Map<Item, Map<String, EmiRecipe>> map) {
		for (Identifier id : server.getLootManager().getIds(LootDataType.LOOT_TABLES)) {
			String path = id.getPath();
			net.minecraft.loot.context.LootContextType type;
			EntityType<?> et = null;
			Item icon;
			if (path.startsWith("archaeology/")) {
				type = LootContextTypes.ARCHAEOLOGY;
				icon = Items.BRUSH;
			} else if (path.equals("gameplay/piglin_bartering")) {
				type = LootContextTypes.BARTER;
				et = EntityType.PIGLIN;
				icon = Items.GOLD_INGOT;
			} else if (path.startsWith("gameplay/hero_of_the_village/")) {
				type = LootContextTypes.GIFT;
				et = EntityType.VILLAGER;
				icon = Items.EMERALD;
			} else if (path.startsWith("gameplay/cat_morning_gift/")) {
				type = LootContextTypes.GIFT;
				et = EntityType.CAT;
				icon = Items.COD;
			} else if (path.equals("gameplay/sniffer_digging")) {
				type = LootContextTypes.GIFT;
				et = EntityType.SNIFFER;
				icon = Items.SNIFFER_EGG;
			} else {
				continue;
			}
			try {
				LootTable table = server.getLootManager().getLootTable(id);
				Entity e = et == null ? null : et.create(w);
				for (int i = 0; i < 60; i++) {
					LootContextParameterSet.Builder b = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, origin);
					if (e != null) {
						b.add(LootContextParameters.THIS_ENTITY, e);
					}
					for (ItemStack drop : table.generateLoot(b.build(type))) {
						add(map, drop.getItem(), "gameplay", path, new ItemStack(icon), TOOL_NORMAL);
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	/** Торговля: предлагаемый товар каждой профессии (с уровнем) и странствующего торговца, включая профессии модов. */
	private static void trades(ServerWorld w, Map<Item, Map<String, EmiRecipe>> map) {
		try {
			VillagerEntity trader = EntityType.VILLAGER.create(w);
			if (trader == null) {
				return;
			}
			net.minecraft.util.math.random.Random rnd = net.minecraft.util.math.random.Random.create(7);
			for (Map.Entry<VillagerProfession, it.unimi.dsi.fastutil.ints.Int2ObjectMap<TradeOffers.Factory[]>> e : TradeOffers.PROFESSION_TO_LEVELED_TRADE.entrySet()) {
				Identifier prof = Registries.VILLAGER_PROFESSION.getId(e.getKey());
				for (var lvl : e.getValue().int2ObjectEntrySet()) {
					for (TradeOffers.Factory f : lvl.getValue()) {
						sampleTrade(map, f, trader, rnd, prof + "|" + lvl.getIntKey());
					}
				}
			}
			for (var lvl : TradeOffers.WANDERING_TRADER_TRADES.int2ObjectEntrySet()) {
				for (TradeOffers.Factory f : lvl.getValue()) {
					sampleTrade(map, f, trader, rnd, "minecraft:wandering_trader|" + lvl.getIntKey());
				}
			}
		} catch (RuntimeException e) {
			AdminControlMod.LOGGER.warn("[CW EMI] торговля не рассчитана: {}", e.toString());
		}
	}

	private static void sampleTrade(Map<Item, Map<String, EmiRecipe>> map, TradeOffers.Factory f, VillagerEntity trader,
									net.minecraft.util.math.random.Random rnd, String detail) {
		for (int i = 0; i < 3; i++) {
			try {
				TradeOffer offer = f.create(trader, rnd);
				if (offer != null && !offer.getSellItem().isEmpty()) {
					add(map, offer.getSellItem().getItem(), "trade", detail, new ItemStack(Items.EMERALD), TOOL_NORMAL);
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	/** Предметы, которые лежат в настроенных наборах бокса (команда админов). */
	private static List<Item> boxItems() {
		List<Item> out = new ArrayList<>();
		DataManager data = DataManager.get();
		if (data == null) {
			return out;
		}
		try {
			TrainingConfig.boxCount();
			for (List<TrainingKitItem> kit : data.settings().boxKits.values()) {
				for (TrainingKitItem spec : kit) {
					Identifier ident = spec == null ? null : Identifier.tryParse(spec.id);
					if (ident != null && Registries.ITEM.containsId(ident)) {
						out.add(Registries.ITEM.get(ident));
					}
				}
			}
		} catch (RuntimeException ignored) {
		}
		return out;
	}

	/**
	 * Бокс админ-контроля: при запуске события в сундук кладётся ОДИН случайный набор из настроенных (не повторяя прошлый).
	 * Шанс предмета ≈ доля наборов, где он лежит. Считается на лету, поэтому правки наборов видны сразу, без пересборки кэша.
	 */
	public static List<EmiRecipe> boxFor(Item item) {
		List<EmiRecipe> out = new ArrayList<>();
		DataManager data = DataManager.get();
		if (data == null) {
			return out;
		}
		try {
			TrainingConfig.boxCount();
			Map<String, List<TrainingKitItem>> kits = data.settings().boxKits;
			String id = Registries.ITEM.getId(item).toString();
			int has = 0, total = kits.size(), maxCount = 0;
			for (List<TrainingKitItem> kit : kits.values()) {
				boolean in = false;
				for (TrainingKitItem spec : kit) {
					if (spec != null && id.equals(spec.id)) {
						in = true;
						maxCount = Math.max(maxCount, spec.count);
					}
				}
				if (in) {
					has++;
				}
			}
			if (has > 0 && total > 0) {
				EmiRecipe r = new EmiRecipe();
				r.kind = EmiRecipe.SOURCE;
				r.note = "box";
				r.typeId = has + "/" + total + "/" + maxCount;
				r.xp = 100f * has / total;
				r.inputs.add(new ArrayList<>(List.of(new ItemStack(Items.CHEST))));
				r.outputs.add(new ItemStack(item));
				out.add(r);
			}
		} catch (RuntimeException ignored) {
		}
		return out;
	}

	/**
	 * Вкладки творческого инвентаря. Только на выделенном сервере (на встроенном состояние ItemGroups принадлежит клиенту).
	 * tabOf — первая обычная вкладка с предметом (ключ перевода), opOnly — предмет есть только во вкладке операторских предметов.
	 */
	private static boolean creativeTabs(MinecraftServer server, ServerWorld w, Map<Item, String> tabOf, Set<Item> opOnly) {
		if (!server.isDedicated()) {
			return false;
		}
		try {
			ItemGroups.updateDisplayContext(w.getEnabledFeatures(), true, w.getRegistryManager());
			ItemGroup operator = Registries.ITEM_GROUP.get(ItemGroups.OPERATOR);
			Set<Item> inOp = new HashSet<>();
			for (ItemGroup g : ItemGroups.getGroups()) {
				if (g.getType() != ItemGroup.Type.CATEGORY) {
					continue;
				}
				String key = g.getDisplayName().getContent() instanceof TranslatableTextContent t ? t.getKey() : g.getDisplayName().getString();
				for (ItemStack s : g.getSearchTabStacks()) {
					if (g == operator) {
						inOp.add(s.getItem());
					} else {
						tabOf.putIfAbsent(s.getItem(), key);
					}
				}
			}
			for (Item item : inOp) {
				if (!tabOf.containsKey(item)) {
					opOnly.add(item);
				}
			}
			return true;
		} catch (RuntimeException | LinkageError e) {
			AdminControlMod.LOGGER.warn("[CW EMI] вкладки творческого режима не определены: {}", e.toString());
			return false;
		}
	}
}
