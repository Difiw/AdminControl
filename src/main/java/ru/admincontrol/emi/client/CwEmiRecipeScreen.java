package ru.admincontrol.emi.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.emi.EmiRecipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Окно CW EMI: рецепты предмета («Крафты в:» со станциями) и режим «Используется в». Все данные получены от сервера и
 * уже отфильтрованы; закрытые станции и рецепты здесь представлены только безопасным индикатором «Недоступно».
 */
public final class CwEmiRecipeScreen extends Screen {
	private record Nav(Item item, boolean obtain) {
	}

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, List<Text> tip, ItemStack stack) {
	}

	private final Screen parent;
	private final Deque<Nav> back = new ArrayDeque<>();
	private final Deque<Nav> forward = new ArrayDeque<>();
	private Nav cur;
	private int groupIdx, recipeIdx;
	private int seenVersion;
	private final List<Hit> hits = new ArrayList<>();
	private int px, py, pw, ph;
	private CwEmiPanel panel;

	public CwEmiRecipeScreen(Screen parent, Item item, boolean obtain) {
		super(Text.translatable("cw_emi.title"));
		this.parent = parent;
		this.cur = new Nav(item, obtain);
		CwEmiClient.pushHistory(item);
		this.seenVersion = CwEmiClient.version;
	}

	@Override
	protected void init() {
		CwEmiConfig cfg = CwEmiConfig.get();
		int panelW = cfg.panelInRecipe && cfg.visible ? CwEmiPanel.standaloneWidth(this.width, 290) + 8 : 0;
		int regionX = cfg.left ? panelW : 0, regionW = this.width - panelW;
		pw = Math.min(regionW - 8, 340);
		ph = Math.min(this.height - 8, 240);
		px = regionX + (regionW - pw) / 2;
		py = (this.height - ph) / 2;
		panel = cfg.panelInRecipe ? new CwEmiPanel(this, true, (item, obtain) -> go(item, obtain)) : null;
		CwEmiOverlay.setCurrent(panel);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void go(Item item, boolean obtain) {
		if (!CwEmiClient.canOpen(item)) {
			return;
		}
		back.push(cur);
		forward.clear();
		cur = new Nav(item, obtain);
		groupIdx = 0;
		recipeIdx = 0;
		CwEmiClient.pushHistory(item);
	}

	private void goBack() {
		if (!back.isEmpty()) {
			forward.push(cur);
			cur = back.pop();
			groupIdx = 0;
			recipeIdx = 0;
		}
	}

	private void goForward() {
		if (!forward.isEmpty()) {
			back.push(cur);
			cur = forward.pop();
			groupIdx = 0;
			recipeIdx = 0;
		}
	}

	private void revalidate() {
		// Права изменились: убираем из навигации всё, что теперь закрыто, и закрываем окно, если закрыт текущий предмет.
		back.removeIf(n -> !CwEmiClient.canOpen(n.item()));
		forward.removeIf(n -> !CwEmiClient.canOpen(n.item()));
		if (!CwEmiClient.canOpen(cur.item())) {
			close();
			return;
		}
		groupIdx = 0;
		recipeIdx = 0;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void mode(boolean obtain) {
		if (cur.obtain() != obtain) {
			cur = new Nav(cur.item(), obtain);
			groupIdx = 0;
			recipeIdx = 0;
		}
	}

	// ---------- отрисовка ----------
	private String clip(String s, int w) {
		return this.textRenderer.getWidth(s) <= w ? s : this.textRenderer.trimToWidth(s, Math.max(0, w - 6)) + "...";
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		this.renderBackground(c);
		renderMain(c, mx, my);
		if (panel != null && MinecraftClient.getInstance().currentScreen == this) {
			panel.render(c, mx, my);
		}
	}

	private void renderMain(DrawContext c, int mx, int my) {
		if (seenVersion != CwEmiClient.version) {
			seenVersion = CwEmiClient.version;
			revalidate();
			if (MinecraftClient.getInstance().currentScreen != this) {
				return;
			}
		}
		hits.clear();
		CwEmiTheme th = CwEmiTheme.current();
		TextRenderer font = this.textRenderer;
		th.panel(c, px, py, pw, ph);
		CwEmiClient.expire(cur.item(), cur.obtain());
		CwEmiClient.Result r = CwEmiClient.result(cur.item(), cur.obtain());
		int left = px + 10, inner = pw - 20;

		// строка 1: навигация, иконка, название
		button(c, mx, my, left, py + 8, 14, 14, "<", !back.isEmpty(), this::goBack, null, Text.translatable("cw_emi.back"));
		button(c, mx, my, left + 16, py + 8, 14, 14, ">", !forward.isEmpty(), this::goForward, null, Text.translatable("cw_emi.forward"));
		ItemStack head = new ItemStack(cur.item());
		c.drawItem(head, left + 36, py + 7);
		hits.add(new Hit(left + 36, py + 7, 16, 16, null, null, null, head));
		c.drawText(font, Text.literal(clip(head.getName().getString(), inner - 82)).setStyle(head.getName().getStyle()), left + 58, py + 11, th.text, false);
		boolean fav = CwEmiClient.isFavorite(cur.item());
		int starX = px + pw - 10 - 14;
		button(c, mx, my, starX, py + 8, 14, 14, "★", CwEmiConfig.get().favoritesEnabled, () -> CwEmiClient.toggleFavorite(cur.item()), null,
				Text.translatable(fav ? "cw_emi.fav_remove" : "cw_emi.fav_add"));
		if (fav) {
			c.drawBorder(starX, py + 8, 14, 14, 0xFFFFD34D);
		}
		// строка 2: режимы
		int tabY = py + 28;
		int w1 = font.getWidth(Text.translatable("cw_emi.mode.obtain").getString()) + 16;
		int w2 = font.getWidth(Text.translatable("cw_emi.mode.uses").getString()) + 16;
		tab(c, mx, my, left, tabY, w1, Text.translatable("cw_emi.mode.obtain"), cur.obtain(), () -> mode(true));
		tab(c, mx, my, left + w1 + 4, tabY, w2, Text.translatable("cw_emi.mode.uses"), !cur.obtain(), () -> mode(false));
		c.fill(px + 8, py + 46, px + pw - 8, py + 47, th.separator);

		int y = py + 52;
		if (r.status == CwEmiClient.Result.LOADING || r.status == CwEmiClient.Result.TIMEOUT) {
			c.drawText(font, Text.translatable(r.status == CwEmiClient.Result.LOADING ? "cw_emi.loading" : "cw_emi.timeout"), left, y + 4, th.sub, false);
			finish(c, mx, my, th, font);
			return;
		}
		if (r.status == CwEmiClient.Result.DENIED) {
			lockedSlot(c, left, y, th);
			c.drawText(font, Text.translatable("cw_emi.unavailable"), left + 26, y + 6, th.sub, false);
			finish(c, mx, my, th, font);
			return;
		}

		// станции: подпись на своей строке, иконки ниже с переносом
		Text label = Text.translatable(cur.obtain() ? "cw_emi.crafts_in" : "cw_emi.used_in");
		c.drawText(font, label, left, y, th.text, false);
		int sy = y + 11;
		int perRow = Math.max(1, inner / 22);
		groupIdx = Math.max(0, Math.min(groupIdx, Math.max(0, r.groups.size() - 1)));
		int slotsCount = r.groups.size() + (r.locked ? 1 : 0);
		for (int i = 0; i < r.groups.size(); i++) {
			CwEmiClient.Group g = r.groups.get(i);
			int idx = i;
			int sx = left + (i % perRow) * 22, yy = sy + (i / perRow) * 22;
			boolean sel = i == groupIdx;
			boolean hv = mx >= sx && mx < sx + 20 && my >= yy && my < yy + 20;
			th.slot(c, sx, yy, 20, hv || sel);
			if (sel) {
				c.drawBorder(sx, yy, 20, 20, th.accent);
			}
			List<Text> tip = new ArrayList<>();
			if (g.recipes.get(0).kind == EmiRecipe.SOURCE) {
				c.drawItem(new ItemStack(net.minecraft.item.Items.COMPASS), sx + 2, yy + 2);
				tip.add(Text.translatable("cw_emi.sources"));
				hits.add(new Hit(sx, yy, 20, 20, () -> {
					groupIdx = idx;
					recipeIdx = 0;
				}, null, tip, null));
			} else if (!g.station.isEmpty()) {
				c.drawItem(g.station, sx + 2, yy + 2);
				tip.add(g.station.getName());
				tip.add(Text.translatable("cw_emi.hint_station").formatted(Formatting.DARK_GRAY));
				Item stationItem = g.station.getItem();
				hits.add(new Hit(sx, yy, 20, 20, () -> {
					groupIdx = idx;
					recipeIdx = 0;
				}, () -> go(stationItem, true), tip, null));
			} else {
				c.drawText(font, "≡", sx + 7, yy + 6, th.text, false);
				tip.add(Text.translatable("cw_emi.other_type", g.typeId));
				hits.add(new Hit(sx, yy, 20, 20, () -> {
					groupIdx = idx;
					recipeIdx = 0;
				}, null, tip, null));
			}
		}
		if (r.locked) {
			int i = r.groups.size();
			lockedSlot(c, left + (i % perRow) * 22, sy + (i / perRow) * 22, th);
		}
		int stationRows = Math.max(1, (slotsCount + perRow - 1) / perRow);
		y = sy + stationRows * 22 + 2;
		if (r.groups.isEmpty()) {
			if (slotsCount == 0 || r.locked) {
				c.drawText(font, Text.translatable("cw_emi.no_recipes"), left + (r.locked ? 26 : 0), (r.locked ? sy + 6 : sy + 4), th.sub, false);
			}
			finish(c, mx, my, th, font);
			return;
		}
		CwEmiClient.Group group = r.groups.get(groupIdx);
		boolean isSource = group.recipes.get(0).kind == EmiRecipe.SOURCE;
		int btnW = 0;
		if (!isSource && !group.station.isEmpty()) {
			Item stationItem = group.station.getItem();
			String bl = Text.translatable("cw_emi.how_station").getString();
			btnW = Math.min(font.getWidth(bl) + 14, inner / 2);
			button(c, mx, my, px + pw - 10 - btnW, y, btnW, 14, clip(bl, btnW - 6), CwEmiClient.canOpen(stationItem),
					() -> go(stationItem, true), null, Text.translatable("cw_emi.how_station_tip"));
		}
		Text stationName = group.station.isEmpty() ? Text.translatable("cw_emi.other_type", group.typeId) : group.station.getName();
		c.drawText(font, clip(isSource ? Text.translatable("cw_emi.sources").getString() : stationName.getString(), inner - btnW - 8), left, y + 3, th.text, false);
		y += 20;

		recipeIdx = Math.max(0, Math.min(recipeIdx, group.recipes.size() - 1));
		int count = group.recipes.size();
		int pagerY = py + ph - 40;
		int srcRows = Math.max(1, (pagerY - 4 - y) / 22);
		if (isSource) {
			count = Math.max(1, count - srcRows + 1);
			recipeIdx = Math.max(0, Math.min(recipeIdx, count - 1));
		}
		final int pages = count;
		if (count > 1) {
			String page = (recipeIdx + 1) + " / " + count;
			int cx = px + pw / 2;
			button(c, mx, my, cx - 34 - font.getWidth(page) / 2, pagerY, 14, 14, "<", true, () -> recipeIdx = (recipeIdx + pages - 1) % pages, null, null);
			button(c, mx, my, cx + 20 + font.getWidth(page) / 2, pagerY, 14, 14, ">", true, () -> recipeIdx = (recipeIdx + 1) % pages, null, null);
			c.drawText(font, page, cx - font.getWidth(page) / 2, pagerY + 3, th.text, false);
		}
		if (isSource) {
			drawSources(c, mx, my, th, font, group.recipes, recipeIdx, y, srcRows);
		} else {
			drawRecipe(c, mx, my, th, font, group.recipes.get(recipeIdx), y, pagerY - 4);
		}
		finish(c, mx, my, th, font);
	}

	private void finish(DrawContext c, int mx, int my, CwEmiTheme th, TextRenderer font) {
		if (!CwEmiClient.serverMode()) {
			c.drawText(font, Text.translatable("cw_emi.local_mode"), px + 10, py + ph - 12, th.sub, false);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
				c.getMatrices().push();
				c.getMatrices().translate(0, 0, 400);
				if (h.stack() != null) {
					Item it = h.stack().getItem();
					List<Text> lines = new ArrayList<>(Screen.getTooltipFromItem(MinecraftClient.getInstance(), h.stack()));
					if (CwEmiClient.canOpen(it)) {
						lines.add(Text.translatable("cw_emi.hint_ingredient").formatted(Formatting.DARK_GRAY));
					}
					if (CwEmiConfig.get().tooltips != 2) {
						c.drawTooltip(font, CwEmiConfig.get().tooltips == 1 ? List.of(h.stack().getName()) : lines, mx, my);
					}
				} else if (h.tip() != null) {
					c.drawTooltip(font, h.tip(), mx, my);
				}
				c.getMatrices().pop();
				break;
			}
		}
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, boolean active, Runnable left, Runnable right, Text tip) {
		CwEmiTheme th = CwEmiTheme.current();
		boolean hv = mx >= x && mx < x + w && my >= y && my < y + h;
		th.button(c, x, y, w, h, hv, active);
		c.drawText(this.textRenderer, label, x + (w - this.textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, th.buttonText(active), false);
		hits.add(new Hit(x, y, w, h, active ? left : null, active ? right : null, tip == null ? null : List.of(tip), null));
	}

	private void tab(DrawContext c, int mx, int my, int x, int y, int w, Text label, boolean selected, Runnable action) {
		CwEmiTheme th = CwEmiTheme.current();
		boolean hv = mx >= x && mx < x + w && my >= y && my < y + 14;
		th.button(c, x, y, w, 14, hv, true);
		if (selected) {
			c.drawBorder(x, y, w, 14, th.accent);
		}
		String s = this.textRenderer.trimToWidth(label.getString(), w - 4);
		c.drawText(this.textRenderer, s, x + (w - this.textRenderer.getWidth(s)) / 2, y + 3, selected ? (th.accent) : th.buttonText(true), false);
		hits.add(new Hit(x, y, w, 14, action, null, null, null));
	}

	/** Закрытая станция/рецепт: знак вопроса с перечёркиванием и подсказка строго «Недоступно». Не кликабельно, ничего не раскрывает. */
	private void lockedSlot(DrawContext c, int x, int y, CwEmiTheme th) {
		th.slot(c, x, y, 20, false);
		c.drawText(this.textRenderer, "?", x + 7, y + 6, th.vanilla ? 0xFF404040 : th.sub, false);
		for (int i = 2; i < 18; i++) {
			c.fill(x + i, y + 18 - i, x + i + 1, y + 19 - i, 0xFFC04040);
		}
		hits.add(new Hit(x, y, 20, 20, null, null, List.of(Text.translatable("cw_emi.unavailable")), null));
	}

	private void ingredientSlot(DrawContext c, int mx, int my, int x, int y, List<ItemStack> alts) {
		CwEmiTheme th = CwEmiTheme.current();
		boolean hv = mx >= x && mx < x + 18 && my >= y && my < y + 18;
		th.slot(c, x, y, 18, hv);
		if (alts.isEmpty()) {
			return;
		}
		ItemStack s = alts.get((int) ((System.currentTimeMillis() / 1000) % alts.size()));
		c.drawItem(s, x + 1, y + 1);
		c.drawItemInSlot(this.textRenderer, s, x + 1, y + 1);
		Item item = s.getItem();
		hits.add(new Hit(x, y, 18, 18, () -> go(item, true), () -> go(item, false), null, s));
	}

	private void outputSlot(DrawContext c, int mx, int my, int x, int y, ItemStack s) {
		CwEmiTheme th = CwEmiTheme.current();
		boolean hv = mx >= x && mx < x + 24 && my >= y && my < y + 24;
		th.slot(c, x, y, 24, hv);
		c.drawBorder(x, y, 24, 24, th.accent);
		c.drawItem(s, x + 4, y + 4);
		c.drawItemInSlot(this.textRenderer, s, x + 4, y + 4);
		Item item = s.getItem();
		hits.add(new Hit(x, y, 24, 24, () -> go(item, true), () -> go(item, false), null, s));
	}

	private void arrow(DrawContext c, int x, int y, CwEmiTheme th) {
		int ac = th.vanilla ? 0xFF555555 : th.accent;
		c.fill(x, y + 3, x + 16, y + 5, ac);
		c.fill(x + 12, y, x + 14, y + 8, ac);
		c.fill(x + 14, y + 2, x + 16, y + 6, ac);
	}

	private void drawRecipe(DrawContext c, int mx, int my, CwEmiTheme th, TextRenderer font, EmiRecipe r, int top, int bottom) {
		int midY = (top + bottom) / 2;
		switch (r.kind) {
			case EmiRecipe.GRID -> {
				if (!r.note.isEmpty()) {
					c.drawText(font, Text.literal(r.note), px + 12, top - 2, th.sub, false);
				}
				drawGrid(c, mx, my, th, r, r.width, r.height, midY);
			}
			case EmiRecipe.SHAPELESS -> {
				c.drawText(font, Text.translatable("cw_emi.shapeless"), px + 12, top - 2, th.sub, false);
				int n = r.inputs.size();
				int cols = n <= 1 ? 1 : n <= 4 ? 2 : 3;
				drawGrid(c, mx, my, th, r, cols, (n + cols - 1) / cols, midY);
			}
			case EmiRecipe.COOKING -> {
				int x = px + pw / 2 - 60;
				ingredientSlot(c, mx, my, x, midY - 20, r.inputs.get(0));
				c.drawText(font, Text.translatable("cw_emi.fuel"), x - 2, midY + 2, th.sub, false);
				arrow(c, x + 32, midY - 15, th);
				outputSlot(c, mx, my, x + 58, midY - 23, r.outputs.get(0));
				c.drawText(font, Text.translatable("cw_emi.cook_time", String.format("%.1f", r.time / 20f)), x - 2, midY + 18, th.text, false);
				if (r.xp > 0) {
					c.drawText(font, Text.translatable("cw_emi.xp", String.format("%.2f", r.xp)), x - 2, midY + 30, th.text, false);
				}
			}
			case EmiRecipe.STONECUT -> {
				int x = px + pw / 2 - 50;
				ingredientSlot(c, mx, my, x, midY - 9, r.inputs.get(0));
				arrow(c, x + 30, midY - 4, th);
				outputSlot(c, mx, my, x + 54, midY - 12, r.outputs.get(0));
			}
			case EmiRecipe.SMITHING -> {
				int x = px + pw / 2 - 96;
				for (int i = 0; i < Math.min(3, r.inputs.size()); i++) {
					ingredientSlot(c, mx, my, x + i * 26, midY - 9, r.inputs.get(i));
				}
				arrow(c, x + 80, midY - 4, th);
				outputSlot(c, mx, my, x + 104, midY - 12, r.outputs.get(0));
			}
			case EmiRecipe.ANVIL -> {
				c.drawText(font, Text.translatable("cw_emi.anvil"), px + 12, top - 2, th.sub, false);
				int x = px + pw / 2 - 100;
				String[] labels = {"cw_emi.anvil.item", "cw_emi.anvil.material", "cw_emi.anvil.fuel"};
				for (int i = 0; i < Math.min(3, r.inputs.size()); i++) {
					ingredientSlot(c, mx, my, x + i * 40, midY - 9, r.inputs.get(i));
					String l = font.trimToWidth(Text.translatable(labels[i]).getString(), 40);
					c.drawText(font, l, x + i * 40, midY + 12, th.sub, false);
				}
				arrow(c, x + 124, midY - 4, th);
				outputSlot(c, mx, my, x + 148, midY - 12, r.outputs.get(0));
				c.drawText(font, Text.translatable("cw_emi.anvil.restore", r.note), px + 12, midY + 30, th.text, false);
			}
			default -> {
				if (!r.typeId.isEmpty()) {
					c.drawText(font, Text.translatable("cw_emi.other_type", r.typeId), px + 12, top - 2, th.sub, false);
				}
				int per = Math.max(1, Math.min(6, (pw - 100) / 20));
				int rowsN = (r.inputs.size() + per - 1) / per;
				int x0 = px + 14, y0 = midY - rowsN * 10;
				for (int i = 0; i < r.inputs.size(); i++) {
					ingredientSlot(c, mx, my, x0 + (i % per) * 20, y0 + (i / per) * 20, r.inputs.get(i));
				}
				arrow(c, x0 + per * 20 + 6, midY - 4, th);
				outputSlot(c, mx, my, x0 + per * 20 + 28, midY - 12, r.outputs.get(0));
				if (!r.note.isEmpty()) {
					c.drawText(font, Text.literal(r.note), px + 12, Math.min(bottom - 10, y0 + rowsN * 20 + 8), th.text, false);
				}
			}
		}
	}

	/** Способы получения без рецепта: строки «значок + описание», прокручиваются от offset. */
	private void drawSources(DrawContext c, int mx, int my, CwEmiTheme th, TextRenderer font, List<EmiRecipe> list, int offset, int top, int rowsFit) {
		int left = px + 10, inner = pw - 20;
		for (int i = 0; i < rowsFit && offset + i < list.size(); i++) {
			EmiRecipe r = list.get(offset + i);
			int y = top + i * 22;
			List<ItemStack> icon = r.inputs.isEmpty() ? List.of() : r.inputs.get(0);
			ingredientSlot(c, mx, my, left, y, icon);
			Text text;
			switch (r.note) {
				case "block", "block_silk", "block_shears", "block_silk_shears" -> {
					String blockId = r.typeId.contains("|") ? r.typeId.substring(0, r.typeId.indexOf('|')) : r.typeId;
					net.minecraft.util.Identifier bid = net.minecraft.util.Identifier.tryParse(blockId);
					Text name = bid != null && net.minecraft.registry.Registries.BLOCK.containsId(bid)
							? net.minecraft.registry.Registries.BLOCK.get(bid).getName() : Text.literal(blockId);
					if (r.note.equals("block") && !r.station.isEmpty()) {
						text = Text.translatable("cw_emi.src.block_tool", name, r.station.getName());
					} else {
						text = Text.translatable("cw_emi.src." + r.note, name);
					}
				}
				case "box" -> {
					String[] p = r.typeId.split("/");
					text = Text.translatable("cw_emi.src.box", p.length > 0 ? p[0] : "?", p.length > 1 ? p[1] : "?",
							String.format(java.util.Locale.US, "%.0f", r.xp), p.length > 2 ? p[2] : "1");
				}
				case "trade" -> {
					String[] p = r.typeId.split("\\|");
					net.minecraft.util.Identifier pid = net.minecraft.util.Identifier.tryParse(p[0]);
					Text prof = pid != null && pid.getNamespace().equals("minecraft")
							? Text.translatable("entity.minecraft.villager." + pid.getPath()) : Text.literal(p[0]);
					text = Text.translatable("cw_emi.src.trade", prof, p.length > 1 ? p[1] : "1");
				}
				case "creative" -> {
					String[] p = r.typeId.split("\\|");
					text = p.length > 1 ? Text.translatable("cw_emi.src.creative_tab", Text.translatable(p[1]), p[0])
							: Text.translatable("cw_emi.src.creative", p[0]);
				}
				case "creative_op" -> text = Text.translatable("cw_emi.src.creative_op", r.typeId);
				case "command" -> text = Text.translatable("cw_emi.src.command", r.typeId);
				case "entity" -> {
					net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(r.typeId);
					Text name = id != null && net.minecraft.registry.Registries.ENTITY_TYPE.containsId(id)
							? net.minecraft.registry.Registries.ENTITY_TYPE.get(id).getName() : Text.literal(r.typeId);
					text = Text.translatable("cw_emi.src.entity", name);
				}
				case "chest" -> text = Text.translatable("cw_emi.src.chest", r.typeId.replace("minecraft:chests/", "").replace('_', ' '));
				case "fishing" -> text = Text.translatable("cw_emi.src.fishing");
				case "mech" -> text = Text.translatable("cw_emi.mech." + r.typeId);
				case "world_block" -> text = Text.translatable("cw_emi.src.world_block");
				case "gameplay" -> {
					String k = r.typeId.contains("piglin") ? "barter" : r.typeId.contains("hero") ? "hero" : r.typeId.contains("cat") ? "cat"
							: r.typeId.contains("sniffer") ? "sniffer" : r.typeId.startsWith("archaeology") ? "archaeology" : "other";
					text = Text.translatable("cw_emi.src.gameplay." + k);
				}
				default -> text = Text.translatable("cw_emi.src.creative", r.typeId);
			}
			List<net.minecraft.text.OrderedText> lines = font.wrapLines(text, inner - 26);
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				c.drawText(font, lines.get(l), left + 24, y + (lines.size() == 1 ? 5 : 1 + l * 10), th.text, false);
			}
		}
	}

	private void drawGrid(DrawContext c, int mx, int my, CwEmiTheme th, EmiRecipe r, int gw, int gh, int midY) {
		int total = gw * 20 + 20 + 16 + 24;
		int x0 = px + (pw - total) / 2;
		int y0 = midY - gh * 10;
		for (int row = 0; row < gh; row++) {
			for (int col = 0; col < gw; col++) {
				int idx = row * gw + col;
				ingredientSlot(c, mx, my, x0 + col * 20, y0 + row * 20, idx < r.inputs.size() ? r.inputs.get(idx) : List.of());
			}
		}
		arrow(c, x0 + gw * 20 + 6, midY - 4, th);
		outputSlot(c, mx, my, x0 + gw * 20 + 28, midY - 12, r.outputs.get(0));
	}

	// ---------- ввод ----------
	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (panel != null && panel.mouseClicked(mx, my, button)) {
			return true;
		}
		if (button == 3) {
			goBack();
			return true;
		}
		if (button == 4) {
			goForward();
			return true;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
				Runnable action = button == 1 ? h.right() : button == 0 ? h.left() : null;
				if (action != null) {
					action.run();
				}
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (panel != null && panel.keyPressed(key, scan, mods)) {
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (CwEmiClient.keyBack.matchesKey(key, scan)) {
			if (back.isEmpty()) {
				close();
			} else {
				goBack();
			}
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (panel != null && panel.mouseScrolled(mx, my, amount)) {
			return true;
		}
		CwEmiClient.Result r = CwEmiClient.result(cur.item(), cur.obtain());
		if (r.status == CwEmiClient.Result.OK && !r.groups.isEmpty()) {
			var g = r.groups.get(Math.max(0, Math.min(groupIdx, r.groups.size() - 1)));
			int n = g.recipes.size();
			if (g.recipes.get(0).kind == EmiRecipe.SOURCE) {
				recipeIdx = Math.max(0, recipeIdx + (amount > 0 ? -1 : 1));
				return true;
			}
			if (n > 1) {
				recipeIdx = (recipeIdx + (amount > 0 ? n - 1 : 1)) % n;
				return true;
			}
		}
		return super.mouseScrolled(mx, my, amount);
	}
}
