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
import ru.admincontrol.data.AuctionListing;
import ru.admincontrol.util.AuctionService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.ScreenNav;

import java.util.ArrayList;
import java.util.List;

public class AuctionScreenHandler extends ScreenHandler {
	public static final String PRICE_KEY = "AdminControlPrice";
	public static final String SELLER_KEY = "AdminControlSeller";
	public static final String MIN_KEY = "AdminControlMin";
	public static final String MAX_KEY = "AdminControlMax";
	public static final String OWN_KEY = "AdminControlOwn";
	private static final int PAGE_SIZE = 27;

	private final SimpleInventory listings = new SimpleInventory(PAGE_SIZE);
	private final List<String> ids = new ArrayList<>();
	private final PropertyDelegate props = new ArrayPropertyDelegate(2);
	private final PlayerEntity viewer;
	private int page;

	public static AuctionScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new AuctionScreenHandler(syncId, inventory);
	}

	public AuctionScreenHandler(int syncId, PlayerInventory inventory) {
		super(ModScreenHandlers.AUCTION, syncId);
		this.viewer = inventory.player;
		this.addProperties(props);
		this.listings.addListener(inv -> this.sendContentUpdates());
		refresh();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(listings, col + row * 9, 8 + col * 18, 18 + row * 18) {
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
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 143));
		}
	}

	public boolean showPageButtons() {
		return props.get(1) > 1;
	}

	private void refresh() {
		ids.clear();
		for (int i = 0; i < PAGE_SIZE; i++) {
			listings.setStack(i, ItemStack.EMPTY);
		}
		List<AuctionListing> all = AuctionService.listings();
		int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		if (page >= pages) {
			page = Math.max(0, pages - 1);
		}
		props.set(0, page);
		props.set(1, pages);
		int from = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && from + i < all.size(); i++) {
			AuctionListing listing = all.get(from + i);
			ItemStack stack = AuctionService.stackOf(listing);
			if (!stack.isEmpty()) {
				NbtCompound nbt = stack.getOrCreateNbt();
				nbt.putInt(PRICE_KEY, listing.pricePerItem);
				nbt.putString(SELLER_KEY, listing.sellerNick == null ? "" : listing.sellerNick);
				nbt.putInt(MIN_KEY, Economy.auctionMin(stack.getItem()));
				nbt.putInt(MAX_KEY, Economy.auctionMax(stack.getItem()));
				nbt.putBoolean(OWN_KEY, playerOwns(listing));
			}
			listings.setStack(i, stack);
			ids.add(listing.id);
		}
	}

	private boolean playerOwns(AuctionListing listing) {
		if (viewer == null || listing == null) {
			return false;
		}
		return viewer.getUuid().toString().equalsIgnoreCase(listing.sellerUuid)
				|| viewer.getGameProfile().getName().equalsIgnoreCase(listing.sellerNick);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		int pages = Math.max(1, props.get(1));
		if (id == 0) {
			page = page <= 0 ? pages - 1 : page - 1;
			refresh();
			return true;
		}
		if (id == 1) {
			page = page + 1 >= pages ? 0 : page + 1;
			refresh();
			return true;
		}
		return false;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0) {
			return;
		}
		if (Economy.sellBlocked(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red(Economy.tradeBlocked(serverPlayer)));
			return;
		}
		if (slotIndex < PAGE_SIZE) {
			if (slotIndex >= ids.size()) {
				return;
			}
			String listingId = ids.get(slotIndex);
			AuctionListing listing = AuctionService.listings().stream()
					.filter(l -> l.id.equals(listingId)).findFirst().orElse(null);
			if (listing == null) {
				return;
			}
			boolean own = (listing.sellerNick != null
					&& listing.sellerNick.equalsIgnoreCase(serverPlayer.getGameProfile().getName()))
					|| (listing.sellerUuid != null
					&& listing.sellerUuid.equalsIgnoreCase(serverPlayer.getUuid().toString()));
			if (own) {
				if (actionType == SlotActionType.QUICK_MOVE || actionType == SlotActionType.THROW) {
					AuctionService.cancel(serverPlayer, listingId);
					Chat.send(serverPlayer, Chat.yellow("Лот снят с аукциона."));
					refresh();
				} else {
					int delta = button == 1 ? -1 : 1;
					AuctionService.adjustOwnPrice(serverPlayer, listingId, delta);
					refresh();
				}
			} else {
				ScreenNav.open(serverPlayer, new AuctionBuyFactory(listingId));
			}
			return;
		}
		Slot slot = this.slots.get(slotIndex);
		ItemStack stack = slot.getStack();
		if (stack.isEmpty() || slot.inventory != serverPlayer.getInventory()) {
			return;
		}
		ScreenNav.open(serverPlayer, new AuctionOfferFactory(slot.getIndex()));
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return !(player instanceof ServerPlayerEntity sp) || !Economy.sellBlocked(sp);
	}
}
