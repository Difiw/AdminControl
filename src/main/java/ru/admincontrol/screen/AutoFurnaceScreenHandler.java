package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import ru.admincontrol.util.FurnaceService;

public class AutoFurnaceScreenHandler extends ScreenHandler {
	public static final int HEADER = 17;
	public static final int INPUT_X = 44;
	public static final int OUTPUT_X = 116;
	public static final int PROPS = FurnaceService.MAX_FURNACES + 2;
	public final int furnaces;
	private final PropertyDelegate props;

	public static AutoFurnaceScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int count = Math.max(1, Math.min(FurnaceService.MAX_FURNACES, buf.readVarInt()));
		return new AutoFurnaceScreenHandler(syncId, inventory, new SimpleInventory(FurnaceService.SLOTS),
				new ArrayPropertyDelegate(PROPS), count);
	}

	public AutoFurnaceScreenHandler(int syncId, PlayerInventory inventory, Inventory furnace, PropertyDelegate props, int count) {
		super(ModScreenHandlers.AUTO_FURNACE, syncId);
		this.furnaces = Math.max(1, Math.min(FurnaceService.MAX_FURNACES, count));
		this.props = props;
		this.addProperties(props);
		for (int i = 0; i < this.furnaces; i++) {
			int y = rowY(i, this.furnaces);
			this.addSlot(new Slot(furnace, i * 2, INPUT_X, y));
			this.addSlot(new OutputSlot(furnace, i * 2 + 1, OUTPUT_X, y));
		}
		int invY = top(this.furnaces) + 15;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, invY + 58));
		}
	}

	public static int area(int furnaces) {
		return furnaces >= 3 ? 82 : furnaces == 2 ? 56 : 36;
	}

	public static int top(int furnaces) {
		return HEADER + area(furnaces);
	}

	public static int rowY(int index, int furnaces) {
		return furnaces >= 2 ? HEADER + 6 + index * 26 : HEADER + 10;
	}

	public int progress(int furnace) {
		return this.props.get(Math.max(0, Math.min(FurnaceService.MAX_FURNACES - 1, furnace)));
	}

	public int interval() {
		return Math.max(1, this.props.get(FurnaceService.MAX_FURNACES));
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack copy = stack.copy();
		int own = this.furnaces * 2;
		if (index < own) {
			if (!this.insertItem(stack, own, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < this.furnaces && !stack.isEmpty(); i++) {
				moved |= this.insertItem(stack, i * 2, i * 2 + 1, false);
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return copy;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	private static final class OutputSlot extends Slot {
		OutputSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}
	}
}
