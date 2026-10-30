package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.screen.LogScreenHandler;
import ru.admincontrol.util.ActivityLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LogScreen extends HandledScreen<LogScreenHandler> {
	private static final int LINE = 11;
	private static final int FILTER_H = 16;
	private static final String SEARCH_HINT = "Поиск: время, действие, описание...";
	private final AcScrollBar bar = new AcScrollBar();
	private final AcDropList times = new AcDropList();
	private final AcDropList kinds = new AcDropList();
	private TextFieldWidget search;
	private ButtonWidget timeBtn;
	private ButtonWidget kindBtn;
	private int scroll;
	private ActivityLog.TimeFilter time = ActivityLog.TimeFilter.ALL;
	private boolean noFilter = true;
	private boolean pendingNoFilter = true;
	private final boolean[] kindOn = new boolean[ActionKind.values().length];
	private final boolean[] pendingKind = new boolean[ActionKind.values().length];

	private static final class Row {
		OrderedText text;
		boolean ok;
		boolean first;
	}

	public LogScreen(LogScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 400;
		this.backgroundHeight = 248;
		this.playerInventoryTitleY = 1000;
		this.titleY = 8;
	}

	private String query() {
		return this.search == null ? this.handler.initialQuery : this.search.getText();
	}

	private List<ActionKind> presentKinds() {
		Set<ActionKind> set = new LinkedHashSet<>();
		for (ActivityLog.Line line : this.handler.lines) {
			if (line != null && line.kind != null) {
				set.add(line.kind);
			}
		}
		List<ActionKind> list = new ArrayList<>(set);
		list.sort((a, b) -> Integer.compare(kindRank(a), kindRank(b)));
		return list;
	}

	private static int kindRank(ActionKind kind) {
		if (kind == ActionKind.ANTICHEAT) {
			return 0;
		}
		if (kind == ActionKind.MONEY) {
			return 1;
		}
		if (kind == ActionKind.POWER) {
			return 2;
		}
		if (kind == ActionKind.LUCK) {
			return 3;
		}
		if (kind == ActionKind.CLAN) {
			return 4;
		}
		return 10 + kind.ordinal();
	}

	private boolean shown(ActivityLog.Line line) {
		if (!ActivityLog.matches(line, query()) || !ActivityLog.inTime(line, this.time)) {
			return false;
		}
		if (!this.handler.filters || this.noFilter) {
			return true;
		}
		if (line == null || line.kind == null) {
			return false;
		}
		int i = line.kind.ordinal();
		return i >= 0 && i < this.kindOn.length && this.kindOn[i];
	}

	private int shownCount() {
		int n = 0;
		for (ActivityLog.Line line : this.handler.lines) {
			if (shown(line)) {
				n++;
			}
		}
		return n;
	}

	private List<Row> wrapped() {
		List<Row> rows = new ArrayList<>();
		int width = this.backgroundWidth - 48;
		for (ActivityLog.Line line : this.handler.lines) {
			if (!shown(line)) {
				continue;
			}
			if (line == null || line.text == null || line.text.isEmpty()) {
				Row empty = new Row();
				empty.text = Text.literal(" ").asOrderedText();
				empty.ok = true;
				empty.first = false;
				rows.add(empty);
				continue;
			}
			List<OrderedText> parts = this.textRenderer.wrapLines(Text.literal(line.text), width);
			for (int i = 0; i < parts.size(); i++) {
				Row row = new Row();
				row.text = parts.get(i);
				row.ok = line.ok;
				row.first = i == 0;
				rows.add(row);
			}
		}
		return rows;
	}

	private int searchX() {
		return this.x + 10;
	}

	private int searchY() {
		return this.y + 24;
	}

	private int searchW() {
		return this.backgroundWidth - 20;
	}

	private int filterY() {
		return this.y + 44;
	}

	private int timeX() {
		return this.x + 10;
	}

	private int kindX() {
		return this.x + 204;
	}

	private int logTop() {
		return this.handler.filters ? 72 : 52;
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

	private List<String> timeLabels() {
		List<String> labels = new ArrayList<>();
		for (ActivityLog.TimeFilter filter : ActivityLog.TimeFilter.values()) {
			labels.add((filter == this.time ? "● " : "○ ") + filter.label);
		}
		return labels;
	}

	private List<String> kindLabels() {
		List<String> labels = new ArrayList<>();
		labels.add((this.pendingNoFilter ? "☑ " : "☐ ") + "Все действия");
		for (ActionKind kind : presentKinds()) {
			boolean on = this.pendingKind[kind.ordinal()];
			labels.add((on ? "☑ " : "☐ ") + kind.label);
		}
		return labels;
	}

	private void openTimes() {
		this.kinds.open = false;
		this.times.open = !this.times.open;
		this.times.scroll = 0;
		placeTimes();
	}

	private void openKinds() {
		this.times.open = false;
		if (this.kinds.open) {
			applyKinds();
			return;
		}
		this.pendingNoFilter = this.noFilter;
		System.arraycopy(this.kindOn, 0, this.pendingKind, 0, this.kindOn.length);
		this.kinds.open = true;
		this.kinds.scroll = 0;
		placeKinds();
	}

	private void applyKinds() {
		this.kinds.open = false;
		this.kinds.dragging = false;
		this.noFilter = this.pendingNoFilter;
		System.arraycopy(this.pendingKind, 0, this.kindOn, 0, this.kindOn.length);
		if (this.noFilter) {
			Arrays.fill(this.kindOn, false);
		}
		this.scroll = 0;
		refreshButtons();
	}

	private void toggleKind(int index) {
		if (index <= 0) {
			this.pendingNoFilter = true;
			Arrays.fill(this.pendingKind, false);
			return;
		}
		List<ActionKind> list = presentKinds();
		int at = index - 1;
		if (at < 0 || at >= list.size()) {
			return;
		}
		ActionKind kind = list.get(at);
		this.pendingKind[kind.ordinal()] = !this.pendingKind[kind.ordinal()];
		this.pendingNoFilter = true;
		for (boolean on : this.pendingKind) {
			if (on) {
				this.pendingNoFilter = false;
				break;
			}
		}
	}

	private void placeTimes() {
		this.times.x = timeX();
		this.times.y = filterY() + FILTER_H;
		this.times.w = 186;
	}

	private void placeKinds() {
		this.kinds.x = kindX();
		this.kinds.y = filterY() + FILTER_H;
		this.kinds.w = 186;
	}

	private void refreshButtons() {
		if (this.timeBtn != null) {
			this.timeBtn.setMessage(Text.literal(this.time.label));
		}
		if (this.kindBtn != null) {
			this.kindBtn.setMessage(Text.literal(this.noFilter ? "Действия" : "Действия ✓"));
		}
	}

	@Override
	protected void init() {
		super.init();
		this.search = new TextFieldWidget(this.textRenderer, searchX(), searchY(), searchW(), FILTER_H, Text.empty());
		this.search.setMaxLength(80);
		this.search.setText(this.handler.initialQuery == null ? "" : this.handler.initialQuery);
		AcHint.apply(this.search, SEARCH_HINT, value -> this.scroll = 0);
		this.addDrawableChild(this.search);
		this.setInitialFocus(this.search);
		if (!this.handler.filters) {
			return;
		}
		this.timeBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.time.label), b -> openTimes())
				.dimensions(timeX(), filterY(), 186, FILTER_H).build());
		this.kindBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Действия"), b -> openKinds())
				.dimensions(kindX(), filterY(), 186, FILTER_H).build());
		refreshButtons();
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
		if (this.times.open && this.times.inPanel(mouseX, mouseY)) {
			this.times.wheel(amount, timeLabels().size());
			return true;
		}
		if (this.kinds.open && this.kinds.inPanel(mouseX, mouseY)) {
			this.kinds.wheel(amount, kindLabels().size());
			return true;
		}
		int total = wrapped().size();
		this.scroll = Math.max(0, Math.min(maxScroll(total), this.scroll - (int) Math.signum(amount) * 3));
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.times.open) {
			placeTimes();
			int total = timeLabels().size();
			if (this.times.inBar(mouseX, mouseY) && this.times.maxScroll(total) > 0) {
				this.times.beginDrag(mouseY, total);
				return true;
			}
			int index = this.times.indexAt(mouseX, mouseY, total);
			if (index >= 0 && index < ActivityLog.TimeFilter.values().length) {
				this.time = ActivityLog.TimeFilter.values()[index];
				this.scroll = 0;
				refreshButtons();
			}
			this.times.open = false;
			return true;
		}
		if (this.kinds.open) {
			placeKinds();
			int total = kindLabels().size();
			if (this.kinds.inBar(mouseX, mouseY) && this.kinds.maxScroll(total) > 0) {
				this.kinds.beginDrag(mouseY, total);
				return true;
			}
			int index = this.kinds.indexAt(mouseX, mouseY, total);
			if (index >= 0) {
				toggleKind(index);
				return true;
			}
			applyKinds();
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
		if (this.times.open && this.times.dragging) {
			this.times.drag(mouseY, timeLabels().size());
			return true;
		}
		if (this.kinds.open && this.kinds.dragging) {
			this.kinds.drag(mouseY, kindLabels().size());
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
		this.times.dragging = false;
		this.kinds.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.search != null) {
			AcHint.tick(this.search, SEARCH_HINT);
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		if (this.times.open) {
			drawDrop(context, this.times, timeLabels());
		}
		if (this.kinds.open) {
			drawDrop(context, this.kinds, kindLabels());
		}
	}

	private void drawDrop(DrawContext context, AcDropList list, List<String> labels) {
		if (list == this.times) {
			placeTimes();
		} else {
			placeKinds();
		}
		list.scroll = Math.max(0, Math.min(list.maxScroll(labels.size()), list.scroll));
		list.draw(context, labels.size(), AcDropList.ROWS);
		int textW = list.w - AcScrollBar.WIDTH - 6;
		int end = Math.min(labels.size(), list.scroll + AcDropList.ROWS);
		for (int i = list.scroll; i < end; i++) {
			int row = i - list.scroll;
			int ry = list.y + row * AcDropList.ROW_H;
			context.fill(list.x + 1, ry, list.x + list.w - AcScrollBar.WIDTH - 1,
					ry + AcDropList.ROW_H - 1, (row % 2 == 0) ? 0xAA202020 : 0xAA141414);
			List<OrderedText> parts = this.textRenderer.wrapLines(Text.literal(labels.get(i)), textW);
			if (!parts.isEmpty()) {
				context.drawText(this.textRenderer, parts.get(0), list.x + 4, ry + 4, AcTheme.TEXT, false);
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, this.handler.filters ? 62 : 44);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, this.handler.title, 8, 6, this.backgroundWidth - 16);
		int found = shownCount();
		int all = this.handler.lines.size();
		int infoY = this.handler.filters ? 62 : 42;
		AcTheme.clipped(this.textRenderer, context, "Найдено " + found + " из " + all, 10, infoY,
				this.backgroundWidth - 24, AcTheme.MUTED);
		List<Row> rows = wrapped();
		int top = logTop();
		if (rows.isEmpty()) {
			context.drawText(this.textRenderer, "Ничего не найдено.", 14, top, AcTheme.MUTED, false);
			return;
		}
		this.scroll = Math.max(0, Math.min(maxScroll(rows.size()), this.scroll));
		int vis = visibleLines();
		int end = Math.min(rows.size(), this.scroll + vis);
		int textX = this.handler.showMarks ? 24 : 14;
		for (int i = this.scroll; i < end; i++) {
			int rowY = top + (i - this.scroll) * LINE;
			Row row = rows.get(i);
			if ((i - this.scroll) % 2 == 0) {
				context.fill(10, rowY - 1, this.backgroundWidth - 16, rowY + LINE - 2, AcTheme.ROW);
			}
			if (this.handler.showMarks && row.first) {
				int color = row.ok ? 0xFF22CC44 : 0xFFCC2222;
				context.fill(14, rowY + 1, 20, rowY + 8, color);
			}
			context.drawText(this.textRenderer, row.text, textX, rowY, AcTheme.TEXT, false);
		}
		int max = maxScroll(rows.size());
		if (max > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, top, barH(), max, vis, this.scroll);
		}
	}
}
