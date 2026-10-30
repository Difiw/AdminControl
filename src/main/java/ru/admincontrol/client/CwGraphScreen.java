package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.cw.CwGraphNetwork;
import ru.admincontrol.cw.CwStoryGraph;
import ru.admincontrol.net.ModPackets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Story tablet: tabs, module list, node canvas, element inspector, element library and live preview.
 * The layout adapts to the window: a compact arrangement for small GUI sizes (large GUI scale) and the full
 * arrangement from the design reference for big windows. The server stays authoritative for validation.
 */
public final class CwGraphScreen extends Screen {
	private static final int NODE_W = 124, NODE_H = 42;
	private static final String[] TABS = {"Сцены", "NPC", "Диалоги", "Задания", "Условия", "Действия", "Предметы", "Локации", "Настройки"};
	private static final String[] TABS_SHORT = {"Сцены", "NPC", "Диал.", "Зад.", "Усл.", "Дейст.", "Предм.", "Лок.", "Настр."};
	private static final String[] CATEGORIES = {"Диалоги", "Задания", "Условия", "Действия", "Игрок", "Мир", "Предметы", "Система"};

	private record Tile(String title, String sub, CwStoryGraph.Kind kind, Map<String, String> preset) {}

	private static Map<String, String> p(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
		return m;
	}

	private static final List<List<Tile>> LIBRARY = List.of(
			List.of(
					new Tile("Диалог с NPC", "Обычный диалог", CwStoryGraph.Kind.DIALOGUE, p("text", "Новая реплика")),
					new Tile("Выбор ответа", "Вариант для игрока", CwStoryGraph.Kind.CHOICE, p("button", "Да", "player_text", "Да.")),
					new Tile("Фраза NPC", "Автоматическая реплика", CwStoryGraph.Kind.ACTION, p("action", "npc_say")),
					new Tile("Условный диалог", "По условию", CwStoryGraph.Kind.CONDITION, p("condition", "flag:name=true")),
					new Tile("Закрыть диалог", "Завершить общение", CwStoryGraph.Kind.ACTION, p("action", "close")),
					new Tile("Пауза", "Ожидание", CwStoryGraph.Kind.WAIT, p("ticks", "40"))),
			List.of(
					new Tile("Начало сцены", "При входе в зону", CwStoryGraph.Kind.START, p("trigger", "first_enter")),
					new Tile("Триггер", "Событие игрока", CwStoryGraph.Kind.TRIGGER, p("action", "trigger")),
					new Tile("Сохранение", "Контрольная точка", CwStoryGraph.Kind.CHECKPOINT, p("action", "checkpoint")),
					new Tile("Завершение", "Закончить сцену", CwStoryGraph.Kind.END, p("result", "complete"))),
			List.of(
					new Tile("Флаг сюжета", "Проверить флаг", CwStoryGraph.Kind.CONDITION, p("condition", "flag:name=true")),
					new Tile("Предмет у игрока", "Проверить инвентарь", CwStoryGraph.Kind.CONDITION, p("condition", "item:minecraft:stone")),
					new Tile("Уровень", "Опыт игрока", CwStoryGraph.Kind.CONDITION, p("condition", "level>=1")),
					new Tile("Здоровье", "Достаточно здоровья", CwStoryGraph.Kind.CONDITION, p("condition", "health>=10"))),
			List.of(
					new Tile("Выдать предмет", "Добавить в инвентарь", CwStoryGraph.Kind.ACTION, p("action", "give")),
					new Tile("Забрать предмет", "Убрать из инвентаря", CwStoryGraph.Kind.ACTION, p("action", "take")),
					new Tile("Установить флаг", "Запомнить событие", CwStoryGraph.Kind.ACTION, p("action", "flag")),
					new Tile("Выдать опыт", "Награда", CwStoryGraph.Kind.ACTION, p("action", "xp")),
					new Tile("Переместить NPC", "Идти к точке", CwStoryGraph.Kind.ACTION, p("action", "npc_move")),
					new Tile("Другая схема", "Перейти к графу", CwStoryGraph.Kind.ACTION, p("action", "graph"))),
			List.of(
					new Tile("Телепортация", "Переместить игрока", CwStoryGraph.Kind.TELEPORT, p("marker", "start")),
					new Tile("Здоровье", "Изменить здоровье", CwStoryGraph.Kind.ACTION, p("action", "health")),
					new Tile("Сытость", "Изменить голод", CwStoryGraph.Kind.ACTION, p("action", "food")),
					new Tile("Эффект", "Наложить эффект", CwStoryGraph.Kind.EFFECT, p("action", "effect"))),
			List.of(
					new Tile("Время суток", "Сюжетное время", CwStoryGraph.Kind.TIME, p("action", "time")),
					new Tile("Звук", "Эффект или музыка", CwStoryGraph.Kind.AUDIO, p("action", "sound")),
					new Tile("Блок", "Поставить/сломать", CwStoryGraph.Kind.BLOCK, p("action", "block")),
					new Tile("NPC", "Управление NPC", CwStoryGraph.Kind.NPC, p("action", "npc"))),
			List.of(
					new Tile("Предмет", "Выдача и проверка", CwStoryGraph.Kind.ITEM, p("action", "give")),
					new Tile("Награда", "Предмет за задание", CwStoryGraph.Kind.ACTION, p("action", "give", "note", "награда"))),
			List.of(
					new Tile("Группа", "Объединить модули", CwStoryGraph.Kind.GROUP, p("note", "группа")),
					new Tile("Сохранение", "Контрольная точка", CwStoryGraph.Kind.CHECKPOINT, p("action", "checkpoint")),
					new Tile("Конец", "Завершить ветку", CwStoryGraph.Kind.END, p("result", "complete")),
					new Tile("Начало", "Стартовая точка", CwStoryGraph.Kind.START, p("trigger", "manual"))));

	private CwStoryGraph.Graph graph;
	private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
	private final Set<String> selected = new LinkedHashSet<>();
	private double panX, panY, zoom = 1.0;
	private boolean panning, dragging, dirty, discardArmed, editingFields, dragSnapshot, compact;
	private String connecting, status = "";
	private int tab, category, listScroll, tileScroll;
	private TextFieldWidget titleField;
	private final TextFieldWidget[] dataFields = new TextFieldWidget[5];
	private final ButtonWidget[] pickers = new ButtonWidget[6];
	private final CwScrollColumn inspector = new CwScrollColumn();
	private static java.util.List<CwSkinPickerScreen.Skin> availableSkins = java.util.List.of();

