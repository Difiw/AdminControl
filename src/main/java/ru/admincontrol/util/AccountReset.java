package ru.admincontrol.util;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import ru.admincontrol.command.CommandSync;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Полный сброс прогресса аккаунта. Наказания, баны и запреты (казино/AGM) не трогаются.
 */
public final class AccountReset {
	private AccountReset() {
	}

	public static String apply(MinecraftServer server, PlayerRecord record, String reason) {
		if (record == null) {
			return "Игрок не найден.";
		}
		if (record.nps) {
			return "Нельзя обнулить NPC.";
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return "Данные сервера недоступны.";
		}
		UUID uuid = record.uuidOrNull();
		ServerPlayerEntity online = uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
		if (online == null) {
			online = CommandUtil.findOnline(server, record.nickname).orElse(null);
		}
		if (online != null) {
			if (EventService.member(online)) {
				EventService.leave(online, false);
			}
			TrainingService.abort(online);
			clearLive(online);
		}
		ClanService.forceDetach(record);
		AuctionService.removeBySeller(record.uuid, record.nickname);
		int coins = Math.max(0, record.coins) + Math.max(0, record.pendingCoins);
		if (coins > 0) {
			CoinLedger.debit(record, coins, "обнуление аккаунта: " + reason);
		}
		wipeProgress(record);
		data.removeAdmin(record);
		data.saveAll();
		CommandSync.refreshAll(server);
		TabListColors.refresh(server);
		HomeProtection.refresh();
		if (online != null) {
			BalanceBoard.refresh(online, true);
			Chat.send(online, Chat.red("Ваш аккаунт обнулён. Причина: " + reason));
			online.networkHandler.disconnect(Chat.red("Аккаунт обнулён. Причина: " + reason));
		}
		UUID id = uuid;
		server.execute(() -> wipeVanillaFiles(server, id));
		return null;
	}

	private static void wipeProgress(PlayerRecord record) {
		List<PunishmentRecord> history = record.history == null ? new ArrayList<>() : new ArrayList<>(record.history);
		int warns = record.activeWarns;
		boolean agmBanned = record.agmBanned;
		boolean casinoBanned = record.casinoBanned;
		boolean nps = record.nps;
		String uuid = record.uuid;
		String nick = record.nickname;

		record.playtimeMillis = 0;
		record.lastJoinAt = 0;
		record.lastLeaveAt = 0;
		record.loginSessions = new ArrayList<>();
		record.dailyKey = "";
		record.dailyBroken = 0;
		record.dailyKills = 0;
		record.dailyJoinPaid = false;
		record.dailyBreakPaid = false;
		record.dailyKillPaid = false;
		record.dailyPlaced = 0;
		record.dailyPlacePaid = false;
		record.dailyPlayMs = 0;
		record.dailyPlayPaid = false;
		record.dailyChat = 0;
		record.dailyChatPaid = false;
		record.dailyCasino = 0;
		record.dailyCasinoPaid = false;
		record.dailyEat = 0;
		record.dailyEatPaid = false;
		record.dailyPvp = 0;
		record.dailyPvpPaid = false;
		record.extraHomeSlots = 0;
		record.extraHomes = new ArrayList<>();
		record.clanId = "";
		record.weekKey = "";
		record.weekBroken = 0;
		record.weekKills = 0;
		record.weekPlayMs = 0;
		record.lastKnownLevel = 0;
		record.x = 0;
		record.y = 0;
		record.z = 0;
		record.dimension = "minecraft:overworld";
		record.gameMode = "survival";
		record.cheatsEnabled = false;
		record.teleportAllowed = true;
		record.inventoryNbt = "";
		record.home = null;
		record.points = new ArrayList<>();
		record.spawnChoice = "";
		record.customSpawn = null;
		record.homePrivate = false;
		record.homePrivateOwned = false;
		record.homeSize = 3;
		record.maxHomeSize = 3;
		record.backpackLevel = 0;
		record.backpack = new ArrayList<>();
		record.workbenchOwned = false;
		record.furnaceLevel = 0;
		record.furnaceItems = new ArrayList<>();
		record.skills = null;
		record.agmEnabled = false;
		record.agmRestore = false;
		record.loginIps = new ArrayList<>();
		record.pendingLapis = 0;
		record.coins = 0;
		record.pendingCoins = 0;
		record.coinsEarned = 0;
		record.coinsSpent = 0;
		record.coinStatsReady = true;
		record.actionFeed = null;
		record.actionFeedDisabled = new ArrayList<>();
		record.showBoard = null;
		double fromPower = record.power();
		double fromLuck = record.luck();
		record.power = 0;
		record.luck = 0;
		ProgressLog.power(record, fromPower, 0, ProgressLog.ADMIN);
		ProgressLog.luck(record, fromLuck, 0, ProgressLog.ADMIN);
		record.casinoGames = 0;
		record.casinoSpent = 0;
		record.casinoWon = 0;
		record.casinoMoneyGames = 0;
		record.casinoItemGames = 0;
		record.casinoItemWins = 0;
		record.casinoItemLosses = 0;
		record.casinoItemLost = 0;
		record.casinoItemReceived = 0;
		record.casinoBiggestWin = 0;
		record.lastCasinoGameMs = 0;
		record.lastCasinoMoneyMs = 0;
		record.lastCasinoItemMs = 0;
		record.casinoItemGot = new java.util.LinkedHashMap<>();
		record.lastAutoclickWarnMs = 0;
		record.anticheatStrikes = 0;
		if (record.anticheatCases != null) {
			record.anticheatCases.clear();
		}
		record.blocksBroken = 0;
		record.blocksPlaced = 0;
		record.peacefulKills = 0;
		record.monsterKills = 0;
		record.lastHealth = 0;
		record.lastHunger = 0;
		record.deaths = 0;

		record.uuid = uuid;
		record.nickname = nick;
		record.nps = nps;
		record.history = history;
		record.activeWarns = warns;
		record.agmBanned = agmBanned;
		record.casinoBanned = casinoBanned;
	}

	private static void clearLive(ServerPlayerEntity player) {
		player.getInventory().clear();
		player.getInventory().markDirty();
		if (player.playerScreenHandler != null) {
			player.playerScreenHandler.sendContentUpdates();
		}
		for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
			player.getEnderChestInventory().setStack(i, ItemStack.EMPTY);
		}
		InventoryUtil.applyExperience(player, 0, 0f, 0);
		player.setScore(0);
		player.clearStatusEffects();
		player.setFireTicks(0);
		player.setFrozenTicks(0);
		player.setVelocity(0, 0, 0);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(5);
		player.setHealth(player.getMaxHealth());
		player.changeGameMode(GameMode.SURVIVAL);
		player.closeHandledScreen();
	}

	private static void wipeVanillaFiles(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) {
			return;
		}
		Achievements.clearAll(server, uuid);
		deleteQuiet(server.getSavePath(WorldSavePath.STATS).resolve(uuid + ".json"));
		Path players = server.getSavePath(WorldSavePath.PLAYERDATA);
		deleteQuiet(players.resolve(uuid + ".dat"));
		deleteQuiet(players.resolve(uuid + ".dat_old"));
	}

	private static void deleteQuiet(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (Exception ignored) {
		}
	}
}
