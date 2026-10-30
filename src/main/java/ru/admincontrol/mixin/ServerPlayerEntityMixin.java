package ru.admincontrol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.GodModeService;
import ru.admincontrol.util.TabListColors;

import java.util.OptionalInt;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
	@Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
	private void adminControl$onDeath(DamageSource source, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (ru.admincontrol.cw.CwRespawnService.interceptDeath(player)) {
			ci.cancel();
			return;
		}
		if (ru.admincontrol.util.NpcService.isControlling(player)) {
			return;
		}
		if (ru.admincontrol.util.NpcService.isNpc(player)) {
			return;
		}
		if (EventService.divertDeath(player, source)) {
			ci.cancel();
			return;
		}
		if (ru.admincontrol.util.TrainingService.onDeath(player)) {
			ci.cancel();
			return;
		}
		AdminNotify.playerAction(player, ActionKind.DEATH, "погиб, причина: " + ru.admincontrol.util.DeathCause.describe(source)
				+ " · " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
		if (ru.admincontrol.data.DataManager.get() != null) {
			ru.admincontrol.data.DataManager.get().getOrCreate(player).deaths++;
			ru.admincontrol.data.DataManager.get().markDirty();
		}
		EventService.onDeath(player);
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void adminControl$agm(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if ((GodModeService.enabled(player) && !ru.admincontrol.util.TrainingService.active(player)
				&& !ru.admincontrol.util.EventService.busy(player)
				&& !ru.admincontrol.util.NpcService.isControlling(player))
				|| AuthService.invulnerable(player)) {
			cir.setReturnValue(false);
			return;
		}
		if (source != null && !AuthService.pending(player)) {
			Entity attacker = source.getAttacker();
			if (attacker instanceof MobEntity mob) {
				AdminNotify.playerAction(player, ActionKind.DAMAGE, "получил урон от моба " + mob.getName().getString());
			} else if (attacker instanceof ServerPlayerEntity other) {
				AdminNotify.playerAction(player, ActionKind.DAMAGE, "получил урон от игрока " + other.getGameProfile().getName());
			}
		}
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void adminControl$warmupDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (amount > 0 && Boolean.TRUE.equals(cir.getReturnValue())) {
			ru.admincontrol.util.TeleportWarmup.onDamage((ServerPlayerEntity) (Object) this);
		}
	}

	@Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"), cancellable = true)
	private void adminControl$teleport(ServerWorld world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (ru.admincontrol.util.WorldGate.blockTravel(player, world)) {
			ci.cancel();
			return;
		}
		if (world != null && world != player.getServerWorld()
				&& ru.admincontrol.util.TpGuard.blocks(player, world, x, y, z)) {
			ci.cancel();
			return;
		}
		if (AuthService.pending(player) || ru.admincontrol.util.SpectateService.isSpectating(player)) {
			return;
		}
		ru.admincontrol.util.TeleportLog.onTeleport(player, x, y, z);
		ru.admincontrol.util.TeleportFx.play(player, world, x, y, z);
	}

	@Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
	private void adminControl$world(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (ru.admincontrol.util.WorldGate.blockTravel(player, destination)) {
			cir.setReturnValue(player);
			return;
		}
		if (destination != null && !AuthService.pending(player)) {
			AdminNotify.playerAction(player, ActionKind.WORLD, "перешёл в другой мир " + destination.getRegistryKey().getValue());
		}
	}

	@Inject(method = "closeHandledScreen", at = @At("RETURN"))
	private void adminControl$closeScreen(CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.ScreenNav.forget((ServerPlayerEntity) (Object) this);
	}

	@Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
	private void adminControl$screen(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (ru.admincontrol.util.ActivityLock.blocked(player)) {
			cir.setReturnValue(OptionalInt.empty());
			String name = factory == null || factory.getDisplayName() == null ? "" : factory.getDisplayName().getString();
			boolean quiet = name.isBlank() || "Инвентарь".equalsIgnoreCase(name);
			ru.admincontrol.util.ActivityLock.deny(player, !quiet);
			return;
		}
		if (ru.admincontrol.util.FreezeService.actionsBlocked(player)) {
			cir.setReturnValue(OptionalInt.empty());
			ru.admincontrol.util.CwService.notice(player, "Вы заморожены (уровень 3): интерфейсы недоступны.");
			return;
		}
		if (factory != null && !AuthService.pending(player)) {
			String name = factory.getDisplayName() == null ? "" : factory.getDisplayName().getString();
			if (name.isBlank()) {
				name = factory.getClass().getSimpleName();
			}
			AdminNotify.playerAction(player, ActionKind.SCREEN, "открыл интерфейс " + name, true);
		}
	}

	@Inject(method = "trySleep", at = @At("HEAD"))
	private void adminControl$sleep(net.minecraft.util.math.BlockPos pos, CallbackInfoReturnable<?> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (!AuthService.pending(player)) {
			AdminNotify.playerAction(player, ActionKind.SLEEP, "лёг спать " + pos.toShortString());
		}
	}

	@Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
	private void adminControl$tabName(CallbackInfoReturnable<Text> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		Text name = TabListColors.displayName((ServerPlayerEntity) (Object) this);
		if (name != null) {
			cir.setReturnValue(name);
		}
	}
}
