package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.client.CwItemPickerScreen;
import ru.admincontrol.lumira.recipe.RecipeEdits;

/**
 * Окно одного крафта: тип (фигурный / бесформенный / рунный верстак / печи), сетка 3×3, результат и количество.
 * ЛКМ по ячейке — выбрать предмет, ПКМ — очистить; тег (#minecraft:planks) вводится в поле под сеткой.
 */
@Environment(EnvType.CLIENT)
public final class RecipeEditScreen extends Screen {
	private static final int CELL = 26;
	private static final int[] KINDS = {RecipeEdits.SHAPED, RecipeEdits.SHAPELESS, RecipeEdits.RUNE, RecipeEdits.SMELTING, RecipeEdits.BLASTING, RecipeEdits.SMOKING};

	private final Screen parent;
	private final RecipeEdits.Entry entry;
	private final boolean isNew;
	private int selected;
	private TextFieldWidget idField, tagField, xpField, timeField;

	public RecipeEditScreen(Screen parent, RecipeEdits.Entry source, boolean isNew) {
		super(Text.literal(isNew ? "Новый крафт" : "Изменить крафт"));
		this.parent = parent;
		this.isNew = isNew;
		RecipeEdits.Entry e = new RecipeEdits.Entry();
		e.id = source.id;
		e.kind = source.kind;
		System.arraycopy(source.slots, 0, e.slots, 0, 9);
		e.output = source.output;
		e.count = Math.max(1, source.count);
		e.xp = source.xp;
		e.time = source.time <= 0 ? 200 : source.time;
		e.edited = source.edited;
		e.custom = source.custom;
		this.entry = e;
	}

	private boolean cooking() {
		return entry.kind == RecipeEdits.SMELTING || entry.kind == RecipeEdits.BLASTING || entry.kind == RecipeEdits.SMOKING;
	}

	private boolean special() {
		return entry.kind == RecipeEdits.OTHER;
	}

	private int gx() {
		return this.width / 2 - 120;
	}

