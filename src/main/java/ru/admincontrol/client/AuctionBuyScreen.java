package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.AuctionBuyScreenHandler;
import ru.admincontrol.util.Money;

public class AuctionBuyScreen extends HandledScreen<AuctionBuyScreenHandler> {
	private int signature = Integer.MIN_VALUE;

	public AuctionBuyScreen(AuctionBuyScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 196;
		this.backgroundHeight = 172;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = this.handler.qty() * 31 + this.handler.total();
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		int x = this.x + 10;
		int y = this.y + 70;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−10"), b -> click(AuctionBuyScreenHandler.QTY_MINUS_10))
				.dimensions(x, y, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(AuctionBuyScreenHandler.QTY_MINUS))
				.dimensions(x + 40, y, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(AuctionBuyScreenHandler.QTY_PLUS))
				.dimensions(x + 118, y, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> click(AuctionBuyScreenHandler.QTY_PLUS_10))
				.dimensions(x + 150, y, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Всё"), b -> click(AuctionBuyScreenHandler.QTY_ALL))
				.dimensions(this.x + 72, y + 22, 52, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> click(AuctionBuyScreenHandler.CANCEL))
				.dimensions(this.x + 12, this.y + 146, 80, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Купить"), b -> click(AuctionBuyScreenHandler.CONFIRM))
				.dimensions(this.x + 104, this.y + 146, 80, 18).build());
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
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Покупка с аукциона", 8, 6, this.backgroundWidth - 16);
		AcTheme.clippedCentered(this.textRenderer, context, "Цена за шт: " + Money.wc(this.handler.pricePerItem()),
				8, 42, this.backgroundWidth - 16, AcTheme.MUTED);
		AcTheme.clippedCentered(this.textRenderer, context, "Количество: " + this.handler.qty() + " / " + this.handler.maxQty(),
				8, 54, this.backgroundWidth - 16, AcTheme.TEXT);
		AcTheme.clippedCentered(this.textRenderer, context, "Итого: " + Money.wc(this.handler.total()),
				8, 118, this.backgroundWidth - 16, AcTheme.GOLD);
	}
}
