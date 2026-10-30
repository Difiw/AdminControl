package ru.admincontrol.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.TabListColors;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
	@Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
	private void adminControl$cwKeepBuilderInventory(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self instanceof ServerPlayerEntity player && ru.admincontrol.cw.CwBuilderWorld.inside(player)) ci.cancel();
	}
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void adminControl$trainingHurt(net.minecraft.entity.damage.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient) {
			return;
		}
		if (self instanceof ServerPlayerEntity player && ru.admincontrol.util.TrainingService.active(player)) {
			player.setInvulnerable(false);
			player.getAbilities().invulnerable = false;
			if (ru.admincontrol.data.DataManager.get() != null) {
				ru.admincontrol.data.DataManager.get().getOrCreate(player).agmEnabled = false;
			}
		}
		if (self instanceof ServerPlayerEntity victim && source != null
				&& source.getAttacker() instanceof ServerPlayerEntity attacker
				&& ru.admincontrol.util.EventService.ignorePvp(attacker, victim)) {
			cir.setReturnValue(false);
			return;
		}
		if (self instanceof ServerPlayerEntity victim && source != null && ru.admincontrol.util.EventService.calm(victim)
				&& (source.getAttacker() instanceof PlayerEntity
						|| source.getAttacker() instanceof net.minecraft.entity.mob.MobEntity
						|| source.getSource() instanceof net.minecraft.entity.mob.MobEntity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
	private void adminControl$displayName(CallbackInfoReturnable<Text> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient) {
			return;
		}
		if (self instanceof ServerPlayerEntity player) {
			Text name = TabListColors.displayName(player);
			if (name != null) {
				cir.setReturnValue(name);
			}
		}
	}

	@Inject(method = "dropInventory", at = @At("TAIL"))
	private void adminControl$dropBackpack(CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!(self instanceof ServerPlayerEntity player) || player.getWorld() == null || player.getWorld().isClient) {
			return;
		}
		if (ru.admincontrol.data.DataManager.get() == null || ru.admincontrol.util.NpcService.isNpc(player)) {
			return;
		}
		if (player.currentScreenHandler != player.playerScreenHandler) {
			player.closeHandledScreen();
		}
		var rec = ru.admincontrol.data.DataManager.get().getOrCreate(player);
		if (rec.backpackLevel <= 0 || rec.backpack == null || rec.backpack.isEmpty()) {
			return;
		}
		java.util.List<String> items = new java.util.ArrayList<>(rec.backpack);
		rec.backpack.clear();
		for (String raw : items) {
			ItemStack stack = ru.admincontrol.util.StackIo.read(raw);
			if (!stack.isEmpty()) {
				player.dropStack(stack);
			}
		}
		ru.admincontrol.data.DataManager.get().saveAll();
	}

	@Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void adminControl$drop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient || stack == null || stack.isEmpty()) {
			return;
		}
		if (self instanceof ServerPlayerEntity player && (AuthService.pending(player)
				|| ru.admincontrol.util.ActivityLock.blocked(player)
				|| (player.isAlive() && ru.admincontrol.util.FreezeService.actionsBlocked(player)))) {
			player.getInventory().insertStack(stack.copy());
			cir.setReturnValue(null);
			return;
		}
		if (self instanceof ServerPlayerEntity player) {
			AdminNotify.playerAction(player, ActionKind.DROP, "выбросил предмет " + ru.admincontrol.util.ItemLabels.of(stack) + " x" + stack.getCount());
		}
	}

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void adminControl$eat(net.minecraft.world.World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient || stack == null || stack.isEmpty()) {
			return;
		}
		if (self instanceof ServerPlayerEntity player && !AuthService.pending(player)) {
			AdminNotify.playerAction(player, ActionKind.CONSUME, "съел / выпил " + ru.admincontrol.util.ItemLabels.of(stack));
		}
	}

	@Inject(method = "applyEnchantmentCosts", at = @At("HEAD"))
	private void adminControl$enchant(ItemStack enchantedItem, int experienceLevels, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient || experienceLevels <= 0) {
			return;
		}
		if (self instanceof ServerPlayerEntity player && !AuthService.pending(player)) {
			String item = enchantedItem == null || enchantedItem.isEmpty()
					? "предмет"
					: ru.admincontrol.util.ItemLabels.of(enchantedItem);
			AdminNotify.playerAction(player, ActionKind.ENCHANT, "зачаровал " + item + ", потратил " + experienceLevels + " ур. опыта");
			ru.admincontrol.util.ItemSecurityService.onEnchant(player, enchantedItem, experienceLevels);
		}
	}

	@Inject(method = "addExperience", at = @At("HEAD"))
	private void adminControl$xp(int experience, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient || experience == 0) {
			return;
		}
		if (self instanceof ServerPlayerEntity player && !AuthService.pending(player)) {
			if (experience > 0) {
				AdminNotify.playerAction(player, ActionKind.XP, "получил опыт +" + experience);
			} else {
				AdminNotify.playerAction(player, ActionKind.XP, "потратил опыт " + experience);
			}
		}
	}
}
