package ru.admincontrol.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {
	@Inject(method = "onTakeOutput", at = @At("HEAD"))
	private void adminControl$anvil(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || stack == null || stack.isEmpty()) {
			return;
		}
		if (AuthService.pending(serverPlayer)) {
			return;
		}
		AdminNotify.playerAction(serverPlayer, ru.admincontrol.data.ActionKind.ENCHANT, "использовал наковальню: "
				+ ru.admincontrol.util.ItemLabels.of(stack) + " x" + stack.getCount());
	}
}
