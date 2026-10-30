package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import ru.admincontrol.util.HelpLists;

import java.util.ArrayList;
import java.util.List;

public class HelpScreenHandler extends ScreenHandler {
	private static final int COMMAND_MAX = 256;
	private static final int DESCRIPTION_MAX = 256;
	public final String title;
	public final String subtitle;
	public final String initialQuery;
	public final List<HelpLists.Entry> entries;

	public static HelpScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String title = buf.readString(80);
		String subtitle = buf.readString(120);
		String query = buf.readString(64);
		int n = Math.min(buf.readVarInt(), 512);
		List<HelpLists.Entry> entries = new ArrayList<>(Math.max(0, n));
		for (int i = 0; i < n; i++) {
			String command = buf.readString(COMMAND_MAX);
			String desc = buf.readString(DESCRIPTION_MAX);
			String tags = buf.readString(80);
			String[] filters = tags == null || tags.isBlank() ? new String[0] : tags.split(",");
			entries.add(new HelpLists.Entry(command, desc, filters));
		}
		return new HelpScreenHandler(syncId, title, subtitle, entries, query);
	}

	public HelpScreenHandler(int syncId, String title, String subtitle, List<HelpLists.Entry> entries, String query) {
		super(ModScreenHandlers.HELP, syncId);
		this.title = title == null ? "Команды" : title;
		this.subtitle = subtitle == null ? "" : subtitle;
		this.entries = entries == null ? List.of() : List.copyOf(entries);
		this.initialQuery = query == null ? "" : query;
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
