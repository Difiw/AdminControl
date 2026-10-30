package ru.admincontrol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.screen.InspectInventoryScreenHandler;

/**
 * Тот же фон и раскладка, что у инвентаря по клавише E.
 */
public class InspectInventoryScreen extends HandledScreen<InspectInventoryScreenHandler> {
	private static final Identifier TEXTURE = new Identifier("textures/gui/container/inventory.png");

	public InspectInventoryScreen(InspectInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
	}

	@Override
	protected void init() {
		super.init();
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Рюкзак"), b -> {
			if (this.client != null && this.client.interactionManager != null) {
				this.client.interactionManager.clickButton(this.handler.syncId, 0);
			}
		}).dimensions(this.x + 88, this.y + 6, 52, 12).build());
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.frameAround(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, TEXTURE);
		int x = this.x;
		int y = this.y;
		context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
		LivingEntity entity = findTargetEntity();
		if (entity != null) {
			InventoryScreen.drawEntity(context, x + 51, y + 75, 30, (float) (x + 51) - mouseX, (float) (y + 75 - 50) - mouseY, entity);
		}
	}

	@Override
	protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
		return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		return mouseX < left || mouseY < top || mouseX >= left + this.backgroundWidth || mouseY >= top + this.backgroundHeight;
	}

	private LivingEntity findTargetEntity() {
		if (this.client == null || this.client.world == null) {
			return this.client != null ? this.client.player : null;
		}
		PlayerEntity target = this.client.world.getPlayerByUuid(this.handler.getTargetUuid());
		return target != null ? target : this.client.player;
	}
}
