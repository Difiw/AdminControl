package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.AplayerActions;

import java.util.List;

public final class AplayerFactory implements ExtendedScreenHandlerFactory {
	private final String nick;

	public AplayerFactory(String nick) {
		this.nick = nick == null ? "" : nick;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Игрок  " + this.nick);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity admin = viewer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
		return new AplayerScreenHandler(syncId, this.nick, AplayerActions.list(admin, this.nick));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(this.nick, 16);
		List<AplayerActions.Action> actions = AplayerActions.list(opener, this.nick);
		buf.writeVarInt(actions.size());
		for (AplayerActions.Action action : actions) {
			buf.writeString(action.id, 32);
			buf.writeString(action.label, 48);
			buf.writeString(action.hint == null ? "" : action.hint, 64);
		}
	}
}
