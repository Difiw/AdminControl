package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.AntiCheatScreenHandler;
import ru.admincontrol.util.AntiCheatRules;

/** /anticheat: вкладка «Проверки» (что и как ловить, тест на себе) и «Наказания» (что выдавать за каждое нарушение). */
public class AntiCheatScreen extends HandledScreen<AntiCheatScreenHandler> {
	private static final int W = 364;
	private static final int TOP = 46;
	private static final int ROW = 20;
	private static final int CONTROLS = 96;
	private static final int PAGE_CHECKS = 0;
	private static final int PAGE_PUNISH = 1;
	/** Вкладка «Наказания»: колонки «вид наказания» и «срок». */
	private static final int KIND_X = 150;
	private static final int KIND_W = 128;
	private static final int AMOUNT_X = 284;
	private static final int AMOUNT_W = 72;
	private static final String[] LABELS = {
			"Античит: сам наказывает, если уверен",
			"Проверка читов: только сообщает админам",
			"Детектор X-Ray (руда и ресурспаки)",
			"Автокликер: лимит кликов в секунду",
			"Автокликер: сброс ступеней через",
			"X-Ray: скрытой руды для срабатывания",
			"X-Ray: за сколько секунд",
			"Проверка АФК",
			"АФК: наказание через",
			"Наказывать админов (иначе — сообщение)",
			"Тест /actest (без таймера)",
			"Тест /actest: кик при активном чите",
			"Поиск багаюза: в баг-лист, без наказаний",
			"Ошибки сервера тоже в баг-лист"
	};
	private int page = PAGE_CHECKS;
	private int signature = Integer.MIN_VALUE;

