package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.CoinsScreenHandler;
import ru.admincontrol.util.Money;

import java.util.List;

public class CoinsScreen extends HandledScreen<CoinsScreenHandler> {
	private int signature = Integer.MIN_VALUE;
	private TextFieldWidget amountField;

	public CoinsScreen(CoinsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 248;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		boolean typing = this.amountField != null && this.amountField.isFocused();
		int sig = this.handler.coins() + this.handler.warnPrice() * 7 + this.handler.warns() * 13
				+ this.handler.earned() * 17 + this.handler.spent() * 19
				+ this.handler.casinoSpent() * 53 + this.handler.casinoWon() * 59
				+ this.handler.tab() * 31 + this.handler.mode() * 37 + this.handler.page() * 41
				+ this.handler.selected() * 43
				+ (this.handler.warnBuy() ? 3 : 1);
		if (!typing) {
			sig += this.handler.amount() * 47;
		}
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		String typed = this.amountField == null ? null : this.amountField.getText();
		boolean focus = this.amountField != null && this.amountField.isFocused();
		int cursor = this.amountField == null ? 0 : this.amountField.getCursor();
		this.clearChildren();
		this.amountField = null;
		this.addDrawableChild(ButtonWidget.builder(Text.literal(this.handler.tab() == 0 ? "▶ Счёт" : "Счёт"),
						b -> click(CoinsScreenHandler.TAB_ACCOUNT))
				.dimensions(this.x + 12, this.y + 24, 104, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(this.handler.tab() == 1 ? "▶ Платежи" : "Платежи"),
						b -> click(CoinsScreenHandler.TAB_PAY))
				.dimensions(this.x + 124, this.y + 24, 104, 16).build());
		if (this.handler.tab() == 0) {
			rebuildAccount();
		} else {
			rebuildPay(typed, focus, cursor);
		}
	}

