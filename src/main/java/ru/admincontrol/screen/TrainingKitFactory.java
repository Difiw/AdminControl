package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.TrainingConfig;

public final class TrainingKitFactory implements ExtendedScreenHandlerFactory {
	private final String key;

	public TrainingKitFactory() {
		this("training");
	}

	public TrainingKitFactory(String key) {
		this.key = key == null ? "training" : key;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(TrainingConfig.kitTitle(this.key));
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new TrainingKitScreenHandler(syncId, playerInventory, this.key);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(this.key, 32);
	}
}
