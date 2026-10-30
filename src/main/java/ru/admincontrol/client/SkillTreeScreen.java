package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.skill.SkillTree.Branch;
import ru.admincontrol.skill.SkillTree.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Окно «Дерево навыков». Обзор — ствол и большие ветки: колесо, стрелки или наведение выбирают ветку, Enter или клик
 * открывает её. В ветке улучшения стоят по линиям (столбцам с названиями), колесо — плавный масштаб, Esc/ПКМ — назад.
 * Справа — подробности выбранного улучшения и задания.
 */
public class SkillTreeScreen extends Screen {
	private static final int R = 15;
	private static final int ROW_STEP = 76;
	private static final int LABEL_W = 112;
	private static final int BASE_SPREAD = 124;
	private static final int LEAN_SPREAD = 24;
	private static final int FIRST = 84;
	private static final int DROP = 22;
	private static final int TRUNK = 62;
	private static final int CROWN_TOP = R + 46;
	private static final int PLATE_W = 170;
	private static final int PLATE_H = 32;
	private static final int O_STEP = 124;
	private static final int O_R = 24;
	private static final int O_TOP = 112;
	private static final int OVERVIEW_W = O_STEP * SkillTree.BRANCHES.size() + 40;
	private static final int OVERVIEW_H = 330;
	private static final float MAX_ZOOM = 1.8f;
	private static final int BAR_Y = 42;
	private static final int BOTTOM = 21;
	private static final long NOTICE_MS = 8000;
	private static final long OPEN_MS = 240;
	private static final long BURST_MS = 750;
	/** Подсветка ветки гаснет за 3 секунды после ухода курсора / остановки колёсика. */
	private static final long GLOW_MS = 3000;
	private static final int TOOLTIP_W = 130;
	private static final int NODE_TOOLTIP_W = 170;

	private static Branch open;
	/** −1 — ничего не подсвечено (так при открытии меню). */
	private static int focus = -1;
	private static long lastGlowAt;
	private static float zoom = -1f;
	private static float tZoom = -1f;
	private static double panX;
	private static double panY;
	private static double tPanX;
	private static double tPanY;

	private enum St { MAX, BUY, POOR, LOCKED }

	private record Burst(String node, long start, boolean ok) {
	}

	private SkillClient.State state;
	private final boolean backToInventory;
	private Node selected = SkillTree.NODES.get(0);
	private Node hovered;
	private Branch hoveredBranch;
	private Branch lastHoverBranch;
	private String pending;
	private long pendingAt;
	private String notice = "";
	private boolean noticeOk;
	private long noticeAt;
	private boolean showHistory;
	private int detailScroll;
	private int detailMax;
	private boolean dragging;
	private double dragDist;
	private float fitZoom = 1f;
	private final Map<Object, ItemStack> icons = new HashMap<>();

	private long lastFrame;
	private long overviewStart;
	private boolean overviewFromBranch;
	private long branchStart;
	private Branch opening;
	private long openingStart;
	private final float[] focusA = new float[SkillTree.BRANCHES.size()];
	private final Map<String, Float> hoverA = new HashMap<>();
	private final List<Burst> bursts = new ArrayList<>();
	private float camX = -1f;
	private float ovScale = 1f;

	private int vx;
	private int vy;
	private int vw;
	private int vh;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int buyX;
	private int buyY;
	private int buyW;
	private boolean buyShown;
	private int tabY;
	private boolean tabShown;

	public SkillTreeScreen(SkillClient.State state) {
		super(Text.literal("Дерево навыков"));
		this.state = state;
		this.backToInventory = !state.viewOnly && SkillClient.takeOpenedFromInventory();
		if (open != null) {
			this.selected = SkillTree.nodesOf(open).get(0);
		}
		long now = Util.getMeasuringTimeMs();
		this.overviewStart = now;
		this.branchStart = now;
		this.lastFrame = now;
		if (open == null) {
			focus = -1;
			lastGlowAt = 0;
			java.util.Arrays.fill(this.focusA, 0f);
		}
		applyNotice(state);
	}

