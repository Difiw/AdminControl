package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.screen.AutoFurnaceScreenHandler;

public class AutoFurnaceScreen extends HandledScreen<AutoFurnaceScreenHandler> {
	private static final Identifier CHEST = new Identifier("textures/gui/container/generic_54.png");
	private static final Identifier FURNACE = new Identifier("textures/gui/container/furnace.png");

	public AutoFurnaceScreen(AutoFurnaceScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = AutoFurnaceScreenHandler.top(handler.furnaces) + 96;
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
		int x = this.x;
		int y = this.y;
		int top = AutoFurnaceScreenHandler.top(this.handler.furnaces);
		context.drawTexture(CHEST, x, y, 0, 0, this.backgroundWidth, AutoFurnaceScreenHandler.HEADER);
		for (int i = AutoFurnaceScreenHandler.HEADER; i < top; i++) {
			context.drawTexture(CHEST, x, y + i, 0, 16, this.backgroundWidth, 1);
		}
		context.drawTexture(CHEST, x, y + top, 0, 126, this.backgroundWidth, 96);
		for (int i = 0; i < this.handler.furnaces; i++) {
			int sy = AutoFurnaceScreenHandler.rowY(i, this.handler.furnaces);
			slot(context, x + AutoFurnaceScreenHandler.INPUT_X, y + sy);
			slot(context, x + AutoFurnaceScreenHandler.OUTPUT_X, y + sy);
			int ax = x + 79;
			int ay = y + sy;
			context.drawTexture(FURNACE, ax, ay, 79, 34, 24, 17);
			int w = (int) Math.min(24L, (long) this.handler.progress(i) * 24L / this.handler.interval());
			if (w > 0) {
				context.drawTexture(FURNACE, ax, ay, 176, 14, w + 1, 16);
			}
		}
	}

	private static void slot(DrawContext context, int sx, int sy) {
		context.fill(sx - 1, sy - 1, sx + 17, sy, 0xFF373737);
		context.fill(sx - 1, sy, sx, sy + 17, 0xFF373737);
		context.fill(sx, sy + 16, sx + 17, sy + 17, 0xFFFFFFFF);
		context.fill(sx + 16, sy, sx + 17, sy + 16, 0xFFFFFFFF);
		context.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
	}
}
