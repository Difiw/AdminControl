package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.DevEventScreenHandler;
import ru.admincontrol.util.EventCatalog;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ru.admincontrol.screen.DevEventScreenHandler.*;

/** /devent: список МП → настройки по умолчанию, набор, ломаемые блоки, свои МП, правка карты и черновики. */
public class DevEventScreen extends HandledScreen<DevEventScreenHandler> {
	private static final int ROW = 22;
	private static final int TOP = 28;
	private static final int KIND_PAIR = 0;
	private static final int KIND_FULL = 1;
	private static final int KIND_TEXT = 2;
	private static final int KIND_REMOVE = 3;
	/** Две кнопки в строке: left/leftText и right/rightText; right == NO_CELL — только левая. */
	private static final int KIND_GRID = 4;
	private static final int NO_CELL = Integer.MIN_VALUE;
	/** Кнопки, которые меняют только экран клиента (на сервер не отправляются). */
	private static final int LOCAL_TEXT = -1;
	private static final int LOCAL_DELETE = -2;
	private static final int LOCAL_CANCEL = -3;

	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private int signature = Integer.MIN_VALUE;
	private int page = Integer.MIN_VALUE;
	private boolean textPage;
	private boolean confirmDelete;
	private String nameDraft = "";
	private String descDraft = "";
	private TextFieldWidget nameField;
	private TextFieldWidget descField;

	private record Row(int kind, String label, int left, int right, String leftText, String rightText, boolean enabled) {
	}

	public DevEventScreen(DevEventScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private static Row pair(String label, int minus, int plus) {
		return new Row(KIND_PAIR, label, minus, plus, "−", "+", true);
	}

	private static Row pair(String label, int minus, int plus, String minusText, String plusText) {
		return new Row(KIND_PAIR, label, minus, plus, minusText, plusText, true);
	}

	private static Row full(String label, int id) {
		return new Row(KIND_FULL, label, id, 0, "", "", true);
	}

	private static Row text(String label) {
		return new Row(KIND_TEXT, label, 0, 0, "", "", false);
	}

	private static Row remove(String label, int id) {
		return new Row(KIND_REMOVE, label, 0, id, "", "✕", true);
	}

	private static Row grid(String leftText, int left, String rightText, int right) {
		return new Row(KIND_GRID, "", left, right, leftText, rightText, true);
	}

	private String choiceLabel(int choice) {
		return (choice >= EventService.TYPES.length ? "★ " : "") + typeName(choice);
	}

	/** Кнопки МП по две в строке в порядке из /devent; id(pos, choice) — что отправить при нажатии. */
	private void addChoiceGrid(List<Row> out, java.util.function.IntBinaryOperator id, int selectedPos) {
		int n = this.handler.orderCount();
		for (int pos = 0; pos < n; pos += 2) {
			int a = this.handler.orderAt(pos);
			String left = (pos == selectedPos ? "▶ " : "") + choiceLabel(a);
			if (pos + 1 < n) {
				int b = this.handler.orderAt(pos + 1);
				String right = (pos + 1 == selectedPos ? "▶ " : "") + choiceLabel(b);
				out.add(grid(left, id.applyAsInt(pos, a), right, id.applyAsInt(pos + 1, b)));
			} else {
				out.add(grid(left, id.applyAsInt(pos, a), "", NO_CELL));
			}
		}
	}

	private boolean orderPage() {
		return this.handler.type() < 0 && this.handler.page() == PAGE_ORDER;
	}

	private String typeName(int type) {
		return this.handler.choiceName(type);
	}

	private static String baseName(int base) {
		return base >= 0 && base < EventService.TYPES.length ? EventService.TYPES[base] : "—";
	}

	private static String blockName(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
			return id + " (нет в игре)";
		}
		return Registries.BLOCK.get(identifier).getName().getString();
	}

	private static String tenths(int value) {
		return String.format(Locale.US, "+%.1f", value / 10.0);
	}

