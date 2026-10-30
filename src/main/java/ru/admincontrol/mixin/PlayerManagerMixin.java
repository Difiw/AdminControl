package ru.admincontrol.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.PunishmentNotice;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
	@Inject(method = "onPlayerConnect", at = @At("HEAD"))
	private void adminControl$evictNpc(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (player != null && !ru.admincontrol.util.NpcService.isNpc(player)) {
			ru.admincontrol.util.NpcService.evictName(((PlayerManager) (Object) this).getServer(),
					player.getGameProfile().getName());
		}
	}

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	private void adminControl$bindNpcHandler(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (player instanceof ru.admincontrol.util.NpcPlayerEntity npc
				&& !ru.admincontrol.util.NpcService.isClientDriven(npc)
				&& !(npc.networkHandler instanceof ru.admincontrol.util.NpcNetworkHandler)) {
			new ru.admincontrol.util.NpcNetworkHandler(((PlayerManager) (Object) this).getServer(), connection, npc);
		}
	}

	@Inject(method = "savePlayerData", at = @At("HEAD"), cancellable = true)
	private void adminControl$skipNpcSave(ServerPlayerEntity player, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (ru.admincontrol.util.NpcService.isNpc(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "respawnPlayer", at = @At("RETURN"))
	private void adminControl$respawn(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity next = cir.getReturnValue();
		if (next != null && !alive) {
			ru.admincontrol.util.AdminNotify.playerAction(next, ru.admincontrol.data.ActionKind.RESPAWN, "возродился");
			ru.admincontrol.util.ClanWarService.afterRespawn(next);
			ru.admincontrol.cw.CwRespawnService.afterRespawn(next);
		}
		if (next != null) {
			if (!alive && ru.admincontrol.cw.CwBuilderWorld.inside(oldPlayer)) {
				ru.admincontrol.cw.CwBuilderWorld.resumeAfterRespawn(next);
			}
			ru.admincontrol.util.WorldGate.ejectIfLocked(next);
		}
	}

	@Inject(method = "checkCanJoin", at = @At("RETURN"), cancellable = true)
	private void adminControl$blockBanned(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (cir.getReturnValue() != null || profile == null) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		data.expireBans(System.currentTimeMillis());
		var ban = data.findActiveBan(profile.getId(), profile.getName());
		if (ban.isPresent()) {
			cir.setReturnValue(PunishmentNotice.fromEntry(profile.getName(), ban.get()));
			return;
		}
		var jail = data.findActiveJail(profile.getId(), profile.getName());
		if (jail.isPresent()) {
			cir.setReturnValue(PunishmentNotice.fromEntry(profile.getName(), jail.get()));
		}
	}
}
