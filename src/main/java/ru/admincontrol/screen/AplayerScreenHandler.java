package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import ru.admincontrol.util.AplayerActions;

import java.util.ArrayList;
import java.util.List;

public class AplayerScreenHandler extends ScreenHandler {
	private final String nick;
	private final List<AplayerActions.Action> actions = new ArrayList<>();

	public static AplayerScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String nick = buf.readString();
		int n = buf.readVarInt();
		List<AplayerActions.Action> actions = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			String id = buf.readString();
			String label = buf.readString();
			String hint = buf.readString();
			actions.add(new AplayerActions.Action(id, label, hint.isBlank() ? null : hint, 0, false, false));
		}
		return new AplayerScreenHandler(syncId, nick, actions);
	}

	public AplayerScreenHandler(int syncId, String nick, List<AplayerActions.Action> actions) {
		super(ModScreenHandlers.APLAYER, syncId);
		this.nick = nick == null ? "" : nick;
		if (actions != null) {
			this.actions.addAll(actions);
		}
	}

	public String nick() {
		return this.nick;
	}

	public List<AplayerActions.Action> actions() {
		return this.actions;
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
