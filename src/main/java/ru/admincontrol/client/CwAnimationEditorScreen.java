package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.cw.CwAnimation;
import ru.admincontrol.cw.CwAnimation.Cycle;
import ru.admincontrol.cw.CwAnimation.Frame;
import ru.admincontrol.cw.CwAnimation.Rot;
import ru.admincontrol.net.ModPackets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Blockbench-style pose and cycle editor: 3D viewport, bone tree, per-axis rotation, frame timeline, preview,
 * a cycle library on disk and binding of the cycle to a live NPC. Opened from the NPC pose tool (locked to the
 * clicked NPC) or from the story tablet. Everything is stored as real rotation values.
 */
public final class CwAnimationEditorScreen extends Screen {
	private static final List<String> BONES = CwAnimation.BONES;
	private static final int[] DEPTH = {1, 0, 1, 1, 1, 1, 2, 1, 2, 2, 2, 2};
	/** Click boxes in blocks: x0, x1, y0, y1 (x from the model centre, y from the feet). */
	private static final Map<String, float[]> BOXES = Map.of(
			"head", new float[]{-0.25f, 0.25f, 1.40f, 1.88f},
			"body", new float[]{-0.25f, 0.25f, 0.70f, 1.40f},
			"right_arm", new float[]{-0.50f, -0.25f, 0.70f, 1.40f},
			"left_arm", new float[]{0.25f, 0.50f, 0.70f, 1.40f},
			"right_leg", new float[]{-0.25f, 0f, 0f, 0.70f},
			"left_leg", new float[]{0f, 0.25f, 0f, 0.70f});

	private final Screen parent;
	private final Consumer<String> done;
	private final String lockedNpc;
	private Cycle cycle;
	private int frameIndex, axis;
	private String bone = "right_arm";
	private boolean previewing, paused, dragRotate, dragCamera, syncing, libraryMode, compact;
	private long previewT, lastNanos;
	private float camYaw = 0f, camPitch = 8f, zoom = 1f;
	private int npcIndex = -2, libScroll, selectedLib = -1;
	private List<String> library = new ArrayList<>();
	private String status = "";
	private TextFieldWidget nameField, durationField;
	private final TextFieldWidget[] rotFields = new TextFieldWidget[3];
	private final CwScrollColumn column = new CwScrollColumn();
	private ButtonWidget copyButton, deleteButton;
	private int M, leftW, rightX, rightW, vx0, vy0, vx1, vy1, timelineY, timelineH, chipW, boneRow, leftTop;

	public CwAnimationEditorScreen(Screen parent, String json, String lockedNpc, Consumer<String> done) {
		super(Text.literal("Редактор поз и анимаций NPC"));
		this.parent = parent;
		this.done = done;
		this.lockedNpc = lockedNpc;
		this.cycle = CwAnimation.parse(json);
		if (cycle.frames.isEmpty()) cycle.frames.add(new Frame());
	}

	// ------------------------------------------------------------------ model helpers

	private Frame frame() { frameIndex = Math.max(0, Math.min(cycle.frames.size() - 1, frameIndex)); return cycle.frames.get(frameIndex); }
	private Rot rot(String b) { return frame().bones.computeIfAbsent(b, k -> new Rot()); }

	private List<AbstractClientPlayerEntity> npcs() {
		List<AbstractClientPlayerEntity> out = new ArrayList<>();
		if (client != null && client.world != null)
			for (AbstractClientPlayerEntity p : client.world.getPlayers()) if (p != client.player) out.add(p);
		return out;
	}

	private LivingEntity modelEntity() {
		var list = npcs();
		if (npcIndex == -2) {
			npcIndex = -1;
			if (lockedNpc != null) for (int i = 0; i < list.size(); i++) if (list.get(i).getGameProfile().getName().equalsIgnoreCase(lockedNpc)) npcIndex = i;
		}
		if (npcIndex >= 0 && npcIndex < list.size()) return list.get(npcIndex);
		return client == null ? null : client.player;
	}

	private String npcName() {
		modelEntity();
		var list = npcs();
		return npcIndex >= 0 && npcIndex < list.size() ? list.get(npcIndex).getGameProfile().getName() : null;
	}

	// ------------------------------------------------------------------ widgets

