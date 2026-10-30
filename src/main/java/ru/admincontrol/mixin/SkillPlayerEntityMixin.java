package ru.admincontrol.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.skill.SkillTree.Stat;

/** Скорость добычи (сервер и свой клиент — иначе блок «откатывается») и расход голода (только сервер). */
@Mixin(PlayerEntity.class)
public class SkillPlayerEntityMixin {
	@Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
	private void adminControl$quickPick(BlockState state, CallbackInfoReturnable<Float> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		double pct;
		if (self.getWorld().isClient) {
			pct = SkillEffects.clientMine(self);
		} else {
			pct = ru.admincontrol.util.ModGate.on() ? SkillEffects.server(self, Stat.MINING) : 0;
		}
		if (pct > 0) {
			cir.setReturnValue(cir.getReturnValueF() * (float) SkillTree.more(pct));
		}
	}

	@ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float adminControl$satiety(float exhaustion) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (exhaustion <= 0 || self.getWorld().isClient || !ru.admincontrol.util.ModGate.on()) {
			return exhaustion;
		}
		double pct = SkillEffects.server(self, Stat.HUNGER);
		return pct > 0 ? exhaustion * (float) SkillTree.less(pct) : exhaustion;
	}
}
