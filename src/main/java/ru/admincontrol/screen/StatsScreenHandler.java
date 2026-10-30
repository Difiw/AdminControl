package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.List;

public class StatsScreenHandler extends ScreenHandler {
	public final String title;
	public final List<String> labels;
	public final List<String> values;

	public static StatsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String heading = buf.readString(80);
		int n = buf.readVarInt();
		List<String> labels = new ArrayList<>(n);
		List<String> values = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			labels.add(buf.readString(80));
			values.add(buf.readString(200));
		}
		return new StatsScreenHandler(syncId, heading, labels, values);
	}

	public StatsScreenHandler(int syncId, String title, List<String> labels, List<String> values) {
		super(ModScreenHandlers.STATS, syncId);
		this.title = title == null ? "Статистика" : title;
		this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
		this.values = values == null ? new ArrayList<>() : new ArrayList<>(values);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