	private boolean editingMine() {
		return this.handler.get(EDIT) == EDIT_MINE;
	}

	private boolean breakPage() {
		return this.handler.type() >= 0 && this.handler.page() == PAGE_BREAK;
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		DevEventScreenHandler h = this.handler;
		if (editingMine()) {
			out.add(text("Вы правите карту «" + typeName(h.get(EDIT_TYPE)) + "»"));
			out.add(text("Помощников: " + h.get(HELPERS) + " из 8"));
			out.add(text("/dbuild <ник> — позвать, /dbuildkick <ник> — убрать"));
			out.add(text("Черновик сохраняется сам каждые 2 минуты"));
			out.add(full("Опубликовать карту и выйти", SAVE_MAP));
			out.add(full("Сохранить черновик и выйти", SAVE_DRAFT));
			out.add(full("Выйти без сохранения", CANCEL_MAP));
			out.add(text("(без сохранения черновик тоже удалится)"));
			return out;
		}
		int type = h.type();
		if (orderPage()) {
			out.add(text("Нажмите МП: ◀ ▶ — на место, ▲ ▼ — на строку"));
			addChoiceGrid(out, (pos, choice) -> ORDER_PICK_BASE + pos, h.orderSelected());
			out.add(full("Сбросить порядок", ORDER_RESET));
			out.add(text("Для /event, /tevent, /allevent и /devent"));
			return out;
		}
		if (type < 0) {
			addChoiceGrid(out, (pos, choice) -> TYPE_BASE + choice, -1);
			if (h.customCount() < EventCatalog.MAX_CUSTOM) {
				out.add(full("＋ Создать своё МП", NEW_CUSTOM));
			} else {
				out.add(text("Своих МП уже " + EventCatalog.MAX_CUSTOM + " — это предел"));
			}
			out.add(full("Порядок кнопок МП", OPEN_ORDER));
			return out;
		}
		if (this.confirmDelete) {
			out.add(text("Удалить «" + typeName(type) + "» навсегда?"));
			out.add(text("Пропадут карта, черновик, набор и настройки."));
			out.add(full("Да, удалить", DELETE_CUSTOM));
			out.add(full("Отмена", LOCAL_CANCEL));
			return out;
		}
		if (breakPage()) {
			List<String> blocks = h.breakable();
			if (blocks.isEmpty()) {
				out.add(text("Список пуст: ломается только то,"));
				out.add(text("что разрешают правила самого МП"));
			} else {
				out.add(text("Участники смогут ломать (" + blocks.size() + " из " + EventCatalog.MAX_BREAKABLE + "):"));
				for (int i = 0; i < blocks.size(); i++) {
					out.add(remove(blockName(blocks.get(i)), BREAK_DEL_BASE + i));
				}
			}
			if (blocks.size() < EventCatalog.MAX_BREAKABLE) {
				out.add(full("Добавить блок из руки", BREAK_HAND));
				out.add(full("Добавить блок под взглядом", BREAK_LOOK));
			}
			if (!blocks.isEmpty()) {
				out.add(full("Очистить список", BREAK_CLEAR));
			}
			return out;
		}
		boolean custom = h.custom();
		int base = h.base();
		if (custom) {
			out.add(full("Название и описание", LOCAL_TEXT));
			out.add(full("Правила: " + baseName(base), BASE_NEXT));
		}
		if (base == 3) {
			out.add(text("2 места (двое)"));
		} else {
			out.add(pair(h.get(PLAYERS) + " мест", PLAYERS_M, PLAYERS_P));
		}
		if (base == 6) {
			boolean bumpers = h.get(BUMPERS) != 0;
			out.add(full(bumpers ? "С бортиками ✓" : "Без бортиков", BUMPERS_TOGGLE));
			out.add(pair("1 место  " + Money.wc(h.get(PRIZE1)), PRIZE1_M, PRIZE1_P));
			out.add(pair("2 место  " + Money.wc(h.get(PRIZE2)), PRIZE2_M, PRIZE2_P));
			out.add(pair("3 место  " + Money.wc(h.get(PRIZE3)), PRIZE3_M, PRIZE3_P));
			if (!bumpers) {
				out.add(pair("удача " + tenths(h.get(LUCK)), LUCK_M, LUCK_P));
			}
		} else {
			out.add(pair("приз " + Money.wc(h.get(REWARD)), REWARD_M, REWARD_P));
			out.add(pair("приз −10 / +10", REWARD_M10, REWARD_P10, "−10", "+10"));
			if (base == 7) {
				out.add(pair("удача " + tenths(h.get(LUCK)), LUCK_M, LUCK_P));
			} else {
				out.add(pair("сила " + tenths(h.get(POWER)), POWER_M, POWER_P));
				out.add(pair("сила −0.5 / +0.5", POWER_M5, POWER_P5, "−0.5", "+0.5"));
			}
		}
		if (base == 3) {
			if (custom) {
				out.add(text("Убийства, время и мобы — как у «Охоты»"));
			} else {
				out.add(pair("убийств: " + h.get(HUNT_KILLS), HUNT_KILLS_M, HUNT_KILLS_P));
				out.add(pair("время: " + h.get(HUNT_SECONDS) + " с", HUNT_TIME_M, HUNT_TIME_P));
				int mob = Math.max(0, Math.min(EventService.HUNT_MOBS.length - 1, h.get(HUNT_MOB)));
				out.add(full("Мобы: " + EventService.HUNT_MOBS[mob], HUNT_MOB_NEXT));
			}
		} else if (base == 12) {
			if (custom) {
				out.add(text("Убийства и время — как у стандартного МП"));
			} else {
				out.add(pair("убийств до победы: " + h.get(STRIKE_KILLS), STRIKE_KILLS_M, STRIKE_KILLS_P));
				out.add(pair("время: " + h.get(STRIKE_MINUTES) + " мин", STRIKE_TIME_M, STRIKE_TIME_P));
			}
		}
		out.add(full("Набор предметов", OPEN_KIT));
		out.add(full("Ломаемые блоки (" + h.get(BREAK_COUNT) + ")", OPEN_BREAK));
		boolean saved = h.get(MAP_SAVED) != 0;
		if (h.get(EDIT) == EDIT_OTHER) {
			out.add(new Row(KIND_FULL, "Правится карта «" + typeName(h.get(EDIT_TYPE)) + "»", EDIT_MAP, 0, "", "", false));
		} else {
			String what = h.get(DRAFT) != 0 ? "черновик" : saved ? "своя" : "стандартная";
			out.add(full("Изменить карту (" + what + ")", EDIT_MAP));
		}
		if (h.get(DRAFT) != 0) {
			out.add(full("Удалить черновик карты", DELETE_DRAFT));
		}
		if (saved) {
			out.add(full(custom ? "Удалить свою карту" : "Вернуть стандартную карту", RESET_MAP));
		}
		out.add(full("Сбросить настройки", RESET));
		if (custom) {
			out.add(full("Удалить это МП", LOCAL_DELETE));
		}
		return out;
	}

