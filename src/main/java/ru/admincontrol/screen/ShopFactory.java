package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ShopFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Магазин");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new ShopScreenHandler(syncId, ShopScreenHandler.snapshot(viewer instanceof ServerPlayerEntity p ? p : null));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] data = ShopScreenHandler.snapshot(opener);
		buf.writeVarInt(data.length);
		for (int value : data) {
			buf.writeVarInt(value);
		}
	}
}
