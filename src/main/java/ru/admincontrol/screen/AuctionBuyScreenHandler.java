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
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.util.AuctionService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.ScreenNav;

public class AuctionBuyScreenHandler extends ScreenHandler {
	public static final int QTY_MINUS_10 = 0;
	public static final int QTY_MINUS = 1;
	public static final int QTY_PLUS = 2;
	public static final int QTY_PLUS_10 = 3;
	public static final int QTY_ALL = 4;
	public static final int CONFIRM = 5;
	public static final int CANCEL = 6;
	private static final int PROPS = 4;

	private final SimpleInventory preview = new SimpleInventory(1);
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);
	private final String listingId;
	private int pricePerItem;
	private int qty;
	private int maxQty;
	private boolean done;

	public static AuctionBuyScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String id = buf.readString();
		ItemStack stack = buf.readItemStack();
		int price = buf.readVarInt();
		int max = buf.readVarInt();
		buf.readString();
		return new AuctionBuyScreenHandler(syncId, id, stack, price, max);
	}

	public AuctionBuyScreenHandler(int syncId, String listingId) {
		this(syncId, listingId, preview(listingId));
	}

	private AuctionBuyScreenHandler(int syncId, String listingId, Preview preview) {
		this(syncId, listingId, preview.stack, preview.price, preview.max);
	}

	private static Preview preview(String listingId) {
		ru.admincontrol.data.AuctionListing listing = AuctionService.find(listingId);
		ItemStack stack = AuctionService.stackOf(listing);
		return new Preview(stack, listing == null ? 0 : listing.pricePerItem, Math.max(1, stack.getCount()));
	}

	private record Preview(ItemStack stack, int price, int max) {
	}

	public AuctionBuyScreenHandler(int syncId, String listingId, ItemStack stack, int pricePerItem, int maxQty) {
		super(ModScreenHandlers.AUCTION_BUY, syncId);
		this.listingId = listingId;
		this.pricePerItem = Math.max(0, pricePerItem);
		this.maxQty = Math.max(1, maxQty);
		this.qty = 1;
		this.preview.setStack(0, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		this.addProperties(this.props);
		this.addSlot(new Slot(this.preview, 0, 80, 20) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return false;
			}

			@Override
			public boolean canTakeItems(PlayerEntity playerEntity) {
				return false;
			}
		});
		sync();
	}

	public int pricePerItem() {
		return this.props.get(0);
	}

	public int qty() {
		return this.props.get(1);
	}

	public int maxQty() {
		return this.props.get(2);
	}

	public int total() {
		return this.props.get(3);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (Economy.sellBlocked(serverPlayer) && id != CANCEL) {
			Chat.send(serverPlayer, Chat.red(Economy.tradeBlocked(serverPlayer)));
			return true;
		}
		if (id == CANCEL) {
			this.done = true;
			ScreenNav.back(serverPlayer);
			return true;
		}
		if (id == CONFIRM) {
			if (!AuctionService.buy(serverPlayer, this.listingId, this.qty)) {
				if (Economy.balance(serverPlayer) < this.pricePerItem * this.qty) {
					Chat.send(serverPlayer, Chat.red("Недостаточно World coin."));
					return true;
				}
				Chat.send(serverPlayer, Chat.red("Лот больше недоступен."));
				this.done = true;
				ScreenNav.back(serverPlayer);
				return true;
			}
			Chat.send(serverPlayer, Chat.green("Куплено " + this.qty + " шт. за " + (this.pricePerItem * this.qty) + " WC."));
			this.done = true;
			ScreenNav.back(serverPlayer);
			return true;
		}
		if (id == QTY_MINUS_10) {
			this.qty -= 10;
		} else if (id == QTY_MINUS) {
			this.qty -= 1;
		} else if (id == QTY_PLUS) {
			this.qty += 1;
		} else if (id == QTY_PLUS_10) {
			this.qty += 10;
		} else if (id == QTY_ALL) {
			this.qty = this.maxQty;
		} else {
			return false;
		}
		this.qty = Math.max(1, Math.min(this.maxQty, this.qty));
		sync();
		return true;
	}

	private void sync() {
		this.props.set(0, this.pricePerItem);
		this.props.set(1, this.qty);
		this.props.set(2, this.maxQty);
		this.props.set(3, this.pricePerItem * this.qty);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return !this.done && (!(player instanceof ServerPlayerEntity sp) || !Economy.sellBlocked(sp));
	}
}
