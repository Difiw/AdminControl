package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.util.ScreenNav;

public final class ModScreenHandlers {
	public static ScreenHandlerType<InspectInventoryScreenHandler> INSPECT;
	public static ScreenHandlerType<SetSpawnScreenHandler> SET_SPAWN;
	public static ScreenHandlerType<HomeScreenHandler> HOME;
	public static ScreenHandlerType<SellScreenHandler> SELL;
	public static ScreenHandlerType<AuctionScreenHandler> AUCTION;
	public static ScreenHandlerType<AuctionOfferScreenHandler> AUCTION_OFFER;
	public static ScreenHandlerType<AuctionBuyScreenHandler> AUCTION_BUY;
	public static ScreenHandlerType<EventScreenHandler> EVENT;
	public static ScreenHandlerType<SetPriceScreenHandler> SET_PRICE;
	public static ScreenHandlerType<LogScreenHandler> LOG;
	public static ScreenHandlerType<HelpScreenHandler> HELP;
	public static ScreenHandlerType<CheckFeedScreenHandler> CHECK_FEED;
	public static ScreenHandlerType<ChCommandsScreenHandler> CH_COMMANDS;
	public static ScreenHandlerType<StatsScreenHandler> STATS;
	public static ScreenHandlerType<CasinoScreenHandler> CASINO;
	public static ScreenHandlerType<CasinoPlayScreenHandler> CASINO_PLAY;
	public static ScreenHandlerType<CasinoItemScreenHandler> CASINO_ITEM;
	public static ScreenHandlerType<ServerPanelScreenHandler> SERVER_PANEL;
	public static ScreenHandlerType<CoinsScreenHandler> COINS;
	public static ScreenHandlerType<CoinsTransferScreenHandler> COINS_TRANSFER;
	public static ScreenHandlerType<TrainingScreenHandler> TRAINING;
	public static ScreenHandlerType<TrainingRewardsScreenHandler> TRAINING_REWARDS;
	public static ScreenHandlerType<TrainingKitScreenHandler> TRAINING_KIT;
	public static ScreenHandlerType<WorldsScreenHandler> WORLDS;
	public static ScreenHandlerType<AplayerScreenHandler> APLAYER;
	public static ScreenHandlerType<RankPermsScreenHandler> RANK_PERMS;
	public static ScreenHandlerType<PlayFeaturesScreenHandler> PLAY_FEATURES;
	public static ScreenHandlerType<ShopScreenHandler> SHOP;
	public static ScreenHandlerType<ClanScreenHandler> CLAN;
	public static ScreenHandlerType<ExtraChestScreenHandler> EXTRA_CHEST;
	public static ScreenHandlerType<PasswordScreenHandler> PASSWORD;
	public static ScreenHandlerType<SellBanScreenHandler> SELL_BAN;
	public static ScreenHandlerType<AchievementsScreenHandler> ACHIEVEMENTS;
	public static ScreenHandlerType<ActivitySettingsScreenHandler> ACTIVITY_SETTINGS;
	public static ScreenHandlerType<AutoFurnaceScreenHandler> AUTO_FURNACE;
	public static ScreenHandlerType<AdminStatsScreenHandler> ADMIN_STATS;
	public static ScreenHandlerType<FileCheckScreenHandler> FILE_CHECK;
	public static ScreenHandlerType<AntiCheatScreenHandler> ANTICHEAT;
	public static ScreenHandlerType<RoleEditorScreenHandler> ROLE_EDITOR;
	public static ScreenHandlerType<DevEventScreenHandler> DEV_EVENT;
	public static ScreenHandlerType<BugListScreenHandler> BUG_LIST;

	private ModScreenHandlers() {
	}

	public static void register() {
		INSPECT = register("inspect_inventory", InspectInventoryScreenHandler::createClient);
		SET_SPAWN = register("set_spawn", SetSpawnScreenHandler::createClient);
		HOME = register("home", HomeScreenHandler::createClient);
		SELL = register("sell", SellScreenHandler::createClient);
		AUCTION = register("auction", AuctionScreenHandler::createClient);
		AUCTION_OFFER = register("auction_offer", AuctionOfferScreenHandler::createClient);
		AUCTION_BUY = register("auction_buy", AuctionBuyScreenHandler::createClient);
		EVENT = register("event", EventScreenHandler::createClient);
		SET_PRICE = register("set_price", SetPriceScreenHandler::createClient);
		LOG = register("log_view", LogScreenHandler::createClient);
		HELP = register("help_view", HelpScreenHandler::createClient);
		CHECK_FEED = register("check_feed", CheckFeedScreenHandler::createClient);
		CH_COMMANDS = register("ch_commands", ChCommandsScreenHandler::createClient);
		STATS = register("stats", StatsScreenHandler::createClient);
		CASINO = register("casino_settings", CasinoScreenHandler::createClient);
		CASINO_PLAY = register("casino_play", CasinoPlayScreenHandler::createClient);
		CASINO_ITEM = register("casino_item", CasinoItemScreenHandler::createClient);
		SERVER_PANEL = register("server_panel", ServerPanelScreenHandler::createClient);
		COINS = register("coins", CoinsScreenHandler::createClient);
		COINS_TRANSFER = register("coins_transfer", CoinsTransferScreenHandler::createClient);
		TRAINING = register("training", TrainingScreenHandler::createClient);
		TRAINING_REWARDS = register("training_rewards", TrainingRewardsScreenHandler::createClient);
		TRAINING_KIT = register("training_kit", TrainingKitScreenHandler::createClient);
		WORLDS = register("worlds", WorldsScreenHandler::createClient);
		APLAYER = register("aplayer", AplayerScreenHandler::createClient);
		RANK_PERMS = register("rank_perms", RankPermsScreenHandler::createClient);
		PLAY_FEATURES = register("play_features", PlayFeaturesScreenHandler::createClient);
		SHOP = register("player_shop", ShopScreenHandler::createClient);
		CLAN = register("player_clan", ClanScreenHandler::createClient);
		EXTRA_CHEST = register("extra_chest", ExtraChestScreenHandler::createClient);
		PASSWORD = register("set_password", PasswordScreenHandler::createClient);
		SELL_BAN = register("sell_ban", SellBanScreenHandler::createClient);
		ACHIEVEMENTS = register("achievements", AchievementsScreenHandler::createClient);
		ACTIVITY_SETTINGS = register("activity_settings", ActivitySettingsScreenHandler::createClient);
		AUTO_FURNACE = register("auto_furnace", AutoFurnaceScreenHandler::createClient);
		ADMIN_STATS = register("admin_stats", AdminStatsScreenHandler::createClient);
		FILE_CHECK = register("file_check", FileCheckScreenHandler::createClient);
		ANTICHEAT = register("anticheat", AntiCheatScreenHandler::createClient);
		ROLE_EDITOR = register("role_editor", RoleEditorScreenHandler::createClient);
		DEV_EVENT = register("dev_event", DevEventScreenHandler::createClient);
		BUG_LIST = register("bug_list", BugListScreenHandler::createClient);
	}

	private static <T extends ScreenHandler> ScreenHandlerType<T> register(String path, ClientFactory<T> factory) {
		return Registry.register(Registries.SCREEN_HANDLER, new Identifier(AdminControlMod.MOD_ID, path),
				new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> {
					boolean back = buf.readBoolean();
					return ScreenNav.applyFlag(factory.create(syncId, inventory, buf), back);
				}));
	}

	@FunctionalInterface
	private interface ClientFactory<T extends ScreenHandler> {
		T create(int syncId, PlayerInventory inventory, PacketByteBuf buf);
	}
}
