package ru.admincontrol.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.admincontrol.lumira.LumiraNecklaces;

/** Усиление/ослабление урона ожерельями: один раз на событие, до обработки брони. */
@Mixin(LivingEntity.class)
public abstract class LumiraDamageMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float admin_control$necklaceDamage(float amount, DamageSource source, float sameAmount) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) {
			return amount;
		}
		return LumiraNecklaces.modifyDamage(self, source, amount);
	}
}
