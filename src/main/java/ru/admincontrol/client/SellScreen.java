package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.screen.SellScreenHandler;

import java.util.List;

public class SellScreen extends HandledScreen<SellScreenHandler> {
	private TextFieldWidget countField;

	public SellScreen(SellScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 132;
		this.titleX = 8;
		this.titleY = 6;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> click(0))
				.dimensions(this.x + 7, this.y + 4, 44, 12).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("ОК"), b -> click(1))
				.dimensions(this.x + 125, this.y + 4, 44, 12).build());
		this.countField = new TextFieldWidget(this.textRenderer, this.x + 54, this.y + 16, 68, 12, Text.empty());
		this.countField.setMaxLength(5);
		this.countField.setEditableColor(0xE8EEF8);
		AcHint.apply(this.countField, "Кол-во", v -> {
			if (this.client != null && this.client.player != null) {
				this.handler.setCountFromText(this.client.player, v);
			}
			var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
			buf.writeString("sell", 32);
			buf.writeString(v == null ? "" : v, 32);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.UI_VALUE, buf);
		});
		this.addDrawableChild(this.countField);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.countField != null && this.countField.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.countField != null && this.countField.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.countField != null) {
			AcHint.tick(this.countField, "Кол-во");
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		for (Slot slot : this.handler.slots) {
			if (!slot.hasStack()) {
				continue;
			}
			int sx = this.x + slot.x;
			int sy = this.y + slot.y;
			if (this.handler.sellForbidden(slot.getStack().getItem())) {
				context.fill(sx, sy, sx + 16, sy + 16, 0x99CC2222);
			} else if (this.handler.selectedCount(slot.getIndex()) > 0) {
				context.fill(sx, sy, sx + 16, sy + 16, 0x6655FFFF);
			}
		}
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.frameAround(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
		VanillaInventoryGui.drawPlayerInventoryOnly(context, this.x, this.y, this.backgroundWidth);
	}

	@Override
	public List<Text> getTooltipFromItem(ItemStack stack) {
		List<Text> tooltip = super.getTooltipFromItem(stack);
		if (this.handler.sellForbidden(stack.getItem())) {
			tooltip.add(Text.literal("Нельзя продать в /sell").formatted(Formatting.RED));
		}
		return tooltip;
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.clippedCentered(this.textRenderer, context, "WC: " + ru.admincontrol.util.Money.format(this.handler.previewLapis), 52, 6, 72, AcTheme.TITLE);
	}
}
