package ru.admincontrol.emi.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.item.ItemEncryption;
import ru.admincontrol.mixin.client.HandledScreenAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Панель CW EMI (поиск, категории, сетка предметов). Работает в двух режимах: рядом с контейнером (HandledScreen) и
 * «standalone» — у окна рецептов, где панель остаётся справа/слева, чтобы можно было сразу выбирать следующий предмет.
 */
final class CwEmiPanel {
	static final int BTN = 14, PAD = 5;
	private static final ItemStack[] CAT_ICONS = {new ItemStack(Items.CHEST), new ItemStack(Items.GRASS_BLOCK),
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.APPLE),
			new ItemStack(Items.POTION), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.NETHER_STAR),
			new ItemStack(Items.CLOCK), new ItemStack(Items.BOOKSHELF)};

	/** Редактор строки поиска на базе ванильного TextFieldWidget (курсор, выделение, Ctrl+C/V/A), рисуется панелью. */
	static final class SearchField extends TextFieldWidget {
		SearchField(TextRenderer font) {
			super(font, 0, 0, 60, 12, Text.empty());
			this.setDrawsBackground(false);
			this.visible = false;
		}

		void typed(char c) {
			boolean v = this.visible;
			this.visible = true;
			try {
				super.charTyped(c, 0);
			} finally {
				this.visible = v;
			}
		}

		boolean key(int key, int scan, int mods) {
			boolean v = this.visible;
			this.visible = true;
			try {
				return super.keyPressed(key, scan, mods);
			} finally {
				this.visible = v;
			}
		}

		void paint(DrawContext c, int mx, int my) {
			boolean v = this.visible;
			this.visible = true;
			try {
				super.render(c, mx, my, 0);
			} finally {
				this.visible = v;
			}
		}
	}

	final Screen screen;
	final SearchField field;
	private final HandledScreen<?> handled;
	private final HandledScreenAccessor acc;
	private final boolean creative, standalone;
	private final BiConsumer<Item, Boolean> opener;
	boolean shown;
	int px, py, pw, ph, cols, rows, cell, icon, gridX, gridY, gridW;
	private int boxX, boxY, boxW, hideX, hideY, gearX, gearY, totalRows;
	private final List<int[]> catRects = new ArrayList<>();
	private List<CwEmiClient.Entry> items = List.of();
	private boolean dragScroll;
	private double grab;

	private int thumbHeight() {
		int sbH = rows * cell;
		return Math.max(10, sbH * rows / Math.max(1, totalRows));
	}

	private int thumbY() {
		int maxScroll = Math.max(1, totalRows - rows);
		return gridY + (rows * cell - thumbHeight()) * CwEmiClient.scrollRows / maxScroll;
	}

	/** Положение ползунка по текущей позиции мыши: ручку можно зажать и тянуть вверх/вниз. */
	private void dragTo(double my) {
		int track = rows * cell - thumbHeight();
		if (track <= 0 || totalRows <= rows) {
			return;
		}
		double f = Math.max(0, Math.min(1, (my - grab - gridY) / track));
		CwEmiClient.scrollRows = (int) Math.round(f * (totalRows - rows));
	}

	CwEmiPanel(Screen screen, boolean standalone, BiConsumer<Item, Boolean> opener) {
		this.screen = screen;
		this.standalone = standalone;
		this.opener = opener;
		this.handled = screen instanceof HandledScreen<?> h ? h : null;
		this.acc = handled == null ? null : (HandledScreenAccessor) handled;
		this.creative = screen instanceof CreativeInventoryScreen;
		this.field = new SearchField(MinecraftClient.getInstance().textRenderer);
		field.setMaxLength(48);
		field.setText(CwEmiClient.search);
		field.setChangedListener(t -> {
			if (!t.equals(CwEmiClient.search)) {
				CwEmiClient.search = t;
				CwEmiClient.scrollRows = 0;
			}
		});
	}

	static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Ширина панели рядом с окном рецептов: остаётся не меньше minOther пикселей под само окно. */
	static int standaloneWidth(int screenW, int minOther) {
		CwEmiConfig cfg = CwEmiConfig.get();
		int cellSize = cfg.iconSize() + 2 + cfg.gap() * 2;
		int fit = (screenW - minOther - 12 - 2 * PAD - 8) / cellSize;
		int cols = Math.max(2, Math.min(fit, cfg.maxColumns > 0 ? cfg.maxColumns : 6));
		return cols * cellSize + 2 * PAD + 8;
	}

	void layout() {
		CwEmiConfig cfg = CwEmiConfig.get();
		int base = cfg.iconSize() + 2 + cfg.gap() * 2;
		int avail, left;
		if (standalone) {
			avail = standaloneWidth(screen.width, 290) - 2 * PAD - 8;
			left = 0;
		} else {
			int cx = acc.adminControl$getX(), cw = acc.adminControl$getBackgroundWidth();
			avail = (cfg.left ? cx - 6 : screen.width - (cx + cw) - 6) - 2 * PAD - 8;
			left = cx;
		}
		int fit = avail / base;
		shown = cfg.visible && fit >= 2;
		if (!shown) {
			return;
		}
		int want = cfg.maxColumns > 0 ? cfg.maxColumns : 12;
		cell = base;
		if (cfg.maxColumns > 0 && cfg.maxColumns > fit) {
			// просили больше колонок, чем помещается: уменьшаем ячейки, чтобы все колонки были на месте
			cell = Math.max(12, avail / cfg.maxColumns);
			cols = Math.min(cfg.maxColumns, avail / cell);
		} else {
			cols = Math.max(2, Math.min(fit, want));
		}
		icon = Math.max(8, Math.min(cfg.iconSize(), cell - 2 - cfg.gap() * 2));
		pw = cols * cell + 2 * PAD + 8;
		if (standalone) {
			px = cfg.left ? 4 : screen.width - pw - 4;
		} else {
			int cx = acc.adminControl$getX(), cw = acc.adminControl$getBackgroundWidth();
			px = cfg.left ? cx - 4 - pw : cx + cw + 4;
		}
		ph = Math.max(80, (screen.height - 8) * cfg.heightPercent / 100);
		py = (screen.height - ph) / 2;
		boxX = px + PAD;
		boxY = py + PAD;
		gearX = px + pw - PAD - BTN;
		hideX = gearX - BTN - 2;
		gearY = hideY = boxY;
		boxW = hideX - 3 - boxX;
		catRects.clear();
		int perRow = Math.max(1, (pw - 2 * PAD + 2) / (BTN + 1));
		int y = boxY + BTN + 4;
		if (!cfg.compact) {
			int cats = CwEmiClient.CATEGORIES + 1;
			for (int i = 0; i < cats; i++) {
				catRects.add(new int[]{px + PAD + (i % perRow) * (BTN + 1), y + (i / perRow) * (BTN + 1), BTN, BTN});
			}
			y += ((cats + perRow - 1) / perRow) * (BTN + 1) + 2;
		}
		gridX = px + PAD;
		gridY = y;
		gridW = cols * cell;
		int footer = cfg.compact ? 0 : 12;
		rows = Math.max(0, (py + ph - PAD - footer - gridY) / cell);
		shown = rows >= 1;
	}

	Item hoveredItem(double mx, double my) {
		CwEmiClient.Entry e = gridEntryAt(mx, my);
		if (e != null) {
			return e.item;
		}
		if (handled == null) {
			return null;
		}
		int x0 = acc.adminControl$getX(), y0 = acc.adminControl$getY();
		for (Slot slot : handled.getScreenHandler().slots) {
			if (slot.isEnabled() && hit(mx, my, x0 + slot.x, y0 + slot.y, 16, 16) && !slot.getStack().isEmpty()) {
				return slot.getStack().getItem();
			}
		}
		return null;
	}

	private CwEmiClient.Entry gridEntryAt(double mx, double my) {
		if (!shown || !hit(mx, my, gridX, gridY, gridW, rows * cell)) {
			return null;
		}
		int col = (int) ((mx - gridX) / cell), row = (int) ((my - gridY) / cell);
		int index = (CwEmiClient.scrollRows + row) * cols + col;
		return index >= 0 && index < items.size() ? items.get(index) : null;
	}

	/** Стрелки страниц Creative в теме сюжета; в обычном мире остаются ванильными. */
	private void restyleCreativeArrows(DrawContext c, CwEmiTheme th, TextRenderer font, int mx, int my) {
		if (!creative || !th.story()) {
			return;
		}
		for (Element e : screen.children()) {
			if (e instanceof ButtonWidget b && b.visible && b.getWidth() <= 26 && b.getHeight() <= 26) {
				boolean hv = hit(mx, my, b.getX(), b.getY(), b.getWidth(), b.getHeight());
				th.button(c, b.getX(), b.getY(), b.getWidth(), b.getHeight(), hv, b.active);
				String label = b.getMessage().getString();
				c.drawText(font, label, b.getX() + (b.getWidth() - font.getWidth(label)) / 2, b.getY() + (b.getHeight() - 8) / 2, th.buttonText(b.active), false);
			}
		}
	}

	void render(DrawContext c, int mx, int my) {
		CwEmiConfig cfg = CwEmiConfig.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		CwEmiTheme th = CwEmiTheme.current();
		TextRenderer font = mc.textRenderer;
		if (!standalone) {
			restyleCreativeArrows(c, th, font, mx, my);
		}
		layout();
		if (!cfg.visible) {
			int bx = cfg.left ? 4 : screen.width - 18;
			boolean hover = hit(mx, my, bx, 4, 14, 14);
			th.button(c, bx, 4, 14, 14, hover, true);
			c.drawText(font, "E", bx + 4, 7, th.buttonText(true), false);
			if (hover) {
				c.drawTooltip(font, Text.translatable("cw_emi.show_panel"), mx, my);
			}
			return;
		}
		if (!shown) {
			return;
		}
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 400);
		th.panel(c, px, py, pw, ph);
		List<Text> tip = null;
		c.fill(boxX, boxY, boxX + boxW, boxY + BTN, 0xFF000000);
		c.drawBorder(boxX, boxY, boxW, BTN, field.isFocused() ? (th.vanilla ? 0xFFFFFFFF : th.accent) : 0xFF8B8B8B);
		field.setX(boxX + 4);
		field.setY(boxY + 3);
		field.setWidth(boxW - 8);
		field.paint(c, mx, my);
		if (field.getText().isEmpty() && !field.isFocused()) {
			c.drawText(font, font.trimToWidth(Text.translatable("cw_emi.search_hint").getString(), boxW - 10), boxX + 4, boxY + 3, 0xFF909090, false);
		}
		if (hit(mx, my, boxX, boxY, boxW, BTN)) {
			tip = List.of(Text.translatable("cw_emi.search_tip"));
		}
		boolean hHide = hit(mx, my, hideX, hideY, BTN, BTN), hGear = hit(mx, my, gearX, gearY, BTN, BTN);
		th.button(c, hideX, hideY, BTN, BTN, hHide, true);
		c.drawText(font, ">", hideX + 5, hideY + 3, th.buttonText(true), false);
		th.button(c, gearX, gearY, BTN, BTN, hGear, true);
		c.drawText(font, "⚙", gearX + 3, gearY + 3, th.buttonText(true), false);
		if (hHide) {
			tip = List.of(Text.translatable("cw_emi.hide_panel"));
		} else if (hGear) {
			tip = List.of(Text.translatable("cw_emi.settings"));
		}
		for (int i = 0; i < catRects.size(); i++) {
			int[] r = catRects.get(i);
			boolean sort = i == CwEmiClient.CATEGORIES;
			boolean sel = !sort && i == CwEmiClient.category;
			boolean hv = hit(mx, my, r[0], r[1], r[2], r[3]);
			th.button(c, r[0], r[1], r[2], r[3], hv, true);
			if (sel) {
				c.drawBorder(r[0], r[1], r[2], r[3], th.accent);
			}
			if (sort) {
				c.drawText(font, "↕", r[0] + 4, r[1] + 3, th.buttonText(true), false);
				if (hv) {
					tip = List.of(Text.translatable("cw_emi.sort").append(": ").append(Text.translatable("cw_emi.sort." + cfg.sort)));
				}
			} else {
				c.getMatrices().push();
				c.getMatrices().translate(r[0] + 3, r[1] + 3, 0);
				c.getMatrices().scale(0.5f, 0.5f, 1f);
				c.drawItem(CAT_ICONS[i], 0, 0);
				c.getMatrices().pop();
				if (hv) {
					Text name = Text.translatable("cw_emi.cat." + i);
					if (i == CwEmiClient.CAT_MODS) {
						name = name.copy().append(": ").append(CwEmiClient.modFilter.isEmpty() ? Text.translatable("cw_emi.all_mods") : Text.literal(CwEmiClient.modName(CwEmiClient.modFilter)));
						tip = List.of(name, Text.translatable("cw_emi.mods_hint").formatted(Formatting.DARK_GRAY));
					} else {
						tip = List.of(name);
					}
				}
			}
		}
		boolean synced = CwEmiClient.ready();
		items = synced ? CwEmiClient.view(CwEmiClient.search, CwEmiClient.category) : List.of();
		totalRows = (items.size() + cols - 1) / cols;
		int maxScroll = Math.max(0, totalRows - rows);
		if (dragScroll) {
			long window = mc.getWindow().getHandle();
			if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
				dragTo(mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
			} else {
				dragScroll = false;
			}
		}
		CwEmiClient.scrollRows = Math.max(0, Math.min(maxScroll, CwEmiClient.scrollRows));
		th.slot(c, gridX - 1, gridY - 1, gridW + 2, false, rows * cell + 2);
		if (!synced || items.isEmpty()) {
			c.drawText(font, font.trimToWidth(Text.translatable(synced ? "cw_emi.nothing" : "cw_emi.syncing").getString(), gridW - 4), gridX + 3, gridY + 4, th.vanilla ? 0xFFFFFFFF : th.sub, false);
		}
		CwEmiClient.Entry hovered = null;
		int off = (cell - icon) / 2;
		for (int r = 0; r < rows; r++) {
			for (int col = 0; col < cols; col++) {
				int index = (CwEmiClient.scrollRows + r) * cols + col;
				if (index >= items.size()) {
					break;
				}
				CwEmiClient.Entry e = items.get(index);
				int x = gridX + col * cell, y = gridY + r * cell;
				if (hit(mx, my, x, y, cell, cell)) {
					c.fill(x, y, x + cell, y + cell, th.vanilla ? 0x80FFFFFF : 0x804B7C80);
					hovered = e;
				}
				c.getMatrices().push();
				c.getMatrices().translate(x + off, y + off, 0);
				float s = icon / 16f;
				c.getMatrices().scale(s, s, 1f);
				c.drawItem(e.stack, 0, 0);
				c.getMatrices().pop();
				if (cfg.favoritesEnabled && CwEmiClient.isFavorite(e.item)) {
					c.drawText(font, "★", x + cell - 7, y, 0xFFFFD34D, true);
				}
			}
		}
		int sbX = gridX + gridW + 2, sbH = rows * cell;
		th.slot(c, sbX, gridY, 6, false, sbH);
		if (maxScroll > 0) {
			int thumb = Math.max(10, sbH * rows / Math.max(1, totalRows));
			int ty = gridY + (sbH - thumb) * CwEmiClient.scrollRows / maxScroll;
			c.fill(sbX + 1, ty, sbX + 5, ty + thumb, th.vanilla ? 0xFFC6C6C6 : th.accent);
		}
		if (!cfg.compact) {
			String s = items.size() + " " + Text.translatable("cw_emi.items").getString();
			c.drawText(font, font.trimToWidth(s, pw - 2 * PAD), px + PAD, py + ph - PAD - 8, th.vanilla ? 0xFF404040 : th.sub, false);
		}
		c.getMatrices().pop();
		if (tip != null) {
			c.getMatrices().push();
			c.getMatrices().translate(0, 0, 450);
			c.drawTooltip(font, tip, mx, my);
			c.getMatrices().pop();
		} else if (hovered != null && cfg.tooltips != 2) {
			c.getMatrices().push();
			c.getMatrices().translate(0, 0, 450);
			drawItemTip(c, font, hovered, mx, my, cfg);
			c.getMatrices().pop();
		}
	}

	private void drawItemTip(DrawContext c, TextRenderer font, CwEmiClient.Entry e, int mx, int my, CwEmiConfig cfg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		List<Text> lines = new ArrayList<>();
		if (cfg.tooltips == 1) {
			lines.add(e.stack.getName());
		} else {
			lines.addAll(Screen.getTooltipFromItem(mc, e.stack));
		}
		if (cfg.showModNames && !ItemEncryption.nameOrDescHiddenClient(e.item)) {
			String mod = FabricLoader.getInstance().getModContainer(e.mod).map(m -> m.getMetadata().getName()).orElse(e.mod);
			lines.add(Text.literal(mod).formatted(Formatting.BLUE, Formatting.ITALIC));
		}
		lines.add(Text.translatable("cw_emi.hint_click").formatted(Formatting.DARK_GRAY));
		lines.add(Text.translatable("cw_emi.hint_fav").formatted(Formatting.DARK_GRAY));
		c.drawTooltip(font, lines, mx, my);
	}

	boolean mouseClicked(double mx, double my, int button) {
		CwEmiConfig cfg = CwEmiConfig.get();
		if (!cfg.visible) {
			int bx = cfg.left ? 4 : screen.width - 18;
			if (hit(mx, my, bx, 4, 14, 14) && button == 0) {
				cfg.visible = true;
				CwEmiConfig.save();
				return true;
			}
			return false;
		}
		if (!shown || !hit(mx, my, px, py, pw, ph)) {
			field.setFocused(false);
			return false;
		}
		boolean inField = hit(mx, my, boxX, boxY, boxW, BTN);
		if (!inField) {
			field.setFocused(false);
		}
		if (hit(mx, my, hideX, hideY, BTN, BTN) && button == 0) {
			cfg.visible = false;
			CwEmiConfig.save();
			return true;
		}
		if (hit(mx, my, gearX, gearY, BTN, BTN) && button == 0) {
			MinecraftClient.getInstance().setScreen(new CwEmiSettingsScreen(screen));
			return true;
		}
		if (inField) {
			if (button == 1) {
				field.setText("");
			}
			field.setFocused(true);
			field.setCursorToEnd();
			return true;
		}
		for (int i = 0; i < catRects.size(); i++) {
			int[] r = catRects.get(i);
			if (!hit(mx, my, r[0], r[1], r[2], r[3])) {
				continue;
			}
			if (i == CwEmiClient.CATEGORIES) {
				cfg.sort = (cfg.sort + (button == 1 ? 3 : 1)) % 4;
				CwEmiConfig.save();
			} else if ((i == CwEmiClient.CAT_FAVORITES && !cfg.favoritesEnabled) || (i == CwEmiClient.CAT_HISTORY && !cfg.historyEnabled)) {
				return true;
			} else if (i == CwEmiClient.CAT_MODS) {
				if (CwEmiClient.category == i) {
					CwEmiClient.cycleMod(button == 1 ? -1 : 1);
				} else {
					CwEmiClient.category = i;
					CwEmiClient.scrollRows = 0;
				}
			} else {
				CwEmiClient.category = i;
				CwEmiClient.scrollRows = 0;
			}
			return true;
		}
		int sbX = gridX + gridW + 2;
		if (hit(mx, my, sbX - 2, gridY, 12, rows * cell) && totalRows > rows && button == 0) {
			int ty = thumbY(), th = thumbHeight();
			grab = my >= ty && my < ty + th ? my - ty : th / 2.0;
			dragScroll = true;
			dragTo(my);
			return true;
		}
		CwEmiClient.Entry e = gridEntryAt(mx, my);
		if (e != null) {
			if (button == 2 || (button == 0 && Screen.hasShiftDown())) {
				if (cfg.favoritesEnabled) {
					CwEmiClient.toggleFavorite(e.item);
				}
			} else {
				opener.accept(e.item, button != 1);
			}
		}
		return true;
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (!shown || !CwEmiConfig.get().visible || !hit(mx, my, px, py, pw, ph)) {
			return false;
		}
		int maxScroll = Math.max(0, totalRows - rows);
		int step = Screen.hasShiftDown() ? rows : 1;
		CwEmiClient.scrollRows = Math.max(0, Math.min(maxScroll, CwEmiClient.scrollRows - (int) Math.signum(amount) * step));
		return true;
	}

	/** true — клавиша обработана панелью и не должна идти дальше. */
	boolean keyPressed(int key, int scan, int mods) {
		if (field.isFocused()) {
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				field.setFocused(false);
				return true;
			}
			field.key(key, scan, mods);
			return true;
		}
		for (Element e : screen.children()) {
			if (e instanceof TextFieldWidget tf && tf != field && tf.isFocused()) {
				return false;
			}
		}
		if (CwEmiClient.keyToggle.matchesKey(key, scan)) {
			CwEmiConfig cfg = CwEmiConfig.get();
			cfg.visible = !cfg.visible;
			CwEmiConfig.save();
			return true;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
		double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
		boolean recipes = CwEmiClient.keyRecipes.matchesKey(key, scan);
		boolean uses = CwEmiClient.keyUses.matchesKey(key, scan);
		boolean fav = CwEmiClient.keyFavorite.matchesKey(key, scan);
		if (!recipes && !uses && !fav) {
			return false;
		}
		Item item = hoveredItem(mx, my);
		if (item == null || !CwEmiClient.canOpen(item)) {
			return false;
		}
		if (fav) {
			if (CwEmiConfig.get().favoritesEnabled) {
				CwEmiClient.toggleFavorite(item);
			}
		} else {
			opener.accept(item, recipes);
		}
		return true;
	}
}
