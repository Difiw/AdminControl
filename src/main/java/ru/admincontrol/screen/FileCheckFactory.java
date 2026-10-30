package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public final class FileCheckFactory implements ExtendedScreenHandlerFactory {
	private final UUID targetUuid;
	private final String targetNick;

	public FileCheckFactory(UUID targetUuid, String targetNick) {
		this.targetUuid = targetUuid;
		this.targetNick = targetNick;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Файлы: " + this.targetNick);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
		return new FileCheckScreenHandler(syncId, this.targetUuid, this.targetNick);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		FileCheckScreenHandler.write(buf, this.targetUuid, this.targetNick);
	}
}
