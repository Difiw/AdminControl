package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.AplayerScreenHandler;
import ru.admincontrol.util.AplayerActions;

public class AplayerScreen extends HandledScreen<AplayerScreenHandler> {
	private static final int ROW = 20;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private String promptId;
	private TextFieldWidget field;

	public AplayerScreen(AplayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 250;
		this.backgroundHeight = 220;
		this.playerInventoryTitleY = 1000;
	}

	private int visibleRows() {
		return 8;
	}

	private int maxScroll() {
		return Math.max(0, this.handler.actions().size() - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		this.field = null;
		if (this.promptId != null) {
			AplayerActions.Action action = AplayerActions.find(this.handler.actions(), this.promptId);
			String hint = action == null || action.hint == null ? "" : action.hint;
			this.field = new TextFieldWidget(this.textRenderer, this.x + 12, this.y + 88, this.backgroundWidth - 24, 18,
					Text.empty());
			this.field.setMaxLength(200);
			AcHint.apply(this.field, hint.isBlank() ? "Введите значение" : hint);
			this.addDrawableChild(this.field);
			this.setInitialFocus(this.field);
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> {
				this.promptId = null;
				rebuild();
			}).dimensions(this.x + 12, this.y + 180, 108, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Выполнить"), b -> run(this.promptId,
					this.field.getText())).dimensions(this.x + 130, this.y + 180, 108, 18).build());
			return;
		}
		var actions = this.handler.actions();
		int vis = visibleRows();
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= actions.size()) {
				break;
			}
			AplayerActions.Action action = actions.get(index);
			int y = this.y + 28 + i * ROW;
			this.addDrawableChild(ButtonWidget.builder(Text.literal(action.label), b -> pick(action))
					.dimensions(this.x + 8, y, this.backgroundWidth - 28, 18).build());
		}
	}

	private void pick(AplayerActions.Action action) {
		if (action.needsExtra()) {
			this.promptId = action.id;
			rebuild();
			return;
		}
		run(action.id, "");
	}

	private void run(String id, String extra) {
		if (this.client == null || this.client.getNetworkHandler() == null) {
			return;
		}
		var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeString(this.handler.nick(), 16);
		buf.writeString(id == null ? "" : id, 32);
		buf.writeString(extra == null ? "" : extra, 200);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ModPackets.APLAYER_RUN, buf);
	}

	private int barX() {
		return this.x + this.backgroundWidth - 12;
	}

	private int barY() {
		return this.y + 28;
	}

	private int barH() {
		return visibleRows() * ROW;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.promptId != null) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.promptId == null && maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, barY(), barH(), maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.promptId == null && this.bar.dragging && maxScroll() > 0) {
			int next = this.bar.click(mouseY, barY(), barH(), maxScroll());
			if (next != this.scroll) {
				this.scroll = next;
				rebuild();
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.bar.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.field != null && this.promptId != null) {
			if (keyCode == 256) {
				this.promptId = null;
				rebuild();
				return true;
			}
			if (keyCode == 257 || keyCode == 335) {
				run(this.promptId, this.field.getText());
				return true;
			}
			if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
			if (this.field.keyPressed(keyCode, scanCode, modifiers) || this.field.isFocused()) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.field != null && this.field.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.field != null) {
			AplayerActions.Action action = AplayerActions.find(this.handler.actions(), this.promptId);
			String hint = action == null || action.hint == null || action.hint.isBlank() ? "Введите значение" : action.hint;
			AcHint.tick(this.field, hint);
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Игрок  " + this.handler.nick(), 8, 6, this.backgroundWidth - 16);
		if (this.promptId != null) {
			AplayerActions.Action action = AplayerActions.find(this.handler.actions(), this.promptId);
			String title = action == null ? this.promptId : action.label;
			context.drawText(this.textRenderer, title, 12, 48, AcTheme.GOLD, false);
			String hint = action == null || action.hint == null ? "" : action.hint;
			context.drawText(this.textRenderer, hint, 12, 66, AcTheme.MUTED, false);
			return;
		}
		if (this.handler.actions().isEmpty()) {
			String empty = "Нет доступных действий";
			context.drawText(this.textRenderer, empty,
					(this.backgroundWidth - this.textRenderer.getWidth(empty))  / 2, 80, AcTheme.MUTED, false);
		}
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
