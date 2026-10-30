package ru.admincontrol.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.InventoryUtil;

/**
 * Сундук 6×9, слоты 0–40 — живой инвентарь цели (основной, броня, вторая рука).
 */
public final class InspectedPlayerInventory implements Inventory {
	public static final int SIZE = 54;

	private final ServerPlayerEntity viewer;
	private final ServerPlayerEntity target;

	public InspectedPlayerInventory(ServerPlayerEntity viewer, ServerPlayerEntity target) {
		this.viewer = viewer;
		this.target = target;
	}

	private boolean live(int slot) {
		return slot >= 0 && slot < target.getInventory().size();
	}

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return target.getInventory().isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return live(slot) ? target.getInventory().getStack(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		if (!live(slot) || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack split = target.getInventory().removeStack(slot, amount);
		markDirty();
		return split;
	}

	@Override
	public ItemStack removeStack(int slot) {
		if (!live(slot)) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = target.getInventory().removeStack(slot);
		markDirty();
		return removed;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (!live(slot)) {
			return;
		}
		target.getInventory().setStack(slot, stack);
		markDirty();
	}

	@Override
	public void markDirty() {
		target.getInventory().markDirty();
		target.playerScreenHandler.sendContentUpdates();
		if (target.currentScreenHandler != null && target.currentScreenHandler != target.playerScreenHandler) {
			target.currentScreenHandler.sendContentUpdates();
		}
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		if (player != viewer || viewer.isRemoved() || target.isRemoved()) {
			return false;
		}
		DataManager data = DataManager.get();
		return data != null && data.isAdmin(viewer);
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return live(slot);
	}

	@Override
	public void clear() {
		target.getInventory().clear();
		markDirty();
	}

	@Override
	public void onClose(PlayerEntity player) {
		DataManager data = DataManager.get();
		if (data == null || target.isRemoved()) {
			return;
		}
		PlayerRecord record = data.getOrCreate(target);
		InventoryUtil.snapshot(target, record);
		data.saveAll();
	}
}
