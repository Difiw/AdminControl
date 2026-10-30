package ru.admincontrol.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree.Stat;

/** «Бережливость»: каждая единица износа с шансом не тратится. */
@Mixin(ItemStack.class)
public class SkillItemStackMixin {
	@ModifyVariable(method = "damage(ILnet/minecraft/util/math/random/Random;Lnet/minecraft/server/network/ServerPlayerEntity;)Z",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int adminControl$durability(int amount, int same, Random random, ServerPlayerEntity player) {
		if (amount <= 0 || player == null || random == null || !ru.admincontrol.util.ModGate.on()) {
			return amount;
		}
		double pct = SkillEffects.server(player, Stat.DURABILITY);
		if (pct <= 0) {
			return amount;
		}
		int left = 0;
		for (int i = 0; i < amount; i++) {
			if (random.nextDouble() * 100 >= pct) {
				left++;
			}
		}
		return left;
	}
}
