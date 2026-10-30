package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.AuctionOfferScreenHandler;
import ru.admincontrol.util.Money;

public class AuctionOfferScreen extends HandledScreen<AuctionOfferScreenHandler> {
	private int signature = Integer.MIN_VALUE;
	private TextFieldWidget priceField;
	private TextFieldWidget qtyField;

	public AuctionOfferScreen(AuctionOfferScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 196;
		this.backgroundHeight = 168;
		this.playerInventoryTitleY = 1000;
		this.titleY = 6;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = this.handler.price() * 31 + this.handler.qty();
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		int x = this.x + 10;
		int y = this.y + 72;
		btn("−10", AuctionOfferScreenHandler.PRICE_MINUS_10, x, y, 36);
		btn("−", AuctionOfferScreenHandler.PRICE_MINUS, x + 40, y, 28);
		btn("+", AuctionOfferScreenHandler.PRICE_PLUS, x + 118, y, 28);
		btn("+10", AuctionOfferScreenHandler.PRICE_PLUS_10, x + 150, y, 36);
		this.priceField = field("Цена", this.handler.price(), x + 72, y, "auction_price");
		int q = y + 36;
		btn("−10", AuctionOfferScreenHandler.QTY_MINUS_10, x, q, 36);
		btn("−", AuctionOfferScreenHandler.QTY_MINUS, x + 40, q, 28);
		btn("+", AuctionOfferScreenHandler.QTY_PLUS, x + 118, q, 28);
		btn("+10", AuctionOfferScreenHandler.QTY_PLUS_10, x + 150, q, 36);
		this.qtyField = field("Кол-во", this.handler.qty(), x + 72, q, "auction_qty");
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Всё"), b -> click(AuctionOfferScreenHandler.QTY_ALL))
				.dimensions(this.x + 72, q + 20, 52, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> click(AuctionOfferScreenHandler.CANCEL))
				.dimensions(this.x + 12, this.y + 142, 80, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Выставить"), b -> click(AuctionOfferScreenHandler.CONFIRM))
				.dimensions(this.x + 104, this.y + 142, 80, 18).build());
	}

	private TextFieldWidget field(String hint, int value, int x, int y, String kind) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, 44, 16, Text.empty());
		f.setMaxLength(12);
		f.setText(String.valueOf(value));
		AcHint.apply(f, hint, v -> {
			var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
			buf.writeString(kind, 32);
			buf.writeString(v == null ? "" : v, 32);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.UI_VALUE, buf);
		});
		this.addDrawableChild(f);
		return f;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.priceField != null && this.priceField.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (this.qtyField != null && this.qtyField.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void btn(String text, int id, int x, int y, int w) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> click(id)).dimensions(x, y, w, 16).build());
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
		AcTheme.titleCentered(this.textRenderer, context, "Выставить на аукцион", 8, 6, this.backgroundWidth - 16);
		AcTheme.clippedCentered(this.textRenderer, context, "Цена за шт: " + Money.wc(this.handler.price()),
				8, 48, this.backgroundWidth - 16, AcTheme.GOLD);
		AcTheme.clippedCentered(this.textRenderer, context, Money.format(this.handler.minPrice()) + "–" + Money.format(this.handler.maxPrice()),
				8, 58, this.backgroundWidth - 16, AcTheme.MUTED);
		AcTheme.clippedCentered(this.textRenderer, context, "Количество: " + this.handler.qty() + " / " + this.handler.maxQty(),
				8, 92, this.backgroundWidth - 16, AcTheme.TEXT);
	}
}
