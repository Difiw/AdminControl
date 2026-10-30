package ru.admincontrol.mixin;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;

/** «Ученик»: бонус только к опыту из сфер (команды /xp и прочие начисления не усиливаются). */
@Mixin(ExperienceOrbEntity.class)
public class SkillExperienceOrbMixin {
	@Unique
	private PlayerEntity adminControl$collector;

	@Inject(method = "onPlayerCollision", at = @At("HEAD"))
	private void adminControl$collector(PlayerEntity player, CallbackInfo ci) {
		this.adminControl$collector = player;
	}

	@ModifyArg(method = "onPlayerCollision", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/player/PlayerEntity;addExperience(I)V"))
	private int adminControl$scholar(int amount) {
		PlayerEntity player = this.adminControl$collector;
		this.adminControl$collector = null;
		if (amount <= 0 || !(player instanceof ServerPlayerEntity) || !ru.admincontrol.util.ModGate.on()) {
			return amount;
		}
		double pct = SkillEffects.server(player, SkillTree.Stat.XP);
		if (pct <= 0) {
			return amount;
		}
		double extra = amount * pct / 100.0;
		int whole = (int) Math.floor(extra);
		if (player.getRandom().nextDouble() < extra - whole) {
			whole++;
		}
		return amount + whole;
	}
}
