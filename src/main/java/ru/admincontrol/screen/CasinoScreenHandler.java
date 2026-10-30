package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Chat;

public class CasinoScreenHandler extends ScreenHandler {
	public static final int TOGGLE = 0;
	public static final int MIN_MINUS = 1;
	public static final int MIN_PLUS = 2;
	public static final int MAX_MINUS = 3;
	public static final int MAX_PLUS = 4;
	public static final int CHANCE_MINUS = 5;
	public static final int CHANCE_PLUS = 6;
	public static final int CHANCE_MINUS_10 = 7;
	public static final int CHANCE_PLUS_10 = 8;
	private final PropertyDelegate props = new ArrayPropertyDelegate(4);

	public static CasinoScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new CasinoScreenHandler(syncId, new int[] {
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
		});
	}

	public CasinoScreenHandler(int syncId, int[] data) {
		super(ModScreenHandlers.CASINO, syncId);
		this.addProperties(props);
		sync(data);
	}

	public boolean casinoOn() {
		return props.get(0) != 0;
	}

	public int minTenths() {
		return props.get(1);
	}

	public int maxTenths() {
		return props.get(2);
	}

	public int chance() {
		return props.get(3);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(serverPlayer) < AdminRanks.CURATOR) {
			return false;
		}
		if (id == TOGGLE) {
			CasinoService.setEnabled(!CasinoService.enabled());
			Chat.send(serverPlayer, Chat.yellow("Казино: " + (CasinoService.enabled() ? "вкл" : "выкл")));
		} else if (id == MIN_MINUS) {
			CasinoService.shiftMin(-1);
		} else if (id == MIN_PLUS) {
			CasinoService.shiftMin(1);
		} else if (id == MAX_MINUS) {
			CasinoService.shiftMax(-1);
		} else if (id == MAX_PLUS) {
			CasinoService.shiftMax(1);
		} else if (id == CHANCE_MINUS) {
			CasinoService.shiftChance(-1);
		} else if (id == CHANCE_PLUS) {
			CasinoService.shiftChance(1);
		} else if (id == CHANCE_MINUS_10) {
			CasinoService.shiftChance(-10);
		} else if (id == CHANCE_PLUS_10) {
			CasinoService.shiftChance(10);
		} else {
			return false;
		}
		if (id == CHANCE_MINUS || id == CHANCE_PLUS || id == CHANCE_MINUS_10 || id == CHANCE_PLUS_10) {
			Chat.send(serverPlayer, Chat.yellow("Шанс победы: " + CasinoService.formatChance()));
		}
		sync(CasinoFactory.snapshot());
		return true;
	}

	private void sync(int[] data) {
		for (int i = 0; i < 4; i++) {
			int fallback = i == 0 ? 1 : (i == 2 ? 20 : (i == 3 ? 50 : 1));
			props.set(i, data != null && i < data.length ? data[i] : fallback);
		}
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
