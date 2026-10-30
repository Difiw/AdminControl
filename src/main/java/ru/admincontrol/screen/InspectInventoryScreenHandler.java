package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.InventoryUtil;

import java.util.UUID;

/**
 * Слоты как у экрана инвентаря (клавиша E): броня, основная часть, хотбар, вторая рука.
 */
public class InspectInventoryScreenHandler extends ScreenHandler {
	private final Inventory targetInventory;
	private final UUID targetUuid;
	private final ServerPlayerEntity targetPlayer;
	private final PlayerRecord offlineRecord;

	public static InspectInventoryScreenHandler createClient(int syncId, PlayerInventory viewerInv, PacketByteBuf buf) {
		UUID uuid = buf.readUuid();
		return new InspectInventoryScreenHandler(syncId, viewerInv, new SimpleInventory(41), uuid, null, null);
	}

	public InspectInventoryScreenHandler(int syncId, PlayerInventory viewerInv, ServerPlayerEntity target) {
		this(syncId, viewerInv, target.getInventory(), target.getUuid(), target, null);
	}

	public InspectInventoryScreenHandler(int syncId, PlayerInventory viewerInv, PlayerRecord record) {
		this(syncId, viewerInv, InventoryUtil.inventoryFromNbt(record == null ? "" : record.inventoryNbt),
				record == null || record.uuidOrNull() == null ? new UUID(0, 0) : record.uuidOrNull(), null, record);
	}

	private InspectInventoryScreenHandler(int syncId, PlayerInventory viewerInv, Inventory targetInventory,
			UUID targetUuid, ServerPlayerEntity targetPlayer, PlayerRecord offlineRecord) {
		super(ModScreenHandlers.INSPECT, syncId);
		this.targetInventory = targetInventory;
		this.targetUuid = targetUuid;
		this.targetPlayer = targetPlayer;
		this.offlineRecord = offlineRecord;
		addPlayerStyleSlots(targetInventory);
	}

	private void addPlayerStyleSlots(Inventory inventory) {
		for (int i = 0; i < 4; i++) {
			this.addSlot(new ViewSlot(inventory, 39 - i, 8, 8 + i * 18));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new ViewSlot(inventory, col + (row + 1) * 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new ViewSlot(inventory, col, 8 + col * 18, 142));
		}
		this.addSlot(new ViewSlot(inventory, 40, 77, 62));
	}

	/** Только просмотр: предметы нельзя взять, положить, обменять или выбросить. */
	private static final class ViewSlot extends Slot {
		ViewSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public boolean canTakeItems(PlayerEntity player) {
			return false;
		}

		@Override
		public ItemStack takeStack(int amount) {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (!player.getWorld().isClient) {
			this.syncState();
		}
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return false;
	}

	@Override
	public boolean canInsertIntoSlot(Slot slot) {
		return false;
	}

	public UUID getTargetUuid() {
		return targetUuid;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id != 0 || !(player instanceof ServerPlayerEntity viewer) || DataManager.get() == null) {
			return false;
		}
		PlayerRecord rec = targetPlayer != null
				? DataManager.get().getOrCreate(targetPlayer)
				: offlineRecord;
		if (rec == null || rec.backpackLevel <= 0) {
			return true;
		}
		ru.admincontrol.util.ScreenNav.replace(viewer, new BackpackFactory(rec));
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (player.getWorld().isClient) {
			return true;
		}
		DataManager data = DataManager.get();
		if (!(player instanceof ServerPlayerEntity viewer) || data == null || !data.isAdmin(viewer)) {
			return false;
		}
		return targetPlayer != null && !targetPlayer.isRemoved() || offlineRecord != null;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}
}
