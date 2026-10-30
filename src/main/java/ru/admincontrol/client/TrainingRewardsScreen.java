package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.TrainingRewardsScreenHandler;
import ru.admincontrol.util.TrainingConfig;

public class TrainingRewardsScreen extends HandledScreen<TrainingRewardsScreenHandler> {
	private int signature = Integer.MIN_VALUE;

	public TrainingRewardsScreen(TrainingRewardsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 250;
		this.backgroundHeight = 220;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = this.handler.type();
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			sig = sig * 13 + this.handler.coins(i) + this.handler.power(i) * 7;
		}
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		int x = this.x + 10;
		for (int i = 0; i < TrainingConfig.TYPES.length; i++) {
			final int fi = i;
			boolean on = this.handler.type() == i;
			this.addDrawableChild(ButtonWidget.builder(Text.literal((on ? "► " : "") + TrainingConfig.TYPE_LABELS[i]),
							b -> click(TrainingRewardsScreenHandler.TYPE_BASE + fi))
					.dimensions(x + i * 76, this.y + 28, 72, 16).build());
		}
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			final int fi = i;
			int y = this.y + 56 + i * 22;
			int base = TrainingRewardsScreenHandler.ROW + i * 4;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(base)).dimensions(x + 70, y, 18, 16).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(base + 1)).dimensions(x + 118, y, 18, 16).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(base + 2)).dimensions(x + 170, y, 18, 16).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(base + 3)).dimensions(x + 218, y, 18, 16).build());
		}
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
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Награды тренировки", 8, 7, this.backgroundWidth - 16);
		context.drawText(this.textRenderer, "Время", 12, 46, AcTheme.MUTED, false);
		context.drawText(this.textRenderer, "WC", 94, 46, AcTheme.MUTED, false);
		context.drawText(this.textRenderer, "сила", 188, 46, AcTheme.MUTED, false);
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			int y = 60 + i * 22;
			context.drawText(this.textRenderer, TrainingConfig.MINUTES[i] + " мин", 12, y, AcTheme.TEXT, false);
			AcTheme.clippedCentered(this.textRenderer, context, ru.admincontrol.util.Money.format(this.handler.coins(i)), 88, y, 30, AcTheme.GOLD);
			AcTheme.clippedCentered(this.textRenderer, context,
					String.format(java.util.Locale.US, "%.1f", this.handler.power(i) / 10.0), 188, y, 30, AcTheme.GOLD);
		}
	}
}
