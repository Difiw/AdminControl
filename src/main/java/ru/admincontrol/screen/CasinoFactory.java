package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.CasinoService;

public final class CasinoFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Казино");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new CasinoScreenHandler(syncId, snapshot());
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] data = snapshot();
		for (int value : data) {
			buf.writeVarInt(value);
		}
	}

	static int[] snapshot() {
		return new int[] {
				CasinoService.enabled() ? 1 : 0,
				CasinoService.minTenths(),
				CasinoService.maxTenths(),
				CasinoService.chance()
		};
	}
}
