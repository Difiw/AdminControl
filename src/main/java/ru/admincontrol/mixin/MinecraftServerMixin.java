package ru.admincontrol.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.ServerTps;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void adminControl$tps(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerTps.onServerTick();
	}

	@Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
	private void adminControl$getPermissionLevel(GameProfile profile, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null || profile == null) {
			return;
		}
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerPlayerEntity player = server.getPlayerManager() == null ? null : server.getPlayerManager().getPlayer(profile.getId());
		if (player != null && data.cheatsAllowed(player)) {
			cir.setReturnValue(4);
			return;
		}
		if (data.settings().playerCheats) {
			cir.setReturnValue(4);
			return;
		}
		if (data.settings().adminCheats && data.isAdmin(profile.getId(), profile.getName())) {
			cir.setReturnValue(4);
		}
	}
}