	// Layout, recomputed in init().
	private int M, topH, leftW, leftY, leftH, canvasX, canvasY, canvasW, canvasH, inspX, inspW, libY, libH, previewX, previewW;
	private int tileX, tileY, tileW, tileH, tileCols, tileRows, toolbarEnd;

	private CwGraphScreen(CwStoryGraph.Graph graph) {
		super(Text.literal("Шёпот Люмиры — редактор сюжетной ветки"));
		this.graph = graph;
	}

	public static void receive(MinecraftClient client, String status, String json, java.util.List<CwSkinPickerScreen.Skin> skins) {
		availableSkins = skins == null ? java.util.List.of() : skins;
		try {
			CwStoryGraph.Graph incoming = CwStoryGraph.fromJson(json);
			client.execute(() -> {
				if (client.currentScreen instanceof CwGraphScreen screen) {
					screen.status = status;
					if (status.startsWith("SAVED")) {
						screen.graph = incoming;
						screen.dirty = false;
						screen.undo.clear();
						screen.redo.clear();
						screen.refreshFields();
					}
				} else client.setScreen(new CwGraphScreen(incoming));
			});
		} catch (Exception ignored) {
		}
	}

	// ------------------------------------------------------------------ layout & widgets

	@Override
	protected void init() {
		clearChildren();
		compact = width < 640 || height < 360;
		M = compact ? 4 : 6;
		topH = compact ? 26 : 48;
		leftW = compact ? Math.min(96, width / 5) : 150;
		inspW = compact ? Math.min(140, width * 29 / 100) : Math.max(150, Math.min(250, width / 4));
		libH = compact ? 76 : Math.max(92, Math.min(150, height * 30 / 100));
		previewW = compact ? 0 : Math.max(150, Math.min(260, width / 4));
		libY = height - libH - M;
		leftY = topH + 6;
		leftH = libY - 5 - leftY;
		inspX = width - inspW - M;
		canvasX = M + leftW + 4;
		canvasY = leftY;
		canvasW = Math.max(100, inspX - 4 - canvasX);
		canvasH = leftH;
		previewX = width - previewW - M;
		if (zoom == 1.0 && compact) zoom = 0.8;

		// Tabs.
		String[] labels = compact ? TABS_SHORT : TABS;
		int tabsX = compact ? M + 82 : Math.min(width / 3 + 40, 330);
		int each = Math.max(30, Math.min(96, (width - tabsX - M - 4) / TABS.length));
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			addDrawableChild(CwUi.Button.blue(tabsX + i * (each + 1), compact ? 3 : 8, each, compact ? 20 : 32, labels[i], CwUi.Icon.NONE, i == tab, b -> { tab = index; listScroll = 0; init(); }));
		}

		// Canvas toolbar: compact is packed from the left so it can never spill over its neighbours.
		String[] names = compact ? new String[]{"←", "→", "Коп.", "×", "Связь", "Пров.", "Сохр.", "Тест"} : new String[]{"←", "→", "Копия", "×", "Связать", "Проверить", "Сохранить", "Тест"};
		Runnable[] actions = {this::undo, this::redo, this::copySelected, this::deleteSelected, this::connectSelected, this::validateGraph, this::saveGraph, this::testSelected};
		int total = -2;
		int[] widths = new int[names.length];
		for (int i = 0; i < names.length; i++) { widths[i] = Math.max(16, textRenderer.getWidth(names[i]) + (compact ? 8 : 12)); total += widths[i] + 2; }
		int tx = compact ? canvasX + 3 : canvasX + canvasW - 6 - total;
		toolbarEnd = tx;
		for (int i = 0; i < names.length; i++) {
			final Runnable action = actions[i];
			addDrawableChild(CwUi.Button.blue(tx, canvasY + 3, widths[i], 16, names[i], i == names.length - 1 && !compact ? CwUi.Icon.PLAY : CwUi.Icon.NONE, i == names.length - 1, b -> action.run()));
			tx += widths[i] + 2;
		}

		// Inspector widgets (positions and visibility are driven by the scroll column).
		int cw = inspW - 14;
		titleField = new TextFieldWidget(textRenderer, inspX + 6, 0, cw, 16, Text.literal("Название"));
		titleField.setMaxLength(128);
		addDrawableChild(titleField);
		for (int i = 0; i < dataFields.length; i++) {
			final int index = i;
			dataFields[i] = new TextFieldWidget(textRenderer, inspX + 6, 0, cw, 16, Text.literal("Параметр"));
			dataFields[i].setMaxLength(4096);
			addDrawableChild(dataFields[i]);
			dataFields[i].setChangedListener(v -> changeData(index, v));
		}
		String[] pickerNames = {"Вариант", "Моя точка", "Предмет", "Скин NPC", "Действие", "Анимации"};
		Runnable[] pickerActions = {this::applyPreset, this::usePlayerPosition, this::openItemPicker, this::openSkinPicker, this::openActionPicker, this::openAnimationEditor};
		int half = (cw - 2) / 2;
		for (int i = 0; i < 6; i++) {
			final Runnable action = pickerActions[i];
			pickers[i] = CwUi.Button.blue(inspX + 6 + (i % 2) * (half + 2), 0, half, 16, pickerNames[i], i == 5 ? CwUi.Icon.GEAR : CwUi.Icon.NONE, false, b -> action.run());
			addDrawableChild(pickers[i]);
		}
		titleField.setChangedListener(v -> {
			if (!editingFields) {
				CwStoryGraph.Node n = single();
				if (n != null && !v.equals(n.title)) { snapshot(); n.title = v; dirty = true; }
			}
		});