	private int gy() {
		return 76;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		idField = new TextFieldWidget(this.textRenderer, cx - 140, 28, 280, 18, Text.literal("id"));
		idField.setMaxLength(100);
		idField.setText(entry.id);
		idField.setEditable(isNew || entry.custom);
		this.addDrawableChild(idField);
		if (!special()) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Тип: " + RecipeEditorScreen.kindName(entry.kind)), b -> {
				int i = 0;
				for (int k = 0; k < KINDS.length; k++) {
					if (KINDS[k] == entry.kind) {
						i = k;
					}
				}
				entry.kind = KINDS[(i + 1) % KINDS.length];
				entry.id = idField.getText();
				clearAndInit();
			}).dimensions(cx - 140, 50, 280, 18).build());
			tagField = new TextFieldWidget(this.textRenderer, gx(), gy() + 3 * CELL + 6, 150, 18, Text.literal("Тег"));
			tagField.setPlaceholder(Text.literal("#minecraft:planks"));
			tagField.setMaxLength(100);
			this.addDrawableChild(tagField);
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Тег → ячейка"), b -> {
				String t = tagField.getText().trim();
				if (!t.isEmpty()) {
					entry.slots[selected] = t.startsWith("#") ? t : "#" + t;
				}
			}).dimensions(gx() + 154, gy() + 3 * CELL + 6, 80, 18).build());
			int ox = gx() + 3 * CELL + 36;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
				entry.count = Math.max(1, entry.count - 1);
				clearAndInit();
			}).dimensions(ox - 8, gy() + 3 * CELL - 30, 20, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("×" + entry.count), b -> {
			}).dimensions(ox + 14, gy() + 3 * CELL - 30, 36, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
				entry.count = Math.min(64, entry.count + 1);
				clearAndInit();
			}).dimensions(ox + 52, gy() + 3 * CELL - 30, 20, 18).build());
			if (cooking()) {
				xpField = new TextFieldWidget(this.textRenderer, ox - 8, gy() + 4, 80, 18, Text.literal("xp"));
				xpField.setText(String.valueOf(entry.xp));
				xpField.setTextPredicate(v -> v.matches("[0-9.]{0,6}"));
				this.addDrawableChild(xpField);
				timeField = new TextFieldWidget(this.textRenderer, ox - 8, gy() + 34, 80, 18, Text.literal("tick"));
				timeField.setText(String.valueOf(entry.time));
				timeField.setTextPredicate(v -> v.matches("[0-9]{0,5}"));
				this.addDrawableChild(timeField);
			}
		}
		int by = this.height - 30;
		if (!special()) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> save()).dimensions(cx - 150, by, 90, 20).build());
		}
		if (!isNew) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить"), b -> {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeString(entry.id, 128);
				ClientPlayNetworking.send(RecipeEdits.DELETE, buf);
				close();
			}).dimensions(cx - 56, by, 70, 20).build());
			if (entry.edited && !entry.custom) {
				this.addDrawableChild(ButtonWidget.builder(Text.literal("Исходный"), b -> {
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeVarInt(0);
					buf.writeString(entry.id, 128);
					ClientPlayNetworking.send(RecipeEdits.RESTORE, buf);
					close();
				}).dimensions(cx + 18, by, 70, 20).build());
			}
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close()).dimensions(cx + 92, by, 60, 20).build());
	}

	private void save() {
		entry.id = idField.getText().trim();
		if (!entry.id.contains(":")) {
			entry.id = "admin_control:" + entry.id;
		}
		if (cooking()) {
			try {
				entry.xp = Float.parseFloat(xpField.getText());
			} catch (NumberFormatException ignored) {
				entry.xp = 0f;
			}
			try {
				entry.time = Integer.parseInt(timeField.getText());
			} catch (NumberFormatException ignored) {
				entry.time = 200;
			}
		}
		PacketByteBuf buf = PacketByteBufs.create();
		entry.write(buf);
		ClientPlayNetworking.send(RecipeEdits.SAVE, buf);
		close();
	}

	private int slotX(int i) {
		return gx() + (i % 3) * CELL;
	}

	private int slotY(int i) {
		return gy() + (i / 3) * CELL;
	}

	private int outX() {
		return gx() + 3 * CELL + 52;
	}

	private int outY() {
		return gy() + CELL + 2;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		if (special()) {
			return false;
		}
		int slots = cooking() ? 1 : 9;
		for (int i = 0; i < slots; i++) {
			int x = cooking() ? gx() + CELL : slotX(i), y = cooking() ? gy() + CELL : slotY(i);
			if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2) {
				selected = i;
				if (button == 1) {
					entry.slots[i] = null;
				} else {
					entry.id = idField.getText();
					this.client.setScreen(new CwItemPickerScreen(this, (Identifier id) -> entry.slots[selected] = id.toString()));
				}
				return true;
			}
		}
		int ox = cooking() ? gx() + 3 * CELL + 36 : outX(), oy = cooking() ? gy() + CELL + 2 : outY();
		if (!cooking() && mx >= outX() && mx < outX() + CELL - 2 && my >= outY() && my < outY() + CELL - 2
				|| cooking() && mx >= gx() + 3 * CELL + 80 && mx < gx() + 3 * CELL + 80 + CELL - 2 && my >= oy && my < oy + CELL - 2) {
			entry.id = idField.getText();
			this.client.setScreen(new CwItemPickerScreen(this, (Identifier id) -> entry.output = id.toString()));
			return true;
		}
		return false;
	}

	private void drawSlot(DrawContext c, int x, int y, String id) {
		c.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF2B3138);
		c.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0xFF4A535C);
		ItemStack stack = RecipeEditorScreen.stackOf(id);
		if (!stack.isEmpty()) {
			c.drawItem(stack, x + 4, y + 4);
		}
		if (id != null && id.startsWith("#")) {
			c.drawText(this.textRenderer, "#", x + 2, y + 2, 0xFFFFE27A, true);
		}
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		renderBackground(c);
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFD27A);
		if (special()) {
			c.drawCenteredTextWithShadow(this.textRenderer, "Особый рецепт: его схему менять нельзя, но можно удалить", this.width / 2, 90, 0xFF9FB3BD);
			c.drawCenteredTextWithShadow(this.textRenderer, "и создать вместо него обычный крафт того же предмета.", this.width / 2, 104, 0xFF9FB3BD);
			super.render(c, mx, my, delta);
			return;
		}
		if (cooking()) {
			drawSlot(c, gx() + CELL, gy() + CELL, entry.slots[0]);
			c.drawText(this.textRenderer, "→", gx() + 3 * CELL + 6, gy() + CELL + 8, 0xFFFFFFFF, true);
			drawSlot(c, gx() + 3 * CELL + 80, gy() + CELL + 2, entry.output);
			c.drawText(this.textRenderer, "опыт", gx() + 3 * CELL + 36 - 44, gy() + 8, 0xFF9FB3BD, false);
			c.drawText(this.textRenderer, "тики", gx() + 3 * CELL + 36 - 44, gy() + 38, 0xFF9FB3BD, false);
		} else {
			for (int i = 0; i < 9; i++) {
				drawSlot(c, slotX(i), slotY(i), entry.slots[i]);
				if (i == selected) {
					c.drawBorder(slotX(i) - 1, slotY(i) - 1, CELL, CELL, 0xFFFFD27A);
				}
			}
			c.drawText(this.textRenderer, "→", gx() + 3 * CELL + 22, outY() + 9, 0xFFFFFFFF, true);
			drawSlot(c, outX(), outY(), entry.output);
		}
		String hint = entry.kind == RecipeEdits.RUNE ? "Собирается только в рунном верстаке." : cooking() ? "Печь/плавильня/коптильня." : "Обычный верстак (и 2×2 у игрока, если схема влезает).";
		c.drawCenteredTextWithShadow(this.textRenderer, hint, this.width / 2, this.height - 46, 0xFF9FB3BD);
		c.drawCenteredTextWithShadow(this.textRenderer, "ЛКМ — выбрать предмет, ПКМ — очистить ячейку", this.width / 2, this.height - 58, 0xFF9FB3BD);
		super.render(c, mx, my, delta);
		for (int i = 0; i < (cooking() ? 1 : 9); i++) {
			int x = cooking() ? gx() + CELL : slotX(i), y = cooking() ? gy() + CELL : slotY(i);
			if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2 && entry.slots[i] != null) {
				c.drawTooltip(this.textRenderer, Text.literal(entry.slots[i]), mx, my);
			}
		}
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
		RecipeEditorScreen.requestSync();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
