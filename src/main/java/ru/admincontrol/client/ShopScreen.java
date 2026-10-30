package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.ShopScreenHandler;
import ru.admincontrol.util.Money;

public class ShopScreen extends HandledScreen<ShopScreenHandler> {
	public ShopScreen(ShopScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 122;
		this.playerInventoryTitleY = 1000;
	}

	private int signature = Integer.MIN_VALUE;
	private int privateLevel = 1;
	private static final String[] PRIVATE_MATERIAL = {"медь", "золото", "железо", "алмаз", "незерит"};

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = (this.handler.on() ? 1 : 0)
				+ this.handler.home() * 5
				+ this.handler.luck() * 7
				+ this.handler.power() * 11
				+ this.handler.packLevel() * 13
				+ this.handler.backpack() * 17
				+ this.handler.privatePrice(1) * 19
				+ this.handler.privatePrice(2) * 23
				+ this.handler.privatePrice(3) * 29
				+ this.handler.privatePrice(4) * 31
				+ this.handler.privatePrice(5) * 53
				+ this.handler.workbenchPrice() * 37
				+ (this.handler.workbenchOwned() ? 41 : 0)
				+ this.handler.furnaceLevel() * 43
				+ this.handler.furnacePrice() * 47
				+ this.handler.furnaceMax() * 59
				+ this.handler.furnaceNextCount() * 61
				+ this.handler.furnaceNextSeconds() * 67
				+ this.handler.coins();
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		int x = this.x + 12;
		int y = this.y + 24;
		int step = 22;
		if (!this.handler.on()) {
			return;
		}
		btn("Слот дома  " + Money.wc(this.handler.home()), ShopScreenHandler.BUY_HOME, x, y, 216);
		btn("Удача +0.1  " + Money.wc(this.handler.luck()), ShopScreenHandler.BUY_LUCK, x, y + step, 216);
		btn("Сила +0.1  " + Money.wc(this.handler.power()), ShopScreenHandler.BUY_POWER, x, y + step * 2, 216);
		int lvl = this.privateLevel;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
			this.privateLevel = this.privateLevel <= 1 ? 5 : this.privateLevel - 1;
			rebuild();
		}).dimensions(x, y + step * 3, 20, 18).build());
		btn("Приват ур." + lvl + " (" + PRIVATE_MATERIAL[lvl - 1] + ")  " + Money.wc(this.handler.privatePrice(lvl)),
				ShopScreenHandler.BUY_PRIVATE + lvl, x + 24, y + step * 3, 168);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
			this.privateLevel = this.privateLevel >= 5 ? 1 : this.privateLevel + 1;
			rebuild();
		}).dimensions(x + 196, y + step * 3, 20, 18).build());
	}

	private void btn(String text, int id, int x, int y, int w) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
			if (this.client != null && this.client.interactionManager != null) {
				this.client.interactionManager.clickButton(this.handler.syncId, id);
			}
		}).dimensions(x, y, w, 18).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.syncUi();
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context,
				this.handler.on() ? ("Магазин  •  " + Money.wc(this.handler.coins())) : "Магазин выключен",
				8, 6, this.backgroundWidth - 16);
	}
}
