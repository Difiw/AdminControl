package ru.admincontrol.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;

@Mixin(PlayerAdvancementTracker.class)
public class PlayerAdvancementTrackerMixin {
	@Shadow
	@Final
	private ServerPlayerEntity owner;

	/** Пока админ управляет NPC, достижения получает NPC, а не админ. */
	@Inject(method = "grantCriterion", at = @At("HEAD"), cancellable = true)
	private void adminControl$npcAdvancement(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on() || this.owner == null) {
			return;
		}
		ServerPlayerEntity npc = ru.admincontrol.util.NpcService.controlledNpc(this.owner);
		if (npc != null && npc != this.owner) {
			cir.setReturnValue(npc.getAdvancementTracker().grantCriterion(advancement, criterionName));
		}
	}

	@Inject(method = "grantCriterion", at = @At("RETURN"))
	private void adminControl$advancement(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (!Boolean.TRUE.equals(cir.getReturnValue()) || this.owner == null || advancement == null) {
			return;
		}
		if (AuthService.pending(this.owner) || advancement.getDisplay() == null) {
			return;
		}
		if (!((PlayerAdvancementTracker) (Object) this).getProgress(advancement).isDone()) {
			return;
		}
		String title = advancement.getDisplay().getTitle().getString();
		AdminNotify.playerAction(this.owner, ru.admincontrol.data.ActionKind.ACHIEVEMENT, "получил достижение " + title);
	}
}
