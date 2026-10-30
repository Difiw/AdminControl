package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

public final class HomeFactory implements ExtendedScreenHandlerFactory {
	private final ServerPlayerEntity player;

	public HomeFactory(ServerPlayerEntity player) {
		this.player = player;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Дом");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		PlayerRecord record = DataManager.get().getOrCreate(player);
		int size = record.homeSize <= 0 ? 5 : record.homeSize;
		int max = Math.max(3, Math.max(size, record.maxHomeSize));
		return new HomeScreenHandler(syncId, record.home != null, record.homePrivate, size, max);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		PlayerRecord record = DataManager.get().getOrCreate(player);
		int size = record.homeSize <= 0 ? 5 : record.homeSize;
		int max = Math.max(3, Math.max(size, record.maxHomeSize));
		buf.writeBoolean(record.home != null);
		buf.writeBoolean(record.homePrivate);
		buf.writeInt(size);
		buf.writeInt(max);
	}
}
