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
import ru.admincontrol.util.PlayFeatures;

public class PlayFeaturesScreenHandler extends ScreenHandler {
	public static final int PROPS = 56;
	public static final int DAILY = 0;
	public static final int JOIN_M = 1;
	public static final int JOIN_P = 2;
	public static final int BREAK_G_M = 3;
	public static final int BREAK_G_P = 4;
	public static final int BREAK_W_M = 5;
	public static final int BREAK_W_P = 6;
	public static final int KILL_G_M = 7;
	public static final int KILL_G_P = 8;
	public static final int KILL_W_M = 9;
	public static final int KILL_W_P = 10;
	public static final int TPA = 11;
	public static final int TPA_M = 12;
	public static final int TPA_P = 13;
	public static final int SHOP = 14;
	public static final int HOME_M = 15;
	public static final int HOME_P = 16;
	public static final int LUCK_M = 17;
	public static final int LUCK_P = 18;
	public static final int POWER_M = 19;
	public static final int POWER_P = 20;
	public static final int CLAN = 23;
	public static final int CLAN_P_M = 24;
	public static final int CLAN_P_P = 25;
	public static final int CLAN_MAX_M = 26;
	public static final int CLAN_MAX_P = 27;
	public static final int AUTO = 28;
	public static final int AUTO_M = 29;
	public static final int AUTO_P = 30;
	public static final int AUTO_TYPE = 31;
	public static final int AUTO_R_M = 32;
	public static final int AUTO_R_P = 33;
	public static final int WEEK = 34;
	public static final int WEEK_M = 35;
	public static final int WEEK_P = 36;
	public static final int HOME_FREE = 37;
	public static final int PRIV_M = 39;
	public static final int PRIV_P = 40;
	public static final int SIZE_M = 41;
	public static final int SIZE_P = 42;
	public static final int PACK1_M = 43;
	public static final int PACK1_P = 44;
	public static final int PACK2_M = 45;
	public static final int PACK2_P = 46;
	public static final int PACK3_M = 47;
	public static final int PACK3_P = 48;
	public static final int PACK4_M = 49;
	public static final int PACK4_P = 50;
	public static final int PACK5_M = 51;
	public static final int PACK5_P = 52;
	public static final int JOIN_ON = 60;
	public static final int BREAK_ON = 61;
	public static final int KILL_ON = 62;
	public static final int PLACE_ON = 63;
	public static final int PLACE_G_M = 64;
	public static final int PLACE_G_P = 65;
	public static final int PLACE_W_M = 66;
	public static final int PLACE_W_P = 67;
	public static final int PLAY_ON = 68;
	public static final int PLAY_G_M = 69;
	public static final int PLAY_G_P = 70;
	public static final int PLAY_W_M = 71;
	public static final int PLAY_W_P = 72;
	public static final int CHAT_ON = 73;
	public static final int CHAT_G_M = 74;
	public static final int CHAT_G_P = 75;
	public static final int CHAT_W_M = 76;
	public static final int CHAT_W_P = 77;
	public static final int CAS_ON = 78;
	public static final int CAS_G_M = 79;
	public static final int CAS_G_P = 80;
	public static final int CAS_W_M = 81;
	public static final int CAS_W_P = 82;
	public static final int EAT_ON = 83;
	public static final int EAT_G_M = 84;
	public static final int EAT_G_P = 85;
	public static final int EAT_W_M = 86;
	public static final int EAT_W_P = 87;
	public static final int PVP_ON = 88;
	public static final int PVP_G_M = 89;
	public static final int PVP_G_P = 90;
	public static final int PVP_W_M = 91;
	public static final int PVP_W_P = 92;
	public static final int AUTO_BOX = 93;
	public static final int AUTO_BOX_M = 94;
	public static final int AUTO_BOX_P = 95;
	public static final int PRIV3_M = 96;
	public static final int PRIV3_P = 97;
	public static final int PRIV4_M = 98;
	public static final int PRIV4_P = 99;
	public static final int PRIV5_M = 100;
	public static final int PRIV5_P = 101;
	public static final int BOX_COOLDOWN_M = 102;
	public static final int BOX_COOLDOWN_P = 103;

	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);

	public static PlayFeaturesScreenHandler createClient(int syncId, net.minecraft.entity.player.PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		int[] data = new int[PROPS];
		for (int i = 0; i < n && i < PROPS; i++) {
			data[i] = buf.readVarInt();
		}
		return new PlayFeaturesScreenHandler(syncId, data);
	}

	public PlayFeaturesScreenHandler(int syncId, int[] data) {
		super(ModScreenHandlers.PLAY_FEATURES, syncId);
		this.addProperties(this.props);
		sync(data);
	}

	public static int[] snapshot() {
		ModSettings s = PlayFeatures.s();
		return new int[] {
				s.dailyEnabled ? 1 : 0,
				Math.max(0, s.dailyJoinWc),
				Math.max(1, s.dailyBreakGoal),
				Math.max(0, s.dailyBreakWc),
				Math.max(1, s.dailyKillGoal),
				Math.max(0, s.dailyKillWc),
				s.tpaEnabled ? 1 : 0,
				Math.max(10, s.tpaSeconds),
				s.shopEnabled ? 1 : 0,
				Math.max(0, s.shopHomePrice),
				Math.max(0, s.shopLuckPrice),
				Math.max(0, s.shopPowerPrice),
				s.clansEnabled ? 1 : 0,
				Math.max(0, s.clanCreatePrice),
				Math.max(2, s.clanMaxMembers),
				s.autoEventEnabled ? 1 : 0,
				Math.max(10, s.autoEventMinutes),
				Math.max(0, Math.min(EventService.TYPES.length, s.autoEventType)),
				Math.max(0, s.autoEventReward),
				s.weeklyTopEnabled ? 1 : 0,
				Math.max(0, s.weeklyTopWc),
				s.thomeUnlocked ? 1 : 0,
				s.privatePrice(1),
				s.privatePrice(2),
				Math.max(0, s.shopBackpackPrice1),
				Math.max(0, s.shopBackpackPrice2),
				Math.max(0, s.shopBackpackPrice3),
				Math.max(0, s.shopBackpackPrice4),
				Math.max(0, s.shopBackpackPrice5),
				s.dailyJoinOn ? 1 : 0,
				s.dailyBreakOn ? 1 : 0,
				s.dailyKillOn ? 1 : 0,
				s.dailyPlaceOn ? 1 : 0,
				Math.max(1, s.dailyPlaceGoal),
				Math.max(0, s.dailyPlaceWc),
				s.dailyPlayOn ? 1 : 0,
				Math.max(1, s.dailyPlayMin),
				Math.max(0, s.dailyPlayWc),
				s.dailyChatOn ? 1 : 0,
				Math.max(1, s.dailyChatGoal),
				Math.max(0, s.dailyChatWc),
				s.dailyCasinoOn ? 1 : 0,
				Math.max(1, s.dailyCasinoGoal),
				Math.max(0, s.dailyCasinoWc),
				s.dailyEatOn ? 1 : 0,
				Math.max(1, s.dailyEatGoal),
				Math.max(0, s.dailyEatWc),
				s.dailyPvpOn ? 1 : 0,
				Math.max(1, s.dailyPvpGoal),
				Math.max(0, s.dailyPvpWc),
				s.autoBoxEnabled ? 1 : 0,
				Math.max(10, s.autoBoxMinutes),
				s.privatePrice(3),
				s.privatePrice(4),
				s.privatePrice(5),
				Math.max(0, s.boxCooldownMinutes)
		};
	}

	public int privatePrice(int level) {
		return switch (Math.max(1, Math.min(5, level))) {
			case 2 -> props.get(23);
			case 3 -> props.get(52);
			case 4 -> props.get(53);
			case 5 -> props.get(54);
			default -> props.get(22);
		};
	}

	public boolean daily() { return props.get(0) != 0; }
	public int joinWc() { return props.get(1); }
	public int breakGoal() { return props.get(2); }
	public int breakWc() { return props.get(3); }
	public int killGoal() { return props.get(4); }
	public int killWc() { return props.get(5); }
	public boolean tpa() { return props.get(6) != 0; }
	public int tpaSec() { return props.get(7); }
	public boolean shop() { return props.get(8) != 0; }
	public int homePrice() { return props.get(9); }
	public int luckPrice() { return props.get(10); }
	public int powerPrice() { return props.get(11); }
	public boolean clans() { return props.get(12) != 0; }
	public int clanPrice() { return props.get(13); }
	public int clanMax() { return props.get(14); }
	public boolean auto() { return props.get(15) != 0; }
	public int autoMin() { return props.get(16); }
	public int autoType() { return props.get(17); }
	public int autoReward() { return props.get(18); }
	public boolean week() { return props.get(19) != 0; }
	public int weekWc() { return props.get(20); }
	public boolean homeFree() { return props.get(21) != 0; }
	public int pack1() { return props.get(24); }
	public int pack2() { return props.get(25); }
	public int pack3() { return props.get(26); }
	public int pack4() { return props.get(27); }
	public int pack5() { return props.get(28); }
	public boolean joinOn() { return props.get(29) != 0; }
	public boolean breakOn() { return props.get(30) != 0; }
	public boolean killOn() { return props.get(31) != 0; }
	public boolean placeOn() { return props.get(32) != 0; }
	public int placeGoal() { return props.get(33); }
	public int placeWc() { return props.get(34); }
	public boolean playOn() { return props.get(35) != 0; }
	public int playMin() { return props.get(36); }
	public int playWc() { return props.get(37); }
	public boolean chatOn() { return props.get(38) != 0; }
	public int chatGoal() { return props.get(39); }
	public int chatWc() { return props.get(40); }
	public boolean casOn() { return props.get(41) != 0; }
	public int casGoal() { return props.get(42); }
	public int casWc() { return props.get(43); }
	public boolean eatOn() { return props.get(44) != 0; }
	public int eatGoal() { return props.get(45); }
	public int eatWc() { return props.get(46); }
	public boolean pvpOn() { return props.get(47) != 0; }
	public int pvpGoal() { return props.get(48); }
	public int pvpWc() { return props.get(49); }
	public boolean autoBox() { return props.get(50) != 0; }
	public int autoBoxMin() { return props.get(51); }
	public int boxCooldown() { return props.get(55); }

	public String autoTypeLabel() {
		int i = autoType();
		if (i >= EventService.TYPES.length) {
			return "цикл всех";
		}
		return EventService.TYPES[Math.max(0, i)];
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin) || DataManager.get() == null) {
			return false;
		}
		int rank = DataManager.get().getAdminLevel(admin);
		if (rank < AdminRanks.CURATOR) {
			return false;
		}
		ModSettings s = DataManager.get().settings();
		if (id == DAILY) s.dailyEnabled = !s.dailyEnabled;
		else if (id == JOIN_ON) s.dailyJoinOn = !s.dailyJoinOn;
		else if (id == JOIN_M) s.dailyJoinWc = Math.max(0, s.dailyJoinWc - 1);
		else if (id == JOIN_P) s.dailyJoinWc = Math.min(1000, s.dailyJoinWc + 1);
		else if (id == BREAK_ON) s.dailyBreakOn = !s.dailyBreakOn;
		else if (id == BREAK_G_M) s.dailyBreakGoal = Math.max(1, s.dailyBreakGoal - 10);
		else if (id == BREAK_G_P) s.dailyBreakGoal = Math.min(10000, s.dailyBreakGoal + 10);
		else if (id == BREAK_W_M) s.dailyBreakWc = Math.max(0, s.dailyBreakWc - 1);
		else if (id == BREAK_W_P) s.dailyBreakWc = Math.min(1000, s.dailyBreakWc + 1);
		else if (id == KILL_ON) s.dailyKillOn = !s.dailyKillOn;
		else if (id == KILL_G_M) s.dailyKillGoal = Math.max(1, s.dailyKillGoal - 1);
		else if (id == KILL_G_P) s.dailyKillGoal = Math.min(500, s.dailyKillGoal + 1);
		else if (id == KILL_W_M) s.dailyKillWc = Math.max(0, s.dailyKillWc - 1);
		else if (id == KILL_W_P) s.dailyKillWc = Math.min(1000, s.dailyKillWc + 1);
		else if (id == PLACE_ON) s.dailyPlaceOn = !s.dailyPlaceOn;
		else if (id == PLACE_G_M) s.dailyPlaceGoal = Math.max(1, s.dailyPlaceGoal - 10);
		else if (id == PLACE_G_P) s.dailyPlaceGoal = Math.min(10000, s.dailyPlaceGoal + 10);
		else if (id == PLACE_W_M) s.dailyPlaceWc = Math.max(0, s.dailyPlaceWc - 1);
		else if (id == PLACE_W_P) s.dailyPlaceWc = Math.min(1000, s.dailyPlaceWc + 1);
		else if (id == PLAY_ON) s.dailyPlayOn = !s.dailyPlayOn;
		else if (id == PLAY_G_M) s.dailyPlayMin = Math.max(1, s.dailyPlayMin - 5);
		else if (id == PLAY_G_P) s.dailyPlayMin = Math.min(1440, s.dailyPlayMin + 5);
		else if (id == PLAY_W_M) s.dailyPlayWc = Math.max(0, s.dailyPlayWc - 1);
		else if (id == PLAY_W_P) s.dailyPlayWc = Math.min(1000, s.dailyPlayWc + 1);
		else if (id == CHAT_ON) s.dailyChatOn = !s.dailyChatOn;
		else if (id == CHAT_G_M) s.dailyChatGoal = Math.max(1, s.dailyChatGoal - 1);
		else if (id == CHAT_G_P) s.dailyChatGoal = Math.min(200, s.dailyChatGoal + 1);
		else if (id == CHAT_W_M) s.dailyChatWc = Math.max(0, s.dailyChatWc - 1);
		else if (id == CHAT_W_P) s.dailyChatWc = Math.min(1000, s.dailyChatWc + 1);
		else if (id == CAS_ON) s.dailyCasinoOn = !s.dailyCasinoOn;
		else if (id == CAS_G_M) s.dailyCasinoGoal = Math.max(1, s.dailyCasinoGoal - 1);
		else if (id == CAS_G_P) s.dailyCasinoGoal = Math.min(50, s.dailyCasinoGoal + 1);
		else if (id == CAS_W_M) s.dailyCasinoWc = Math.max(0, s.dailyCasinoWc - 1);
		else if (id == CAS_W_P) s.dailyCasinoWc = Math.min(1000, s.dailyCasinoWc + 1);
		else if (id == EAT_ON) s.dailyEatOn = !s.dailyEatOn;
		else if (id == EAT_G_M) s.dailyEatGoal = Math.max(1, s.dailyEatGoal - 1);
		else if (id == EAT_G_P) s.dailyEatGoal = Math.min(50, s.dailyEatGoal + 1);
		else if (id == EAT_W_M) s.dailyEatWc = Math.max(0, s.dailyEatWc - 1);
		else if (id == EAT_W_P) s.dailyEatWc = Math.min(1000, s.dailyEatWc + 1);
		else if (id == PVP_ON) s.dailyPvpOn = !s.dailyPvpOn;
		else if (id == PVP_G_M) s.dailyPvpGoal = Math.max(1, s.dailyPvpGoal - 1);
		else if (id == PVP_G_P) s.dailyPvpGoal = Math.min(50, s.dailyPvpGoal + 1);
		else if (id == PVP_W_M) s.dailyPvpWc = Math.max(0, s.dailyPvpWc - 1);
		else if (id == PVP_W_P) s.dailyPvpWc = Math.min(1000, s.dailyPvpWc + 1);
		else if (id == TPA) s.tpaEnabled = !s.tpaEnabled;
		else if (id == TPA_M) s.tpaSeconds = Math.max(10, s.tpaSeconds - 10);
		else if (id == TPA_P) s.tpaSeconds = Math.min(300, s.tpaSeconds + 10);
		else if (id == SHOP) s.shopEnabled = !s.shopEnabled;
		else if (id == HOME_M) s.shopHomePrice = Math.max(0, s.shopHomePrice - 5);
		else if (id == HOME_P) s.shopHomePrice = Math.min(10000, s.shopHomePrice + 5);
		else if (id == LUCK_M) s.shopLuckPrice = Math.max(0, s.shopLuckPrice - 5);
		else if (id == LUCK_P) s.shopLuckPrice = Math.min(10000, s.shopLuckPrice + 5);
		else if (id == POWER_M) s.shopPowerPrice = Math.max(0, s.shopPowerPrice - 5);
		else if (id == POWER_P) s.shopPowerPrice = Math.min(10000, s.shopPowerPrice + 5);
		else if (id == CLAN) s.clansEnabled = !s.clansEnabled;
		else if (id == CLAN_P_M) s.clanCreatePrice = Math.max(0, s.clanCreatePrice - 10);
		else if (id == CLAN_P_P) s.clanCreatePrice = Math.min(10000, s.clanCreatePrice + 10);
		else if (id == CLAN_MAX_M) s.clanMaxMembers = Math.max(2, s.clanMaxMembers - 1);
		else if (id == CLAN_MAX_P) s.clanMaxMembers = Math.min(32, s.clanMaxMembers + 1);
		else if (id == AUTO) {
			s.autoEventEnabled = !s.autoEventEnabled;
			s.autoEventNextAt = 0L;
			s.autoEventWarned = false;
		} else if (id == AUTO_M) s.autoEventMinutes = Math.max(10, s.autoEventMinutes - 10);
		else if (id == AUTO_P) s.autoEventMinutes = Math.min(1440, s.autoEventMinutes + 10);
		else if (id == AUTO_TYPE) s.autoEventType = (s.autoEventType + 1) % (EventService.TYPES.length + 1);
		else if (id == AUTO_R_M) s.autoEventReward = Math.max(0, s.autoEventReward - 1);
		else if (id == AUTO_R_P) s.autoEventReward = Math.min(1000, s.autoEventReward + 1);
		else if (id == AUTO_BOX) {
			s.autoBoxEnabled = !s.autoBoxEnabled;
			s.autoBoxNextAt = 0L;
			s.autoBoxWarned = false;
		} else if (id == AUTO_BOX_M) s.autoBoxMinutes = Math.max(10, s.autoBoxMinutes - 10);
		else if (id == AUTO_BOX_P) s.autoBoxMinutes = Math.min(1440, s.autoBoxMinutes + 10);
		else if (id == BOX_COOLDOWN_M) s.boxCooldownMinutes = Math.max(0, s.boxCooldownMinutes - 5);
		else if (id == BOX_COOLDOWN_P) s.boxCooldownMinutes = Math.min(1440, s.boxCooldownMinutes + 5);
		else if (id == WEEK) s.weeklyTopEnabled = !s.weeklyTopEnabled;
		else if (id == WEEK_M) s.weeklyTopWc = Math.max(0, s.weeklyTopWc - 5);
		else if (id == WEEK_P) s.weeklyTopWc = Math.min(10000, s.weeklyTopWc + 5);
		else if (id == HOME_FREE) s.thomeUnlocked = !s.thomeUnlocked;
		else if (id == PRIV_M || id == PRIV_P || id == SIZE_M || id == SIZE_P
				|| (id >= PRIV3_M && id <= PRIV5_P)
				|| id == PACK1_M || id == PACK1_P || id == PACK2_M || id == PACK2_P
				|| id == PACK3_M || id == PACK3_P || id == PACK4_M || id == PACK4_P
				|| id == PACK5_M || id == PACK5_P) {
			if (rank < AdminRanks.TECH) {
				return false;
			}
			if (id == PRIV_M) s.addPrivatePrice(1, -10);
			else if (id == PRIV_P) s.addPrivatePrice(1, 10);
			else if (id == SIZE_M) s.addPrivatePrice(2, -10);
			else if (id == SIZE_P) s.addPrivatePrice(2, 10);
			else if (id == PRIV3_M) s.addPrivatePrice(3, -10);
			else if (id == PRIV3_P) s.addPrivatePrice(3, 10);
			else if (id == PRIV4_M) s.addPrivatePrice(4, -10);
			else if (id == PRIV4_P) s.addPrivatePrice(4, 10);
			else if (id == PRIV5_M) s.addPrivatePrice(5, -10);
			else if (id == PRIV5_P) s.addPrivatePrice(5, 10);
			else if (id == PACK1_M) s.shopBackpackPrice1 = Math.max(0, s.shopBackpackPrice1 - 5);
			else if (id == PACK1_P) s.shopBackpackPrice1 = Math.min(10000, s.shopBackpackPrice1 + 5);
			else if (id == PACK2_M) s.shopBackpackPrice2 = Math.max(0, s.shopBackpackPrice2 - 5);
			else if (id == PACK2_P) s.shopBackpackPrice2 = Math.min(10000, s.shopBackpackPrice2 + 5);
			else if (id == PACK3_M) s.shopBackpackPrice3 = Math.max(0, s.shopBackpackPrice3 - 5);
			else if (id == PACK3_P) s.shopBackpackPrice3 = Math.min(10000, s.shopBackpackPrice3 + 5);
			else if (id == PACK4_M) s.shopBackpackPrice4 = Math.max(0, s.shopBackpackPrice4 - 5);
			else if (id == PACK4_P) s.shopBackpackPrice4 = Math.min(10000, s.shopBackpackPrice4 + 5);
			else if (id == PACK5_M) s.shopBackpackPrice5 = Math.max(0, s.shopBackpackPrice5 - 5);
			else if (id == PACK5_P) s.shopBackpackPrice5 = Math.min(10000, s.shopBackpackPrice5 + 5);
		}
		else return false;
		DataManager.get().saveAll();
		Chat.send(admin, Chat.yellow("Настройки игроков сохранены."));
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
