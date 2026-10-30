package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.HangingSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.LumiraIds;

/** Клиентская регистрация: слои прозрачности листвы/саженцев/дверей и отрисовка табличек новых пород. */
@Environment(EnvType.CLIENT)
public final class LumiraClient {
	private LumiraClient() {
	}

	public static void register() {
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			cutoutMipped(LumiraContent.block(s + "_leaves"));
			cutout(LumiraContent.block(s + "_sapling"));
			cutout(LumiraContent.block(s + "_door"));
			cutout(LumiraContent.block(s + "_closed_door"));
			cutout(LumiraContent.block(s + "_trapdoor"));
		}
		for (Block glass : LumiraContent.MAGIC_GLASS) {
			BlockRenderLayerMap.INSTANCE.putBlock(glass, RenderLayer.getTranslucent());
		}
		for (Block pane : LumiraContent.MAGIC_GLASS_PANES) {
			BlockRenderLayerMap.INSTANCE.putBlock(pane, RenderLayer.getTranslucent());
		}
		cutout(LumiraContent.block("rune_anvil"));
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(ru.admincontrol.lumira.screen.LumiraScreens.WORKBENCH, RuneWorkbenchScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(ru.admincontrol.lumira.screen.LumiraScreens.ANVIL, RuneAnvilScreen::new);
		BlockEntityRendererFactories.register(LumiraContent.SIGN_ENTITY, SignRenderer::new);
	}

	private static void cutout(Block block) {
		BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
	}

	private static void cutoutMipped(Block block) {
		BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutoutMipped());
	}

	/** Обычная и подвесная таблички используют разные ванильные отрисовщики. */
	private static final class SignRenderer implements BlockEntityRenderer<SignBlockEntity> {
		private final SignBlockEntityRenderer normal;
		private final HangingSignBlockEntityRenderer hanging;

		SignRenderer(BlockEntityRendererFactory.Context ctx) {
			this.normal = new SignBlockEntityRenderer(ctx);
			this.hanging = new HangingSignBlockEntityRenderer(ctx);
		}

		@Override
		public void render(SignBlockEntity entity, float tickDelta, MatrixStack matrices,
						   VertexConsumerProvider vertexConsumers, int light, int overlay) {
			net.minecraft.block.Block block = entity.getCachedState().getBlock();
			if (block instanceof net.minecraft.block.HangingSignBlock || block instanceof net.minecraft.block.WallHangingSignBlock) {
				hanging.render(entity, tickDelta, matrices, vertexConsumers, light, overlay);
			} else {
				normal.render(entity, tickDelta, matrices, vertexConsumers, light, overlay);
			}
		}
	}
}
