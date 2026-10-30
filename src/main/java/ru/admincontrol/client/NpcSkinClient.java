package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcSkinClient {
	private static final Map<UUID, Identifier> TEXTURES = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
	private static PlayerEntityModel<AbstractClientPlayerEntity> slimModel;
	private NpcSkinClient() {}

	public static void receive(MinecraftClient client, UUID uuid, String name, boolean slim, byte[] png) {
		client.execute(() -> {
			NativeImage image = null;
			boolean handedToTextureManager = false;
			try {
				image = NativeImage.read(new ByteArrayInputStream(png));
				if (image.getWidth() != 64 || image.getHeight() != 64) return;
				Identifier textureId = new Identifier("admin_control", "npc/" + uuid.toString().replace('-', '_'));
				client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
				handedToTextureManager = true;
				TEXTURES.put(uuid, textureId);
				SLIM.put(uuid, slim);
			} catch (Exception ignored) {
				// Malformed or stale packets must never crash the rendering thread.
			} finally {
				if (!handedToTextureManager && image != null) image.close();
			}
		});
	}

	public static Identifier texture(UUID uuid) { return TEXTURES.get(uuid); }
	public static boolean slim(UUID uuid) { return SLIM.getOrDefault(uuid, false); }
	public static PlayerEntityModel<AbstractClientPlayerEntity> slimModel() {
		if (slimModel == null) {
			MinecraftClient client = MinecraftClient.getInstance();
			slimModel = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), false);
		}
		return slimModel;
	}
}
