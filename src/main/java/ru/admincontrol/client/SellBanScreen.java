package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.screen.SellBanScreenHandler;
import ru.admincontrol.util.CreativeCatalog;

import java.util.List;

public class SellBanScreen extends HandledScreen<SellBanScreenHandler> {
	public SellBanScreen(SellBanScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 186;
		this.playerInventoryTitleY = 1000;
		this.titleY = 6;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.x + 8;
		int y = this.y + 18;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Вкл ←"), b -> click(0)).dimensions(x, y, 44, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Вкл →"), b -> click(1)).dimensions(x + 46, y, 44, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> click(2)).dimensions(x + 118, y, 22, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> click(3)).dimensions(x + 142, y, 22, 14).build());
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
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		VanillaInventoryGui.draw(context, this.x, this.y + 18, 176, 5);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		String head = CreativeCatalog.label(this.handler.tab()) + "  "
				+ this.handler.pageIndex() + "/" + this.handler.pageCount();
		AcTheme.clippedCentered(this.textRenderer, context, head, 8, 6, this.backgroundWidth - 16, AcTheme.TITLE);
	}

	@Override
	public List<Text> getTooltipFromItem(ItemStack stack) {
		List<Text> tooltip = super.getTooltipFromItem(stack);
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(SellBanScreenHandler.BAN_KEY)) {
			boolean ban = nbt.getBoolean(SellBanScreenHandler.BAN_KEY);
			tooltip.add(Text.literal(ban ? "Запрещён в /sell  (клик — разрешить)" : "Разрешён  (клик — запретить)")
					.formatted(ban ? Formatting.RED : Formatting.GREEN));
		}
		return tooltip;
	}
}
