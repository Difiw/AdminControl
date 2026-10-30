package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Chat;

public class CheckFeedScreenHandler extends ScreenHandler {
	public static final int ALL_ON = ActionKind.values().length;
	public static final int ALL_OFF = ALL_ON + 1;
	private final PropertyDelegate props = new ArrayPropertyDelegate(ActionKind.values().length);

	public static CheckFeedScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int[] flags = new int[ActionKind.values().length];
		for (int i = 0; i < flags.length; i++) {
			flags[i] = buf.readBoolean() ? 1 : 0;
		}
		return new CheckFeedScreenHandler(syncId, flags);
	}

	public CheckFeedScreenHandler(int syncId, int[] flags) {
		super(ModScreenHandlers.CHECK_FEED, syncId);
		this.addProperties(props);
		for (int i = 0; i < ActionKind.values().length; i++) {
			int value = flags != null && i < flags.length ? flags[i] : 1;
			props.set(i, value);
		}
	}

	public boolean enabled(int index) {
		return props.get(index) != 0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		PlayerRecord record = DataManager.get().getOrCreate(serverPlayer);
		if (id == ALL_ON) {
			record.setAllFeeds(true);
			Chat.send(serverPlayer, Chat.yellow("Все логи в чат включены."));
		} else if (id == ALL_OFF) {
			record.setAllFeeds(false);
			Chat.send(serverPlayer, Chat.yellow("Все логи в чат выключены."));
		} else {
			ActionKind kind = ActionKind.byOrdinal(id);
			if (kind == null) {
				return false;
			}
			boolean on = !record.feedEnabled(kind);
			record.setFeedEnabled(kind, on);
			Chat.send(serverPlayer, Chat.gray(kind.label + ": " + (on ? "вкл" : "выкл")));
		}
		DataManager.get().saveAll();
		syncFrom(record);
		return true;
	}

	private void syncFrom(PlayerRecord record) {
		for (ActionKind kind : ActionKind.values()) {
			props.set(kind.ordinal(), record.feedEnabled(kind) ? 1 : 0);
		}
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
