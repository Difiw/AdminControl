package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.ChCommandsScreenHandler;

public class ChCommandsScreen extends HandledScreen<ChCommandsScreenHandler> {
	private static final int ROW = 20;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;

	public ChCommandsScreen(ChCommandsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 250;
		this.backgroundHeight = 220;
		this.playerInventoryTitleY = 1000;
	}

	private int visibleRows() {
		return 8;
	}

	private int maxScroll() {
		return Math.max(0, this.handler.entries().size() - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		var entries = this.handler.entries();
		int vis = visibleRows();
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= entries.size()) {
				break;
			}
			ChCommandsScreenHandler.Entry entry = entries.get(index);
			int y = this.y + 28 + i * ROW;
			String scope = entry.everyone ? "все" : "игроки";
			this.addDrawableChild(ButtonWidget.builder(
							Text.literal("/" + entry.name + "  (" + scope + ")"), b -> {
							})
					.dimensions(this.x + 8, y, this.backgroundWidth - 78, 18).build());
			int enableId = index;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("§aВкл"), b -> click(enableId))
					.dimensions(this.x + this.backgroundWidth - 66, y, 46, 18).build());
		}
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

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, barY(), barH(), maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && maxScroll() > 0) {
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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
		AcTheme.titleCentered(this.textRenderer, context, "Выключенные команды", 8, 6, this.backgroundWidth - 16);
		if (this.handler.entries().isEmpty()) {
			String empty = "Нет выключенных команд";
			context.drawText(this.textRenderer, empty, (this.backgroundWidth - this.textRenderer.getWidth(empty))  / 2, 80, AcTheme.MUTED, false);
		}
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
