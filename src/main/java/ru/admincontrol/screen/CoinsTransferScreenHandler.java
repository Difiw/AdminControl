package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.List;

public class CoinsTransferScreenHandler extends ScreenHandler {
	public static final int PAGE_PREV = 0;
	public static final int PAGE_NEXT = 1;
	public static final int MINUS_10 = 2;
	public static final int MINUS_1 = 3;
	public static final int PLUS_1 = 4;
	public static final int PLUS_10 = 5;
	public static final int SET_ALL = 6;
	public static final int CONFIRM = 7;
	public static final int BACK = 8;
	public static final int NICK_BASE = 100;
	public static final int PAGE_SIZE = 6;
	private final PropertyDelegate props = new ArrayPropertyDelegate(4);
	public final List<String> nicks;
	private int amount = 1;
	private int selected = -1;
	private int page;

	public static CoinsTransferScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<String> nicks = new ArrayList<>(Math.max(0, n));
		for (int i = 0; i < n; i++) {
			nicks.add(buf.readString(16));
		}
		int coins = buf.readVarInt();
		return new CoinsTransferScreenHandler(syncId, nicks, coins, 1, -1);
	}

	public CoinsTransferScreenHandler(int syncId, List<String> nicks, int coins, int amount, int selected) {
		super(ModScreenHandlers.COINS_TRANSFER, syncId);
		this.nicks = nicks == null ? new ArrayList<>() : new ArrayList<>(nicks);
		this.amount = Math.max(1, amount);
		this.selected = selected;
		this.addProperties(this.props);
		this.props.set(3, Math.max(0, coins));
		sync();
	}

	public int coins() {
		return this.props.get(3);
	}

	public int amount() {
		return Math.max(1, this.props.get(0));
	}

	public int selected() {
		return this.props.get(1);
	}

	public int page() {
		return this.props.get(2);
	}

	public String selectedNick() {
		int index = selected();
		if (index < 0 || index >= this.nicks.size()) {
			return "";
		}
		return this.nicks.get(index);
	}

	public int pageCount() {
		if (this.nicks.isEmpty()) {
			return 1;
		}
		return (this.nicks.size() + PAGE_SIZE - 1) / PAGE_SIZE;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (id == BACK) {
			ru.admincontrol.util.ScreenNav.back(serverPlayer);
			return true;
		}
		if (id == CONFIRM) {
			String nick = selectedNick();
			if (nick == null || nick.isBlank()) {
				Chat.send(serverPlayer, Chat.red("Выберите игрока."));
				return true;
			}
			PlayerRecord dest = DataManager.get() == null ? null : DataManager.get().findByNick(nick).orElse(null);
			if (dest == null) {
				Chat.send(serverPlayer, Chat.red("Игрок не найден."));
				return true;
			}
			int pay = Math.max(1, this.amount);
			if (!Economy.transfer(serverPlayer, dest, pay)) {
				Chat.send(serverPlayer, Chat.red("Недостаточно World coin."));
				syncBalance(serverPlayer);
				return true;
			}
			ServerPlayerEntity to = ru.admincontrol.command.CommandUtil.findOnline(serverPlayer.getServer(), dest.nickname).orElse(null);
			if (to != null) {
				Chat.send(to, Chat.green("Вы получили " + Money.wc(pay) + " от " + serverPlayer.getGameProfile().getName() + "."));
			}
			Chat.send(serverPlayer, Chat.green("Передано " + Money.wc(pay) + " игроку " + dest.nickname + "."));
			ru.admincontrol.util.ScreenNav.back(serverPlayer);
			return true;
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
			if (index >= 0 && index < this.nicks.size()) {
				this.selected = index;
			}
		} else {
			return false;
		}
		int max = Math.max(1, Economy.balance(serverPlayer));
		this.amount = Math.max(1, Math.min(max, this.amount));
		syncBalance(serverPlayer);
		sync();
		return true;
	}

	private void syncBalance(ServerPlayerEntity player) {
		this.props.set(3, Economy.balance(player));
	}

	private void sync() {
		this.props.set(0, this.amount);
		this.props.set(1, this.selected);
		this.props.set(2, this.page);
	}

	public void setAmountFromText(ServerPlayerEntity player, String raw) {
		int parsed = ru.admincontrol.util.UiValues.parseInt(raw, Integer.MIN_VALUE);
		if (raw == null || raw.isBlank() || parsed == Integer.MIN_VALUE) {
			return;
		}
		int max = Math.max(1, Economy.balance(player));
		this.amount = Math.max(1, Math.min(max, parsed));
		sync();
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
