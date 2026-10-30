package ru.admincontrol.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.command.SecretCommands;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.CommandInputLog;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	/** До входа сервер игнорирует любые игровые пакеты, кроме движения (его гасит lockAuthMove), чата и команд /l и /password. */
	@Inject(method = {"onClickSlot", "onUpdateSelectedSlot", "onPlayerInteractItem", "onPlayerInteractBlock",
			"onPlayerInteractEntity", "onPlayerAction", "onHandSwing", "onPickFromInventory", "onButtonClick",
			"onCraftRequest", "onClientCommand", "onPlayerInput", "onCreativeInventoryAction", "onRecipeBookData",
			"onRecipeCategoryOptions", "onAdvancementTab", "onRequestCommandCompletions", "onSelectMerchantTrade",
			"onRenameItem", "onUpdateBeacon", "onBookUpdate", "onUpdateSign", "onSpectatorTeleport",
			"onUpdatePlayerAbilities", "onVehicleMove", "onQueryEntityNbt", "onQueryBlockNbt"},
			at = @At("HEAD"), cancellable = true)
	private void adminControl$authSilence(CallbackInfo ci) {
		if (this.player != null && ru.admincontrol.util.AuthService.pending(this.player)) {
			ci.cancel();
		}
	}

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void adminControl$isolateCwChat(ChatMessageC2SPacket packet, CallbackInfo ci) {
		if (player == null || player.getServer() == null || packet == null) return;
		String worldId=player.getWorld().getRegistryKey().getValue().toString();
		boolean cw=worldId.equals("admin_control:cw_builder")||worldId.matches("admin_control:cw_story_(?:[0-9]|1[0-5])");
		if(!cw)return;
		String message=packet.chatMessage();if(message==null||message.isBlank()) {ci.cancel();return;}
		String label=worldId.equals("admin_control:cw_builder")?"Стройка CW":"Сюжет CW";
		net.minecraft.text.Text out=net.minecraft.text.Text.literal("["+label+"] <"+player.getName().getString()+"> "+message);
		for(ServerPlayerEntity target:player.getServer().getPlayerManager().getPlayerList())if(target.getWorld().getRegistryKey().equals(player.getWorld().getRegistryKey()))target.sendMessage(out,false);
		ci.cancel();
	}

	@Inject(method = "onHandSwing", at = @At("HEAD"))
	private void adminControl$swing(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (this.player != null && !this.player.getWorld().isClient) {
			ru.admincontrol.util.AntiCheatService.click(this.player);
		}
	}

	@Inject(method = "onPlayerAction", at = @At("HEAD"))
	private void adminControl$playerAction(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (this.player == null || packet == null || this.player.getWorld().isClient) {
			return;
		}
		net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action action = packet.getAction();
		if (action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
			ru.admincontrol.util.AntiCheatService.clickStartBreak(this.player);
		}
	}

	@Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
	private void adminControl$hitEntity(net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (this.player == null || packet == null || this.player.getWorld().isClient) {
			return;
		}
		net.minecraft.entity.Entity target = packet.getEntity(this.player.getServerWorld());
		boolean[] attack = {false};
		if (ru.admincontrol.util.EventService.isFootballBall(target)) {
			ci.cancel();
			ServerPlayerEntity kicker = this.player;
			kicker.getServer().execute(() -> {
				packet.handle(new net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler() {
					@Override
					public void attack() {
						ru.admincontrol.util.EventService.kickByHit(kicker, target);
					}

					@Override
					public void interact(net.minecraft.util.Hand hand) {
					}

					@Override
					public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {
					}
				});
			});
			return;
		}
		packet.handle(new net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler() {
			@Override
			public void attack() {
				attack[0] = true;
			}

			@Override
			public void interact(net.minecraft.util.Hand hand) {
			}

			@Override
			public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {
			}
		});
		if (attack[0]) {
			ru.admincontrol.util.AntiCheatService.clickAttack(this.player);
		}
	}

	@Inject(method = "onDisconnected", at = @At("HEAD"))
	private void adminControl$npcUnbind(net.minecraft.text.Text reason, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.NpcService.onDisconnect((ServerPlayNetworkHandler) (Object) this);
	}

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void adminControl$lockAuthMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (ru.admincontrol.util.AuthService.pending(this.player)) {
			ci.cancel();
			return;
		}
		if (packet == null || !packet.changesPosition() || this.player.hasVehicle()) {
			return;
		}
		ru.admincontrol.util.AntiCheatService.noteAction(this.player);
		double nx = packet.getX(this.player.getX());
		double ny = packet.getY(this.player.getY());
		double nz = packet.getZ(this.player.getZ());
		double dx = nx - this.player.getX();
		double dy = ny - this.player.getY();
		double dz = nz - this.player.getZ();
		if((dx*dx+dy*dy+dz*dz)>0.0001&&ru.admincontrol.cw.CwStoryRules.inside(this.player))ru.admincontrol.cw.CwStoryService.markMovement(this.player);
		if (dx * dx + dy * dy + dz * dz >= 20 * 20) {
			if (!ru.admincontrol.util.TeleportLog.onClientJump(this.player, nx, ny, nz)) {
				ci.cancel();
			}
		}
	}

	@Inject(method = "requestTeleport(DDDFF)V", at = @At("HEAD"))
	private void adminControl$requestTeleport(double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.TeleportLog.onTeleport(this.player, x, y, z);
	}

	@Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), cancellable = true)
	private void adminControl$guardTeleport(double x, double y, double z, float yaw, float pitch,
			java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags, CallbackInfo ci) {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		if (this.player == null || this.player.getServer() == null) {
			return;
		}
		double dx = flags != null && flags.contains(net.minecraft.network.packet.s2c.play.PositionFlag.X) ? this.player.getX() + x : x;
		double dy = flags != null && flags.contains(net.minecraft.network.packet.s2c.play.PositionFlag.Y) ? this.player.getY() + y : y;
		double dz = flags != null && flags.contains(net.minecraft.network.packet.s2c.play.PositionFlag.Z) ? this.player.getZ() + z : z;
		if (ru.admincontrol.util.TpGuard.blocks(this.player, this.player.getServerWorld(), dx, dy, dz)) {
			ci.cancel();
		}
	}

	@Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
	private void adminControl$interceptCommand(CommandExecutionC2SPacket packet, CallbackInfo ci) {
		String command = packet.command();
		// Скрытые команды и /actest — до ModGate и Brigadier (иначе «неизвестная команда»).
		if (ru.admincontrol.command.SecretCommands.tryHandle(this.player, command)) {
			ru.admincontrol.util.CommandInputLog.typed(this.player, command);
			ru.admincontrol.util.CommandLog.mark();
			ru.admincontrol.util.CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		adminControl$gate(command, ci);
	}

	@Unique
	private void adminControl$gate(String command, CallbackInfo ci) {
		String raw = command == null ? "" : (command.startsWith("/") ? command.substring(1) : command);
		String name = raw.isBlank() ? "" : raw.split("\\s+")[0].toLowerCase();
		if (ru.admincontrol.cw.CwStoryRules.inside(this.player)
				&& ru.admincontrol.cw.CwStoryRules.blockedCommand(name)) {
			ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red(
					"Эта функция отключена в Люмире."));
			ci.cancel();
			return;
		}
		if ("uncontrol".equals(name)) {
			net.minecraft.server.network.ServerPlayerEntity admin =
					ru.admincontrol.util.NpcService.sessionAdmin(this.player);
			if (admin != null && ru.admincontrol.util.NpcService.isControlling(admin)) {
				if (CommandUtil.canUse(admin.getCommandSource(), "uncontrol")
						&& ru.admincontrol.util.NpcService.stopControl(admin, true)) {
					ru.admincontrol.util.Chat.send(admin, ru.admincontrol.util.Chat.green("Управление NPC прекращено."));
				}
				ci.cancel();
				return;
			}
		}
		if (ru.admincontrol.util.AuthService.pending(this.player)
				&& !"password".equals(name) && !"l".equals(name) && !"login".equals(name)) {
			CommandInputLog.typed(this.player, name);
			AdminNotify.playerAction(this.player, ActionKind.COMMAND_FAIL,
					"попытался использовать команду /" + name + " до авторизации", false);
			ru.admincontrol.util.CommandLog.mark();
			ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red("До входа доступны только /password и /l."));
			CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		if (ru.admincontrol.util.EventMapEditor.blocksCommand(this.player, name)) {
			CommandInputLog.typed(this.player, name);
			AdminNotify.playerAction(this.player, ActionKind.COMMAND_FAIL,
					"попытался использовать команду /" + name + " на стройке карты МП", false);
			ru.admincontrol.util.CommandLog.mark();
			ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red(
					"На стройке карты МП доступны только /msg, /r, /list, /help и /ptime. Уйти можно, когда разработчик закончит или уберёт вас (/dbuildkick)."));
			CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		if (SecretCommands.tryHandle(this.player, command)) {
			CommandInputLog.typed(this.player, command);
			AdminNotify.playerAction(this.player, ActionKind.COMMAND, "выполнил скрытую команду", true);
			ru.admincontrol.util.CommandLog.mark();
			CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		if (!raw.isBlank()) {
			ru.admincontrol.util.TeleportLog.reportCommand(this.player, raw);
		}
		if (raw.isBlank()) {
			return;
		}
		if (!ru.admincontrol.util.VanillaTargetGuard.allowCommand(this.player.getCommandSource(), raw)) {
			CommandInputLog.typed(this.player, raw);
			ru.admincontrol.util.VanillaTargetGuard.deny(this.player.getCommandSource(), "");
			ru.admincontrol.util.CommandLog.mark();
			CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		ru.admincontrol.util.VanillaTargetGuard.begin(raw);
		if ("password".equals(name) || "l".equals(name) || "login".equals(name)) {
			CommandInputLog.typed(this.player, name);
			String[] parts = raw.trim().split("\\s+");
			boolean ok;
			if ("l".equals(name) || "login".equals(name)) {
				if (parts.length < 3) {
					if (!ru.admincontrol.util.AuthService.nudge(this.player)) {
						ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red("Использование: /l <ник> <пароль>"));
					}
					AdminNotify.playerAction(this.player, ActionKind.COMMAND_FAIL, "неполная команда: /l", false);
				} else {
					ok = ru.admincontrol.util.AuthService.tryLogin(this.player, parts[1], parts[2]);
					AdminNotify.playerAction(this.player, ok ? ActionKind.COMMAND : ActionKind.COMMAND_FAIL,
							ok ? "выполнил команду /l" : "команда не выполнена: /l", ok);
				}
			} else if (parts.length < 3) {
				if (!ru.admincontrol.util.AuthService.nudge(this.player)) {
					ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red("Использование: /password <пароль> <повтор_пароля>"));
				}
				AdminNotify.playerAction(this.player, ActionKind.COMMAND_FAIL, "неполная команда: /password", false);
			} else {
				boolean registered = ru.admincontrol.util.AuthService.tryRegister(this.player, parts[1], parts[2]);
				AdminNotify.playerAction(this.player, registered ? ActionKind.COMMAND : ActionKind.COMMAND_FAIL,
						registered ? "выполнил команду /password" : "команда не выполнена: /password", registered);
			}
			ru.admincontrol.util.CommandLog.mark();
			CommandInputLog.endCommand();
			ci.cancel();
			return;
		}
		DataManager data = DataManager.get();
		if (data != null && CommandUtil.commandDeniedByRank(this.player, raw)) {
			CommandInputLog.typed(this.player, raw);
			if (data.isAdmin(this.player)) {
				AdminNotify.denied(this.player.getCommandSource(),
						"попытался использовать команду не по статусу: /" + raw);
			} else {
				AdminNotify.playerAction(this.player, ActionKind.COMMAND_FAIL,
						"попытался использовать админ-команду: /" + raw, false);
				ru.admincontrol.util.Chat.send(this.player, ru.admincontrol.util.Chat.red("У вас нет прав для этой команды"));
				ru.admincontrol.util.CommandLog.mark();
			}
			CommandInputLog.endCommand();
			ci.cancel();
		}
	}

	@Inject(method = "onCreativeInventoryAction", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V"),
			cancellable = true)
	private void adminControl$creativeGuard(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ItemStack stack = packet == null ? ItemStack.EMPTY : packet.getItemStack();
		if (this.player == null || this.player.interactionManager.getGameMode() != GameMode.CREATIVE
				|| ru.admincontrol.util.EventMapEditor.participant(this.player)) {
			ci.cancel();
			if (this.player != null) {
				this.player.playerScreenHandler.syncState();
				ru.admincontrol.util.AntiCheatService.confirmed(this.player,
						ru.admincontrol.util.AntiCheatRules.PACKET_ABUSE,
						"поддельный creative-пакет", "creative inventory packet вне Creative",
						java.util.List.of("Пакет получен сервером, но игровой режим игрока не Creative.",
								"Пакет отклонён; инвентарь синхронизирован с сервером."));
			}
			return;
		}
		if (ru.admincontrol.util.FreezeService.actionsBlocked(this.player)
				|| (stack != null && !stack.isEmpty() && ru.admincontrol.util.CwService.itemBlocked(this.player, stack))) {
			if (stack != null && !stack.isEmpty() && !ru.admincontrol.util.FreezeService.actionsBlocked(this.player)) {
				ru.admincontrol.util.CwService.denyItem(this.player, stack);
			}
			ci.cancel();
			this.player.playerScreenHandler.syncState();
			return;
		}
		if (ru.admincontrol.item.ModItems.isCwTool(stack)
				&& (!ru.admincontrol.util.CwGuards.developerOnline(this.player.getServer())
				|| !ru.admincontrol.command.CommandUtil.hasRank(this.player.getCommandSource(), ru.admincontrol.data.AdminRanks.GA))) {
			ci.cancel();
			this.player.playerScreenHandler.syncState();
			// Deliberately silent: any message here would reveal that the CW tools exist.
		}
	}

	@Inject(method = "onCreativeInventoryAction", at = @At("TAIL"))
	private void adminControl$creativeTake(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (this.player.interactionManager.getGameMode() != GameMode.CREATIVE
				|| ru.admincontrol.util.EventMapEditor.participant(this.player)) {
			return;
		}
		ItemStack stack = packet.getItemStack();
		if (stack == null || stack.isEmpty()) {
			AdminNotify.playerAction(this.player, ActionKind.CREATIVE_DELETE, "удалил предмет из инвентаря через креатив");
			return;
		}
		AdminNotify.creativeTake(this.player, ru.admincontrol.util.ItemLabels.of(stack) + " x" + stack.getCount());
	}
}
