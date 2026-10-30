package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.admincontrol.client.CwTransitionOverlay;

/**
 * Любой переход в сюжетное/строительное измерение и обратно (игра, тест, стройка) идёт под тематическим затемнением,
 * без ванильной надписи «Подключение к миру»: ощущение, будто игрок остаётся в том же мире.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class LumiraRespawnMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private MinecraftClient client;

	private static boolean admin_control$story(Identifier id) {
		return id != null && id.getNamespace().equals("admin_control")
				&& (id.getPath().startsWith("cw_story_") || id.getPath().equals("cw_builder"));
	}

	@Inject(method = "onPlayerRespawn", at = @At("HEAD"))
	private void admin_control$transition(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
		Identifier target = packet.getDimension().getValue();
		Identifier current = this.client.world == null ? null : this.client.world.getRegistryKey().getValue();
		if (admin_control$story(target) || admin_control$story(current)) {
			CwTransitionOverlay.beginLocal();
		}
	}
}