	@Override
	protected void init() {
		clearChildren();
		compact = width < 640 || height < 360;
		M = 4;
		leftW = compact ? 98 : 150;
		rightW = compact ? 148 : 190;
		rightX = width - rightW - M;
		vx0 = M + leftW + 4;
		vx1 = rightX - 4;
		vy0 = 26;
		timelineH = compact ? 40 : 58;
		chipW = compact ? 36 : 46;
		timelineY = height - timelineH - M;
		vy1 = timelineY - 4;
		boneRow = compact ? 13 : 15;
		leftTop = 26 + 18;

		// Top action bar (always visible).
		String[] names = {previewing && !paused ? "▶ Просмотр" : "▶ Просмотр", paused ? "Дальше" : "Пауза", cycle.loop ? "Повтор: да" : "Повтор: нет", "Подтвердить", "Отменить"};
		int total = 0;
		int[] w = new int[names.length];
		for (int i = 0; i < names.length; i++) { w[i] = textRenderer.getWidth(names[i]) + 10; total += w[i] + 2; }
		int x = width - M - total;
		for (int i = 0; i < names.length; i++) {
			final int index = i;
			boolean primary = i == 3 || (i == 0 && previewing);
			addDrawableChild(CwUi.Button.blue(x, 4, w[i], 17, names[i], i == 4 ? CwUi.Icon.NONE : CwUi.Icon.NONE, primary, b -> topAction(index)));
			x += w[i] + 2;
		}

		// Left panel: bones / library switch.
		addDrawableChild(CwUi.Button.blue(M + 3, 28, leftW / 2 - 4, 13, "Кости", CwUi.Icon.NONE, !libraryMode, b -> { libraryMode = false; init(); }));
		addDrawableChild(CwUi.Button.blue(M + leftW / 2 + 1, 28, leftW / 2 - 4, 13, compact ? "Цикл." : "Библиотека", CwUi.Icon.NONE, libraryMode, b -> { libraryMode = true; init(); }));
		copyButton = CwUi.Button.blue(M + 3, height - M - 17, leftW / 2 - 4, 14, "Копия", CwUi.Icon.NONE, false, b -> cloneLibrary());
		deleteButton = CwUi.Button.blue(M + leftW / 2 + 1, height - M - 17, leftW / 2 - 4, 14, compact ? "Удал." : "Удалить", CwUi.Icon.NONE, false, b -> deleteLibrary());
		copyButton.active = deleteButton.active = selectedLib >= 0;
		copyButton.visible = deleteButton.visible = libraryMode;
		addDrawableChild(copyButton);
		addDrawableChild(deleteButton);

		// Right column.
		int cw = rightW - 12;
		column.begin(rightX + 6, 40, cw, height - M - 4 - 40);
		ButtonWidget[] axes = new ButtonWidget[3];
		for (int i = 0; i < 3; i++) {
			final int a = i;
			axes[i] = CwUi.Button.blue(rightX + 6 + i * (cw / 3), 0, cw / 3 - 2, 16, "XYZ".substring(i, i + 1), CwUi.Icon.NONE, axis == i, b -> { axis = a; init(); });
			addDrawableChild(axes[i]);
		}
		column.add("Ось вращения (тяните мышью по модели)", 16, axes);
		for (int i = 0; i < 3; i++) {
			final int a = i;
			var minus = CwUi.Button.blue(rightX + 6, 0, 24, 16, "−5", CwUi.Icon.NONE, false, b -> nudge(a, -5));
			rotFields[i] = new TextFieldWidget(textRenderer, rightX + 32, 0, cw - 58, 16, Text.empty());
			rotFields[i].setMaxLength(8);
			rotFields[i].setChangedListener(v -> { if (!syncing) setRot(a, parse(v)); });
			var plus = CwUi.Button.blue(rightX + 6 + cw - 24, 0, 24, 16, "+5", CwUi.Icon.NONE, false, b -> nudge(a, 5));
			addDrawableChild(minus);
			addDrawableChild(rotFields[i]);
			addDrawableChild(plus);
			column.add("Угол " + "XYZ".charAt(i) + " (°)", 16, minus, rotFields[i], plus);
		}
		var reset = CwUi.Button.blue(rightX + 6, 0, cw, 16, "Сбросить часть", CwUi.Icon.NONE, false, b -> { frame().bones.remove(bone); syncFields(); });
		addDrawableChild(reset);
		column.add(null, 16, reset);
		durationField = new TextFieldWidget(textRenderer, rightX + 6, 0, 50, 16, Text.empty());
		durationField.setMaxLength(6);
		durationField.setChangedListener(v -> { if (!syncing) { try { frame().durationMs = Math.max(50, Math.min(60000, Integer.parseInt(v.trim()))); } catch (NumberFormatException ignored) { } } });
		var easing = CwUi.Button.blue(rightX + 58, 0, cw - 52, 16, easingName(frame().easing), CwUi.Icon.NONE, false, b -> {
			var list = CwAnimation.EASINGS;
			frame().easing = list.get((list.indexOf(frame().easing) + 1) % list.size());
			init();
		});
		addDrawableChild(durationField);
		addDrawableChild(easing);
		column.add("Переход, мс • плавность", 16, durationField, easing);
		var add = CwUi.Button.blue(rightX + 6, 0, cw, 18, "Добавить позу / кадр", CwUi.Icon.PLUS, true, b -> addFrame());
		addDrawableChild(add);
		column.add(null, 18, add);
		int third = cw / 3;
		var left = CwUi.Button.blue(rightX + 6, 0, third - 2, 16, "←", CwUi.Icon.NONE, false, b -> moveFrame(-1));
		var right = CwUi.Button.blue(rightX + 6 + third, 0, third - 2, 16, "→", CwUi.Icon.NONE, false, b -> moveFrame(1));
		var delete = CwUi.Button.blue(rightX + 6 + 2 * third, 0, third - 2, 16, "Удал.", CwUi.Icon.NONE, false, b -> deleteFrame());
		addDrawableChild(left);
		addDrawableChild(right);
		addDrawableChild(delete);
		column.add("Порядок кадра", 16, left, right, delete);
		nameField = new TextFieldWidget(textRenderer, rightX + 6, 0, cw, 16, Text.empty());
		nameField.setMaxLength(48);
		nameField.setText(cycle.name);
		nameField.setChangedListener(v -> { if (!syncing) cycle.name = v.isBlank() ? "Новый цикл" : v; });
		addDrawableChild(nameField);
		column.add("Имя цикла", 16, nameField);
		var save = CwUi.Button.blue(rightX + 6, 0, cw, 16, "Сохранить в библиотеку", CwUi.Icon.NONE, false, b -> saveToLibrary());
		addDrawableChild(save);
		column.add(null, 16, save);
		String npc = npcName();
		var model = CwUi.Button.blue(rightX + 6, 0, cw, 16, npc == null ? "мой игрок" : npc, CwUi.Icon.NONE, false, b -> { npcIndex = npcIndex + 1 >= npcs().size() ? -1 : npcIndex + 1; init(); });
		addDrawableChild(model);
		column.add("Модель в окне (клик — сменить)", 16, model);
		var bind = CwUi.Button.blue(rightX + 6, 0, cw / 2 - 2, 16, "Привязать", CwUi.Icon.NONE, true, b -> bind(false));
		var unbind = CwUi.Button.blue(rightX + 6 + cw / 2, 0, cw / 2 - 2, 16, "Снять", CwUi.Icon.NONE, false, b -> bind(true));
		bind.active = unbind.active = npc != null;
		addDrawableChild(bind);
		addDrawableChild(unbind);
		column.add("Анимация на NPC", 16, bind, unbind);
		refreshLibrary();
		syncFields();
		column.layout();
	}

