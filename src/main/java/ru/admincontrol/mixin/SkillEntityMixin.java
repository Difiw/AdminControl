package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.skill.SkillTree.Stat;

/** «Жабры»: больший запас воздуха. Клиент считает так же, чтобы полоска пузырей не вылезала за 10. */
@Mixin(Entity.class)
public class SkillEntityMixin {
	@Inject(method = "getMaxAir", at = @At("RETURN"), cancellable = true)
	private void adminControl$air(CallbackInfoReturnable<Integer> cir) {
		Object self = this;
		if (!(self instanceof PlayerEntity player)) {
			return;
		}
		double pct;
		if (player instanceof ServerPlayerEntity server) {
			pct = ru.admincontrol.util.ModGate.on() ? SkillEffects.server(server, Stat.AIR) : 0;
		} else {
			pct = SkillEffects.clientAir(player);
		}
		if (pct > 0) {
			cir.setReturnValue((int) Math.round(cir.getReturnValueI() * SkillTree.more(pct)));
		}
	}
}
