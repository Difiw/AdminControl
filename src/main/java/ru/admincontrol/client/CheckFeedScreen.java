package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.screen.CheckFeedScreenHandler;

public class CheckFeedScreen extends HandledScreen<CheckFeedScreenHandler> {
	private static final int ROW = 16;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private int signature = -1;

	public CheckFeedScreen(CheckFeedScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 250;
		this.backgroundHeight = 248;
		this.playerInventoryTitleY = 1000;
	}

	private int visibleRows() {
		return 10;
	}

	private int maxScroll() {
		return Math.max(0, ActionKind.values().length - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = 0;
		for (int i = 0; i < ActionKind.values().length; i++) {
			sig = sig * 3 + (this.handler.enabled(i) ? 1 : 2);
		}
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Все вкл"), b -> click(CheckFeedScreenHandler.ALL_ON))
				.dimensions(this.x + 10, this.y + 24, 110, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Все выкл"), b -> click(CheckFeedScreenHandler.ALL_OFF))
				.dimensions(this.x + 130, this.y + 24, 110, 16).build());
		ActionKind[] kinds = ActionKind.values();
		int vis = visibleRows();
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= kinds.length) {
				break;
			}
			ActionKind kind = kinds[index];
			boolean on = this.handler.enabled(index);
			String mark = on ? "§aВКЛ" : "§cВЫКЛ";
			this.addDrawableChild(ButtonWidget.builder(Text.literal(mark + " §f" + kind.label), b -> click(kind.ordinal()))
					.dimensions(this.x + 10, this.y + 44 + i * ROW, this.backgroundWidth - 32, 14).build());
		}
	}

	private int barX() {
		return this.x + this.backgroundWidth - 12;
	}

	private int barY() {
		return this.y + 44;
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
		this.syncUi();
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
		AcTheme.titleCentered(this.textRenderer, context, "Логи в чат", 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 44, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
