package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.admincontrol.command.CommandSync;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.BanEntry;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AuctionService;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.RulesService;
import ru.admincontrol.util.TabListColors;
import ru.admincontrol.util.ScreenNav;
import ru.admincontrol.util.TrainingConfig;

public class ServerPanelScreenHandler extends ScreenHandler {
	public static final int LOCK = 0;
	public static final int AMODE = 1;
	public static final int PMODE = 2;
	public static final int SELL_M = 7;
	public static final int SELL_P = 8;
	public static final int AMIN_M = 9;
	public static final int AMIN_P = 10;
	public static final int AMAX_M = 11;
	public static final int AMAX_P = 12;
	public static final int CASINO = 13;
	public static final int CMIN_M = 14;
	public static final int CMIN_P = 15;
	public static final int CMAX_M = 16;
	public static final int CMAX_P = 17;
	public static final int CH_M10 = 18;
	public static final int CH_M = 19;
	public static final int CH_P = 20;
	public static final int CH_P10 = 21;
	public static final int RULES_CREATE = 22;
	public static final int RULES_KEY = 23;
	public static final int RULES_UNKEY = 24;
	public static final int ALL_KICK = 25;
	public static final int OPEN_PRICE = 26;
	public static final int OPEN_CASINO = 27;
	public static final int OPEN_CHECK = 28;
	public static final int TIME_DAY = 29;
	public static final int TIME_NOON = 30;
	public static final int TIME_NIGHT = 31;
	public static final int TIME_MIDNIGHT = 32;
	public static final int WEATHER_CLEAR = 33;
	public static final int WEATHER_RAIN = 34;
	public static final int WEATHER_THUNDER = 35;
	public static final int WORLD_SPAWN = 36;
	public static final int OPEN_CHCOMMANDS = 37;
	public static final int OPEN_EVENT = 38;
	public static final int CHECK_WIGHT = 39;
	public static final int LOCK_ADD_ADMIN = 40;
	public static final int START_SERVER = 41;
	public static final int STOP_SERVER = 42;
	public static final int KILL_LOCK = 43;
	public static final int REVIVE = 44;
	public static final int ALL_CLEAR = 45;
	public static final int ALL_UNADM = 46;
	public static final int ALL_PERM = 47;
	public static final int ALL_UNPERM = 48;
	public static final int UNDEV = 49;
	public static final int PAY_TRANSFERS = 50;
	public static final int AUCTION_LIMIT = 51;
	public static final int OPEN_TRAINING_REWARDS = 52;
	public static final int WARN_BUY = 53;
	public static final int WARN_PRICE_M = 54;
	public static final int WARN_PRICE_P = 55;
	public static final int OPEN_TRAINING_KIT = 56;
	public static final int OPEN_EVENT_KIT_0 = 57;
	public static final int OPEN_EVENT_KIT_1 = 58;
	public static final int OPEN_EVENT_KIT_2 = 59;
	public static final int OPEN_EVENT_KIT_3 = 60;
	public static final int OPEN_EVENT_KIT_4 = 61;
	public static final int OPEN_EVENT_KIT_5 = 67;
	public static final int OPEN_WORLDS = 68;
	public static final int OPEN_PLAY_FEATURES = 69;
	public static final int OPEN_SELL_BAN = 70;
	public static final int OPEN_EVENT_KIT_6 = 71;
	public static final int OPEN_ARANKS = 72;
	public static final int OPEN_ACTIVITY = 73;
	public static final int CASINO_BET_M = 74;
	public static final int CASINO_BET_P = 75;
	public static final int CASINO_BET_M1000 = 77;
	public static final int CASINO_BET_P1000 = 78;
	public static final int OPEN_CASINO_ITEM = 76;
	public static final int MONEY_DELAY_M = 142;
	public static final int MONEY_DELAY_P = 143;
	public static final int ITEM_DELAY_M = 144;
	public static final int ITEM_DELAY_P = 145;
	public static final int CLICK_M = 146;
	public static final int CLICK_P = 147;
	public static final int AC_JAIL_M = 148;
	public static final int AC_JAIL_P = 149;
	public static final int ANTICHEAT = 150;
	public static final int XRAY = 151;
	public static final int BOX_R_M = 152;
	public static final int BOX_R_P = 153;
	public static final int BOX_WARN_M = 154;
	public static final int BOX_WARN_P = 155;
	public static final int BOX_CLEAR_M = 156;
	public static final int BOX_CLEAR_P = 157;
	public static final int BOX_KIT_M = 158;
	public static final int BOX_KIT_P = 159;
	public static final int OPEN_BOX_KIT = 160;
	public static final int BOX_KIT_ADD = 161;
	public static final int BOX_KIT_DEL = 162;
	public static final int CLICK_HOLD_M = 163;
	public static final int CLICK_HOLD_P = 164;
	public static final int XRAY_NEED_M = 165;
	public static final int XRAY_NEED_P = 166;
	public static final int AFK = 167;
	public static final int AFK_M = 168;
	public static final int AFK_P = 169;
	public static final int XRAY_BAN_M = 170;
	public static final int XRAY_BAN_P = 171;
	public static final int CLICK_BAN_M = 172;
	public static final int CLICK_BAN_P = 173;
	public static final int CLAN_HOME_M = 80;
	public static final int CLAN_HOME_P = 81;
	public static final int CLAN_PRIV_M = 82;
	public static final int CLAN_PRIV_P = 83;
	public static final int WH_PRICE_BASE = 84;
	public static final int WH_SLOT_BASE = 94;
	public static final int PACK_SLOT_BASE = 104;
	public static final int BOX_LIFE_M = 114;
	public static final int BOX_LIFE_P = 115;
	public static final int COLOR_ADMIN_M = 116;
	public static final int COLOR_ADMIN_P = 117;
	public static final int COLOR_CUR_M = 118;
	public static final int COLOR_CUR_P = 119;
	public static final int COLOR_ZGA_M = 120;
	public static final int COLOR_ZGA_P = 121;
	public static final int COLOR_GA_M = 122;
	public static final int COLOR_GA_P = 123;
	public static final int CLAN_WIN_M = 124;
	public static final int CLAN_WIN_P = 125;
	public static final int FULLSTOP_CD_M = 126;
	public static final int FULLSTOP_CD_P = 127;
	public static final int FULLSTOP_CD_M30 = 128;
	public static final int FULLSTOP_CD_P30 = 129;
	public static final int TRAIN_DAILY_M = 174;
	public static final int TRAIN_DAILY_P = 175;
	public static final int PROTEIN_ON = 176;
	public static final int CW_STORY_TOGGLE = 177;
	public static final int CW_BUILDER_ENTER = 178;
	public static final int CW_LUA_CREATE = 179;
	public static final int CW_LUA_FIND = 180;
	public static final int CW_LUA_REMOVE = 181;
	public static final int CW_SKINS_RELOAD = 182;
	public static final int CW_RESOURCES_RELOAD = 183;
	public static final int CW_LUA_WAVE = 184;
	public static final int CW_LUA_RESET_POSE = 185;
	public static final int CW_STORY_ANNOUNCE = 186;
	public static final int BOX_COOLDOWN_M = 187;
	public static final int BOX_COOLDOWN_P = 188;
	public static final int BETA_TOGGLE = 189;
	public static final int PROPS = 84;
	public static final int HUNT_KILLS_M = 62;
	public static final int HUNT_KILLS_P = 63;
	public static final int HUNT_TIME_M = 64;
	public static final int HUNT_TIME_P = 65;
	public static final int HUNT_MOB = 66;
	public static final int CWAR_LOOT_M = 130;
	public static final int CWAR_LOOT_P = 131;
	public static final int CWAR_MAX_M = 132;
	public static final int CWAR_MAX_P = 133;
	public static final int CWAR_AUTO = 134;
	public static final int CWAR_MIN_M = 135;
	public static final int CWAR_MIN_P = 136;
	public static final int CWAR_CORE = 137;
	public static final int CWAR_CH_M = 138;
	public static final int CWAR_CH_P = 139;
	public static final int CWAR_CD_M = 140;
	public static final int CWAR_CD_P = 141;

