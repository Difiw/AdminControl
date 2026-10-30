package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.lumira.recipe.RecipeEdits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Редактор крафтов (/dserver → «Настройки крафтов»): список всех рецептов мода с поиском; крафт можно открыть,
 * изменить, удалить, вернуть к исходному или создать новый — сколько угодно рецептов на один предмет.
 */
@Environment(EnvType.CLIENT)
public final class RecipeEditorScreen extends Screen {
	static final List<RecipeEdits.Entry> ENTRIES = new ArrayList<>();
	static final List<String> REMOVED = new ArrayList<>();
	static boolean loaded;
	/** Поиск запоминается между открытиями (например, «coin»). */
	static String lastFilter = "";

	private static final int ROW_H = 22, ROWS = 9;
	private final Screen parent;
	private TextFieldWidget search;
	private List<RecipeEdits.Entry> view = List.of();
	private int scroll;
	private boolean showRemoved;
	private boolean confirmReset;

	public RecipeEditorScreen(Screen parent, String filter) {
		super(Text.literal("Редактор крафтов"));
		this.parent = parent;
		if (filter != null) {
			lastFilter = filter;
		}
	}

	public static void requestSync() {
		ClientPlayNetworking.send(RecipeEdits.LIST_REQ, PacketByteBufs.create());
	}