	private int visibleRows() {
		return (this.backgroundHeight - TOP - 32) / ROW;
	}

	private int maxScroll() {
		return Math.max(0, rows().size() - visibleRows());
	}

	private int footerY() {
		return this.y + this.backgroundHeight - 24;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = 0;
		for (int i = 0; i < PROPS; i++) {
			sig = sig * 31 + this.handler.get(i);
		}
		int nextPage = editingMine() ? -1000 : this.handler.type() * 4 + this.handler.page();
		if (nextPage != this.page) {
			this.page = nextPage;
			this.scroll = 0;
			this.textPage = false;
			this.confirmDelete = false;
			this.signature = Integer.MIN_VALUE;
		}
		if (sig != this.signature) {
			this.signature = sig;
			if (!this.textPage) {
				rebuild();
			}
		}
	}

	private void rebuild() {
		this.clearChildren();
		this.nameField = null;
		this.descField = null;
		int fw = this.backgroundWidth - 16;
		int fx = this.x + 8;
		int half = (fw - 4) / 2;
		if (this.textPage) {
			this.nameField = new TextFieldWidget(this.textRenderer, fx, this.y + TOP + 14, fw, 18, Text.literal("Название"));
			this.nameField.setMaxLength(EventCatalog.NAME_MAX);
			this.nameField.setText(this.nameDraft);
			this.nameField.setChangedListener(s -> this.nameDraft = s);
			this.addDrawableChild(this.nameField);
			this.descField = new TextFieldWidget(this.textRenderer, fx, this.y + TOP + 54, fw, 18, Text.literal("Описание"));
			this.descField.setMaxLength(EventCatalog.DESC_MAX);
			this.descField.setText(this.descDraft);
			this.descField.setChangedListener(s -> this.descDraft = s);
			this.addDrawableChild(this.descField);
			this.setInitialFocus(this.nameField);
			button("Сохранить", fx, footerY(), half, this::saveText);
			button("Отмена", fx + fw - half, footerY(), half, this::closeTextPage);
			return;
		}
		List<Row> rows = rows();
		this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
		int x = this.x + 8;
		int w = this.backgroundWidth - 28;
		for (int i = 0; i < visibleRows(); i++) {
			int index = this.scroll + i;
			if (index >= rows.size()) {
				break;
			}
			addRow(rows.get(index), x, this.y + TOP + i * ROW, w);
		}
		if (!editingMine() && orderPage()) {
			int aw = 26;
			int[] ids = {ORDER_LEFT, ORDER_RIGHT, ORDER_UP, ORDER_DOWN};
			String[] arrows = {"◀", "▶", "▲", "▼"};
			boolean picked = this.handler.orderSelected() >= 0;
			for (int i = 0; i < ids.length; i++) {
				int id = ids[i];
				button(arrows[i], fx + i * (aw + 4), footerY(), aw, () -> click(id)).active = picked;
			}
			int left = fx + ids.length * (aw + 4);
			button("К списку", left, footerY(), fx + fw - left, () -> click(BACK));
		} else if (!editingMine() && this.handler.type() >= 0) {
			String back = this.confirmDelete || breakPage() ? "Назад" : "К списку";
			button(back, fx, footerY(), half, () -> {
				if (this.confirmDelete) {
					click(LOCAL_CANCEL);
				} else {
					click(BACK);
				}
			});
			button("Закрыть", fx + fw - half, footerY(), half, this::close);
		} else {
			button("Закрыть", fx, footerY(), fw, this::close);
		}
	}

