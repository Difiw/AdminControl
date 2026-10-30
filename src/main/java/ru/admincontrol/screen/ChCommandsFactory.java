package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.List;

public final class ChCommandsFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Выключенные команды");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new ChCommandsScreenHandler(syncId, snapshot());
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		List<ChCommandsScreenHandler.Entry> entries = snapshot();
		buf.writeVarInt(entries.size());
		for (ChCommandsScreenHandler.Entry entry : entries) {
			buf.writeString(entry.name, 64);
			buf.writeBoolean(entry.everyone);
		}
	}

	static List<ChCommandsScreenHandler.Entry> snapshot() {
		List<ChCommandsScreenHandler.Entry> entries = new ArrayList<>();
		DataManager data = DataManager.get();
		if (data == null) {
			return entries;
		}
		ModSettings s = data.settings();
		if (s.disabledPlayerCommands != null) {
			for (String name : s.disabledPlayerCommands) {
				if (name != null && !name.isBlank()) {
					entries.add(new ChCommandsScreenHandler.Entry(name, false));
				}
			}
		}
		if (s.disabledEveryoneCommands != null) {
			for (String name : s.disabledEveryoneCommands) {
				if (name != null && !name.isBlank()) {
					entries.add(new ChCommandsScreenHandler.Entry(name, true));
				}
			}
		}
		return entries;
	}
}