	private void rebuildAccount() {
		int y = this.y + 144;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("История"), b -> click(CoinsScreenHandler.HISTORY))
				.dimensions(this.x + 12, y, 216, 18).build());
		y += 22;
		if (this.handler.warnBuy()) {
			this.addDrawableChild(ButtonWidget.builder(
							Text.literal("Снять варн (" + Money.wc(this.handler.warnPrice()) + ")"),
							b -> click(CoinsScreenHandler.UNWARN))
					.dimensions(this.x + 12, y, 216, 18).build());
		}
	}

	private void rebuildPay(String typed, boolean focus, int cursor) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(this.handler.mode() == 0 ? "▶ Игроку" : "Игроку"),
						b -> click(CoinsScreenHandler.MODE_PLAYER))
				.dimensions(this.x + 12, this.y + 44, 104, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(this.handler.mode() == 1 ? "▶ В клан" : "В клан"),
						b -> click(CoinsScreenHandler.MODE_CLAN))
				.dimensions(this.x + 124, this.y + 44, 104, 16).build());
		List<String> list = this.handler.targets();
		int from = this.handler.page() * CoinsScreenHandler.PAGE_SIZE;
		int y = this.y + 64;
		for (int i = 0; i < CoinsScreenHandler.PAGE_SIZE; i++) {
			int index = from + i;
			if (index >= list.size()) {
				break;
			}
			String name = list.get(index);
			boolean on = index == this.handler.selected();
			int row = i;
			this.addDrawableChild(ButtonWidget.builder(Text.literal((on ? "► " : "  ") + name),
							b -> click(CoinsScreenHandler.NICK_BASE + row))
					.dimensions(this.x + 12, y + i * 16, 216, 15).build());
		}
		int nav = this.y + 148;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("←"), b -> click(CoinsScreenHandler.PAGE_PREV))
				.dimensions(this.x + 12, nav, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("→"), b -> click(CoinsScreenHandler.PAGE_NEXT))
				.dimensions(this.x + 200, nav, 28, 16).build());
		int ay = this.y + 168;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−10"), b -> click(CoinsScreenHandler.MINUS_10))
				.dimensions(this.x + 12, ay, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(CoinsScreenHandler.MINUS_1))
				.dimensions(this.x + 52, ay, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(CoinsScreenHandler.PLUS_1))
				.dimensions(this.x + 160, ay, 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> click(CoinsScreenHandler.PLUS_10))
				.dimensions(this.x + 192, ay, 36, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Всё"), b -> click(CoinsScreenHandler.SET_ALL))
				.dimensions(this.x + 94, ay, 52, 16).build());
		this.amountField = new TextFieldWidget(this.textRenderer, this.x + 12, this.y + 186, 216, 18, Text.empty());
		this.amountField.setMaxLength(12);
		this.amountField.setEditableColor(0xE8EEF8);
		this.amountField.setDrawsBackground(true);
		AcHint.apply(this.amountField, "Сумма перевода", v -> sendValue("coins", v));
		if (focus && typed != null) {
			this.amountField.setText(typed);
			this.amountField.setCursor(Math.max(0, Math.min(cursor, typed.length())));
			this.amountField.setFocused(true);
			this.setFocused(this.amountField);
		} else {
			int n = this.handler.amount();
			this.amountField.setText(n <= 0 ? "" : String.valueOf(n));
		}
		this.addDrawableChild(this.amountField);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Перевести"), b -> click(CoinsScreenHandler.CONFIRM))
				.dimensions(this.x + 12, this.y + 206, 216, 16).build());
	}

	private void sendValue(String kind, String value) {
		var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeString(kind, 32);
		buf.writeString(value == null ? "" : value, 32);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.UI_VALUE, buf);
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

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.syncUi();
		if (this.amountField != null) {
			AcHint.tick(this.amountField, "Сумма перевода");
		}
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
		AcTheme.titleCentered(this.textRenderer, context, "World coin", 8, 7, this.backgroundWidth - 16);
		if (this.handler.tab() == 0) {
			AcTheme.clippedCentered(this.textRenderer, context, "Баланс: " + Money.wc(this.handler.coins()),
					8, 48, this.backgroundWidth - 16, AcTheme.GOLD);
			AcTheme.clippedCentered(this.textRenderer, context, "Заработано: " + Money.wc(this.handler.earned()),
					8, 64, this.backgroundWidth - 16, AcTheme.TEXT);
			AcTheme.clippedCentered(this.textRenderer, context, "Потрачено: " + Money.wc(this.handler.spent()),
					8, 80, this.backgroundWidth - 16, AcTheme.MUTED);
			int casinoNet = this.handler.casinoWon() - this.handler.casinoSpent();
			AcTheme.clippedCentered(this.textRenderer, context,
					"Казино ставки: " + Money.wc(this.handler.casinoSpent()),
					8, 96, this.backgroundWidth - 16, AcTheme.MUTED);
			AcTheme.clippedCentered(this.textRenderer, context,
					"Казино выплаты: " + Money.wc(this.handler.casinoWon())
							+ "  •  итог " + (casinoNet > 0 ? "+" : "") + Money.format(casinoNet),
					8, 112, this.backgroundWidth - 16, casinoNet >= 0 ? AcTheme.TEXT : 0xFFCC6666);
			if (this.handler.warnBuy()) {
				AcTheme.clipped(this.textRenderer, context, "Варны: " + this.handler.warns() + "/3",
						12, 124, this.backgroundWidth - 24, AcTheme.MUTED);
			}
			return;
		}
		String pick = this.handler.selectedName();
		if (!this.handler.payClan() && !this.handler.transfers()) {
			AcTheme.clipped(this.textRenderer, context, "Переводы игрокам выключены.",
					12, 80, this.backgroundWidth - 24, AcTheme.MUTED);
		} else if (this.handler.targets().isEmpty()) {
			String empty;
			if (this.handler.payClan()) {
				empty = "Кланов нет.";
			} else if (!this.handler.transfers()) {
				empty = "Переводы игрокам выключены.";
			} else {
				empty = "Нет других игроков.";
			}
			AcTheme.clipped(this.textRenderer, context, empty, 12, 80, this.backgroundWidth - 24, AcTheme.MUTED);
		}
		String dest = pick.isBlank() ? (this.handler.payClan() ? "клан не выбран" : "игрок не выбран") : pick;
		AcTheme.clippedCentered(this.textRenderer, context,
				"стр. " + (this.handler.page() + 1) + " / " + this.handler.pageCount()
						+ "  •  " + dest + "  •  " + Money.format(this.handler.coins()),
				44, 151, 152, AcTheme.MUTED);
	}
}
