package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import ru.admincontrol.screen.EventScreenHandler;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран МП. Высота подгоняется под GUI scale; низ — два фиксированных ряда кнопок.
 * Контент сверху со скроллом, чтобы ничего не слипалось.
 */
public class EventScreen extends HandledScreen<EventScreenHandler> {
	private static final int ROW = 20;
	private static final int BTN = 18;
	private static final int TYPE_BASE = EventScreenHandler.TYPE_BASE;
	/** Место под кнопки навигации под панелью. */
	private static final int NAV_GAP = 22;
	/** Два ряда кнопок внутри панели + зазор. */
	private static final int FOOTER_ROWS = 42;
	private static final int LOCK_RED = 0xFFE53935;
	private static final int LOCK_HOLE = 0xFF4A0000;

	private final AcScrollBar bar = new AcScrollBar();
	private int listScroll;
	private int cfgScroll;
	private int descScroll;
	private int rosterScroll;
	private boolean showDesc;
	private boolean configuring;
	private boolean lastRunning;
	private int page;
	private int signature = Integer.MIN_VALUE;
	private int listRows = 5;

	private ButtonWidget cancel;
	private ButtonWidget leftAction;
	private ButtonWidget rightAction;
	private ButtonWidget midAction;
	private final List<ButtonWidget> pickButtons = new ArrayList<>();
	private final List<Integer> pickTypes = new ArrayList<>();

	public EventScreen(EventScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 260;
		this.backgroundHeight = 200;
		this.playerInventoryTitleY = 1000;
		this.configuring = handler.running();
		this.lastRunning = handler.running();
		this.page = handler.page();
	}

	private boolean rosterPage() {
		return this.handler.running() && this.handler.manager() && !this.showDesc
				&& this.page != EventScreenHandler.PAGE_MAIN;
	}

	private int rosterMax() {
		return Math.max(0, this.handler.rosterSize() - this.listRows);
	}

	private void fitSize() {
		int maxH = Math.max(160, this.height - NAV_GAP);
		this.backgroundHeight = Math.min(200, maxH);
		this.backgroundWidth = Math.min(260, Math.max(220, this.width - 40));
		this.x = (this.width - this.backgroundWidth) / 2;
		this.y = (this.height - this.backgroundHeight) / 2;
		int contentH = this.backgroundHeight - 28 - FOOTER_ROWS;
		this.listRows = Math.max(3, contentH / ROW);
	}

	private int contentTop() {
		return this.y + 26;
	}

	private int rowY(int row) {
		return contentTop() + row * ROW;
	}

	private int labelY(int row) {
		return 26 + row * ROW + 5;
	}

	/** Нижний ряд: Отменить / Стоп. */
	private int cancelY() {
		return this.y + this.backgroundHeight - 18;
	}

	/** Ряд над отменой: основные действия. */
	private int actionY() {
		return cancelY() - 22;
	}

	private int contentBottom() {
		return actionY() - 4;
	}

