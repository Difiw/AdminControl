package ru.admincontrol.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical, mouse-wheel scrollable stack of widget rows with optional labels. Widgets are owned by the screen;
 * the column only moves them and hides the ones that do not fully fit, so nothing can overlap its neighbours.
 */
public final class CwScrollColumn {
	private static final int LABEL_H = 10, GAP = 3;

	private static final class Row {
		final String label;
		final int height;
		final List<ClickableWidget> widgets;
		int y;

		Row(String label, int widgetHeight, List<ClickableWidget> widgets) {
			this.label = label;
			this.height = widgetHeight + (label == null ? 0 : LABEL_H);
			this.widgets = widgets;
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private int x, y, w, h, scroll;

	public void begin(int x, int y, int w, int h) {
		rows.clear();
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public void add(String label, int widgetHeight, ClickableWidget... widgets) {
		rows.add(new Row(label, widgetHeight, List.of(widgets)));
	}

	private int contentHeight() {
		int total = 0;
		for (Row r : rows) total += r.height + GAP;
		return Math.max(0, total - GAP);
	}

	public void layout() {
		scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - h), scroll));
		int cy = y - scroll;
		for (Row r : rows) {
			r.y = cy;
			boolean visible = cy >= y && cy + r.height <= y + h;
			for (ClickableWidget widget : r.widgets) {
				widget.setY(cy + (r.label == null ? 0 : LABEL_H));
				widget.visible = visible;
			}
			cy += r.height + GAP;
		}
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (mx < x || mx >= x + w || my < y || my >= y + h || contentHeight() <= h) return false;
		scroll -= (int) Math.signum(amount) * 18;
		layout();
		return true;
	}

	public void render(DrawContext c, TextRenderer tr) {
		for (Row r : rows) {
			if (r.label == null || r.y < y || r.y + r.height > y + h) continue;
			c.drawTextWithShadow(tr, tr.trimToWidth(r.label, w), x, r.y, CwUi.TEXT_DIM);
		}
		int content = contentHeight();
		if (content > h) {
			int thumb = Math.max(14, h * h / content);
			int top = y + (h - thumb) * scroll / Math.max(1, content - h);
			c.fill(x + w + 1, y, x + w + 3, y + h, 0x55000000);
			c.fill(x + w + 1, top, x + w + 3, top + thumb, CwUi.BLUE);
		}
	}
}
