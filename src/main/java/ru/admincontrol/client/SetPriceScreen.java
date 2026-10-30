package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.screen.SetPriceScreenHandler;
import ru.admincontrol.util.CreativeCatalog;
import ru.admincontrol.util.Money;

import java.util.List;

public class SetPriceScreen extends HandledScreen<SetPriceScreenHandler> {
	private static final int SIDE = SetPriceScreenHandler.SIDE;
	private static final int CHEST = 176;
	private static final int EXTRA = 24;

	public SetPriceScreen(SetPriceScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = CHEST + SIDE * 2;
		this.backgroundHeight = VanillaInventoryGui.height(SetPriceScreenHandler.ROWS) + EXTRA;
		this.titleX = SIDE + 8;
		this.titleY = 6;
		this.playerInventoryTitleY = this.backgroundHeight - 94 - EXTRA;
		this.playerInventoryTitleX = SIDE + 8;
	}

	@Override
	protected void init() {
		super.init();
		int chest = this.x + SIDE;
		int top = this.y - 20;
		this.addDrawableChild(btn("Вкл ←", 14, chest, top, 50));
		this.addDrawableChild(btn("Вкл →", 15, chest + 52, top, 50));
		this.addDrawableChild(btn("<", 0, chest + 118, top, 22));
		this.addDrawableChild(btn(">", 1, chest + 144, top, 32));

		int lx = this.x + 4;
		int rx = this.x + this.backgroundWidth - 48;
		int y = this.y + 28;
		int g = 18;
		this.addDrawableChild(btn("−10", 6, lx, y, 44));
		this.addDrawableChild(btn("−", 4, lx, y + g, 44));
		this.addDrawableChild(btn("+", 5, lx, y + g * 2, 44));
		this.addDrawableChild(btn("+10", 7, lx, y + g * 3, 44));
		this.addDrawableChild(btn("−10", 10, rx, y, 44));
		this.addDrawableChild(btn("−", 8, rx, y + g, 44));
		this.addDrawableChild(btn("+", 9, rx, y + g * 2, 44));
		this.addDrawableChild(btn("+10", 11, rx, y + g * 3, 44));

		int by = this.y + VanillaInventoryGui.height(SetPriceScreenHandler.ROWS) + 4;
		this.addDrawableChild(btn("−10", 16, chest, by, 32));
		this.addDrawableChild(btn("−", 17, chest + 34, by, 22));
		this.addDrawableChild(btn("+", 18, chest + CHEST - 56, by, 22));
		this.addDrawableChild(btn("+10", 19, chest + CHEST - 32, by, 32));
	}

	private ButtonWidget btn(String text, int id, int x, int y, int w) {
		return ButtonWidget.builder(Text.literal(text), b -> click(id)).dimensions(x, y, w, 16).build();
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y - 24, this.backgroundWidth, this.backgroundHeight + 24, 22);
		VanillaInventoryGui.draw(context, this.x + SIDE, this.y, CHEST, SetPriceScreenHandler.ROWS);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, CreativeCatalog.label(this.handler.tab()), SIDE + 8, 6, AcTheme.TITLE, false);
		String pages = this.handler.pageIndex() + "/" + this.handler.pageCount();
		context.drawText(this.textRenderer, pages, SIDE + CHEST - 8 - this.textRenderer.getWidth(pages), 6, AcTheme.MUTED, false);
		drawSideLabel(context, "Мин", this.handler.minPercent() + "%", 4, 44);
		drawSideLabel(context, "Макс", this.handler.maxPercent() + "%", this.backgroundWidth - 48, 44);
		String section = "Раздел «" + CreativeCatalog.label(this.handler.tab()) + "»  ±WC";
		int chestH = VanillaInventoryGui.height(SetPriceScreenHandler.ROWS);
		AcTheme.clippedCentered(this.textRenderer, context, section, SIDE + 56, chestH + 8, CHEST - 112, AcTheme.TITLE);
	}

	private void drawSideLabel(DrawContext context, String title, String value, int x, int width) {
		int titleW = this.textRenderer.getWidth(title);
		int valueW = this.textRenderer.getWidth(value);
		context.drawText(this.textRenderer, title, x + (width - titleW)  / 2, 6, AcTheme.MUTED, false);
		context.drawText(this.textRenderer, value, x + (width - valueW)  / 2, 16, AcTheme.TITLE, false);
	}

	@Override
	public List<Text> getTooltipFromItem(ItemStack stack) {
		List<Text> tooltip = super.getTooltipFromItem(stack);
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(SetPriceScreenHandler.SELL_KEY)) {
			int sell = nbt.getInt(SetPriceScreenHandler.SELL_KEY);
			int min = nbt.getInt(SetPriceScreenHandler.AMIN_KEY);
			int max = nbt.getInt(SetPriceScreenHandler.AMAX_KEY);
			tooltip.add(Text.literal("Цена /sell: " + Money.wc(sell) + " за 1 шт.").formatted(Formatting.YELLOW));
			tooltip.add(Text.literal("Аукцион: " + Money.format(min) + "–" + Money.wc(max) + " ("
					+ this.handler.minPercent() + "–" + this.handler.maxPercent() + "%)").formatted(Formatting.YELLOW));
			tooltip.add(Text.literal("ЛКМ +1  ПКМ −1 к цене /sell").formatted(Formatting.GRAY));
			tooltip.add(Text.literal("Кнопки внизу меняют весь раздел.").formatted(Formatting.GRAY));
		}
		return tooltip;
	}
}
