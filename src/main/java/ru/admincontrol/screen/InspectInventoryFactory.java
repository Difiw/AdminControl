package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.PlayerRecord;

import java.util.UUID;

public final class InspectInventoryFactory implements ExtendedScreenHandlerFactory {
	private final ServerPlayerEntity online;
	private final PlayerRecord offline;

	public InspectInventoryFactory(ServerPlayerEntity target) {
		this.online = target;
		this.offline = null;
	}

	public InspectInventoryFactory(PlayerRecord record) {
		this.online = null;
		this.offline = record;
	}

	@Override
	public Text getDisplayName() {
		return Text.empty();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		if (online != null) {
			return new InspectInventoryScreenHandler(syncId, playerInventory, online);
		}
		return new InspectInventoryScreenHandler(syncId, playerInventory, offline);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
		UUID uuid = online != null ? online.getUuid() : (offline == null || offline.uuidOrNull() == null
				? new UUID(0, 0) : offline.uuidOrNull());
		buf.writeUuid(uuid);
	}
}
