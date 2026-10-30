package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.util.CreativeCatalog;
import ru.admincontrol.util.Economy;

import java.util.List;

public class SellBanScreenHandler extends ScreenHandler {
	public static final int PAGE_SIZE = 45;
	public static final String BAN_KEY = "AdminControlBan";
	private final SimpleInventory items = new SimpleInventory(PAGE_SIZE);
	private final PropertyDelegate props = new ArrayPropertyDelegate(3);
	private int tab;
	private int page;
	private List<ItemStack> catalog = CreativeCatalog.items(0);

	public static SellBanScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new SellBanScreenHandler(syncId, inventory);
	}

	public SellBanScreenHandler(int syncId, PlayerInventory inventory) {
		super(ModScreenHandlers.SELL_BAN, syncId);
		this.addProperties(this.props);
		for (int row = 0; row < 5; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(items, col + row * 9, 8 + col * 18, 36 + row * 18) {
					@Override
					public boolean canInsert(ItemStack stack) {
						return false;
					}

					@Override
					public boolean canTakeItems(PlayerEntity playerEntity) {
						return false;
					}
				});
			}
		}
		fill();
	}

	public int tab() {
		return props.get(0);
	}

	public int pageIndex() {
		return props.get(1) + 1;
	}

	public int pageCount() {
		return Math.max(1, props.get(2));
	}

	private void fill() {
		this.catalog = CreativeCatalog.items(this.tab);
		int pages = Math.max(1, (catalog.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		this.page = Math.min(this.page, pages - 1);
		props.set(0, this.tab);
		props.set(1, this.page);
		props.set(2, pages);
		int from = this.page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = from + i;
			if (index >= catalog.size()) {
				items.setStack(i, ItemStack.EMPTY);
				continue;
			}
			ItemStack stack = catalog.get(index).copy();
			NbtCompound nbt = stack.getOrCreateNbt();
			nbt.putBoolean(BAN_KEY, !Economy.canSell(stack.getItem()));
			items.setStack(i, stack);
		}
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == 0) {
			this.tab = Math.floorMod(this.tab - 1, CreativeCatalog.tabs());
			this.page = 0;
		} else if (id == 1) {
			this.tab = Math.floorMod(this.tab + 1, CreativeCatalog.tabs());
			this.page = 0;
		} else if (id == 2) {
			this.page = Math.max(0, this.page - 1);
		} else if (id == 3) {
			this.page++;
			if (this.page * PAGE_SIZE >= catalog.size()) {
				this.page = 0;
			}
		} else {
			return false;
		}
		fill();
		return true;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity) || slotIndex < 0 || slotIndex >= PAGE_SIZE) {
			return;
		}
		ItemStack stack = this.slots.get(slotIndex).getStack();
		if (stack.isEmpty()) {
			return;
		}
		Economy.toggleSellBan(stack.getItem());
		fill();
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
