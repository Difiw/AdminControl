package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.CoinsTransferScreenHandler;

public class CoinsTransferScreen extends HandledScreen<CoinsTransferScreenHandler> {
	private int signature = Integer.MIN_VALUE;
	private TextFieldWidget amountField;

	public CoinsTransferScreen(CoinsTransferScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		boolean typing = this.amountField != null && this.amountField.isFocused();
		int sig = this.handler.page() * 31 + this.handler.selected() * 17 + this.handler.coins();
		if (!typing) {
			sig += this.handler.amount() * 13;
		}
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		int y = this.y + 44;
		int from = this.handler.page() * CoinsTransferScreenHandler.PAGE_SIZE;
		for (int i = 0; i < CoinsTransferScreenHandler.PAGE_SIZE; i++) {
			int index = from + i;
			if (index >= this.handler.nicks.size()) {
				break;
			}
			String nick = this.handler.nicks.get(index);
			boolean on = index == this.handler.selected();
			String label = (on ? "► " : "  ") + nick;
			int row = i;
			this.addDrawableChild(ButtonWidget.builder(Text.literal(label),
							b -> click(CoinsTransferScreenHandler.NICK_BASE + row))
					.dimensions(this.x + 12, y + i * 16, 216, 15).build());
		}
		int nav = this.y + 142;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("←"), b -> click(CoinsTransferScreenHandler.PAGE_PREV))
				.dimensions(this.x + 12, nav, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("→"), b -> click(CoinsTransferScreenHandler.PAGE_NEXT))
				.dimensions(this.x + 200, nav, 28, 16).build());
		int ay = this.y + 168;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−10"), b -> click(CoinsTransferScreenHandler.MINUS_10))
				.dimensions(this.x + 12, ay, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(CoinsTransferScreenHandler.MINUS_1))
				.dimensions(this.x + 52, ay, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(CoinsTransferScreenHandler.PLUS_1))
				.dimensions(this.x + 160, ay, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> click(CoinsTransferScreenHandler.PLUS_10))
				.dimensions(this.x + 192, ay, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Всё"), b -> click(CoinsTransferScreenHandler.SET_ALL))
				.dimensions(this.x + 94, ay, 52, 16).build());
		this.amountField = new TextFieldWidget(this.textRenderer, this.x + 12, this.y + 186, 216, 18, Text.empty());
		this.amountField.setEditableColor(0xE8EEF8);
		this.amountField.setMaxLength(12);
		this.amountField.setText(String.valueOf(this.handler.amount()));
		AcHint.apply(this.amountField, "Сумма перевода", v -> {
			var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
			buf.writeString("coins_transfer", 32);
			buf.writeString(v == null ? "" : v, 32);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.UI_VALUE, buf);
		});
		this.addDrawableChild(this.amountField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Перевести"), b -> click(CoinsTransferScreenHandler.CONFIRM))
				.dimensions(this.x + 12, this.y + 204, 216, 18).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.amountField != null && this.amountField.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.amountField != null && this.amountField.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
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
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Перевод World coin", 8, 6, this.backgroundWidth - 16);
		AcTheme.clipped(this.textRenderer, context,
				this.handler.selectedNick().isBlank() ? "Игрок: не выбран" : "Игрок: " + this.handler.selectedNick(),
				12, 24, this.backgroundWidth - 24, AcTheme.TEXT);
		if (this.handler.nicks.isEmpty()) {
			AcTheme.wrapped(this.textRenderer, context, "Нет других игроков на сервере.", 12, 48, this.backgroundWidth - 24, AcTheme.MUTED);
		}
		AcTheme.clippedCentered(this.textRenderer, context, "стр. " + (this.handler.page() + 1) + " / " + this.handler.pageCount(),
				12, 146, this.backgroundWidth - 24, AcTheme.MUTED);
		if (this.amountField != null) {
			AcHint.tick(this.amountField, "Сумма перевода");
		}
	}
}
