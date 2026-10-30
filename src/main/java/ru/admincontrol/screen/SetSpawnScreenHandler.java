package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.SpawnChoice;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ClanService;

public class SetSpawnScreenHandler extends ScreenHandler {
	public final boolean hasHome;
	public final boolean hasBed;
	public final boolean hasClan;
	public SpawnChoice current;

	public static SetSpawnScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		boolean hasHome = buf.readBoolean();
		boolean hasBed = buf.readBoolean();
		boolean hasClan = buf.readBoolean();
		SpawnChoice current = SpawnChoice.from(buf.readString());
		if (current == null) {
			current = hasHome ? SpawnChoice.HOME : SpawnChoice.BED;
		}
		return new SetSpawnScreenHandler(syncId, hasHome, hasBed, hasClan, current);
	}

	public SetSpawnScreenHandler(int syncId, boolean hasHome, boolean hasBed, boolean hasClan, SpawnChoice current) {
		super(ModScreenHandlers.SET_SPAWN, syncId);
		this.hasHome = hasHome;
		this.hasBed = hasBed;
		this.hasClan = hasClan;
		this.current = current == null ? SpawnChoice.BED : current;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		PlayerRecord record = data.getOrCreate(serverPlayer);
		SpawnChoice choice = switch (id) {
			case 0 -> SpawnChoice.BED;
			case 1 -> SpawnChoice.WORLD;
			case 2 -> SpawnChoice.HOME;
			case 3 -> SpawnChoice.CLAN;
			default -> null;
		};
		if (choice == null) {
			return false;
		}
		if (choice == SpawnChoice.HOME && record.home == null) {
			serverPlayer.sendMessage(Chat.red("У вас нет метки дома."), false);
			return true;
		}
		if (choice == SpawnChoice.CLAN && !ClanService.canSpawnAtClan(serverPlayer)) {
			serverPlayer.sendMessage(Chat.red("Нет дома клана или доступа на телепорт."), false);
			return true;
		}
		record.spawnChoice = choice.id();
		this.current = choice;
		data.saveAll();
		ru.admincontrol.util.AdminNotify.playerAction(serverPlayer, ru.admincontrol.data.ActionKind.SPAWN, "изменил точку спавна на " + choice.label());
		serverPlayer.sendMessage(Chat.green("Точка возрождения: " + choice.label() + "."), false);
		serverPlayer.closeHandledScreen();
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

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
	}
}
