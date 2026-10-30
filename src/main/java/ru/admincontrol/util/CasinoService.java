package ru.admincontrol.util;

import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class CasinoService {
	private static final Random RANDOM = new Random();

	private CasinoService() {
	}

	public static int minTenths() {
		return clampRange(settings().casinoMinTenths);
	}

	public static int maxTenths() {
		int min = minTenths();
		int max = clampRange(settings().casinoMaxTenths);
		return Math.max(min, max);
	}

	public static boolean enabled() {
		return settings().casinoEnabled;
	}

	public static void setEnabled(boolean on) {
		settings().casinoEnabled = on;
		DataManager.get().saveAll();
	}

	public static void shiftMin(int deltaTenths) {
		int next = clampRange(minTenths() + deltaTenths);
		settings().casinoMinTenths = next;
		if (maxTenths() < next) {
			settings().casinoMaxTenths = next;
		}
		DataManager.get().saveAll();
	}

	public static void shiftMax(int deltaTenths) {
		int next = clampRange(maxTenths() + deltaTenths);
		settings().casinoMaxTenths = next;
		if (minTenths() > next) {
			settings().casinoMinTenths = next;
		}
		DataManager.get().saveAll();
	}

	public static int chance() {
		Integer value = settings().casinoChance;
		return clampChance(value == null ? 15 : value);
	}

	public static void shiftChance(int delta) {
		settings().casinoChance = clampChance(chance() + delta);
		DataManager.get().saveAll();
	}

	public static String formatChance() {
		return chance() + "%";
	}

	public static String formatMult(int tenths) {
		return String.format(Locale.US, "%.1f", tenths / 10.0);
	}

	public static final class Spin {
		public boolean ok;
		public int tenths = -1;
		public int payout;
		public int net;
		public double luck;
	}

	public static final int FLOOR_BET = 0;

	public static int minBet() {
		int stored = settings().casinoMinBet;
		return Math.max(0, stored);
	}

	public static void shiftMinBet(int delta) {
		long next = (long) minBet() + delta;
		if (next < 0L) {
			next = 0L;
		}
		if (next > 2_000_000_000L) {
			next = 2_000_000_000L;
		}
		settings().casinoMinBet = (int) next;
		DataManager.get().saveAll();
	}

	public static void ensureHouseOdds() {
		ModSettings s = settings();
		boolean dirty = false;
		if (s.casinoOddsVersion < 2) {
			s.casinoOddsVersion = 2;
			s.casinoMinTenths = 0;
			s.casinoMaxTenths = 25;
			s.casinoChance = 15;
			s.casinoMinBet = Math.max(0, s.casinoMinBet);
			dirty = true;
		}
		if (s.casinoItemOddsVersion < 1) {
			s.casinoItemOddsVersion = 1;
			s.casinoPairChance = 6;
			s.casinoTripleChance = 2;
			s.casinoJackpotChance = 1;
			s.casinoPairPrize = 2;
			s.casinoTriplePrize = 4;
			s.casinoJackpotPrize = 8;
			if (s.casinoSpecials == null || s.casinoSpecials.isBlank() || "777:2:12".equals(s.casinoSpecials)) {
				s.casinoSpecials = "777:1:8";
			}
			dirty = true;
		}
		if (dirty) {
			DataManager.get().markDirty();
		}
	}

	public static int moneyDelayMs() {
		return Math.max(0, settings().casinoMoneyDelaySec) * 1000;
	}

	public static int itemDelayMs() {
		return Math.max(0, settings().casinoItemDelaySec) * 1000;
	}

	public static void shiftDelay(boolean item, int deltaSec) {
		if (item) {
			settings().casinoItemDelaySec = Math.max(0, Math.min(300, settings().casinoItemDelaySec + deltaSec));
		} else {
			settings().casinoMoneyDelaySec = Math.max(0, Math.min(300, settings().casinoMoneyDelaySec + deltaSec));
		}
		DataManager.get().saveAll();
	}

	public static String delayLabel(int seconds) {
		return seconds <= 0 ? "выкл" : seconds + " сек";
	}

	private static boolean waitBet(ServerPlayerEntity player, PlayerRecord record, boolean item, long now) {
		int need = item ? itemDelayMs() : moneyDelayMs();
		if (need <= 0) {
			return false;
		}
		long last = item ? record.lastCasinoItemMs : record.lastCasinoMoneyMs;
		if (last <= 0L) {
			last = record.lastCasinoGameMs;
		}
		long left = need - (now - last);
		if (left <= 0) {
			return false;
		}
		int sec = (int) Math.max(1, (left + 999) / 1000);
		Chat.send(player, Chat.red("Подождите " + sec + " сек. перед следующей ставкой "
				+ (item ? "на предметы" : "на деньги") + "."));
		return true;
	}

	public static Spin play(ServerPlayerEntity player, int bet) {
		Spin spin = new Spin();
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return spin;
		}
		if (AuthService.pending(player)) {
			Chat.send(player, Chat.red("Сначала войдите в аккаунт."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "не смог сыграть: нет входа", false);
			return spin;
		}
		if (!enabled()) {
			Chat.send(player, Chat.red("Казино сейчас выключено."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "не смог сыграть: казино выключено", false);
			return spin;
		}
		if (bet < minBet()) {
			Chat.send(player, Chat.red("Ставка должна быть не меньше " + Money.wc(minBet()) + "."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "не смог сыграть: ставка", false);
			return spin;
		}
		PlayerRecord record = DataManager.get().progress(player);
		spin.luck = record.luck();
		if (record.casinoBanned) {
			Chat.send(player, Chat.red("Вам запрещено играть в казино."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "не смог сыграть: запрет", false);
			return spin;
		}
		long now = System.currentTimeMillis();
		if (waitBet(player, record, false, now)) {
			return spin;
		}
		if (!Economy.takeCurrency(player, bet, "казино: ставка")) {
			Chat.send(player, Chat.red("Недостаточно World coin."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "не смог сыграть: нет монет", false);
			return spin;
		}
		int tenths = rollTenths();
		double multiplier = tenths / 10.0;
		int payout = tenths <= 0 ? 0 : (int) Math.floor(bet * multiplier);
		record.lastCasinoGameMs = now;
		record.lastCasinoMoneyMs = now;
		record.casinoGames++;
		record.casinoMoneyGames++;
		record.casinoSpent += bet;
		record.casinoWon += payout;
		if (payout > record.casinoBiggestWin) {
			record.casinoBiggestWin = payout;
		}
		DataManager.get().saveAll();
		PlayFeatures.onCasino(player);
		Achievements.onCasinoPlayed(player, bet, payout);
		if (tenths >= 20) {
			double from = record.luck();
			record.luck = Math.round((from + 0.1) * 10.0) / 10.0;
			ProgressLog.luck(record, from, record.luck(), ProgressLog.CASINO);
		}
		if (payout > 0) {
			Economy.giveCurrency(player, payout, "казино: выплата ×" + formatMult(tenths));
		} else {
			DataManager.get().saveAll();
			BalanceBoard.refresh(player, true);
		}
		BalanceBoard.refresh(player, true);
		int net = payout - bet;
		spin.ok = true;
		spin.tenths = tenths;
		spin.payout = payout;
		spin.net = net;
		spin.luck = record.luck();
		String mult = formatMult(tenths);
		boolean win = net > 0;
		AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO,
				"деньги: ставка " + Money.format(bet) + ", ×" + mult + ", выплата " + Money.format(payout)
						+ " (" + (net >= 0 ? "+" : "") + Money.format(net) + ")"
						+ (win ? ", выигрыш" : (net == 0 ? ", возврат" : ", проигрыш")),
				win || net == 0);
		if (net > 0) {
			Chat.send(player, Chat.green("Казино: ×" + mult + ". Выигрыш " + Money.wc(payout) + " (+" + Money.format(net) + "). Удача: "
					+ String.format(Locale.US, "%.1f", record.luck())));
		} else if (net == 0) {
			Chat.send(player, Chat.yellow("Казино: ×" + mult + ". Возврат ставки " + Money.wc(payout) + ". Удача: "
					+ String.format(Locale.US, "%.1f", record.luck())));
		} else {
			Chat.send(player, Chat.red("Казино: ×" + mult + ". Выплата " + Money.wc(payout) + " (" + Money.format(net) + "). Удача: "
					+ String.format(Locale.US, "%.1f", record.luck())));
		}
		return spin;
	}

	public static List<ItemStack> applyOreLuck(BlockState state, LootContextParameterSet.Builder builder, List<ItemStack> original) {
		if (original == null || original.isEmpty() || state == null || builder == null || DataManager.get() == null) {
			return original;
		}
		if (!isOre(state)) {
			return original;
		}
		ItemStack tool = builder.getOptional(LootContextParameters.TOOL);
		if (tool != null && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, tool) > 0) {
			return original;
		}
		Entity entity = builder.getOptional(LootContextParameters.THIS_ENTITY);
		if (!(entity instanceof ServerPlayerEntity player)) {
			return original;
		}
		PlayerRecord record = DataManager.get().progress(player);
		double luck = record.luck();
		if (luck <= 2.0) {
			return original;
		}
		ServerWorld world = builder.getWorld();
		double bonusPerItem = (luck - 2.0) * 0.25;
		int guaranteed = (int) bonusPerItem;
		double chance = bonusPerItem - guaranteed;
		List<ItemStack> out = new ArrayList<>(original.size());
		for (ItemStack stack : original) {
			if (stack == null || stack.isEmpty()) {
				out.add(stack);
				continue;
			}
			ItemStack copy = stack.copy();
			int extra = guaranteed * copy.getCount();
			for (int i = 0; i < copy.getCount(); i++) {
				if (world.getRandom().nextDouble() < chance) {
					extra++;
				}
			}
			if (extra > 0) {
				copy.setCount(Math.min(copy.getMaxCount() * 8, copy.getCount() + extra));
			}
			out.add(copy);
		}
		return out;
	}

	private static boolean isOre(BlockState state) {
		String path = Registries.BLOCK.getId(state.getBlock()).getPath();
		return path.endsWith("_ore") || "ancient_debris".equals(path) || "gilded_blackstone".equals(path);
	}

	/** Дом всегда в плюсе: чаще полный проигрыш, крупные × редки. */
	private static int rollTenths() {
		int min = minTenths();
		int max = Math.max(min, maxTenths());
		double generosity = chance() / 100.0;
		double bust = 0.50 - generosity * 0.18;
		if (RANDOM.nextDouble() < bust) {
			return min;
		}
		int span = max - min + 1;
		double[] weights = new double[span];
		double total = 0;
		for (int i = 0; i < span; i++) {
			int tenths = min + i;
			double mult = tenths / 10.0;
			double weight = 1.0 / Math.pow(Math.max(0.15, mult + 0.25), 3.6 - generosity * 0.5);
			if (tenths >= 12) {
				weight *= 0.22;
			}
			if (tenths >= 18) {
				weight *= 0.18;
			}
			if (tenths >= 25) {
				weight *= 0.12;
			}
			weights[i] = weight;
			total += weight;
		}
		double roll = RANDOM.nextDouble() * total;
		double cursor = 0;
		for (int i = 0; i < span; i++) {
			cursor += weights[i];
			if (roll <= cursor) {
				return min + i;
			}
		}
		return min;
	}

	private static int clampRange(int tenths) {
		return Math.min(9999, Math.max(0, tenths));
	}

	private static int clampChance(int value) {
		return Math.min(100, Math.max(0, value));
	}

	public static final class ItemSpin {
		public boolean ok;
		public String a = "";
		public String b = "";
		public String c = "";
		public int prize;
		public String kind = "lose";
	}

	public static java.util.List<String> itemSymbols() {
		String raw = settings().casinoItemSymbols;
		java.util.List<String> out = new java.util.ArrayList<>();
		if (raw != null) {
			for (String part : raw.split("[,;]")) {
				String s = part.trim();
				if (!s.isEmpty()) {
					out.add(s);
				}
			}
		}
		while (out.size() < 20) {
			out.add(String.valueOf(out.size() + 1));
		}
		if (out.size() > 20) {
			return out.subList(0, 20);
		}
		return out;
	}

	public static void setItemText(String kind, String value) {
		if ("casino_symbols".equals(kind)) {
			settings().casinoItemSymbols = value == null ? "" : value;
		} else if ("casino_specials".equals(kind)) {
			settings().casinoSpecials = value == null ? "" : value;
		}
		DataManager.get().saveAll();
	}

	public static void shiftItemChance(String which, int delta) {
		ModSettings s = settings();
		if ("pair".equals(which)) {
			s.casinoPairChance = clampChance(s.casinoPairChance + delta);
		} else if ("triple".equals(which)) {
			s.casinoTripleChance = clampChance(s.casinoTripleChance + delta);
		} else if ("jackpot".equals(which)) {
			s.casinoJackpotChance = clampChance(s.casinoJackpotChance + delta);
		} else if ("pairPrize".equals(which)) {
			s.casinoPairPrize = Math.max(1, Math.min(64, s.casinoPairPrize + delta));
		} else if ("triplePrize".equals(which)) {
			s.casinoTriplePrize = Math.max(1, Math.min(64, s.casinoTriplePrize + delta));
		} else if ("jackpotPrize".equals(which)) {
			s.casinoJackpotPrize = Math.max(1, Math.min(64, s.casinoJackpotPrize + delta));
		}
		DataManager.get().saveAll();
	}

	public static ItemSpin playItem(ServerPlayerEntity player, int slot) {
		ItemSpin spin = new ItemSpin();
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return spin;
		}
		if (AuthService.pending(player)) {
			Chat.send(player, Chat.red("Сначала войдите в аккаунт."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "предмет: нет входа", false);
			return spin;
		}
		if (!enabled()) {
			Chat.send(player, Chat.red("Казино сейчас выключено."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "предмет: казино выключено", false);
			return spin;
		}
		PlayerRecord record = DataManager.get().progress(player);
		if (record.casinoBanned) {
			Chat.send(player, Chat.red("Вам запрещено играть в казино."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "предмет: запрет", false);
			return spin;
		}
		long now = System.currentTimeMillis();
		if (waitBet(player, record, true, now)) {
			return spin;
		}
		if (slot < 0 || slot >= 36) {
			Chat.send(player, Chat.red("Выберите предмет в инвентаре."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "предмет: нет ставки", false);
			return spin;
		}
		ItemStack stack = player.getInventory().getStack(slot);
		if (stack == null || stack.isEmpty()) {
			Chat.send(player, Chat.red("Выберите предмет в инвентаре."));
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO, "предмет: нет ставки", false);
			return spin;
		}
		ItemStack stake = stack.copy();
		stake.setCount(1);
		stack.decrement(1);
		player.getInventory().markDirty();
		java.util.List<String> symbols = itemSymbols();
		Special special = rollSpecial();
		double roll = RANDOM.nextDouble() * 100.0;
		ModSettings s = settings();
		if (special != null) {
			spin.a = spin.b = spin.c = special.symbol;
			spin.prize = special.prize;
			spin.kind = "special";
		} else if (roll < s.casinoJackpotChance) {
			String sym = symbols.get(0);
			for (String cand : symbols) {
				if ("777".equals(cand)) {
					sym = cand;
					break;
				}
			}
			spin.a = spin.b = spin.c = sym;
			spin.prize = Math.max(1, s.casinoJackpotPrize);
			spin.kind = "jackpot";
		} else if (roll < s.casinoJackpotChance + s.casinoTripleChance) {
			String sym = symbols.get(RANDOM.nextInt(symbols.size()));
			spin.a = spin.b = spin.c = sym;
			spin.prize = Math.max(1, s.casinoTriplePrize);
			spin.kind = "triple";
		} else if (roll < s.casinoJackpotChance + s.casinoTripleChance + s.casinoPairChance) {
			String sym = symbols.get(RANDOM.nextInt(symbols.size()));
			String other = symbols.get(RANDOM.nextInt(symbols.size()));
			int pos = RANDOM.nextInt(3);
			spin.a = pos == 0 ? other : sym;
			spin.b = pos == 1 ? other : sym;
			spin.c = pos == 2 ? other : sym;
			if (spin.a.equals(spin.b) && spin.b.equals(spin.c)) {
				spin.b = other.equals(sym) ? symbols.get((symbols.indexOf(sym) + 1) % symbols.size()) : other;
			}
			spin.prize = Math.max(1, s.casinoPairPrize);
			spin.kind = "pair";
		} else {
			spin.a = symbols.get(RANDOM.nextInt(symbols.size()));
			spin.b = symbols.get(RANDOM.nextInt(symbols.size()));
			spin.c = symbols.get(RANDOM.nextInt(symbols.size()));
			if (spin.a.equals(spin.b) || spin.b.equals(spin.c) || spin.a.equals(spin.c)) {
				spin.c = symbols.get((symbols.indexOf(spin.a) + 3) % symbols.size());
			}
			spin.prize = 0;
			spin.kind = "lose";
		}
		int given = 0;
		if (spin.prize > 0) {
			ItemStack reward = stake.copy();
			given = Math.min(stake.getMaxCount(), spin.prize);
			reward.setCount(given);
			if (!player.getInventory().insertStack(reward)) {
				player.dropItem(reward, false);
			}
		}
		record.lastCasinoGameMs = now;
		record.lastCasinoItemMs = now;
		record.casinoGames++;
		record.casinoItemGames++;
		record.casinoItemLost++;
		if (given > 0) {
			record.casinoItemWins++;
			record.casinoItemReceived += given;
			noteItemWin(record, stake, given);
		} else {
			record.casinoItemLosses++;
		}
		DataManager.get().saveAll();
		spin.ok = true;
		String line = spin.a + " | " + spin.b + " | " + spin.c;
		String item = ItemLabels.of(stake);
		boolean win = given > 0;
		AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.CASINO,
				"предмет: ставка " + item + " ×1, " + line + ", " + itemKindLabel(spin.kind)
						+ (win ? (", выигрыш ×" + given + ", получено " + given) : ", проигрыш, потерян 1"),
				win);
		if (win) {
			Chat.send(player, Chat.green("Казино (предмет): " + line + " → ×" + given));
		} else {
			Chat.send(player, Chat.red("Казино (предмет): " + line + " — ставка сгорела."));
		}
		return spin;
	}

	private static void noteItemWin(PlayerRecord record, ItemStack stake, int given) {
		if (record == null || stake == null || stake.isEmpty() || given <= 0) {
			return;
		}
		if (record.casinoItemGot == null) {
			record.casinoItemGot = new java.util.LinkedHashMap<>();
		}
		String id = Registries.ITEM.getId(stake.getItem()).toString();
		record.casinoItemGot.merge(id, given, Integer::sum);
	}

	public static String topItemLabel(PlayerRecord record) {
		if (record == null || record.casinoItemGot == null || record.casinoItemGot.isEmpty()) {
			return "нет";
		}
		String best = null;
		int max = 0;
		for (var e : record.casinoItemGot.entrySet()) {
			int n = e.getValue() == null ? 0 : e.getValue();
			if (n > max) {
				max = n;
				best = e.getKey();
			}
		}
		if (best == null || max <= 0) {
			return "нет";
		}
		net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(best);
		String name = best;
		if (id != null && Registries.ITEM.containsId(id)) {
			name = ItemLabels.of(Registries.ITEM.get(id));
		}
		return name + " ×" + max;
	}

	private static String itemKindLabel(String kind) {
		if ("jackpot".equals(kind)) {
			return "джекпот";
		}
		if ("triple".equals(kind)) {
			return "тройка";
		}
		if ("pair".equals(kind)) {
			return "пара";
		}
		if ("special".equals(kind)) {
			return "особый";
		}
		return "пусто";
	}

	private static Special rollSpecial() {
		String raw = settings().casinoSpecials;
		if (raw == null || raw.isBlank()) {
			return null;
		}
		for (String part : raw.split("[;,]")) {
			String[] bits = part.trim().split(":");
			if (bits.length < 3) {
				continue;
			}
			String symbol = bits[0].trim();
			int chance;
			int prize;
			try {
				chance = Integer.parseInt(bits[1].trim());
				prize = Integer.parseInt(bits[2].trim());
			} catch (NumberFormatException ignored) {
				continue;
			}
			if (symbol.isEmpty() || chance <= 0) {
				continue;
			}
			if (RANDOM.nextDouble() * 100.0 < chance) {
				Special special = new Special();
				special.symbol = symbol;
				special.prize = Math.max(1, prize);
				return special;
			}
		}
		return null;
	}

	private static final class Special {
		String symbol;
		int prize;
	}

	private static ModSettings settings() {
		return DataManager.get().settings();
	}
}