	@Override
	public void close() {
		if (this.backToInventory && this.client != null && this.client.player != null) {
			this.client.setScreen(new InventoryScreen(this.client.player));
			return;
		}
		super.close();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			super.close();
			return true;
		}
		if (this.opening == null) {
			if (open == null) {
				switch (keyCode) {
					case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> {
						moveFocus(-1);
						return true;
					}
					case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> {
						moveFocus(1);
						return true;
					}
					case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
						if (focus >= 0 && focus < SkillTree.BRANCHES.size()) {
							startOpen(SkillTree.BRANCHES.get(focus));
						}
						return true;
					}
					default -> {
					}
				}
			} else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				showOverview();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	public void onState(SkillClient.State next) {
		long now = Util.getMeasuringTimeMs();
		boolean bought = false;
		for (Node n : SkillTree.NODES) {
			if (next.node(n.id).level() > this.state.node(n.id).level()) {
				this.bursts.add(new Burst(n.id, now, true));
				bought = true;
			}
		}
		if (bought) {
			uiSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.6f);
		} else if (this.pending != null && !next.ok) {
			this.bursts.add(new Burst(this.pending, now, false));
			uiSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f);
		}
		this.state = next;
		this.pending = null;
		applyNotice(next);
	}

	private void applyNotice(SkillClient.State s) {
		if (s != null && s.notice != null && !s.notice.isEmpty()) {
			this.notice = s.notice;
			this.noticeOk = s.ok;
			this.noticeAt = System.currentTimeMillis();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		this.pw = Math.max(150, Math.min(210, (int) (this.width * 0.27)));
		this.px = this.width - 4 - this.pw;
		this.py = BAR_Y;
		this.ph = this.height - this.py - BOTTOM;
		this.vx = 6;
		this.vy = BAR_Y;
		this.vw = this.px - 4 - this.vx;
		this.vh = this.ph;
		this.fitZoom = fitScale();
		if (open != null) {
			if (zoom < 0) {
				fit();
			} else {
				tZoom = Math.max(this.fitZoom, Math.min(maxZoom(), tZoom));
				zoom = Math.max(this.fitZoom, Math.min(maxZoom(), zoom));
			}
			clampTarget();
		} else {
			this.ovScale = this.fitZoom * 1.236f;
			if (this.camX < 0) {
				this.camX = focus < 0 ? OVERVIEW_W / 2f
						: OVERVIEW_W / 2f + (branchX(focus) - OVERVIEW_W / 2f) * 0.22f;
			}
		}
		int by = this.height - 17;
		int x = 6;
		if (open != null) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("‹ Все ветки"), btn -> showOverview())
					.dimensions(x, by, 66, 14)
					.tooltip(Tooltip.of(Text.literal("Вернуться к выбору ветки (Esc, Backspace или правый клик)"))).build());
			x += 70;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), btn -> zoomAt(this.vx + this.vw / 2.0, this.vy + this.vh / 2.0, 1 / 1.2f))
					.dimensions(x, by, 16, 14).tooltip(Tooltip.of(Text.literal("Отдалить (колесо мыши)"))).build());
			x += 18;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> zoomAt(this.vx + this.vw / 2.0, this.vy + this.vh / 2.0, 1.2f))
					.dimensions(x, by, 16, 14).tooltip(Tooltip.of(Text.literal("Приблизить (колесо мыши)"))).build());
			x += 18;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Весь вид"), btn -> {
				tZoom = this.fitZoom;
				tPanX = (this.vw - canvasW() * tZoom) / 2.0;
				tPanY = (this.vh - canvasH() * tZoom) / 2.0;
				clampTarget();
			}).dimensions(x, by, 52, 14).build());
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), btn -> this.close())
				.dimensions(this.width - 4 - 58, by, 58, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Обновить"), btn -> SkillClient.refresh())
				.dimensions(this.width - 4 - 58 - 62, by, 58, 14).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("★ ЗАДАНИЯ"), btn -> openQuests())
				.dimensions(Math.max(176, this.width / 2 - 78), by, 156, 14)
				.tooltip(Tooltip.of(Text.literal("Открыть отдельный экран заданий, прогресса и наград"))).build());
	}

	private void openQuests() {
		if (this.client != null) {
			this.client.setScreen(new QuestScreen(this.state));
		}
	}

	// ---------- анимация ----------

	private static float clamp01(double v) {
		return (float) Math.max(0, Math.min(1, v));
	}

	private static float easeOut(double t) {
		double u = 1 - clamp01(t);
		return (float) (1 - u * u * u);
	}

	private static float easeBack(double t) {
		double x = clamp01(t);
		double c1 = 1.70158;
		double c3 = c1 + 1;
		return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
	}

	private static float approach(float value, float target, float dt, float speed) {
		return value + (target - value) * (1 - (float) Math.exp(-dt * speed));
	}

	private static void around(DrawContext context, float x, float y, float scale) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0f);
		context.getMatrices().scale(scale, scale, 1f);
		context.getMatrices().translate(-x, -y, 0f);
	}

	private static void restore(DrawContext context) {
		context.getMatrices().pop();
	}

	// ---------- раскладка ----------

	private static List<Node> nodes() {
		return open == null ? List.of() : SkillTree.nodesOf(open);
	}

	private static int lanes(Branch b) {
		int max = b.lanes().size();
		for (Node n : SkillTree.nodesOf(b)) {
			max = Math.max(max, n.col + 1);
		}
		return Math.max(1, max);
	}

	private static int maxRow() {
		int max = 0;
		for (Node n : nodes()) {
			max = Math.max(max, n.row);
		}
		return max;
	}

	private static double lean(int col) {
		return open == null ? 0 : col - (lanes(open) - 1) / 2.0;
	}

	/** Сторона, куда растут боковые веточки линии: наружу от ствола, у средней линии — поочерёдно. */
	private static int side(Node n) {
		double l = lean(n.col);
		return l > 0.01 ? 1 : l < -0.01 ? -1 : (n.row % 2 == 0 ? 1 : -1);
	}

	private static int forkY() {
		return CROWN_TOP + FIRST + maxRow() * ROW_STEP;
	}

	private static int rootY() {
		return forkY() + TRUNK;
	}

	private static int canvasW() {
		if (open == null) {
			return OVERVIEW_W;
		}
		double mid = (lanes(open) - 1) / 2.0;
		int half = (int) Math.ceil(mid * (BASE_SPREAD + LEAN_SPREAD * maxRow())) + 10 + LABEL_W / 2 + 14;
		return Math.max(PLATE_W + 200, half * 2);
	}

	private static int canvasH() {
		return open == null ? OVERVIEW_H : rootY() + PLATE_H + 34;
	}

	private static int nodeX(Node n) {
		double wiggle = n.row > 0 ? Math.sin(n.col * 1.7 + n.row * 2.3) * 8 : 0;
		return (int) Math.round(canvasW() / 2.0 + lean(n.col) * (BASE_SPREAD + LEAN_SPREAD * n.row) + wiggle);
	}

	private static int nodeY(Node n) {
		return forkY() - FIRST - n.row * ROW_STEP + (int) Math.round(Math.abs(lean(n.col)) * DROP);
	}

	private static int branchX(int i) {
		return (int) Math.round(OVERVIEW_W / 2.0 + (i - (SkillTree.BRANCHES.size() - 1) / 2.0) * O_STEP);
	}

	private static int branchY(int i) {
		double d = i - (SkillTree.BRANCHES.size() - 1) / 2.0;
		return (int) Math.round(O_TOP + d * d * (SkillTree.BRANCHES.size() > 6 ? 8 : 11));
	}

	private float fitScale() {
		if (open == null) {
			float s = Math.min((this.vw - 30) / (float) OVERVIEW_W, (this.vh - 24) / (float) OVERVIEW_H);
			return Math.max(0.3f, Math.min(1.6f, s));
		}
		float s = Math.min((this.vw - 12) / (float) canvasW(), (this.vh - 12) / (float) canvasH());
		return Math.max(0.3f, Math.min(1.25f, s));
	}

	private float maxZoom() {
		return Math.max(this.fitZoom, MAX_ZOOM);
	}

	private void fit() {
		zoom = tZoom = this.fitZoom;
		panX = tPanX = (this.vw - canvasW() * zoom) / 2.0;
		panY = tPanY = (this.vh - canvasH() * zoom) / 2.0;
	}

	private double clampAxis(double pan, double size, double view) {
		if (size + 16 <= view) {
			return (view - size) / 2.0;
		}
		return Math.max(view - size - 8, Math.min(8, pan));
	}

	private void clampTarget() {
		tPanX = clampAxis(tPanX, canvasW() * tZoom, this.vw);
		tPanY = clampAxis(tPanY, canvasH() * tZoom, this.vh);
	}

	private void zoomAt(double mx, double my, float factor) {
		if (open == null) {
			return;
		}
		double cx = (mx - this.vx - tPanX) / tZoom;
		double cy = (my - this.vy - tPanY) / tZoom;
		tZoom = Math.max(this.fitZoom, Math.min(maxZoom(), tZoom * factor));
		tPanX = mx - this.vx - cx * tZoom;
		tPanY = my - this.vy - cy * tZoom;
		clampTarget();
	}

	private void moveFocus(int dir) {
		int n = SkillTree.BRANCHES.size();
		if (focus < 0) {
			focus = dir > 0 ? 0 : n - 1;
		} else {
			focus = ((focus + dir) % n + n) % n;
		}
		touchGlow();
		uiSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6f, 0.9f);
	}

	private static void touchGlow() {
		lastGlowAt = Util.getMeasuringTimeMs();
	}

	/** 1 — полная подсветка выбранной ветки; 0 — погасла (через GLOW_MS после последнего действия). */
	private static float glowStrength(long now) {
		if (focus < 0 || lastGlowAt <= 0) {
			return 0f;
		}
		long idle = now - lastGlowAt;
		if (idle <= 0) {
			return 1f;
		}
		if (idle >= GLOW_MS) {
			return 0f;
		}
		return 1f - idle / (float) GLOW_MS;
	}

	private void startOpen(Branch b) {
		if (this.opening != null) {
			return;
		}
		if (SkillTree.nodesOf(b).isEmpty()) {
			focus = SkillTree.BRANCHES.indexOf(b);
			this.notice = "Ветка «" + b.title() + "» откроется вместе с казино в одном из обновлений.";
			this.noticeOk = false;
			this.noticeAt = System.currentTimeMillis();
			return;
		}
		focus = SkillTree.BRANCHES.indexOf(b);
		this.opening = b;
		this.openingStart = Util.getMeasuringTimeMs();
		uiSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f);
	}

	private void finishOpen() {
		Branch b = this.opening;
		this.opening = null;
		open = b;
		zoom = -1f;
		if (this.selected == null || this.selected.branch != b) {
			select(SkillTree.nodesOf(b).get(0));
		}
		this.hoveredBranch = null;
		this.branchStart = Util.getMeasuringTimeMs();
		this.clearAndInit();
	}

	private void showOverview() {
		if (open != null) {
			focus = Math.max(0, SkillTree.BRANCHES.indexOf(open));
		}
		open = null;
		this.hovered = null;
		this.lastHoverBranch = null;
		this.overviewStart = Util.getMeasuringTimeMs();
		this.overviewFromBranch = true;
		this.clearAndInit();
	}

	private void select(Node n) {
		if (n != this.selected) {
			this.selected = n;
			this.detailScroll = 0;
			uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.8f);
		}
	}

	// ---------- состояние узлов ----------

	private ItemStack icon(Object key, net.minecraft.item.Item item) {
		return this.icons.computeIfAbsent(key, k -> new ItemStack(item));
	}

	private ItemStack icon(Node n) {
		return icon(n, n.icon);
	}

	private List<SkillTree.Req> missing(Node n) {
		List<SkillTree.Req> out = new ArrayList<>();
		for (SkillTree.Req r : n.requires) {
			if (this.state.node(r.node()).level() < r.level()) {
				out.add(r);
			}
		}
		return out;
	}

	private St status(Node n) {
		SkillClient.NodeState ns = this.state.node(n.id);
		if (ns.level() >= ns.max()) {
			return St.MAX;
		}
		if (!missing(n).isEmpty()) {
			return St.LOCKED;
		}
		return this.state.coins(n.branch.currency().id()) >= n.cost(ns.level() + 1) ? St.BUY : St.POOR;
	}

	private String effectText(Node n, int level) {
		if (n == SkillTree.FURNACE) {
			return level <= 0 ? "нет" : "автопечь ур. " + level;
		}
		return SkillTree.effect(n, level);
	}

	private String missingText(Node n) {
		List<String> parts = new ArrayList<>();
		for (SkillTree.Req r : missing(n)) {
			Node need = SkillTree.node(r.node());
			if (need != null) {
				parts.add("«" + need.title + "» ур. " + r.level());
			}
		}
		return String.join(", ", parts);
	}

	private String statusText(Node n) {
		SkillClient.NodeState ns = this.state.node(n.id);
		SkillTree.Currency c = n.branch.currency();
		return switch (status(n)) {
			case MAX -> "Максимальный уровень.";
			case LOCKED -> "Закрыто: сначала откройте " + missingText(n) + ".";
			case POOR -> "Не хватает " + (n.cost(ns.level() + 1) - this.state.coins(c.id())) + " " + c.coin() + ".";
			case BUY -> ns.level() > 0 ? "Доступно следующее улучшение." : "Можно купить.";
		};
	}

	private int statusColor(St st) {
		return switch (st) {
			case MAX -> 0x55FF77;
			case BUY -> 0xFFD34D;
			case POOR -> 0xFF9944;
			case LOCKED -> 0xFF5555;
		};
	}

	private Set<String> chain(Node from) {
		Set<String> out = new HashSet<>();
		List<Node> stack = new ArrayList<>();
		if (from != null) {
			stack.add(from);
		}
		while (!stack.isEmpty()) {
			Node n = stack.remove(stack.size() - 1);
			if (!out.add(n.id)) {
				continue;
			}
			for (SkillTree.Req r : n.requires) {
				Node p = SkillTree.node(r.node());
				if (p != null) {
					stack.add(p);
				}
			}
		}
		return out;
	}

	/** {куплено уровней, всего уровней, узлов можно купить сейчас} по ветке (или по линии, если {@code col >= 0}). */
	private int[] progress(Branch b, int col) {
		int have = 0;
		int total = 0;
		int ready = 0;
		for (Node n : SkillTree.nodesOf(b)) {
			if (col >= 0 && n.col != col) {
				continue;
			}
			SkillClient.NodeState ns = this.state.node(n.id);
			have += Math.min(ns.level(), ns.max());
			total += ns.max();
			if (status(n) == St.BUY) {
				ready++;
			}
		}
		return new int[] {have, total, ready};
	}

	// ---------- попадания мышью ----------

	private boolean inViewport(double mx, double my) {
		return mx >= this.vx && mx < this.vx + this.vw - 2 && my >= this.vy && my < this.vy + this.vh - 2;
	}

	private boolean inPanel(double mx, double my) {
		return mx >= this.px && mx < this.px + this.pw - 2 && my >= this.py && my < this.py + this.ph - 2;
	}

	private Node nodeAt(double mx, double my) {
		if (open == null || !inViewport(mx, my)) {
			return null;
		}
		double cx = (mx - this.vx - panX) / zoom;
		double cy = (my - this.vy - panY) / zoom;
		for (Node n : nodes()) {
			double dx = cx - nodeX(n);
			double dy = cy - nodeY(n);
			float hover = this.hoverA.getOrDefault(n.id, 0f);
			int hit = R + 3 + (int) (R * 1.2f * hover);
			if (dx * dx + dy * dy <= hit * hit) {
				return n;
			}
			int half = labelWidth(n) / 2;
			if (Math.abs(dx) <= half && dy >= R && dy <= R + 29) {
				return n;
			}
		}
		return null;
	}

	private Branch branchAt(double mx, double my) {
		if (open != null || this.opening != null || !inViewport(mx, my)) {
			return null;
		}
		double cx = (mx - (this.vx + this.vw / 2.0)) / this.ovScale + this.camX;
		double cy = (my - (this.vy + this.vh / 2.0)) / this.ovScale + OVERVIEW_H / 2.0;
		for (int i = 0; i < SkillTree.BRANCHES.size(); i++) {
			double dx = cx - branchX(i);
			double dy = cy - branchY(i);
			if (Math.abs(dx) <= O_STEP / 2.0 - 4 && dy >= -O_R - 70 && dy <= O_R + 50) {
				return SkillTree.BRANCHES.get(i);
			}
		}
		return null;
	}

	@Override
	public void tick() {
		if (this.pending != null && System.currentTimeMillis() - this.pendingAt > 3000) {
			this.pending = null;
			SkillClient.refresh();
		}
	}

	// ---------- отрисовка ----------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = Util.getMeasuringTimeMs();
		float dt = Math.min(0.1f, (now - this.lastFrame) / 1000f);
		this.lastFrame = now;
		if (this.opening != null && now - this.openingStart >= OPEN_MS) {
			finishOpen();
		}
		this.renderBackground(context);
		this.hovered = this.dragging || this.opening != null ? null : nodeAt(mouseX, mouseY);
		this.hoveredBranch = branchAt(mouseX, mouseY);
		if (this.hoveredBranch != null) {
			int next = SkillTree.BRANCHES.indexOf(this.hoveredBranch);
			if (next != focus) {
				uiSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6f, 0.9f);
			}
			focus = next;
			touchGlow();
		}
		this.lastHoverBranch = this.hoveredBranch;
		float glow = glowStrength(now);
		for (int i = 0; i < this.focusA.length; i++) {
			float target = i == focus ? glow : 0f;
			// Поток по ветке гаснет медленнее обводки — плавно, а не рывком.
			float speed = target < this.focusA[i] ? 3.2f : 12f;
			this.focusA[i] = approach(this.focusA[i], target, dt, speed);
		}
		for (Node n : nodes()) {
			this.hoverA.put(n.id, approach(this.hoverA.getOrDefault(n.id, 0f), n == this.hovered ? 1f : 0f, dt, 16f));
		}
		if (open != null && zoom > 0) {
			zoom = approach(zoom, tZoom, dt, 14f);
			panX = panX + (tPanX - panX) * (1 - Math.exp(-dt * 14f));
			panY = panY + (tPanY - panY) * (1 - Math.exp(-dt * 14f));
		}
		drawTopBar(context);
		AcTheme.panel(context, this.vx, this.vy, this.vw, this.vh, 0);
		if (this.state.viewOnly) {
			int c = 0xC0FF4040;
			context.fill(this.vx - 2, this.vy - 2, this.vx + this.vw, this.vy - 1, c);
			context.fill(this.vx - 2, this.vy + this.vh - 1, this.vx + this.vw, this.vy + this.vh, c);
			context.fill(this.vx - 2, this.vy - 2, this.vx - 1, this.vy + this.vh, c);
			context.fill(this.vx + this.vw - 1, this.vy - 2, this.vx + this.vw, this.vy + this.vh, c);
		}
		float fade;
		context.enableScissor(this.vx, this.vy, this.vx + this.vw - 2, this.vy + this.vh - 2);
		context.getMatrices().push();
		if (open == null) {
			float intro = easeOut((now - this.overviewStart) / 380.0);
			float targetCam = focus < 0 ? OVERVIEW_W / 2f
					: OVERVIEW_W / 2f + (branchX(focus) - OVERVIEW_W / 2f) * 0.22f;
			this.camX = this.camX < 0 ? targetCam : approach(this.camX, targetCam, dt, 6f);
			float focusZoom = 0f;
			for (float a : this.focusA) {
				focusZoom = Math.max(focusZoom, a);
			}
			this.ovScale = this.fitZoom * 1.236f * (1f + 0.12f * focusZoom)
					* (this.overviewFromBranch ? 1 + 0.35f * (1 - intro) : 1f);
			context.getMatrices().translate(this.vx + this.vw / 2f, this.vy + this.vh / 2f, 0f);
			context.getMatrices().scale(this.ovScale, this.ovScale, 1f);
			context.getMatrices().translate(-this.camX, -OVERVIEW_H / 2f, 0f);
			fade = this.overviewFromBranch ? 1 - intro : 0.5f * (1 - intro);
			if (this.opening != null) {
				int i = SkillTree.BRANCHES.indexOf(this.opening);
				float t = easeOut((now - this.openingStart) / (double) OPEN_MS);
				around(context, branchX(i), branchY(i), 1 + 1.8f * t);
				drawOverview(context, now);
				restore(context);
				fade = t;
			} else {
				drawOverview(context, now);
			}
		} else {
			context.getMatrices().translate((float) (this.vx + panX), (float) (this.vy + panY), 0f);
			context.getMatrices().scale(zoom, zoom, 1f);
			drawBranch(context, now);
			fade = 1 - easeOut((now - this.branchStart) / 320.0);
		}
		context.getMatrices().pop();
		if (fade > 0.01f) {
			context.getMatrices().push();
			context.getMatrices().translate(0f, 0f, 300f);
			context.fill(this.vx, this.vy, this.vx + this.vw, this.vy + this.vh, ((int) (fade * 230) << 24) | 0x080604);
			context.getMatrices().pop();
		}
		context.disableScissor();
		drawViewportText(context);
		drawDetail(context);
		super.render(context, mouseX, mouseY, delta);
		if (this.hovered != null) {
			context.drawOrderedTooltip(this.textRenderer, wrapTooltip(tooltip(this.hovered)), mouseX, mouseY);
		} else if (this.hoveredBranch != null) {
			context.drawOrderedTooltip(this.textRenderer, branchTooltip(this.hoveredBranch), mouseX, mouseY);
		}
	}

	private boolean npcTree() {
		return !this.state.viewOnly && this.client != null && this.client.player != null && this.state.target != null
				&& !this.state.target.isBlank()
				&& !this.state.target.equalsIgnoreCase(this.client.player.getGameProfile().getName());
	}

	private void drawTopBar(DrawContext context) {
		AcTheme.panel(context, 6, 8, this.width - 10, 30, 0);
		String title = npcTree() ? "Дерево навыков NPC «" + this.state.target + "»" : "Дерево навыков";
		context.drawText(this.textRenderer, title, 12, 12, AcTheme.TITLE, false);
		String line;
		int color;
		boolean fresh = !this.notice.isEmpty() && System.currentTimeMillis() - this.noticeAt < NOTICE_MS;
		if (fresh) {
			line = this.notice;
			color = this.noticeOk ? 0x55FF77 : AcTheme.RED;
		} else if (this.state.viewOnly) {
			line = "ПРОСМОТР: дерево игрока " + this.state.target + " — только чтение, покупки отключены";
			color = AcTheme.RED;
		} else if (npcTree()) {
			line = "Вы управляете NPC: покупки, эффекты и коины — только у этого NPC, не у вас.";
			color = 0x7FDBFF;
		} else {
			line = "Улучшения выбираются в ветках. Все задания вынесены в отдельный экран: кнопка «★ ЗАДАНИЯ» внизу.";
			color = AcTheme.MUTED;
		}
		int chipsW = 0;
		List<String> chips = new ArrayList<>();
		for (SkillTree.Currency c : SkillTree.CURRENCIES) {
			String text = c.title() + " " + this.state.coins(c.id());
			chips.add(text);
			chipsW += this.textRenderer.getWidth(text) + 16;
		}
		int titleW = this.textRenderer.getWidth(title) + 24;
		boolean twoRows = chipsW > this.width - 28 - titleW;
		int x = this.width - 10;
		int y = twoRows ? 10 : 15;
		for (int i = SkillTree.CURRENCIES.size() - 1; i >= 0; i--) {
			if (twoRows && i == 2) {
				x = this.width - 10;
				y = 24;
			}
			SkillTree.Currency c = SkillTree.CURRENCIES.get(i);
			String text = chips.get(i);
			int w = this.textRenderer.getWidth(text) + 14;
			x -= w;
			context.fill(x, y - 2, x + w - 2, y + 10, 0x70000000);
			context.fill(x + 3, y + 2, x + 8, y + 7, 0xFF000000 | c.color());
			context.drawText(this.textRenderer, text, x + 11, y, 0xFFFFFF, false);
			x -= 2;
		}
		int lineMax = (twoRows ? this.width - 24 - chipsW / 2 - 20 : this.width - 32 - chipsW) - 6;
		AcTheme.clipped(this.textRenderer, context, line, 12, 24, Math.max(60, lineMax), color);
	}

	private void drawViewportText(DrawContext context) {
		context.getMatrices().push();
		context.getMatrices().translate(0f, 0f, 320f);
		String crumb = open == null ? "Все ветки" : "Все ветки › " + open.title();
		int cw = Math.min(this.vw - 6, this.textRenderer.getWidth(crumb) + 8);
		context.fill(this.vx + 2, this.vy + 2, this.vx + 2 + cw, this.vy + 14, 0xA0000000);
		AcTheme.clipped(this.textRenderer, context, crumb, this.vx + 6, this.vy + 4, this.vw - 12,
				open == null ? 0xFFFFFF : AcTheme.TITLE);
		String hint = open == null
				? "Колесо, стрелки или наведение — выбрать ветку · Enter или клик — открыть"
				: "Колесо — масштаб · тянуть — сдвиг · клик — выбрать · Esc или ПКМ — все ветки";
		int hw = Math.min(this.vw - 6, this.textRenderer.getWidth(hint) + 6);
		context.fill(this.vx, this.vy + this.vh - 14, this.vx + hw, this.vy + this.vh - 2, 0xA0000000);
		AcTheme.clipped(this.textRenderer, context, hint, this.vx + 3, this.vy + this.vh - 12, this.vw - 10, 0x888888);
		context.getMatrices().pop();
	}

	/** Обзор: ствол, расходящийся на большие ветки; над каждой — схема её линий. Выбранная ветка подсвечена и приподнята. */
	private void drawOverview(DrawContext context, long now) {
		int cx = OVERVIEW_W / 2;
		int baseY = OVERVIEW_H - 8;
		int forkY = OVERVIEW_H - 70;
		int count = SkillTree.BRANCHES.size();
		long since = now - this.overviewStart;
		float grow = this.overviewFromBranch ? 1f : easeOut(since / 520.0);
		float[] pop = new float[count];
		float[] scale = new float[count];
		float[] lift = new float[count];
		for (int i = 0; i < count; i++) {
			pop[i] = this.overviewFromBranch ? 1f : easeBack((since - 160 - i * 70) / 320.0);
			// Ховер/колесо: иконка ветки ×2.
			scale[i] = pop[i] * (1 + 1.0f * this.focusA[i]);
			lift[i] = -14 * this.focusA[i];
		}
		double pulse = (Math.sin(now / 250.0) + 1) / 2;
		context.draw(() -> {
			for (int i = 0; i < count; i++) {
				float fy = branchY(i) + lift[i] + O_R * scale[i];
				branch(context, cx, forkY, branchX(i), fy, 4, WOOD, grow);
			}
			for (int i = 0; i < count; i++) {
				float a = this.focusA[i];
				if (a <= 0.01f) {
					continue;
				}
				float fy = branchY(i) + lift[i] + O_R * scale[i];
				int gold = ((int) (0xFF * a) << 24) | (GOLD_LINE & 0xFFFFFF);
				int bark = ((int) (0xFF * a) << 24) | (BARK & 0xFFFFFF);
				curve(context, cx, forkY, branchX(i), fy, 6, bark, grow);
				curve(context, cx, forkY, branchX(i), fy, 4, gold, grow);
				if (grow >= 1f) {
					for (int k = 0; k < 4; k++) {
						double t = ((now / 900.0) + k / 4.0) % 1.0;
						float[] p = bezier(cx, forkY, branchX(i), fy, (float) t);
						float pulseA = (float) Math.sin(t * Math.PI) * a;
						disc(context, Math.round(p[0]), Math.round(p[1]), 5, ((int) (0x80 * pulseA) << 24) | 0xFFE680);
						disc(context, Math.round(p[0]), Math.round(p[1]), 2, ((int) (0xFF * pulseA) << 24) | 0xFFFFF0);
					}
				}
			}
			context.fill(cx - 13, forkY - 4, cx + 14, baseY, BARK);
			context.fillGradient(cx - 11, forkY - 2, cx + 12, baseY, 0xFF6B5238, 0xFF3E2E1E);
			context.fill(cx - 40, baseY - 2, cx + 41, baseY + 4, BARK);
			for (int i = 0; i < count; i++) {
				if (pop[i] <= 0.01f) {
					continue;
				}
				Branch b = SkillTree.BRANCHES.get(i);
				int x = branchX(i);
				int y = branchY(i);
				int[] p = progress(b, -1);
				float a = this.focusA[i];
				int rgb = a > 0.5f ? GOLD_LINE : RING;
				context.getMatrices().push();
				context.getMatrices().translate(0f, lift[i] - O_R * (scale[i] - 1), 0f);
				miniMap(context, b, x, y - O_R - 10, a);
				context.getMatrices().pop();
				context.getMatrices().push();
				context.getMatrices().translate(0f, lift[i], 0f);
				around(context, x, y, scale[i]);
				if (a > 0.02f) {
					disc(context, x, y, O_R + 9, ((int) ((0x40 + 0x50 * pulse) * a) << 24) | 0xFFD34D);
				}
				if (a > 0.02f) {
					disc(context, x, y, O_R + 3, ((int) (0xFF * a) << 24) | 0xFFFFFF);
				}
				disc(context, x, y, O_R + 1, BARK);
				disc(context, x, y, O_R, rgb);
				disc(context, x, y, O_R - 3, a > 0.5f ? 0xFF2A2214 : 0xFF1C1610);
				if (a > 0.02f) {
					double ang = now / 380.0;
					for (int k = 0; k < 3; k++) {
						double q = ang + k * Math.PI * 2 / 3;
						int ox = x + (int) Math.round(Math.cos(q) * (O_R + 6));
						int oy = y + (int) Math.round(Math.sin(q) * (O_R + 6));
						context.fill(ox - 1, oy - 1, ox + 2, oy + 2, ((int) (0xFF * a) << 24) | 0xFFE680);
					}
				}
				restore(context);
				context.getMatrices().pop();
				context.getMatrices().push();
				context.getMatrices().translate(0f, lift[i] + O_R * (scale[i] - 1), 0f);
				int w = O_STEP - 14;
				context.fill(x - w / 2, y + O_R + 4, x + w / 2, y + O_R + 40, a > 0.5f ? 0xE0302412 : 0xD0101010);
				if (a > 0.02f) {
					context.fill(x - w / 2, y + O_R + 3, x + w / 2, y + O_R + 4, ((int) (0xFF * a) << 24) | 0xFFD34D);
				}
				int barW = w - 12;
				double ratio = p[1] <= 0 ? 0 : p[0] / (double) p[1];
				context.fill(x - barW / 2, y + O_R + 35, x + barW / 2, y + O_R + 37, 0xFF2A2A2A);
				context.fill(x - barW / 2, y + O_R + 35, x - barW / 2 + (int) (barW * ratio * grow), y + O_R + 37, RING);
				context.getMatrices().pop();
			}
		});
		for (int i = 0; i < count; i++) {
			if (pop[i] <= 0.01f) {
				continue;
			}
			Branch b = SkillTree.BRANCHES.get(i);
			int x = branchX(i);
			int y = branchY(i);
			float a = this.focusA[i];
			context.getMatrices().push();
			context.getMatrices().translate(x, y + lift[i], 0f);
			float s = 1.55f * scale[i];
			context.getMatrices().scale(s, s, 1f);
			context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) (Math.sin(now / 300.0) * 0.12 * a)));
			context.drawItem(icon(b, b.icon()), -8, -8);
			context.getMatrices().pop();
			int[] p = progress(b, -1);
			context.getMatrices().push();
			context.getMatrices().translate(0f, lift[i] + O_R * (scale[i] - 1), 0f);
			int w = O_STEP - 18;
			String title = b.title();
			if (this.textRenderer.getWidth(title) > w) {
				title = this.textRenderer.trimToWidth(title, w - 4) + "…";
			}
			int titleColor = a > 0.5f ? AcTheme.TITLE : 0xFFFFFF;
			context.drawText(this.textRenderer, title, x - this.textRenderer.getWidth(title) / 2, y + O_R + 7, titleColor, a > 0.5f);
			boolean soon = p[1] == 0;
			boolean off = this.state.offBranches.contains(b.id());
			String lv = soon ? "скоро" : off ? "отключена" : p[0] + "/" + p[1] + " ур." + (p[2] > 0 ? " · можно: " + p[2] : "");
			context.drawText(this.textRenderer, lv, x - this.textRenderer.getWidth(lv) / 2, y + O_R + 17,
					off && !soon ? 0xFF5A5A : 0xAAAAAA, false);
			String coins = soon ? "в разработке" : this.state.coins(b.currency().id()) + " " + b.currency().title();
			if (this.textRenderer.getWidth(coins) > w) {
				coins = this.textRenderer.trimToWidth(coins, w - 4) + "…";
			}
			context.drawText(this.textRenderer, coins, x - this.textRenderer.getWidth(coins) / 2, y + O_R + 26,
					0xFFBBBBBB, false);
			context.getMatrices().pop();
		}
		String root;
		if (focus < 0 || focus >= SkillTree.BRANCHES.size()) {
			root = "Наведите или колёсико — выбрать ветку · Enter — открыть";
		} else {
			Branch f = SkillTree.BRANCHES.get(focus);
			root = SkillTree.nodesOf(f).isEmpty() ? "«" + f.title() + "» — скоро"
					: this.state.offBranches.contains(f.id()) ? "«" + f.title() + "» отключена администрацией · Enter — посмотреть"
					: "Enter — открыть «" + f.title() + "»";
		}
		int rw = this.textRenderer.getWidth(root);
		context.fill(cx - rw / 2 - 4, OVERVIEW_H - 23, cx + rw / 2 + 4, OVERVIEW_H - 10, 0xC0000000);
		context.drawText(this.textRenderer, root, cx - rw / 2, OVERVIEW_H - 20,
				(int) (0xB0 + 0x4F * pulse) << 16 | 0xD34D | 0xFF000000, false);
	}

	/** Схема линий ветки точками (над её кругом на обзоре). */
	private void miniMap(DrawContext context, Branch b, int cx, int bottom, float focusA) {
		int sx = 10;
		int sy = 12;
		double mid = (lanes(b) - 1) / 2.0;
		for (Node n : SkillTree.nodesOf(b)) {
			for (SkillTree.Req r : n.requires) {
				Node p = SkillTree.node(r.node());
				if (p == null || p.branch != b) {
					continue;
				}
				segment(context, (float) (cx + (p.col - mid) * sx), bottom - p.row * sy,
						(float) (cx + (n.col - mid) * sx), bottom - n.row * sy, 0,
						focusA > 0.5f ? 0xC0FFE680 : 0x80FFFFFF);
			}
		}
		for (Node n : SkillTree.nodesOf(b)) {
			int x = (int) Math.round(cx + (n.col - mid) * sx);
			int y = bottom - n.row * sy;
			int level = this.state.node(n.id).level();
			int color = level > 0 ? RING : 0xFF555555;
			context.fill(x - 2, y - 2, x + 3, y + 3, BARK);
			context.fill(x - 1, y - 1, x + 2, y + 2, color);
		}
	}

	/** Одна ветка: ствол от таблички, развилка и ветви-линии, расходящиеся кроной в разные стороны. */
	private void drawBranch(DrawContext context, long now) {
		Branch b = open;
		List<Node> nodes = nodes();
		int cw = canvasW();
		int ch = canvasH();
		int cx = cw / 2;
		int rootY = rootY();
		int forkY = forkY();
		int laneCount = lanes(b);
		long since = now - this.branchStart;
		float trunk = easeOut((since - 20) / 280.0);
		int rgbBranch = WOOD & 0xFFFFFF;
		Map<String, Float> pop = new HashMap<>();
		Map<String, Float> grow = new HashMap<>();
		Map<String, float[]> paths = new HashMap<>();
		Set<String> hasChild = new HashSet<>();
		for (Node n : nodes) {
			long delay = n.row * 110L + Math.round(Math.abs(lean(n.col)) * 40);
			grow.put(n.id, easeOut((since - 200 - delay) / 300.0));
			pop.put(n.id, easeBack((since - 340 - delay) / 300.0));
			for (SkillTree.Req r : n.requires) {
				Node p = SkillTree.node(r.node());
				if (p != null && p.branch == b) {
					hasChild.add(p.id);
					paths.putIfAbsent(n.id, stemPath(p, n));
				}
			}
			paths.putIfAbsent(n.id, limbPath(n));
		}
		context.fillGradient(4, 0, cw - 4, ch, 0x03000000 | rgbBranch, 0x24000000 | rgbBranch);
		Set<String> chain = chain(this.selected);
		List<int[]> reqMarks = new ArrayList<>();
		double pulse = (Math.sin(now / 250.0) + 1) / 2;
		context.draw(() -> {
			motes(context, cw, ch, now);
			for (int s = -1; s <= 1; s += 2) {
				float y = rootY + PLATE_H - 2;
				limb(context, new float[] {cx + s * 30, y, cx + s * 55, y + 8, cx + s * 85, y + 12, cx + s * 118, y + 24},
						4, 1, WOOD, trunk);
				limb(context, new float[] {cx + s * 10, y, cx + s * 14, y + 12, cx + s * 26, y + 18, cx + s * 40, y + 30},
						3, 1, WOOD, trunk);
			}
			float[] stem = {cx, rootY + 4, cx - 5, rootY - TRUNK * 0.35f, cx + 4, forkY + TRUNK * 0.35f, cx, forkY - 6};
			tapered(context, stem, 13, 8, BARK, trunk);
			tapered(context, stem, 11, 6, WOOD, trunk);
			for (int k = -1; k <= 1; k += 2) {
				float[] line = stem.clone();
				for (int i = 0; i < line.length; i += 2) {
					line[i] += k * 4;
				}
				tapered(context, line, 0, 0, 0xFF46372A, trunk);
			}
			float[] shine = stem.clone();
			for (int i = 0; i < shine.length; i += 2) {
				shine[i] -= 1;
			}
			tapered(context, shine, 0, 0, 0xFF75614A, trunk * 0.9f);
			for (Node n : nodes) {
				float g = grow.get(n.id);
				float[] path = paths.get(n.id);
				boolean root = true;
				for (SkillTree.Req r : n.requires) {
					Node p = SkillTree.node(r.node());
					if (p == null || p.branch != b) {
						continue;
					}
					root = false;
					float[] q = stemPath(p, n);
					boolean met = this.state.node(p.id).level() >= r.level();
					int color = chain.contains(n.id) && chain.contains(p.id) ? GOLD_LINE : WOOD;
					limb(context, q, 3, 2, color, g);
					if (r.level() > 1 && g > 0.8f) {
						float[] m = cubic(q, 0.5f);
						int mx = Math.round(m[0]);
						int my = Math.round(m[1]);
						reqMarks.add(new int[] {mx, my, r.level(), met ? 1 : 0});
						context.fill(mx - 14, my - 5, mx + 14, my + 5, 0xE0101010);
					}
				}
				if (root) {
					limb(context, path, 6, 3, chain.contains(n.id) ? GOLD_LINE : WOOD, g);
				}
				if (g >= 1f) {
					float sway = (float) Math.sin(now / 900.0 + n.row * 1.3 + n.col) * 1.5f;
					float[] at = cubic(path, root ? 0.55f : 0.38f);
					int s = side(n);
					float ex = at[0] + s * (root ? 24 : 18);
					float ey = at[1] - (root ? 16 : 12) + sway;
					limb(context, new float[] {at[0], at[1], at[0] + s * 6, at[1] - 2, ex - s * 6, ey + 4, ex, ey}, 2, 1, WOOD, 1f);
					disc(context, Math.round(ex), Math.round(ey), 2, 0xFF7A6247);
				}
				if (!hasChild.contains(n.id) && pop.get(n.id) > 0.9f) {
					float sway = (float) Math.sin(now / 800.0 + n.col * 2.1) * 2f;
					int x = nodeX(n);
					int y = nodeY(n) - R;
					int s = side(n);
					limb(context, new float[] {x, y, x, y - 8, x + s * 4, y - 16, x + s * 8 + sway, y - 24}, 2, 1, WOOD, 1f);
					disc(context, Math.round(x + s * 8 + sway), y - 24, 2, 0xFF7A6247);
				}
			}
			plate(context, b, cx, rootY);
			for (Node n : nodes) {
				float s = pop.get(n.id);
				if (s <= 0.01f) {
					continue;
				}
				nodePush(context, n, s * (1 + 1.5f * this.hoverA.getOrDefault(n.id, 0f)), now);
				nodeShape(context, n, now, pulse);
				restore(context);
			}
		});
		for (Node n : nodes) {
			float s = pop.get(n.id);
			if (s <= 0.01f) {
				continue;
			}
			nodePush(context, n, s * (1 + 1.5f * this.hoverA.getOrDefault(n.id, 0f)), now);
			context.drawItem(icon(n), nodeX(n) - 8, nodeY(n) - 8);
			restore(context);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0f, 0f, 200f);
		context.draw(() -> {
			for (Node n : nodes) {
				float s = pop.get(n.id);
				if (s > 0.01f && status(n) == St.LOCKED) {
					nodePush(context, n, s * (1 + 1.5f * this.hoverA.getOrDefault(n.id, 0f)), now);
					disc(context, nodeX(n), nodeY(n), R - 2, 0xA0000000);
					restore(context);
				}
			}
			drawBursts(context, now);
		});
		context.getMatrices().pop();
		String head = b.title();
		context.drawText(this.textRenderer, head, cx - this.textRenderer.getWidth(head) / 2, rootY + 6, 0xFFFFFF, true);
		String coins = "«" + b.currency().title() + "»: " + this.state.coins(b.currency().id());
		context.drawText(this.textRenderer, coins, cx - this.textRenderer.getWidth(coins) / 2, rootY + 19,
				0xFFBBBBBB, false);
		for (int col = 0; col < laneCount; col++) {
			String name = b.lane(col);
			Node first = null;
			for (Node n : nodes) {
				if (n.col == col && (first == null || n.row < first.row)) {
					first = n;
				}
			}
			if (name.isEmpty() || first == null) {
				continue;
			}
			float a = Math.min(1f, pop.get(first.id));
			if (a < 0.05f) {
				continue;
			}
			int[] p = progress(b, col);
			String text = name + "  " + p[0] + "/" + p[1];
			if (this.textRenderer.getWidth(text) > LABEL_W) {
				text = this.textRenderer.trimToWidth(text, LABEL_W - 4) + "…";
			}
			int alpha = (int) (a * 255);
			int tw = this.textRenderer.getWidth(text);
			int x = nodeX(first);
			int y = nodeY(first) + R + 33;
			int rope = (alpha << 24) | 0x7A6247;
			context.fill(x - tw / 2, y - 4, x - tw / 2 + 1, y - 1, rope);
			context.fill(x + tw / 2 - 1, y - 4, x + tw / 2, y - 1, rope);
			context.fill(x - tw / 2 - 5, y - 1, x + tw / 2 + 5, y + 11, ((int) (a * 0xE0) << 24) | 0x2A1E12);
			context.fill(x - tw / 2 - 5, y + 10, x + tw / 2 + 5, y + 11, (alpha << 24) | (RING & 0xFFFFFF));
			int color = p[2] > 0 ? 0xFFD34D : 0xE8DCC4;
			context.drawText(this.textRenderer, text, x - tw / 2, y + 1, (alpha << 24) | color, false);
		}
		for (Node n : nodes) {
			float s = pop.get(n.id);
			if (s <= 0.01f) {
				continue;
			}
			nodePush(context, n, s * (1 + 0.06f * this.hoverA.getOrDefault(n.id, 0f)), now);
			nodeLabel(context, n);
			restore(context);
		}
		for (int[] m : reqMarks) {
			String req = "ур. " + m[2];
			context.drawText(this.textRenderer, req, m[0] - this.textRenderer.getWidth(req) / 2, m[1] - 4,
					m[3] == 1 ? 0x55FF77 : 0xAAAAAA, false);
		}
	}

	/** Ветвь от развилки ствола к первому улучшению линии: сначала вверх, потом вбок. */
	private static float[] limbPath(Node head) {
		float x0 = canvasW() / 2f + (float) lean(head.col) * 8f;
		float y0 = forkY();
		float x1 = nodeX(head);
		float y1 = nodeY(head) + R;
		float dx = x1 - x0;
		float dy = y0 - y1;
		return new float[] {x0, y0, x0 + dx * 0.15f, y0 - dy * 0.55f - 10, x0 + dx * 0.6f, y1 + dy * 0.15f, x1, y1};
	}

	/** Побег между соседними улучшениями линии с лёгким изгибом наружу. */
	private static float[] stemPath(Node p, Node n) {
		float x0 = nodeX(p);
		float y0 = nodeY(p) - R;
		float x1 = nodeX(n);
		float y1 = nodeY(n) + R;
		float dx = x1 - x0;
		float dy = y0 - y1;
		float bow = side(n) * 6f;
		return new float[] {x0, y0, x0 + dx * 0.1f + bow, y0 - dy * 0.5f, x1 - dx * 0.1f + bow, y1 + dy * 0.5f, x1, y1};
	}

	/** Пылинки, медленно всплывающие над деревом. */
	private static void motes(DrawContext context, int cw, int ch, long now) {
		for (int i = 0; i < 28; i++) {
			double f = Math.sin(i * 12.9898) * 43758.5453;
			f -= Math.floor(f);
			int x = (int) (8 + f * (cw - 16) + Math.sin(now / 1300.0 + i) * 6);
			double speed = 10 + (i % 5) * 5;
			int y = (int) (ch - ((now / 1000.0 * speed + i * 97) % (ch + 10)));
			int a = (int) (0x18 + 0x34 * (Math.sin(now / 500.0 + i * 2.1) + 1) / 2);
			int size = i % 3 == 0 ? 2 : 1;
			context.fill(x, y, x + size, y + size, (a << 24) | (RING & 0xFFFFFF));
		}
	}

	private float shake(Node n, long now) {
		for (Burst burst : this.bursts) {
			if (!burst.ok() && burst.node().equals(n.id)) {
				double t = (now - burst.start()) / 420.0;
				if (t < 1) {
					return (float) (Math.sin(t * Math.PI * 8) * 4 * (1 - t));
				}
			}
		}
		return 0f;
	}

	private void nodePush(DrawContext context, Node n, float scale, long now) {
		float x = nodeX(n);
		float y = nodeY(n);
		context.getMatrices().push();
		context.getMatrices().translate(x + shake(n, now), y, 0f);
		context.getMatrices().scale(scale, scale, 1f);
		context.getMatrices().translate(-x, -y, 0f);
	}

	/** Вспышка после клика по улучшению: зелёная с искрами — куплено, красная с дрожью — не вышло. */
	private void drawBursts(DrawContext context, long now) {
		Iterator<Burst> it = this.bursts.iterator();
		while (it.hasNext()) {
			Burst burst = it.next();
			double t = (now - burst.start()) / (double) BURST_MS;
			if (t >= 1) {
				it.remove();
				continue;
			}
			Node n = SkillTree.node(burst.node());
			if (n == null || n.branch != open) {
				continue;
			}
			int x = nodeX(n);
			int y = nodeY(n);
			float e = easeOut(t);
			int alpha = (int) (0xFF * (1 - t));
			if (!burst.ok()) {
				if (t < 0.6) {
					double k = 1 - t / 0.6;
					disc(context, x, y, R + 3, ((int) (0xC0 * k) << 24) | 0xFF3030);
					int ring = (int) (R + 4 + 10 * e);
					for (int q = 0; q < 24; q++) {
						double ang = q * Math.PI * 2 / 24;
						int ox = x + (int) Math.round(Math.cos(ang) * ring);
						int oy = y + (int) Math.round(Math.sin(ang) * ring);
						context.fill(ox - 1, oy - 1, ox + 1, oy + 1, ((int) (0xFF * k) << 24) | 0xFF5050);
					}
				}
				continue;
			}
			int ring = (int) (R + 26 * e);
			for (int k = 0; k < 32; k++) {
				double q = k * Math.PI * 2 / 32;
				int ox = x + (int) Math.round(Math.cos(q) * ring);
				int oy = y + (int) Math.round(Math.sin(q) * ring);
				context.fill(ox - 1, oy - 1, ox + 2, oy + 2, (alpha << 24) | 0x6CFF7A);
			}
			for (int k = 0; k < 12; k++) {
				double q = k * Math.PI * 2 / 12 + 0.3;
				double dist = R + 42 * e;
				int ox = x + (int) Math.round(Math.cos(q) * dist);
				int oy = y + (int) Math.round(Math.sin(q) * dist - 10 * t * t);
				context.fill(ox - 1, oy - 1, ox + 1, oy + 1, (alpha << 24) | 0xE8FFE8);
			}
			if (t < 0.4) {
				disc(context, x, y, R + 3, ((int) (0xB0 * (1 - t / 0.4)) << 24) | 0x5CFF70);
			}
		}
	}

	private static float[] cubic(float[] p, float t) {
		float u = 1f - t;
		float a = u * u * u;
		float b = 3 * u * u * t;
		float c = 3 * u * t * t;
		float d = t * t * t;
		return new float[] {a * p[0] + b * p[2] + c * p[4] + d * p[6], a * p[1] + b * p[3] + c * p[5] + d * p[7]};
	}

	/** Кривая Безье {x0,y0, c1x,c1y, c2x,c2y, x1,y1}, сужающаяся от {@code h0} к {@code h1}. */
	private static void tapered(DrawContext context, float[] p, float h0, float h1, int color, float grow) {
		if (grow <= 0.001f) {
			return;
		}
		int steps = 20;
		float px = p[0];
		float py = p[1];
		for (int i = 1; i <= steps; i++) {
			float t = Math.min(grow, i / (float) steps);
			float[] q = cubic(p, t);
			segment(context, px, py, q[0], q[1], Math.max(0, Math.round(h0 + (h1 - h0) * t)), color);
			px = q[0];
			py = q[1];
			if (t >= grow) {
				break;
			}
		}
	}

	private static void limb(DrawContext context, float[] p, int h0, int h1, int color, float grow) {
		tapered(context, p, h0 + 1, h1 + 1, BARK, grow);
		tapered(context, p, h0 - 1, h1 - 1, color, grow);
	}

	private static final int GOLD_LINE = 0xFFFFD34D;
	private static final int RING = 0xFFCFC2A8;
	private static final int WOOD = 0xFF5E4B36;
	private static final int BARK = 0xFF1E140A;

	/** Изогнутая ветвь снизу вверх: тёмная кора и цветная сердцевина; {@code grow} — какая часть уже «выросла». */
	private static void branch(DrawContext context, float x0, float y0, float x1, float y1, int half, int color, float grow) {
		curve(context, x0, y0, x1, y1, half + 1, BARK, grow);
		curve(context, x0, y0, x1, y1, half - 1, color, grow);
	}

	private static float[] bezier(float x0, float y0, float x1, float y1, float t) {
		float my = (y0 + y1) / 2f;
		float u = 1f - t;
		float x = u * u * u * x0 + 3 * u * u * t * x0 + 3 * u * t * t * x1 + t * t * t * x1;
		float y = u * u * u * y0 + 3 * u * u * t * my + 3 * u * t * t * my + t * t * t * y1;
		return new float[] {x, y};
	}

	private static void curve(DrawContext context, float x0, float y0, float x1, float y1, int half, int color, float grow) {
		if (grow <= 0.001f) {
			return;
		}
		int steps = 16;
		float px = x0;
		float py = y0;
		for (int i = 1; i <= steps; i++) {
			float t = Math.min(grow, i / (float) steps);
			float[] p = bezier(x0, y0, x1, y1, t);
			segment(context, px, py, p[0], p[1], half, color);
			px = p[0];
			py = p[1];
			if (t >= grow) {
				break;
			}
		}
	}

	private static void segment(DrawContext context, float x0, float y0, float x1, float y1, int half, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) {
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(x0, y0, 0f);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
		context.fill(-1, -half, (int) Math.ceil(len) + 1, half + 1, color);
		context.getMatrices().pop();
	}

	private static void disc(DrawContext context, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
			context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	/** Корень ветки: табличка с названием и валютой. */
	private static void plate(DrawContext context, Branch b, int cx, int y) {
		int x0 = cx - PLATE_W / 2;
		int rgb = RING;
		context.fill(x0 - 2, y - 2, x0 + PLATE_W + 2, y + PLATE_H + 2, BARK);
		context.fill(x0 - 1, y - 1, x0 + PLATE_W + 1, y + PLATE_H + 1, rgb);
		context.fillGradient(x0, y, x0 + PLATE_W, y + PLATE_H, 0xFF3A2C1C, 0xFF1C140C);
	}

	private void nodeShape(DrawContext context, Node n, long now, double pulse) {
		int x = nodeX(n);
		int y = nodeY(n);
		SkillClient.NodeState ns = this.state.node(n.id);
		St st = status(n);
		int bg;
		int border;
		switch (st) {
			case MAX -> {
				bg = 0xFF173A22;
				border = 0xFF46C46A;
				disc(context, x, y, R + 4, 0x3046C46A);
			}
			case BUY -> {
				bg = 0xFF2E2A10;
				int g = 0xB0 + (int) (0x4F * pulse);
				border = 0xFF000000 | (0xFF << 16) | (g << 8) | 0x40;
				disc(context, x, y, R + 5, ((int) (0x30 + 0x60 * pulse) << 24) | 0xFFC21A);
			}
			case POOR -> {
				bg = 0xFF301C10;
				border = 0xFFE0823A;
			}
			default -> {
				bg = 0xFF181818;
				border = 0xFF444444;
			}
		}
		if (n == this.selected) {
			disc(context, x, y, R + 3, 0xFFFFFFFF);
		} else if (n == this.hovered) {
			disc(context, x, y, R + 2, 0xFFBBBBBB);
		}
		disc(context, x, y, R + 1, BARK);
		disc(context, x, y, R, border);
		disc(context, x, y, R - 2, bg);
		if (n == this.selected) {
			double ang = now / 420.0;
			for (int k = 0; k < 2; k++) {
				double q = ang + k * Math.PI;
				int ox = x + (int) Math.round(Math.cos(q) * (R + 7));
				int oy = y + (int) Math.round(Math.sin(q) * (R + 7));
				context.fill(ox - 1, oy - 1, ox + 2, oy + 2, 0xFFFFE680);
			}
		}
		int max = Math.max(1, ns.max());
		int rgb = RING;
		int pip = max <= 8 ? 4 : max <= 12 ? 3 : 2;
		int gap = max <= 12 ? 2 : 1;
		int total = max * pip + (max - 1) * gap;
		int px0 = x - total / 2;
		context.fill(px0 - 1, y + R + 2, px0 + total + 1, y + R + 7, BARK);
		for (int i = 0; i < max; i++) {
			int p = px0 + i * (pip + gap);
			context.fill(p, y + R + 3, p + pip, y + R + 6, i < ns.level() ? rgb : 0xFF3A3A3A);
		}
		int w = labelWidth(n);
		context.fill(x - w / 2, y + R + 8, x + w / 2, y + R + 29, 0xD0101010);
	}

	private int labelWidth(Node n) {
		int text = Math.max(this.textRenderer.getWidth(n.title), this.textRenderer.getWidth(subLabel(n)));
		return Math.min(LABEL_W, text + 8);
	}

	private String subLabel(Node n) {
		SkillClient.NodeState ns = this.state.node(n.id);
		int cost = n.cost(ns.level() + 1);
		return switch (status(n)) {
			case MAX -> "МАКС " + ns.level() + "/" + ns.max();
			case BUY -> (ns.level() > 0 ? "Улучшить: " : "Купить: ") + cost;
			case POOR -> "ур. " + ns.level() + "/" + ns.max() + " · нужно " + cost;
			case LOCKED -> "закрыто";
		};
	}

	private void nodeLabel(DrawContext context, Node n) {
		int x = nodeX(n);
		int y = nodeY(n);
		St st = status(n);
		int w = labelWidth(n);
		int textColor = st == St.LOCKED ? AcTheme.MUTED : n == this.selected ? AcTheme.TITLE : 0xFFFFFF;
		String title = n.title;
		if (this.textRenderer.getWidth(title) > w - 6) {
			title = this.textRenderer.trimToWidth(title, w - 10) + "…";
		}
		context.drawText(this.textRenderer, title, x - this.textRenderer.getWidth(title) / 2, y + R + 10, textColor, false);
		String sub = subLabel(n);
		if (this.textRenderer.getWidth(sub) > w - 4) {
			sub = this.textRenderer.trimToWidth(sub, w - 8) + "…";
		}
		int subColor = switch (st) {
			case MAX -> 0x55FF77;
			case BUY -> 0xFFD34D;
			case POOR -> 0xFF9944;
			case LOCKED -> 0x888888;
		};
		context.drawText(this.textRenderer, sub, x - this.textRenderer.getWidth(sub) / 2, y + R + 20, subColor, false);
	}

	private List<Text> tooltip(Node n) {
		List<Text> out = new ArrayList<>();
		SkillClient.NodeState ns = this.state.node(n.id);
		SkillTree.Currency c = n.branch.currency();
		St st = status(n);
		out.add(Text.literal(n.title).formatted(Formatting.GOLD));
		String lane = n.branch.lane(n.col);
		out.add(Text.literal(n.branch.title() + (lane.isEmpty() ? "" : " › " + lane) + " • валюта «" + c.title() + "»")
				.formatted(Formatting.GRAY));
		out.add(Text.literal("Уровень: " + ns.level() + "/" + ns.max()));
		out.add(Text.literal("Сейчас: " + effectText(n, ns.level())));
		if (ns.level() < ns.max()) {
			out.add(Text.literal("Следующий: " + effectText(n, ns.level() + 1)).formatted(Formatting.AQUA));
			out.add(Text.literal("Цена: " + n.cost(ns.level() + 1) + " " + c.coin() + " (у вас " + this.state.coins(c.id()) + ")"));
		}
		out.add(Text.literal(statusText(n)).styled(s -> s.withColor(statusColor(st))));
		if (this.state.offBranches.contains(n.branch.id())) {
			out.add(Text.literal("Ветка отключена администрацией: навык не действует").formatted(Formatting.RED));
		}
		if (!this.state.viewOnly && (st == St.BUY || st == St.POOR)) {
			String act = st == St.POOR ? "попробовать купить" : ns.level() > 0 ? "улучшить" : "купить";
			out.add(Text.literal("Клик — " + act).formatted(Formatting.DARK_GRAY));
		}
		return out;
	}

	/** Узкая подсказка ветки: название и короткое описание с переносом, чтобы не закрывать обзор. */
	private List<OrderedText> branchTooltip(Branch b) {
		List<OrderedText> out = new ArrayList<>();
		out.add(Text.literal(b.title()).formatted(Formatting.GOLD).asOrderedText());
		out.addAll(this.textRenderer.wrapLines(Text.literal(b.description()).formatted(Formatting.GRAY), TOOLTIP_W));
		return out;
	}

	private List<OrderedText> wrapTooltip(List<Text> lines) {
		List<OrderedText> out = new ArrayList<>();
		for (Text line : lines) {
			out.addAll(this.textRenderer.wrapLines(line, NODE_TOOLTIP_W));
		}
		return out;
	}

	private void drawDetail(DrawContext context) {
		AcTheme.panel(context, this.px, this.py, this.pw, this.ph, 0);
		this.buyShown = false;
		this.tabShown = false;
		Node n = this.selected;
		if (n == null) {
			return;
		}
		context.enableScissor(this.px, this.py, this.px + this.pw - 2, this.py + this.ph - 2);
		int x = this.px + 8;
		int w = this.pw - 20;
		int y = this.py + 6 - this.detailScroll;
		SkillClient.NodeState ns = this.state.node(n.id);
		SkillTree.Currency c = n.branch.currency();
		St st = status(n);
		context.drawItem(icon(n), x, y);
		AcTheme.clipped(this.textRenderer, context, n.title, x + 20, y, w - 20, AcTheme.TITLE);
		String lane = n.branch.lane(n.col);
		AcTheme.clipped(this.textRenderer, context, n.branch.title() + (lane.isEmpty() ? "" : " › " + lane) + " • «" + c.title() + "»",
				x + 20, y + 10, w - 20, c.color());
		y += 24;
		if (this.state.offBranches.contains(n.branch.id())) {
			y = AcTheme.wrapped(this.textRenderer, context, "Ветка отключена администрацией: навыки не действуют, покупки закрыты.",
					x, y, w, 0xFF5A5A) + 3;
		}
		y = AcTheme.wrapped(this.textRenderer, context, n.description, x, y, w, 0xDDDDDD) + 3;
		context.drawText(this.textRenderer, "Уровень: " + ns.level() + "/" + ns.max(), x, y, 0xFFFFFF, false);
		y += 11;
		y = AcTheme.wrapped(this.textRenderer, context, "Сейчас: " + effectText(n, ns.level()), x, y, w, 0xBBBBBB);
		if (ns.level() < ns.max()) {
			y = AcTheme.wrapped(this.textRenderer, context, "Следующий: " + effectText(n, ns.level() + 1), x, y, w, 0x7FDBFF);
		}
		if (!ns.note().isEmpty()) {
			y = AcTheme.wrapped(this.textRenderer, context, ns.note(), x, y, w, 0x7FDBFF);
		}
		if (ns.level() < ns.max()) {
			int cost = n.cost(ns.level() + 1);
			int have = this.state.coins(c.id());
			y = AcTheme.wrapped(this.textRenderer, context, "Цена: " + cost + " " + c.coin() + " (у вас " + have + ")",
					x, y, w, have >= cost ? 0xFFD34D : 0xFF9944);
		}
		y += 3;
		if (n.requires.isEmpty()) {
			context.drawText(this.textRenderer, "Требований нет", x, y, AcTheme.MUTED, false);
			y += 10;
		} else {
			context.drawText(this.textRenderer, "Требуется:", x, y, 0xFFFFFF, false);
			y += 10;
			for (SkillTree.Req r : n.requires) {
				Node need = SkillTree.node(r.node());
				if (need == null) {
					continue;
				}
				int have = this.state.node(need.id).level();
				boolean ok = have >= r.level();
				String text = (ok ? "✔ " : "✘ ") + "«" + need.title + "» ур. " + r.level() + (ok ? "" : " (сейчас " + have + ")");
				y = AcTheme.wrapped(this.textRenderer, context, text, x + 4, y, w - 4, ok ? 0x55FF77 : AcTheme.RED);
			}
		}
		y += 2;
		y = AcTheme.wrapped(this.textRenderer, context, statusText(n), x, y, w, statusColor(st)) + 3;
		if (this.state.viewOnly) {
			y = AcTheme.wrapped(this.textRenderer, context, "Только просмотр — покупки отключены.", x, y, w, AcTheme.RED) + 4;
		} else if (st != St.MAX) {
			boolean can = st == St.BUY && this.pending == null;
			String label;
			if (this.pending != null) {
				label = "Покупка...";
			} else if (st == St.BUY) {
				label = (ns.level() > 0 ? "Улучшить до ур. " : "Купить ур. ") + (ns.level() + 1) + " за " + n.cost(ns.level() + 1);
			} else if (st == St.POOR) {
				label = "Не хватает " + (n.cost(ns.level() + 1) - this.state.coins(c.id())) + " коинов";
			} else {
				label = "Сначала откройте требования";
			}
			context.fill(x, y, x + w, y + 18, can ? 0xFF8A6D12 : 0xFF2A2A2A);
			context.fill(x + 1, y + 1, x + w - 1, y + 17, can ? 0xFFB8921C : 0xFF3A3A3A);
			AcTheme.clippedCentered(this.textRenderer, context, label, x + 2, y + 5, w - 4, can ? 0xFFFFFF : 0x999999);
			this.buyX = x;
			this.buyY = y;
			this.buyW = w;
			this.buyShown = true;
			y += 24;
		} else {
			y += 2;
		}
		this.tabY = y;
		this.tabShown = true;
		int half = (w - 4) / 2;
		tab(context, x, y, half, "Задания «" + c.title() + "»", !this.showHistory);
		tab(context, x + half + 4, y, half, "История", this.showHistory);
		y += 22;
		if (this.showHistory) {
			if (this.state.history.isEmpty()) {
				y = AcTheme.wrapped(this.textRenderer, context, "Пока пусто.", x, y, w, AcTheme.MUTED);
			}
			for (String h : this.state.history) {
				y = AcTheme.wrapped(this.textRenderer, context, h, x, y, w, 0xCCCCCC) + 2;
			}
		} else {
			y = drawQuests(context, c, x, y, w);
		}
		context.disableScissor();
		int content = y + this.detailScroll - this.py;
		this.detailMax = Math.max(0, content - this.ph + 8);
		this.detailScroll = Math.min(this.detailScroll, this.detailMax);
		if (this.detailMax > 0) {
			int trackH = this.ph - 6;
			int barH = Math.max(16, trackH * (this.ph) / Math.max(1, content));
			int barY = this.py + 2 + (int) ((trackH - barH) * (this.detailScroll / (double) this.detailMax));
			context.fill(this.px + this.pw - 7, this.py + 2, this.px + this.pw - 4, this.py + 2 + trackH, 0x40FFFFFF);
			context.fill(this.px + this.pw - 7, barY, this.px + this.pw - 4, barY + barH, 0xC0FFFFFF);
		}
	}

	private void tab(DrawContext context, int x, int y, int w, String label, boolean active) {
		context.fill(x, y, x + w, y + 16, active ? 0xFF4A4A4A : 0xFF262626);
		if (active) {
			context.fill(x, y + 15, x + w, y + 16, 0xFFFFD34D);
		}
		AcTheme.clippedCentered(this.textRenderer, context, label, x + 2, y + 4, w - 4, active ? 0xFFFFFF : 0xAAAAAA);
	}

	private int drawQuests(DrawContext context, SkillTree.Currency c, int x, int y, int w) {
		y = AcTheme.wrapped(this.textRenderer, context,
				"КАК ЭТО РАБОТАЕТ: ничего брать у NPC не нужно. Просто выполняйте указанную цель — сервер сам считает прогресс и сразу выдаёт коины.",
				x, y, w, 0x7FDBFF) + 5;
		y = AcTheme.wrapped(this.textRenderer, context, "ЕЖЕДНЕВНЫЕ ЗАДАНИЯ • обновляются каждый день", x, y, w, AcTheme.TITLE) + 3;
		for (SkillTree.Daily d : SkillTree.DAILIES) {
			if (d.currency() != c) {
				continue;
			}
			SkillClient.DailyState ds = this.state.dailies.get(d.id());
			long value = ds == null ? 0 : ds.progress();
			boolean done = ds != null && ds.done();
			y = AcTheme.wrapped(this.textRenderer, context, d.title() + "  (+" + d.reward() + ")", x, y, w, done ? 0x55FF77 : 0xFFFFFF);
			if (done) {
				y = AcTheme.wrapped(this.textRenderer, context, "✔ выполнено сегодня, новое — завтра", x, y, w, 0x55FF77);
			} else {
				y = bar(context, x, y, w, value, d.goal(), d.divisor(), d.unit(), c.color());
			}
			y += 5;
		}
		y = AcTheme.wrapped(this.textRenderer, context, "ДОЛГОСРОЧНЫЕ ЗАДАНИЯ • несколько этапов, выполняются один раз", x, y, w, AcTheme.TITLE) + 3;
		for (SkillTree.Quest q : SkillTree.QUESTS) {
			if (q.currency() != c) {
				continue;
			}
			SkillClient.QuestState qs = this.state.quests.get(q.id());
			long value = qs == null ? 0 : qs.progress();
			int stage = qs == null ? 0 : qs.stage();
			boolean done = stage >= q.stages();
			String head = q.title() + "  этап " + Math.min(stage + 1, q.stages()) + "/" + q.stages();
			y = AcTheme.wrapped(this.textRenderer, context, head, x, y, w, done ? 0x55FF77 : 0xFFFFFF);
			y = AcTheme.wrapped(this.textRenderer, context, q.description(), x, y, w, 0x999999);
			if (done) {
				y = AcTheme.wrapped(this.textRenderer, context, "✔ все этапы выполнены", x, y, w, 0x55FF77);
			} else {
				y = bar(context, x, y, w, value, q.goals()[stage], q.divisor(), q.unit(), c.color());
				context.drawText(this.textRenderer, "награда +" + q.rewards()[stage], x, y, 0xFFD34D, false);
				y += 10;
			}
			if (q.id().equals("life_alive")) {
				y = AcTheme.wrapped(this.textRenderer, context, "Текущая серия: " + (this.state.lifeSeconds / 60) + " мин",
						x, y, w, 0xBBBBBB);
			}
			y += 5;
		}
		y = AcTheme.wrapped(this.textRenderer, context,
				"Этапы выдаются один раз и не повторяются. Прогресс считает сервер; дневные лимиты защищают от фарма.",
				x, y, w, 0x777777);
		return y;
	}

	private int bar(DrawContext context, int x, int y, int w, long value, long goal, int divisor, String unit, int rgb) {
		double ratio = goal <= 0 ? 1 : Math.min(1.0, value / (double) goal);
		context.fill(x, y + 1, x + w, y + 6, 0xFF2A2A2A);
		context.fill(x, y + 1, x + (int) (w * ratio), y + 6, 0xFF000000 | rgb);
		y += 8;
		String text = SkillTree.format(Math.min(value, goal), divisor, unit) + " / " + SkillTree.format(goal, divisor, unit);
		context.drawText(this.textRenderer, text, x, y, 0xDDDDDD, false);
		return y + 10;
	}

	/** Клик по улучшению: покупает сразу, если хватает монет; иначе — красная вспышка и причина. */
	private void clickBuy(Node n) {
		if (n == null || this.state.viewOnly || this.pending != null) {
			return;
		}
		St st = status(n);
		if (st == St.MAX) {
			return;
		}
		if (st == St.BUY) {
			this.pending = n.id;
			this.pendingAt = System.currentTimeMillis();
			SkillClient.buy(n.id, this.state.node(n.id).level());
			return;
		}
		this.bursts.add(new Burst(n.id, Util.getMeasuringTimeMs(), false));
		this.notice = statusText(n);
		this.noticeOk = false;
		this.noticeAt = System.currentTimeMillis();
		uiSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f);
	}

	private void uiSound(net.minecraft.sound.SoundEvent sound, float pitch) {
		uiSound(sound, pitch, 1.0f);
	}

	private void uiSound(net.minecraft.sound.SoundEvent sound, float pitch, float volume) {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
		}
	}

	private void buy() {
		Node n = this.selected;
		if (n == null || this.state.viewOnly || this.pending != null || status(n) != St.BUY) {
			return;
		}
		this.pending = n.id;
		this.pendingAt = System.currentTimeMillis();
		SkillClient.buy(n.id, this.state.node(n.id).level());
	}

	// ---------- ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (inPanel(mouseX, mouseY)) {
			if (button == 0 && this.buyShown && mouseX >= this.buyX && mouseX < this.buyX + this.buyW
					&& mouseY >= this.buyY && mouseY < this.buyY + 18) {
				buy();
				return true;
			}
			if (button == 0 && this.tabShown && mouseY >= this.tabY && mouseY < this.tabY + 16) {
				int half = (this.pw - 24) / 2;
				this.showHistory = mouseX >= this.px + 8 + half + 4;
				this.detailScroll = Math.min(this.detailScroll, Math.max(0, this.tabY + this.detailScroll - this.py - 10));
				return true;
			}
			return true;
		}
		if (this.opening != null) {
			return true;
		}
		if (open == null) {
			if (button == 0) {
				Branch b = branchAt(mouseX, mouseY);
				if (b != null) {
					startOpen(b);
					return true;
				}
			}
			return inViewport(mouseX, mouseY);
		}
		if (button == 1 && inViewport(mouseX, mouseY)) {
			showOverview();
			return true;
		}
		if (button == 0 && inViewport(mouseX, mouseY)) {
			this.dragging = true;
			this.dragDist = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.dragging) {
			tPanX += deltaX;
			tPanY += deltaY;
			clampTarget();
			panX = tPanX;
			panY = tPanY;
			this.dragDist += Math.abs(deltaX) + Math.abs(deltaY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.dragging && button == 0) {
			this.dragging = false;
			if (this.dragDist < 4) {
				Node n = nodeAt(mouseX, mouseY);
				if (n != null) {
					select(n);
					clickBuy(n);
				}
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inViewport(mouseX, mouseY)) {
			if (this.opening != null) {
				return true;
			}
			if (open == null) {
				moveFocus(amount > 0 ? -1 : 1);
			} else {
				zoomAt(mouseX, mouseY, amount > 0 ? 1.15f : 1 / 1.15f);
			}
			return true;
		}
		if (inPanel(mouseX, mouseY)) {
			this.detailScroll = (int) Math.max(0, Math.min(this.detailMax, this.detailScroll - amount * 14));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void removed() {
		SkillClient.closed();
		super.removed();
	}
}
