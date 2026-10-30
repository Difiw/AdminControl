package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SellScreenHandler extends ScreenHandler {
	public static final int DUMP_ALL = -323;
	public static final int DUMP_TYPE = -1;

	public int previewLapis;
	private final int[] selected = new int[36];
	private final Set<String> extraBanned = new HashSet<>();
	private int lastInv = -1;

	public static SellScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int preview = buf.readInt();
		int n = buf.readVarInt();
		List<String> bans = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			bans.add(buf.readString(128));
		}
		return new SellScreenHandler(syncId, inventory, preview, bans);
	}

	public static void writeOpening(PacketByteBuf buf) {
		buf.writeInt(0);
		List<String> bans = List.of();
		DataManager data = DataManager.get();
		if (data != null && data.settings().sellBanned != null) {
			bans = data.settings().sellBanned;
		}
		buf.writeVarInt(bans.size());
		for (String id : bans) {
			buf.writeString(id == null ? "" : id, 128);
		}
	}

	public SellScreenHandler(int syncId, PlayerInventory inventory, int preview) {
		this(syncId, inventory, preview, List.of());
	}

	public SellScreenHandler(int syncId, PlayerInventory inventory, int preview, Collection<String> bans) {
		super(ModScreenHandlers.SELL, syncId);
		this.previewLapis = preview;
		if (bans != null) {
			for (String id : bans) {
				if (id != null && !id.isBlank()) {
					this.extraBanned.add(id);
				}
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 31 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 89));
		}
	}

	public int selectedCount(int inventoryIndex) {
		if (inventoryIndex < 0 || inventoryIndex >= selected.length) {
			return 0;
		}
		return selected[inventoryIndex];
	}

	public boolean sellForbidden(Item item) {
		return !Economy.canSell(item, this.extraBanned);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (Economy.sellBlocked(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red("/sell недоступен на мероприятии, тренировке и в битве кланов."));
			serverPlayer.closeHandledScreen();
			return true;
		}
		if (id == 0) {
			serverPlayer.closeHandledScreen();
			return true;
		}
		if (id == 1) {
			int lapis = 0;
			for (int i = 0; i < 36; i++) {
				ItemStack stack = player.getInventory().getStack(i);
				int count = Math.min(selected[i], stack.getCount());
				if (count <= 0 || stack.isEmpty() || sellForbidden(stack.getItem())) {
					continue;
				}
				lapis += Economy.priceOf(stack.getItem()) * count;
				stack.decrement(count);
			}
			player.getInventory().markDirty();
			Economy.giveCurrency(serverPlayer, lapis, "продажа предметов серверу");
			Chat.send(serverPlayer, Chat.green("Продано серверу. Получено: " + Money.wc(lapis) + "."));
			serverPlayer.closeHandledScreen();
			return true;
		}
		return false;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && Economy.sellBlocked(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red("/sell недоступен на мероприятии, тренировке и в битве кланов."));
			serverPlayer.closeHandledScreen();
			return;
		}
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}
		Slot slot = this.slots.get(slotIndex);
		int inv = slot.getIndex();
		if (inv < 0 || inv >= 36) {
			return;
		}
		ItemStack stack = player.getInventory().getStack(inv);
		if (stack.isEmpty()) {
			selected[inv] = 0;
			recalc(player);
			return;
		}
		this.lastInv = inv;
		if (sellForbidden(stack.getItem())) {
			selected[inv] = 0;
			recalc(player);
			return;
		}
		if (button == 1) {
			selected[inv] = Math.max(0, selected[inv] - 1);
		} else {
			selected[inv] = Math.min(stack.getCount(), selected[inv] + 1);
		}
		recalc(player);
	}

	public void setCountFromText(PlayerEntity player, String raw) {
		if (player == null) {
			return;
		}
		int count = ru.admincontrol.util.UiValues.parseInt(raw, Integer.MIN_VALUE);
		if (count == Integer.MIN_VALUE) {
			return;
		}
		if (count == DUMP_ALL) {
			selectWholeInventory(player);
			recalc(player);
			return;
		}
		if (this.lastInv < 0 || this.lastInv >= 36) {
			return;
		}
		ItemStack focus = player.getInventory().getStack(this.lastInv);
		if (focus.isEmpty() || sellForbidden(focus.getItem())) {
			return;
		}
		Item type = focus.getItem();
		int have = countOf(player, type);
		if (count == DUMP_TYPE || count > have) {
			selectAllOfType(player, type);
		} else if (count <= 0) {
			clearType(player, type);
		} else {
			selectAmountOfType(player, type, count);
		}
		recalc(player);
	}

	private int countOf(PlayerEntity player, Item type) {
		int total = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (!stack.isEmpty() && stack.isOf(type) && !sellForbidden(type)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private void selectWholeInventory(PlayerEntity player) {
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty() || sellForbidden(stack.getItem())) {
				selected[i] = 0;
			} else {
				selected[i] = stack.getCount();
			}
		}
	}

	private void clearType(PlayerEntity player, Item type) {
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (!stack.isEmpty() && stack.isOf(type)) {
				selected[i] = 0;
			}
		}
	}

	private void selectAllOfType(PlayerEntity player, Item type) {
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty() || !stack.isOf(type) || sellForbidden(type)) {
				if (!stack.isEmpty() && stack.isOf(type)) {
					selected[i] = 0;
				}
				continue;
			}
			selected[i] = stack.getCount();
		}
	}

	private void selectAmountOfType(PlayerEntity player, Item type, int want) {
		int left = Math.max(0, want);
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty() || !stack.isOf(type) || sellForbidden(type)) {
				if (!stack.isEmpty() && stack.isOf(type)) {
					selected[i] = 0;
				}
				continue;
			}
			int take = Math.min(stack.getCount(), left);
			selected[i] = take;
			left -= take;
		}
	}

	private void recalc(PlayerEntity player) {
		int total = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			int count = Math.min(selected[i], stack.getCount());
			if (count > 0 && !stack.isEmpty() && !sellForbidden(stack.getItem())) {
				total += Economy.priceOf(stack.getItem()) * count;
			}
		}
		this.previewLapis = total;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return !(player instanceof ServerPlayerEntity serverPlayer) || !Economy.sellBlocked(serverPlayer);
	}
}
