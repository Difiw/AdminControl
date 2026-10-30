package ru.admincontrol.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
	@Unique
	private Item adminControl$pendingPlace;

	@Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
	private void adminControl$placeHead(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		this.adminControl$pendingPlace = stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem
				? stack.getItem() : null;
		if (player == null || world == null || world.isClient || hitResult == null) {
			return;
		}
		if (ru.admincontrol.item.ItemEncryption.useBlocked(player, stack)) {
			ru.admincontrol.item.ItemEncryption.denyUse(player, stack);
			this.adminControl$pendingPlace = null;
			cir.setReturnValue(ActionResult.FAIL);
			return;
		}
		net.minecraft.util.math.BlockPos hitPos = hitResult.getBlockPos();
		net.minecraft.util.math.BlockPos placePos = hitPos.offset(hitResult.getSide());
		boolean placing = this.adminControl$pendingPlace != null;
		if (ru.admincontrol.util.HomeProtection.blockedUse(player, hitPos)
				|| (placing && ru.admincontrol.util.HomeProtection.blocked(player, placePos))) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

	@Inject(method = "interactBlock", at = @At("RETURN"))
	private void adminControl$place(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		Item placed = this.adminControl$pendingPlace;
		this.adminControl$pendingPlace = null;
		if (player == null || world == null || world.isClient || AuthService.pending(player) || DataManager.get() == null) {
			return;
		}
		ActionResult result = cir.getReturnValue();
		if (result == null || !result.isAccepted() || placed == null) {
			return;
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		record.blocksPlaced++;
		ru.admincontrol.util.PlayFeatures.onPlace(player);
		net.minecraft.util.math.BlockPos hitPos = hitResult.getBlockPos();
		net.minecraft.util.math.BlockPos sidePos = hitPos.offset(hitResult.getSide());
		ru.admincontrol.util.SkillTreeService.onPlaced(world,
				world.getBlockState(sidePos).getBlock().asItem() == placed ? sidePos : hitPos);
		ru.admincontrol.util.AntiCheatService.noteAction(player);
		AdminNotify.playerAction(player, ActionKind.PLACE, "разместил блок "
				+ ru.admincontrol.util.ItemLabels.of(placed) + " " + hitResult.getBlockPos().toShortString(), true);
	}
}
