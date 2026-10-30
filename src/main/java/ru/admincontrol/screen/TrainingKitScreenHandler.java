package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.CreativeCatalog;
import ru.admincontrol.util.TrainingConfig;

import java.util.List;

public class TrainingKitScreenHandler extends ScreenHandler {
	public static final int CREATIVE = 27;
	public static final int KIT = 41;
	public static final int INV_Y = 72;
	public static final int TAB_M = 0;
	public static final int TAB_P = 1;
	public static final int PAGE_M = 2;
	public static final int PAGE_P = 3;
	public static final int COUNT_M = 4;
	public static final int COUNT_P = 5;
	public static final int KIND_M = 6;
	public static final int KIND_P = 7;
	public static final int ENCH_M = 8;
	public static final int ENCH_P = 9;
	public static final int CLEAR = 10;
	public static final int FROM_INV = 11;
	public static final int SET_KIND = 100;
	public static final int TOGGLE_ENCH = 200;
	private static final int PROPS = 7;
	private final SimpleInventory creativeInv = new SimpleInventory(CREATIVE);
	private final SimpleInventory kitInv = new SimpleInventory(KIT);
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);
	public String kitKey = "training";

	public static TrainingKitScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new TrainingKitScreenHandler(syncId, inventory, buf.readString(32));
	}

	public TrainingKitScreenHandler(int syncId, PlayerInventory inventory, String kitKey) {
		super(ModScreenHandlers.TRAINING_KIT, syncId);
		this.kitKey = kitKey == null || kitKey.isBlank() ? "training" : kitKey;
		this.addProperties(this.props);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(this.creativeInv, col + row * 9, 8 + col * 18, 18 + row * 18) {
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
		for (int i = 0; i < 4; i++) {
			this.addSlot(new Slot(this.kitInv, 39 - i, 8, INV_Y + 8 + i * 18));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(this.kitInv, col + (row + 1) * 9, 8 + col * 18, INV_Y + 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(this.kitInv, col, 8 + col * 18, INV_Y + 142));
		}
		this.addSlot(new Slot(this.kitInv, 40, 77, INV_Y + 62));
		fillAll();
	}

	public String title() {
		return TrainingConfig.kitTitle(this.kitKey);
	}

	public int selected() {
		return this.props.get(0);
	}

	public int selCount() {
		return this.props.get(1);
	}

	public int selEnch() {
		return this.props.get(2);
	}

	public int selKind() {
		return this.props.get(3);
	}

	public int tab() {
		return this.props.get(4);
	}

	public int page() {
		return this.props.get(5);
	}

	public int pageCount() {
		return Math.max(1, this.props.get(6));
	}

	public String tabLabel() {
		return CreativeCatalog.label(tab());
	}

	public String selEnchantName() {
		return TrainingConfig.enchantLabel(TrainingConfig.enchantId(selKind()));
	}

	public int kitSlotScreenIndex(int invIndex) {
		for (int i = CREATIVE; i < this.slots.size(); i++) {
			Slot slot = this.slots.get(i);
			if (slot.inventory == this.kitInv && slot.getIndex() == invIndex) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!admin(player)) {
			return false;
		}
		if (id == TAB_M) {
			this.props.set(4, Math.floorMod(tab() - 1, CreativeCatalog.tabs()));
			this.props.set(5, 0);
			fillCreative();
			return true;
		}
		if (id == TAB_P) {
			this.props.set(4, Math.floorMod(tab() + 1, CreativeCatalog.tabs()));
			this.props.set(5, 0);
			fillCreative();
			return true;
		}
		if (id == PAGE_M) {
			this.props.set(5, Math.max(0, page() - 1));
			fillCreative();
			return true;
		}
		if (id == PAGE_P) {
			this.props.set(5, Math.min(Math.max(1, (CreativeCatalog.items(tab()).size() + CREATIVE - 1) / CREATIVE) - 1, page() + 1));
			fillCreative();
			return true;
		}
		if (id == FROM_INV) {
			var own = player.getInventory();
			for (int i = 0; i < KIT; i++) {
				ItemStack src = i < own.size() ? own.getStack(i) : ItemStack.EMPTY;
				this.kitInv.setStack(i, src.isEmpty() ? ItemStack.EMPTY : src.copy());
			}
			saveKit();
			syncSelected();
			ru.admincontrol.util.Chat.send((ServerPlayerEntity) player,
					ru.admincontrol.util.Chat.yellow("Набор заменён содержимым вашего инвентаря (с NBT и бронёй)."));
			return true;
		}
		ItemStack stack = selectedStack();
		if (id == CLEAR) {
			if (!stack.isEmpty()) {
				this.kitInv.setStack(selected(), ItemStack.EMPTY);
				saveKit();
			}
			syncSelected();
			return true;
		}
		if (id >= SET_KIND && id < SET_KIND + TrainingConfig.ENCHANTS.length) {
			this.props.set(3, id - SET_KIND);
			syncSelected();
			return true;
		}
		if (stack.isEmpty()) {
			return false;
		}
		if (id >= TOGGLE_ENCH && id < TOGGLE_ENCH + TrainingConfig.ENCHANTS.length) {
			int idx = id - TOGGLE_ENCH;
			this.props.set(3, idx);
			String encId = TrainingConfig.enchantId(idx);
			var enc = TrainingConfig.enchant(encId);
			int level = enc == null ? 0 : net.minecraft.enchantment.EnchantmentHelper.getLevel(enc, stack);
			if (level > 0) {
				TrainingConfig.applyEnchant(stack, encId, -level);
			} else {
				TrainingConfig.applyEnchant(stack, encId, 1);
			}
			this.kitInv.setStack(selected(), stack);
			saveKit();
			syncSelected();
			return true;
		}
		if (id == COUNT_M) {
			stack.setCount(Math.max(1, stack.getCount() - 1));
		} else if (id == COUNT_P) {
			stack.setCount(Math.min(stack.getMaxCount(), stack.getCount() + 1));
		} else if (id == KIND_M) {
			this.props.set(3, Math.floorMod(selKind() - 1, TrainingConfig.ENCHANTS.length));
			syncSelected();
			return true;
		} else if (id == KIND_P) {
			this.props.set(3, Math.floorMod(selKind() + 1, TrainingConfig.ENCHANTS.length));
			syncSelected();
			return true;
		} else if (id == ENCH_M) {
			TrainingConfig.applyEnchant(stack, TrainingConfig.enchantId(selKind()), -1);
		} else if (id == ENCH_P) {
			TrainingConfig.applyEnchant(stack, TrainingConfig.enchantId(selKind()), 1);
		} else {
			return false;
		}
		this.kitInv.setStack(selected(), stack);
		saveKit();
		syncSelected();
		return true;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (!admin(player)) {
			return;
		}
		if (slotIndex == -999) {
			this.setCursorStack(ItemStack.EMPTY);
			return;
		}
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}
		if (actionType == SlotActionType.SWAP) {
			return;
		}
		if (slotIndex < CREATIVE) {
			ItemStack shown = this.slots.get(slotIndex).getStack();
			if (shown.isEmpty()) {
				return;
			}
			ItemStack give = shown.copy();
			if (button == 1 && actionType == SlotActionType.PICKUP) {
				give.setCount(1);
			} else {
				give.setCount(give.getMaxCount());
			}
			this.setCursorStack(give);
			return;
		}
		Slot slot = this.slots.get(slotIndex);
		this.props.set(0, slot.getIndex());
		if (actionType == SlotActionType.CLONE) {
			if (!slot.getStack().isEmpty()) {
				ItemStack copy = slot.getStack().copy();
				copy.setCount(copy.getMaxCount());
				this.setCursorStack(copy);
			}
			syncSelected();
			return;
		}
		if (actionType == SlotActionType.THROW) {
			slot.setStack(ItemStack.EMPTY);
			saveKit();
			syncSelected();
			return;
		}
		super.onSlotClick(slotIndex, button, actionType, player);
		saveKit();
		syncSelected();
	}

	@Override
	public void onClosed(PlayerEntity player) {
		this.setCursorStack(ItemStack.EMPTY);
		if (admin(player)) {
			saveKit();
		}
		super.onClosed(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		if (!admin(player) || index < 0 || index >= this.slots.size()) {
			return ItemStack.EMPTY;
		}
		if (index < CREATIVE) {
			ItemStack shown = this.slots.get(index).getStack();
			if (!shown.isEmpty()) {
				ItemStack give = shown.copy();
				give.setCount(give.getMaxCount());
				this.setCursorStack(give);
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	private boolean admin(PlayerEntity player) {
		return player instanceof ServerPlayerEntity serverPlayer
				&& DataManager.get() != null
				&& DataManager.get().getAdminLevel(serverPlayer) >= AdminRanks.TECH;
	}

	private ItemStack selectedStack() {
		int sel = selected();
		if (sel < 0 || sel >= KIT) {
			return ItemStack.EMPTY;
		}
		return this.kitInv.getStack(sel);
	}

	private void fillAll() {
		if (DataManager.get() == null) {
			return;
		}
		TrainingConfig.readKitInventory(this.kitKey, this.kitInv);
		fillCreative();
		syncSelected();
	}

	private void fillCreative() {
		List<ItemStack> items = CreativeCatalog.items(tab());
		int pages = Math.max(1, (items.size() + CREATIVE - 1) / CREATIVE);
		int page = Math.max(0, Math.min(pages - 1, page()));
		this.props.set(5, page);
		this.props.set(6, pages);
		int start = page * CREATIVE;
		for (int i = 0; i < CREATIVE; i++) {
			int at = start + i;
			this.creativeInv.setStack(i, at < items.size() ? items.get(at).copy() : ItemStack.EMPTY);
		}
		this.creativeInv.markDirty();
		sendContentUpdates();
	}

	private void saveKit() {
		TrainingConfig.writeKitInventory(this.kitKey, this.kitInv);
		this.kitInv.markDirty();
		sendContentUpdates();
	}

	private void syncSelected() {
		ItemStack stack = selectedStack();
		this.props.set(1, stack.getCount());
		int enc = 0;
		if (!stack.isEmpty()) {
			var map = net.minecraft.enchantment.EnchantmentHelper.get(stack);
			var target = ru.admincontrol.util.TrainingConfig.enchant(TrainingConfig.enchantId(selKind()));
			if (target != null) {
				enc = map.getOrDefault(target, 0);
			}
		}
		this.props.set(2, enc);
		sendContentUpdates();
	}
}
