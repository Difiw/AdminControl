package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ServerPanelFactory implements ExtendedScreenHandlerFactory {
	private final boolean developer;

	public ServerPanelFactory() {
		this(false);
	}

	public ServerPanelFactory(boolean developer) {
		this.developer = developer;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(this.developer ? "Контроль разработчика" : "Сервер");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
		return new ServerPanelScreenHandler(syncId, ServerPanelScreenHandler.snapshot(player, this.developer));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] data = ServerPanelScreenHandler.snapshot(opener, this.developer);
		buf.writeVarInt(data.length);
		for (int value : data) {
			buf.writeVarInt(value);
		}
		for (String key : ru.admincontrol.data.AdminRanks.TAG_KEYS) {
			int level = ru.admincontrol.data.AdminRanks.levelOfTagKey(key);
			buf.writeString(ru.admincontrol.data.AdminRanks.chatTag(level), 32);
		}
	}
}
