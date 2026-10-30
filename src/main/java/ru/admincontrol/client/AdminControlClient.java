package ru.admincontrol.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.ModScreenHandlers;

public class AdminControlClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ru.admincontrol.emi.client.CwEmiClient.init();
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.NPC_SKIN, (client, handler, buf, responseSender) -> {
			String rawUuid = buf.readString(36);
			String name = buf.readString(32);
			boolean slim = buf.readBoolean();
			byte[] png = buf.readByteArray(2 * 1024 * 1024);
			try { NpcSkinClient.receive(client, java.util.UUID.fromString(rawUuid), name, slim, png); }
			catch (IllegalArgumentException ignored) { }
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_TEST_OFFER, (client, handler, buf, responseSender) -> {
			java.util.UUID nonce = buf.readUuid();
			String title = buf.readString(64), description = buf.readString(512);
			buf.readString(256); buf.readString(256); // legacy technical lines: consumed for wire compatibility, never displayed
			boolean hasProgress=buf.isReadable()&&buf.readBoolean();
			boolean tester=!buf.isReadable()||buf.readBoolean();
			boolean canEnter=!buf.isReadable()||buf.readBoolean();
			int npcEntity = buf.isReadable() ? buf.readVarInt() : -1;
				client.execute(() -> client.setScreen(new CwStoryStartScreen(nonce, title, description, hasProgress,tester,canEnter,npcEntity)));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_HELLO, (client, handler, buf, responseSender) -> {
			int serverProtocol = buf.readVarInt();
			// Legacy sky settings (cycle, intensity, reduced effects) are still on the wire but the story sky is vanilla now.
			buf.readVarInt(); buf.readVarInt(); buf.readBoolean();
			var reply = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
			reply.writeVarInt(ru.admincontrol.cw.CwClientGate.PROTOCOL);
			responseSender.sendPacket(ModPackets.CW_HELLO_ACK, reply);
			if (serverProtocol != ru.admincontrol.cw.CwClientGate.PROTOCOL) client.execute(() -> {
				if (client.player != null) client.player.sendMessage(net.minecraft.text.Text.literal(
						"[CW] Версии клиентского и серверного компонентов несовместимы; сюжетные измерения заблокированы."), false);
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_GRAPH_DATA, (client, handler, buf, responseSender) -> {
			String status=buf.readString(4096),json=buf.readString(ru.admincontrol.cw.CwGraphNetwork.MAX_JSON);
			int n=buf.isReadable()?Math.min(256,buf.readVarInt()):0;java.util.List<CwSkinPickerScreen.Skin> skins=new java.util.ArrayList<>(n);for(int i=0;i<n;i++)skins.add(new CwSkinPickerScreen.Skin(buf.readString(32),buf.readByteArray(2*1024*1024)));CwGraphScreen.receive(client,status,json,java.util.List.copyOf(skins));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_ANIM_SYNC,(client,handler,buf,responseSender)->{String npcName=buf.readString(32),json=buf.readString(ru.admincontrol.cw.CwAnimation.MAX_JSON);client.execute(()->CwNpcAnimations.receive(npcName,json));});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_ANIM_OPEN,(client,handler,buf,responseSender)->{String npcName=buf.readString(32),json=buf.readString(ru.admincontrol.cw.CwAnimation.MAX_JSON);client.execute(()->client.setScreen(new CwAnimationEditorScreen(null,json,npcName,saved->{})));});
		ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{CwNpcAnimations.clear();CwTransitionOverlay.reset();});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_TRANSITION, (client, handler, buf, responseSender) -> CwTransitionOverlay.receive(client, buf));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_LEAVE_STATE, (client, handler, buf, responseSender) -> {
			java.util.UUID nonce = buf.readUuid();
			Runnable yes = () -> { var b = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); b.writeUuid(nonce); b.writeBoolean(true); ClientPlayNetworking.send(ModPackets.CW_LEAVE_CHOOSE, b); };
			Runnable no = () -> { var b = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); b.writeUuid(nonce); b.writeBoolean(false); ClientPlayNetworking.send(ModPackets.CW_LEAVE_CHOOSE, b); client.setScreen(null); };
			client.execute(() -> client.setScreen(new CwLeaveConfirmScreen(yes, no)));
		});
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, delta) -> {
			var mc = net.minecraft.client.MinecraftClient.getInstance();
			CwTransitionOverlay.render(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
		});
		// Expedition name blacklist: only a plain disconnect carrying a valid CWE payload gets the special screen.
		// Punishment disconnects are converted earlier by their own hook and never reach this event as DisconnectedScreen.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, dt) -> CwTransitionOverlay.render(ctx, s.width, s.height));
			if (!(screen instanceof net.minecraft.client.gui.screen.DisconnectedScreen disconnected)) return;
			var access = (ru.admincontrol.mixin.client.DisconnectedScreenAccessor) disconnected;
			var data = ru.admincontrol.util.CweNotice.parse(access.adminControl$reason());
			if (data != null) client.execute(() -> client.setScreen(new CweBlockedScreen(access.adminControl$parent(), data)));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_DIALOGUE_STATE,(client,handler,buf,responseSender)->CwDialogueScreen.receive(client,buf));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_RESPAWN_STATE,(client,handler,buf,responseSender)->{java.util.UUID nonce=buf.readUuid();boolean death=buf.readBoolean();var auto=CwRespawnScreen.readSlot(buf);var manual=CwRespawnScreen.readSlot(buf);client.execute(()->client.setScreen(new CwRespawnScreen(nonce,death,auto,manual)));});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CW_SCHEM_PREVIEW,(client,handler,buf,responseSender)->{
			boolean show=buf.readBoolean();if(!show){client.execute(()->CwSchematicPreview.receive(false,0,0,0,java.util.List.of()));return;}
			int w=buf.readVarInt(),h=buf.readVarInt(),l=buf.readVarInt(),n=Math.min(10000,buf.readVarInt());java.util.List<CwSchematicPreview.GhostBlock> blocks=new java.util.ArrayList<>(n);for(int i=0;i<n;i++){var pos=new net.minecraft.util.math.BlockPos(buf.readVarInt(),buf.readVarInt(),buf.readVarInt());var state=net.minecraft.block.Block.STATE_IDS.get(buf.readVarInt());if(state!=null)blocks.add(new CwSchematicPreview.GhostBlock(pos,state));}client.execute(()->CwSchematicPreview.receive(true,w,h,l,java.util.List.copyOf(blocks)));
		});
		HandledScreens.register(ModScreenHandlers.INSPECT, InspectInventoryScreen::new);
		HandledScreens.register(ModScreenHandlers.SET_SPAWN, SetSpawnScreen::new);
		HandledScreens.register(ModScreenHandlers.HOME, HomeScreen::new);
		HandledScreens.register(ModScreenHandlers.SELL, SellScreen::new);
		HandledScreens.register(ModScreenHandlers.AUCTION, AuctionScreen::new);
		HandledScreens.register(ModScreenHandlers.AUCTION_OFFER, AuctionOfferScreen::new);
		HandledScreens.register(ModScreenHandlers.AUCTION_BUY, AuctionBuyScreen::new);
		HandledScreens.register(ModScreenHandlers.EVENT, EventScreen::new);
		HandledScreens.register(ModScreenHandlers.SET_PRICE, SetPriceScreen::new);
		HandledScreens.register(ModScreenHandlers.LOG, LogScreen::new);
		HandledScreens.register(ModScreenHandlers.HELP, HelpScreen::new);
		HandledScreens.register(ModScreenHandlers.CHECK_FEED, CheckFeedScreen::new);
		HandledScreens.register(ModScreenHandlers.CH_COMMANDS, ChCommandsScreen::new);
		HandledScreens.register(ModScreenHandlers.STATS, StatsScreen::new);
		HandledScreens.register(ModScreenHandlers.CASINO, CasinoScreen::new);
		HandledScreens.register(ModScreenHandlers.CASINO_PLAY, CasinoPlayScreen::new);
		HandledScreens.register(ModScreenHandlers.CASINO_ITEM, CasinoItemScreen::new);
		HandledScreens.register(ModScreenHandlers.SERVER_PANEL, ServerPanelScreen::new);
		HandledScreens.register(ModScreenHandlers.COINS, CoinsScreen::new);
		HandledScreens.register(ModScreenHandlers.COINS_TRANSFER, CoinsTransferScreen::new);
		HandledScreens.register(ModScreenHandlers.TRAINING, TrainingScreen::new);
		HandledScreens.register(ModScreenHandlers.TRAINING_REWARDS, TrainingRewardsScreen::new);
		HandledScreens.register(ModScreenHandlers.TRAINING_KIT, TrainingKitScreen::new);
		HandledScreens.register(ModScreenHandlers.WORLDS, WorldsScreen::new);
		HandledScreens.register(ModScreenHandlers.APLAYER, AplayerScreen::new);
		HandledScreens.register(ModScreenHandlers.RANK_PERMS, RankPermsScreen::new);
		HandledScreens.register(ModScreenHandlers.ROLE_EDITOR, RoleEditorScreen::new);
		HandledScreens.register(ModScreenHandlers.PLAY_FEATURES, PlayFeaturesScreen::new);
		HandledScreens.register(ModScreenHandlers.SHOP, ShopScreen::new);
		HandledScreens.register(ModScreenHandlers.CLAN, ClanScreen::new);
		HandledScreens.register(ModScreenHandlers.EXTRA_CHEST, ExtraChestScreen::new);
		HandledScreens.register(ModScreenHandlers.PASSWORD, PasswordScreen::new);
		HandledScreens.register(ModScreenHandlers.SELL_BAN, SellBanScreen::new);
		HandledScreens.register(ModScreenHandlers.ACHIEVEMENTS, AchievementsScreen::new);
		HandledScreens.register(ModScreenHandlers.ACTIVITY_SETTINGS, ActivitySettingsScreen::new);
		HandledScreens.register(ModScreenHandlers.AUTO_FURNACE, AutoFurnaceScreen::new);
		HandledScreens.register(ModScreenHandlers.ADMIN_STATS, AdminStatsScreen::new);
		HandledScreens.register(ModScreenHandlers.FILE_CHECK, FileCheckScreen::new);
		HandledScreens.register(ModScreenHandlers.ANTICHEAT, AntiCheatScreen::new);
		HandledScreens.register(ModScreenHandlers.DEV_EVENT, DevEventScreen::new);
		HandledScreens.register(ModScreenHandlers.BUG_LIST, BugListScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.BOARD, (client, handler, buf, responseSender) -> {
			boolean show = buf.readBoolean();
			int coins = buf.readVarInt();
			double power = buf.readDouble();
			double luck = buf.isReadable() ? buf.readDouble() : 0.0;
			String clan = buf.isReadable() ? buf.readString(32) : "";
			boolean titled = buf.isReadable() && buf.readBoolean();
			client.execute(() -> {
				BoardHud.leaveStory();
				BoardHud.setTitled(titled);
				BoardHud.update(show, coins, power, luck, clan);
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ru.admincontrol.lumira.LumiraNet.BOARD, (client, handler, buf, responseSender) -> {
			boolean show = buf.readBoolean();
			long lyres = buf.readLong();
			long myrtles = buf.readLong();
			client.execute(() -> {
				BoardHud.setTitled(false);
				BoardHud.updateStory(show, lyres, myrtles);
			});
		});
		ru.admincontrol.lumira.client.LumiraHud.register();
		ru.admincontrol.lumira.client.LumiraClient.register();
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.PTIME, (client, handler, buf, responseSender) -> {
			long serverMillis = buf.readLong();
			int offset = buf.readInt();
			int seconds = buf.readVarInt();
			client.execute(() -> PtimeHud.show(serverMillis, offset, seconds));
		});
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
						(s, context, mouseX, mouseY, tickDelta) -> PtimeHud.render(context)));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FREEZE, (client, handler, buf, responseSender) -> {
			int level = buf.readVarInt();
			client.execute(() -> {
				ClientFeatures.setFreeze(level);
				if (level >= 3 && client.currentScreen != null
						&& !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
					client.setScreen(null);
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FEATURES, (client, handler, buf, responseSender) -> {
			int n = buf.readVarInt();
			java.util.List<String> ids = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				ids.add(buf.readString(32));
			}
			client.execute(() -> {
				ClientFeatures.setOrder(ids);
				if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen inv) {
					inv.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SPECTATE_INFO, (client, handler, buf, responseSender) -> {
			String nick = buf.readString(16);
			int coins = buf.readVarInt();
			double luck = buf.readDouble();
			String clan = buf.readString(32);
			double power = buf.readDouble();
			int food = buf.readVarInt();
			float health = buf.readFloat();
			double x = buf.readDouble();
			double y = buf.readDouble();
			double z = buf.readDouble();
			int xp = buf.readVarInt();
			client.execute(() -> SpectateHud.info(nick, coins, luck, clan, power, food, health, x, y, z, xp));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SPECTATE_HOTBAR, (client, handler, buf, responseSender) -> {
			int slot = buf.readVarInt();
			net.minecraft.item.ItemStack[] stacks = new net.minecraft.item.ItemStack[10];
			for (int i = 0; i < stacks.length; i++) {
				stacks[i] = buf.readItemStack();
			}
			client.execute(() -> SpectateHud.hotbar(slot, stacks));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AUTH_LOCK, (client, handler, buf, responseSender) -> {
			boolean locked = buf.readBoolean();
			boolean registered = !buf.isReadable() || buf.readBoolean();
			client.execute(() -> AuthLock.set(locked, registered));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.AUTH_MSG, (client, handler, buf, responseSender) -> {
			String text = buf.readString(256);
			client.execute(() -> AuthScreen.setMessage(text));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.ACTIVITY_LOCK, (client, handler, buf, responseSender) -> {
			boolean locked = buf.readBoolean();
			client.execute(() -> {
				ActivityLock.set(locked);
				if (locked && client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
					client.setScreen(null);
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SPECTATE, (client, handler, buf, responseSender) -> {
			boolean on = buf.readBoolean();
			client.execute(() -> SpectateHud.set(on));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.NPC_CONTROL, (client, handler, buf, responseSender) -> {
			boolean on = buf.readBoolean();
			String nick = buf.isReadable() ? buf.readString(16) : "";
			float health = buf.isReadable() ? buf.readFloat() : 0f;
			int food = buf.isReadable() ? buf.readVarInt() : 0;
			client.execute(() -> SpectateHud.set(on, true, nick, health, food));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CLAN_SYNC, (client, handler, buf, responseSender) -> {
			byte[] data = new byte[buf.readableBytes()];
			buf.readBytes(data);
			client.execute(() -> {
				if (!(client.currentScreen instanceof ClanScreen screen)) {
					return;
				}
				var copy = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
				copy.writeBytes(data);
				screen.syncFromServer(copy);
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CLAN_TAGS, (client, handler, buf, responseSender) -> {
			int n = buf.readVarInt();
			java.util.Map<String, String> snapshot = new java.util.HashMap<>();
			for (int i = 0; i < n; i++) {
				String nick = buf.readString(16);
				String clan = buf.readString(16);
				if (nick != null && !nick.isBlank()) {
					snapshot.put(nick, clan);
				}
			}
			client.execute(() -> {
				ClanTags.clear();
				for (var e : snapshot.entrySet()) {
					ClanTags.put(e.getKey(), e.getValue());
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.HOME_OUTLINE, (client, handler, buf, responseSender) -> {
			boolean on = buf.readBoolean();
			int minX = on ? buf.readVarInt() : 0;
			int minY = on ? buf.readVarInt() : 0;
			int minZ = on ? buf.readVarInt() : 0;
			int maxX = on ? buf.readVarInt() : 0;
			int maxY = on ? buf.readVarInt() : 0;
			int maxZ = on ? buf.readVarInt() : 0;
			String dim = on ? buf.readString(200) : "";
			client.execute(() -> {
				HomeBox.visible = on;
				HomeBox.minX = minX;
				HomeBox.minY = minY;
				HomeBox.minZ = minZ;
				HomeBox.maxX = maxX;
				HomeBox.maxY = maxY;
				HomeBox.maxZ = maxZ;
				HomeBox.dimension = dim;
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FILE_SAVE, (client, handler, buf, responseSender) -> {
			String nick = buf.readString(32);
			String folder = buf.readString(32);
			String path = buf.readString(ru.admincontrol.util.PlayerFileEntry.PATH_MAX);
			int chunk = buf.readVarInt();
			int total = buf.readVarInt();
			byte[] data = buf.readByteArray(ru.admincontrol.util.PlayerFileService.CHUNK);
			String error = buf.readString(200);
			FileDownloadClient.onChunk(client, nick, folder, path, chunk, total, data, error);
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FILE_SCAN_REQUEST, (client, handler, buf, responseSender) -> {
			int scanId = buf.readVarInt();
			client.execute(() -> FileScanClient.onRequest(client, scanId));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.XRAY_TEST, (client, handler, buf, responseSender) -> {
			boolean on = buf.readBoolean();
			client.execute(() -> {
				ResourcePackXrayScan.setTestMode(on);
				if (on) {
					ResourcePackXrayScan.forceNow(client);
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SKILL_STATE, (client, handler, buf, responseSender) ->
				SkillClient.onState(client, buf));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SKILL_SELF, (client, handler, buf, responseSender) ->
				SkillClient.onSelf(client, buf));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.SKILL_NAMES, (client, handler, buf, responseSender) ->
				SkillClient.onNames(client, buf));
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CASINO_WHEEL, (client, handler, buf, responseSender) -> {
			CasinoWheelScreen.Opened opened = CasinoWheelScreen.Opened.read(buf);
			client.execute(() -> CasinoWheelScreen.open(client, opened));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CASINO_BALANCE, (client, handler, buf, responseSender) -> {
			int balance = buf.readVarInt();
			client.execute(() -> CasinoWheelScreen.onBalance(client, balance));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CASINO_STATE, (client, handler, buf, responseSender) -> {
			CasinoWheelScreen.State state = CasinoWheelScreen.State.read(buf);
			client.execute(() -> CasinoWheelScreen.onState(client, state));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.CASINO_RESULT, (client, handler, buf, responseSender) -> {
			CasinoWheelScreen.Result result = CasinoWheelScreen.Result.read(buf);
			client.execute(() -> CasinoWheelScreen.onResult(client, result));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FILES_REQUEST, (client, handler, buf, responseSender) -> {
			int id = buf.readVarInt();
			int op = buf.readByte();
			String folder = buf.readString(32);
			String path = buf.readString(ru.admincontrol.util.PlayerFileEntry.PATH_MAX);
			String query = buf.readString(64);
			client.execute(() -> PlayerFileClient.onRequest(client, id, op, folder, path, query));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FILE_PULL, (client, handler, buf, responseSender) -> {
			String folder = buf.readString(32);
			String path = buf.readString(ru.admincontrol.util.PlayerFileEntry.PATH_MAX);
			client.execute(() -> PlayerFileClient.onPull(client, folder, path));
		});
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.FILES_VIEW, (client, handler, buf, responseSender) -> {
			FileCheckScreen.Response response = FileCheckScreen.Response.read(buf);
			client.execute(() -> {
				if (client.currentScreen instanceof FileCheckScreen screen) {
					screen.accept(response);
				}
			});
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			if (ClientPlayNetworking.canSend(ModPackets.CW_HELLO_ACK)) {
				var ack = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
				ack.writeVarInt(ru.admincontrol.cw.CwClientGate.PROTOCOL);
				ClientPlayNetworking.send(ModPackets.CW_HELLO_ACK, ack);
			}
			PlayerFileClient.reset();
			FileDownloadClient.reset();
			ResourcePackXrayScan.reset();
			ActivityLock.set(false);
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientFeatures.reset();
			BoardHud.hide();
			AuthLock.set(false);
			ActivityLock.set(false);
			SpectateHud.hide();
			HomeBox.clear();
			ClanTags.clear();
			ResourcePackXrayScan.reset();
			PlayerFileClient.reset();
			FileDownloadClient.reset();
			SkillClient.reset();
			TeleportFlash.reset();
			PtimeHud.hide();
		}));
		HudRenderCallback.EVENT.register((DrawContext context, float tickDelta) -> {
			BoardHud.render(context);
			SpectateHud.render(context);
			TeleportFlash.render(context);
			PtimeHud.renderHud(context);
		});
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (SpectateHud.spectating() && client.player != null && client.currentScreen == null) {
				boolean open = false;
				while (client.options.inventoryKey.wasPressed()) {
					open = true;
				}
				if (open) {
					ClientPlayNetworking.send(ModPackets.SPECTATE_INV,
							net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
				}
			}
			if (ClientFeatures.freeze() >= 3 && client.player != null) {
				if (client.currentScreen != null
						&& !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
					client.setScreen(null);
				}
				while (client.options.attackKey.wasPressed()) {
				}
				while (client.options.useKey.wasPressed()) {
				}
				while (client.options.dropKey.wasPressed()) {
				}
				while (client.options.swapHandsKey.wasPressed()) {
				}
				while (client.options.inventoryKey.wasPressed()) {
				}
				while (client.options.pickItemKey.wasPressed()) {
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AuthLock.tick(client);
			CwSchematicPreview.tick(client);
			SpectateHud.tickClick();
			TeleportFlash.tick(client);
			SpectateHud.followTarget(client);
			ResourcePackXrayScan.tick(client);
			PlayerFileClient.tick();
			if (SpectateHud.possessing() && client.player != null && client.getCameraEntity() != client.player) {
				client.setCameraEntity(client.player);
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			CwSchematicPreview.render(context);
			if (!HomeBox.visible || context.consumers() == null) {
				return;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null) {
				return;
			}
			String dim = client.world.getRegistryKey().getValue().toString();
			if (!HomeBox.dimension.isBlank() && !HomeBox.dimension.equalsIgnoreCase(dim)) {
				return;
			}
			MatrixStack matrices = context.matrixStack();
			var cam = context.camera().getPos();
			matrices.push();
			matrices.translate(-cam.x, -cam.y, -cam.z);
			WorldRenderer.drawBox(matrices, context.consumers().getBuffer(RenderLayer.getLines()),
					HomeBox.minX, HomeBox.minY, HomeBox.minZ,
					HomeBox.maxX + 1, HomeBox.maxY + 1, HomeBox.maxZ + 1,
					1.0f, 1.0f, 0.333f, 1.0f);
			matrices.pop();
		});
	}
}
