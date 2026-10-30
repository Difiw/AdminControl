package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class StatsFactory implements ExtendedScreenHandlerFactory {
	private final String title;
	private final List<String> labels;
	private final List<String> values;

	public StatsFactory(String title, List<String> labels, List<String> values) {
		this.title = title == null ? "Статистика" : title;
		this.labels = labels == null ? List.of() : labels;
		this.values = values == null ? List.of() : values;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(title);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new StatsScreenHandler(syncId, title, labels, values);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(title, 80);
		int n = Math.min(labels.size(), values.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			buf.writeString(labels.get(i), 80);
			buf.writeString(values.get(i), 200);
		}
	}

	public static List<String> readLabels(PacketByteBuf buf) {
		return new ArrayList<>();
	}
}