	private void topAction(int i) {
		switch (i) {
			case 0 -> startPreview();
			case 1 -> { if (previewing) { paused = !paused; init(); } }
			case 2 -> { cycle.loop = !cycle.loop; init(); }
			case 3 -> confirm();
			default -> close();
		}
	}

	private static String easingName(String e) {
		return switch (e) { case "LINEAR" -> "линейная"; case "EASE_IN" -> "разгон"; case "EASE_OUT" -> "торможение"; case "STEP" -> "ступень"; default -> "плавная"; };
	}

	private static float parse(String v) {
		try { return Math.max(-CwAnimation.LIMIT, Math.min(CwAnimation.LIMIT, Float.parseFloat(v.replace(',', '.').trim()))); } catch (Exception e) { return 0f; }
	}

	private static String fmt(float v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

	private void syncFields() {
		if (rotFields[0] == null) return;
		syncing = true;
		Rot r = frame().bones.get(bone);
		rotFields[0].setText(fmt(r == null ? 0 : r.x));
		rotFields[1].setText(fmt(r == null ? 0 : r.y));
		rotFields[2].setText(fmt(r == null ? 0 : r.z));
		durationField.setText(Integer.toString(frame().durationMs));
		syncing = false;
	}

	private void setRot(int a, float value) {
		Rot r = rot(bone);
		value = Math.max(-CwAnimation.LIMIT, Math.min(CwAnimation.LIMIT, value));
		if (a == 0) r.x = value; else if (a == 1) r.y = value; else r.z = value;
	}

	private void nudge(int a, float delta) {
		Rot r = rot(bone);
		setRot(a, (a == 0 ? r.x : a == 1 ? r.y : r.z) + delta);
		syncFields();
	}

	// ------------------------------------------------------------------ frames, preview

	private void addFrame() {
		if (cycle.frames.size() >= CwAnimation.MAX_FRAMES) { status = "Максимум кадров: " + CwAnimation.MAX_FRAMES; return; }
		cycle.frames.add(frameIndex + 1, frame().copy());
		frameIndex++;
		status = "Поза " + (frameIndex + 1) + ": измените части этого кадра";
		init();
	}

	private void deleteFrame() {
		if (cycle.frames.size() <= 1) { status = "Нужен хотя бы один кадр"; return; }
		cycle.frames.remove(frameIndex);
		frameIndex = Math.max(0, frameIndex - 1);
		init();
	}

	private void moveFrame(int d) {
		int to = frameIndex + d;
		if (to < 0 || to >= cycle.frames.size()) return;
		Frame f = cycle.frames.remove(frameIndex);
		cycle.frames.add(to, f);
		frameIndex = to;
	}

	private void startPreview() {
		previewing = true;
		paused = false;
		previewT = 0;
		lastNanos = System.nanoTime();
		init();
	}

	private void confirm() {
		cycle.name = nameField.getText().isBlank() ? "Новый цикл" : nameField.getText();
		CwAnimation.sanitize(cycle);
		String json = CwAnimation.toJson(cycle);
		writeLibrary(cycle.name, json);
		if (lockedNpc != null) bind(false);
		done.accept(json);
		if (client != null) client.setScreen(parent);
	}

	private void bind(boolean clear) {
		String npc = npcName();
		if (npc == null) return;
		CwAnimation.sanitize(cycle);
		var buf = PacketByteBufs.create();
		buf.writeString(npc, 32);
		buf.writeString(clear ? "" : CwAnimation.toJson(cycle), CwAnimation.MAX_JSON);
		ClientPlayNetworking.send(ModPackets.CW_ANIM_BIND, buf);
		status = clear ? "Запрос на снятие анимации отправлен" : "«" + cycle.name + "» → " + npc;
	}

	// ------------------------------------------------------------------ library on disk

	private static Path libraryDir() { return FabricLoader.getInstance().getConfigDir().resolve("admincontrol").resolve("animations"); }
	private static String safe(String name) { return name.replaceAll("[^A-Za-z0-9А-Яа-яЁё_ -]", "_").trim(); }

	private void refreshLibrary() {
		library = new ArrayList<>();
		try (Stream<Path> s = Files.isDirectory(libraryDir()) ? Files.list(libraryDir()) : Stream.<Path>empty()) {
			s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> { String n = p.getFileName().toString(); library.add(n.substring(0, n.length() - 5)); });
		} catch (IOException ignored) {
		}
	}

