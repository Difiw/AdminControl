package ru.admincontrol.emi.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Настройки CW EMI. Ползунки и кнопки нарисованы и обрабатываются самим экраном (перетаскивание мышью работает
 * непрерывно), серверные запреты видимости эти настройки не меняют.
 */
public final class CwEmiSettingsScreen extends Screen {
	private static final int ROW_W = 240, ROW_H = 20, GAP = 4;

	private static final class Row {
		Supplier<Text> label;
		Runnable action;
		int min, max;
		java.util.function.IntSupplier get;
		IntConsumer set;

		boolean slider() {
			return get != null;
		}
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private int scroll;
	private Row dragging;
	private int listTop, visibleCount;

	public CwEmiSettingsScreen(Screen parent) {
		super(Text.translatable("cw_emi.settings"));
		this.parent = parent;
	}

	private static Text onOff(boolean v) {
		return Text.translatable(v ? "options.on" : "options.off");
	}

	private void slider(int min, int max, java.util.function.IntSupplier get, IntConsumer set, IntFunction<Text> label) {
		Row r = new Row();
		r.min = min;
		r.max = max;
		r.get = get;
		r.set = set;
		r.label = () -> label.apply(get.getAsInt());
		rows.add(r);
	}

	private void toggle(Supplier<Text> label, Runnable action) {
		Row r = new Row();
		r.label = label;
		r.action = action;
		rows.add(r);
	}

	@Override
	protected void init() {
		rows.clear();
		CwEmiConfig c = CwEmiConfig.get();
		slider(30, 100, () -> c.opacity, v -> c.opacity = v, v -> Text.translatable("cw_emi.opt.opacity").append(": " + v + "%"));
		slider(12, 28, () -> c.iconSize, v -> c.iconSize = v, v -> Text.translatable("cw_emi.opt.icon").append(": " + v + " px"));
		slider(0, 14, () -> c.maxColumns, v -> c.maxColumns = v, v -> Text.translatable("cw_emi.opt.width").append(": ")
				.append(v == 0 ? Text.translatable("cw_emi.auto") : Text.literal(v + " " + Text.translatable("cw_emi.cols").getString())));
		slider(40, 100, () -> c.heightPercent, v -> c.heightPercent = v, v -> Text.translatable("cw_emi.opt.height").append(": " + v + "%"));
		toggle(() -> Text.translatable("cw_emi.opt.recipe_panel").append(": ").append(onOff(c.panelInRecipe)), () -> c.panelInRecipe = !c.panelInRecipe);
		toggle(() -> Text.translatable("cw_emi.opt.side").append(": ").append(Text.translatable(c.left ? "cw_emi.left" : "cw_emi.right")), () -> c.left = !c.left);
		toggle(() -> Text.translatable("cw_emi.opt.density").append(": ").append(Text.translatable("cw_emi.density." + c.density)), () -> c.density = (c.density + 1) % 3);
		toggle(() -> Text.translatable("cw_emi.opt.mode").append(": ").append(Text.translatable(c.compact ? "cw_emi.compact" : "cw_emi.expanded")), () -> c.compact = !c.compact);
		toggle(() -> Text.translatable("cw_emi.opt.tooltips").append(": ").append(Text.translatable("cw_emi.tooltips." + c.tooltips)), () -> c.tooltips = (c.tooltips + 1) % 3);
		toggle(() -> Text.translatable("cw_emi.opt.modnames").append(": ").append(onOff(c.showModNames)), () -> c.showModNames = !c.showModNames);
		toggle(() -> Text.translatable("cw_emi.opt.history").append(": ").append(onOff(c.historyEnabled)), () -> {
			c.historyEnabled = !c.historyEnabled;
			if (!c.historyEnabled) {
				CwEmiClient.clearHistory();
			}
		});
		toggle(() -> Text.translatable("cw_emi.opt.favorites").append(": ").append(onOff(c.favoritesEnabled)), () -> c.favoritesEnabled = !c.favoritesEnabled);
		toggle(() -> Text.translatable("cw_emi.sort").append(": ").append(Text.translatable("cw_emi.sort." + c.sort)), () -> c.sort = (c.sort + 1) % 4);
		toggle(() -> Text.translatable("cw_emi.opt.visible").append(": ").append(onOff(c.visible)), () -> c.visible = !c.visible);
		toggle(() -> Text.translatable("cw_emi.opt.clear_history"), CwEmiClient::clearHistory);
		listTop = 36;
		visibleCount = Math.max(1, (this.height - 36 - 34) / (ROW_H + GAP));
	}

