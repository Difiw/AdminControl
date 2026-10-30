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

public class AuctionOfferScreenHandler extends ScreenHandler {
	public static final int PRICE_MINUS_10 = 0;
	public static final int PRICE_MINUS = 1;
	public static final int PRICE_PLUS = 2;
	public static final int PRICE_PLUS_10 = 3;
	public static final int QTY_MINUS_10 = 4;
	public static final int QTY_MINUS = 5;
	public static final int QTY_PLUS = 6;
	public static final int QTY_PLUS_10 = 7;
	public static final int QTY_ALL = 8;
	public static final int CONFIRM = 9;
	public static final int CANCEL = 10;
	private static final int PROPS = 5;

	private final SimpleInventory preview = new SimpleInventory(1);
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);
	private final int invSlot;
	private int price;
	private int qty;
	private int minPrice;
	private int maxPrice;
	private int maxQty;
	private boolean done;

	public static AuctionOfferScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int slot = buf.readVarInt();
		ItemStack stack = buf.readItemStack();
		int min = buf.readVarInt();
		int max = buf.readVarInt();
		int price = buf.readVarInt();
		int qty = buf.readVarInt();
		return new AuctionOfferScreenHandler(syncId, inventory, slot, stack, min, max, price, qty);
	}

	public AuctionOfferScreenHandler(int syncId, PlayerInventory inventory, int invSlot) {
		this(syncId, inventory, invSlot, inventory.getStack(invSlot).copy(),
				inventory.getStack(invSlot).isEmpty() ? 1 : Economy.auctionMin(inventory.getStack(invSlot).getItem()),
				inventory.getStack(invSlot).isEmpty() ? 1 : Economy.auctionMax(inventory.getStack(invSlot).getItem()),
				inventory.getStack(invSlot).isEmpty() ? 1 : Economy.auctionMin(inventory.getStack(invSlot).getItem()),
				inventory.getStack(invSlot).getCount());
	}

	public AuctionOfferScreenHandler(int syncId, PlayerInventory inventory, int invSlot, ItemStack stack,
			int min, int max, int price, int qty) {
		super(ModScreenHandlers.AUCTION_OFFER, syncId);
		this.invSlot = invSlot;
		this.minPrice = Math.max(1, min);
		this.maxPrice = Math.max(this.minPrice, max);
		this.price = Economy.clampAuctionPrice(stack.getItem(), price);
		this.maxQty = Math.max(1, qty);
		this.qty = this.maxQty;
		this.preview.setStack(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
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

	public int price() {
		return this.props.get(0);
	}

	public int qty() {
		return this.props.get(1);
	}

	public int minPrice() {
		return this.props.get(2);
	}

	public int maxPrice() {
		return this.props.get(3);
	}

	public int maxQty() {
		return this.props.get(4);
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
		ItemStack template = this.preview.getStack(0);
		if (id == CANCEL) {
			this.done = true;
			ScreenNav.back(serverPlayer);
			return true;
		}
		if (id == CONFIRM) {
			ItemStack listed = AuctionService.takeMatching(serverPlayer, template, this.qty);
			if (listed.isEmpty()) {
				Chat.send(serverPlayer, Chat.red("Предмет больше не в инвентаре."));
				this.done = true;
				ScreenNav.back(serverPlayer);
				return true;
			}
			AuctionService.add(serverPlayer, listed, this.price);
			Chat.send(serverPlayer, Chat.green("Лот выставлен: " + listed.getCount() + " шт. по "
					+ this.price + " WC за 1 шт. (" + this.minPrice + "–" + this.maxPrice + ")."));
			this.done = true;
			ScreenNav.back(serverPlayer);
			return true;
		}
		if (id == PRICE_MINUS_10) {
			this.price -= 10;
		} else if (id == PRICE_MINUS) {
			this.price -= 1;
		} else if (id == PRICE_PLUS) {
			this.price += 1;
		} else if (id == PRICE_PLUS_10) {
			this.price += 10;
		} else if (id == QTY_MINUS_10) {
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
		if (!template.isEmpty()) {
			this.price = Economy.clampAuctionPrice(template.getItem(), this.price);
		} else {
			this.price = Math.max(this.minPrice, Math.min(this.maxPrice, this.price));
		}
		this.qty = Math.max(1, Math.min(this.maxQty, this.qty));
		sync();
		return true;
	}

	public void setPriceFromText(ServerPlayerEntity player, String raw) {
		ItemStack template = this.preview.getStack(0);
		this.price = ru.admincontrol.util.UiValues.parseInt(raw, this.price);
		if (!template.isEmpty()) {
			this.price = Economy.clampAuctionPrice(template.getItem(), this.price);
		} else {
			this.price = Math.max(this.minPrice, Math.min(this.maxPrice, this.price));
		}
		sync();
	}

	public void setQtyFromText(ServerPlayerEntity player, String raw) {
		this.qty = Math.max(1, Math.min(this.maxQty, ru.admincontrol.util.UiValues.parseInt(raw, this.qty)));
		sync();
	}

	private void sync() {
		this.props.set(0, this.price);
		this.props.set(1, this.qty);
		this.props.set(2, this.minPrice);
		this.props.set(3, this.maxPrice);
		this.props.set(4, this.maxQty);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return !this.done && (!(player instanceof ServerPlayerEntity sp) || !Economy.sellBlocked(sp));
	}

	public int invSlot() {
		return this.invSlot;
	}
}
