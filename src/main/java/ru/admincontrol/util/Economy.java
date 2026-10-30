package ru.admincontrol.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

public final class Economy {
	private Economy() {
	}

	public static Item currency() {
		return Items.LAPIS_LAZULI;
	}

	public static boolean sellBlocked(ServerPlayerEntity player) {
		return tradeBlocked(player) != null;
	}

	public static String tradeBlocked(ServerPlayerEntity player) {
		if (player == null) {
			return null;
		}
		if (EventService.member(player) || EventService.isHost(player)) {
			return "/sell и /auction недоступны на мероприятии.";
		}
		if (TrainingService.active(player)) {
			return "/sell и /auction недоступны на тренировке.";
		}
		if (ClanWarService.memberOfWar(player) || ClanWarService.jailed(player)) {
			return "/sell и /auction недоступны во время битвы кланов.";
		}
		return null;
	}

	public static boolean canSell(Item item) {
		return canSell(item, null);
	}

	public static boolean canSell(Item item, java.util.Collection<String> extraBanned) {
		if (item == null || item == Items.AIR) {
			return false;
		}
		if (isBlockedByKind(item)) {
			return false;
		}
		String id = Registries.ITEM.getId(item).toString();
		if (listed(extraBanned, id)) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return true;
		}
		return !listed(data.settings().sellBanned, id);
	}

	private static boolean listed(java.util.Collection<String> banned, String id) {
		if (banned == null || id == null) {
			return false;
		}
		for (String entry : banned) {
			if (id.equalsIgnoreCase(entry)) {
				return true;
			}
		}
		return false;
	}

	public static boolean toggleSellBan(Item item) {
		DataManager data = DataManager.get();
		if (data == null || item == null || item == Items.AIR) {
			return false;
		}
		if (data.settings().sellBanned == null) {
			data.settings().sellBanned = new java.util.ArrayList<>();
		}
		String id = Registries.ITEM.getId(item).toString();
		java.util.List<String> banned = data.settings().sellBanned;
		for (int i = 0; i < banned.size(); i++) {
			if (id.equalsIgnoreCase(banned.get(i))) {
				banned.remove(i);
				data.saveAll();
				return false;
			}
		}
		banned.add(id);
		data.saveAll();
		return true;
	}

	public static void ensureDefaultSellBans() {
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		if (data.settings().sellBanned == null) {
			data.settings().sellBanned = new java.util.ArrayList<>();
		}
		java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(data.settings().sellBanned);
		boolean changed = false;
		for (String id : defaultSellBans()) {
			if (set.add(id)) {
				changed = true;
			}
		}
		if (changed) {
			data.settings().sellBanned = new java.util.ArrayList<>(set);
			data.markDirty();
		}
	}

	private static boolean isBlockedByKind(Item item) {
		Identifier id = Registries.ITEM.getId(item);
		if (id == null || !"minecraft".equals(id.getNamespace())) {
			return false;
		}
		String p = id.getPath();
		if (p.contains("command_block") || p.contains("structure") || p.equals("barrier") || p.equals("jigsaw")
				|| p.equals("light") || p.equals("debug_stick") || p.equals("knowledge_book")
				|| p.equals("spawner") || p.endsWith("_spawn_egg") || p.equals("bedrock")
				|| p.equals("end_portal_frame") || p.equals("budding_amethyst") || p.equals("farmland")
				|| p.equals("dirt_path") || p.equals("infested_stone") || p.startsWith("infested_")) {
			return true;
		}
		if (p.equals("grass_block") || p.equals("grass") || p.equals("short_grass") || p.equals("tall_grass")
				|| p.equals("fern") || p.equals("large_fern") || p.equals("dead_bush") || p.equals("seagrass")
				|| p.equals("kelp") || p.equals("vine") || p.equals("lily_pad") || p.equals("moss_carpet")
				|| p.equals("hanging_roots") || p.equals("glow_lichen") || p.equals("snow")
				|| p.equals("cobblestone") || p.equals("dirt") || p.equals("coarse_dirt") || p.equals("gravel")
				|| p.equals("sand") || p.equals("red_sand") || p.equals("clay") || p.equals("flint")) {
			return true;
		}
		if (p.endsWith("_sapling") || p.endsWith("_seeds") || p.equals("wheat_seeds") || p.equals("beetroot_seeds")
				|| p.equals("melon_seeds") || p.equals("pumpkin_seeds") || p.equals("torchflower_seeds")
				|| p.equals("pitcher_pod") || p.equals("cocoa_beans")) {
			return true;
		}
		if (p.endsWith("_log") || p.endsWith("_wood") || p.endsWith("_stem") || p.endsWith("_hyphae")
				|| p.endsWith("_leaves") || p.endsWith("_planks")) {
			return true;
		}
		return p.endsWith("_tulip") || p.equals("dandelion") || p.equals("poppy") || p.equals("blue_orchid")
				|| p.equals("allium") || p.equals("azure_bluet") || p.equals("oxeye_daisy") || p.equals("cornflower")
				|| p.equals("lily_of_the_valley") || p.equals("wither_rose") || p.equals("sunflower")
				|| p.equals("lilac") || p.equals("rose_bush") || p.equals("peony") || p.equals("pink_petals")
				|| p.equals("torchflower") || p.equals("pitcher_plant") || p.equals("spore_blossom");
	}

	private static java.util.List<String> defaultSellBans() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (isBlockedByKind(item)) {
				out.add(Registries.ITEM.getId(item).toString());
			}
		}
		return out;
	}

	public static int priceOf(Item item) {
		if (!canSell(item)) {
			return 0;
		}
		DataManager data = DataManager.get();
		if (data == null || item == null || item == Items.AIR) {
			return 1;
		}
		String id = Registries.ITEM.getId(item).toString();
		Integer custom = data.settings().itemPrices.get(id);
		if (custom != null && custom >= 0) {
			return custom;
		}
		int fallback = data.settings().defaultSellPrice;
		return Math.max(0, fallback);
	}

	public static int clampPercent(int value, int min, int max) {
		return Math.min(max, Math.max(min, value));
	}

	public static int auctionMinPercent() {
		DataManager data = DataManager.get();
		if (data == null) {
			return 100;
		}
		return clampPercent(data.settings().auctionMinPercent, 0, 1000);
	}

	public static int auctionMaxPercent() {
		DataManager data = DataManager.get();
		int min = auctionMinPercent();
		if (data == null) {
			return Math.max(min, 150);
		}
		return clampPercent(data.settings().auctionMaxPercent, min, 1000);
	}

	public static boolean setAuctionMinPercent(int percent) {
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		int value = clampPercent(percent, 0, 1000);
		if (value > auctionMaxPercent()) {
			data.settings().auctionMaxPercent = value;
		}
		data.settings().auctionMinPercent = value;
		data.saveAll();
		return true;
	}

	public static boolean setAuctionMaxPercent(int percent) {
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		int value = clampPercent(percent, 0, 1000);
		if (value < auctionMinPercent()) {
			data.settings().auctionMinPercent = value;
		}
		data.settings().auctionMaxPercent = value;
		data.saveAll();
		return true;
	}

	public static int percentOfSell(int sellPrice, int percent) {
		return Math.max(0, (int) Math.round(Math.max(0, sellPrice) * (percent / 100.0)));
	}

	public static int auctionMin(Item item) {
		return percentOfSell(priceOf(item), auctionMinPercent());
	}

	public static int auctionMax(Item item) {
		int min = auctionMin(item);
		int max = percentOfSell(priceOf(item), auctionMaxPercent());
		return Math.max(min, max);
	}

	public static int clampAuctionPrice(Item item, int price) {
		DataManager data = DataManager.get();
		if (data != null && !data.settings().auctionPriceLimit) {
			return Math.max(0, price);
		}
		int min = auctionMin(item);
		int max = auctionMax(item);
		return Math.min(max, Math.max(min, price));
	}

	public static void setPrice(Item item, int price) {
		setPrice(item, price, true);
	}

	public static void setPrice(Item item, int price, boolean save) {
		DataManager data = DataManager.get();
		if (data == null || item == null) {
			return;
		}
		data.settings().itemPrices.put(Registries.ITEM.getId(item).toString(), Math.max(0, price));
		if (save) {
			data.saveAll();
		}
	}

	public static String name() {
		return "World coin";
	}

	public static int balance(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		if (data == null || player == null) {
			return 0;
		}
		return Math.max(0, data.getOrCreate(NpcService.moneyPlayer(player)).coins);
	}

	public static int balanceOf(PlayerRecord record) {
		return record == null ? 0 : Math.max(0, record.coins);
	}

	public static int countCurrency(ServerPlayerEntity player) {
		return balance(player);
	}

	public static boolean takeCurrency(ServerPlayerEntity player, int amount) {
		return takeCurrency(player, amount, null);
	}

	public static boolean takeCurrency(ServerPlayerEntity player, int amount, String reason) {
		if (amount <= 0) {
			return true;
		}
		DataManager data = DataManager.get();
		if (data == null || player == null) {
			return false;
		}
		PlayerRecord record = data.getOrCreate(NpcService.moneyPlayer(player));
		if (record.coins < amount) {
			return false;
		}
		record.coins -= amount;
		data.markDirty();
		BalanceBoard.refresh(player);
		if (reason != null && !reason.isBlank()) {
			CoinLedger.debit(player, amount, reason);
		} else {
			CoinLedger.noteUnlogged(record, amount, false);
		}
		return true;
	}

	public static void giveCurrency(ServerPlayerEntity player, int amount) {
		giveCurrency(player, amount, null);
	}

	public static void giveCurrency(ServerPlayerEntity player, int amount, String reason) {
		if (player == null || amount == 0) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		PlayerRecord record = data.getOrCreate(NpcService.moneyPlayer(player));
		record.coins = Math.max(0, record.coins + amount);
		data.markDirty();
		BalanceBoard.refresh(player);
		if (amount > 0 && reason != null && !reason.isBlank()) {
			CoinLedger.credit(player, amount, reason);
		} else if (amount < 0 && reason != null && !reason.isBlank()) {
			CoinLedger.debit(player, -amount, reason);
		} else if (amount > 0) {
			CoinLedger.noteUnlogged(record, amount, true);
		} else if (amount < 0) {
			CoinLedger.noteUnlogged(record, -amount, false);
		}
	}

	public static void addCoins(PlayerRecord record, int amount) {
		addCoins(record, amount, null);
	}

	public static void addCoins(PlayerRecord record, int amount, String reason) {
		if (record == null || amount <= 0) {
			return;
		}
		record.coins = Math.max(0, record.coins + amount);
		DataManager data = DataManager.get();
		if (data != null) {
			data.markDirty();
		}
		if (reason != null && !reason.isBlank()) {
			CoinLedger.credit(record, amount, reason);
		} else {
			CoinLedger.noteUnlogged(record, amount, true);
		}
	}

	public static int removeCoins(PlayerRecord record, int amount) {
		return removeCoins(record, amount, null);
	}

	public static int removeCoins(PlayerRecord record, int amount, String reason) {
		if (record == null) {
			return 0;
		}
		int take = Math.min(Math.max(0, record.coins), Math.max(0, amount));
		record.coins = Math.max(0, record.coins - Math.max(0, amount));
		DataManager data = DataManager.get();
		if (data != null) {
			data.markDirty();
		}
		if (take > 0 && reason != null && !reason.isBlank()) {
			CoinLedger.debit(record, take, reason);
		} else if (take > 0) {
			CoinLedger.noteUnlogged(record, take, false);
		}
		return record.coins;
	}

	public static boolean transfer(ServerPlayerEntity from, PlayerRecord dest, int amount) {
		if (from == null || dest == null || amount <= 0) {
			return false;
		}
		if (from.getGameProfile().getName().equalsIgnoreCase(dest.nickname)) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data != null && !data.settings().playerTransfers) {
			return false;
		}
		String fromName = from.getGameProfile().getName();
		if (!takeCurrency(from, amount, "перевод игроку " + dest.nickname)) {
			return false;
		}
		ServerPlayerEntity to = from.getServer() == null
				? null
				: CommandUtil.findOnline(from.getServer(), dest.nickname).orElse(null);
		if (to != null) {
			giveCurrency(to, amount, "перевод от " + fromName);
		} else {
			addCoins(dest, amount, "перевод от " + fromName);
		}
		if (data != null) {
			data.saveIfDirty();
		}
		return true;
	}

	public static void giveCurrencyOfflineOrDrop(ServerPlayerEntity player, int amount) {
		giveCurrency(player, amount);
	}

	public static Item parseItem(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return null;
		}
		return Registries.ITEM.get(id);
	}
}
