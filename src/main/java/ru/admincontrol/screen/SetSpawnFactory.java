package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.SpawnChoice;
import ru.admincontrol.util.SpawnService;

public final class SetSpawnFactory implements ExtendedScreenHandlerFactory {
	private final ServerPlayerEntity player;

	public SetSpawnFactory(ServerPlayerEntity player) {
		this.player = player;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Точка возрождения");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		PlayerRecord record = DataManager.get().getOrCreate(player);
		boolean hasHome = record.home != null;
		boolean hasBed = SpawnService.hasBed(player);
		boolean hasClan = ru.admincontrol.util.ClanService.canSpawnAtClan(player);
		SpawnChoice current = SpawnService.effectiveChoice(player, record);
		return new SetSpawnScreenHandler(syncId, hasHome, hasBed, hasClan, current);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		PlayerRecord record = DataManager.get().getOrCreate(player);
		boolean hasHome = record.home != null;
		boolean hasBed = SpawnService.hasBed(player);
		boolean hasClan = ru.admincontrol.util.ClanService.canSpawnAtClan(player);
		SpawnChoice current = SpawnService.effectiveChoice(player, record);
		buf.writeBoolean(hasHome);
		buf.writeBoolean(hasBed);
		buf.writeBoolean(hasClan);
		buf.writeString(current.id());
	}
}
