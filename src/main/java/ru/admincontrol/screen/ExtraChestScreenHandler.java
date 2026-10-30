package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class ExtraChestScreenHandler extends ScreenHandler {
	public final int rows;
	private final SimpleInventory chest;
	private final boolean canPut;
	private final boolean canTake;
	private Runnable onClose = () -> {
	};

	public static ExtraChestScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int rows = Math.max(1, Math.min(6, buf.readVarInt()));
		boolean viewOnly = buf.isReadable() && buf.readBoolean();
		boolean put = !viewOnly;
		boolean take = !viewOnly;
		if (buf.isReadable(2)) {
			put = buf.readBoolean();
			take = buf.readBoolean();
		}
		return new ExtraChestScreenHandler(syncId, inventory, new SimpleInventory(rows * 9), rows, put, take, () -> {
		});
	}

	public ExtraChestScreenHandler(int syncId, PlayerInventory inventory, SimpleInventory chest, int rows, Runnable onClose) {
		this(syncId, inventory, chest, rows, false, onClose);
	}

	public ExtraChestScreenHandler(int syncId, PlayerInventory inventory, SimpleInventory chest, int rows, boolean viewOnly, Runnable onClose) {
		this(syncId, inventory, chest, rows, !viewOnly, !viewOnly, onClose);
	}

	public ExtraChestScreenHandler(int syncId, PlayerInventory inventory, SimpleInventory chest, int rows,
			boolean canPut, boolean canTake, Runnable onClose) {
		super(ModScreenHandlers.EXTRA_CHEST, syncId);
		this.rows = Math.max(1, Math.min(6, rows));
		this.chest = chest;
		this.canPut = canPut;
		this.canTake = canTake;
		this.onClose = onClose == null ? () -> {
		} : onClose;
		int chestY = 18;
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				int x = 8 + col * 18;
				int y = chestY + row * 18;
				this.addSlot(canPut && canTake ? new Slot(chest, index, x, y) : new LockedSlot(chest, index, x, y, canPut, canTake));
			}
		}
		int invY = chestY + this.rows * 18 + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, invY + 58));
		}
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.onClose.run();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();
			int chestSize = this.rows * 9;
			if (index < chestSize ? !this.canTake : !this.canPut) {
				return ItemStack.EMPTY;
			}
			if (index < chestSize) {
				if (!this.insertItem(stack, chestSize, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.insertItem(stack, 0, chestSize, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return copy;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	private static final class LockedSlot extends Slot {
		private final boolean put;
		private final boolean take;

		LockedSlot(net.minecraft.inventory.Inventory inventory, int index, int x, int y, boolean put, boolean take) {
			super(inventory, index, x, y);
			this.put = put;
			this.take = take;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return this.put;
		}

		@Override
		public boolean canTakeItems(PlayerEntity playerEntity) {
			return this.take;
		}
	}
}