	public AntiCheatScreen(AntiCheatScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = W;
		this.backgroundHeight = TOP + AntiCheatScreenHandler.SETTINGS * ROW + 8;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private boolean hasAmount(int kind) {
		return kind == AntiCheatRules.BAN || kind == AntiCheatRules.JAIL;
	}

	private int signature() {
		int sig = this.page;
		for (int i = 0; i < AntiCheatScreenHandler.SETTINGS; i++) {
			if (AntiCheatScreenHandler.isToggle(i)) {
				sig = sig * 31 + this.handler.get(i);
			}
		}
		for (int i = 0; i < AntiCheatRules.KEYS.length; i++) {
			sig = sig * 31 + (hasAmount(this.handler.ruleKind(i)) ? 1 : 0);
		}
		return sig;
	}

	private void rebuild() {
		this.signature = signature();
		this.clearChildren();
		int half = (W - 20) / 2;
		ButtonWidget checks = button(this.page == PAGE_CHECKS ? "▶ Проверки" : "Проверки", this.x + 8, this.y + 20, half,
				() -> switchPage(PAGE_CHECKS));
		ButtonWidget punish = button(this.page == PAGE_PUNISH ? "▶ Наказания" : "Наказания", this.x + W - 8 - half, this.y + 20, half,
				() -> switchPage(PAGE_PUNISH));
		checks.active = this.page != PAGE_CHECKS;
		punish.active = this.page != PAGE_PUNISH;
		if (this.page == PAGE_CHECKS) {
			buildChecks();
		} else {
			buildPunish();
		}
		AcNavButtons.add(this);
	}

	private void switchPage(int next) {
		this.page = next;
		rebuild();
	}

	private void buildChecks() {
		int cx = this.x + W - 8 - CONTROLS;
		for (int row = 0; row < AntiCheatScreenHandler.SETTINGS; row++) {
			int y = this.y + TOP + row * ROW;
			int r = row;
			if (row == AntiCheatScreenHandler.TEST) {
				boolean on = this.handler.get(row) != 0;
				String label = on ? "§a/actest вкл — стоп" : "Вкл /actest";
				button(label, cx, y, CONTROLS, () -> click(AntiCheatScreenHandler.plus(r)));
			} else if (AntiCheatScreenHandler.isToggle(row)) {
				boolean on = this.handler.get(row) != 0;
				button(on ? "§aВключено" : "§cВыключено", cx, y, CONTROLS, () -> click(AntiCheatScreenHandler.plus(r)));
			} else {
				button("−", cx, y, 20, () -> click(AntiCheatScreenHandler.minus(r)));
				button("+", cx + CONTROLS - 20, y, 20, () -> click(AntiCheatScreenHandler.plus(r)));
			}
		}
	}

	private void buildPunish() {
		for (int i = 0; i < AntiCheatRules.KEYS.length; i++) {
			int y = this.y + TOP + i * ROW;
			int kindId = AntiCheatScreenHandler.KIND_BASE + i * 2;
			button("◀", this.x + KIND_X, y, 16, () -> click(kindId));
			button("▶", this.x + KIND_X + KIND_W - 16, y, 16, () -> click(kindId + 1));
			if (hasAmount(this.handler.ruleKind(i))) {
				int amountId = AntiCheatScreenHandler.AMOUNT_BASE + i * 2;
				button("−", this.x + AMOUNT_X, y, 16, () -> click(amountId));
				button("+", this.x + AMOUNT_X + AMOUNT_W - 16, y, 16, () -> click(amountId + 1));
			}
		}
	}

	private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
		return this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
				.dimensions(x, y, w, 18).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private String value(int row) {
		int v = this.handler.get(row);
		return switch (row) {
			case AntiCheatScreenHandler.CLICKS -> v <= 0 ? "выкл" : v + " к/с";
			case AntiCheatScreenHandler.REPEAT, AntiCheatScreenHandler.XRAY_WINDOW -> v + " с";
			case AntiCheatScreenHandler.AFK_MIN -> v + " мин";
			case AntiCheatScreenHandler.TEST_MIN -> "команда /actest";
			default -> String.valueOf(v);
		};
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (signature() != this.signature) {
			rebuild();
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, W, this.backgroundHeight, 20);
		int rows = this.page == PAGE_CHECKS ? AntiCheatScreenHandler.SETTINGS : AntiCheatRules.KEYS.length;
		for (int row = 0; row < rows; row += 2) {
			AcTheme.row(context, this.x + 4, this.y + TOP + row * ROW - 1, W - 10, ROW);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Настройки античита", 8, 6, W - 16);
		if (this.page == PAGE_CHECKS) {
			int cx = W - 8 - CONTROLS;
			for (int row = 0; row < AntiCheatScreenHandler.SETTINGS; row++) {
				int y = TOP + row * ROW + 5;
				AcTheme.clipped(this.textRenderer, context, LABELS[row], 8, y, cx - 14, AcTheme.TEXT);
				if (!AntiCheatScreenHandler.isToggle(row)) {
					AcTheme.clippedCentered(this.textRenderer, context, value(row), cx + 20, y, CONTROLS - 40, AcTheme.GOLD);
				}
			}
			return;
		}
		for (int i = 0; i < AntiCheatRules.KEYS.length; i++) {
			int y = TOP + i * ROW + 5;
			int kind = Math.max(0, Math.min(AntiCheatRules.KIND_LABELS.length - 1, this.handler.ruleKind(i)));
			AcTheme.clipped(this.textRenderer, context, AntiCheatRules.LABELS[i], 8, y, KIND_X - 12, AcTheme.TEXT);
			AcTheme.clippedCentered(this.textRenderer, context, AntiCheatRules.KIND_LABELS[kind], KIND_X + 16, y,
					KIND_W - 32, kind == AntiCheatRules.NONE ? AcTheme.TEXT : AcTheme.GOLD);
			if (hasAmount(kind)) {
				int amount = this.handler.ruleAmount(i);
				String text = kind == AntiCheatRules.JAIL ? amount + " мин" : amount + " дн";
				AcTheme.clippedCentered(this.textRenderer, context, text, AMOUNT_X + 16, y, AMOUNT_W - 32, AcTheme.GOLD);
			}
		}
		int y = TOP + AntiCheatRules.KEYS.length * ROW + 8;
		AcTheme.wrapped(this.textRenderer, context, "«Предупреждение» — надпись на экране, без записи в наказания. "
				+ "Варн — как обычный: на 3/3 бан 10 дней. «Только админам» — игрока не трогают, админам приходит сообщение.",
				8, y, W - 16, 0x8A96A8);
		AcTheme.wrapped(this.textRenderer, context, "Администрацию античит наказывает, только если включено «Наказывать админов». "
				+ "/actest — без таймера: выключенный чит только пишет в чат; активный — сразу кик и сброс /actest.",
				8, y + 46, W - 16, 0x8A96A8);
	}
}
