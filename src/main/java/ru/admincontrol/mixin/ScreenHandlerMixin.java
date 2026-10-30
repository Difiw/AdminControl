package ru.admincontrol.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.NavAccess;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin implements NavAccess {
	@Unique
	private boolean adminControl$hasBack;

	@Override
	public void adminControl$setHasBack(boolean value) {
		this.adminControl$hasBack = value;
	}

	@Override
	public boolean adminControl$hasBack() {
		return this.adminControl$hasBack;
	}
	/**
	 * Станции ремесла работают и на стройке: верстаки, наковальни и т. п. ничего не хранят (содержимое возвращается
	 * игроку при закрытии), поэтому предметы не покидают строительный профиль. Сундуки, печи и воронки остаются закрыты.
	 */
	private static boolean adminControl$craftingStation(ScreenHandler handler) {
		return handler instanceof ru.admincontrol.lumira.screen.RuneWorkbenchHandler
				|| handler instanceof ru.admincontrol.lumira.screen.RuneAnvilHandler
				|| handler instanceof net.minecraft.screen.CraftingScreenHandler
				|| handler instanceof net.minecraft.screen.AnvilScreenHandler
				|| handler instanceof net.minecraft.screen.GrindstoneScreenHandler
				|| handler instanceof net.minecraft.screen.SmithingScreenHandler
				|| handler instanceof net.minecraft.screen.StonecutterScreenHandler
				|| handler instanceof net.minecraft.screen.LoomScreenHandler
				|| handler instanceof net.minecraft.screen.CartographyTableScreenHandler
				|| handler instanceof net.minecraft.screen.EnchantmentScreenHandler;
	}

	@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
	private void adminControl$inventory(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || player.getWorld() == null || player.getWorld().isClient) {
			return;
		}
		if (ru.admincontrol.util.ActivityLock.blocked(serverPlayer)) {
			ci.cancel();
			serverPlayer.closeHandledScreen();
			return;
		}
		ScreenHandler handler = (ScreenHandler) (Object) this;
		if (ru.admincontrol.util.FreezeService.actionsBlocked(serverPlayer)) {
			ci.cancel();
			handler.syncState();
			return;
		}
		if (actionType != SlotActionType.THROW && adminControl$cwBlocked(serverPlayer, handler, slotIndex, button, actionType)) {
			ci.cancel();
			handler.syncState();
			return;
		}
		if (AuthService.pending(serverPlayer) || actionType == SlotActionType.QUICK_CRAFT) {
			return;
		}
		String item = "пусто";
		if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
			Slot slot = handler.getSlot(slotIndex);
			ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getStack();
			if (stack != null && !stack.isEmpty()) {
				item = ru.admincontrol.util.ItemLabels.of(stack) + " x" + stack.getCount();
			}
		}
		ItemStack cursor = handler.getCursorStack();
		if ((item.equals("пусто")) && cursor != null && !cursor.isEmpty()) {
			item = ru.admincontrol.util.ItemLabels.of(cursor) + " x" + cursor.getCount();
		}
		if (item.equals("пусто") && actionType != SlotActionType.THROW) {
			return;
		}
		AdminNotify.playerAction(serverPlayer, ActionKind.INVENTORY,
				actionType.name().toLowerCase() + " слот " + slotIndex + " (" + item + ")");
	}

	@Unique
	private static boolean adminControl$cwBlocked(ServerPlayerEntity player, ScreenHandler handler, int slotIndex,
			int button, SlotActionType actionType) {
		String pkg = handler.getClass().getName();
		boolean menu = pkg.startsWith("ru.admincontrol.")
				&& !(handler instanceof ru.admincontrol.screen.ExtraChestScreenHandler)
				&& !(handler instanceof ru.admincontrol.screen.AutoFurnaceScreenHandler);
		if (menu) {
			return false;
		}
		ItemStack hit = ItemStack.EMPTY;
		if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
			hit = handler.getSlot(slotIndex).getStack();
		}
		ItemStack swap = ItemStack.EMPTY;
		if (actionType == SlotActionType.SWAP) {
			swap = button == 40 ? player.getOffHandStack()
					: button >= 0 && button < 9 ? player.getInventory().getStack(button) : ItemStack.EMPTY;
		}
		for (ItemStack stack : new ItemStack[] {hit, handler.getCursorStack(), swap}) {
			if (stack != null && !stack.isEmpty() && ru.admincontrol.util.CwService.itemBlocked(player, stack)) {
				ru.admincontrol.util.CwService.denyItem(player, stack);
				return true;
			}
		}
		return false;
	}
}
