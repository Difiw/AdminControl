package ru.admincontrol.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.HomeProtection;
import ru.admincontrol.util.ModGate;

public final class PlayerActionTracker {
	private PlayerActionTracker() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ModGate.on() && !ru.admincontrol.util.NpcService.isNpc(handler.player)) {
				AdminNotify.playerAction(handler.player, ActionKind.JOIN, "вошёл в мир");
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (ModGate.on()) {
				AdminNotify.playerAction(handler.player, ActionKind.JOIN, "вышел из мира");
			}
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!ModGate.on()) {
				return true;
			}
			if (AuthService.pending(sender)) {
				String text = message.getContent().getString();
				AuthService.tryChatAuth(sender, text);
				return false;
			}
			String text = message.getContent().getString();
			if (text == null || text.isBlank()) {
				return false;
			}
			if (text.startsWith("/")) {
				ru.admincontrol.util.CommandInputLog.typed(sender, text);
				ru.admincontrol.util.CommandInputLog.endCommand();
				AdminNotify.playerAction(sender, ActionKind.CHAT, "написал в чат: /" + ru.admincontrol.util.CommandInputLog.display(text));
				ru.admincontrol.util.AntiCheatService.noteAction(sender);
			} else {
				AdminNotify.playerAction(sender, ActionKind.CHAT, "написал в чат: " + text);
				ru.admincontrol.util.PlayFeatures.onChat(sender);
				ru.admincontrol.util.AntiCheatService.noteAction(sender);
			}
			Chat.broadcast(sender.getServer(), Chat.chatMessage(sender, text));
			return false;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !ModGate.on() || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			if (AuthService.pending(serverPlayer)) {
				return ActionResult.FAIL;
			}
			ItemStack used = player.getStackInHand(hand);
			if (ru.admincontrol.item.ItemEncryption.useBlocked(serverPlayer, used)) {
				ru.admincontrol.item.ItemEncryption.denyUse(serverPlayer, used);
				return ActionResult.FAIL;
			}
			if (ru.admincontrol.util.EventMapEditor.blocksUse(serverPlayer, world.getBlockState(hit.getBlockPos()))) {
				serverPlayer.sendMessage(Chat.red("На стройке карты МП эндер-сундук недоступен."), true);
				return ActionResult.FAIL;
			}
			ru.admincontrol.util.AntiCheatService.noteAction(serverPlayer);
			if (ru.admincontrol.util.BoxEventService.inCube(hit.getBlockPos())) {
				ru.admincontrol.util.BoxEventService.tryClaimUse(serverPlayer, hit.getBlockPos());
			} else {
				ActionResult warUse = ru.admincontrol.util.ClanWarService.useBlock(serverPlayer, hit.getBlockPos());
				if (warUse != null) {
					return warUse;
				}
				net.minecraft.util.math.BlockPos hitPos = hit.getBlockPos();
				net.minecraft.util.math.BlockPos placePos = hitPos.offset(hit.getSide());
				ItemStack held = player.getStackInHand(hand);
				boolean placing = held.getItem() instanceof BlockItem;
				if (HomeProtection.blockedUse(serverPlayer, hitPos)
						|| (placing && HomeProtection.blocked(serverPlayer, placePos))) {
					serverPlayer.sendMessage(Chat.red("Этот участок защищён приватом."), true);
					AdminNotify.playerAction(serverPlayer, ActionKind.PLACE, "не смог использовать блок "
							+ hitPos.toShortString() + " (приват)", false);
					return ActionResult.FAIL;
				}
			}
			Block block = world.getBlockState(hit.getBlockPos()).getBlock();
			String blockId = Registries.BLOCK.getId(block).toString();
			if (isWorkstation(blockId)) {
				AdminNotify.playerAction(serverPlayer, ActionKind.WORKSTATION, "использовал стол " + ru.admincontrol.util.ItemLabels.of(block));
			}
			if (blockId.contains("portal")) {
				AdminNotify.playerAction(serverPlayer, ActionKind.PORTAL, "взаимодействовал с порталом " + blockId
						+ " " + hit.getBlockPos().toShortString());
			}
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof BlockItem) {
				return ActionResult.PASS;
			}
			if (Registries.ITEM.getId(stack.getItem()).toString().contains("flint_and_steel")
					&& blockId.contains("obsidian")) {
				AdminNotify.playerAction(serverPlayer, ActionKind.PORTAL, "зажёг портал " + hit.getBlockPos().toShortString());
			}
			String item = stack.isEmpty() ? "рукой" : ru.admincontrol.util.ItemLabels.of(stack);
			AdminNotify.playerAction(serverPlayer, ActionKind.HIT_BLOCK, "ударил/использовал " + item + " по блоку "
					+ hit.getBlockPos().getX() + ", " + hit.getBlockPos().getY() + ", " + hit.getBlockPos().getZ());
			return ActionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClient || !ModGate.on() || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			if (AuthService.pending(serverPlayer)) {
				return ActionResult.FAIL;
			}
			if (!ru.admincontrol.util.BoxEventService.inCube(pos) && HomeProtection.blocked(serverPlayer, pos)) {
				serverPlayer.sendMessage(Chat.red("Этот участок защищён приватом."), true);
				return ActionResult.FAIL;
			}
			AdminNotify.playerAction(serverPlayer, ActionKind.HIT_BLOCK, "ударил по блоку " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
					+ " (" + ru.admincontrol.util.ItemLabels.of(world.getBlockState(pos).getBlock()) + ")");
			return ActionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && ModGate.on() && player instanceof ServerPlayerEntity serverPlayer && !AuthService.pending(serverPlayer)) {
				ItemStack stack = player.getStackInHand(hand);
				if (ru.admincontrol.item.ItemEncryption.useBlocked(serverPlayer, stack)) {
					ru.admincontrol.item.ItemEncryption.denyUse(serverPlayer, stack);
					return TypedActionResult.fail(stack);
				}
				if (!stack.isEmpty() && !(stack.getItem() instanceof BlockItem)) {
					AdminNotify.playerAction(serverPlayer, ActionKind.USE_ITEM, "использовал предмет " + ru.admincontrol.util.ItemLabels.of(stack));
				}
			}
			return TypedActionResult.pass(player.getStackInHand(hand));
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient && ModGate.on() && player instanceof ServerPlayerEntity serverPlayer) {
				ItemStack stack = player.getStackInHand(hand);
				if (ru.admincontrol.item.ItemEncryption.useBlocked(serverPlayer, stack)) {
					ru.admincontrol.item.ItemEncryption.denyUse(serverPlayer, stack);
					return ActionResult.FAIL;
				}
			}
			return ActionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient || !ModGate.on() || !(player instanceof ServerPlayerEntity attacker) || AuthService.pending(attacker)) {
				return ActionResult.PASS;
			}
			if (entity instanceof ServerPlayerEntity victim) {
				AdminNotify.playerHitPlayer(attacker, victim);
				ru.admincontrol.util.EventService.noteCombat(attacker, victim);
			} else if (entity instanceof MobEntity) {
				AdminNotify.playerAction(attacker, ActionKind.ATTACK_MOB, "атаковал моба " + entity.getName().getString());
			} else {
				AdminNotify.playerAction(attacker, ActionKind.ATTACK_MOB, "атаковал сущность " + entity.getName().getString());
			}
			return ActionResult.PASS;
		});

		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient || !ModGate.on() || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return true;
			}
			if (AuthService.pending(serverPlayer)) {
				return false;
			}
			if (!ru.admincontrol.util.PrivateService.beforeBreak(serverPlayer, world, pos)) {
				return false;
			}
			if (!ru.admincontrol.util.BoxEventService.inCube(pos) && HomeProtection.blocked(serverPlayer, pos)) {
				serverPlayer.sendMessage(Chat.red("Этот участок защищён приватом."), true);
				AdminNotify.playerAction(serverPlayer, ActionKind.BREAK, "не смог сломать блок "
						+ pos.toShortString() + " (приват)", false);
				return false;
			}
			if (!ru.admincontrol.util.ClanWarService.allowBreak(serverPlayer, pos)) {
				return false;
			}
			if (!ru.admincontrol.util.BoxEventService.allowBreak(serverPlayer, pos)) {
				return false;
			}
			if (!ru.admincontrol.util.EventService.allowBreak(serverPlayer, pos)) {
				return false;
			}
			if (ru.admincontrol.util.TrainingService.blockedBreak(serverPlayer, pos)) {
				serverPlayer.sendMessage(Chat.red("Нельзя ломать стенки тренировки."), true);
				return false;
			}
			String id = Registries.BLOCK.getId(state.getBlock()).toString();
			if (id.contains("portal")) {
				AdminNotify.playerAction(serverPlayer, ActionKind.PORTAL, "сломал портал " + pos.toShortString());
			}
			return true;
		});

		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient || !ModGate.on() || !(player instanceof ServerPlayerEntity serverPlayer) || AuthService.pending(serverPlayer)
					|| DataManager.get() == null) {
				return;
			}
			PlayerRecord record = DataManager.get().getOrCreate(serverPlayer);
			record.blocksBroken++;
			ru.admincontrol.util.PlayFeatures.onBreak(serverPlayer);
			ru.admincontrol.util.SkillTreeService.onBreak(serverPlayer, world, pos, state);
			ru.admincontrol.util.BugAbuseService.onBreak(serverPlayer, world, pos, state);
			ru.admincontrol.util.AntiCheatService.noteAction(serverPlayer);
			AdminNotify.playerAction(serverPlayer, ActionKind.BREAK, "сломал блок "
					+ ru.admincontrol.util.ItemLabels.of(state.getBlock()) + " " + pos.toShortString(), true);
			ru.admincontrol.util.EventService.onBlockBroken(serverPlayer, pos);
			ru.admincontrol.util.ClanWarService.onBroken(serverPlayer, pos);
			ru.admincontrol.util.AntiCheatService.onOreBreak(serverPlayer, world, pos, state);
		});
	}

	private static boolean isWorkstation(String id) {
		return id.contains("crafting_table") || id.contains("furnace") || id.contains("anvil")
				|| id.contains("enchant") || id.contains("smithing") || id.contains("grindstone")
				|| id.contains("loom") || id.contains("stonecutter") || id.contains("cartography")
				|| id.contains("blast_furnace") || id.contains("smoker") || id.contains("brewing")
				|| id.contains("lectern") || id.contains("beacon") || id.contains("chest")
				|| id.contains("barrel") || id.contains("hopper") || id.contains("shulker")
				|| id.contains("dispenser") || id.contains("dropper") || id.contains("ender")
				|| id.contains("trapped_chest") || id.contains("button") || id.contains("lever")
				|| id.contains("door") || id.contains("gate") || id.contains("trapdoor")
				|| id.contains("repeater") || id.contains("comparator") || id.contains("note_block")
				|| id.contains("jukebox") || id.contains("bed") || id.contains("respawn_anchor")
				|| id.contains("cauldron") || id.contains("composter") || id.contains("chiseled_bookshelf");
	}
}
