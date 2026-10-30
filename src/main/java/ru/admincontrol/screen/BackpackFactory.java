package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.PlayFeatures;
import ru.admincontrol.util.StackIo;

public final class BackpackFactory implements ExtendedScreenHandlerFactory {
	private final PlayerRecord record;

	public BackpackFactory(PlayerRecord record) {
		this.record = record;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Рюкзак");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
		PlayerRecord rec = this.record;
		int level = rec == null ? 0 : Math.max(0, rec.backpackLevel);
		int slots = level <= 0 ? 9 : PlayFeatures.s().backpackSlots(level);
		int rows = Math.max(1, Math.min(6, (slots + 8) / 9));
		SimpleInventory chest = new SimpleInventory(rows * 9);
		if (rec != null) {
			StackIo.fill(chest, rec.backpack, rows * 9);
		}
		return new ExtraChestScreenHandler(syncId, inventory, chest, rows, () -> {
			if (rec != null && DataManager.get() != null) {
				rec.backpack = StackIo.writeInv(chest, rows * 9);
				DataManager.get().saveAll();
			}
		});
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		PlayerRecord rec = this.record != null ? this.record : DataManager.get().progress(opener);
		int level = rec == null ? 0 : Math.max(0, rec.backpackLevel);
		int slots = level <= 0 ? 9 : PlayFeatures.s().backpackSlots(level);
		buf.writeVarInt(Math.max(1, Math.min(6, (slots + 8) / 9)));
	}
}
