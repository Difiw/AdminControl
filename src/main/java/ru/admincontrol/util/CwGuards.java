package ru.admincontrol.util;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Серверные запреты /cw item, /cw mod и заморозки 3 уровня для взаимодействий с миром. */
public final class CwGuards {
	private CwGuards() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0 || developerOnline(server)) return;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (NpcService.isNpc(player)) continue;
				boolean changed = false;
				for (int i = 0; i < player.getInventory().size(); i++) {
					if (ru.admincontrol.item.ModItems.isCwTool(player.getInventory().getStack(i))) {
						player.getInventory().setStack(i, ItemStack.EMPTY);
						changed = true;
					}
				}
				// Silent on purpose: a message would reveal that CW tools exist.
			}
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!ModGate.on()) {
				return TypedActionResult.pass(stack);
			}
			return heldDenied(player, world, hand) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!ModGate.on()) {
				return ActionResult.PASS;
			}
			if (heldDenied(player, world, hand)) {
				return ActionResult.FAIL;
			}
			if (player instanceof ServerPlayerEntity sp && !world.isClient
					&& CwService.blockBlocked(sp, world.getBlockState(hit.getBlockPos()).getBlock())) {
				CwService.notice(sp, "Блоки этого мода вам запрещены.");
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!ModGate.on()) {
				return ActionResult.PASS;
			}
			if (heldDenied(player, world, hand)) {
				return ActionResult.FAIL;
			}
			if (player instanceof ServerPlayerEntity sp && !world.isClient
					&& CwService.blockBlocked(sp, world.getBlockState(pos).getBlock())) {
				CwService.notice(sp, "Блоки этого мода вам запрещены.");
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!ModGate.on()) {
				return ActionResult.PASS;
			}
			if (heldDenied(player, world, hand)) {
				return ActionResult.FAIL;
			}
			if (player instanceof ServerPlayerEntity sp && !world.isClient && CwService.entityBlocked(sp, entity)) {
				CwService.notice(sp, "Существа этого мода вам запрещены.");
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!ModGate.on()) {
				return ActionResult.PASS;
			}
			if (heldDenied(player, world, hand)) {
				return ActionResult.FAIL;
			}
			if (player instanceof ServerPlayerEntity sp && !world.isClient && CwService.entityBlocked(sp, entity)) {
				CwService.notice(sp, "Существа этого мода вам запрещены.");
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!ModGate.on()) {
				return true;
			}
			if (!(player instanceof ServerPlayerEntity sp) || world.isClient) {
				return true;
			}
			if (FreezeService.actionsBlocked(sp)) {
				return false;
			}
			ItemStack held = sp.getMainHandStack();
			if (!held.isEmpty() && CwService.itemBlocked(sp, held)) {
				CwService.denyItem(sp, held);
				return false;
			}
			if (CwService.blockBlocked(sp, state.getBlock())) {
				CwService.notice(sp, "Блоки этого мода вам запрещены.");
				return false;
			}
			return true;
		});
	}

	private static boolean heldDenied(PlayerEntity player, World world, Hand hand) {
		if (world.isClient || !(player instanceof ServerPlayerEntity sp) || NpcService.isNpc(sp)) {
			return false;
		}
		if (FreezeService.actionsBlocked(sp)) {
			CwService.notice(sp, "Вы заморожены (уровень 3): доступен только чат.");
			return true;
		}
		ItemStack stack = sp.getStackInHand(hand);
		if (ru.admincontrol.item.ModItems.isCwTool(stack)) {
			if (!developerOnline(sp.getServer())) {
				// silent
				return true;
			}
			if (!ru.admincontrol.command.CommandUtil.hasRank(sp.getCommandSource(), ru.admincontrol.data.AdminRanks.GA)) {
				// silent
				return true;
			}
		}
		if (!stack.isEmpty() && CwService.itemBlocked(sp, stack)) {
			CwService.denyItem(sp, stack);
			return true;
		}
		return false;
	}

	public static boolean developerOnline(net.minecraft.server.MinecraftServer server) {
		if (server == null || ru.admincontrol.data.DataManager.get() == null) return false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!NpcService.isNpc(player) && ru.admincontrol.data.DataManager.get().getAdminLevel(player) >= ru.admincontrol.data.AdminRanks.DEV) return true;
		}
		return false;
	}
}
