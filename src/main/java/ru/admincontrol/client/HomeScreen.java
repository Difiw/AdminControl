package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.HomeScreenHandler;

public class HomeScreen extends HandledScreen<HomeScreenHandler> {
	private ButtonWidget outline;

	public HomeScreen(HomeScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 260;
		this.backgroundHeight = 130;
	}

	@Override
	protected void init() {
		super.init();
		this.playerInventoryTitleX = 1000;
		int x = this.x + 16;
		int y = this.y + 36;
		int w = this.backgroundWidth - 32;
		click(x, y, w, HomeScreenHandler.GO, "Телепортироваться домой");
		click(x, y + 28, w, HomeScreenHandler.DELETE, "Удалить метку дома");
		this.outline = this.addDrawableChild(ButtonWidget.builder(Text.literal(outlineLabel()), b -> {
			if (this.client != null && this.client.getNetworkHandler() != null) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						ru.admincontrol.net.ModPackets.HOME_OUTLINE,
						net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
			}
		}).dimensions(x, y + 56, w, 24).build());
	}

	private String outlineLabel() {
		return HomeBox.visible ? "Не отображать приват" : "Показать приват";
	}

	private void syncUi() {
		if (this.outline != null) {
			this.outline.setMessage(Text.literal(outlineLabel()));
		}
	}

	private void click(int x, int y, int w, int id, String text) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> click(id)).dimensions(x, y, w, 24).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.syncUi();
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 32);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, this.title.getString(), 8, 8, this.backgroundWidth - 16);
		AcTheme.clipped(this.textRenderer, context, "Приват — блоком из /shop", 16, 20, this.backgroundWidth - 32, AcTheme.MUTED);
	}
}
