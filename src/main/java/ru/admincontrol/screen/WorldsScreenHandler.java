package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.WorldGate;

import java.util.ArrayList;
import java.util.List;

public class WorldsScreenHandler extends ScreenHandler {
	private final List<WorldGate.Entry> entries = new ArrayList<>();

	public static WorldsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<WorldGate.Entry> entries = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			entries.add(new WorldGate.Entry(buf.readString(), buf.readString(), buf.readBoolean()));
		}
		return new WorldsScreenHandler(syncId, entries);
	}

	public WorldsScreenHandler(int syncId, List<WorldGate.Entry> entries) {
		super(ModScreenHandlers.WORLDS, syncId);
		if (entries != null) {
			this.entries.addAll(entries);
		}
	}

	public List<WorldGate.Entry> entries() {
		return this.entries;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(serverPlayer) < AdminRanks.TECH) {
			return false;
		}
		if (id < 0 || id >= this.entries.size()) {
			return false;
		}
		WorldGate.Entry entry = this.entries.get(id);
		boolean locked = WorldGate.toggle(serverPlayer.getServer(), entry.id);
		Chat.send(serverPlayer, locked
				? Chat.red("Мир «" + entry.label + "» закрыт.")
				: Chat.green("Мир «" + entry.label + "» открыт."));
		ru.admincontrol.util.ScreenNav.replace(serverPlayer, new WorldsFactory());
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