	private void writeLibrary(String name, String json) {
		try {
			Files.createDirectories(libraryDir());
			Files.writeString(libraryDir().resolve(safe(name) + ".json"), json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			status = "Не удалось записать в библиотеку";
		}
	}

	private void saveToLibrary() {
		cycle.name = nameField.getText().isBlank() ? "Новый цикл" : nameField.getText();
		CwAnimation.sanitize(cycle);
		writeLibrary(cycle.name, CwAnimation.toJson(cycle));
		status = "Сохранено в библиотеке: " + cycle.name;
		refreshLibrary();
	}

	private void openLibrary(String name) {
		try {
			cycle = CwAnimation.parse(Files.readString(libraryDir().resolve(safe(name) + ".json"), StandardCharsets.UTF_8));
			if (cycle.frames.isEmpty()) cycle.frames.add(new Frame());
			frameIndex = 0;
			previewing = false;
			libraryMode = false;
			status = "Открыт цикл: " + cycle.name;
			init();
		} catch (IOException e) {
			status = "Не удалось открыть цикл";
		}
	}

	private void cloneLibrary() {
		if (selectedLib < 0 || selectedLib >= library.size()) return;
		String src = library.get(selectedLib);
		try {
			Cycle c = CwAnimation.parse(Files.readString(libraryDir().resolve(src + ".json"), StandardCharsets.UTF_8));
			String name = src + " (копия)";
			c.name = name.length() > 48 ? name.substring(0, 48) : name;
			writeLibrary(c.name, CwAnimation.toJson(c));
			status = "Создана копия: " + c.name;
			refreshLibrary();
		} catch (IOException e) {
			status = "Не удалось скопировать цикл";
		}
	}

	private void deleteLibrary() {
		if (selectedLib < 0 || selectedLib >= library.size()) return;
		try {
			Files.deleteIfExists(libraryDir().resolve(library.get(selectedLib) + ".json"));
			status = "Цикл удалён из библиотеки";
		} catch (IOException e) {
			status = "Не удалось удалить цикл";
		}
		selectedLib = -1;
		init();
	}

	// ------------------------------------------------------------------ input

	private float viewSize() { return Math.max(24f, (vy1 - vy0) * 0.40f * zoom); }
	private int feetY() { return vy0 + (int) (((vy1 - vy0) + 1.875f * viewSize()) / 2f); }
	private int centreX() { return (vx0 + vx1) / 2; }

	private String hitBone(double mx, double my) {
		float cos = (float) Math.cos(Math.toRadians(camYaw));
		if (Math.abs(cos) < 0.25f) return null;
		float s = viewSize();
		float x = (float) (mx - centreX()) / s / Math.abs(cos);
		if (cos < 0) x = -x;
		float y = (feetY() - (float) my) / s;
		for (var e : BOXES.entrySet()) {
			float[] b = e.getValue();
			if (x >= b[0] && x <= b[1] && y >= b[2] && y <= b[3]) return e.getKey();
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) return true;
		if (!libraryMode && mx >= M && mx < M + leftW && my >= leftTop && my < leftTop + BONES.size() * boneRow) {
			bone = BONES.get((int) ((my - leftTop) / boneRow));
			syncFields();
			return true;
		}
		if (libraryMode && mx >= M && mx < M + leftW && my >= leftTop && my < height - M - 22) {
			int i = libScroll + (int) ((my - leftTop) / 12);
			if (i < library.size()) {
				if (selectedLib == i) openLibrary(library.get(i)); else { selectedLib = i; init(); }
			}
			return true;
		}
		if (my >= timelineY + 14 && my < timelineY + timelineH - 3 && mx >= vx0 && mx < vx1) {
			int i = (int) ((mx - vx0) / chipW);
			if (i >= 0 && i < cycle.frames.size()) { frameIndex = i; previewing = false; init(); }
			return true;
		}
		if (mx >= vx0 && mx < vx1 && my >= vy0 && my < vy1) {
			if (button == 1) { dragCamera = true; return true; }
			if (button == 0) {
				String hit = hitBone(mx, my);
				if (hit != null) { bone = hit; syncFields(); }
				dragRotate = true;
				previewing = false;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragCamera) { camYaw += (float) dx * 0.8f; camPitch = Math.max(-40f, Math.min(40f, camPitch + (float) dy * 0.5f)); return true; }
		if (dragRotate) {
			Rot r = rot(bone);
			float cur = axis == 0 ? r.x : axis == 1 ? r.y : r.z;
			setRot(axis, cur + (float) dx * 0.8f);
			syncFields();
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) { dragRotate = false; dragCamera = false; return super.mouseReleased(mx, my, button); }

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (column.mouseScrolled(mx, my, amount)) return true;
		if (mx >= vx0 && mx < vx1 && my >= vy0 && my < vy1) { zoom = Math.max(0.5f, Math.min(2.2f, zoom + (float) amount * 0.1f)); return true; }
		if (libraryMode && mx < M + leftW) { libScroll = Math.max(0, Math.min(Math.max(0, library.size() - 1), libScroll - (int) Math.signum(amount))); return true; }
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (getFocused() instanceof TextFieldWidget) return super.keyPressed(key, scan, mods);
		if (key == GLFW.GLFW_KEY_SPACE) { if (previewing) { paused = !paused; init(); } else startPreview(); return true; }
		return super.keyPressed(key, scan, mods);
	}

	@Override public void close() { if (client != null) client.setScreen(parent); }
	@Override public boolean shouldPause() { return false; }

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		long now = System.nanoTime();
		if (previewing && !paused) previewT += (now - lastNanos) / 1_000_000L;
		lastNanos = now;
		if (previewing && !cycle.loop && previewT > cycle.totalMs() + 200) { previewing = false; init(); }

		c.fill(0, 0, width, height, 0xF2060A10);
		CwUi.tabletPanel(c, M, 2, width - 2 * M, 21, false);
		String title = lockedNpc != null ? "Анимации: " + lockedNpc : "Анимации NPC";
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(title, Math.max(30, width / 4)), M + 6, 8, CwUi.GOLD_FILL);

		// Left panel.
		CwUi.tabletPanel(c, M, 26, leftW, height - 26 - M, false);
		if (!libraryMode) {
			for (int i = 0; i < BONES.size(); i++) {
				int y = leftTop + i * boneRow;
				String b = BONES.get(i);
				if (b.equals(bone)) c.fill(M + 3, y - 1, M + leftW - 3, y + boneRow - 2, 0xAA1A3E70);
				Rot r = frame().bones.get(b);
				boolean used = r != null && !r.zero();
				int ix = M + 6 + DEPTH[i] * (compact ? 6 : 10);
				c.fill(ix, y + 2, ix + 3, y + 5, used ? 0xFF65D684 : 0xFF3A4658);
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(b, M + leftW - ix - 10), ix + 6, y + 1, b.equals(bone) ? CwUi.TEXT : CwUi.TEXT_DIM);
			}
		} else {
			int rows = Math.max(0, (height - M - 22 - leftTop) / 12);
			for (int i = 0; i < rows && libScroll + i < library.size(); i++) {
				int y = leftTop + i * 12, idx = libScroll + i;
				if (idx == selectedLib) c.fill(M + 3, y - 1, M + leftW - 3, y + 11, 0xAA1A3E70);
				c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(library.get(idx), leftW - 12), M + 6, y + 1, idx == selectedLib ? CwUi.TEXT : CwUi.TEXT_DIM);
			}
			if (library.isEmpty()) c.drawTextWithShadow(textRenderer, "Пока пусто", M + 6, leftTop + 1, CwUi.TEXT_DIM);
		}

		// Viewport.
		CwUi.tabletPanel(c, vx0, vy0, vx1 - vx0, vy1 - vy0, true);
		renderModel(c);
		String hint = status.isEmpty() ? "ЛКМ: часть + тянуть • ПКМ: камера • колесо: масштаб" : status;
		c.fill(vx0 + 2, vy1 - 12, vx1 - 2, vy1 - 2, 0xAA050A12);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(hint, vx1 - vx0 - 10), vx0 + 5, vy1 - 11, status.isEmpty() ? 0xFF93A6B8 : 0xFF8FFFA0);

		// Right panel.
		CwUi.tabletPanel(c, rightX, 26, rightW, height - 26 - M, false);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Часть: " + bone + " • кадр " + (frameIndex + 1) + "/" + cycle.frames.size(), rightW - 10), rightX + 6, 29, CwUi.GOLD_FILL);

		// Timeline.
		CwUi.tabletPanel(c, vx0, timelineY, vx1 - vx0, timelineH, false);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Кадры • " + cycle.totalMs() + " мс" + (cycle.loop ? " • повтор" : ""), vx1 - vx0 - 8), vx0 + 5, timelineY + 3, CwUi.GOLD_FILL);
		for (int i = 0; i < cycle.frames.size(); i++) {
			int x = vx0 + i * chipW;
			if (x + chipW - 4 > vx1) break;
			boolean sel = i == frameIndex;
			int top = timelineY + 14, bottom = timelineY + timelineH - 3;
			c.fill(x + 3, top, x + chipW - 3, bottom, sel ? 0xFF1A3E70 : 0xCC0E1522);
			CwUi.border(c, x + 3, top, chipW - 6, bottom - top, sel ? CwUi.BLUE : CwUi.BLUE_DIM);
			c.drawCenteredTextWithShadow(textRenderer, Integer.toString(i + 1), x + chipW / 2, top + 3, CwUi.TEXT);
			if (bottom - top >= 24) c.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(cycle.frames.get(i).durationMs + "мс", chipW - 6), x + chipW / 2, top + 14, CwUi.TEXT_DIM);
		}
		if (previewing && cycle.totalMs() > 0) {
			long t = cycle.loop ? previewT % cycle.totalMs() : Math.min(previewT, cycle.totalMs());
			int px = vx0 + 3 + (int) ((vx1 - vx0 - 6) * t / cycle.totalMs());
			c.fill(px, timelineY + 12, px + 1, timelineY + timelineH - 2, 0xFFFF8FDA);
		}
		super.render(c, mx, my, delta);
		column.render(c, textRenderer);
	}

	private void renderModel(DrawContext c) {
		LivingEntity entity = modelEntity();
		if (entity == null) return;
		Map<String, Rot> pose = previewing ? CwAnimation.pose(cycle, previewT) : frame().bones;
		float s = viewSize();
		int cx = centreX(), fy = feetY();
		float oBody = entity.bodyYaw, oPrevBody = entity.prevBodyYaw, oYaw = entity.getYaw(), oPrevYaw = entity.prevYaw;
		float oPitch = entity.getPitch(), oPrevPitch = entity.prevPitch, oHead = entity.headYaw, oPrevHead = entity.prevHeadYaw;
		entity.bodyYaw = entity.prevBodyYaw = 180f + camYaw;
		entity.setYaw(180f + camYaw);
		entity.prevYaw = 180f + camYaw;
		entity.headYaw = entity.prevHeadYaw = 180f + camYaw;
		entity.setPitch(0f);
		entity.prevPitch = 0f;
		Quaternionf q = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf q2 = new Quaternionf().rotateX((float) Math.toRadians(camPitch));
		q.mul(q2);
		c.enableScissor(vx0 + 1, vy0 + 1, vx1 - 1, vy1 - 1);
		CwNpcAnimations.beginPreview(entity, pose);
		try {
			InventoryScreen.drawEntity(c, cx, fy, (int) s, q, q2, entity);
		} catch (Exception ignored) {
		} finally {
			CwNpcAnimations.endPreview();
		}
		String base = switch (bone) { case "hat" -> "head"; case "jacket" -> "body"; case "right_sleeve" -> "right_arm"; case "left_sleeve" -> "left_arm"; case "right_pants" -> "right_leg"; case "left_pants" -> "left_leg"; default -> bone; };
		float[] b = BOXES.get(base);
		if (b != null) {
			float cos = (float) Math.cos(Math.toRadians(camYaw));
			float sign = cos < 0 ? -1 : 1;
			int x0 = cx + (int) (sign * b[0] * s * Math.abs(cos)), x1 = cx + (int) (sign * b[1] * s * Math.abs(cos));
			CwUi.border(c, Math.min(x0, x1), fy - (int) (b[3] * s), Math.max(2, Math.abs(x1 - x0)), (int) ((b[3] - b[2]) * s), 0xCC3C8DFF);
		}
		c.disableScissor();
		entity.bodyYaw = oBody; entity.prevBodyYaw = oPrevBody; entity.setYaw(oYaw); entity.prevYaw = oPrevYaw;
		entity.setPitch(oPitch); entity.prevPitch = oPrevPitch; entity.headYaw = oHead; entity.prevHeadYaw = oPrevHead;
	}
}
