package ru.admincontrol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.screen.ExtraChestScreenHandler;

public class ExtraChestScreen extends HandledScreen<ExtraChestScreenHandler> {
	private static final Identifier TEXTURE = new Identifier("textures/gui/container/generic_54.png");

	public ExtraChestScreen(ExtraChestScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 114 + handler.rows * 18;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.setShaderTexture(0, TEXTURE);
		int x = this.x;
		int y = this.y;
		int rows = this.handler.rows;
		context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, rows * 18 + 17);
		context.drawTexture(TEXTURE, x, y + rows * 18 + 17, 0, 126, this.backgroundWidth, 96);
	}
}