	private final String[] rankTags = new String[AdminRanks.TAG_KEYS.length];
	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);

	public static ServerPanelScreenHandler createClient(int syncId, net.minecraft.entity.player.PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		int[] data = new int[PROPS];
		for (int i = 0; i < n && i < PROPS; i++) {
			data[i] = buf.readVarInt();
		}
		ServerPanelScreenHandler handler = new ServerPanelScreenHandler(syncId, data);
		if (buf.isReadable()) {
			for (int i = 0; i < handler.rankTags.length && buf.isReadable(); i++) {
				handler.rankTags[i] = buf.readString(32);
			}
		}
		return handler;
	}

	public ServerPanelScreenHandler(int syncId, int[] data) {
		super(ModScreenHandlers.SERVER_PANEL, syncId);
		this.addProperties(this.props);
		sync(data);
		fillTags();
	}

	private void fillTags() {
		for (int i = 0; i < AdminRanks.TAG_KEYS.length; i++) {
			this.rankTags[i] = AdminRanks.chatTag(AdminRanks.levelOfTagKey(AdminRanks.TAG_KEYS[i]));
		}
	}

	public String rankTag(int index) {
		if (index < 0 || index >= this.rankTags.length) {
			return "";
		}
		return this.rankTags[index] == null ? "" : this.rankTags[index];
	}

	public static int[] snapshot() {
		return snapshot(null, false);
	}

	public static int[] snapshot(ServerPlayerEntity player, boolean developer) {
		DataManager data = DataManager.get();
		ModSettings s = data == null ? new ModSettings() : data.settings();
		int lockMode = 0;
		if ("kill".equalsIgnoreCase(s.serverLockMode)) {
			lockMode = 2;
		} else if (s.serverLocked || "stop".equalsIgnoreCase(s.serverLockMode)) {
			lockMode = 1;
		}
		int time = 0;
		int weather = 0;
		if (player != null) {
			ServerWorld world = player.getServerWorld();
			time = (int) (world.getTimeOfDay() % 24000L);
			if (world.isThundering()) {
				weather = 2;
			} else if (world.isRaining()) {
				weather = 1;
			}
		}
		int wight = s.wightNicks == null ? 0 : s.wightNicks.size();
		return new int[] {
				lockMode != 0 ? 1 : 0,
				s.adminCheats ? 1 : 0,
				s.playerCheats ? 1 : 0,
				Math.max(1, s.defaultSellPrice),
				s.auctionMinPercent,
				s.auctionMaxPercent,
				s.casinoEnabled ? 1 : 0,
				CasinoService.minTenths(),
				CasinoService.maxTenths(),
				CasinoService.chance(),
				s.rulesEncrypted ? 1 : 0,
				lockMode,
				s.addAdminLockedToStaff ? 1 : 0,
				wight,
				developer ? 1 : 0,
				time,
				weather,
				s.playerTransfers ? 1 : 0,
				s.auctionPriceLimit ? 1 : 0,
				s.warnBuyEnabled ? 1 : 0,
				Math.max(1, s.warnBuyPrice),
				Math.max(1, s.eventHuntKills),
				Math.max(30, s.eventHuntSeconds),
				Math.max(0, Math.min(4, s.eventHuntMob)),
				Math.max(0, s.clanHomePrice),
				Math.max(0, s.clanHomePrivatePrice),
				Math.max(0, s.clanWarehousePrice1),
				Math.max(0, s.clanWarehousePrice2),
				Math.max(0, s.clanWarehousePrice3),
				Math.max(0, s.clanWarehousePrice4),
				Math.max(0, s.clanWarehousePrice5),
				Math.max(9, s.clanWarehouseSlots1),
				Math.max(9, s.clanWarehouseSlots2),
				Math.max(9, s.clanWarehouseSlots3),
				Math.max(9, s.clanWarehouseSlots4),
				Math.max(9, s.clanWarehouseSlots5),
				Math.max(9, s.backpackSlots1),
				Math.max(9, s.backpackSlots2),
				Math.max(9, s.backpackSlots3),
				Math.max(9, s.backpackSlots4),
				Math.max(9, s.backpackSlots5),
				CasinoService.minBet(),
				Math.max(15, s.clanWarLootSeconds),
				Math.max(3, s.clanWarMaxMinutes),
				s.clanWarAutoApprove ? 1 : 0,
				Math.max(1, s.clanWarMinOnline),
				coreIndex(s),
				Math.max(30, s.clanWarChallengeSeconds),
				Math.max(0, s.clanWarCooldownMinutes),
				Math.max(0, s.casinoMoneyDelaySec),
				Math.max(0, s.casinoItemDelaySec),
				Math.max(0, s.anticheatClicksPerSec),
				Math.max(1, s.anticheatJailMinutes),
				s.anticheatEnabled ? 1 : 0,
				s.anticheatXray ? 1 : 0,
				Math.max(16, s.boxRadius),
				Math.max(1, s.boxWarnMinutes),
				Math.max(15, s.boxClearSeconds),
				Math.max(0, s.boxKitEditIndex),
				Math.max(1, s.boxKits == null ? 1 : s.boxKits.size()),
				Math.max(1, s.anticheatClickHoldSec),
				Math.max(12, s.anticheatXrayHidden),
				s.afkKickEnabled ? 1 : 0,
				Math.max(1, s.afkMinutes),
				Math.max(1, s.anticheatXrayBanDays),
				Math.max(1, s.anticheatClickBanDays),
				Math.max(1, s.boxLifetimeMinutes),
				s.colorAdmin & 0xFFF,
				s.colorCurator & 0xFFF,
				s.colorZga & 0xFFF,
				s.colorGa & 0xFFF,
				Math.max(0, s.clanWinWc),
				s.fullStopCooldownSec,
				(s.colorAdmin >> 12) & 0xFFF,
				(s.colorCurator >> 12) & 0xFFF,
				(s.colorZga >> 12) & 0xFFF,
				(s.colorGa >> 12) & 0xFFF,
				ru.admincontrol.util.NickColors.blogger() & 0xFFF,
				(ru.admincontrol.util.NickColors.blogger() >> 12) & 0xFFF,
				Math.max(1, Math.min(50, s.trainingDailyMax <= 0 ? 5 : s.trainingDailyMax)),
				s.proteinEnabled ? 1 : 0,
				s.cwStoryEnabled ? 1 : 0,
				Math.max(0, s.boxCooldownMinutes),
				s.betaFeaturesEnabled ? 1 : 0
		};
	}

	/** Свойства экрана передаются как short — цвет делится на две половины по 12 бит. */
	private int color(int lo, int hi) {
		return ((this.props.get(hi) & 0xFFF) << 12) | (this.props.get(lo) & 0xFFF);
	}

	public boolean locked() {
		return this.props.get(0) != 0;
	}

	public boolean amode() {
		return this.props.get(1) != 0;
	}

	public boolean pmode() {
		return this.props.get(2) != 0;
	}

	public int sellPrice() {
		return this.props.get(3);
	}

	public int aucMin() {
		return this.props.get(4);
	}

	public int aucMax() {
		return this.props.get(5);
	}

	public boolean casinoOn() {
		return this.props.get(6) != 0;
	}

	public int casinoMin() {
		return this.props.get(7);
	}

	public int casinoMax() {
		return this.props.get(8);
	}

	public int chance() {
		return this.props.get(9);
	}

	public int casinoMinBet() {
		return Math.max(0, this.props.get(41));
	}

	public boolean rulesEnc() {
		return this.props.get(10) != 0;
	}

	public int lockMode() {
		return this.props.get(11);
	}

	public boolean addAdminLocked() {
		return this.props.get(12) != 0;
	}

	public int wightCount() {
		return this.props.get(13);
	}

	public boolean isDev() {
		return this.props.get(14) != 0;
	}

	public int timeOfDay() {
		return this.props.get(15);
	}

	public int weather() {
		return this.props.get(16);
	}

	public boolean transfers() {
		return this.props.get(17) != 0;
	}

	public boolean auctionLimit() {
		return this.props.get(18) != 0;
	}

	public boolean warnBuy() {
		return this.props.get(19) != 0;
	}

	public int warnPrice() {
		return this.props.get(20);
	}

	public int huntKills() {
		return this.props.get(21);
	}

	public int huntSeconds() {
		return this.props.get(22);
	}

	public int huntMob() {
		return this.props.get(23);
	}

	public String huntMobLabel() {
		int i = Math.max(0, Math.min(EventService.HUNT_MOBS.length - 1, huntMob()));
		return EventService.HUNT_MOBS[i];
	}

	public int clanHome() { return this.props.get(24); }
	public int clanPriv() { return this.props.get(25); }
	public int whPrice(int level) { return this.props.get(25 + Math.max(1, Math.min(5, level))); }
	public int whSlots(int level) { return this.props.get(30 + Math.max(1, Math.min(5, level))); }
	public int packSlots(int level) { return this.props.get(35 + Math.max(1, Math.min(5, level))); }

	public int warLoot() { return this.props.get(42); }
	public int warMax() { return this.props.get(43); }
	public boolean warAuto() { return this.props.get(44) != 0; }
	public int warMin() { return this.props.get(45); }
	public String warCore() {
		int i = Math.max(0, Math.min(ru.admincontrol.util.ClanWarService.CORE_BLOCKS.length - 1, this.props.get(46)));
		return ru.admincontrol.util.ClanWarService.CORE_BLOCKS[i];
	}
	public int warChallenge() { return this.props.get(47); }
	public int warCooldown() { return this.props.get(48); }
	public int moneyDelay() { return this.props.get(49); }
	public int itemDelay() { return this.props.get(50); }
	public int clickLimit() { return this.props.get(51); }
	public int acJail() { return this.props.get(52); }
	public boolean anticheatOn() { return this.props.get(53) != 0; }
	public boolean xrayOn() { return this.props.get(54) != 0; }
	public int boxRadius() { return this.props.get(55); }
	public int boxWarn() { return this.props.get(56); }
	public int boxClear() { return this.props.get(57); }
	public int boxKit() { return this.props.get(58); }
	public int boxKits() { return Math.max(1, this.props.get(59)); }
	public int clickHold() { return Math.max(1, this.props.get(60)); }
	public int xrayNeed() { return Math.max(12, this.props.get(61)); }
	public boolean afkOn() { return this.props.get(62) != 0; }
	public int afkMin() { return Math.max(1, this.props.get(63)); }
	public int xrayBanDays() { return Math.max(1, this.props.get(64)); }
	public int clickBanDays() { return Math.max(1, this.props.get(65)); }
	public int boxLife() { return Math.max(1, this.props.get(66)); }
	public int boxCooldown() { return Math.max(0, this.props.get(82)); }
	public int colorAdmin() { return color(67, 73); }
	public int colorCurator() { return color(68, 74); }
	public int colorZga() { return color(69, 75); }
	public int colorGa() { return color(70, 76); }
	public int colorBlogger() { return color(77, 78); }
	public int clanWinWc() { return Math.max(0, this.props.get(71)); }
	public int fullStopCooldown() { return this.props.get(72); }
	public int trainingDailyMax() { return Math.max(1, this.props.get(79)); }
	public boolean proteinOn() { return this.props.get(80) != 0; }
	public boolean cwStoryOn() { return this.props.get(81) != 0; }
	public boolean betaOn() { return this.props.get(83) != 0; }

	public void applyNickColor(ServerPlayerEntity player, String kind, String value) {
		Integer rgb = ru.admincontrol.util.NickColors.parse(value);
		if (rgb == null) {
			Chat.send(player, Chat.red("Цвет вводится текстом: #dd1f40"));
			return;
		}
		ModSettings s = DataManager.get().settings();
		String who;
		switch (kind) {
			case "nick_color_admin" -> {
				s.colorAdmin = rgb;
				who = "обычных админов";
			}
			case "nick_color_curator" -> {
				s.colorCurator = rgb;
				who = "куратора";
			}
			case "nick_color_zga" -> {
				s.colorZga = rgb;
				who = "ЗГА/тех";
			}
			case "nick_color_ga" -> {
				s.colorGa = rgb;
				who = "ГА/разраба";
			}
			case "nick_color_blogger" -> {
				s.colorBlogger = rgb;
				who = "блогера";
			}
			default -> {
				return;
			}
		}
		DataManager.get().saveAll();
		TabListColors.refresh(player.getServer());
		Chat.send(player, Chat.yellow("Цвет " + who + ": " + ru.admincontrol.util.NickColors.hex(rgb)));
		sync(snapshot(player, true));
	}

	public void applyRankTag(ServerPlayerEntity player, String kind, String value) {
		if (kind == null || !kind.startsWith("rank_tag_")) {
			return;
		}
		String key = kind.substring("rank_tag_".length()).toLowerCase(java.util.Locale.ROOT);
		boolean known = false;
		for (String id : AdminRanks.TAG_KEYS) {
			if (id.equals(key)) {
				known = true;
				break;
			}
		}
		if (!known) {
			return;
		}
		String text = value == null ? "" : value.trim();
		if (text.isBlank() || text.length() > 24) {
			Chat.send(player, Chat.red("Впишите текст роли, например Модератор"));
			return;
		}
		ModSettings s = DataManager.get().settings();
		if (s.rankChatTags == null) {
			s.rankChatTags = new java.util.LinkedHashMap<>();
		}
		s.rankChatTags.put(key, text);
		DataManager.get().saveAll();
		fillTags();
		TabListColors.refresh(player.getServer());
		Chat.send(player, Chat.yellow("Роль в [] для " + key + ": " + text));
		sync(snapshot(player, true));
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		int rank = DataManager.get().getAdminLevel(serverPlayer);
		if (rank < AdminRanks.CURATOR) {
			return false;
		}
		if (id == OPEN_PLAY_FEATURES) {
			ScreenNav.open(serverPlayer, new PlayFeaturesFactory());
			return true;
		}
		if (rank < AdminRanks.ZGA) {
			return false;
		}
		if (id == OPEN_ACTIVITY) {
			ScreenNav.open(serverPlayer, new ActivitySettingsFactory());
			return true;
		}
		if (rank < AdminRanks.TECH) {
			return false;
		}
		if (devOnly(id) && rank < AdminRanks.DEV) {
			return false;
		}
		ModSettings s = DataManager.get().settings();
		if (id == BETA_TOGGLE) {
			s.betaFeaturesEnabled = !s.betaFeaturesEnabled;
			s.botTrainingEnabled = s.betaFeaturesEnabled;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Бета-версии (тренировки с ботами, дуэли): " + onOff(s.betaFeaturesEnabled)));
		} else if (id == CW_STORY_TOGGLE) {
			s.cwStoryEnabled = !s.cwStoryEnabled;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Сюжет CW Expedition: " + onOff(s.cwStoryEnabled)));
		} else if (id == CW_STORY_ANNOUNCE) {
			Chat.broadcast(serverPlayer.getServer(), net.minecraft.text.Text.literal("[CW Expedition] Сюжет " + (s.cwStoryEnabled ? "открыт для прохождения." : "временно отключён администрацией.")));
			ru.admincontrol.cw.CwAudit.write(serverPlayer.getCommandSource(), "story announce", "global", s.cwStoryEnabled ? "enabled" : "disabled");
		} else if (id == CW_BUILDER_ENTER) {
			serverPlayer.closeHandledScreen();
			String error = ru.admincontrol.cw.CwBuilderWorld.inside(serverPlayer)
					? ru.admincontrol.cw.CwBuilderWorld.leave(serverPlayer) : ru.admincontrol.cw.CwBuilderWorld.enter(serverPlayer);
			Chat.send(serverPlayer, error == null ? Chat.green("Переход выполнен.") : Chat.red(error));
			return true;
		} else if (id == CW_LUA_CREATE) {
			String error = ru.admincontrol.util.CwNpcService.validate("Luya");
			if (error == null) error = ru.admincontrol.util.NpcService.spawnSkinned(serverPlayer, "Luya", "Luya");
			if (error == null) ru.admincontrol.util.NpcStore.setCwMetadata("Luya", "Luya", "Luya", true);
			Chat.send(serverPlayer, error == null ? Chat.green("Luya создана со скином Luya.png.") : Chat.red(error));
		} else if (id == CW_LUA_FIND) {
			String error = ru.admincontrol.util.NpcService.bringHere(serverPlayer, "Luya");
			Chat.send(serverPlayer, error == null ? Chat.green("Luya перемещена к вам.") : Chat.red(error));
		} else if (id == CW_LUA_REMOVE) {
			String error = ru.admincontrol.util.NpcService.remove(serverPlayer.getServer(), "Luya");
			Chat.send(serverPlayer, error == null ? Chat.green("Luya удалена.") : Chat.red(error));
		} else if (id == CW_SKINS_RELOAD) {
			String error = ru.admincontrol.util.CwNpcService.reload();
			ru.admincontrol.util.CwNpcService.syncAll(serverPlayer.getServer());
			Chat.send(serverPlayer, error == null ? Chat.green("Скины NPC перечитаны.") : Chat.red(error));
		} else if (id == CW_RESOURCES_RELOAD) {
			serverPlayer.getServer().getCommandManager().executeWithPrefix(serverPlayer.getCommandSource(), "cw story resources reload");
		} else if (id == CW_LUA_WAVE || id == CW_LUA_RESET_POSE) {
			var lua = ru.admincontrol.util.NpcService.find(serverPlayer.getServer(), "Luya");
			String error = id == CW_LUA_WAVE
					? ru.admincontrol.cw.CwNpcPoseService.animate(lua, "wave_right", true)
					: ru.admincontrol.cw.CwNpcPoseService.setPose(lua, "straight");
			if (id == CW_LUA_RESET_POSE && error == null) ru.admincontrol.cw.CwNpcPoseService.animate(lua, "none", false);
			Chat.send(serverPlayer, error == null ? Chat.green(id == CW_LUA_WAVE ? "Luya машет правой рукой (цикл)." : "Поза и анимация Luya сброшены.") : Chat.red(error));
		} else if (id == LOCK || id == STOP_SERVER) {
			applyLock(serverPlayer, "stop", "панель сервера");
		} else if (id == START_SERVER) {
			s.serverLocked = false;
			s.serverLockMode = "none";
			s.serverLockReason = "";
			DataManager.get().saveAll();
			Chat.broadcast(serverPlayer.getServer(), Chat.red("Администратор "
					+ serverPlayer.getGameProfile().getName() + " открыл сервер."));
		} else if (id == AMODE) {
			s.adminCheats = !s.adminCheats;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Читы администрации: " + onOff(s.adminCheats)));
			CommandSync.refreshAll(serverPlayer.getServer());
		} else if (id == PMODE) {
			s.playerCheats = !s.playerCheats;
			DataManager.get().saveAll();
			Chat.broadcast(serverPlayer.getServer(), Chat.red("Администратор "
					+ serverPlayer.getGameProfile().getName() + " "
					+ (s.playerCheats ? "включил" : "выключил") + " /pmode для игроков"));
			CommandSync.refreshAll(serverPlayer.getServer());
		} else if (id == SELL_M) {
			s.defaultSellPrice = Math.max(1, s.defaultSellPrice - 1);
			DataManager.get().saveAll();
		} else if (id == SELL_P) {
			s.defaultSellPrice = Math.min(1_000_000_000, s.defaultSellPrice + 1);
			DataManager.get().saveAll();
		} else if (id == AMIN_M || id == AMIN_P || id == AMAX_M || id == AMAX_P) {
			shiftAuction(s, id);
		} else if (id == CASINO) {
			CasinoService.setEnabled(!CasinoService.enabled());
			Chat.send(serverPlayer, Chat.yellow("Казино: " + onOff(CasinoService.enabled())));
		} else if (id == CMIN_M) {
			CasinoService.shiftMin(-1);
		} else if (id == CMIN_P) {
			CasinoService.shiftMin(1);
		} else if (id == CMAX_M) {
			CasinoService.shiftMax(-1);
		} else if (id == CMAX_P) {
			CasinoService.shiftMax(1);
		} else if (id == CH_M10) {
			CasinoService.shiftChance(-10);
		} else if (id == CH_M) {
			CasinoService.shiftChance(-1);
		} else if (id == CH_P) {
			CasinoService.shiftChance(1);
		} else if (id == CH_P10) {
			CasinoService.shiftChance(10);
		} else if (id == CASINO_BET_M || id == CASINO_BET_P || id == CASINO_BET_M1000 || id == CASINO_BET_P1000) {
			if (id == CASINO_BET_M) CasinoService.shiftMinBet(-100);
			else if (id == CASINO_BET_P) CasinoService.shiftMinBet(100);
			else if (id == CASINO_BET_M1000) CasinoService.shiftMinBet(-10000);
			else CasinoService.shiftMinBet(10000);
		} else if (id == RULES_CREATE) {
			RulesService.createDefault();
			Chat.send(serverPlayer, Chat.green("Создан файл rules.txt"));
		} else if (id == RULES_KEY) {
			RulesService.encrypt();
			Chat.send(serverPlayer, Chat.green("rules.txt зашифрован."));
		} else if (id == RULES_UNKEY) {
			RulesService.decrypt();
			Chat.send(serverPlayer, Chat.green("rules.txt расшифрован."));
		} else if (id == ALL_KICK) {
			kickAll(serverPlayer, AdminRanks.TECH);
		} else if (id == OPEN_PRICE) {
			ScreenNav.open(serverPlayer, new SetPriceFactory());
			return true;
		} else if (id == OPEN_SELL_BAN) {
			ScreenNav.open(serverPlayer, new SellBanFactory());
			return true;
		} else if (id == OPEN_CASINO) {
			ScreenNav.open(serverPlayer, new CasinoFactory());
			return true;
		} else if (id == OPEN_CASINO_ITEM) {
			if (!this.isDev()) {
				return true;
			}
			ScreenNav.open(serverPlayer, new CasinoItemFactory());
			return true;
		} else if (id == OPEN_CHECK) {
			ScreenNav.open(serverPlayer, new CheckFeedFactory());
			return true;
		} else if (id == TIME_DAY) {
			setTime(serverPlayer, 1000L, "день");
		} else if (id == TIME_NOON) {
			setTime(serverPlayer, 6000L, "полдень");
		} else if (id == TIME_NIGHT) {
			setTime(serverPlayer, 13000L, "ночь");
		} else if (id == TIME_MIDNIGHT) {
			setTime(serverPlayer, 18000L, "полночь");
		} else if (id == WEATHER_CLEAR) {
			setWeather(serverPlayer, false, false, "ясно");
		} else if (id == WEATHER_RAIN) {
			setWeather(serverPlayer, true, false, "дождь");
		} else if (id == WEATHER_THUNDER) {
			setWeather(serverPlayer, true, true, "гроза");
		} else if (id == WORLD_SPAWN) {
			ServerWorld world = serverPlayer.getServerWorld();
			world.setSpawnPos(serverPlayer.getBlockPos(), serverPlayer.getYaw());
			Chat.broadcast(serverPlayer.getServer(), Chat.red("Администратор "
					+ serverPlayer.getGameProfile().getName() + " изменил общую точку появления в мире"));
			Chat.send(serverPlayer, Chat.green("Точка спавна: " + serverPlayer.getBlockPos().toShortString()));
		} else if (id == OPEN_CHCOMMANDS) {
			ScreenNav.open(serverPlayer, new ChCommandsFactory());
			return true;
		} else if (id == OPEN_ARANKS) {
			ScreenNav.open(serverPlayer, new RankPermsFactory(-1));
			return true;
		} else if (id == OPEN_EVENT) {
			ScreenNav.open(serverPlayer, new EventFactory());
			return true;
		} else if (id == CHECK_WIGHT) {
			var nicks = DataManager.get().settings().wightNicks;
			Chat.send(serverPlayer, Chat.gold("Whitelist: " + (nicks == null || nicks.isEmpty()
					? "пусто" : String.join(", ", nicks))));
		} else if (id == LOCK_ADD_ADMIN) {
			s.addAdminLockedToStaff = !s.addAdminLockedToStaff;
			DataManager.get().saveAll();
			CommandSync.refreshAll(serverPlayer.getServer());
			Chat.send(serverPlayer, Chat.yellow("/add_admin для игроков: "
					+ (s.addAdminLockedToStaff ? "закрыт" : "открыт")));
		} else if (id == KILL_LOCK) {
			applyLock(serverPlayer, "kill", "панель /dserver");
		} else if (id == REVIVE) {
			s.serverLocked = false;
			s.serverLockMode = "none";
			s.serverLockReason = "";
			DataManager.get().saveAll();
			Chat.broadcast(serverPlayer.getServer(), Chat.red("Сервер открыт разработчиком."));
		} else if (id == ALL_CLEAR) {
			int n = 0;
			for (ServerPlayerEntity other : serverPlayer.getServer().getPlayerManager().getPlayerList()) {
				if (other.getUuid().equals(serverPlayer.getUuid())) {
					continue;
				}
				other.getInventory().clear();
				n++;
			}
			Chat.send(serverPlayer, Chat.yellow("Инвентари очищены: " + n));
		} else if (id == ALL_UNADM) {
			stripAdmins(serverPlayer);
		} else if (id == ALL_PERM) {
			massPermaban(serverPlayer);
		} else if (id == ALL_UNPERM) {
			int n = DataManager.get().removeMassPermabans();
			Chat.send(serverPlayer, Chat.yellow("Снято массовых пермабанов: " + n));
		} else if (id == UNDEV) {
			DataManager.get().setHeadAdmin(serverPlayer);
			CommandSync.refreshAll(serverPlayer.getServer());
			TabListColors.refresh(serverPlayer.getServer());
			Chat.send(serverPlayer, Chat.gold("Права разработчика сняты. Вы снова Главный админ (ГА)."));
			serverPlayer.closeHandledScreen();
			return true;
		} else if (id == PAY_TRANSFERS) {
			s.playerTransfers = !s.playerTransfers;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Переводы игроков: " + onOff(s.playerTransfers)));
		} else if (id == AUCTION_LIMIT) {
			s.auctionPriceLimit = !s.auctionPriceLimit;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Ограничение цен аукциона: " + onOff(s.auctionPriceLimit)));
		} else if (id == OPEN_TRAINING_REWARDS) {
			ScreenNav.open(serverPlayer, new TrainingRewardsFactory());
			return true;
		} else if (id == WARN_BUY) {
			if (rank < AdminRanks.DEV) {
				return false;
			}
			s.warnBuyEnabled = !s.warnBuyEnabled;
			DataManager.get().saveAll();
			Chat.send(serverPlayer, Chat.yellow("Снятие варна за WC: " + onOff(s.warnBuyEnabled)));
		} else if (id == WARN_PRICE_M || id == WARN_PRICE_P) {
			if (rank < AdminRanks.DEV) {
				return false;
			}
			int delta = id == WARN_PRICE_M ? -10 : 10;
			s.warnBuyPrice = Math.max(1, Math.min(1_000_000, s.warnBuyPrice + delta));
			DataManager.get().saveAll();
		} else if (id == OPEN_TRAINING_KIT) {
			ScreenNav.open(serverPlayer, new TrainingKitFactory("training"));
			return true;
		} else if (id == OPEN_EVENT_KIT_0 || id == OPEN_EVENT_KIT_1 || id == OPEN_EVENT_KIT_2
				|| id == OPEN_EVENT_KIT_3 || id == OPEN_EVENT_KIT_4 || id == OPEN_EVENT_KIT_5
				|| id == OPEN_EVENT_KIT_6) {
			String key = id == OPEN_EVENT_KIT_0 ? "0" : (id == OPEN_EVENT_KIT_1 ? "1"
					: (id == OPEN_EVENT_KIT_2 ? "2" : (id == OPEN_EVENT_KIT_3 ? "3"
					: (id == OPEN_EVENT_KIT_4 ? "4" : (id == OPEN_EVENT_KIT_5 ? "5" : "6")))));
			ScreenNav.open(serverPlayer, new TrainingKitFactory(key));
			return true;
		} else if (id == OPEN_WORLDS) {
			ScreenNav.open(serverPlayer, new WorldsFactory());
			return true;
		} else if (id == HUNT_KILLS_M || id == HUNT_KILLS_P) {
			s.eventHuntKills = Math.max(1, Math.min(200, s.eventHuntKills + (id == HUNT_KILLS_M ? -1 : 1)));
			DataManager.get().saveAll();
		} else if (id == HUNT_TIME_M || id == HUNT_TIME_P) {
			s.eventHuntSeconds = Math.max(30, Math.min(900, s.eventHuntSeconds + (id == HUNT_TIME_M ? -15 : 15)));
			DataManager.get().saveAll();
		} else if (id == HUNT_MOB) {
			s.eventHuntMob = (s.eventHuntMob + 1) % EventService.HUNT_MOBS.length;
			DataManager.get().saveAll();
			TrainingConfig.refreshHuntDefaultKit();
			Chat.send(serverPlayer, Chat.yellow("Охота: мобы «" + EventService.huntMobLabel() + "», набор обновлён."));
		} else if (id == CLAN_HOME_M || id == CLAN_HOME_P || id == CLAN_PRIV_M || id == CLAN_PRIV_P
				|| (id >= WH_PRICE_BASE && id < WH_PRICE_BASE + 10)
				|| (id >= WH_SLOT_BASE && id < WH_SLOT_BASE + 10)
				|| (id >= PACK_SLOT_BASE && id < PACK_SLOT_BASE + 10)) {
			if (id >= PACK_SLOT_BASE && id < PACK_SLOT_BASE + 10) {
				int lv = (id - PACK_SLOT_BASE) / 2 + 1;
				boolean plus = (id - PACK_SLOT_BASE) % 2 == 1;
				setPackSlot(s, lv, Math.max(9, Math.min(54, packSlot(s, lv) + (plus ? 9 : -9))));
			} else {
				if (rank < AdminRanks.DEV) {
					return false;
				}
				if (id == CLAN_HOME_M) s.clanHomePrice = Math.max(0, s.clanHomePrice - 10);
				else if (id == CLAN_HOME_P) s.clanHomePrice = Math.min(10000, s.clanHomePrice + 10);
				else if (id == CLAN_PRIV_M) s.clanHomePrivatePrice = Math.max(0, s.clanHomePrivatePrice - 10);
				else if (id == CLAN_PRIV_P) s.clanHomePrivatePrice = Math.min(10000, s.clanHomePrivatePrice + 10);
				else if (id >= WH_PRICE_BASE && id < WH_PRICE_BASE + 10) {
					int lv = (id - WH_PRICE_BASE) / 2 + 1;
					boolean plus = (id - WH_PRICE_BASE) % 2 == 1;
					setWhPrice(s, lv, Math.max(0, Math.min(10000, whPriceOf(s, lv) + (plus ? 10 : -10))));
				} else {
					int lv = (id - WH_SLOT_BASE) / 2 + 1;
					boolean plus = (id - WH_SLOT_BASE) % 2 == 1;
					setWhSlot(s, lv, Math.max(9, Math.min(54, whSlotOf(s, lv) + (plus ? 9 : -9))));
				}
			}
			DataManager.get().saveAll();
		} else if (id == CWAR_LOOT_M || id == CWAR_LOOT_P || id == CWAR_MAX_M || id == CWAR_MAX_P
				|| id == CWAR_AUTO || id == CWAR_MIN_M || id == CWAR_MIN_P || id == CWAR_CORE
				|| id == CWAR_CH_M || id == CWAR_CH_P || id == CWAR_CD_M || id == CWAR_CD_P) {
			if (rank < AdminRanks.DEV) {
				return false;
			}
			if (id == CWAR_LOOT_M) s.clanWarLootSeconds = Math.max(15, s.clanWarLootSeconds - 15);
			else if (id == CWAR_LOOT_P) s.clanWarLootSeconds = Math.min(600, s.clanWarLootSeconds + 15);
			else if (id == CWAR_MAX_M) s.clanWarMaxMinutes = Math.max(3, s.clanWarMaxMinutes - 1);
			else if (id == CWAR_MAX_P) s.clanWarMaxMinutes = Math.min(120, s.clanWarMaxMinutes + 1);
			else if (id == CWAR_AUTO) s.clanWarAutoApprove = !s.clanWarAutoApprove;
			else if (id == CWAR_MIN_M) s.clanWarMinOnline = Math.max(1, s.clanWarMinOnline - 1);
			else if (id == CWAR_MIN_P) s.clanWarMinOnline = Math.min(20, s.clanWarMinOnline + 1);
			else if (id == CWAR_CORE) ru.admincontrol.util.ClanWarService.cycleCore();
			else if (id == CWAR_CH_M) s.clanWarChallengeSeconds = Math.max(30, s.clanWarChallengeSeconds - 30);
			else if (id == CWAR_CH_P) s.clanWarChallengeSeconds = Math.min(900, s.clanWarChallengeSeconds + 30);
			else if (id == CWAR_CD_M) s.clanWarCooldownMinutes = Math.max(0, s.clanWarCooldownMinutes - 5);
			else if (id == CWAR_CD_P) s.clanWarCooldownMinutes = Math.min(240, s.clanWarCooldownMinutes + 5);
			DataManager.get().saveAll();
			if (id == CWAR_AUTO) {
				Chat.send(serverPlayer, Chat.yellow("Авто-битва кланов: " + onOff(s.clanWarAutoApprove)));
			}
			if (id == CWAR_CORE) {
				Chat.send(serverPlayer, Chat.yellow("Ядро битвы: " + s.clanWarCoreBlock));
			}
		} else if (id == MONEY_DELAY_M || id == MONEY_DELAY_P || id == ITEM_DELAY_M || id == ITEM_DELAY_P
				|| id == CLICK_M || id == CLICK_P || id == AC_JAIL_M || id == AC_JAIL_P
				|| id == ANTICHEAT || id == XRAY
				|| id == BOX_R_M || id == BOX_R_P || id == BOX_WARN_M || id == BOX_WARN_P
				|| id == BOX_CLEAR_M || id == BOX_CLEAR_P || id == BOX_LIFE_M || id == BOX_LIFE_P
				|| id == CLAN_WIN_M || id == CLAN_WIN_P
				|| id == FULLSTOP_CD_M || id == FULLSTOP_CD_P
				|| id == FULLSTOP_CD_M30 || id == FULLSTOP_CD_P30
				|| id == TRAIN_DAILY_M || id == TRAIN_DAILY_P || id == PROTEIN_ON
				|| id == BOX_KIT_M || id == BOX_KIT_P
				|| id == OPEN_BOX_KIT || id == BOX_KIT_ADD || id == BOX_KIT_DEL
				|| id == CLICK_HOLD_M || id == CLICK_HOLD_P || id == XRAY_NEED_M || id == XRAY_NEED_P
				|| id == AFK || id == AFK_M || id == AFK_P
				|| id == XRAY_BAN_M || id == XRAY_BAN_P
				|| id == CLICK_BAN_M || id == CLICK_BAN_P) {
			if (rank < AdminRanks.DEV) {
				return false;
			}
			if (id == MONEY_DELAY_M) CasinoService.shiftDelay(false, -1);
			else if (id == MONEY_DELAY_P) CasinoService.shiftDelay(false, 1);
			else if (id == ITEM_DELAY_M) CasinoService.shiftDelay(true, -1);
			else if (id == ITEM_DELAY_P) CasinoService.shiftDelay(true, 1);
			else if (id == CLICK_M) {
				int next = s.anticheatClicksPerSec <= 0 ? 0 : s.anticheatClicksPerSec - 1;
				if (next > 0 && next < 25) {
					next = 0;
				}
				s.anticheatClicksPerSec = next;
				DataManager.get().saveAll();
			} else if (id == CLICK_P) {
				int next = s.anticheatClicksPerSec <= 0 ? 25 : s.anticheatClicksPerSec + 1;
				s.anticheatClicksPerSec = Math.min(80, next);
				DataManager.get().saveAll();
			} else if (id == CLICK_HOLD_M) {
				s.anticheatClickHoldSec = Math.max(1, s.anticheatClickHoldSec - 1);
				DataManager.get().saveAll();
			} else if (id == CLICK_HOLD_P) {
				s.anticheatClickHoldSec = Math.min(10, s.anticheatClickHoldSec + 1);
				DataManager.get().saveAll();
			} else if (id == XRAY_NEED_M) {
				s.anticheatXrayHidden = Math.max(8, s.anticheatXrayHidden - 1);
				DataManager.get().saveAll();
			} else if (id == XRAY_NEED_P) {
				s.anticheatXrayHidden = Math.min(40, s.anticheatXrayHidden + 1);
				DataManager.get().saveAll();
			} else if (id == AFK) {
				s.afkKickEnabled = !s.afkKickEnabled;
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Проверка АФК: " + onOff(s.afkKickEnabled)));
			} else if (id == AFK_M) {
				s.afkMinutes = Math.max(1, s.afkMinutes - 1);
				DataManager.get().saveAll();
			} else if (id == AFK_P) {
				s.afkMinutes = Math.min(60, s.afkMinutes + 1);
				DataManager.get().saveAll();
			} else if (id == XRAY_BAN_M) {
				s.anticheatXrayBanDays = Math.max(1, s.anticheatXrayBanDays - 1);
				ru.admincontrol.util.AntiCheatRules.setBanDays(true, s.anticheatXrayBanDays);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бан за читы: " + s.anticheatXrayBanDays + " дн"));
			} else if (id == XRAY_BAN_P) {
				s.anticheatXrayBanDays = Math.min(90, s.anticheatXrayBanDays + 1);
				ru.admincontrol.util.AntiCheatRules.setBanDays(true, s.anticheatXrayBanDays);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бан за читы: " + s.anticheatXrayBanDays + " дн"));
			} else if (id == CLICK_BAN_M) {
				s.anticheatClickBanDays = Math.max(1, s.anticheatClickBanDays - 1);
				ru.admincontrol.util.AntiCheatRules.setBanDays(false, s.anticheatClickBanDays);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бан за автокликер (3-й раз): " + s.anticheatClickBanDays + " дн"));
			} else if (id == CLICK_BAN_P) {
				s.anticheatClickBanDays = Math.min(30, s.anticheatClickBanDays + 1);
				ru.admincontrol.util.AntiCheatRules.setBanDays(false, s.anticheatClickBanDays);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бан за автокликер (3-й раз): " + s.anticheatClickBanDays + " дн"));
			} else if (id == AC_JAIL_M) {
				s.anticheatJailMinutes = Math.max(1, s.anticheatJailMinutes - 1);
				DataManager.get().saveAll();
			} else if (id == AC_JAIL_P) {
				s.anticheatJailMinutes = Math.min(60, s.anticheatJailMinutes + 1);
				DataManager.get().saveAll();
			} else if (id == ANTICHEAT) {
				s.anticheatEnabled = !s.anticheatEnabled;
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Античит: " + onOff(s.anticheatEnabled)));
			} else if (id == XRAY) {
				s.anticheatXray = !s.anticheatXray;
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Проверка x-ray: " + onOff(s.anticheatXray)));
			} else if (id == BOX_R_M) {
				s.boxRadius = Math.max(16, s.boxRadius - 10);
				DataManager.get().saveAll();
			} else if (id == BOX_R_P) {
				s.boxRadius = Math.min(5000, s.boxRadius + 10);
				DataManager.get().saveAll();
			} else if (id == BOX_WARN_M) {
				s.boxWarnMinutes = Math.max(1, s.boxWarnMinutes - 1);
				DataManager.get().saveAll();
			} else if (id == BOX_WARN_P) {
				s.boxWarnMinutes = Math.min(60, s.boxWarnMinutes + 1);
				DataManager.get().saveAll();
			} else if (id == BOX_CLEAR_M) {
				s.boxClearSeconds = Math.max(15, s.boxClearSeconds - 15);
				DataManager.get().saveAll();
			} else if (id == BOX_CLEAR_P) {
				s.boxClearSeconds = Math.min(600, s.boxClearSeconds + 15);
				DataManager.get().saveAll();
			} else if (id == BOX_LIFE_M) {
				s.boxLifetimeMinutes = Math.max(1, s.boxLifetimeMinutes - 1);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бокс живёт " + s.boxLifetimeMinutes + " мин после появления."));
			} else if (id == BOX_LIFE_P) {
				s.boxLifetimeMinutes = Math.min(60, s.boxLifetimeMinutes + 1);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Бокс живёт " + s.boxLifetimeMinutes + " мин после появления."));
			} else if (id == BOX_COOLDOWN_M || id == BOX_COOLDOWN_P) {
				int delta = id == BOX_COOLDOWN_M ? -5 : 5;
				s.boxCooldownMinutes = Math.max(0, Math.min(1440, s.boxCooldownMinutes + delta));
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Пауза между боксами: "
						+ (s.boxCooldownMinutes == 0 ? "выкл" : s.boxCooldownMinutes + " мин")));
			} else if (id == CLAN_WIN_M) {
				s.clanWinWc = Math.max(0, s.clanWinWc - 500);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Награда клану-победителю: " + ru.admincontrol.util.Money.wc(s.clanWinWc)));
			} else if (id == CLAN_WIN_P) {
				s.clanWinWc = Math.min(2_000_000_000, s.clanWinWc + 500);
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Награда клану-победителю: " + ru.admincontrol.util.Money.wc(s.clanWinWc)));
			} else if (id == FULLSTOP_CD_M || id == FULLSTOP_CD_M30) {
				ru.admincontrol.util.FullStopLock.shiftCooldown(id == FULLSTOP_CD_M ? -10 : -30);
				Chat.send(serverPlayer, Chat.yellow(ru.admincontrol.util.FullStopLock.settingLabel()));
			} else if (id == FULLSTOP_CD_P || id == FULLSTOP_CD_P30) {
				ru.admincontrol.util.FullStopLock.shiftCooldown(id == FULLSTOP_CD_P ? 10 : 30);
				Chat.send(serverPlayer, Chat.yellow(ru.admincontrol.util.FullStopLock.settingLabel()));
			} else if (id == TRAIN_DAILY_M || id == TRAIN_DAILY_P) {
				int cur = s.trainingDailyMax <= 0 ? 5 : s.trainingDailyMax;
				s.trainingDailyMax = Math.max(1, Math.min(50, cur + (id == TRAIN_DAILY_M ? -1 : 1)));
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Дневной лимит тренировок (база): " + s.trainingDailyMax));
			} else if (id == PROTEIN_ON) {
				s.proteinEnabled = !s.proteinEnabled;
				DataManager.get().saveAll();
				Chat.send(serverPlayer, Chat.yellow("Протеин: " + onOff(s.proteinEnabled)));
			} else if (id == BOX_KIT_M) {
				TrainingConfig.boxCount();
				s.boxKitEditIndex = Math.max(0, s.boxKitEditIndex - 1);
				DataManager.get().saveAll();
			} else if (id == BOX_KIT_P) {
				int max = Math.max(0, TrainingConfig.boxCount() - 1);
				s.boxKitEditIndex = Math.min(max, s.boxKitEditIndex + 1);
				DataManager.get().saveAll();
			} else if (id == OPEN_BOX_KIT) {
				ScreenNav.open(serverPlayer, new TrainingKitFactory(TrainingConfig.boxKey(s.boxKitEditIndex)));
				return true;
			} else if (id == BOX_KIT_ADD) {
				TrainingConfig.addBoxKit();
				Chat.send(serverPlayer, Chat.yellow("Добавлен набор лута бокса."));
			} else if (id == BOX_KIT_DEL) {
				TrainingConfig.removeBoxKit();
				Chat.send(serverPlayer, Chat.yellow("Набор лута бокса удалён."));
			}
		} else {
			return false;
		}
		sync(snapshot(serverPlayer, rank >= AdminRanks.DEV));
		return true;
	}

	private static int packSlot(ModSettings s, int level) {
		return s.backpackSlots(level);
	}

	private static void setPackSlot(ModSettings s, int level, int value) {
		switch (Math.max(1, Math.min(5, level))) {
			case 2 -> s.backpackSlots2 = value;
			case 3 -> s.backpackSlots3 = value;
			case 4 -> s.backpackSlots4 = value;
			case 5 -> s.backpackSlots5 = value;
			default -> s.backpackSlots1 = value;
		}
	}

	private static int whPriceOf(ModSettings s, int level) {
		return s.clanWarehousePrice(level);
	}

	private static void setWhPrice(ModSettings s, int level, int value) {
		switch (Math.max(1, Math.min(5, level))) {
			case 2 -> s.clanWarehousePrice2 = value;
			case 3 -> s.clanWarehousePrice3 = value;
			case 4 -> s.clanWarehousePrice4 = value;
			case 5 -> s.clanWarehousePrice5 = value;
			default -> s.clanWarehousePrice1 = value;
		}
	}

	private static int whSlotOf(ModSettings s, int level) {
		return s.clanWarehouseSlots(level);
	}

	private static void setWhSlot(ModSettings s, int level, int value) {
		switch (Math.max(1, Math.min(5, level))) {
			case 2 -> s.clanWarehouseSlots2 = value;
			case 3 -> s.clanWarehouseSlots3 = value;
			case 4 -> s.clanWarehouseSlots4 = value;
			case 5 -> s.clanWarehouseSlots5 = value;
			default -> s.clanWarehouseSlots1 = value;
		}
	}

	private static boolean devOnly(int id) {
		return id == KILL_LOCK || id == REVIVE || id == ALL_CLEAR || id == ALL_UNADM
				|| id == ALL_PERM || id == ALL_UNPERM || id == UNDEV
				|| id == WARN_BUY || id == WARN_PRICE_M || id == WARN_PRICE_P
				|| id == OPEN_ARANKS || id == OPEN_CASINO || id == OPEN_CASINO_ITEM
				|| id == CASINO || id == CMIN_M || id == CMIN_P || id == CMAX_M || id == CMAX_P
				|| id == CH_M10 || id == CH_M || id == CH_P || id == CH_P10
				|| id == CASINO_BET_M || id == CASINO_BET_P || id == CASINO_BET_M1000 || id == CASINO_BET_P1000
				|| id == MONEY_DELAY_M || id == MONEY_DELAY_P || id == ITEM_DELAY_M || id == ITEM_DELAY_P
				|| id == CLICK_M || id == CLICK_P || id == AC_JAIL_M || id == AC_JAIL_P
				|| id == ANTICHEAT || id == XRAY
				|| id == BOX_R_M || id == BOX_R_P || id == BOX_WARN_M || id == BOX_WARN_P
				|| id == BOX_CLEAR_M || id == BOX_CLEAR_P || id == BOX_LIFE_M || id == BOX_LIFE_P
				|| id == CLAN_WIN_M || id == CLAN_WIN_P
				|| id == FULLSTOP_CD_M || id == FULLSTOP_CD_P
				|| id == FULLSTOP_CD_M30 || id == FULLSTOP_CD_P30
				|| id == TRAIN_DAILY_M || id == TRAIN_DAILY_P || id == PROTEIN_ON
				|| id == BOX_KIT_M || id == BOX_KIT_P
				|| id == OPEN_BOX_KIT || id == BOX_KIT_ADD || id == BOX_KIT_DEL
				|| id == CLICK_HOLD_M || id == CLICK_HOLD_P || id == XRAY_NEED_M || id == XRAY_NEED_P
				|| id == AFK || id == AFK_M || id == AFK_P
				|| id == XRAY_BAN_M || id == XRAY_BAN_P
				|| id == CLICK_BAN_M || id == CLICK_BAN_P
				|| id == CWAR_LOOT_M || id == CWAR_LOOT_P || id == CWAR_MAX_M || id == CWAR_MAX_P
				|| id == CWAR_AUTO || id == CWAR_MIN_M || id == CWAR_MIN_P || id == CWAR_CORE
				|| id == CWAR_CH_M || id == CWAR_CH_P || id == CWAR_CD_M || id == CWAR_CD_P
				|| id == CW_STORY_TOGGLE || id == CW_BUILDER_ENTER || id == CW_LUA_CREATE
				|| id == CW_LUA_FIND || id == CW_LUA_REMOVE || id == CW_SKINS_RELOAD
				|| id == BETA_TOGGLE || id == CW_RESOURCES_RELOAD || id == CW_LUA_WAVE || id == CW_LUA_RESET_POSE || id == CW_STORY_ANNOUNCE;
	}

	private static int coreIndex(ModSettings s) {
		String id = s.clanWarCoreBlock == null ? "" : s.clanWarCoreBlock;
		for (int i = 0; i < ru.admincontrol.util.ClanWarService.CORE_BLOCKS.length; i++) {
			if (ru.admincontrol.util.ClanWarService.CORE_BLOCKS[i].equalsIgnoreCase(id)) {
				return i;
			}
		}
		return 0;
	}

	private static void shiftAuction(ModSettings s, int id) {
		if (id == AMIN_M) {
			s.auctionMinPercent = Math.max(0, s.auctionMinPercent - 5);
		} else if (id == AMIN_P) {
			s.auctionMinPercent = Math.min(1000, s.auctionMinPercent + 5);
		} else if (id == AMAX_M) {
			s.auctionMaxPercent = Math.max(0, s.auctionMaxPercent - 5);
		} else {
			s.auctionMaxPercent = Math.min(1000, s.auctionMaxPercent + 5);
		}
		if (s.auctionMaxPercent < s.auctionMinPercent) {
			if (id == AMIN_M || id == AMIN_P) {
				s.auctionMaxPercent = s.auctionMinPercent;
			} else {
				s.auctionMinPercent = s.auctionMaxPercent;
			}
		}
		DataManager.get().saveAll();
		AuctionService.clampAllPrices();
	}

	private static void applyLock(ServerPlayerEntity admin, String mode, String reason) {
		DataManager dm = DataManager.get();
		ModSettings s = dm.settings();
		s.serverLocked = true;
		s.serverLockMode = mode;
		s.serverLockReason = reason;
		dm.saveAll();
		Chat.broadcast(admin.getServer(), Chat.red("Администратор "
				+ admin.getGameProfile().getName() + " закрыл сервер."));
		for (ServerPlayerEntity other : admin.getServer().getPlayerManager().getPlayerList()) {
			if (!dm.canJoinWhileLocked(other)) {
				other.networkHandler.disconnect(Chat.red("Сервер закрыт: " + dm.serverLockReason()));
			}
		}
	}

	private static void kickAll(ServerPlayerEntity admin, int keepRank) {
		int kicked = 0;
		DataManager dm = DataManager.get();
		for (ServerPlayerEntity other : admin.getServer().getPlayerManager().getPlayerList()) {
			if (other == admin || dm.getAdminLevel(other) >= keepRank) {
				continue;
			}
			other.networkHandler.disconnect(Chat.red("Отключение администратором "
					+ admin.getGameProfile().getName()));
			kicked++;
		}
		Chat.send(admin, Chat.yellow("Отключено игроков: " + kicked + "."));
	}

	private static void setTime(ServerPlayerEntity admin, long time, String label) {
		for (ServerWorld world : admin.getServer().getWorlds()) {
			world.setTimeOfDay(time);
		}
		Chat.broadcast(admin.getServer(), Chat.red("Администратор "
				+ admin.getGameProfile().getName() + " изменил время в мире"));
		Chat.send(admin, Chat.yellow("Время: " + label));
	}

	private static void setWeather(ServerPlayerEntity admin, boolean rain, boolean thunder, String label) {
		int duration = 6000;
		int clear = rain ? 0 : duration;
		int rainTicks = rain ? duration : 0;
		for (ServerWorld world : admin.getServer().getWorlds()) {
			world.setWeather(clear, rainTicks, rain, thunder);
		}
		Chat.broadcast(admin.getServer(), Chat.red("Администратор "
				+ admin.getGameProfile().getName() + " изменил погоду в мире"));
		Chat.send(admin, Chat.yellow("Погода: " + label));
	}

	private static void stripAdmins(ServerPlayerEntity self) {
		DataManager data = DataManager.get();
		for (PlayerRecord record : data.records()) {
			if (self.getUuid().toString().equalsIgnoreCase(record.uuid)
					|| self.getGameProfile().getName().equalsIgnoreCase(record.nickname)) {
				continue;
			}
			if (data.isAdmin(record.uuidOrNull(), record.nickname)
					&& data.getAdminLevel(record.uuidOrNull(), record.nickname) < AdminRanks.DEV) {
				data.removeAdmin(record);
			}
		}
		data.settings().addAdminLockedToStaff = true;
		data.saveAll();
		CommandSync.refreshAll(self.getServer());
		TabListColors.refresh(self.getServer());
		Chat.send(self, Chat.yellow("Админы сняты, /add_admin заблокирован для игроков."));
	}

	private static void massPermaban(ServerPlayerEntity self) {
		DataManager data = DataManager.get();
		String reason = "панель /dserver";
		for (PlayerRecord record : data.records()) {
			if (self.getGameProfile().getName().equalsIgnoreCase(record.nickname)) {
				continue;
			}
			if (data.findActiveBan(record.uuidOrNull(), record.nickname).isPresent()) {
				continue;
			}
			BanEntry entry = new BanEntry();
			entry.uuid = record.uuid;
			entry.nickname = record.nickname;
			entry.permanent = true;
			entry.reason = reason;
			entry.fromAllPermab = true;
			entry.createdAt = System.currentTimeMillis();
			data.addBan(entry);
			ru.admincontrol.command.CommandUtil.findOnline(self.getServer(), record.nickname)
					.ifPresent(p -> ru.admincontrol.util.PunishmentNotice.disconnect(p,
							ru.admincontrol.util.PunishmentNotice.Kind.PERMBAN,
							ru.admincontrol.util.PunishmentNotice.displayAdmin(self.getGameProfile().getName(), false),
							reason, ru.admincontrol.util.PunishmentNotice.permDuration(), entry.createdAt));
		}
		Chat.send(self, Chat.red("Массовый пермабан выполнен."));
	}

	private static String onOff(boolean on) {
		return on ? "вкл" : "выкл";
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