	public static void onList(PacketByteBuf buf) {
		int n = buf.readVarInt();
		List<RecipeEdits.Entry> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			list.add(RecipeEdits.Entry.read(buf));
		}
		int m = buf.readVarInt();
		List<String> removed = new ArrayList<>();
		for (int i = 0; i < m; i++) {
			removed.add(buf.readString(128));
		}
		MinecraftClient.getInstance().execute(() -> {
			ENTRIES.clear();
			ENTRIES.addAll(list);
			REMOVED.clear();
			REMOVED.addAll(removed);
			loaded = true;
			Screen s = MinecraftClient.getInstance().currentScreen;
			if (s instanceof RecipeEditorScreen ed) {
				ed.refresh();
			}
		});
	}

	static String kindName(int kind) {
		return switch (kind) {
			case RecipeEdits.SHAPED -> "Фигурный";
			case RecipeEdits.SHAPELESS -> "Бесформенный";
			case RecipeEdits.RUNE -> "Рунный верстак";
			case RecipeEdits.SMELTING -> "Печь";
			case RecipeEdits.BLASTING -> "Плавильная печь";
			case RecipeEdits.SMOKING -> "Коптильня";
			default -> "Особый";
		};
	}

	static ItemStack stackOf(String id) {
		if (id == null || id.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (id.startsWith("#")) {
			Identifier tagId = Identifier.tryParse(id.substring(1));
			if (tagId != null) {
				for (var entry : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, tagId))) {
					return new ItemStack(entry.value());
				}
			}
			return ItemStack.EMPTY;
		}
		Identifier ident = Identifier.tryParse(id);
		if (ident != null && Registries.ITEM.containsId(ident)) {
			return new ItemStack(Registries.ITEM.get(ident));
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 170;
		search = new TextFieldWidget(this.textRenderer, left, 30, 230, 18, Text.literal("Поиск"));
		search.setPlaceholder(Text.literal("Поиск: id или название…"));
		search.setText(lastFilter);
		search.setChangedListener(v -> {
			lastFilter = v;
			scroll = 0;
			refresh();
		});
		this.addDrawableChild(search);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+ Новый крафт"), b -> {
			RecipeEdits.Entry e = new RecipeEdits.Entry();
			e.id = "admin_control:custom/new_" + (System.currentTimeMillis() % 100000);
			e.kind = RecipeEdits.SHAPED;
			e.custom = true;
			this.client.setScreen(new RecipeEditScreen(this, e, true));
		}).dimensions(left + 236, 30, 104, 18).build());
		int by = 58 + ROWS * ROW_H + 8;
		this.addDrawableChild(ButtonWidget.builder(Text.literal(showRemoved ? "Показать крафты" : "Удалённые (" + REMOVED.size() + ")"), b -> {
			showRemoved = !showRemoved;
			scroll = 0;
			clearAndInit();
		}).dimensions(left, by, 110, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(confirmReset ? "Точно сбросить все?" : "Сбросить все правки"), b -> {
			if (!confirmReset) {
				confirmReset = true;
				clearAndInit();
				return;
			}
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(1);
			buf.writeString("", 128);
			ClientPlayNetworking.send(RecipeEdits.RESTORE, buf);
			confirmReset = false;
			clearAndInit();
		}).dimensions(left + 116, by, 130, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close()).dimensions(left + 252, by, 88, 18).build());
		refresh();
	}

	private void refresh() {
		String q = lastFilter == null ? "" : lastFilter.trim().toLowerCase(Locale.ROOT);
		List<RecipeEdits.Entry> out = new ArrayList<>();
		if (showRemoved) {
			for (String id : REMOVED) {
				if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
					RecipeEdits.Entry e = new RecipeEdits.Entry();
					e.id = id;
					e.removed = true;
					out.add(e);
				}
			}
		} else {
			for (RecipeEdits.Entry e : ENTRIES) {
				if (q.isEmpty() || e.id.toLowerCase(Locale.ROOT).contains(q) || e.output.toLowerCase(Locale.ROOT).contains(q)
						|| stackOf(e.output).getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
					out.add(e);
				}
			}
		}
		view = out;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private int maxScroll() {
		return Math.max(0, view.size() - ROWS);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount)));
		return true;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		int left = this.width / 2 - 170;
		for (int i = 0; i < ROWS; i++) {
			int idx = scroll + i;
			if (idx >= view.size()) {
				break;
			}
			int y = 58 + i * ROW_H;
			if (my < y || my >= y + ROW_H - 2 || mx < left || mx >= left + 340) {
				continue;
			}
			RecipeEdits.Entry e = view.get(idx);
			boolean onDelete = mx >= left + 312;
			if (e.removed) {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeVarInt(0);
				buf.writeString(e.id, 128);
				ClientPlayNetworking.send(RecipeEdits.RESTORE, buf);
				return true;
			}
			if (onDelete) {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeString(e.id, 128);
				ClientPlayNetworking.send(RecipeEdits.DELETE, buf);
				return true;
			}
			this.client.setScreen(new RecipeEditScreen(this, e, false));
			return true;
		}
		return false;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		renderBackground(c);
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFD27A);
		int left = this.width / 2 - 170;
		c.fill(left - 4, 54, left + 344, 58 + ROWS * ROW_H + 2, 0xEE111722);
		if (!loaded) {
			c.drawCenteredTextWithShadow(this.textRenderer, "Загрузка рецептов с сервера…", this.width / 2, 100, 0xFF9FB3BD);
		}
		for (int i = 0; i < ROWS; i++) {
			int idx = scroll + i;
			if (idx >= view.size()) {
				break;
			}
			RecipeEdits.Entry e = view.get(idx);
			int y = 58 + i * ROW_H;
			boolean hover = mx >= left && mx < left + 340 && my >= y && my < y + ROW_H - 2;
			c.fill(left, y, left + 340, y + ROW_H - 2, hover ? 0xFF3A4658 : 0xFF263040);
			if (!e.removed) {
				ItemStack out = stackOf(e.output);
				if (!out.isEmpty()) {
					c.drawItem(out, left + 2, y + 1);
				}
				String name = out.isEmpty() ? e.output : out.getName().getString() + (e.count > 1 ? " ×" + e.count : "");
				c.drawText(this.textRenderer, this.textRenderer.trimToWidth(name, 190), left + 24, y + 2, 0xFFFFFFFF, false);
				String tag = kindName(e.kind) + (e.custom ? " • свой" : e.edited ? " • изменён" : "");
				c.drawText(this.textRenderer, this.textRenderer.trimToWidth(e.id + "  [" + tag + "]", 280), left + 24, y + 12, 0xFF8FA3B5, false);
				c.fill(left + 312, y, left + 340, y + ROW_H - 2, hover && mx >= left + 312 ? 0xFF8A2B2B : 0xFF5A2020);
				c.drawCenteredTextWithShadow(this.textRenderer, "✕", left + 326, y + 7, 0xFFFFD0D0);
			} else {
				c.drawText(this.textRenderer, e.id, left + 6, y + 2, 0xFFFFFFFF, false);
				c.drawText(this.textRenderer, "Удалён. Нажмите, чтобы вернуть исходный крафт.", left + 6, y + 12, 0xFF8FA3B5, false);
			}
		}
		String foot = "Найдено: " + view.size() + " • колёсико — прокрутка • ЛКМ — открыть, ✕ — удалить";
		c.drawCenteredTextWithShadow(this.textRenderer, foot, this.width / 2, 58 + ROWS * ROW_H + 30, 0xFF9FB3BD);
		super.render(c, mx, my, delta);
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
