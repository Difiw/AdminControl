package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.LuckDrops;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void adminControl$jail(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if ((Object) this instanceof ServerPlayerEntity player && ru.admincontrol.util.ClanWarService.jailed(player)) {
			cir.setReturnValue(false);
			return;
		}
		if (ru.admincontrol.util.EventService.isFootballBall((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float adminControl$power(float amount, DamageSource source) {
		if (amount <= 0 || source == null || !ru.admincontrol.util.ModGate.on()) {
			return amount;
		}
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof ServerPlayerEntity player) || DataManager.get() == null) {
			return amount;
		}
		if ((Object) this instanceof ServerPlayerEntity victim && ru.admincontrol.util.EventService.member(player)
				&& ru.admincontrol.util.EventService.member(victim)) {
			return amount;
		}
		return amount * (float) ru.admincontrol.util.ProteinService.effectivePower(player);
	}

	@Redirect(method = "dropLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getLuck()F"))
	private float adminControl$lootLuck(PlayerEntity player) {
		float base = player.getLuck();
		if (player instanceof ServerPlayerEntity serverPlayer && ru.admincontrol.util.ModGate.on()) {
			return LuckDrops.lootContextLuck(serverPlayer, base);
		}
		return base;
	}

	@Redirect(method = "dropLoot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;JLjava/util/function/Consumer;)V"))
	private void adminControl$boostMobLoot(LootTable table, LootContextParameterSet params, long seed, Consumer<ItemStack> consumer) {
		LivingEntity self = (LivingEntity) (Object) this;
		ServerPlayerEntity killer = null;
		if (!(self instanceof ServerPlayerEntity) && self.getWorld() != null && !self.getWorld().isClient
				&& ru.admincontrol.util.ModGate.on()) {
			Entity fromParams = params.getOptional(net.minecraft.loot.context.LootContextParameters.KILLER_ENTITY);
			PlayerEntity lastHit = params.getOptional(net.minecraft.loot.context.LootContextParameters.LAST_DAMAGE_PLAYER);
			if (fromParams instanceof ServerPlayerEntity player) {
				killer = player;
			} else if (lastHit instanceof ServerPlayerEntity player) {
				killer = player;
			} else if (self.getAttacker() instanceof ServerPlayerEntity player) {
				killer = player;
			}
		}
		ServerPlayerEntity finalKiller = killer;
		table.generateLoot(params, seed, stack -> {
			ItemStack boosted = finalKiller == null ? stack : LuckDrops.boostMobDrop(finalKiller, stack, self.getRandom());
			consumer.accept(boosted);
		});
	}

	@Inject(method = "onDeath", at = @At("TAIL"))
	private void adminControl$killed(DamageSource source, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld() == null || self.getWorld().isClient || source == null) {
			return;
		}
		if (!(source.getAttacker() instanceof ServerPlayerEntity player) || DataManager.get() == null) {
			return;
		}
		ru.admincontrol.util.SkillTreeService.onKill(player, self);
		if (self instanceof ServerPlayerEntity victim) {
			if (victim != player) {
				ru.admincontrol.util.PlayFeatures.onPvpKill(player);
			}
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		String name = self.getName().getString();
		if (self instanceof HostileEntity) {
			record.monsterKills++;
			ru.admincontrol.util.PlayFeatures.onKill(player);
			AdminNotify.playerAction(player, ActionKind.KILL_MONSTER, "убил монстра " + name, true);
			ru.admincontrol.util.EventService.onHostileKilled(player, self);
		} else if (self instanceof PassiveEntity) {
			record.peacefulKills++;
			AdminNotify.playerAction(player, ActionKind.KILL_PEACEFUL, "убил мирного моба " + name, true);
		} else {
			record.peacefulKills++;
			AdminNotify.playerAction(player, ActionKind.KILL_PEACEFUL, "убил существо " + name, true);
		}
	}

	@Inject(method = "eatFood", at = @At("RETURN"))
	private void adminControl$eat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if ((Object) this instanceof ServerPlayerEntity player && world != null && !world.isClient) {
			ru.admincontrol.util.PlayFeatures.onEat(player);
		}
	}
}