	private void addRow(Row row, int x, int y, int w) {
		if (row.kind() == KIND_TEXT) {
			return;
		}
		if (row.kind() == KIND_FULL) {
			ButtonWidget b = button(row.label(), x, y, w, () -> click(row.left()));
			b.active = row.enabled();
			return;
		}
		if (row.kind() == KIND_GRID) {
			int cell = (w - 4) / 2;
			button(row.leftText(), x, y, cell, () -> click(row.left()));
			if (row.right() != NO_CELL) {
				button(row.rightText(), x + cell + 4, y, cell, () -> click(row.right()));
			}
			return;
		}
		if (row.kind() == KIND_REMOVE) {
			int rw = 22;
			button(row.label(), x, y, w - rw - 4, () -> {
			});
			button(row.rightText(), x + w - rw, y, rw, () -> click(row.right()));
			return;
		}
		int lw = Math.max(22, this.textRenderer.getWidth(row.leftText()) + 8);
		int rw = Math.max(22, this.textRenderer.getWidth(row.rightText()) + 8);
		button(row.leftText(), x, y, lw, () -> click(row.left()));
		button(row.label(), x + lw + 4, y, w - lw - rw - 8, () -> {
		});
		button(row.rightText(), x + w - rw, y, rw, () -> click(row.right()));
	}

