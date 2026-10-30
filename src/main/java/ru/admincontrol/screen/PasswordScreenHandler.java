package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.PasswordService;

public class PasswordScreenHandler extends ScreenHandler {
	public static PasswordScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new PasswordScreenHandler(syncId);
	}

	public PasswordScreenHandler(int syncId) {
		super(ModScreenHandlers.PASSWORD, syncId);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		return player instanceof ServerPlayerEntity;
	}

	public static void apply(ServerPlayerEntity player, String oldP, String neu, String repeat) {
		if (!PasswordService.change(player, oldP, neu, repeat)) {
			Chat.send(player, Chat.red("Не удалось сменить пароль. Проверьте старый пароль и совпадение нового."));
			return;
		}
		Chat.send(player, Chat.green("Пароль изменён."));
		AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.COMMAND, "сменил пароль через /setpassword", true);
		player.closeHandledScreen();
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
