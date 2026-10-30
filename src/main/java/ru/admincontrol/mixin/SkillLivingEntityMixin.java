package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.skill.SkillTree.Stat;

/** Серверные эффекты дерева навыков для живых существ. */
@Mixin(LivingEntity.class)
public class SkillLivingEntityMixin {
	@Inject(method = "computeFallDamage", at = @At("RETURN"), cancellable = true)
	private void adminControl$softFall(float fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on() || !((Object) this instanceof ServerPlayerEntity player)) {
			return;
		}
		double pct = SkillEffects.server(player, Stat.FALL);
		int base = cir.getReturnValueI();
		if (pct > 0 && base > 0) {
			cir.setReturnValue((int) Math.round(base * SkillTree.less(pct)));
		}
	}

	/** «Неуловимость»: шанс полностью избежать удара моба в ближнем бою. */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void adminControl$dodge(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (amount <= 0 || source == null || !ru.admincontrol.util.ModGate.on()
				|| !((Object) this instanceof ServerPlayerEntity victim)) {
			return;
		}
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof MobEntity) || source.getSource() != attacker) {
			return;
		}
		double pct = SkillEffects.server(victim, Stat.DODGE);
		if (pct > 0 && victim.getRandom().nextDouble() * 100 < pct) {
			ServerWorld world = victim.getServerWorld();
			world.spawnParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1, victim.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS,
					0.9f, 1.4f);
			cir.setReturnValue(false);
		}
	}

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float adminControl$skillDamage(float amount, DamageSource source) {
		if (amount <= 0 || source == null || !ru.admincontrol.util.ModGate.on()) {
			return amount;
		}
		double result = amount;
		LivingEntity self = (LivingEntity) (Object) this;
		Entity direct = source.getSource();
		if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != self) {
			if (!(self instanceof PlayerEntity)) {
				result *= SkillTree.more(SkillEffects.server(attacker, Stat.HUNTER));
			}
			if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
				result *= SkillTree.more(SkillEffects.server(attacker, Stat.ARCHER));
			}
			if (direct == attacker && !source.isOf(DamageTypes.THORNS)) {
				double crit = SkillEffects.server(attacker, Stat.CRIT);
				if (crit > 0 && attacker.getRandom().nextDouble() * 100 < crit && self.getWorld() instanceof ServerWorld w) {
					result *= 1.5;
					w.spawnParticles(ParticleTypes.ENCHANTED_HIT, self.getX(), self.getBodyY(0.5), self.getZ(), 14,
							0.3, 0.4, 0.3, 0.2);
					w.playSound(null, self.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS,
							1.0f, 1.2f);
				}
				if (self.getHealth() <= self.getMaxHealth() * 0.35f) {
					result *= SkillTree.more(SkillEffects.server(attacker, Stat.EXECUTE));
				} else if (self.getHealth() >= self.getMaxHealth()) {
					result *= SkillTree.more(SkillEffects.server(attacker, Stat.FIRST_STRIKE));
				}
				double steal = SkillEffects.server(attacker, Stat.LIFESTEAL);
				if (steal > 0 && self.isAlive() && self.timeUntilRegen <= 10 && !self.isInvulnerableTo(source)) {
					attacker.heal((float) (Math.min(result, self.getHealth()) * steal / 100.0));
				}
			}
		}
		if (self instanceof ServerPlayerEntity victim) {
			Entity attacker = source.getAttacker();
			if (source.isIn(DamageTypeTags.IS_FIRE)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.FIRE));
			}
			if (attacker instanceof Monster) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.MOB_GUARD));
			}
			if (source.isOf(DamageTypes.DROWN)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.DROWN));
			}
			if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.BLAST));
			}
			if (source.isIn(DamageTypeTags.IS_FREEZING)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.FREEZE));
			}
			if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC) || source.isOf(DamageTypes.WITHER)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.MAGIC));
			}
			if (source.isOf(DamageTypes.CACTUS) || source.isOf(DamageTypes.SWEET_BERRY_BUSH)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.PRICKLY));
			}
			if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.PROJ_GUARD));
			}
			if (attacker instanceof PlayerEntity && attacker != victim) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.PVP_GUARD));
			}
			if (victim.getHealth() < victim.getMaxHealth() * 0.3f) {
				result *= SkillTree.less(SkillEffects.server(victim, Stat.LAST_STAND));
			}
			double thorns = SkillEffects.server(victim, Stat.THORNS);
			if (thorns > 0 && attacker instanceof LivingEntity hitter && hitter != victim && direct == attacker
					&& !source.isOf(DamageTypes.THORNS) && victim.timeUntilRegen <= 10 && hitter.isAlive()) {
				hitter.damage(victim.getDamageSources().thorns(victim), (float) (result * thorns / 100.0));
			}
		}
		return (float) result;
	}

	/** «Целитель»: любое лечение игрока сильнее. */
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float adminControl$healBoost(float amount) {
		if (amount > 0 && ru.admincontrol.util.ModGate.on() && (Object) this instanceof ServerPlayerEntity player) {
			double pct = SkillEffects.server(player, Stat.HEAL_BOOST);
			if (pct > 0) {
				return (float) (amount * SkillTree.more(pct));
			}
		}
		return amount;
	}

	@Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
	private void adminControl$unseen(Entity entity, CallbackInfoReturnable<Double> cir) {
		if (!ru.admincontrol.util.ModGate.on() || !((Object) this instanceof ServerPlayerEntity player)
				|| entity instanceof PlayerEntity) {
			return;
		}
		double pct = SkillEffects.server(player, Stat.UNSEEN);
		if (pct > 0) {
			cir.setReturnValue(cir.getReturnValueD() * SkillTree.less(pct));
		}
	}

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void adminControl$skillEat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (ru.admincontrol.util.ModGate.on() && (Object) this instanceof ServerPlayerEntity player && world != null
				&& !world.isClient) {
			double feast = SkillEffects.server(player, Stat.FEAST);
			FoodComponent food = stack.getItem().getFoodComponent();
			if (feast > 0 && food != null) {
				int extra = (int) Math.round(food.getHunger() * feast / 100.0);
				if (extra > 0) {
					player.getHungerManager().add(extra, food.getSaturationModifier());
				}
			}
			ru.admincontrol.util.SkillTreeService.onEat(player, stack);
		}
	}
}
