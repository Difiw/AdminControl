package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

public final class CheckFeedFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Логи в чат");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new CheckFeedScreenHandler(syncId, flags(viewer instanceof ServerPlayerEntity player ? player : null));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] flags = flags(opener);
		for (int flag : flags) {
			buf.writeBoolean(flag != 0);
		}
	}

	private static int[] flags(ServerPlayerEntity player) {
		int[] flags = new int[ActionKind.values().length];
		PlayerRecord record = player == null || DataManager.get() == null ? null : DataManager.get().getOrCreate(player);
		for (int i = 0; i < flags.length; i++) {
			flags[i] = record == null || record.feedEnabled(ActionKind.byOrdinal(i)) ? 1 : 0;
		}
		return flags;
	}
}
