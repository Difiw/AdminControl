package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.Economy;

public final class AuctionOfferFactory implements ExtendedScreenHandlerFactory {
	private final int invSlot;

	public AuctionOfferFactory(int invSlot) {
		this.invSlot = invSlot;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Выставить лот");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new AuctionOfferScreenHandler(syncId, playerInventory, this.invSlot);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		ItemStack stack = opener.getInventory().getStack(this.invSlot);
		buf.writeVarInt(this.invSlot);
		buf.writeItemStack(stack);
		int min = stack.isEmpty() ? 1 : Economy.auctionMin(stack.getItem());
		int max = stack.isEmpty() ? 1 : Economy.auctionMax(stack.getItem());
		buf.writeVarInt(min);
		buf.writeVarInt(max);
		buf.writeVarInt(min);
		buf.writeVarInt(stack.getCount());
	}
}
