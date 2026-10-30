package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.ModerationService;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ClanService;
import ru.admincontrol.util.CoinLedger;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.List;

public class CoinsScreenHandler extends ScreenHandler {
	public static final int HISTORY = 1;
	public static final int CLOSE = 2;
	public static final int UNWARN = 3;
	public static final int TAB_ACCOUNT = 4;
	public static final int TAB_PAY = 5;
	public static final int MODE_PLAYER = 6;
	public static final int MODE_CLAN = 7;
	public static final int PAGE_PREV = 8;
	public static final int PAGE_NEXT = 9;
	public static final int MINUS_10 = 10;
	public static final int MINUS_1 = 11;
	public static final int PLUS_1 = 12;
	public static final int PLUS_10 = 13;
	public static final int SET_ALL = 14;
	public static final int CONFIRM = 15;
	public static final int NICK_BASE = 100;
	public static final int PAGE_SIZE = 5;
	private final PropertyDelegate props = new ArrayPropertyDelegate(14);
	public final List<String> nicks;
	public final List<String> clans;
	private int tab;
	private int mode;
	private int page;
	private int selected = -1;
	private int amount = 1;

	public static CoinsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int[] data = new int[9];
		for (int i = 0; i < data.length; i++) {
			data[i] = buf.readVarInt();
		}
		return new CoinsScreenHandler(syncId, data, readNames(buf), readNames(buf));
	}

	private static List<String> readNames(PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<String> names = new ArrayList<>(Math.max(0, n));
		for (int i = 0; i < n; i++) {
			names.add(buf.readString(16));
		}
		return names;
	}

	public CoinsScreenHandler(int syncId, int[] data, List<String> nicks, List<String> clans) {
		super(ModScreenHandlers.COINS, syncId);
		this.nicks = nicks == null ? new ArrayList<>() : new ArrayList<>(nicks);
		this.clans = clans == null ? new ArrayList<>() : new ArrayList<>(clans);
		this.addProperties(this.props);
		if (data != null) {
			for (int i = 0; i < 7 && i < data.length; i++) {
				this.props.set(i, data[i]);
			}
			if (data.length > 7) {
				this.props.set(12, data[7]);
			}
			if (data.length > 8) {
				this.props.set(13, data[8]);
			}
		}
		syncPay();
	}

	public int coins() {
		return this.props.get(0);
	}

	public boolean warnBuy() {
		return this.props.get(1) != 0;
	}

	public int warnPrice() {
		return this.props.get(2);
	}

	public int warns() {
		return this.props.get(3);
	}

	public boolean transfers() {
		return this.props.get(4) != 0;
	}

	public int earned() {
		return this.props.get(5);
	}

	public int spent() {
		return this.props.get(6);
	}

	public int casinoSpent() {
		return this.props.get(12);
	}

	public int casinoWon() {
		return this.props.get(13);
	}

	public int tab() {
		return this.props.get(7);
	}

	public int mode() {
		return this.props.get(8);
	}

	public int page() {
		return this.props.get(9);
	}

	public int selected() {
		return this.props.get(10);
	}

	public int amount() {
		return Math.max(1, this.props.get(11));
	}

	public boolean payClan() {
		return mode() == 1;
	}

	public List<String> targets() {
		return payClan() ? this.clans : this.nicks;
	}

	public String selectedName() {
		List<String> list = targets();
		int index = selected();
		if (index < 0 || index >= list.size()) {
			return "";
		}
		return list.get(index);
	}

	public int pageCount() {
		List<String> list = targets();
		if (list.isEmpty()) {
			return 1;
		}
		return (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (id == CLOSE) {
			serverPlayer.closeHandledScreen();
			return true;
		}
		if (id == TAB_ACCOUNT) {
			this.tab = 0;
			syncPay();
			return true;
		}
		if (id == TAB_PAY) {
			this.tab = 1;
			syncPay();
			return true;
		}
		if (id == MODE_PLAYER) {
			this.mode = 0;
			this.page = 0;
			this.selected = -1;
			syncPay();
			return true;
		}
		if (id == MODE_CLAN) {
			this.mode = 1;
			this.page = 0;
			this.selected = -1;
			syncPay();
			return true;
		}
		if (id == HISTORY) {
			ru.admincontrol.util.ScreenNav.open(serverPlayer, new LogFactory("История World coin",
					CoinLedger.history(serverPlayer.getGameProfile().getName())));
			return true;
		}
		if (id == UNWARN) {
			return unwarn(serverPlayer);
		}
		if (id == CONFIRM) {
			return confirmPay(serverPlayer);
		}
		if (id == PAGE_PREV) {
			this.page = Math.max(0, this.page - 1);
		} else if (id == PAGE_NEXT) {
			this.page = Math.min(pageCount() - 1, this.page + 1);
		} else if (id == MINUS_10) {
			this.amount -= 10;
		} else if (id == MINUS_1) {
			this.amount -= 1;
		} else if (id == PLUS_1) {
			this.amount += 1;
		} else if (id == PLUS_10) {
			this.amount += 10;
		} else if (id == SET_ALL) {
			this.amount = Math.max(1, Economy.balance(serverPlayer));
		} else if (id >= NICK_BASE && id < NICK_BASE + PAGE_SIZE) {
			int index = this.page * PAGE_SIZE + (id - NICK_BASE);
			List<String> list = this.mode == 1 ? this.clans : this.nicks;
			if (index >= 0 && index < list.size()) {
				this.selected = index;
			}
		} else {
			return false;
		}
		int max = Math.max(1, Economy.balance(serverPlayer));
		this.amount = Math.max(1, Math.min(max, this.amount));
		refresh(serverPlayer);
		syncPay();
		return true;
	}

	private boolean unwarn(ServerPlayerEntity serverPlayer) {
		var s = DataManager.get() == null ? null : DataManager.get().settings();
		if (s == null || !s.warnBuyEnabled) {
			Chat.send(serverPlayer, Chat.red("Снятие варна за WC выключено."));
			return true;
		}
		int price = Math.max(1, s.warnBuyPrice);
		if (DataManager.get().getOrCreate(serverPlayer).activeWarns <= 0) {
			Chat.send(serverPlayer, Chat.red("У вас нет активных варнов."));
			return true;
		}
		if (!Economy.takeCurrency(serverPlayer, price, "снятие варна")) {
			Chat.send(serverPlayer, Chat.red("Недостаточно World coin. Нужно " + Money.wc(price) + "."));
			refresh(serverPlayer);
			return true;
		}
		if (!ModerationService.buyUnwarn(serverPlayer)) {
			Economy.giveCurrency(serverPlayer, price, "возврат: варн не снят");
			Chat.send(serverPlayer, Chat.red("Не удалось снять варн."));
		}
		refresh(serverPlayer);
		return true;
	}

	private boolean confirmPay(ServerPlayerEntity serverPlayer) {
		String name = selectedName();
		if (name == null || name.isBlank()) {
			Chat.send(serverPlayer, Chat.red(payClan() ? "Выберите клан." : "Выберите игрока."));
			return true;
		}
		int pay = Math.max(1, this.amount);
		if (this.mode == 1) {
			String err = ClanService.donate(serverPlayer, name, pay);
			if (err != null) {
				Chat.send(serverPlayer, Chat.red(err));
				refresh(serverPlayer);
				syncPay();
				return true;
			}
			Chat.send(serverPlayer, Chat.green("Передано " + Money.wc(pay) + " в казну клана «" + name + "»."));
			refresh(serverPlayer);
			syncPay();
			return true;
		}
		if (DataManager.get() != null && !DataManager.get().settings().playerTransfers) {
			Chat.send(serverPlayer, Chat.red("Переводы между игроками выключены."));
			return true;
		}
		PlayerRecord dest = DataManager.get() == null ? null : DataManager.get().findByNick(name).orElse(null);
		if (dest == null) {
			Chat.send(serverPlayer, Chat.red("Игрок не найден."));
			return true;
		}
		if (!Economy.transfer(serverPlayer, dest, pay)) {
			Chat.send(serverPlayer, Chat.red("Недостаточно World coin."));
			refresh(serverPlayer);
			syncPay();
			return true;
		}
		ServerPlayerEntity to = ru.admincontrol.command.CommandUtil.findOnline(serverPlayer.getServer(), dest.nickname).orElse(null);
		if (to != null) {
			Chat.send(to, Chat.green("Вы получили " + Money.wc(pay) + " от " + serverPlayer.getGameProfile().getName() + "."));
		}
		Chat.send(serverPlayer, Chat.green("Передано " + Money.wc(pay) + " игроку " + dest.nickname + "."));
		refresh(serverPlayer);
		syncPay();
		return true;
	}

	private void refresh(ServerPlayerEntity player) {
		int[] data = CoinsFactory.snapshot(player);
		for (int i = 0; i < 7 && i < data.length; i++) {
			this.props.set(i, data[i]);
		}
		if (data.length > 7) {
			this.props.set(12, data[7]);
		}
		if (data.length > 8) {
			this.props.set(13, data[8]);
		}
	}

	public void setAmountFromText(ServerPlayerEntity player, String raw) {
		int parsed = ru.admincontrol.util.UiValues.parseInt(raw, Integer.MIN_VALUE);
		if (raw == null || raw.isBlank() || parsed == Integer.MIN_VALUE) {
			return;
		}
		int max = Math.max(1, Economy.balance(player));
		this.amount = Math.max(1, Math.min(max, parsed));
		syncPay();
	}

	private void syncPay() {
		this.props.set(7, this.tab);
		this.props.set(8, this.mode);
		this.props.set(9, this.page);
		this.props.set(10, this.selected);
		this.props.set(11, Math.max(1, this.amount));
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
