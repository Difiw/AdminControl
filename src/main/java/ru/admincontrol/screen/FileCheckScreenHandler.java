package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;

import java.util.UUID;

/** Окно проверки файлов игрока; содержимое папок приходит отдельными пакетами по запросу. */
public class FileCheckScreenHandler extends ScreenHandler {
	public final String targetNick;
	public final UUID targetUuid;

	public static FileCheckScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new FileCheckScreenHandler(syncId, buf.readUuid(), buf.readString(16));
	}

	public FileCheckScreenHandler(int syncId, UUID targetUuid, String targetNick) {
		super(ModScreenHandlers.FILE_CHECK, syncId);
		this.targetUuid = targetUuid;
		this.targetNick = targetNick == null ? "" : targetNick;
	}

	public static void write(PacketByteBuf buf, UUID targetUuid, String targetNick) {
		buf.writeUuid(targetUuid);
		buf.writeString(targetNick == null ? "" : targetNick, 16);
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