	@Override
	protected void init() {
		super.init();
		fitSize();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		this.cancel = this.leftAction = this.rightAction = this.midAction = null;
		this.pickButtons.clear();
		this.pickTypes.clear();
		fitSize();

		int x = this.x + 8;
		int w = this.backgroundWidth - 16;
		boolean run = this.handler.running();
		boolean manager = run && this.handler.manager();
		boolean pick = !run && !this.showDesc && !this.configuring;
		boolean cfg = !run && !this.showDesc && this.configuring;
		boolean blockedType = this.handler.blocked(this.handler.typeIndex());

		if (this.showDesc) {
			this.midAction = addBtn("Назад", x, actionY(), w, BTN, () -> {
				this.showDesc = false;
				rebuild();
			});
		} else if (pick) {
			buildPickRows(x, w);
			int third = (w - 8) / 3;
			this.leftAction = addBtn("Описание", x, actionY(), third, BTN, () -> {
				this.showDesc = true;
				this.descScroll = 0;
				rebuild();
			});
			this.midAction = addBtn("Настроить", x + third + 4, actionY(), third, BTN, () -> {
				this.configuring = true;
				this.cfgScroll = 0;
				rebuild();
			});
			this.rightAction = addBtn("Запустить", x + w - third, actionY(), third, BTN, () -> click(8));
			this.rightAction.active = !blockedType;
		} else if (cfg) {
			buildConfigRows(x, w);
			this.leftAction = addBtn("К списку", x, actionY(), w / 2 - 3, BTN, () -> {
				this.configuring = false;
				rebuild();
			});
			this.rightAction = addBtn("Запустить", x + w / 2 + 3, actionY(), w / 2 - 3, BTN,
					() -> click(8));
			this.rightAction.active = !blockedType;
		} else if (manager && this.page == EventScreenHandler.PAGE_MEMBERS) {
			buildMemberRows(x, w);
			this.midAction = addBtn("Обновить список", x, actionY(), w, BTN,
					() -> click(EventScreenHandler.OPEN_MEMBERS));
		} else if (manager && this.page == EventScreenHandler.PAGE_PRIZE) {
			buildPrizeRows(x, w);
			int n = this.handler.selectedCount();
			this.midAction = addBtn(n > 0 ? "Выдать приз (" + n + ")" : "Выдать приз", x, actionY(), w, BTN,
					() -> click(EventScreenHandler.AWARD));
			this.midAction.active = n > 0;
		} else if (run) {
			if (manager) {
				int half = (w - 4) / 2;
				int gy = this.y + 62;
				addBtn("Участники (" + this.handler.members() + ")", x, gy, half, BTN, () -> {
					this.rosterScroll = 0;
					click(EventScreenHandler.OPEN_MEMBERS);
				});
				addBtn("Выдать приз", x + w - half, gy, half, BTN, () -> {
					this.rosterScroll = 0;
					click(EventScreenHandler.OPEN_PRIZE);
				});
				addBtn("Перезапустить", x, gy + ROW, half, BTN, () -> click(EventScreenHandler.RESTART))
						.active = !this.handler.registering();
				addBtn(this.handler.watching() ? "Вернуться" : "ТП на МП", x + w - half, gy + ROW, half, BTN,
						() -> click(EventScreenHandler.WATCH));
			}
			boolean begin = manager && this.handler.registering();
			this.leftAction = addBtn("Описание", x, actionY(), begin ? w / 2 - 3 : w, BTN, () -> {
				this.showDesc = true;
				this.descScroll = 0;
				rebuild();
			});
			if (begin) {
				this.rightAction = addBtn("Начать", x + w / 2 + 3, actionY(), w / 2 - 3, BTN, () -> click(11));
			}
		}

		if (!run) {
			this.cancel = addBtn("Отменить", x, cancelY(), w, 16, this::sendStop);
		} else if (manager && this.page != EventScreenHandler.PAGE_MAIN && !this.showDesc) {
			this.cancel = addBtn("Назад", x, cancelY(), w, 16, () -> {
				this.page = EventScreenHandler.PAGE_MAIN;
				rebuild();
			});
		} else if (manager) {
			this.cancel = addBtn("Закончить МП", x, cancelY(), w, 16, this::sendStop);
		} else {
			this.cancel = addBtn("Закрыть", x, cancelY(), w, 16, this::close);
		}
	}

	private void sendStop() {
		if (this.client != null && this.client.getNetworkHandler() != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					ru.admincontrol.net.ModPackets.EVENT_CANCEL,
					net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
		}
		click(10);
	}

	private void buildMemberRows(int x, int w) {
		this.rosterScroll = Math.max(0, Math.min(rosterMax(), this.rosterScroll));
		int kickW = 44;
		for (int i = 0; i < this.listRows; i++) {
			int index = this.rosterScroll + i;
			if (index >= this.handler.rosterSize()) {
				break;
			}
			int yy = rowY(i);
			if (yy + BTN > contentBottom()) {
				break;
			}
			String label = this.handler.rosterName(index) + (this.handler.rosterAlive(index) ? "" : "  · выбыл");
			addBtn(label, x, yy, w - 12 - kickW - 4, BTN, () -> {
			}).active = false;
			addBtn("Кик", x + w - 12 - kickW, yy, kickW, BTN, () -> click(EventScreenHandler.KICK_BASE + index));
		}
	}

