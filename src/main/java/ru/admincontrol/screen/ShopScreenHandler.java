package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.PlayFeatures;

public class ShopScreenHandler extends ScreenHandler {
	public static final int PROPS = 23;
	/** Покупка блока привата уровня N: кнопка BUY_PRIVATE + N. */
	public static final int BUY_PRIVATE = 20;
	public static final int BUY_HOME = 1;
	public static final int BUY_LUCK = 2;
	public static final int BUY_POWER = 3;
	public static final int BUY_BACKPACK = 4;
	public static final int BUY_PRIV = 5;
	public static final int BUY_SIZE = 6;
	public static final int BUY_WORKBENCH = 7;
	public static final int BUY_FURNACE = 8;
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);

	public static ShopScreenHandler createClient(int syncId, net.minecraft.entity.player.PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		int[] data = new int[PROPS];
		for (int i = 0; i < n && i < PROPS; i++) {
			data[i] = buf.readVarInt();
		}
		return new ShopScreenHandler(syncId, data);
	}

	public ShopScreenHandler(int syncId, int[] data) {
		super(ModScreenHandlers.SHOP, syncId);
		this.addProperties(this.props);
		sync(data);
	}

	public static int[] snapshot(ServerPlayerEntity player) {
		var s = PlayFeatures.s();
		int coins = player == null ? 0 : Economy.balance(player);
		PlayerRecord rec = player == null || ru.admincontrol.data.DataManager.get() == null
				? null : ru.admincontrol.data.DataManager.get().progress(player);
		int pack = rec == null ? 0 : rec.backpackLevel;
		int nextPack = Math.min(5, pack + 1);
		int furnace = rec == null ? 0 : rec.furnaceLevel;
		return new int[] {
				coins, s.shopHomePrice, s.shopLuckPrice, s.shopPowerPrice, s.shopEnabled ? 1 : 0,
				s.backpackPrice(Math.max(1, nextPack)), s.shopHomePrivatePrice, s.shopHomeSizePrice, pack,
				rec == null ? 0 : (rec.homePrivateOwned ? 1 : 0), rec == null ? 3 : rec.maxHomeSize,
				s.shopWorkbenchPrice, rec != null && rec.workbenchOwned ? 1 : 0, furnace,
				s.furnacePrice(furnace + 1), s.furnaceMaxLevel(),
				s.privatePrice(1), s.privatePrice(2), s.privatePrice(3), s.privatePrice(4), s.privatePrice(5),
				s.furnaceCountFor(furnace + 1), s.furnaceSecondsFor(furnace + 1)
		};
	}

	public int furnaceNextCount() { return props.get(21); }
	public int furnaceNextSeconds() { return props.get(22); }

	public int privatePrice(int level) {
		return props.get(15 + Math.max(1, Math.min(5, level)));
	}

	public int coins() { return props.get(0); }
	public int home() { return props.get(1); }
	public int luck() { return props.get(2); }
	public int power() { return props.get(3); }
	public boolean on() { return props.get(4) != 0; }
	public int backpack() { return props.get(5); }
	public int priv() { return props.get(6); }
	public int sizePrice() { return props.get(7); }
	public int packLevel() { return props.get(8); }
	public boolean privOwned() { return props.get(9) != 0; }
	public int maxSize() { return props.get(10); }
	public int workbenchPrice() { return props.get(11); }
	public boolean workbenchOwned() { return props.get(12) != 0; }
	public int furnaceLevel() { return props.get(13); }
	public int furnacePrice() { return props.get(14); }
	public int furnaceMax() { return props.get(15); }

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (id > BUY_PRIVATE && id <= BUY_PRIVATE + ru.admincontrol.util.PrivateService.LEVELS) {
			PlayFeatures.buy(serverPlayer, "private" + (id - BUY_PRIVATE));
			sync(snapshot(serverPlayer));
			return true;
		}
		String key = switch (id) {
			case BUY_HOME -> "home";
			case BUY_LUCK -> "luck";
			case BUY_POWER -> "power";
			case BUY_BACKPACK -> "backpack";
			case BUY_WORKBENCH -> "workbench";
			case BUY_FURNACE -> "furnace";
			default -> null;
		};
		if (key == null) {
			return false;
		}
		PlayFeatures.buy(serverPlayer, key);
		sync(snapshot(serverPlayer));
		return true;
	}

	private void sync(int[] data) {
		for (int i = 0; i < PROPS; i++) {
			this.props.set(i, data != null && i < data.length ? data[i] : 0);
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
