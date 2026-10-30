package ru.admincontrol.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.BanEntry;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.InventoryUtil;
import ru.admincontrol.util.PunishmentNotice;
import ru.admincontrol.util.TabListColors;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerTracker {
	private static final Map<UUID, Long> BAN_PASS = new ConcurrentHashMap<>();
	private static int tickCounter;

	private PlayerTracker() {
	}

	/** Вход игрока (и включение мода при онлайн-игроках). */
	public static void joined(MinecraftServer server, ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		String ip = player.getIp();
		PlayerRecord record = data.getOrCreate(player);
		if (!ru.admincontrol.util.NpcService.isNpc(player)) {
			record.lastJoinAt = System.currentTimeMillis();
			data.markDirty();
			ru.admincontrol.util.AntiCheatService.noteAction(player);
			if (data.isAdmin(player)) {
				ru.admincontrol.util.AdminStats.recordJoin(player);
			}
		}
		if (ip != null && !ip.isBlank() && (record.loginIps == null || record.loginIps.stream().noneMatch(ip::equalsIgnoreCase))) {
			if (record.loginIps == null) {
				record.loginIps = new java.util.ArrayList<>();
			}
			record.loginIps.add(ip);
		}
		enforceJoinRules(server, player);
		server.execute(() -> {
			enforceJoinRules(server, player);
			ru.admincontrol.util.ActivityLock.sync(player);
			ru.admincontrol.util.ClanNametags.apply(player);
			ru.admincontrol.util.Achievements.checkCasinoPoverty(player);
			ru.admincontrol.util.PlayFeatures.onJoin(player);
			ru.admincontrol.util.ClanWarService.afterJoin(player);
			ru.admincontrol.util.EventService.showBar(player);
			ru.admincontrol.util.InvButtons.sync(player);
			ru.admincontrol.util.FreezeService.sync(player);
			ru.admincontrol.util.FileScanService.onJoin(player);
			ru.admincontrol.util.SkillTreeService.onJoin(player);
		});
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ru.admincontrol.util.ModGate.on()) {
				joined(server, handler.player);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DataManager data = DataManager.get();
			if (data == null || !ru.admincontrol.util.ModGate.on()) {
				return;
			}
			ServerPlayerEntity disconnected = handler.player;
			ServerPlayerEntity admin = ru.admincontrol.util.NpcService.sessionAdmin(disconnected);
			if (ru.admincontrol.util.NpcService.isIdleNpc(disconnected)) {
				return;
			}
			ServerPlayerEntity player = admin != null ? admin : disconnected;
			ru.admincontrol.util.AuthService.forget(player);
			ServerPlayerEntity leaving = player;
			ru.admincontrol.util.InventoryVault.leaving(leaving, () -> {
				try {
					ru.admincontrol.util.EventService.onDisconnect(leaving);
				} catch (RuntimeException e) {
					ru.admincontrol.AdminControlMod.LOGGER.error("Выход с МП при отключении", e);
				}
				try {
					ru.admincontrol.util.EventMapEditor.onDisconnect(leaving);
				} catch (RuntimeException e) {
					ru.admincontrol.AdminControlMod.LOGGER.error("Выход с правки карты МП при отключении", e);
				}
				try {
					ru.admincontrol.util.TrainingService.abort(leaving);
				} catch (RuntimeException e) {
					ru.admincontrol.AdminControlMod.LOGGER.error("Выход с тренировки при отключении", e);
				}
			});
			ru.admincontrol.util.ClanWarService.onDisconnect(player);
			ru.admincontrol.util.ScreenNav.forget(player);
			ru.admincontrol.util.HomeOutline.forget(player);
			ru.admincontrol.util.WorldGate.forget(player);
			ru.admincontrol.util.TeleportLog.forget(player);
			ru.admincontrol.util.SpectateService.stop(player);
			ru.admincontrol.util.NpcService.stopControl(player, true);
			ru.admincontrol.util.TpaService.forget(player);
			ru.admincontrol.util.BalanceBoard.hide(player);
			ru.admincontrol.util.AntiCheatService.forget(player);
			ru.admincontrol.util.BugAbuseService.forget(player);
			ru.admincontrol.util.FileScanService.forget(player);
			ru.admincontrol.util.ItemSecurityService.forget(player);
			ru.admincontrol.util.PacketGuard.forget(player);
			ru.admincontrol.util.SkillTreeService.forget(player);
			ru.admincontrol.util.FurnaceService.forget(player);
			ru.admincontrol.util.TeleportWarmup.forget(player);
			ru.admincontrol.util.AdminStats.forget(player);
			BAN_PASS.remove(player.getUuid());
			PlayerRecord record = data.getOrCreate(player);
			if (!ru.admincontrol.util.NpcService.isNpc(disconnected)) {
				record.lastLeaveAt = System.currentTimeMillis();
				if (data.isAdmin(player)) {
					ru.admincontrol.util.AdminStats.recordLeave(player);
				}
			}
			InventoryUtil.snapshot(player, record);
			data.saveNow();
			TabListColors.refresh(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DataManager data = DataManager.get();
			if (data == null || !ru.admincontrol.util.ModGate.on()) {
				return;
			}
			tickCounter++;
			ru.admincontrol.util.EventService.tick(server);
			ru.admincontrol.util.DuelService.tick(server);
			ru.admincontrol.util.EventMapEditor.tick(server);
			ru.admincontrol.util.BoxEventService.tick(server);
			ru.admincontrol.util.ClanWarService.tick(server);
			ru.admincontrol.util.TrainingService.tick(server);
			ru.admincontrol.util.NpcService.tick(server);
			ru.admincontrol.util.HomeOutline.tick(server);
			ru.admincontrol.util.CwService.tick(server);
			ru.admincontrol.util.FreezeService.tick(server);
			ru.admincontrol.util.ServerStopService.tick(server);
			ru.admincontrol.util.FurnaceService.tick(server);
			ru.admincontrol.util.TeleportWarmup.tick(server);
			ru.admincontrol.util.PrivateService.tick(server);
			ru.admincontrol.util.WeatherLog.tick(server);
			ru.admincontrol.util.FileScanService.tick(server, tickCounter);
			ru.admincontrol.util.ItemSecurityService.tick(server, tickCounter);
			ru.admincontrol.util.SkillTreeService.tick(server, tickCounter);
			if (tickCounter % 20 == 0) {
				ru.admincontrol.util.AntiCheatService.tickAfk(server);
				ru.admincontrol.util.BugAbuseService.tick(server, tickCounter);
			}
			if (tickCounter % 200 == 0) {
				ru.admincontrol.util.BugList.saveIfDirty();
			}
			if (tickCounter % 100 == 0) {
				ru.admincontrol.util.ClanNametags.refreshAll(server);
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (ru.admincontrol.util.NpcService.isNpc(player)) {
					continue;
				}
				if (kickIfBlocked(player)) {
					continue;
				}
				if (ru.admincontrol.util.AuthService.pending(player)) {
					ru.admincontrol.util.AuthService.tick(player);
				}
				if (ru.admincontrol.util.SpectateService.isSpectating(player)) {
					ru.admincontrol.util.SpectateService.tick(player);
				}
			}
			if (tickCounter % 20 != 0) {
				return;
			}
			long now = System.currentTimeMillis();
			data.expireBans(now);
			ru.admincontrol.util.ActivityLog.flush();
			ru.admincontrol.util.AdminStats.tick(server);
			boolean snapInv = tickCounter % 400 == 0;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (ru.admincontrol.util.AuthService.pending(player)) {
					continue;
				}
				if (ru.admincontrol.util.NpcService.isNpc(player)
						&& !ru.admincontrol.util.NpcService.isControlled(player)) {
					continue;
				}
				ru.admincontrol.util.WorldGate.ejectIfLocked(player);
				PlayerRecord record = data.getOrCreate(player);
				record.playtimeMillis += 1000L;
				ru.admincontrol.util.PlayFeatures.addWeekPlay(record, 1000L);
				ru.admincontrol.util.PlayFeatures.addDayPlay(player, 1000L);
				if (!record.dailyJoinPaid) {
					ru.admincontrol.util.PlayFeatures.onJoin(player);
				}
				if (ru.admincontrol.util.NpcService.isControlling(player)
						|| ru.admincontrol.util.NpcService.isControlled(player)) {
					InventoryUtil.snapshotPos(player, record);
				} else if (snapInv) {
					InventoryUtil.snapshot(player, record);
				} else if (tickCounter % 100 == 0) {
					InventoryUtil.snapshotPos(player, record);
				}
				if (!ru.admincontrol.util.NpcService.isNpc(player) && tickCounter % 40 == 0) {
					ru.admincontrol.util.BalanceBoard.refresh(player);
				}
			}
			data.saveIfDue(now);
			ru.admincontrol.util.PlayFeatures.tick(server);
			ru.admincontrol.util.ClanService.purgeInvites();
		});
	}

	private static void enforceJoinRules(MinecraftServer server, ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		if (ru.admincontrol.util.NpcService.isNpc(player)) {
			data.getOrCreate(player);
			TabListColors.refresh(server);
			return;
		}
		if (kickIfBlocked(player)) {
			return;
		}
		if (ru.admincontrol.util.NickFilter.enforce(player)) {
			return;
		}

		if (data.isServerLocked() && !data.canJoinWhileLocked(player)) {
			String reason = data.serverLockReason();
			player.networkHandler.disconnect(Chat.red("Сервер временно закрыт"
					+ (reason.isBlank() ? "." : ": " + reason)));
			return;
		}

		data.getOrCreate(player);
		TabListColors.refresh(server);
		ru.admincontrol.util.AuthService.begin(player);
	}

	public static void clearBanPass() {
		BAN_PASS.clear();
	}

	private static boolean kickIfBlocked(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || ru.admincontrol.util.NpcService.isNpc(player)) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		Long until = BAN_PASS.get(player.getUuid());
		if (until != null && now < until) {
			return false;
		}
		Optional<BanEntry> ban = data.findActiveBan(player.getUuid(), player.getGameProfile().getName());
		if (ban.isPresent()) {
			BAN_PASS.remove(player.getUuid());
			kickBan(player, ban.get());
			return true;
		}
		Optional<BanEntry> jail = data.findActiveJail(player.getUuid(), player.getGameProfile().getName());
		if (jail.isPresent()) {
			BAN_PASS.remove(player.getUuid());
			kickJail(player, jail.get());
			return true;
		}
		var ipBan = ru.admincontrol.util.IpService.find(player.getIp());
		if (ipBan.isPresent() && !(data.isAdmin(player)
				&& data.getAdminLevel(player) >= ru.admincontrol.data.AdminRanks.GA)) {
			BAN_PASS.remove(player.getUuid());
			ru.admincontrol.data.IpBan entry = ipBan.get();
			player.networkHandler.disconnect(Chat.red("Ваш IP-адрес заблокирован.\nПричина: " + entry.reason
					+ "\nАдминистратор: " + entry.adminNick));
			return true;
		}
		BAN_PASS.put(player.getUuid(), now + 1000L);
		return false;
	}

	private static void kickBan(ServerPlayerEntity player, BanEntry entry) {
		PunishmentNotice.disconnectBan(player, entry);
	}

	private static void kickJail(ServerPlayerEntity player, BanEntry entry) {
		PunishmentNotice.disconnectBan(player, entry);
	}
}
