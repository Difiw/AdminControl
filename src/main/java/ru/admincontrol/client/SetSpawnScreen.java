package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.data.SpawnChoice;
import ru.admincontrol.screen.SetSpawnScreenHandler;

public class SetSpawnScreen extends HandledScreen<SetSpawnScreenHandler> {
	public SetSpawnScreen(SetSpawnScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		int extra = (this.handler.hasHome ? 32 : 0) + (this.handler.hasClan ? 32 : 0);
		this.backgroundWidth = 240;
		this.backgroundHeight = 136 + extra;
	}

	@Override
	protected void init() {
		super.init();
		this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
		this.playerInventoryTitleX = 1000;
		int x = this.x + 20;
		int y = this.y + 38;
		int w = this.backgroundWidth - 40;
		addChoiceButton(x, y, w, 0, SpawnChoice.BED, "Спавн у кровати", this.handler.hasBed ? "есть кровать" : "кровати нет");
		y += 32;
		addChoiceButton(x, y, w, 1, SpawnChoice.WORLD, "Спавн на общей точке мира", "точка мира");
		if (this.handler.hasHome) {
			y += 32;
			addChoiceButton(x, y, w, 2, SpawnChoice.HOME, "Спавн на метке дома", "метка дома есть");
		}
		if (this.handler.hasClan) {
			y += 32;
			addChoiceButton(x, y, w, 3, SpawnChoice.CLAN, "Спавн в доме клана", "есть доступ");
		}
	}

	private void addChoiceButton(int x, int y, int width, int id, SpawnChoice choice, String label, String hint) {
		boolean selected = this.handler.current == choice;
		Text text = Text.literal((selected ? "▶ " : "  ") + label + "  · " + hint)
				.formatted(selected ? Formatting.GREEN : Formatting.WHITE);
		this.addDrawableChild(ButtonWidget.builder(text, button -> {
			if (this.client != null && this.client.interactionManager != null) {
				this.client.interactionManager.clickButton(this.handler.syncId, id);
			}
		}).dimensions(x, y, width, 24).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 32);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, this.title.getString(), 8, 8, this.backgroundWidth - 16);
		AcTheme.clipped(this.textRenderer, context, "Выберите, куда вас будут возвращать", 20, 22, this.backgroundWidth - 40, AcTheme.MUTED);
	}
}
