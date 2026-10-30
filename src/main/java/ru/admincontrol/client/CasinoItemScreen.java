package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.CasinoItemScreenHandler;

public class CasinoItemScreen extends HandledScreen<CasinoItemScreenHandler> {
	private TextFieldWidget symbols;
	private TextFieldWidget specials;
	private int signature = Integer.MIN_VALUE;

	public CasinoItemScreen(CasinoItemScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 250;
		this.backgroundHeight = 236;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		this.symbols = new TextFieldWidget(this.textRenderer, this.x + 12, this.y + 36, 226, 16, Text.empty());
		this.symbols.setMaxLength(256);
		this.symbols.setText(this.handler.symbols);
		AcHint.apply(this.symbols, "Символы через запятую (20)");
		this.addDrawableChild(this.symbols);
		this.specials = new TextFieldWidget(this.textRenderer, this.x + 12, this.y + 70, 226, 16, Text.empty());
		this.specials.setMaxLength(256);
		this.specials.setText(this.handler.specials);
		AcHint.apply(this.specials, "Особые: 777:шанс:приз;888:1:12");
		this.addDrawableChild(this.specials);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить тексты"), b -> saveTexts())
				.dimensions(this.x + 12, this.y + 90, 226, 16).build());
		pair("2 одинаковых, шанс %: " + this.handler.pairChance(), CasinoItemScreenHandler.PAIR_M, CasinoItemScreenHandler.PAIR_P, 108);
		pair("3 одинаковых, шанс %: " + this.handler.tripleChance(), CasinoItemScreenHandler.TRIPLE_M, CasinoItemScreenHandler.TRIPLE_P, 126);
		pair("Джекпот, шанс %: " + this.handler.jackChance(), CasinoItemScreenHandler.JACK_M, CasinoItemScreenHandler.JACK_P, 144);
		pair("Приз за 2: ×" + this.handler.pairPrize(), CasinoItemScreenHandler.PAIR_PRIZE_M, CasinoItemScreenHandler.PAIR_PRIZE_P, 162);
		pair("Приз за 3: ×" + this.handler.triplePrize(), CasinoItemScreenHandler.TRIPLE_PRIZE_M, CasinoItemScreenHandler.TRIPLE_PRIZE_P, 180);
		pair("Приз 777: ×" + this.handler.jackPrize(), CasinoItemScreenHandler.JACK_PRIZE_M, CasinoItemScreenHandler.JACK_PRIZE_P, 198);
	}

	private void pair(String label, int minus, int plus, int yOff) {
		int y = this.y + yOff;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(minus)).dimensions(this.x + 12, y, 18, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
		}).dimensions(this.x + 32, y, 186, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(plus)).dimensions(this.x + 220, y, 18, 16).build());
	}

	private void saveTexts() {
		send("casino_symbols", this.symbols.getText());
		send("casino_specials", this.specials.getText());
	}

	private void send(String kind, String value) {
		var buf = PacketByteBufs.create();
		buf.writeString(kind, 32);
		buf.writeString(value == null ? "" : value, 256);
		ClientPlayNetworking.send(ModPackets.UI_VALUE, buf);
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int sig = this.handler.pairChance() + this.handler.tripleChance() * 3 + this.handler.jackChance() * 5
				+ this.handler.pairPrize() * 7 + this.handler.triplePrize() * 11 + this.handler.jackPrize() * 13;
		if (sig != this.signature) {
			this.signature = sig;
			String s = this.symbols == null ? "" : this.symbols.getText();
			String sp = this.specials == null ? "" : this.specials.getText();
			rebuild();
			if (this.symbols != null) {
				this.symbols.setText(s);
			}
			if (this.specials != null) {
				this.specials.setText(sp);
			}
		}
		if (this.symbols != null) {
			AcHint.tick(this.symbols, "Символы через запятую (20)");
		}
		if (this.specials != null) {
			AcHint.tick(this.specials, "Особые: 777:шанс:приз;888:1:12");
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Предметное казино", 8, 6, this.backgroundWidth - 16);
		AcTheme.clipped(this.textRenderer, context, "Список символов", 12, 24, 220, AcTheme.MUTED);
		AcTheme.clipped(this.textRenderer, context, "Исключения (символ:шанс%:приз)", 12, 58, 220, AcTheme.MUTED);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.symbols != null && this.symbols.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (this.specials != null && this.specials.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.symbols != null && this.symbols.charTyped(chr, modifiers)) {
			return true;
		}
		if (this.specials != null && this.specials.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}
}
