package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.PasswordScreenHandler;

public class PasswordScreen extends HandledScreen<PasswordScreenHandler> {
	private TextFieldWidget oldF;
	private TextFieldWidget newF;
	private TextFieldWidget repF;
	private ButtonWidget apply;

	public PasswordScreen(PasswordScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 240;
		this.backgroundHeight = 160;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.x + 20;
		int w = this.backgroundWidth - 40;
		this.oldF = new TextFieldWidget(this.textRenderer, x, this.y + 40, w, 18, Text.empty());
		this.newF = new TextFieldWidget(this.textRenderer, x, this.y + 70, w, 18, Text.empty());
		this.repF = new TextFieldWidget(this.textRenderer, x, this.y + 100, w, 18, Text.empty());
		this.oldF.setMaxLength(32);
		this.newF.setMaxLength(32);
		this.repF.setMaxLength(32);
		AcHint.apply(this.oldF, "Старый пароль");
		AcHint.apply(this.newF, "Новый пароль");
		AcHint.apply(this.repF, "Повтор пароля");
		this.addDrawableChild(this.oldF);
		this.addDrawableChild(this.newF);
		this.addDrawableChild(this.repF);
		this.apply = this.addDrawableChild(ButtonWidget.builder(Text.literal("Применить"), b -> send())
				.dimensions(x, this.y + 128, w, 20).build());
		this.apply.visible = false;
		this.setInitialFocus(this.oldF);
	}

	private void syncUi() {
		boolean ready = !this.oldF.getText().isBlank() && this.newF.getText().length() >= 3
				&& this.newF.getText().equals(this.repF.getText());
		this.apply.visible = ready;
	}

	private void send() {
		var buf = PacketByteBufs.create();
		buf.writeString(this.oldF.getText(), 32);
		buf.writeString(this.newF.getText(), 32);
		buf.writeString(this.repF.getText(), 32);
		ClientPlayNetworking.send(ModPackets.SET_PASSWORD, buf);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.oldF.keyPressed(keyCode, scanCode, modifiers) || this.newF.keyPressed(keyCode, scanCode, modifiers)
				|| this.repF.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.oldF.charTyped(chr, modifiers) || this.newF.charTyped(chr, modifiers) || this.repF.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.syncUi();
		AcHint.tick(this.oldF, "Старый пароль");
		AcHint.tick(this.newF, "Новый пароль");
		AcHint.tick(this.repF, "Повтор пароля");
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Смена пароля", 8, 6, this.backgroundWidth - 16);
		context.drawText(this.textRenderer, "Старый", 20, 30, AcTheme.MUTED, false);
		context.drawText(this.textRenderer, "Новый", 20, 60, AcTheme.MUTED, false);
		context.drawText(this.textRenderer, "Повтор", 20, 90, AcTheme.MUTED, false);
	}
}
