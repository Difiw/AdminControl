package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.screen.HelpScreenHandler;
import ru.admincontrol.util.HelpLists;

import java.util.ArrayList;
import java.util.List;

public class HelpScreen extends HandledScreen<HelpScreenHandler> {
	private static final int LINE = 11;
	private static final int FILTER_W = 110;
	private static final int FILTER_H = 16;
	private final AcScrollBar bar = new AcScrollBar();
	private final AcDropList filters = new AcDropList();
	private TextFieldWidget search;
	private int scroll;
	private String filter = HelpLists.ALL;

	private static final class Row {
		OrderedText text;
		boolean first;
	}

	public HelpScreen(HelpScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 400;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
		this.titleY = 8;
	}

	private String query() {
		return this.search == null ? this.handler.initialQuery : this.search.getText();
	}

	private List<HelpLists.Entry> shown() {
		List<HelpLists.Entry> out = new ArrayList<>();
		for (HelpLists.Entry entry : this.handler.entries) {
			if (HelpLists.matches(entry, query(), this.filter)) {
				out.add(entry);
			}
		}
		return out;
	}

	private List<String> filterLabels() {
		List<String> labels = HelpLists.filterNames(this.handler.entries);
		List<String> out = new ArrayList<>(labels.size());
		for (String name : labels) {
			out.add((name.equalsIgnoreCase(this.filter) ? "● " : "○ ") + name);
		}
		return out;
	}

	private int logTop() {
		return 58;
	}

	private int visibleLines() {
		return Math.max(1, (this.backgroundHeight - logTop() - 12) / LINE);
	}

	private int maxScroll(int total) {
		return Math.max(0, total - visibleLines());
	}

	private int barX() {
		return this.x + this.backgroundWidth - 12;
	}

	private int barY() {
		return this.y + logTop();
	}

	private int barH() {
		return visibleLines() * LINE;
	}

	private void placeFilters() {
		this.filters.x = this.x + this.backgroundWidth - 16 - FILTER_W;
		this.filters.y = this.y + 24 + FILTER_H;
		this.filters.w = FILTER_W;
	}

	private List<Row> wrapped() {
		List<Row> rows = new ArrayList<>();
		int width = this.backgroundWidth - 48;
		for (HelpLists.Entry entry : shown()) {
			List<OrderedText> parts = this.textRenderer.wrapLines(Text.literal(entry.line()), width);
			for (int i = 0; i < parts.size(); i++) {
				Row row = new Row();
				row.text = parts.get(i);
				row.first = i == 0;
				rows.add(row);
			}
		}
		return rows;
	}

	@Override
	protected void init() {
		super.init();
		int searchW = this.backgroundWidth - 36 - FILTER_W;
		this.search = new TextFieldWidget(this.textRenderer, this.x + 10, this.y + 24, searchW, FILTER_H,
				Text.empty());
		this.search.setMaxLength(48);
		String resolved = HelpLists.resolveFilter(this.handler.entries, this.handler.initialQuery);
		if (resolved != null) {
			this.filter = resolved;
			this.search.setText("");
		} else {
			this.search.setText(this.handler.initialQuery);
		}
		AcHint.apply(this.search, "Поиск команды или описания...", text -> this.scroll = 0);
		this.addDrawableChild(this.search);
		this.setInitialFocus(this.search);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Фильтр"), b -> {
			if (this.filters.open) {
				this.filters.open = false;
			} else {
				this.filters.open = true;
				this.filters.scroll = 0;
				placeFilters();
			}
		}).dimensions(this.x + this.backgroundWidth - 16 - FILTER_W, this.y + 24, FILTER_W, FILTER_H).build());
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.search != null && this.search.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				this.search.setFocused(false);
				return true;
			}
			if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
			if (this.search.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.search != null && this.search.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.filters.open && this.filters.inPanel(mouseX, mouseY)) {
			this.filters.wheel(amount, filterLabels().size());
			return true;
		}
		int total = wrapped().size();
		this.scroll = Math.max(0, Math.min(maxScroll(total), this.scroll - (int) Math.signum(amount) * 3));
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.filters.open) {
			placeFilters();
			int total = filterLabels().size();
			if (this.filters.inBar(mouseX, mouseY) && this.filters.maxScroll(total) > 0) {
				this.filters.beginDrag(mouseY, total);
				return true;
			}
			int index = this.filters.indexAt(mouseX, mouseY, total);
			if (index >= 0) {
				List<String> names = HelpLists.filterNames(this.handler.entries);
				if (index < names.size()) {
					this.filter = names.get(index);
					this.scroll = 0;
				}
				this.filters.open = false;
				return true;
			}
			this.filters.open = false;
			return true;
		}
		int total = wrapped().size();
		int max = maxScroll(total);
		if (max > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, barY(), barH(), max);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.filters.open && this.filters.dragging) {
			this.filters.drag(mouseY, filterLabels().size());
			return true;
		}
		int max = maxScroll(wrapped().size());
		if (this.bar.dragging && max > 0) {
			this.scroll = this.bar.click(mouseY, barY(), barH(), max);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.bar.dragging = false;
		this.filters.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.search != null) {
			AcHint.tick(this.search, "Поиск команды или описания...");
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		if (this.filters.open) {
			placeFilters();
			List<String> labels = filterLabels();
			this.filters.scroll = Math.max(0, Math.min(this.filters.maxScroll(labels.size()), this.filters.scroll));
			this.filters.draw(context, labels.size(), AcDropList.ROWS);
			int textW = this.filters.w - AcScrollBar.WIDTH - 6;
			int end = Math.min(labels.size(), this.filters.scroll + AcDropList.ROWS);
			for (int i = this.filters.scroll; i < end; i++) {
				int row = i - this.filters.scroll;
				int ry = this.filters.y + row * AcDropList.ROW_H;
				context.fill(this.filters.x + 1, ry, this.filters.x + this.filters.w - AcScrollBar.WIDTH - 1,
						ry + AcDropList.ROW_H - 1, (row % 2 == 0) ? 0xAA202020 : 0xAA141414);
				List<OrderedText> parts = this.textRenderer.wrapLines(Text.literal(labels.get(i)), textW);
				if (!parts.isEmpty()) {
					context.drawText(this.textRenderer, parts.get(0), this.filters.x + 4, ry + 4, AcTheme.TEXT, false);
				}
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, this.handler.title, 8, 6, this.backgroundWidth - 16);
		int top = logTop();
		int found = shown().size();
		String info = this.filter + " · " + found;
		if (!this.handler.subtitle.isBlank()) {
			info = this.handler.subtitle + "  ·  " + info;
		}
		context.drawText(this.textRenderer, info, 12, 44, AcTheme.MUTED, false);
		List<Row> rows = wrapped();
		if (rows.isEmpty()) {
			context.drawText(this.textRenderer, "Ничего не найдено.", 14, top, AcTheme.MUTED, false);
			return;
		}
		this.scroll = Math.max(0, Math.min(maxScroll(rows.size()), this.scroll));
		int vis = visibleLines();
		int end = Math.min(rows.size(), this.scroll + vis);
		for (int i = this.scroll; i < end; i++) {
			int rowY = top + (i - this.scroll) * LINE;
			if ((i - this.scroll) % 2 == 0) {
				context.fill(10, rowY - 1, this.backgroundWidth - 16, rowY + LINE - 2, AcTheme.ROW);
			}
			context.drawText(this.textRenderer, rows.get(i).text, 14, rowY, AcTheme.TEXT, false);
		}
		int max = maxScroll(rows.size());
		if (max > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, top, barH(), max, vis, this.scroll);
		}
	}
}
