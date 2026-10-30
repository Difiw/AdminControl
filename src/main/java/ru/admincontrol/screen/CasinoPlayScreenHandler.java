package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Economy;

public class CasinoPlayScreenHandler extends ScreenHandler {
	public static final int INV_GUI_X = 32;
	public static final int INV_GUI_Y = 96;
	public static final int PLAY = 0;
	public static final int MINUS_10 = 1;
	public static final int MINUS_1 = 2;
	public static final int PLUS_1 = 3;
	public static final int PLUS_10 = 4;
	public static final int PLUS_50 = 5;
	public static final int PLUS_100 = 6;
	public static final int SET_1 = 7;
	public static final int SET_10 = 8;
	public static final int SET_50 = 9;
	public static final int SET_100 = 10;
	public static final int SET_ALL = 11;
	public static final int MODE_MONEY = 12;
	public static final int MODE_ITEM = 13;
	public static final int MINUS_100 = 14;
	public static final int MINUS_1000 = 15;
	public static final int PLUS_1000 = 16;
	public static final int SET_100K = 17;
	private static final int PROPS = 12;
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);
	private int bet = 1;
	private int lastTenths = -1;
	private int lastPayout;
	private int lastNet;
	private int mode;
	private int itemSlot = -1;

	public static CasinoPlayScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int[] data = new int[PROPS];
		for (int i = 0; i < PROPS; i++) {
			data[i] = buf.readVarInt();
		}
		return new CasinoPlayScreenHandler(syncId, inventory, data);
	}

	public CasinoPlayScreenHandler(int syncId, int[] data) {
		this(syncId, null, data);
	}

	public CasinoPlayScreenHandler(int syncId, PlayerInventory inventory, int[] data) {
		super(ModScreenHandlers.CASINO_PLAY, syncId);
		this.addProperties(props);
		int min = CasinoService.minBet();
		if (data != null && data.length > 2) {
			this.bet = Math.max(min, data[2]);
			this.lastTenths = data.length > 3 ? data[3] : 0;
			this.lastPayout = data.length > 4 ? data[4] : 0;
			this.lastNet = data.length > 5 ? data[5] : 0;
			this.mode = data.length > 10 ? data[10] : 0;
			this.itemSlot = data.length > 11 ? data[11] : -1;
		} else {
			this.bet = min;
		}
		if (inventory != null && this.mode == 1) {
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 9; col++) {
					this.addSlot(new Slot(inventory, col + row * 9 + 9,
							INV_GUI_X + 8 + col * 18, INV_GUI_Y + 31 + row * 18));
				}
			}
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col, INV_GUI_X + 8 + col * 18, INV_GUI_Y + 89));
			}
		}
		sync(data);
	}

	public int coins() {
		return props.get(0);
	}

	public double luck() {
		return props.get(1) / 10.0;
	}

	public int bet() {
		return Math.max(CasinoService.minBet(), props.get(2));
	}

	public int lastTenths() {
		return props.get(3);
	}

	public int lastPayout() {
		return props.get(4);
	}

	public int lastNet() {
		return props.get(5);
	}

	public boolean casinoOn() {
		return props.get(6) != 0;
	}

	public boolean banned() {
		return props.get(7) != 0;
	}

	public int minTenths() {
		return props.get(8);
	}

	public int maxTenths() {
		return props.get(9);
	}

	public int mode() {
		return props.get(10);
	}

	public int itemSlot() {
		return props.get(11);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		PlayerRecord record = DataManager.get().progress(serverPlayer);
		int min = CasinoService.minBet();
		int coins = Economy.balance(serverPlayer);
		if (id == PLAY) {
			if (this.mode == 1) {
				CasinoService.ItemSpin spin = CasinoService.playItem(serverPlayer, this.itemSlot);
				if (spin.ok) {
					this.lastTenths = spin.prize;
					this.lastPayout = spin.prize;
					this.lastNet = spin.prize;
				}
			} else {
				CasinoService.Spin spin = CasinoService.play(serverPlayer, this.bet);
				if (spin.ok) {
					this.lastTenths = spin.tenths;
					this.lastPayout = spin.payout;
					this.lastNet = spin.net;
				}
			}
		} else if (id == MODE_MONEY) {
			if (this.mode != 0) {
				ru.admincontrol.util.ScreenNav.replace(serverPlayer, new CasinoPlayFactory(0, this.bet));
			}
			return true;
		} else if (id == MODE_ITEM) {
			if (this.mode != 1) {
				ru.admincontrol.util.ScreenNav.replace(serverPlayer, new CasinoPlayFactory(1, this.bet));
			}
			return true;
		} else if (id == MINUS_10) {
			this.bet = Math.max(min, this.bet - 10);
		} else if (id == MINUS_1) {
			this.bet = Math.max(min, this.bet - 1);
		} else if (id == PLUS_1) {
			this.bet += 1;
		} else if (id == PLUS_10) {
			this.bet += 10;
		} else if (id == PLUS_50) {
			this.bet += 50;
		} else if (id == PLUS_100) {
			this.bet += 100;
		} else if (id == MINUS_100) {
			this.bet = Math.max(min, this.bet - 100);
		} else if (id == MINUS_1000) {
			this.bet = Math.max(min, this.bet - 1000);
		} else if (id == PLUS_1000) {
			this.bet += 1000;
		} else if (id == SET_1) {
			this.bet = min;
		} else if (id == SET_10) {
			this.bet = Math.max(min, min <= 0 ? 2 : min * 2);
		} else if (id == SET_50) {
			this.bet = Math.max(min, 5000);
		} else if (id == SET_100) {
			this.bet = Math.max(min, 10000);
		} else if (id == SET_100K) {
			this.bet = Math.max(min, 100000);
		} else if (id == SET_ALL) {
			this.bet = Math.max(min, coins);
		} else {
			return false;
		}
		this.bet = Math.max(min, Math.min(2_000_000_000, this.bet));
		syncFrom(serverPlayer, record);
		return true;
	}

	private void syncFrom(ServerPlayerEntity player, PlayerRecord record) {
		sync(new int[] {
				Economy.balance(player),
				(int) Math.round(record.luck() * 10.0),
				this.bet,
				this.lastTenths,
				this.lastPayout,
				this.lastNet,
				CasinoService.enabled() ? 1 : 0,
				record.casinoBanned ? 1 : 0,
				CasinoService.minTenths(),
				CasinoService.maxTenths(),
				this.mode,
				this.itemSlot
		});
	}

	private void sync(int[] data) {
		for (int i = 0; i < PROPS; i++) {
			props.set(i, data != null && i < data.length ? data[i] : 0);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (this.mode != 1 || slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}
		this.itemSlot = this.slots.get(slotIndex).getIndex();
		if (player instanceof ServerPlayerEntity serverPlayer) {
			syncFrom(serverPlayer, DataManager.get().progress(serverPlayer));
		}
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
