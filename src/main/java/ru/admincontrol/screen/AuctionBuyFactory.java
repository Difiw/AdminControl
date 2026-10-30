package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.AuctionListing;
import ru.admincontrol.util.AuctionService;

public final class AuctionBuyFactory implements ExtendedScreenHandlerFactory {
	private final String listingId;

	public AuctionBuyFactory(String listingId) {
		this.listingId = listingId;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Купить лот");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new AuctionBuyScreenHandler(syncId, this.listingId);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		AuctionListing listing = AuctionService.find(this.listingId);
		ItemStack stack = AuctionService.stackOf(listing);
		buf.writeString(this.listingId == null ? "" : this.listingId);
		buf.writeItemStack(stack);
		buf.writeVarInt(listing == null ? 0 : listing.pricePerItem);
		buf.writeVarInt(Math.max(1, stack.getCount()));
		buf.writeString(listing == null || listing.sellerNick == null ? "" : listing.sellerNick);
	}
}