		// Library geometry and category switchers.
		if (compact) {
			tileX = M + 4;
			tileY = libY + 20;
			tileCols = 3;
			tileRows = 1;
			tileH = libH - 24;
			int chipW = (width - 2 * M - 8) / CATEGORIES.length;
			for (int i = 0; i < CATEGORIES.length; i++) {
				final int index = i;
				addDrawableChild(CwUi.Button.blue(M + 4 + i * chipW, libY + 4, chipW - 1, 13, CATEGORIES[i], CwUi.Icon.NONE, i == category, b -> { category = index; tileScroll = 0; init(); }));
			}
			tileW = (width - 2 * M - 8 - (tileCols - 1) * 4) / tileCols;
		} else {
			tileX = M + 126;
			tileY = libY + 22;
			tileCols = 3;
			tileRows = 2;
			tileH = Math.max(26, (libH - 28) / 2 - 2);
			int rowH = Math.max(11, Math.min(16, (libH - 24) / CATEGORIES.length));
			for (int i = 0; i < CATEGORIES.length; i++) {
				final int index = i;
				addDrawableChild(CwUi.Button.blue(M + 6, libY + 22 + i * (rowH + 1), 110, rowH, CATEGORIES[i], CwUi.Icon.NONE, i == category, b -> { category = index; tileScroll = 0; init(); }));
			}
			tileW = (previewX - 8 - tileX - (tileCols - 1) * 6) / tileCols;
		}
		refreshFields();
	}

	private int tileGap() { return compact ? 4 : 6; }

	private void rebuildInspector() {
		if (titleField == null) return;
		for (ClickableWidget w : new ClickableWidget[]{titleField}) w.visible = false;
		for (var f : dataFields) f.visible = false;
		for (var b : pickers) b.visible = false;
		int top = leftY + (compact ? 30 : 46);
		inspector.begin(inspX + 6, top, inspW - 14, leftY + leftH - 4 - top);
		CwStoryGraph.Node n = single();
		if (n != null) {
			inspector.add("Название", 16, titleField);
			String[] keys = keys(n);
			for (int i = 0; i < keys.length && i < dataFields.length; i++) inspector.add(label(keys[i]), 16, dataFields[i]);
			inspector.add(null, 16, pickers[0], pickers[1]);
			inspector.add(null, 16, pickers[2], pickers[3]);
			inspector.add(null, 16, pickers[4], pickers[5]);
		}
		inspector.layout();
	}

	// ------------------------------------------------------------------ graph operations

	private void snapshot() { undo.addLast(CwStoryGraph.toJson(graph)); while (undo.size() > 100) undo.removeFirst(); redo.clear(); }
	private void restore(String json) { graph = CwStoryGraph.fromJson(json); selected.retainAll(graph.nodes.keySet()); dirty = true; refreshFields(); }
	private void undo() { if (undo.isEmpty()) return; redo.addLast(CwStoryGraph.toJson(graph)); restore(undo.removeLast()); }
	private void redo() { if (redo.isEmpty()) return; undo.addLast(CwStoryGraph.toJson(graph)); restore(redo.removeLast()); }

	private void addNode(Tile tile) {
		snapshot();
		CwStoryGraph.Node n = new CwStoryGraph.Node();
		n.id = tile.kind().name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
		n.kind = tile.kind();
		n.title = tile.title();
		n.data.putAll(tile.preset());
		double cx = (-panX) / zoom - NODE_W / 2.0, cy = (-panY) / zoom - NODE_H / 2.0;
		double[] free = findFree(cx, cy, null);
		n.x = free[0];
		n.y = free[1];
		graph.nodes.put(n.id, n);
		selected.clear();
		selected.add(n.id);
		dirty = true;
		refreshFields();
		status = "Добавлен модуль: " + tile.title();
	}

	private double[] findFree(double x, double y, String ignore) {
		if (!overlaps(x, y, ignore, Set.of())) return new double[]{x, y};
		for (int ring = 1; ring < 40; ring++)
			for (int dx = -ring; dx <= ring; dx++)
				for (int dy = -ring; dy <= ring; dy++)
					if (Math.abs(dx) == ring || Math.abs(dy) == ring) {
						double nx = x + dx * (NODE_W + 14), ny = y + dy * (NODE_H + 14);
						if (!overlaps(nx, ny, ignore, Set.of())) return new double[]{nx, ny};
					}
		return new double[]{x, y};
	}

	private boolean overlaps(double x, double y, String ignore, Set<String> moving) {
		double gap = 4.0 / zoom;
		for (CwStoryGraph.Node other : graph.nodes.values()) {
			if (other == null || other.id.equals(ignore) || moving.contains(other.id)) continue;
			if (x < other.x + NODE_W + gap && x + NODE_W + gap > other.x && y < other.y + NODE_H + gap && y + NODE_H + gap > other.y) return true;
		}
		return false;
	}

	private void connectSelected() {
		if (selected.size() != 2) { status = "Выберите два модуля с Shift, затем «Связь»"; return; }
		var ids = new ArrayList<>(selected);
		link(ids.get(0), ids.get(1));
	}

	private void link(String from, String to) {
		if (from.equals(to)) return;
		if (graph.edges.stream().anyMatch(e -> e.from.equals(from) && e.to.equals(to))) { status = "Эти модули уже соединены"; return; }
		snapshot();
		CwStoryGraph.Edge e = new CwStoryGraph.Edge();
		e.id = UUID.randomUUID().toString();
		e.from = from;
		e.to = to;
		graph.edges.add(e);
		dirty = true;
		status = "Соединено: " + graph.nodes.get(from).title + " → " + graph.nodes.get(to).title;
	}

	private void deleteSelected() {
		if (selected.isEmpty()) return;
		snapshot();
		graph.nodes.keySet().removeAll(selected);
		graph.edges.removeIf(e -> selected.contains(e.from) || selected.contains(e.to));
		selected.clear();
		dirty = true;
		refreshFields();
	}

	private void copySelected() {
		if (selected.isEmpty()) return;
		snapshot();
		Set<String> added = new LinkedHashSet<>();
		for (String id : Set.copyOf(selected)) {
			CwStoryGraph.Node old = graph.nodes.get(id);
			if (old == null || old.kind == CwStoryGraph.Kind.START) continue;
			CwStoryGraph.Graph holder = new CwStoryGraph.Graph();
			holder.nodes.put(old.id, old);
			CwStoryGraph.Node n = CwStoryGraph.fromJson(CwStoryGraph.toJson(holder)).nodes.get(old.id);
			if (n == null) continue;
			n.id = old.id + "_copy_" + UUID.randomUUID().toString().substring(0, 4);
			n.x += 28;
			n.y += 28;
			graph.nodes.put(n.id, n);
			added.add(n.id);
		}
		selected.clear();
		selected.addAll(added);
		dirty = true;
		refreshFields();
	}

	private void validateGraph() {
		var errors = CwStoryGraph.validate(graph);
		status = errors.isEmpty() ? "Проверка пройдена: ошибок нет" : "Ошибки: " + String.join("; ", errors);
	}

	private void saveGraph() {
		var buf = PacketByteBufs.create();
		buf.writeVarInt(graph.revision);
		buf.writeString(CwStoryGraph.toJson(graph), CwGraphNetwork.MAX_JSON);
		ClientPlayNetworking.send(ModPackets.CW_GRAPH_SAVE, buf);
		status = "Сохранение…";
	}

	private void testSelected() {
		CwStoryGraph.Node n = single();
		if (n == null) { status = "Выберите модуль, с которого начать тест"; return; }
		var buf = PacketByteBufs.create();
		buf.writeString(graph.graphId, 80);
		buf.writeString(n.id, 64);
		ClientPlayNetworking.send(ModPackets.CW_GRAPH_TEST, buf);
	}

	private void applyPreset() {
		CwStoryGraph.Node n = single();
		if (n == null) { status = "Сначала выберите модуль"; return; }
		snapshot();
		String key, value;
		switch (n.kind) {
			case START -> { key = "trigger"; value = next(n.data.get(key), new String[]{"npc", "first_enter", "point", "manual"}); }
			case ACTION -> { key = "action"; value = next(n.data.get(key), new String[]{"give", "take", "health", "food", "saturation", "xp", "flag", "graph", "close", "npc_say", "npc_move"}); }
			case CONDITION -> { key = "condition"; value = next(n.data.get(key), new String[]{"flag:name=true", "item:minecraft:stone", "level>=1", "health>=10", "food>=10"}); }
			case WAIT -> { key = "ticks"; value = next(n.data.get(key), new String[]{"20", "60", "100", "200"}); }
			case END -> { key = "result"; value = next(n.data.get(key), new String[]{"close", "complete", "pause"}); }
			case CHOICE -> { key = "close"; value = next(n.data.get(key), new String[]{"false", "true"}); }
			default -> { key = "sound"; value = next(n.data.get(key), new String[]{"", "minecraft:entity.villager.yes", "minecraft:entity.villager.no"}); }
		}
		n.data.put(key, value);
		dirty = true;
		refreshFields();
		status = "Выбран вариант: " + value;
	}

	private String next(String current, String[] values) {
		for (int i = 0; i < values.length; i++) if (values[i].equals(current)) return values[(i + 1) % values.length];
		return values[0];
	}

	private void usePlayerPosition() {
		CwStoryGraph.Node n = single();
		if (n == null || n.kind != CwStoryGraph.Kind.TELEPORT || client == null || client.player == null || client.world == null) {
			status = "«Моя точка» работает для модуля телепортации";
			return;
		}
		snapshot();
		n.data.put("world", client.world.getRegistryKey().getValue().toString());
		n.data.put("x", String.format(java.util.Locale.ROOT, "%.2f", client.player.getX()));
		n.data.put("y", String.format(java.util.Locale.ROOT, "%.2f", client.player.getY()));
		n.data.put("z", String.format(java.util.Locale.ROOT, "%.2f", client.player.getZ()));
		dirty = true;
		refreshFields();
		status = "Координаты игрока записаны";
	}

	private void openItemPicker() {
		CwStoryGraph.Node n = single();
		if (n == null || (n.kind != CwStoryGraph.Kind.ACTION && n.kind != CwStoryGraph.Kind.ITEM)) { status = "Предмет выбирается для модулей «Действие» и «Предмет»"; return; }
		if (client != null) client.setScreen(new CwItemPickerScreen(this, id -> {
			snapshot();
			n.data.put("value", id.toString());
			if (!n.data.containsKey("action")) n.data.put("action", "give");
			dirty = true;
			refreshFields();
			status = "Выбран предмет: " + id;
		}));
	}

	private void openSkinPicker() {
		CwStoryGraph.Node n = single();
		if (n == null || n.kind != CwStoryGraph.Kind.DIALOGUE) { status = "Скин выбирается для модуля «Диалог с NPC»"; return; }
		if (client != null) client.setScreen(new CwSkinPickerScreen(this, availableSkins, name -> {
			snapshot();
			n.data.put("portrait", name);
			dirty = true;
			refreshFields();
			status = "Выбран скин: " + name;
		}));
	}

	private void openActionPicker() {
		CwStoryGraph.Node n = single();
		if (n == null || n.kind != CwStoryGraph.Kind.ACTION) { status = "Сначала выберите модуль «Действие»"; return; }
		java.util.List<CwOptionPickerScreen.Option> options = java.util.List.of(
				new CwOptionPickerScreen.Option("Выдать предмет", "give"), new CwOptionPickerScreen.Option("Забрать предмет", "take"),
				new CwOptionPickerScreen.Option("Изменить здоровье", "health"), new CwOptionPickerScreen.Option("Изменить сытость", "food"),
				new CwOptionPickerScreen.Option("Выдать опыт", "xp"), new CwOptionPickerScreen.Option("Установить сюжетный флаг", "flag"),
				new CwOptionPickerScreen.Option("Открыть другую схему", "graph"), new CwOptionPickerScreen.Option("Закрыть диалог", "close"),
				new CwOptionPickerScreen.Option("Реплика NPC в чат", "npc_say"), new CwOptionPickerScreen.Option("Переместить NPC", "npc_move"));
		if (client != null) client.setScreen(new CwOptionPickerScreen(this, "Что должно произойти?", options, value -> {
			snapshot();
			n.data.put("action", value);
			dirty = true;
			refreshFields();
			status = "Действие выбрано";
		}));
	}

	private void openAnimationEditor() {
		CwStoryGraph.Node n = single();
		if (n == null) { status = "Выберите модуль NPC или действия"; return; }
		if (client != null) client.setScreen(new CwAnimationEditorScreen(this, n.data.get("animation_json"), null, json -> {
			snapshot();
			n.data.put("animation_json", json);
			n.data.put("animation", "custom");
			dirty = true;
			refreshFields();
			status = "Цикл анимации записан в модуль";
		}));
	}

	private CwStoryGraph.Node single() { return selected.size() == 1 ? graph.nodes.get(selected.iterator().next()) : null; }

	private String[] keys(CwStoryGraph.Node n) {
		if (n == null || n.kind == null) return new String[0];
		return switch (n.kind) {
			case START -> new String[]{"trigger", "npc", "marker", "condition", "note"};
			case DIALOGUE -> new String[]{"npc", "text", "speaker", "portrait", "sound"};
			case CHOICE -> new String[]{"button", "player_text", "port", "condition", "close"};
			case CONDITION -> new String[]{"condition", "true_port", "false_port", "flag", "value"};
			case ACTION, NPC, ITEM, BLOCK, EFFECT, AUDIO, CHECKPOINT, TIME, TRIGGER, GROUP -> new String[]{"action", "target", "value", "amount", "animation"};
			case TELEPORT -> new String[]{"marker", "world", "x", "y", "z"};
			case WAIT -> new String[]{"ticks", "npc_text", "npc", "animation", "note"};
			case END -> new String[]{"result", "next_graph", "flag", "value", "message"};
		};
	}

	private String label(String key) {
		return switch (key) {
			case "trigger" -> "Когда запускать";
			case "npc" -> "NPC";
			case "marker" -> "Метка";
			case "condition" -> "Условие";
			case "text" -> "Текст реплики NPC";
			case "speaker" -> "Имя говорящего";
			case "portrait" -> "Скин NPC";
			case "sound" -> "Звук";
			case "button" -> "Надпись кнопки";
			case "player_text" -> "Полная реплика игрока";
			case "port" -> "Название ветки";
			case "close" -> "Закрыть диалог";
			case "true_port" -> "Если верно";
			case "false_port" -> "Если неверно";
			case "flag" -> "Флаг сюжета";
			case "value" -> "Значение / предмет";
			case "action" -> "Действие";
			case "target" -> "Цель";
			case "amount" -> "Количество";
			case "world" -> "Мир";
			case "x" -> "X";
			case "y" -> "Y";
			case "z" -> "Z";
			case "ticks" -> "Ожидание (20 = 1 с)";
			case "npc_text" -> "Реплика при ожидании";
			case "animation" -> "Анимация NPC";
			case "result" -> "Результат";
			case "next_graph" -> "Следующая схема";
			case "message" -> "Сообщение";
			default -> "Заметка";
		};
	}

	private void changeData(int index, String value) {
		if (editingFields) return;
		CwStoryGraph.Node n = single();
		String[] keys = keys(n);
		if (n == null || index >= keys.length) return;
		String key = keys[index];
		if (value.equals(n.data.getOrDefault(key, ""))) return;
		snapshot();
		if (value.isBlank()) n.data.remove(key); else n.data.put(key, value);
		dirty = true;
	}

	private void refreshFields() {
		if (titleField == null) return;
		editingFields = true;
		CwStoryGraph.Node n = single();
		if (n != null && n.kind == null) n.kind = CwStoryGraph.Kind.DIALOGUE;
		titleField.setText(n == null ? "" : n.title);
		titleField.setEditable(n != null);
		String[] keys = keys(n);
		for (int i = 0; i < dataFields.length; i++) {
			boolean active = i < keys.length;
			dataFields[i].setText(active ? n.data.getOrDefault(keys[i], "") : "");
			dataFields[i].setEditable(active);
		}
		editingFields = false;
		rebuildInspector();
	}

	// ------------------------------------------------------------------ input

	private double sx(CwStoryGraph.Node n) { return canvasX + canvasW / 2.0 + panX + n.x * zoom; }
	private double sy(CwStoryGraph.Node n) { return canvasY + 11 + canvasH / 2.0 + panY + n.y * zoom; }
	private boolean inCanvas(double mx, double my) { return mx >= canvasX && mx < canvasX + canvasW && my >= canvasY + 22 && my < canvasY + canvasH - 14; }

	private CwStoryGraph.Node hit(double mx, double my) {
		CwStoryGraph.Node found = null;
		for (CwStoryGraph.Node n : graph.nodes.values())
			if (mx >= sx(n) - 8 && mx <= sx(n) + NODE_W * zoom + 8 && my >= sy(n) && my <= sy(n) + NODE_H * zoom) found = n;
		return found;
	}

	private List<CwStoryGraph.Node> listNodes() {
		Predicate<CwStoryGraph.Node> filter = switch (tab) {
			case 1 -> n -> n.kind == CwStoryGraph.Kind.NPC || n.kind == CwStoryGraph.Kind.DIALOGUE;
			case 2 -> n -> n.kind == CwStoryGraph.Kind.DIALOGUE || n.kind == CwStoryGraph.Kind.CHOICE;
			case 3 -> n -> n.kind == CwStoryGraph.Kind.START || n.kind == CwStoryGraph.Kind.END || n.kind == CwStoryGraph.Kind.TRIGGER || n.kind == CwStoryGraph.Kind.CHECKPOINT;
			case 4 -> n -> n.kind == CwStoryGraph.Kind.CONDITION;
			case 5 -> n -> n.kind == CwStoryGraph.Kind.ACTION || n.kind == CwStoryGraph.Kind.EFFECT || n.kind == CwStoryGraph.Kind.AUDIO || n.kind == CwStoryGraph.Kind.WAIT;
			case 6 -> n -> n.kind == CwStoryGraph.Kind.ITEM || (n.kind == CwStoryGraph.Kind.ACTION && ("give".equals(n.data.get("action")) || "take".equals(n.data.get("action"))));
			case 7 -> n -> n.kind == CwStoryGraph.Kind.TELEPORT || n.kind == CwStoryGraph.Kind.BLOCK || n.kind == CwStoryGraph.Kind.TIME;
			default -> n -> true;
		};
		List<CwStoryGraph.Node> out = new ArrayList<>();
		for (CwStoryGraph.Node n : graph.nodes.values()) if (n != null && filter.test(n)) out.add(n);
		return out;
	}

	private int rowH() { return compact ? 24 : 30; }
	private int listTop() { return leftY + (compact ? 16 : 24); }

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) return true;
		if (tab != 8 && mx >= M && mx < M + leftW && my >= listTop() && my < leftY + leftH - 3) {
			var nodes = listNodes();
			int i = (int) ((my - listTop()) / rowH()) + listScroll;
			if (i >= 0 && i < nodes.size()) {
				var n = nodes.get(i);
				selected.clear();
				selected.add(n.id);
				panX = -(n.x + NODE_W / 2.0) * zoom;
				panY = -(n.y + NODE_H / 2.0) * zoom - 11;
				refreshFields();
			}
			return true;
		}
		Tile tile = tileAt(mx, my);
		if (tile != null) { addNode(tile); return true; }
		if (!inCanvas(mx, my)) return false;
		if (button == 2 || button == 1) { panning = true; return true; }
		CwStoryGraph.Node n = hit(mx, my);
		if (n == null) { if (!hasShiftDown()) selected.clear(); refreshFields(); return true; }
		double right = sx(n) + NODE_W * zoom, cy = sy(n) + NODE_H * zoom / 2;
		if (connecting != null && !connecting.equals(n.id)) { link(connecting, n.id); connecting = null; return true; }
		if (Math.abs(mx - right) < 10 && Math.abs(my - cy) < 12) { connecting = n.id; status = "Нажмите на модуль, к которому провести линию"; return true; }
		if (hasShiftDown()) { if (!selected.add(n.id)) selected.remove(n.id); }
		else if (!selected.contains(n.id)) { selected.clear(); selected.add(n.id); }
		dragging = true;
		dragSnapshot = false;
		refreshFields();
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (panning) { panX += dx; panY += dy; return true; }
		if (dragging && !selected.isEmpty() && Math.abs(dx) + Math.abs(dy) > 0) {
			double wx = dx / zoom, wy = dy / zoom;
			boolean blocked = false;
			for (String id : selected) {
				CwStoryGraph.Node n = graph.nodes.get(id);
				if (n != null && overlaps(n.x + wx, n.y + wy, id, selected)) { blocked = true; break; }
			}
			if (!blocked) {
				if (!dragSnapshot) { snapshot(); dragSnapshot = true; }
				for (String id : selected) { CwStoryGraph.Node n = graph.nodes.get(id); if (n != null) { n.x += wx; n.y += wy; } }
				dirty = true;
			}
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) { panning = false; dragging = false; return super.mouseReleased(mx, my, button); }

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (inspector.mouseScrolled(mx, my, amount)) return true;
		if (mx >= M && mx < M + leftW && my >= leftY && my < leftY + leftH) {
			listScroll = Math.max(0, Math.min(Math.max(0, listNodes().size() - 1), listScroll - (int) Math.signum(amount)));
			return true;
		}
		if (my >= libY && (previewW == 0 || mx < previewX)) {
			int pages = Math.max(0, (LIBRARY.get(category).size() + tileCols - 1) / tileCols - tileRows);
			tileScroll = Math.max(0, Math.min(pages, tileScroll - (int) Math.signum(amount)));
			return true;
		}
		if (!inCanvas(mx, my)) return false;
		double old = zoom;
		zoom = Math.max(.35, Math.min(2.5, zoom + amount * .1));
		double ox = mx - (canvasX + canvasW / 2.0), oy = my - (canvasY + 11 + canvasH / 2.0);
		panX += ox * (1 - zoom / old);
		panY += oy * (1 - zoom / old);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		boolean typing = getFocused() instanceof TextFieldWidget;
		if (!typing) {
			if (hasControlDown() && key == GLFW.GLFW_KEY_Z) { undo(); return true; }
			if (hasControlDown() && key == GLFW.GLFW_KEY_Y) { redo(); return true; }
			if (hasControlDown() && key == GLFW.GLFW_KEY_C) { copySelected(); return true; }
			if (key == GLFW.GLFW_KEY_DELETE) { deleteSelected(); return true; }
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public void close() {
		if (dirty && !discardArmed) { discardArmed = true; status = "Есть несохранённые правки. Escape ещё раз — выйти без сохранения."; return; }
		super.close();
	}

	@Override public boolean shouldPause() { return false; }

	// ------------------------------------------------------------------ rendering

	private Tile tileAt(double mx, double my) {
		int gap = tileGap();
		if (mx < tileX || my < tileY || my >= tileY + tileRows * (tileH + gap)) return null;
		int col = (int) ((mx - tileX) / (tileW + gap)), row = (int) ((my - tileY) / (tileH + gap));
		if (col >= tileCols || row >= tileRows || mx - tileX - col * (tileW + gap) > tileW || my - tileY - row * (tileH + gap) > tileH) return null;
		int i = (tileScroll + row) * tileCols + col;
		var list = LIBRARY.get(category);
		return i >= 0 && i < list.size() ? list.get(i) : null;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0xF2060A10);
		renderTopBar(c);
		renderLeft(c);
		renderCanvas(c, mx, my);
		renderInspector(c);
		renderLibrary(c, mx, my);
		if (previewW > 0) renderPreview(c, mx, my);
		super.render(c, mx, my, delta);
		inspector.render(c, textRenderer);
	}

	private void renderTopBar(DrawContext c) {
		CwUi.tabletPanel(c, M, 2, width - 2 * M, topH - 2, false);
		if (compact) {
			c.drawTextWithShadow(textRenderer, "Шёпот", M + 6, 4, CwUi.GOLD_FILL);
			c.drawTextWithShadow(textRenderer, "Люмиры", M + 6, 14, CwUi.GOLD_FILL);
			return;
		}
		CwUi.tabletPanel(c, M + 6, 8, 34, 34, true);
		c.fill(M + 15, 17, M + 31, 33, 0xFFD9C394);
		c.fill(M + 22, 17, M + 24, 33, 0xFF3A2C14);
		CwUi.drawScaled(c, textRenderer, "Шёпот Люмиры", M + 48, 11, 1.25f, CwUi.GOLD_FILL);
		c.drawTextWithShadow(textRenderer, "Редактор сюжетной ветки", M + 48, 28, 0xFF9DB8D8);
	}

	private void renderLeft(DrawContext c) {
		CwUi.tabletPanel(c, M, leftY, leftW, leftH, false);
		if (tab == 8) {
			c.drawTextWithShadow(textRenderer, "Настройки ветки", M + 6, leftY + 5, CwUi.GOLD_FILL);
			int y = leftY + 18;
			for (String line : new String[]{"Граф: " + graph.graphId, "Версия: " + graph.revision, "Модулей: " + graph.nodes.size(), "Связей: " + graph.edges.size(), dirty ? "Есть правки" : "Сохранено"}) {
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, leftW - 10), M + 6, y, CwUi.TEXT);
				y += 11;
			}
			return;
		}
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Список: " + TABS[tab].toLowerCase(), leftW - 10), M + 6, leftY + 5, CwUi.GOLD_FILL);
		var nodes = listNodes();
		c.enableScissor(M + 2, listTop(), M + leftW - 2, leftY + leftH - 3);
		int rowH = rowH();
		for (int i = listScroll; i < nodes.size(); i++) {
			int y = listTop() + (i - listScroll) * rowH;
			if (y > leftY + leftH) break;
			var n = nodes.get(i);
			boolean sel = selected.contains(n.id);
			c.fill(M + 4, y, M + leftW - 4, y + rowH - 2, sel ? 0xAA1A3E70 : 0x88101828);
			CwUi.border(c, M + 4, y, leftW - 8, rowH - 2, sel ? CwUi.BLUE : CwUi.BLUE_DIM);
			c.fill(M + 7, y + 3, M + 10, y + rowH - 5, color(n.kind));
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(n.title == null || n.title.isBlank() ? n.id : n.title, leftW - 22), M + 14, y + 3, CwUi.TEXT);
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(kindName(n.kind), leftW - 22), M + 14, y + 13, CwUi.TEXT_DIM);
		}
		if (nodes.isEmpty()) c.drawTextWithShadow(textRenderer, "Пока пусто", M + 8, listTop() + 4, CwUi.TEXT_DIM);
		c.disableScissor();
	}

	private void renderCanvas(DrawContext c, int mx, int my) {
		CwUi.tabletPanel(c, canvasX, canvasY, canvasW, canvasH, true);
		if (!compact) {
			String title = "Сцена: " + (graph.graphId == null ? "" : graph.graphId);
			if (canvasX + 8 + textRenderer.getWidth(title) + 8 < toolbarEnd) c.drawTextWithShadow(textRenderer, title, canvasX + 8, canvasY + 7, CwUi.TEXT);
		}
		int top = canvasY + 22, bottom = canvasY + canvasH - 14;
		c.enableScissor(canvasX + 1, top, canvasX + canvasW - 1, bottom);
		int step = Math.max(8, (int) (24 * zoom));
		int gx0 = (int) (((canvasX + canvasW / 2.0 + panX) % step + step) % step) + canvasX;
		int gy0 = (int) (((canvasY + 11 + canvasH / 2.0 + panY) % step + step) % step) + canvasY;
		for (int x = gx0; x < canvasX + canvasW; x += step) c.fill(x, top, x + 1, bottom, 0x1E3C5A72);
		for (int y = gy0; y < bottom; y += step) if (y >= top) c.fill(canvasX, y, canvasX + canvasW, y + 1, 0x1E3C5A72);
		for (CwStoryGraph.Edge e : graph.edges) {
			var a = graph.nodes.get(e.from);
			var b = graph.nodes.get(e.to);
			if (a == null || b == null) continue;
			elbow(c, (int) (sx(a) + NODE_W * zoom), (int) (sy(a) + NODE_H * zoom / 2), (int) sx(b), (int) (sy(b) + NODE_H * zoom / 2), 0xFF5AA9FF);
		}
		if (connecting != null) {
			var a = graph.nodes.get(connecting);
			if (a != null) elbow(c, (int) (sx(a) + NODE_W * zoom), (int) (sy(a) + NODE_H * zoom / 2), mx, my, 0xFFFF8FDA);
		}
		for (CwStoryGraph.Node n : graph.nodes.values()) {
			if (n == null) continue;
			if (n.kind == null) n.kind = CwStoryGraph.Kind.DIALOGUE;
			drawNode(c, n);
		}
		if (!compact) renderMinimap(c);
		c.disableScissor();
		c.fill(canvasX + 2, canvasY + canvasH - 13, canvasX + canvasW - 2, canvasY + canvasH - 2, 0xAA050A12);
		String text = status.isEmpty() ? "Колесо — масштаб • ПКМ — сдвиг" : status;
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text, canvasW - 10), canvasX + 5, canvasY + canvasH - 11, status.startsWith("Ошиб") || status.startsWith("ERROR") ? 0xFFFF7777 : 0xFF8FFFA0);
	}

	private void drawNode(DrawContext c, CwStoryGraph.Node n) {
		int x = (int) sx(n), y = (int) sy(n), w = (int) (NODE_W * zoom), h = (int) (NODE_H * zoom);
		if (x > canvasX + canvasW || x + w < canvasX || y > canvasY + canvasH || y + h < canvasY) return;
		boolean sel = selected.contains(n.id);
		int col = color(n.kind);
		c.fill(x, y, x + w, y + h, sel ? 0xEE16305A : 0xDD0E1522);
		CwUi.border(c, x, y, w, h, sel ? CwUi.BLUE : (col & 0x00FFFFFF) | 0xAA000000);
		if (sel) CwUi.border(c, x - 1, y - 1, w + 2, h + 2, 0x663C8DFF);
		c.fill(x + 1, y + 1, x + 4, y + h - 1, col);
		c.fill(x - 3, y + h / 2 - 3, x + 3, y + h / 2 + 4, 0xFF8FD8FF);
		c.fill(x + w - 3, y + h / 2 - 3, x + w + 3, y + h / 2 + 4, 0xFFFF8FDA);
		if (zoom > .55) {
			String title = n.title == null || n.title.isBlank() ? n.id : n.title;
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(title, Math.max(10, w - 14)), x + 8, y + 6, CwUi.TEXT);
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(subtitle(n), Math.max(10, w - 14)), x + 8, y + 19, CwUi.TEXT_DIM);
		}
	}

	private String subtitle(CwStoryGraph.Node n) {
		return switch (n.kind) {
			case DIALOGUE -> n.data.getOrDefault("npc", "NPC");
			case CHOICE -> "«" + n.data.getOrDefault("button", "ответ") + "»";
			case ACTION -> n.data.getOrDefault("action", "действие");
			case CONDITION -> n.data.getOrDefault("condition", "условие");
			case WAIT -> n.data.getOrDefault("ticks", "20") + " тиков";
			default -> kindName(n.kind);
		};
	}

	private void elbow(DrawContext c, int x1, int y1, int x2, int y2, int color) {
		int mid = x2 >= x1 ? (x1 + x2) / 2 : x1 + 18;
		hline(c, x1, mid, y1, color);
		if (x2 >= x1) {
			vline(c, mid, y1, y2, color);
			hline(c, mid, x2, y2, color);
		} else {
			int lane = (y1 + y2) / 2;
			vline(c, mid, y1, lane, color);
			hline(c, x2 - 18, mid, lane, color);
			vline(c, x2 - 18, lane, y2, color);
			hline(c, x2 - 18, x2, y2, color);
		}
		c.fill(x2 - 4, y2 - 3, x2 - 3, y2 + 4, color);
		c.fill(x2 - 3, y2 - 2, x2 - 2, y2 + 3, color);
		c.fill(x2 - 2, y2 - 1, x2 - 1, y2 + 2, color);
	}

	private void hline(DrawContext c, int a, int b, int y, int color) { c.fill(Math.min(a, b), y, Math.max(a, b) + 1, y + 1, color); }
	private void vline(DrawContext c, int x, int a, int b, int color) { c.fill(x, Math.min(a, b), x + 1, Math.max(a, b) + 1, color); }

	private void renderMinimap(DrawContext c) {
		if (graph.nodes.isEmpty()) return;
		int mw = 86, mh = 54, x0 = canvasX + canvasW - mw - 8, y0 = canvasY + canvasH - mh - 20;
		c.fill(x0, y0, x0 + mw, y0 + mh, 0xAA050A12);
		CwUi.border(c, x0, y0, mw, mh, CwUi.BLUE_DIM);
		double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
		for (var n : graph.nodes.values()) { minX = Math.min(minX, n.x); minY = Math.min(minY, n.y); maxX = Math.max(maxX, n.x + NODE_W); maxY = Math.max(maxY, n.y + NODE_H); }
		double sc = Math.min((mw - 6) / Math.max(1, maxX - minX), (mh - 6) / Math.max(1, maxY - minY));
		for (var n : graph.nodes.values()) {
			int px = x0 + 3 + (int) ((n.x - minX) * sc), py = y0 + 3 + (int) ((n.y - minY) * sc);
			c.fill(px, py, px + Math.max(2, (int) (NODE_W * sc)), py + Math.max(2, (int) (NODE_H * sc)), selected.contains(n.id) ? CwUi.BLUE : color(n.kind));
		}
	}

	private void renderInspector(DrawContext c) {
		CwUi.tabletPanel(c, inspX, leftY, inspW, leftH, false);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Настройки элемента", inspW - 10), inspX + 6, leftY + 5, CwUi.GOLD_FILL);
		CwStoryGraph.Node n = single();
		if (n == null) {
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(selected.size() > 1 ? "Выбрано: " + selected.size() : "Выберите модуль", inspW - 10), inspX + 6, leftY + 20, CwUi.TEXT_DIM);
			return;
		}
		int sw = compact ? 10 : 18;
		c.fill(inspX + 6, leftY + 16, inspX + 6 + sw, leftY + 16 + sw, color(n.kind));
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(kindName(n.kind), inspW - sw - 18), inspX + 10 + sw, leftY + 16 + (sw - 8) / 2, CwUi.TEXT);
	}

	private void renderLibrary(DrawContext c, int mx, int my) {
		int panelW = compact ? width - 2 * M : previewX - 6 - M;
		CwUi.tabletPanel(c, M, libY, panelW, libH, false);
		if (!compact) c.drawTextWithShadow(textRenderer, "Библиотека элементов", M + 8, libY + 6, CwUi.GOLD_FILL);
		var list = LIBRARY.get(category);
		int gap = tileGap();
		for (int row = 0; row < tileRows; row++)
			for (int col = 0; col < tileCols; col++) {
				int i = (tileScroll + row) * tileCols + col;
				if (i >= list.size()) continue;
				var t = list.get(i);
				int x = tileX + col * (tileW + gap), y = tileY + row * (tileH + gap);
				boolean hot = mx >= x && mx < x + tileW && my >= y && my < y + tileH;
				c.fill(x, y, x + tileW, y + tileH, hot ? 0xDD1A3E70 : 0xCC0E1522);
				CwUi.border(c, x, y, tileW, tileH, hot ? CwUi.BLUE : CwUi.BLUE_DIM);
				int icon = Math.min(14, tileH - 8);
				c.fill(x + 4, y + (tileH - icon) / 2, x + 4 + icon, y + (tileH - icon) / 2 + icon, color(t.kind()));
				int tx = x + icon + 9, tw = tileW - icon - 13;
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(t.title(), tw), tx, y + (tileH - 19) / 2, CwUi.TEXT);
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(t.sub(), tw), tx, y + (tileH - 19) / 2 + 10, CwUi.TEXT_DIM);
			}
		int pages = (list.size() + tileCols - 1) / tileCols;
		if (pages > tileRows) c.drawTextWithShadow(textRenderer, "колесо ▼", (compact ? width - M - 4 : previewX - 10) - textRenderer.getWidth("колесо ▼") - 4, libY + (compact ? libH - 9 : 6), CwUi.TEXT_DIM);
	}

	private void renderPreview(DrawContext c, int mx, int my) {
		CwUi.tabletPanel(c, previewX, libY, previewW, libH, false);
		c.drawTextWithShadow(textRenderer, "Предпросмотр", previewX + 8, libY + 6, CwUi.GOLD_FILL);
		int x = previewX + 6, y = libY + 20, w = previewW - 12, h = libH - 26;
		c.fill(x, y, x + w, y + h, 0xFF05080D);
		CwUi.border(c, x, y, w, h, CwUi.BLUE_DIM);
		CwStoryGraph.Node n = single();
		if (n == null) { c.drawTextWithShadow(textRenderer, "Выберите диалог", x + 8, y + 8, CwUi.TEXT_DIM); return; }
		if (n.kind == CwStoryGraph.Kind.DIALOGUE) {
			CwUi.drawNpc(c, CwUi.nearestNpc(client), x + 1, y + 1, x + w / 3, y + h - 1, mx, my);
			int bx = x + w / 3 + 2;
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(n.data.getOrDefault("npc", "NPC"), x + w - bx - 4), bx, y + 5, CwUi.NPC);
			int ty = CwUi.drawWrapped(c, textRenderer, textRenderer.trimToWidth(n.data.getOrDefault("text", n.title), (x + w - bx - 4) * 3), bx, y + 17, x + w - bx - 6, CwUi.TEXT);
			for (var e : graph.edges) {
				if (!e.from.equals(n.id)) continue;
				var target = graph.nodes.get(e.to);
				if (target == null || target.kind != CwStoryGraph.Kind.CHOICE || ty > y + h - 14) continue;
				c.fill(bx, ty + 2, x + w - 4, ty + 13, 0xFF1A3E70);
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(target.data.getOrDefault("button", target.title), x + w - bx - 10), bx + 3, ty + 4, CwUi.TEXT);
				ty += 14;
			}
		} else if (n.kind == CwStoryGraph.Kind.CHOICE) {
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Кнопка: " + n.data.getOrDefault("button", n.title), w - 12), x + 8, y + 8, CwUi.TEXT);
			CwUi.drawWrapped(c, textRenderer, "В историю: " + n.data.getOrDefault("player_text", "—"), x + 8, y + 22, w - 16, CwUi.PLAYER);
		} else {
			c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(kindName(n.kind) + ": " + subtitle(n), w - 12), x + 8, y + 8, CwUi.TEXT);
		}
	}

	private String kindName(CwStoryGraph.Kind k) {
		if (k == null) return "не задано";
		return switch (k) {
			case START -> "Начало"; case END -> "Конец"; case DIALOGUE -> "Диалог с NPC"; case CHOICE -> "Ответ игрока";
			case CONDITION -> "Условие"; case ACTION -> "Действие"; case TELEPORT -> "Перемещение"; case WAIT -> "Пауза";
			case TRIGGER -> "Триггер"; case NPC -> "NPC"; case ITEM -> "Предмет"; case BLOCK -> "Блок"; case EFFECT -> "Эффект";
			case AUDIO -> "Звук"; case CHECKPOINT -> "Сохранение"; case TIME -> "Время"; case GROUP -> "Группа";
		};
	}

	private int color(CwStoryGraph.Kind k) {
		if (k == null) return 0xFFFF5555;
		return switch (k) {
			case START -> 0xFF65D684; case END -> 0xFFFF6A7A; case DIALOGUE -> 0xFF6DB7FF; case CHOICE -> 0xFFFFC55C;
			case CONDITION -> 0xFFC488FF; case ACTION -> 0xFFFF8CB9; case TELEPORT -> 0xFF58E1DB; case WAIT -> 0xFFA8B2C1;
			default -> 0xFF78A6C8;
		};
	}
}
