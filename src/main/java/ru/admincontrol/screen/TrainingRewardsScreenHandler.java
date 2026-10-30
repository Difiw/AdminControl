package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.TrainingConfig;

public class TrainingRewardsScreenHandler extends ScreenHandler {
	public static final int TYPE_BASE = 0;
	public static final int ROW = 10;
	private final PropertyDelegate props = new ArrayPropertyDelegate(1 + TrainingConfig.MINUTES.length * 2);
	private int type;

	public static TrainingRewardsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new TrainingRewardsScreenHandler(syncId, buf.readVarInt());
	}

	public TrainingRewardsScreenHandler(int syncId, int type) {
		super(ModScreenHandlers.TRAINING_REWARDS, syncId);
		this.type = type;
		this.addProperties(this.props);
		sync();
	}

	public int type() {
		return this.props.get(0);
	}

	public int coins(int timeIndex) {
		return this.props.get(1 + timeIndex * 2);
	}

	public int power(int timeIndex) {
		return this.props.get(2 + timeIndex * 2);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(serverPlayer) < AdminRanks.TECH) {
			return false;
		}
		if (id >= TYPE_BASE && id < TYPE_BASE + TrainingConfig.TYPES.length) {
			this.type = id - TYPE_BASE;
			sync();
			return true;
		}
		int row = (id - ROW) / 4;
		int act = (id - ROW) % 4;
		if (row < 0 || row >= TrainingConfig.MINUTES.length) {
			return false;
		}
		String kind = TrainingConfig.TYPES[Math.max(0, this.type)];
		int min = TrainingConfig.MINUTES[row];
		if (act == 0) {
			TrainingConfig.addCoins(kind, min, -1);
		} else if (act == 1) {
			TrainingConfig.addCoins(kind, min, 1);
		} else if (act == 2) {
			TrainingConfig.addPower(kind, min, -1);
		} else {
			TrainingConfig.addPower(kind, min, 1);
		}
		sync();
		return true;
	}

	private void sync() {
		this.props.set(0, this.type);
		String kind = TrainingConfig.TYPES[Math.max(0, Math.min(TrainingConfig.TYPES.length - 1, this.type))];
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			int min = TrainingConfig.MINUTES[i];
			this.props.set(1 + i * 2, TrainingConfig.coins(kind, min));
			this.props.set(2 + i * 2, TrainingConfig.powerTenths(kind, min));
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
