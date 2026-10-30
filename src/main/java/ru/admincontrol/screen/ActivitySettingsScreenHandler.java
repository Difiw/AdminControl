package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.ScreenNav;
import ru.admincontrol.util.TrainingConfig;

public class ActivitySettingsScreenHandler extends ScreenHandler {
	public static final int PROPS = 7;
	public static final int OPEN_TRAINING_REWARDS = 0;
	public static final int OPEN_TRAINING_KIT = 1;
	public static final int OPEN_KIT_BASE = 2;
	public static final int HUNT_KILLS_M = 20;
	public static final int HUNT_KILLS_P = 21;
	public static final int HUNT_TIME_M = 22;
	public static final int HUNT_TIME_P = 23;
	public static final int HUNT_MOB = 24;
	public static final int STRIKE_KILLS_M = 25;
	public static final int STRIKE_KILLS_P = 26;
	public static final int STRIKE_TIME_M = 27;
	public static final int STRIKE_TIME_P = 28;
	public static final int TRAIN_DAILY_M = 29;
	public static final int TRAIN_DAILY_P = 30;
	public static final int TRAIN_RADIUS_M = 31;
	public static final int TRAIN_RADIUS_P = 32;

	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);

	public static ActivitySettingsScreenHandler createClient(int syncId, net.minecraft.entity.player.PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		int[] data = new int[PROPS];
		for (int i = 0; i < n && i < PROPS; i++) {
			data[i] = buf.readVarInt();
		}
		return new ActivitySettingsScreenHandler(syncId, data);
	}

	public ActivitySettingsScreenHandler(int syncId, int[] data) {
		super(ModScreenHandlers.ACTIVITY_SETTINGS, syncId);
		this.addProperties(this.props);
		sync(data);
	}

	public static int[] snapshot() {
		ModSettings s = DataManager.get() == null ? new ModSettings() : DataManager.get().settings();
		return new int[] {
				Math.max(1, s.eventHuntKills),
				Math.max(30, s.eventHuntSeconds),
				Math.max(0, Math.min(EventService.HUNT_MOBS.length - 1, s.eventHuntMob)),
				EventService.strikeKills(),
				EventService.strikeMinutes(),
				Math.max(1, Math.min(50, s.trainingDailyMax <= 0 ? 5 : s.trainingDailyMax)),
				Math.max(64, Math.min(30000, s.trainingSpawnRadius <= 0 ? 1000 : s.trainingSpawnRadius))
		};
	}

	public int trainingSpawnRadius() { return Math.max(64, this.props.get(6)); }

	public int trainingDailyMax() {
		return Math.max(1, this.props.get(5));
	}

	public int strikeKills() {
		return this.props.get(3);
	}

	public int strikeMinutes() {
		return this.props.get(4);
	}

	public int huntKills() {
		return this.props.get(0);
	}

	public int huntSeconds() {
		return this.props.get(1);
	}

	public String huntMobLabel() {
		int i = Math.max(0, Math.min(EventService.HUNT_MOBS.length - 1, this.props.get(2)));
		return EventService.HUNT_MOBS[i];
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(admin) < AdminRanks.ZGA) {
			return false;
		}
		ModSettings s = DataManager.get().settings();
		if (id == OPEN_TRAINING_REWARDS) {
			ScreenNav.open(admin, new TrainingRewardsFactory());
			return true;
		}
		if (id == OPEN_TRAINING_KIT) {
			ScreenNav.open(admin, new TrainingKitFactory("training"));
			return true;
		}
		if (id >= OPEN_KIT_BASE && id < OPEN_KIT_BASE + EventService.TYPES.length) {
			ScreenNav.open(admin, new TrainingKitFactory(String.valueOf(id - OPEN_KIT_BASE)));
			return true;
		}
		if (id == HUNT_KILLS_M || id == HUNT_KILLS_P) {
			s.eventHuntKills = Math.max(1, Math.min(200, s.eventHuntKills + (id == HUNT_KILLS_M ? -1 : 1)));
		} else if (id == HUNT_TIME_M || id == HUNT_TIME_P) {
			s.eventHuntSeconds = Math.max(30, Math.min(900, s.eventHuntSeconds + (id == HUNT_TIME_M ? -15 : 15)));
		} else if (id == HUNT_MOB) {
			s.eventHuntMob = (s.eventHuntMob + 1) % EventService.HUNT_MOBS.length;
			TrainingConfig.refreshHuntDefaultKit();
			Chat.send(admin, Chat.yellow("Охота: мобы «" + EventService.huntMobLabel() + "», набор обновлён."));
		} else if (id == STRIKE_KILLS_M || id == STRIKE_KILLS_P) {
			int step = s.eventStrikeKills >= 50 ? 10 : 5;
			s.eventStrikeKills = Math.max(5, Math.min(500, s.eventStrikeKills + (id == STRIKE_KILLS_M ? -step : step)));
		} else if (id == STRIKE_TIME_M || id == STRIKE_TIME_P) {
			s.eventStrikeMinutes = Math.max(1, Math.min(120, s.eventStrikeMinutes + (id == STRIKE_TIME_M ? -1 : 1)));
		} else if (id == TRAIN_DAILY_M || id == TRAIN_DAILY_P) {
			int cur = s.trainingDailyMax <= 0 ? 5 : s.trainingDailyMax;
			s.trainingDailyMax = Math.max(1, Math.min(50, cur + (id == TRAIN_DAILY_M ? -1 : 1)));
			Chat.send(admin, Chat.yellow("Дневной лимит тренировок (база): " + s.trainingDailyMax));
		} else if (id == TRAIN_RADIUS_M || id == TRAIN_RADIUS_P) {
			int cur = s.trainingSpawnRadius <= 0 ? 1000 : s.trainingSpawnRadius;
			s.trainingSpawnRadius = Math.max(64, Math.min(30000, cur + (id == TRAIN_RADIUS_M ? -100 : 100)));
			Chat.send(admin, Chat.yellow("Радиус тренировок от спавна: " + s.trainingSpawnRadius + " блоков"));
		} else {
			return false;
		}
		DataManager.get().saveAll();
		sync(snapshot());
		return true;
	}

	private void sync(int[] data) {
		for (int i = 0; i < PROPS; i++) {
			this.props.set(i, data != null && i < data.length ? data[i] : 0);
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