	private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
		return this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
				.dimensions(x, y, w, 18).build());
	}

	private void click(int id) {
		if (id == LOCAL_TEXT) {
			int type = this.handler.type();
			this.nameDraft = this.handler.choiceName(type);
			this.descDraft = this.handler.choiceDesc(type);
			this.textPage = true;
			rebuild();
			return;
		}
		if (id == LOCAL_DELETE || id == LOCAL_CANCEL) {
			this.confirmDelete = id == LOCAL_DELETE;
			this.scroll = 0;
			rebuild();
			return;
		}
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private void saveText() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(this.handler.type());
		buf.writeString(this.nameDraft, EventCatalog.NAME_MAX * 4);
		buf.writeString(this.descDraft, EventCatalog.DESC_MAX * 4);
		ClientPlayNetworking.send(ModPackets.DEV_EVENT_TEXT, buf);
		this.textPage = false;
		rebuild();
	}

	private void closeTextPage() {
		this.textPage = false;
		rebuild();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.textPage) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeTextPage();
				return true;
			}
			if (this.getFocused() instanceof TextFieldWidget field) {
				if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
					saveText();
					return true;
				}
				if (field.keyPressed(keyCode, scanCode, modifiers) || field.isActive()) {
					return true;
				}
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.getFocused() instanceof TextFieldWidget field && field.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.textPage) {
			return true;
		}
		int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.textPage && maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, this.x + this.backgroundWidth - 12, this.y + TOP,
				visibleRows() * ROW)) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, this.y + TOP, visibleRows() * ROW, maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && !this.textPage && maxScroll() > 0) {
			int next = this.bar.click(mouseY, this.y + TOP, visibleRows() * ROW, maxScroll());
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
		String title;
		if (editingMine()) {
			title = "Правка карты";
		} else if (orderPage()) {
			title = "Порядок кнопок МП";
		} else if (this.handler.type() < 0) {
			title = "МП: настройки и карты";
		} else if (this.textPage) {
			title = "Название и описание";
		} else if (breakPage()) {
			title = "Ломаемые блоки: " + typeName(this.handler.type());
		} else {
			title = typeName(this.handler.type());
		}
		AcTheme.titleCentered(this.textRenderer, context, title, 8, 6, this.backgroundWidth - 16);
		int w = this.backgroundWidth - 16;
		if (this.textPage) {
			AcTheme.clipped(this.textRenderer, context, "Название (до " + EventCatalog.NAME_MAX + " символов)",
					8, TOP + 2, w, 0xC8D2E0);
			AcTheme.clipped(this.textRenderer, context, "Описание для игроков (до " + EventCatalog.DESC_MAX + " символов)",
					8, TOP + 42, w, 0xC8D2E0);
			AcTheme.wrapped(this.textRenderer, context, "Правила, размер арены и механика берутся у «"
					+ baseName(this.handler.base()) + "». Карта — своя: её можно построить в «Изменить карту».",
					8, TOP + 82, w, 0x8A96A8);
			AcTheme.clipped(this.textRenderer, context, "Символов: " + this.descDraft.length() + " / " + EventCatalog.DESC_MAX,
					8, TOP + 130, w, 0x8A96A8);
			return;
		}
		List<Row> rows = rows();
		for (int i = 0; i < visibleRows(); i++) {
			int index = this.scroll + i;
			if (index >= rows.size()) {
				break;
			}
			Row row = rows.get(index);
			if (row.kind() == KIND_TEXT) {
				AcTheme.clippedCentered(this.textRenderer, context, row.label(), 8, TOP + i * ROW + 5,
						this.backgroundWidth - 28, AcTheme.SCORE);
			}
		}
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, TOP, visibleRows() * ROW, maxScroll(), visibleRows(), this.scroll);
		}
	}
}