	private void buildPrizeRows(int x, int w) {
		this.rosterScroll = Math.max(0, Math.min(rosterMax(), this.rosterScroll));
		for (int i = 0; i < this.listRows; i++) {
			int index = this.rosterScroll + i;
			if (index >= this.handler.rosterSize()) {
				break;
			}
			int yy = rowY(i);
			if (yy + BTN > contentBottom()) {
				break;
			}
			boolean awarded = this.handler.rosterAwarded(index);
			String mark = awarded ? "✓ " : this.handler.selected(index) ? "■ " : "□ ";
			String label = mark + this.handler.rosterName(index)
					+ (awarded ? "  · приз выдан" : this.handler.rosterAlive(index) ? "" : "  · выбыл");
			addBtn(label, x, yy, w - 12, BTN, () -> click(EventScreenHandler.SEL_BASE + index)).active = !awarded;
		}
	}

	private ButtonWidget addBtn(String text, int x, int y, int w, int h, Runnable action) {
		return this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> action.run())
				.dimensions(x, y, w, h).build());
	}

	/** Строк сетки выбора: по две кнопки МП в строке. */
	private int pickRowCount() {
		return (this.handler.choiceCount() + 1) / 2;
	}

	private void buildPickRows(int x, int w) {
		this.listScroll = Math.max(0, Math.min(listMax(), this.listScroll));
		int cell = (w - 12 - 4) / 2;
		for (int i = 0; i < this.listRows; i++) {
			int yy = rowY(i);
			if (yy + BTN > contentBottom()) {
				break;
			}
			for (int col = 0; col < 2; col++) {
				int pos = (this.listScroll + i) * 2 + col;
				if (pos >= this.handler.choiceCount()) {
					break;
				}
				int index = this.handler.choiceAt(pos);
				boolean selected = index == this.handler.typeIndex();
				String label = (selected ? "▶ " : "") + (this.handler.customChoice(index) ? "★ " : "")
						+ this.handler.choiceName(index);
				int id = TYPE_BASE + index;
				ButtonWidget button = this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
					click(id);
					this.configuring = false;
				}).dimensions(x + col * (cell + 4), yy, cell, BTN).build());
				this.pickButtons.add(button);
				this.pickTypes.add(index);
			}
		}
	}

	/** Заблокированное через /cw event МП: красная обводка кнопки и красный замок справа от названия. */
	private void drawBlocked(DrawContext context) {
		for (int i = 0; i < this.pickButtons.size(); i++) {
			if (!this.handler.blocked(this.pickTypes.get(i))) {
				continue;
			}
			ButtonWidget b = this.pickButtons.get(i);
			int bx = b.getX();
			int by = b.getY();
			int bw = b.getWidth();
			int bh = b.getHeight();
			context.drawBorder(bx - 1, by - 1, bw + 2, bh + 2, LOCK_RED);
			context.drawBorder(bx, by, bw, bh, LOCK_RED);
			int tw = this.textRenderer.getWidth(b.getMessage());
			int lx = bx + (bw - tw) / 2 + tw + 4;
			lx = Math.min(lx, bx + bw - 11);
			drawLock(context, lx, by + (bh - 9) / 2);
		}
	}

	private static void drawLock(DrawContext context, int lx, int ly) {
		context.fill(lx + 1, ly, lx + 6, ly + 1, LOCK_RED);
		context.fill(lx + 1, ly, lx + 2, ly + 4, LOCK_RED);
		context.fill(lx + 5, ly, lx + 6, ly + 4, LOCK_RED);
		context.fill(lx, ly + 4, lx + 7, ly + 9, LOCK_RED);
		context.fill(lx + 3, ly + 5, lx + 4, ly + 8, LOCK_HOLE);
	}

	/** Кнопки настроек; скролл, если строк больше, чем влезает. */
	private void buildConfigRows(int x, int w) {
		List<int[]> layout = configLayout();
		int visible = Math.max(1, (contentBottom() - contentTop()) / ROW);
		int maxScroll = Math.max(0, layout.size() - visible);
		this.cfgScroll = Math.max(0, Math.min(maxScroll, this.cfgScroll));

		for (int i = 0; i < visible; i++) {
			int index = this.cfgScroll + i;
			if (index >= layout.size()) {
				break;
			}
			int[] row = layout.get(index);
			int yy = rowY(i);
			if (yy + BTN > contentBottom()) {
				break;
			}
			int kind = row[0];
			if (kind == 1) {
				// full button: id in row[1], uses message from rebuild via click id only — store as open desc = -1
				if (row[1] == -1) {
					this.addDrawableChild(ButtonWidget.builder(Text.literal("Описание"), b -> {
						this.showDesc = true;
						this.descScroll = 0;
						rebuild();
					}).dimensions(x, yy, w, BTN).build());
				} else if (row[1] == -2) {
					this.addDrawableChild(ButtonWidget.builder(Text.literal("Только вы (тест)"), b -> {
					}).dimensions(x, yy, w, BTN).build()).active = false;
				} else if (row[1] == 16) {
					this.addDrawableChild(ButtonWidget.builder(
							Text.literal(this.handler.raceBumpers() ? "С бортиками ✓" : "Без бортиков"),
							b -> click(16)).dimensions(x, yy, w, BTN).build());
				}
			} else {
				// steppers: leftId, rightId, leftW, rightW, leftLabel, rightLabel encoded
				int leftId = row[1];
				int rightId = row[2];
				String leftL = labelFor(leftId);
				String rightL = labelFor(rightId);
				int lw = Math.max(18, this.textRenderer.getWidth(leftL) + 8);
				int rw = Math.max(18, this.textRenderer.getWidth(rightL) + 8);
				this.addDrawableChild(ButtonWidget.builder(Text.literal(leftL), b -> click(leftId))
						.dimensions(x, yy, lw, BTN).build());
				this.addDrawableChild(ButtonWidget.builder(Text.literal(rightL), b -> click(rightId))
						.dimensions(x + w - rw, yy, rw, BTN).build());
			}
		}
	}

	private String labelFor(int id) {
		return switch (id) {
			case 6 -> "−10";
			case 7 -> "+10";
			case 14 -> "−0.5";
			case 15 -> "+0.5";
			case 2, 4, 12, 17, 19, 21, 23 -> "−";
			case 3, 5, 13, 18, 20, 22, 24 -> "+";
			default -> "·";
		};
	}

	/** kind 0 = steppers [0, leftId, rightId], kind 1 = full [1, specialId] */
	private List<int[]> configLayout() {
		List<int[]> rows = new ArrayList<>();
		if (!this.handler.adminOnly()) {
			rows.add(new int[]{0, 2, 3});
		} else {
			rows.add(new int[]{1, -2});
		}
		if (this.handler.race()) {
			rows.add(new int[]{1, 16});
			rows.add(new int[]{0, 17, 18});
			rows.add(new int[]{0, 19, 20});
			rows.add(new int[]{0, 21, 22});
			if (!this.handler.raceBumpers()) {
				rows.add(new int[]{0, 23, 24});
			}
		} else if (this.handler.meat()) {
			rows.add(new int[]{0, 4, 5});
			rows.add(new int[]{0, 6, 7});
			rows.add(new int[]{0, 23, 24});
		} else {
			rows.add(new int[]{0, 4, 5});
			rows.add(new int[]{0, 6, 7});
			rows.add(new int[]{0, 12, 13});
			rows.add(new int[]{0, 14, 15});
		}
		rows.add(new int[]{1, -1});
		return rows;
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private void syncUi() {
		int sig = this.handler.typeIndex() * 31 + this.handler.players() * 17 + this.handler.reward() * 13
				+ this.handler.powerTenths() * 7 + (this.handler.running() ? 3 : 0)
				+ (this.handler.raceBumpers() ? 5 : 0) + this.handler.prize1() + this.handler.prize2()
				+ this.handler.prize3() + this.handler.raceLuck()
				+ (this.showDesc ? 11 : 0) + (this.configuring ? 19 : 0)
				+ this.handler.members() * 23 + this.handler.phase().hashCode()
				+ (this.handler.registering() ? 29 : 0)
				+ this.width * 41 + this.height * 43
				+ this.page * 47 + (this.handler.manager() ? 53 : 0) + (this.handler.watching() ? 59 : 0)
				+ this.handler.selectionMask() * 61 + this.handler.blockedMask() * 67;
		boolean run = this.handler.running();
		if (run != this.lastRunning) {
			this.lastRunning = run;
			this.configuring = run;
			this.showDesc = false;
			this.page = EventScreenHandler.PAGE_MAIN;
		}
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private List<OrderedText> descLines() {
		return this.textRenderer.wrapLines(Text.literal(this.handler.description()),
				this.backgroundWidth - 36);
	}

	private int descVisible() {
		return Math.max(3, (contentBottom() - (this.y + 40)) / 12);
	}

	private int descMax() {
		return Math.max(0, descLines().size() - descVisible());
	}

	private int cfgVisible() {
		return Math.max(1, (contentBottom() - contentTop()) / ROW);
	}

	private int cfgMax() {
		return Math.max(0, configLayout().size() - cfgVisible());
	}

	private int listMax() {
		return Math.max(0, pickRowCount() - this.listRows);
	}

	private int barX() {
		return this.x + this.backgroundWidth - 12;
	}

	private int barY() {
		return contentTop();
	}

	private int barH() {
		if (this.showDesc) {
			return descVisible() * 12;
		}
		if (rosterPage()) {
			return this.listRows * ROW;
		}
		if (this.configuring && !this.handler.running()) {
			return cfgVisible() * ROW;
		}
		return this.listRows * ROW;
	}

	private int scrollMax() {
		if (this.showDesc) {
			return descMax();
		}
		if (rosterPage()) {
			return rosterMax();
		}
		if (this.handler.running()) {
			return 0;
		}
		if (this.configuring) {
			return cfgMax();
		}
		return listMax();
	}

	private int scrollValue() {
		if (this.showDesc) {
			return this.descScroll;
		}
		if (rosterPage()) {
			return this.rosterScroll;
		}
		if (this.configuring) {
			return this.cfgScroll;
		}
		return this.listScroll;
	}

	private void setScroll(int v) {
		if (this.showDesc) {
			this.descScroll = v;
		} else if (rosterPage()) {
			this.rosterScroll = v;
			rebuild();
		} else if (this.configuring) {
			this.cfgScroll = v;
			rebuild();
		} else {
			this.listScroll = v;
			rebuild();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int max = scrollMax();
		if (max > 0) {
			int next = Math.max(0, Math.min(max, scrollValue() - (int) Math.signum(amount)));
			if (next != scrollValue()) {
				setScroll(next);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int max = scrollMax();
		if (max > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
			this.bar.dragging = true;
			setScroll(this.bar.click(mouseY, barY(), barH(), max));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging) {
			int max = scrollMax();
			if (max > 0) {
				setScroll(this.bar.click(mouseY, barY(), barH(), max));
				return true;
			}
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
		drawBlocked(context);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	private void labelBetween(DrawContext context, String text, int row, int color) {
		int pad = 44;
		int y = labelY(row);
		if (y + 8 >= this.backgroundHeight - FOOTER_ROWS) {
			return;
		}
		AcTheme.clippedCentered(this.textRenderer, context, text, pad, y,
				this.backgroundWidth - pad * 2, color);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		String title = this.handler.adminOnly() ? "Тест МП" : "Мероприятие";
		if (rosterPage()) {
			title = this.page == EventScreenHandler.PAGE_MEMBERS ? "Участники МП"
					: "Приз: " + Money.wc(this.handler.prizeAmount()) + " каждому";
		}
		AcTheme.titleCentered(this.textRenderer, context, title, 6, 7, this.backgroundWidth - 12);
		int inner = this.backgroundWidth - 20;
		int textFloor = this.backgroundHeight - FOOTER_ROWS - 2;

		if (this.showDesc) {
			AcTheme.clippedCentered(this.textRenderer, context, this.handler.typeName(), 10, 24, inner, AcTheme.NAME);
			List<OrderedText> lines = descLines();
			this.descScroll = Math.max(0, Math.min(descMax(), this.descScroll));
			int y = 38;
			int end = Math.min(lines.size(), this.descScroll + descVisible());
			for (int i = this.descScroll; i < end && y + 10 < textFloor; i++) {
				context.drawText(this.textRenderer, lines.get(i), 10, y, AcTheme.TEXT, false);
				y += 12;
			}
			if (descMax() > 0) {
				this.bar.draw(context, this.backgroundWidth - 12, 38, barH(), descMax(), descVisible(), this.descScroll);
			}
			return;
		}

		if (rosterPage()) {
			if (this.handler.rosterSize() == 0) {
				AcTheme.clippedCentered(this.textRenderer, context, "Участников нет", 10, 40, inner, AcTheme.MUTED);
			}
			if (rosterMax() > 0) {
				this.bar.draw(context, this.backgroundWidth - 12, 26, this.listRows * ROW,
						rosterMax(), this.listRows, this.rosterScroll);
			}
			return;
		}

		if (this.handler.running() && this.handler.manager()) {
			AcTheme.clippedCentered(this.textRenderer, context, this.handler.typeName(), 10, 24, inner, AcTheme.NAME);
			AcTheme.clippedCentered(this.textRenderer, context,
					this.handler.phase() + "  " + this.handler.members() + "/" + this.handler.players(),
					10, 36, inner, AcTheme.SCORE);
			AcTheme.clippedCentered(this.textRenderer, context, prizeLine(), 10, 48, inner, AcTheme.SCORE);
			return;
		}

		if (this.handler.running()) {
			AcTheme.clippedCentered(this.textRenderer, context, this.handler.typeName(), 10, 28, inner, AcTheme.NAME);
			AcTheme.clippedCentered(this.textRenderer, context,
					this.handler.phase() + "  " + this.handler.members() + "/" + this.handler.players(),
					10, 48, inner, AcTheme.SCORE);
			AcTheme.clippedCentered(this.textRenderer, context, prizeLine(), 10, 68, inner, AcTheme.SCORE);
			AcTheme.clippedCentered(this.textRenderer, context,
					"Управляет организатор или куратор+", 10, 88, inner, AcTheme.MUTED);
			return;
		}

		if (!this.configuring) {
			if (listMax() > 0) {
				this.bar.draw(context, this.backgroundWidth - 12, 26, this.listRows * ROW,
						listMax(), this.listRows, this.listScroll);
			}
			return;
		}

		// подписи к видимым строкам настроек
		List<int[]> layout = configLayout();
		int visible = cfgVisible();
		this.cfgScroll = Math.max(0, Math.min(cfgMax(), this.cfgScroll));
		if (this.handler.blocked(this.handler.typeIndex())) {
			AcTheme.clippedCentered(this.textRenderer, context, this.handler.typeName() + " — заблокировано",
					10, 16, inner, LOCK_RED);
		} else {
			AcTheme.clippedCentered(this.textRenderer, context, this.handler.typeName(), 10, 16, inner, AcTheme.NAME);
		}

		for (int i = 0; i < visible; i++) {
			int index = this.cfgScroll + i;
			if (index >= layout.size()) {
				break;
			}
			if (labelY(i) + 8 >= textFloor) {
				break;
			}
			String text = labelForRow(layout.get(index), index);
			if (text != null) {
				labelBetween(context, text, i, AcTheme.SCORE);
			}
		}
		if (cfgMax() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 26, cfgVisible() * ROW,
					cfgMax(), cfgVisible(), this.cfgScroll);
		}
	}

	private String prizeLine() {
		if (this.handler.race()) {
			return "1: " + Money.format(this.handler.prize1()) + "  2: " + Money.format(this.handler.prize2())
					+ "  3: " + Money.wc(this.handler.prize3());
		}
		if (this.handler.meat()) {
			return "Приз " + Money.wc(this.handler.reward()) + "  удача " + this.handler.luckLabel();
		}
		return "Приз " + Money.wc(this.handler.reward()) + "  сила " + this.handler.powerLabel();
	}

	private String labelForRow(int[] row, int absoluteIndex) {
		if (row[0] == 1) {
			return null;
		}
		int left = row[1];
		if (left == 2) {
			if (this.handler.base() == 3) {
				return this.handler.players() + " мест (двое)";
			}
			return this.handler.players() + " мест (от " + EventService.MIN_PLAYERS + ")";
		}
		if (left == 17) {
			return "1 место  " + Money.wc(this.handler.prize1());
		}
		if (left == 19) {
			return "2 место  " + Money.wc(this.handler.prize2());
		}
		if (left == 21) {
			return "3 место  " + Money.wc(this.handler.prize3());
		}
		if (left == 23) {
			return "удача " + this.handler.luckLabel();
		}
		if (left == 4) {
			return Money.wc(this.handler.reward());
		}
		if (left == 6 && this.handler.meat()) {
			return "приз −10 / +10";
		}
		if (left == 6) {
			return "приз −10 / +10";
		}
		if (left == 12) {
			return "сила " + this.handler.powerLabel();
		}
		if (left == 14) {
			return "сила −0.5 / +0.5";
		}
		return null;
	}
}
