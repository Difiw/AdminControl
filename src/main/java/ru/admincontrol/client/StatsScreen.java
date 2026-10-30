package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import ru.admincontrol.screen.StatsScreenHandler;

import java.util.ArrayList;
import java.util.List;

public class StatsScreen extends HandledScreen<StatsScreenHandler> {
	private static final int ROW = 12;
	private static final int VALUE_W = 108;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private final List<Line> lines = new ArrayList<>();

	private static final class Line {
		final String label;
		final OrderedText value;

		Line(String label, OrderedText value) {
			this.label = label;
			this.value = value;
		}
	}

	public StatsScreen(StatsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		int n = Math.max(8, handler.labels.size());
		int full = 36 + n * ROW + 16;
		this.backgroundWidth = 240;
		this.backgroundHeight = Math.min(230, full);
		this.playerInventoryTitleY = 1000;
	}

	private void rebuildLines() {
		this.lines.clear();
		int n = Math.min(this.handler.labels.size(), this.handler.values.size());
		for (int i = 0; i < n; i++) {
			String label = this.handler.labels.get(i);
			String value = this.handler.values.get(i) == null ? "" : this.handler.values.get(i);
			List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(value), VALUE_W);
			if (wrapped.isEmpty()) {
				this.lines.add(new Line(label, Text.literal("").asOrderedText()));
				continue;
			}
			this.lines.add(new Line(label, wrapped.get(0)));
			for (int w = 1; w < wrapped.size(); w++) {
				this.lines.add(new Line("", wrapped.get(w)));
			}
		}
	}

	private int visibleRows() {
		return Math.max(1, (this.backgroundHeight - 48) / ROW);
	}

	private int maxScroll() {
		return Math.max(0, this.lines.size() - visibleRows());
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
		if (maxScroll() <= 0) {
			return false;
		}
		this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount) * 2));
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, barY(), barH(), maxScroll());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && maxScroll() > 0) {
			this.scroll = this.bar.click(mouseY, barY(), barH(), maxScroll());
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
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		rebuildLines();
		AcTheme.titleCentered(this.textRenderer, context, this.handler.title, 8, 7, this.backgroundWidth - 16);
		this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
		int vis = visibleRows();
		int end = Math.min(this.lines.size(), this.scroll + vis);
		for (int i = this.scroll; i < end; i++) {
			int y = 28 + (i - this.scroll) * ROW;
			if ((i - this.scroll) % 2 == 0) {
				context.fill(8, y - 1, this.backgroundWidth - 16, y + ROW - 2, AcTheme.ROW);
			}
			Line line = this.lines.get(i);
			int labelW = this.backgroundWidth - 28 - VALUE_W;
			if (!line.label.isBlank()) {
				AcTheme.clipped(this.textRenderer, context, line.label, 12, y, labelW, AcTheme.NAME);
			}
			context.drawText(this.textRenderer, line.value, this.backgroundWidth - 16 - VALUE_W, y, AcTheme.SCORE, false);
		}
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, barH(), maxScroll(), vis, this.scroll);
		}
	}
}
