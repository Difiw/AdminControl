package ru.admincontrol.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.client.ClanTags;
import ru.admincontrol.client.SkillClient;
import ru.admincontrol.client.SpectateHud;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
	@Unique private PlayerEntityModel<AbstractClientPlayerEntity> adminControl$oldModel;
	@Inject(method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
	private void adminControl$npcSkin(AbstractClientPlayerEntity player, CallbackInfoReturnable<net.minecraft.util.Identifier> cir) {
		if (player != null) {
			net.minecraft.util.Identifier texture = ru.admincontrol.client.NpcSkinClient.texture(player.getUuid());
			if (texture != null) cir.setReturnValue(texture);
		}
	}
	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void adminControl$hideSelfWhileNpcControl(AbstractClientPlayerEntity player, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (player == null) {
			return;
		}
		String nick = player.getGameProfile().getName();
		if (SpectateHud.hidingPossessedNpc(nick) || SpectateHud.hidingSpectateTarget(nick)) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && player != client.player) {
				ci.cancel();
				return;
			}
		}
		if (ru.admincontrol.client.NpcSkinClient.slim(player.getUuid())) {
			LivingEntityRendererAccessor renderer = (LivingEntityRendererAccessor) this;
			adminControl$oldModel = (PlayerEntityModel<AbstractClientPlayerEntity>) renderer.adminControl$getModel();
			renderer.adminControl$setModel(ru.admincontrol.client.NpcSkinClient.slimModel());
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
	private void adminControl$restorePlayerModel(AbstractClientPlayerEntity player, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (adminControl$oldModel != null) {
			((LivingEntityRendererAccessor) this).adminControl$setModel(adminControl$oldModel);
			adminControl$oldModel = null;
		}
	}

	@Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void adminControl$shadeLabel(AbstractClientPlayerEntity player, Text text, MatrixStack matrices,
			VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (player != null && SkillClient.labelHidden(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("TAIL"))
	private void adminControl$clanNametag(AbstractClientPlayerEntity player, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (player == null || vertices == null) {
			return;
		}
		String clan = ClanTags.of(player.getGameProfile().getName());
		if (clan == null || clan.isBlank()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == player && client.options.getPerspective().isFirstPerson()) {
			return;
		}
		if (client.getEntityRenderDispatcher().getSquaredDistanceToCamera(player) > 4096.0 || SkillClient.labelHidden(player)) {
			return;
		}
		Text text = Text.literal(clan).formatted(Formatting.AQUA);
		TextRenderer tr = client.textRenderer;
		boolean sneak = !player.isSneaky();
		float y = player.getNameLabelHeight() + 0.28f;
		matrices.push();
		matrices.translate(0.0, y, 0.0);
		matrices.multiply(client.getEntityRenderDispatcher().getRotation());
		matrices.scale(-0.025f, -0.025f, 0.025f);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float x = -tr.getWidth(text) / 2f;
		int bg = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
		int see = sneak ? 0 : 2;
		tr.draw(text, x, 0, 0x20FFFFFF, false, matrix, vertices,
				see == 0 ? TextRenderer.TextLayerType.NORMAL : TextRenderer.TextLayerType.SEE_THROUGH, bg, light);
		if (sneak) {
			tr.draw(text, x, 0, 0xFFFFFFFF, false, matrix, vertices, TextRenderer.TextLayerType.NORMAL, 0, light);
		}
		matrices.pop();
	}
}
