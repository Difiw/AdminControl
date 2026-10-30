package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;

public final class AcDropList {
	public static final int ROWS = 5;
	public static final int ROW_H = 16;
	public boolean open;
	public int scroll;
	public boolean dragging;
	public final AcScrollBar bar = new AcScrollBar();
	public int x;
	public int y;
	public int w;

	public int h() {
		return ROWS * ROW_H;
	}

	public int maxScroll(int total) {
		return Math.max(0, total - ROWS);
	}

	public boolean inPanel(double mx, double my) {
		return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + h();
	}

	public boolean inBar(double mx, double my) {
		return this.bar.inTrack(mx, my, barX(), this.y, h());
	}

	public int barX() {
		return this.x + this.w - AcScrollBar.WIDTH;
	}

	public int rowAt(double my) {
		if (my < this.y || my >= this.y + h()) {
			return -1;
		}
		return (int) ((my - this.y) / ROW_H);
	}

	public int indexAt(double mx, double my, int total) {
		if (!inPanel(mx, my) || inBar(mx, my)) {
			return -1;
		}
		int row = rowAt(my);
		if (row < 0) {
			return -1;
		}
		int index = this.scroll + row;
		return index >= 0 && index < total ? index : -1;
	}

	public void wheel(double amount, int total) {
		this.scroll = Math.max(0, Math.min(maxScroll(total), this.scroll - (int) Math.signum(amount)));
	}

	public void beginDrag(double my, int total) {
		this.dragging = true;
		this.scroll = this.bar.click(my, this.y, h(), maxScroll(total));
	}

	public void drag(double my, int total) {
		if (this.dragging) {
			this.scroll = this.bar.click(my, this.y, h(), maxScroll(total));
		}
	}

	public void draw(DrawContext context, int total, int visibleHint) {
		AcTheme.panel(context, this.x, this.y, this.w, h(), 0);
		int max = maxScroll(total);
		if (max > 0) {
			this.bar.draw(context, barX(), this.y, h(), max, ROWS, this.scroll);
		}
	}
}
