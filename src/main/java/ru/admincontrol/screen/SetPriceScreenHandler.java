package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.AuctionService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.CreativeCatalog;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.List;

public class SetPriceScreenHandler extends ScreenHandler {
	public static final int HEADER = 22;
	public static final int SIDE = 52;
	public static final int ROWS = 5;
	public static final int PAGE_SIZE = ROWS * 9;
	public static final String SELL_KEY = "AdminControlSell";
	public static final String AMIN_KEY = "AdminControlAMin";
	public static final String AMAX_KEY = "AdminControlAMax";

	private final SimpleInventory items = new SimpleInventory(PAGE_SIZE);
	private final List<ItemStack> catalog = new ArrayList<>();
	private final PropertyDelegate props = new ArrayPropertyDelegate(5);
	private int tab;
	private int page;

	public static SetPriceScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new SetPriceScreenHandler(syncId, inventory);
	}

	public SetPriceScreenHandler(int syncId, PlayerInventory inventory) {
		super(ModScreenHandlers.SET_PRICE, syncId);
		this.items.addListener(inv -> this.sendContentUpdates());
		this.addProperties(props);
		this.catalog.addAll(CreativeCatalog.items(0));
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(items, col + row * 9, SIDE + 8 + col * 18, 18 + row * 18) {
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
		int invY = 103 + (ROWS - 4) * 18;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, SIDE + 8 + col * 18, invY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, SIDE + 8 + col * 18, 161 + (ROWS - 4) * 18));
		}
		syncProps();
		fillPage();
	}

	public int minPercent() {
		return props.get(1);
	}

	public int maxPercent() {
		return props.get(2);
	}

	public int pageIndex() {
		return props.get(0) + 1;
	}

	public int tab() {
		return props.get(4);
	}

	public int pageCount() {
		return Math.max(1, props.get(3));
	}

	private void syncProps() {
		int pages = Math.max(1, (catalog.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		props.set(0, page);
		props.set(1, Economy.auctionMinPercent());
		props.set(2, Economy.auctionMaxPercent());
		props.set(3, pages);
		props.set(4, tab);
	}

	private void reload() {
		catalog.clear();
		catalog.addAll(CreativeCatalog.items(tab));
		fillPage();
	}

	private void fillPage() {
		syncProps();
		int from = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = from + i;
			if (index >= catalog.size()) {
				items.setStack(i, ItemStack.EMPTY);
				continue;
			}
			ItemStack stack = catalog.get(index).copy();
			int sell = Economy.priceOf(stack.getItem());
			NbtCompound nbt = stack.getOrCreateNbt();
			nbt.putInt(SELL_KEY, sell);
			nbt.putInt(AMIN_KEY, Economy.auctionMin(stack.getItem()));
			nbt.putInt(AMAX_KEY, Economy.auctionMax(stack.getItem()));
			items.setStack(i, stack);
		}
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		return switch (id) {
			case 0 -> {
				page = Math.max(0, page - 1);
				fillPage();
				yield true;
			}
			case 1 -> {
				page++;
				if (page * PAGE_SIZE >= catalog.size()) {
					page = 0;
				}
				fillPage();
				yield true;
			}
			case 2 -> adjustDisplayed(-1, player);
			case 3 -> adjustDisplayed(1, player);
			case 4 -> changeMin(-1, player);
			case 5 -> changeMin(1, player);
			case 6 -> changeMin(-10, player);
			case 7 -> changeMin(10, player);
			case 8 -> changeMax(-1, player);
			case 9 -> changeMax(1, player);
			case 10 -> changeMax(-10, player);
			case 11 -> changeMax(10, player);
			case 12 -> adjustDisplayed(-10, player);
			case 13 -> adjustDisplayed(10, player);
			case 14 -> {
				tab = Math.floorMod(tab - 1, CreativeCatalog.tabs());
				page = 0;
				reload();
				yield true;
			}
			case 15 -> {
				tab = Math.floorMod(tab + 1, CreativeCatalog.tabs());
				page = 0;
				reload();
				yield true;
			}
			case 16 -> adjustSection(-10, player);
			case 17 -> adjustSection(-1, player);
			case 18 -> adjustSection(1, player);
			case 19 -> adjustSection(10, player);
			default -> false;
		};
	}

	private boolean changeMin(int delta, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		Economy.setAuctionMinPercent(Economy.auctionMinPercent() + delta);
		AuctionService.clampAllPrices();
		Chat.send(serverPlayer, Chat.yellow("Аукцион мин: " + Economy.auctionMinPercent()
				+ "%  макс: " + Economy.auctionMaxPercent() + "% от /sell"));
		fillPage();
		return true;
	}

	private boolean changeMax(int delta, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		Economy.setAuctionMaxPercent(Economy.auctionMaxPercent() + delta);
		AuctionService.clampAllPrices();
		Chat.send(serverPlayer, Chat.yellow("Аукцион мин: " + Economy.auctionMinPercent()
				+ "%  макс: " + Economy.auctionMaxPercent() + "% от /sell"));
		fillPage();
		return true;
	}

	private boolean adjustSection(int delta, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		int changed = 0;
		for (ItemStack stack : catalog) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			Economy.setPrice(item, Math.max(0, Economy.priceOf(item) + delta), false);
			changed++;
		}
		if (changed > 0 && DataManager.get() != null) {
			DataManager.get().saveAll();
			AuctionService.clampAllPrices();
			Chat.send(serverPlayer, Chat.yellow("Раздел «" + CreativeCatalog.label(tab) + "»: " + changed
					+ " предметов, /sell " + (delta > 0 ? "+" : "") + delta + " WC/шт."));
		}
		fillPage();
		return true;
	}

	private boolean adjustDisplayed(int delta, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		int from = page * PAGE_SIZE;
		int changed = 0;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = from + i;
			if (index >= catalog.size()) {
				break;
			}
			Item item = catalog.get(index).getItem();
			Economy.setPrice(item, Math.max(0, Economy.priceOf(item) + delta), false);
			changed++;
		}
		if (changed > 0 && DataManager.get() != null) {
			DataManager.get().saveAll();
			AuctionService.clampAllPrices();
			Chat.send(serverPlayer, Chat.yellow("Раздел: " + changed + " предметов, /sell "
					+ (delta > 0 ? "+" : "") + Money.format(delta) + " WC/шт."));
		}
		fillPage();
		return true;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity) || slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}
		ItemStack stack = this.slots.get(slotIndex).getStack();
		if (stack.isEmpty()) {
			return;
		}
		int current = Economy.priceOf(stack.getItem());
		int next = button == 1 ? Math.max(0, current - 1) : current + 1;
		Economy.setPrice(stack.getItem(), next);
		AuctionService.clampAllPrices();
		if (slotIndex < PAGE_SIZE) {
			fillPage();
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
