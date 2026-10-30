package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.screen.AuctionScreenHandler;
import ru.admincontrol.util.Money;

import java.util.List;

public class AuctionScreen extends HandledScreen<AuctionScreenHandler> {
	private static final int ROWS = 3;

	public AuctionScreen(AuctionScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = VanillaInventoryGui.height(ROWS);
		this.titleX = 28;
		this.titleY = 6;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	private ButtonWidget prev;
	private ButtonWidget next;

	@Override
	protected void init() {
		super.init();
		this.prev = this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> click(0))
				.dimensions(this.x + 7, this.y + 4, 16, 12).build());
		this.next = this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> click(1))
				.dimensions(this.x + 153, this.y + 4, 16, 12).build());
		updateArrows();
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private void updateArrows() {
		boolean show = this.handler.showPageButtons();
		this.prev.visible = show;
		this.next.visible = show;
		this.prev.active = show;
		this.next.active = show;
		this.titleX = show ? 28 : 8;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		updateArrows();
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.frameAround(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
		VanillaInventoryGui.draw(context, this.x, this.y, this.backgroundWidth, ROWS);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		int titleX = this.handler.showPageButtons() ? 26 : 8;
		int titleW = this.handler.showPageButtons() ? 124 : 160;
		AcTheme.clippedCentered(this.textRenderer, context, this.title.getString(), titleX, 6, titleW, AcTheme.TITLE);
		AcTheme.clipped(this.textRenderer, context, "Инвентарь: клик — выставить", 8, this.playerInventoryTitleY, 160, AcTheme.MUTED);
	}

	@Override
	public List<Text> getTooltipFromItem(ItemStack stack) {
		List<Text> tooltip = super.getTooltipFromItem(stack);
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(AuctionScreenHandler.PRICE_KEY)) {
			int price = nbt.getInt(AuctionScreenHandler.PRICE_KEY);
			int min = nbt.getInt(AuctionScreenHandler.MIN_KEY);
			int max = nbt.getInt(AuctionScreenHandler.MAX_KEY);
			tooltip.add(Text.literal("Цена: " + Money.wc(price) + " за 1 шт.").formatted(Formatting.AQUA));
			if (nbt.contains(AuctionScreenHandler.MIN_KEY)) {
				tooltip.add(Text.literal("Диапазон: " + Money.format(min) + "–" + Money.format(max) + " (100–150% /sell)").formatted(Formatting.DARK_AQUA));
			}
			String seller = nbt.getString(AuctionScreenHandler.SELLER_KEY);
			if (seller != null && !seller.isBlank()) {
				tooltip.add(Text.literal("Продавец: " + seller).formatted(Formatting.GRAY));
			}
			if (nbt.getBoolean(AuctionScreenHandler.OWN_KEY)) {
				tooltip.add(Text.literal("ЛКМ +1  ПКМ −1  Shift — забрать").formatted(Formatting.YELLOW));
			} else {
				tooltip.add(Text.literal("Нажмите, чтобы выбрать количество").formatted(Formatting.YELLOW));
			}
		}
		return tooltip;
	}
}
