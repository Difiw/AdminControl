package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.WorldGate;

import java.util.List;

public final class WorldsFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Миры сервера");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
		return new WorldsScreenHandler(syncId, WorldGate.list(player == null ? null : player.getServer()));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		List<WorldGate.Entry> entries = WorldGate.list(opener.getServer());
		buf.writeVarInt(entries.size());
		for (WorldGate.Entry entry : entries) {
			buf.writeString(entry.id, 128);
			buf.writeString(entry.label, 64);
			buf.writeBoolean(entry.locked);
		}
	}
}
