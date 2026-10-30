package ru.admincontrol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.AdminCommands;
import ru.admincontrol.command.DeveloperCommands;
import ru.admincontrol.command.PlayerCommands;
import ru.admincontrol.command.SystemCommands;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.event.PlayerActionTracker;
import ru.admincontrol.event.PlayerTracker;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.ModScreenHandlers;
import ru.admincontrol.util.ActionLog;
import ru.admincontrol.util.EventService;

public class AdminControlMod implements ModInitializer {
	public static final String MOD_ID = "admin_control";
	public static final Logger LOGGER = LoggerFactory.getLogger("AdminControl");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DataManager.initialize(server);
			java.util.List<String> cwMigration=ru.admincontrol.cw.CwMigrationService.run(false);
			for(String line:cwMigration){if(line.startsWith("FAIL"))LOGGER.error("CW migration: {}",line);else LOGGER.info("CW migration: {}",line);}
			if(ru.admincontrol.cw.CwMigrationService.healthy())for(String line:ru.admincontrol.cw.CwInstanceRegistry.processPending()){if(line.startsWith("FAIL"))LOGGER.error("CW instance lifecycle: {}",line);else LOGGER.info("CW instance lifecycle: {}",line);}
			ActionLog.info("Admin Control загружен. Первый администратор: команда /add_admin <ник>");
			LOGGER.info("Данные Admin Control: {}", DataManager.get().getConfigDir());
			ru.admincontrol.util.BugList.attach(server);
			ru.admincontrol.util.ServerErrorWatch.install();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ru.admincontrol.item.ItemEncryption.reloadFromSettings();
			ru.admincontrol.cw.CwWorldBackupService.recover(server);
			if (ru.admincontrol.emi.EmiReport.requested()) {
				try {
					ru.admincontrol.emi.EmiReport.run(server);
				} catch (Throwable e) {
					LOGGER.error("[CW EMI] отчёт не построен", e);
				}
				server.stop(false);
				return;
			}
			if (ru.admincontrol.lumira.LumiraSelfTest.enabled()) {
				ru.admincontrol.lumira.LumiraSelfTest.start(server);
			}
			if (ru.admincontrol.util.ModGate.on() && ru.admincontrol.cw.CwMigrationService.healthy()) {
				ru.admincontrol.cw.CwInstanceRegistry.load();
				ru.admincontrol.cw.CwStoryGraph.load();
				ru.admincontrol.cw.CwBuilderWorld.prepare(server);
				ru.admincontrol.util.EventMapEditor.recoverAfterCrash(server);
				ru.admincontrol.util.NpcService.restoreAll(server);
				ru.admincontrol.util.CwNpcService.syncAll(server);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			ru.admincontrol.cw.CwClientGate.hello(handler.player);
			ru.admincontrol.emi.EmiServer.hello(handler.player);
			ru.admincontrol.util.CwNpcService.syncExisting(handler.player);
			// STORY owns the player until its session is explicitly closed. In particular, a STORY started from BUILD also
			// has an ACTIVE builder return record; restoring that record first used to throw the player into the overworld
			// and made the story recovery delete the still-valid session as "stale".
			// Not in the login tick: a dimension change while the client is still receiving its first chunks leaves it with chunks of the wrong dimension.
			ru.admincontrol.cw.CwJoinRecovery.schedule(handler.player);
			ru.admincontrol.item.ItemEncryption.sendTo(handler.player);
		}));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ru.admincontrol.cw.CwJoinRecovery.release(handler.player);
			// Профили сюжета и стройки сохраняются СРАЗУ, пока игрок ещё в мире: отложенный вызов выполнился бы после записи
			// данных игрока, и при следующем входе он остался бы в сюжетном мире.
			try {
				// Межмировая слежка временно переносит тело администратора к цели. Сначала возвращаем его
				// в исходный мир, чтобы STORY сохранил актуальный профиль и координаты, а не временную позицию слежки.
				ru.admincontrol.util.SpectateService.stop(handler.player);
				ru.admincontrol.cw.CwStoryWorldService.onDisconnect(handler.player);
				ru.admincontrol.cw.CwBuilderWorld.onDisconnect(handler.player);
				ru.admincontrol.cw.CwDiagnostics.forget(handler.player.getUuid());
			} catch (RuntimeException e) {
				LOGGER.error("CW disconnect save failed", e);
			}
			server.execute(() -> {
					ru.admincontrol.cw.CwClientGate.clear(handler.player);
					ru.admincontrol.emi.EmiServer.forget(handler.player);
					ru.admincontrol.cw.CwTestEntryService.clear(handler.player);
					ru.admincontrol.cw.CwStoryEntryService.clear(handler.player);
					ru.admincontrol.cw.CwDialogueService.clear(handler.player);
					ru.admincontrol.cw.CwRespawnService.clear(handler.player);
					ru.admincontrol.cw.CwLeaveService.clear(handler.player);
				ru.admincontrol.lumira.LumiraNet.forget(handler.player.getUuid());
			});
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ru.admincontrol.cw.CwStoryWorldService.autosave(server);
			ru.admincontrol.cw.CwBuilderWorld.flush(server);
			ru.admincontrol.cw.CwDurable.drain();
			ru.admincontrol.util.ClanWarService.stop(server, "остановка сервера");
			if (EventService.active()) {
				EventService.stop(server, "остановлено: выключение сервера");
			}
			ru.admincontrol.util.EventMapEditor.stopAll(server, "выключение сервера");
			ru.admincontrol.util.TrainingService.stopAll(server);
			ru.admincontrol.util.NpcService.removeAll(server);
			ru.admincontrol.util.PrivateService.removeLabels(server);
			ru.admincontrol.util.BugList.detach();
			DataManager.shutdown();
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ru.admincontrol.cw.CwJoinRecovery::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tick=server.getTicks();
			ru.admincontrol.cw.CwStoryClock.tick(server);
			ru.admincontrol.emi.EmiServer.tick(server);
			ru.admincontrol.lumira.LumiraNet.tick(server);
			ru.admincontrol.lumira.gen.LumiraBiomeGen.tick(server);
			ru.admincontrol.cw.CwCheckpointService.tick(server);
			ru.admincontrol.cw.CwWorldBackupService.tick(server);
			ru.admincontrol.cw.CwRespawnService.tick(server);
			ru.admincontrol.cw.CwTransition.tick(server);
			ru.admincontrol.cw.CweBlacklist.tick(server);
			if(tick%20==0)ru.admincontrol.cw.CwStoryWorldService.autosave(server);
			ru.admincontrol.cw.CwBuilderWorld.autosave(server);
			if(tick%200==0)for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())ru.admincontrol.util.CwNpcService.syncExisting(player);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (ru.admincontrol.util.ModGate.on()) {
				ru.admincontrol.util.PrivateService.onEntityLoad(entity, world);
				if(entity instanceof ru.admincontrol.util.NpcPlayerEntity npc){var saved=ru.admincontrol.util.NpcStore.get(npc.getGameProfile().getName());if(saved!=null&&saved.skinId!=null&&!saved.skinId.isBlank())ru.admincontrol.util.CwNpcService.syncSkinToAll(world.getServer(),npc.getUuid(),saved.skinId);}
			}
		});

		ru.admincontrol.item.ModItems.register();
		ru.admincontrol.lumira.LumiraContent.register();
		PlayerTracker.register();
		PlayerActionTracker.register();
		ModScreenHandlers.register();
		receive(ModPackets.EVENT_CANCEL, (server, player, handler, buf, responseSender) ->
				server.execute(() -> {
					if (player == null || !CommandUtil.canUse(player.getCommandSource(), "event")) {
						return;
					}
					if (EventService.active()) {
						if (!EventService.canManage(player)) {
							return;
						}
						EventService.stop(server, "остановил администратор " + player.getGameProfile().getName());
					}
					player.closeHandledScreen();
				}));
		receive(ModPackets.SPECTATE_EXIT, (server, player, handler, buf, responseSender) ->
				server.execute(() -> {
					if (player == null || !CommandUtil.isAdminSource(player.getCommandSource())) {
						return;
					}
					if (ru.admincontrol.util.SpectateService.stop(player)) {
						ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.green("Слежка выключена."));
					}
				}));
		receive(ModPackets.SPECTATE_INV, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.SpectateService.openTargetInventory(player)));
		receive(ModPackets.NPC_CONTROL_EXIT, (server, player, handler, buf, responseSender) ->
				server.execute(() -> {
					ServerPlayerEntity admin = ru.admincontrol.util.NpcService.sessionAdmin(player);
					if (admin == null || !CommandUtil.canUse(admin.getCommandSource(), "uncontrol")) {
						return;
					}
					if (ru.admincontrol.util.NpcService.stopControl(admin)) {
						ru.admincontrol.util.Chat.send(admin, ru.admincontrol.util.Chat.green("Управление NPC прекращено."));
					}
				}));
		receive(ModPackets.HOME_OUTLINE, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.HomeOutline.toggle(player)));
		receive(ModPackets.APLAYER_RUN, (server, player, handler, buf, responseSender) -> {
			String nick = buf.readString();
			String id = buf.readString();
			String extra = buf.readString();
			server.execute(() -> ru.admincontrol.util.AplayerActions.run(player, nick, id, extra));
		});
		receive(ModPackets.SCREEN_BACK, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.ScreenNav.back(player)));
		receive(ModPackets.SCREEN_REOPEN, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.ScreenNav.reopen(player)));
		receive(ModPackets.SCREEN_BUTTON, (server, player, handler, buf, responseSender) -> {
			int syncId = buf.readVarInt();
			int buttonId = buf.readVarInt();
			server.execute(() -> {
				if (player == null || player.currentScreenHandler == null
						|| player.currentScreenHandler.syncId != syncId) {
					return;
				}
				if (player.currentScreenHandler.onButtonClick(player, buttonId)) {
					player.currentScreenHandler.sendContentUpdates();
				}
			});
		});
		receive(ModPackets.CLAN_ACTION, (server, player, handler, buf, responseSender) -> {
			String action = buf.readString(16);
			String extra = buf.readString(32);
			server.execute(() -> {
				if (player == null) {
					return;
				}
				String err;
				String ok;
				if ("create".equals(action)) {
					err = ru.admincontrol.util.ClanService.create(player, extra);
					ok = "Клан создан.";
				} else if ("rename".equals(action)) {
					err = ru.admincontrol.util.ClanService.rename(player, extra);
					ok = "Клан переименован.";
				} else if ("invite".equals(action)) {
					err = ru.admincontrol.util.ClanService.invite(player, extra);
					ok = "Приглашение отправлено.";
				} else {
					return;
				}
				if (err != null) {
					ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red(err));
				} else {
					ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.green(ok));
				}
				if ("create".equals(action) || "rename".equals(action)) {
					ru.admincontrol.screen.ClanScreenHandler.refreshOpen(player);
				}
			});
		});
		receive(ModPackets.SET_PASSWORD, (server, player, handler, buf, responseSender) -> {
			String oldP = buf.readString(32);
			String neu = buf.readString(32);
			String rep = buf.readString(32);
			server.execute(() -> ru.admincontrol.screen.PasswordScreenHandler.apply(player, oldP, neu, rep));
		});
		receive(ModPackets.OPEN_BACKPACK, (server, player, handler, buf, responseSender) ->
				server.execute(() -> {
					if (ru.admincontrol.cw.CwStoryRules.denyUi(player, "Рюкзак")) return;
					if (player == null || DataManager.get() == null) {
						return;
					}
					if (ru.admincontrol.util.AuthService.pending(player)) {
						return;
					}
					if (ru.admincontrol.util.ActivityLock.blocked(player)) {
						ru.admincontrol.util.ActivityLock.deny(player, true);
						return;
					}
					var rec = DataManager.get().progress(player);
					if (ru.admincontrol.util.InvButtons.offNotice(player, rec, ru.admincontrol.skill.SkillTree.BACKPACK)) {
						return;
					}
					if (rec.backpackLevel <= 0) {
						ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red("Рюкзак открывается в дереве навыков: /skills, ветка «Добыча и ремесло»."));
						return;
					}
					ru.admincontrol.util.ScreenNav.openRoot(player, new ru.admincontrol.screen.BackpackFactory(rec));
				}));
		receive(ModPackets.ACH_TOGGLE, (server, player, handler, buf, responseSender) -> {
			String nick = buf.readString(16);
			String id = buf.readString(128);
			boolean grant = buf.readBoolean();
			server.execute(() -> {
				if (player == null || DataManager.get() == null
						|| !ru.admincontrol.data.AdminRanks.curatorOrBlogger(DataManager.get().getAdminLevel(player))) {
					return;
				}
				String name = nick;
				java.util.UUID uuid = null;
				var rec = DataManager.get().findByNick(nick).orElse(null);
				if (rec != null) {
					uuid = rec.uuidOrNull();
					name = rec.nickname;
				}
				if (uuid == null) {
					ServerPlayerEntity target = ru.admincontrol.command.CommandUtil.findOnline(server, nick).orElse(null);
					if (target != null) {
						uuid = target.getUuid();
						name = target.getGameProfile().getName();
					}
				}
				if (uuid == null) {
					return;
				}
				var ident = net.minecraft.util.Identifier.tryParse(id);
				if (!ru.admincontrol.util.Achievements.toggle(server, uuid, ident, grant)) {
					return;
				}
				ru.admincontrol.util.ScreenNav.replace(player, new ru.admincontrol.screen.AchievementsFactory(name, uuid));
			});
		});
		receive(ModPackets.UI_VALUE, (server, player, handler, buf, responseSender) -> {
			String kind = buf.readString(32);
			String value = buf.readString(256);
			server.execute(() -> ru.admincontrol.util.UiValues.apply(player, kind, value));
		});
		receive(ModPackets.ROLE_SAVE, (server, player, handler, buf, responseSender) -> {
			String key = buf.readString(16);
			String title = buf.readString(64);
			String role = buf.readString(64);
			String format = buf.readString(128);
			String color = buf.readString(16);
			String access = buf.readString(8);
			server.execute(() -> ru.admincontrol.screen.RoleEditorScreenHandler.save(player, key, title, role, format, color, access));
		});
		receive(ModPackets.XRAY_PACK, (server, player, handler, buf, responseSender) -> {
			String file = buf.readString(200);
			server.execute(() -> ru.admincontrol.util.AntiCheatService.onXrayResourcePack(player, file));
		});
		receive(ModPackets.OPEN_WORKBENCH, (server, player, handler, buf, responseSender) ->
				server.execute(() -> { if (!ru.admincontrol.cw.CwStoryRules.denyUi(player, "Переносной верстак")) ru.admincontrol.util.InvButtons.openWorkbench(player); }));
		receive(ModPackets.OPEN_FURNACE, (server, player, handler, buf, responseSender) ->
				server.execute(() -> { if (!ru.admincontrol.cw.CwStoryRules.denyUi(player, "Переносная печь")) ru.admincontrol.util.InvButtons.openFurnace(player); }));
		receive(ModPackets.FILES_LIST, (server, player, handler, buf, responseSender) -> {
			int id = buf.readVarInt();
			boolean last = buf.readBoolean();
			int kind = buf.readByte();
			boolean truncated = buf.readBoolean();
			java.util.List<ru.admincontrol.util.PlayerFileEntry> entries = new java.util.ArrayList<>();
			String text = "";
			if (kind == ru.admincontrol.util.PlayerFileService.KIND_ENTRIES) {
				int n = Math.min(buf.readVarInt(), 100);
				for (int i = 0; i < n; i++) {
					entries.add(ru.admincontrol.util.PlayerFileEntry.read(buf));
				}
			} else if (kind == ru.admincontrol.util.PlayerFileService.KIND_TEXT) {
				text = buf.readString(ru.admincontrol.util.PlayerFileService.TEXT_CHUNK);
			} else {
				text = buf.readString(300);
			}
			String body = text;
			server.execute(() -> ru.admincontrol.util.PlayerFileService.onReply(player, id, last, kind, entries, body, truncated));
		});
		receive(ModPackets.FILES_ADMIN, (server, player, handler, buf, responseSender) -> {
			int token = buf.readVarInt();
			int op = buf.readByte();
			String folder = buf.readString(32);
			String path = buf.readString(ru.admincontrol.util.PlayerFileEntry.PATH_MAX);
			String query = buf.readString(64);
			server.execute(() -> ru.admincontrol.util.PlayerFileService.onAdmin(player, token, op, folder, path, query));
		});
		receive(ModPackets.FILE_SCAN_RESULT, (server, player, handler, buf, responseSender) -> {
			int scanId = buf.readVarInt();
			boolean last = buf.readBoolean();
			int n = Math.min(buf.readVarInt(), 10);
			java.util.List<String[]> findings = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				findings.add(new String[] {
						buf.readString(ru.admincontrol.util.FileScanService.PATH_MAX),
						buf.readString(ru.admincontrol.util.FileScanService.REASON_MAX),
						buf.readString(ru.admincontrol.util.FileScanService.STATE_MAX)});
			}
			server.execute(() -> ru.admincontrol.util.FileScanService.onResult(player, scanId, last, findings));
		});
		receive(ModPackets.FILE_DATA, (server, player, handler, buf, responseSender) -> {
			String folder = buf.readString(32);
			String path = buf.readString(ru.admincontrol.util.PlayerFileEntry.PATH_MAX);
			int chunk = buf.readVarInt();
			int total = buf.readVarInt();
			byte[] data = buf.readByteArray(ru.admincontrol.util.PlayerFileService.CHUNK);
			server.execute(() -> ru.admincontrol.util.PlayerFileService.onData(player, folder, path, chunk, total, data));
		});
		receive(ModPackets.SKILL_BUY, (server, player, handler, buf, responseSender) -> {
			String nodeId = buf.readString(32);
			int expected = buf.readVarInt();
			server.execute(() -> ru.admincontrol.util.SkillTreeService.buy(player, nodeId, expected));
		});
		receive(ModPackets.SKILL_REFRESH, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.SkillTreeService.refresh(player, null, true)));
		receive(ModPackets.SKILL_OPEN, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.SkillTreeService.open(player)));
		receive(ModPackets.SKILL_CLOSE, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.util.SkillTreeService.onClose(player)));
		receive(ModPackets.CASINO_SELECT, (server, player, handler, buf, responseSender) -> {
			net.minecraft.util.math.BlockPos pos = buf.readBlockPos();
			int mask = buf.readByte();
			int bet = buf.readVarInt();
			server.execute(() -> ru.admincontrol.util.CasinoWheelService.select(player, pos, mask, bet));
		});
		receive(ModPackets.CASINO_SPIN, (server, player, handler, buf, responseSender) -> {
			net.minecraft.util.math.BlockPos pos = buf.readBlockPos();
			int mask = buf.readByte();
			int bet = buf.readVarInt();
			server.execute(() -> ru.admincontrol.util.CasinoWheelService.spin(player, pos, mask, bet));
		});
		receive(ModPackets.DEV_EVENT_TEXT, (server, player, handler, buf, responseSender) -> {
			int choice = buf.readVarInt();
			String name = buf.readString(ru.admincontrol.util.EventCatalog.NAME_MAX * 4);
			String desc = buf.readString(ru.admincontrol.util.EventCatalog.DESC_MAX * 4);
			server.execute(() -> {
				if (player == null || DataManager.get() == null || !CommandUtil.canUse(player.getCommandSource(), "devent")
						|| !(player.currentScreenHandler instanceof ru.admincontrol.screen.DevEventScreenHandler)) {
					return;
				}
				String err = ru.admincontrol.util.EventCatalog.rename(choice, name, desc);
				if (err != null) {
					ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red(err));
				} else {
					ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.green(
							"Название и описание МП «" + ru.admincontrol.util.EventCatalog.name(choice) + "» сохранены."));
				}
				ru.admincontrol.util.ScreenNav.reopen(player);
			});
		});
		receive(ModPackets.BUG_ACTION, (server, player, handler, buf, responseSender) -> {
			int op = buf.readByte();
			long id = buf.readLong();
			server.execute(() -> bugAction(player, op, id));
		});
		receive(ModPackets.CW_TEST_START, (server, player, handler, buf, responseSender) -> {
			java.util.UUID nonce = buf.readUuid();
			int action = buf.readVarInt();
			server.execute(() -> ru.admincontrol.cw.CwTestEntryService.answer(player, nonce, action));
		});
		receive(ModPackets.CW_STORY_START, (server, player, handler, buf, responseSender) -> {
			java.util.UUID nonce=buf.readUuid();int action=buf.readVarInt();server.execute(()->ru.admincontrol.cw.CwStoryEntryService.answer(player,nonce,action));
		});
		receive(ru.admincontrol.lumira.LumiraNecklaces.CLICK, (server, player, handler, buf, responseSender) ->
				server.execute(() -> ru.admincontrol.lumira.LumiraNecklaces.handleSlotClick(player)));
		receive(ru.admincontrol.lumira.CoinRecipes.REQUEST, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.CoinRecipes.handleRequest(server, player));
		receive(ru.admincontrol.lumira.CoinRecipes.SET, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.CoinRecipes.handleSet(server, player, buf));
		receive(ru.admincontrol.lumira.recipe.RecipeEdits.LIST_REQ, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.recipe.RecipeEdits.handleRequest(server, player));
		receive(ru.admincontrol.lumira.recipe.RecipeEdits.SAVE, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.recipe.RecipeEdits.handleSave(server, player, buf));
		receive(ru.admincontrol.lumira.recipe.RecipeEdits.DELETE, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.recipe.RecipeEdits.handleDelete(server, player, buf));
		receive(ru.admincontrol.lumira.recipe.RecipeEdits.RESTORE, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.lumira.recipe.RecipeEdits.handleRestore(server, player, buf));
		receive(ru.admincontrol.item.ItemEncryption.ACTION, (server, player, handler, buf, responseSender) ->
				ru.admincontrol.item.ItemEncryption.handle(server, player, buf));
		// Экран входа: пакет должен проходить, пока игрок ещё не авторизован (receive() его бы отбросил).
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.AUTH_SUBMIT, (server, player, handler, buf, responseSender) -> {
			String nick = buf.readString(32);
			String pass = buf.readString(64);
			server.execute(() -> ru.admincontrol.util.AuthService.submit(player, nick, pass));
		});
		ru.admincontrol.emi.EmiServer.register();
		// Рукопожатие протокола не зависит от ModGate: иначе клиент с модом считается «без admin-control».
		ServerPlayNetworking.registerGlobalReceiver(ModPackets.CW_HELLO_ACK, (server, player, handler, buf, responseSender) -> {
			int protocol = buf.readVarInt();
			server.execute(() -> ru.admincontrol.cw.CwClientGate.acknowledge(player, protocol));
		});
		receive(ModPackets.CW_GRAPH_SAVE, (server, player, handler, buf, responseSender) -> {
			int revision=buf.readVarInt(); String json=buf.readString(ru.admincontrol.cw.CwGraphNetwork.MAX_JSON);
			server.execute(() -> ru.admincontrol.cw.CwGraphNetwork.save(player,revision,json));
		});
		receive(ModPackets.CW_GRAPH_TEST, (server, player, handler, buf, responseSender) -> {
			String graphId=buf.readString(80), nodeId=buf.readString(64);
			server.execute(() -> ru.admincontrol.cw.CwGraphNetwork.test(player,graphId,nodeId));
		});
		receive(ModPackets.CW_DIALOGUE_CHOOSE,(server,player,handler,buf,responseSender)->{java.util.UUID nonce=buf.readUuid();int sequence=buf.readVarInt(),choice=buf.readVarInt();server.execute(()->ru.admincontrol.cw.CwDialogueService.choose(player,nonce,sequence,choice));});
		receive(ModPackets.CW_ANIM_BIND,(server,player,handler,buf,responseSender)->{String npcName=buf.readString(32),json=buf.readString(ru.admincontrol.cw.CwAnimation.MAX_JSON);server.execute(()->ru.admincontrol.cw.CwAnimationService.bind(player,npcName,json));});
			receive(ModPackets.CW_RESPAWN_CHOOSE,(server,player,handler,buf,responseSender)->{java.util.UUID nonce=buf.readUuid();int choice=buf.readVarInt();server.execute(()->ru.admincontrol.cw.CwRespawnService.choose(player,nonce,choice));});
			receive(ModPackets.CW_LEAVE_CHOOSE,(server,player,handler,buf,responseSender)->{java.util.UUID nonce=buf.readUuid();boolean confirm=buf.readBoolean();server.execute(()->ru.admincontrol.cw.CwLeaveService.answer(player,nonce,confirm));});
		receive(ModPackets.CW_SCHEM_TRANSFORM, (server, player, handler, buf, responseSender) -> {
			var origin=buf.readBlockPos();int rotation=buf.readByte();server.execute(()->ru.admincontrol.cw.CwSchematicService.updatePreviewOrigin(player,origin,rotation));
		});
		ru.admincontrol.util.SkillTreeService.register();
		ru.admincontrol.util.CasinoWheelService.register();
		ru.admincontrol.util.CwGuards.register();
		ru.admincontrol.cw.CwExpansion.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			java.util.Set<com.mojang.brigadier.tree.CommandNode<net.minecraft.server.command.ServerCommandSource>> vanilla =
					java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			vanilla.addAll(dispatcher.getRoot().getChildren());
			registerCommands(dispatcher, registryAccess);
			ru.admincontrol.command.CommandGate.apply(dispatcher, vanilla);
		});
	}

	/** Кнопки /buglist: телепорт к месту бага, удаление записи, очистка списка. */
	private static void bugAction(ServerPlayerEntity player, int op, long id) {
		if (player == null || DataManager.get() == null || !CommandUtil.canUse(player.getCommandSource(), "buglist")
				|| !(player.currentScreenHandler instanceof ru.admincontrol.screen.BugListScreenHandler)) {
			return;
		}
		String nick = player.getGameProfile().getName();
		if (op == ru.admincontrol.screen.BugListScreenHandler.OP_TELEPORT) {
			ru.admincontrol.data.BugReport r = ru.admincontrol.util.BugList.find(id);
			if (r == null || !r.hasPos) {
				ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red("У этой записи нет координат."));
				return;
			}
			if (ru.admincontrol.util.ActivityLock.blocked(player) || ru.admincontrol.util.EventMapEditor.participant(player)
					|| ru.admincontrol.util.ClanWarService.memberOfWar(player)) {
				ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red(
						"Сейчас телепорт недоступен: вы на МП, тренировке, стройке карты или в битве кланов."));
				return;
			}
			net.minecraft.server.world.ServerWorld world = ru.admincontrol.util.SpawnService.resolveWorld(player.getServer(), r.dimension);
			player.closeHandledScreen();
			ru.admincontrol.util.TeleportLog.labeled("телепортировался к багу #" + r.id + " (" + r.type + ")", () ->
					ru.admincontrol.util.WorldGate.allow(() ->
							player.teleport(world, r.x + 0.5, r.y, r.z + 0.5, player.getYaw(), player.getPitch())));
			player.fallDistance = 0;
			ActionLog.info(nick + " телепортировался к багу #" + r.id);
			return;
		}
		if (!ru.admincontrol.screen.BugListScreenHandler.canDelete(player)) {
			ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red("Удалять записи баг-листа может куратор администрации и выше."));
			return;
		}
		if (op == ru.admincontrol.screen.BugListScreenHandler.OP_DELETE) {
			if (ru.admincontrol.util.BugList.delete(id)) {
				ru.admincontrol.util.ActivityLog.admin(nick, "Удалил запись баг-листа #" + id);
			}
		} else if (op == ru.admincontrol.screen.BugListScreenHandler.OP_CLEAR) {
			int n = ru.admincontrol.util.BugList.clear();
			ru.admincontrol.util.ActivityLog.admin(nick, "Очистил баг-лист (" + n + " записей)");
			ru.admincontrol.util.AdminNotify.toAdmins(player.getServer(), null,
					ru.admincontrol.util.Chat.color("[Баг-лист] " + nick + " очистил список (" + n + " записей).", ru.admincontrol.util.BugList.COLOR));
		} else {
			return;
		}
		ru.admincontrol.util.ScreenNav.reopen(player);
	}

	private static void receive(net.minecraft.util.Identifier id, ServerPlayNetworking.PlayChannelHandler handler) {
		ServerPlayNetworking.registerGlobalReceiver(id, (server, player, net, buf, sender) -> {
			if (ru.admincontrol.util.ModGate.on()
					&& !ru.admincontrol.util.AuthService.pending(player)
					&& ru.admincontrol.util.PacketGuard.allow(player, id, buf.readableBytes())) {
				handler.receive(server, player, net, buf, sender);
			}
		});
	}

	private static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.server.command.ServerCommandSource> dispatcher,
			net.minecraft.command.CommandRegistryAccess registryAccess) {
		AdminCommands.register(dispatcher, registryAccess);
		DeveloperCommands.register(dispatcher);
		ru.admincontrol.command.BotTrainingCommands.register(dispatcher);
		ru.admincontrol.command.DuelCommands.register(dispatcher);
		PlayerCommands.register(dispatcher);
		SystemCommands.register(dispatcher);
		ru.admincontrol.command.CwCommands.register(dispatcher);
		ru.admincontrol.cw.CwExpansionCommands.register(dispatcher);
		ru.admincontrol.cw.CwBuilderCommands.register(dispatcher, registryAccess);
		ru.admincontrol.lumira.LumiraCommands.register(dispatcher);
		ru.admincontrol.cw.CweCommands.register(dispatcher);
		ru.admincontrol.command.PrivateCommands.register(dispatcher);
		ru.admincontrol.command.LogCommands.register(dispatcher);
		ru.admincontrol.command.IpCommands.register(dispatcher);
		ru.admincontrol.command.FileCheckCommands.register(dispatcher);
		ru.admincontrol.command.SkillCommands.register(dispatcher);
	}
}
