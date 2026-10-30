package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DevEventFactory implements ExtendedScreenHandlerFactory {
	/** Выбранное МП и страница запоминаются, чтобы «Назад» из набора и пересоздание экрана вернули туда же. */
	private int type;
	private int page;

	public DevEventFactory(int type) {
		this.type = type;
	}

	void remember(int type, int page) {
		this.type = type;
		this.page = page;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Настройки МП");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new DevEventScreenHandler(syncId, viewer instanceof ServerPlayerEntity sp ? sp : null, this, this.type, this.page);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		DevEventScreenHandler.write(buf, opener, this.type, this.page);
	}
}
