package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Chat;

import java.util.ArrayList;
import java.util.List;

public class ChCommandsScreenHandler extends ScreenHandler {
	private final PropertyDelegate props = new ArrayPropertyDelegate(1);
	private final List<Entry> entries = new ArrayList<>();

	public static final class Entry {
		public final String name;
		public final boolean everyone;

		public Entry(String name, boolean everyone) {
			this.name = name == null ? "" : name;
			this.everyone = everyone;
		}
	}

	public static ChCommandsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			entries.add(new Entry(buf.readString(), buf.readBoolean()));
		}
		return new ChCommandsScreenHandler(syncId, entries);
	}

	public ChCommandsScreenHandler(int syncId, List<Entry> entries) {
		super(ModScreenHandlers.CH_COMMANDS, syncId);
		if (entries != null) {
			this.entries.addAll(entries);
		}
		this.addProperties(this.props);
		this.props.set(0, this.entries.size());
	}

	public List<Entry> entries() {
		return this.entries;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		int rank = DataManager.get().getAdminLevel(serverPlayer);
		if (rank < AdminRanks.ADMIN || AdminRanks.isBlogger(rank)) {
			return false;
		}
		if (id < 0 || id >= this.entries.size()) {
			return false;
		}
		Entry entry = this.entries.get(id);
		var list = entry.everyone
				? DataManager.get().settings().disabledEveryoneCommands
				: DataManager.get().settings().disabledPlayerCommands;
		list.removeIf(v -> v != null && v.equalsIgnoreCase(entry.name));
		DataManager.get().saveAll();
		Chat.send(serverPlayer, Chat.green("/" + entry.name + " включена"
				+ (entry.everyone ? " для всех" : " для игроков") + "."));
		ru.admincontrol.util.ScreenNav.replace(serverPlayer, new ChCommandsFactory());
		return true;
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