	private int rowX() {
		return this.width / 2 - ROW_W / 2;
	}

	private int rowY(int index) {
		return listTop + (index - scroll) * (ROW_H + GAP);
	}

	private void dragTo(Row r, double mx) {
		double f = Math.max(0, Math.min(1, (mx - rowX() - 9) / (ROW_W - 18)));
		int v = r.min + (int) Math.round(f * (r.max - r.min));
		if (v != r.get.getAsInt()) {
			r.set.accept(v);
			CwEmiClient.version++;
		}
	}

	private boolean inDone(double mx, double my) {
		return mx >= this.width / 2 + 2 && mx < this.width / 2 + 110 && my >= this.height - 26 && my < this.height - 6;
	}

	private boolean inReset(double mx, double my) {
		return mx >= this.width / 2 - 110 && mx < this.width / 2 - 2 && my >= this.height - 26 && my < this.height - 6;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (inDone(mx, my)) {
			close();
			return true;
		}
		if (inReset(mx, my)) {
			CwEmiConfig.reset();
			CwEmiClient.version++;
			this.client.setScreen(new CwEmiSettingsScreen(parent));
			return true;
		}
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleCount); i++) {
			int y = rowY(i);
			if (mx >= rowX() && mx < rowX() + ROW_W && my >= y && my < y + ROW_H) {
				Row r = rows.get(i);
				if (r.slider()) {
					dragging = r;
					dragTo(r, mx);
				} else {
					r.action.run();
					CwEmiConfig.save();
					CwEmiClient.version++;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging != null) {
			dragTo(dragging, mx);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging != null) {
			dragging = null;
			CwEmiConfig.save();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleCount), scroll - (int) Math.signum(amount)));
		return true;
	}

	@Override
	public void close() {
		CwEmiConfig.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		this.renderBackground(c);
		CwEmiTheme th = CwEmiTheme.current();
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
		c.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("cw_emi.keys_hint"), this.width / 2, 22, 0xFFA0A0A0);
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleCount); i++) {
			Row r = rows.get(i);
			int x = rowX(), y = rowY(i);
			boolean hv = mx >= x && mx < x + ROW_W && my >= y && my < y + ROW_H;
			th.button(c, x, y, ROW_W, ROW_H, hv || r == dragging, true);
			if (r.slider()) {
				double f = (r.get.getAsInt() - r.min) / (double) (r.max - r.min);
				int hx = x + 4 + (int) (f * (ROW_W - 18));
				c.fill(x + 4, y + ROW_H - 5, x + ROW_W - 4, y + ROW_H - 3, th.vanilla ? 0xFF373737 : 0xFF0F1418);
				c.fill(x + 4, y + ROW_H - 5, hx + 5, y + ROW_H - 3, th.vanilla ? 0xFFC6C6C6 : th.accent);
				th.button(c, hx, y + 2, 10, ROW_H - 4, hv || r == dragging, true);
			}
			Text label = r.label.get();
			c.drawText(this.textRenderer, label, x + (ROW_W - this.textRenderer.getWidth(label)) / 2, y + (r.slider() ? 3 : 6), th.buttonText(true), false);
		}
		for (int k = 0; k < 2; k++) {
			int x = k == 0 ? this.width / 2 - 110 : this.width / 2 + 2;
			boolean hv = k == 0 ? inReset(mx, my) : inDone(mx, my);
			th.button(c, x, this.height - 26, 108, 20, hv, true);
			Text t = Text.translatable(k == 0 ? "cw_emi.reset" : "gui.done");
			c.drawText(this.textRenderer, t, x + (108 - this.textRenderer.getWidth(t)) / 2, this.height - 20, th.buttonText(true), false);
		}
	}
}
