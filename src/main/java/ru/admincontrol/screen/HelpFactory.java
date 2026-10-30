package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.HelpLists;

import java.util.List;

public final class HelpFactory implements ExtendedScreenHandlerFactory {
	private static final int COMMAND_MAX = 256;
	private static final int DESCRIPTION_MAX = 256;
	private final String title;
	private final String subtitle;
	private final List<HelpLists.Entry> entries;
	private final String query;

	public HelpFactory(String title, String subtitle, List<HelpLists.Entry> entries, String query) {
		this.title = title == null ? "Команды" : title;
		this.subtitle = subtitle == null ? "" : subtitle;
		this.entries = entries == null ? List.of() : entries;
		this.query = query == null ? "" : query;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(this.title);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new HelpScreenHandler(syncId, this.title, this.subtitle, this.entries, this.query);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(this.title, 80);
		buf.writeString(this.subtitle, 120);
		buf.writeString(this.query, 64);
		int count = Math.min(this.entries.size(), 512);
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			HelpLists.Entry entry = this.entries.get(i);
			buf.writeString(entry.command == null ? "" : entry.command, COMMAND_MAX);
			buf.writeString(entry.desc == null ? "" : entry.desc, DESCRIPTION_MAX);
			buf.writeString(String.join(",", entry.filters), 80);
		}
	}
}
