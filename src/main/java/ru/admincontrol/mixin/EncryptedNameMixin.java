package ru.admincontrol.mixin;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.item.ItemEncryption;

import java.util.ArrayList;
import java.util.List;

/** Зашифрованный предмет: вместо названия переключающиеся символы, подсказка без описания. */
@Mixin(ItemStack.class)
public abstract class EncryptedNameMixin {
	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	private void admin_control$name(CallbackInfoReturnable<Text> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (!self.hasCustomName() && ItemEncryption.hidden(self.getItem())) {
			cir.setReturnValue(ItemEncryption.maskedName());
		}
	}

	@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
	private void admin_control$tooltip(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir) {
		ItemStack self = (ItemStack) (Object) this;
		boolean name = ItemEncryption.hidden(self.getItem()), desc = ItemEncryption.descHidden(self.getItem());
		if (!name && !desc) {
			return;
		}
		// Поля независимы: скрытое название заменяется маской, скрытое описание отбрасывает все строки под названием.
		List<Text> original = cir.getReturnValue();
		List<Text> out = new ArrayList<>();
		out.add(name ? ItemEncryption.maskedName() : original.isEmpty() ? Text.empty() : original.get(0));
		if (!desc) {
			for (int i = 1; i < original.size(); i++) {
				out.add(original.get(i));
			}
		}
		cir.setReturnValue(out);
	}
}
