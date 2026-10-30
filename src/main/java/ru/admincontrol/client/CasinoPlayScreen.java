package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import ru.admincontrol.screen.CasinoPlayScreenHandler;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Money;

import java.util.Locale;

public class CasinoPlayScreen extends HandledScreen<CasinoPlayScreenHandler> implements AutoFit.Sized {
	private int signature = Integer.MIN_VALUE;

	@Override
	public int adminControl$maxPanelHeight() {
		return 252;
	}

	public CasinoPlayScreen(CasinoPlayScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 188;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = this.handler.coins() * 31 + this.handler.bet() * 17 + this.handler.lastTenths() * 13
				+ this.handler.lastNet() + (this.handler.casinoOn() ? 3 : 1) + (this.handler.banned() ? 7 : 0)
				+ this.handler.mode() * 19 + this.handler.itemSlot();
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		boolean item = this.handler.mode() == 1;
		this.backgroundHeight = item ? 252 : 188;
		this.x = (this.width - this.backgroundWidth) / 2;
		this.y = (this.height - this.backgroundHeight) / 2;
		this.clearChildren();
		int x = this.x + 12;
		int w = this.backgroundWidth - 24;
		this.addDrawableChild(ButtonWidget.builder(Text.literal(item ? "Деньги" : "▶ Деньги"),
						b -> click(CasinoPlayScreenHandler.MODE_MONEY))
				.dimensions(x, this.y + 22, w / 2 - 4, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(item ? "▶ Предмет" : "Предмет"),
						b -> click(CasinoPlayScreenHandler.MODE_ITEM))
				.dimensions(x + w / 2 + 4, this.y + 22, w / 2 - 4, 16).build());
		if (!item) {
			int y = this.y + 86;
			btn("−1к", CasinoPlayScreenHandler.MINUS_1000, x, y, 36);
			btn("−100", CasinoPlayScreenHandler.MINUS_100, x + 38, y, 40);
			btn("−", CasinoPlayScreenHandler.MINUS_1, x + 80, y, 28);
			btn("+", CasinoPlayScreenHandler.PLUS_1, x + 110, y, 28);
			btn("+100", CasinoPlayScreenHandler.PLUS_100, x + 140, y, 40);
			btn("+1к", CasinoPlayScreenHandler.PLUS_1000, x + 182, y, 34);
			int q = y + 20;
			btn(minLabel(), CasinoPlayScreenHandler.SET_1, x, q, 40);
			btn("×2", CasinoPlayScreenHandler.SET_10, x + 44, q, 36);
			btn("10к", CasinoPlayScreenHandler.SET_100, x + 84, q, 40);
			btn("100к", CasinoPlayScreenHandler.SET_100K, x + 128, q, 44);
			btn("Всё", CasinoPlayScreenHandler.SET_ALL, x + 176, q, 40);
		}
		String play = this.handler.banned() ? "Запрещено" : (this.handler.casinoOn() ? "Играть" : "Выключено");
		int playY = item ? this.y + 76 : this.y + 130;
		this.addDrawableChild(ButtonWidget.builder(Text.literal(play), b -> click(CasinoPlayScreenHandler.PLAY))
				.dimensions(x, playY, w, 18).build());
	}

	private static String minLabel() {
		int min = CasinoService.minBet();
		return min <= 0 ? "0" : String.valueOf(min);
	}

	private void btn(String text, int id, int x, int y, int w) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> click(id)).dimensions(x, y, w, 16).build());
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
		if (this.handler.mode() == 1) {
			for (Slot slot : this.handler.slots) {
				if (slot.getIndex() == this.handler.itemSlot() && slot.x > -1000) {
					int sx = this.x + slot.x;
					int sy = this.y + slot.y;
					context.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
				}
			}
			this.drawMouseoverTooltip(context, mouseX, mouseY);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
		if (this.handler.mode() == 1) {
			VanillaInventoryGui.drawPlayerInventoryOnly(context,
					this.x + CasinoPlayScreenHandler.INV_GUI_X,
					this.y + CasinoPlayScreenHandler.INV_GUI_Y, 176);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Казино", 8, 6, this.backgroundWidth - 16);
		if (this.handler.mode() == 1) {
			AcTheme.clippedCentered(this.textRenderer, context, "Клик по предмету — ставка",
					8, 42, this.backgroundWidth - 16, AcTheme.MUTED);
			String pick = this.handler.itemSlot() < 0 ? "предмет не выбран" : ("слот " + this.handler.itemSlot());
			AcTheme.clippedCentered(this.textRenderer, context, pick, 8, 56, this.backgroundWidth - 16, AcTheme.GOLD);
			if (this.handler.lastTenths() > 0) {
				AcTheme.clippedCentered(this.textRenderer, context, "Приз: ×" + this.handler.lastTenths(),
						8, 68, this.backgroundWidth - 16, AcTheme.GOLD);
			}
			return;
		}
		AcTheme.clipped(this.textRenderer, context, "Баланс: " + Money.wc(this.handler.coins()),
				12, 42, 110, AcTheme.TEXT);
		AcTheme.clipped(this.textRenderer, context,
				"Удача: " + String.format(Locale.US, "%.1f", this.handler.luck()),
				118, 42, 110, AcTheme.MUTED);
		AcTheme.clippedCentered(this.textRenderer, context,
				"×" + CasinoService.formatMult(this.handler.minTenths())
						+ " — ×" + CasinoService.formatMult(this.handler.maxTenths()),
				8, 56, this.backgroundWidth - 16, AcTheme.MUTED);
		AcTheme.clippedCentered(this.textRenderer, context, "Ставка: " + Money.format(this.handler.bet()),
				8, 70, this.backgroundWidth - 16, AcTheme.GOLD);
		if (this.handler.lastTenths() >= 0) {
			int net = this.handler.lastNet();
			String result = "×" + CasinoService.formatMult(this.handler.lastTenths())
					+ "  →  " + Money.wc(this.handler.lastPayout()) + "  ("
					+ (net > 0 ? "+" : "") + Money.format(net) + ")";
			int color = net > 0 ? 0x55FF55 : (net == 0 ? AcTheme.GOLD : AcTheme.RED);
			AcTheme.clippedCentered(this.textRenderer, context, result, 8, 154, this.backgroundWidth - 16, color);
		} else {
			AcTheme.clippedCentered(this.textRenderer, context, "Выше множитель — реже выпадает",
					8, 154, this.backgroundWidth - 16, AcTheme.MUTED);
		}
	}
}
