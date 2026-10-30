package ru.admincontrol.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.PrivateService;

@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Unique
	private static int adminControl$privateLevel;

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At("HEAD"), cancellable = true)
	private void adminControl$privateCheck(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		adminControl$privateLevel = 0;
		if (context == null || context.getWorld().isClient
				|| !(context.getPlayer() instanceof ServerPlayerEntity player)) {
			return;
		}
		int level = PrivateService.levelOf(context.getStack());
		if (level <= 0) {
			return;
		}
		String error = PrivateService.canPlace(player, context.getWorld(), context.getBlockPos(), level);
		if (error != null) {
			Chat.send(player, Chat.red(error));
			cir.setReturnValue(ActionResult.FAIL);
			return;
		}
		adminControl$privateLevel = level;
	}

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At("RETURN"))
	private void adminControl$privatePlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		int level = adminControl$privateLevel;
		adminControl$privateLevel = 0;
		if (level <= 0 || context == null || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
			return;
		}
		ActionResult result = cir.getReturnValue();
		if (result != null && result.isAccepted()) {
			PrivateService.placed(player, context.getWorld(), context.getBlockPos(), level);
		}
	}
}
