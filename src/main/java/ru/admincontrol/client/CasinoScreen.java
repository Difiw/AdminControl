package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.CasinoScreenHandler;
import ru.admincontrol.util.CasinoService;

public class CasinoScreen extends HandledScreen<CasinoScreenHandler> {
	private int signature = -1;

	public CasinoScreen(CasinoScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 204;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = (this.handler.casinoOn() ? 1 : 0) * 100000 + this.handler.minTenths() * 1000
				+ this.handler.maxTenths() * 10 + this.handler.chance();
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		boolean on = this.handler.casinoOn();
		this.addDrawableChild(ButtonWidget.builder(Text.literal(on ? "§aКазино вкл" : "§cКазино выкл"),
						b -> click(CasinoScreenHandler.TOGGLE))
				.dimensions(this.x + 12, this.y + 24, 216, 18).build());
		row(CasinoScreenHandler.MIN_MINUS, CasinoScreenHandler.MIN_PLUS, 60);
		row(CasinoScreenHandler.MAX_MINUS, CasinoScreenHandler.MAX_PLUS, 96);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−10"), b -> click(CasinoScreenHandler.CHANCE_MINUS_10))
				.dimensions(this.x + 12, this.y + 150, 50, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(CasinoScreenHandler.CHANCE_MINUS))
				.dimensions(this.x + 66, this.y + 150, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(CasinoScreenHandler.CHANCE_PLUS))
				.dimensions(this.x + 138, this.y + 150, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> click(CasinoScreenHandler.CHANCE_PLUS_10))
				.dimensions(this.x + 178, this.y + 150, 50, 16).build());
	}

	private void row(int minus, int plus, int y) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(minus))
				.dimensions(this.x + 12, this.y + y, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(plus))
				.dimensions(this.x + 200, this.y + y, 28, 16).build());
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
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Настройки казино", 8, 6, this.backgroundWidth - 16);
		center(context, "Мин. множитель: ×" + CasinoService.formatMult(this.handler.minTenths()), 48, AcTheme.TEXT);
		center(context, "Макс. множитель: ×" + CasinoService.formatMult(this.handler.maxTenths()), 84, AcTheme.TEXT);
		center(context, "Шанс победы: " + this.handler.chance() + "%", 132, AcTheme.TEXT);
		center(context, "0% жёстче  ·  100% чуть мягче, дом всё равно в плюсе", 172, AcTheme.MUTED);
	}

	private void center(DrawContext context, String text, int y, int color) {
		AcTheme.clippedCentered(this.textRenderer, context, text, 8, y, this.backgroundWidth - 16, color);
	}
}
