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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.client.CwItemPickerScreen;
import ru.admincontrol.item.ItemEncryption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно «Настроить список зашифрованных предметов» (/dserver → настройки сюжета).
	 * В списке четыре независимых статуса: название, описание, картинка и разрешение использовать.
 * Добавить можно любой предмет, в том числе ванильный.
 */
@Environment(EnvType.CLIENT)
public final class EncryptedItemsScreen extends Screen {
	private static final int ROW = 42;
	private final Screen parent;
	private TextFieldWidget search;
	private String filter = "";
	private int scroll;

	public EncryptedItemsScreen(Screen parent) {
		super(Text.literal("Зашифрованные предметы"));
		this.parent = parent;
	}

	public static void requestSync() {
		if (!ClientPlayNetworking.canSend(ItemEncryption.ACTION)) {
			return;
		}
		send(ItemEncryption.ACT_REQUEST, "");
	}

	private static void send(int action, String id) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(action);
		buf.writeString(id, 128);
		ClientPlayNetworking.send(ItemEncryption.ACTION, buf);
	}

	/** Вызывается при получении нового состояния с сервера. */
	public static void onSync() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen instanceof EncryptedItemsScreen s) {
			s.clearAndInit();
		}
	}

	private static String onOff(boolean on) {
		return on ? "ВКЛ" : "ВЫКЛ";
	}

	private List<String> filtered() {
		List<String> out = new ArrayList<>();
		for (String id : ItemEncryption.allIds()) {
			Identifier ident = Identifier.tryParse(id);
			String name = ident != null && Registries.ITEM.containsId(ident) ? Registries.ITEM.get(ident).getName().getString() : id;
			if (filter.isEmpty() || id.toLowerCase(Locale.ROOT).contains(filter) || name.toLowerCase(Locale.ROOT).contains(filter)) {
				out.add(id);
			}
		}
		return out;
	}

	private int visibleRows() {
		return Math.max(3, (this.height - 164) / ROW);
	}

	private int panelWidth() {
		return Math.max(300, Math.min(480, this.width - 16));
	}

	private int panelLeft() {
		return (this.width - panelWidth()) / 2;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int left = panelLeft();
		int panel = panelWidth();
		int top = (panel - 12) / 4;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Назв./опис.: " + onOff(ItemEncryption.enabled())),
				b -> send(ItemEncryption.ACT_TOGGLE_NAMES, "")).dimensions(left, 26, top, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Картинки: " + onOff(ItemEncryption.iconsEnabled())),
				b -> send(ItemEncryption.ACT_TOGGLE_ICONS, "")).dimensions(left + top + 4, 26, top, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Блок крафта: " + onOff(ItemEncryption.recipesBlocked())),
				b -> send(ItemEncryption.ACT_TOGGLE_RECIPES, "")).dimensions(left + (top + 4) * 2, 26, top, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Использование: по списку"), b -> {})
				.dimensions(left + (top + 4) * 3, 26, panel - (top + 4) * 3, 20).build()).active = false;
		int half = (panel - 8) / 3;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+ Добавить предмет"),
				b -> this.client.setScreen(new CwItemPickerScreen(this, id -> send(ItemEncryption.ACT_ADD, id.toString()))))
				.dimensions(left, 50, half, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Список по умолчанию"),
				b -> send(ItemEncryption.ACT_RESET, "")).dimensions(left + half + 4, 50, half, 20).build());
		this.search = new TextFieldWidget(this.textRenderer, left + (half + 4) * 2, 50,
				panel - (half + 4) * 2, 20, Text.literal("Поиск"));
		this.search.setPlaceholder(Text.literal("Поиск…"));
		this.search.setText(filter);
		this.search.setChangedListener(text -> {
			filter = text.trim().toLowerCase(Locale.ROOT);
			scroll = 0;
			this.client.execute(this::clearAndInit);
		});
		this.addDrawableChild(this.search);
		List<String> ids = filtered();
		int rows = visibleRows();
		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, ids.size() - rows)));
		for (int i = 0; i < rows && this.scroll + i < ids.size(); i++) {
			String id = ids.get(this.scroll + i);
			int y = 90 + i * ROW;
			boolean nameOn = ItemEncryption.ids().contains(id);
			boolean iconOn = ItemEncryption.iconIds().contains(id);
			boolean descOn = ItemEncryption.descIds().contains(id);
			boolean useBlocked = ItemEncryption.useBlockedIds().contains(id);
			int unit = Math.max(42, (panel - 10) / 6);
			int buttonsY = y + 20;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Имя " + (nameOn ? "✔" : "✖")),
					b -> send(nameOn ? ItemEncryption.ACT_NAME_OFF : ItemEncryption.ACT_NAME_ON, id)).dimensions(left, buttonsY, unit, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Опис. " + (descOn ? "✔" : "✖")),
					b -> send(descOn ? ItemEncryption.ACT_DESC_OFF : ItemEncryption.ACT_DESC_ON, id)).dimensions(left + unit + 2, buttonsY, unit, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Иконка " + (iconOn ? "✔" : "✖")),
					b -> send(iconOn ? ItemEncryption.ACT_ICON_OFF : ItemEncryption.ACT_ICON_ON, id)).dimensions(left + (unit + 2) * 2, buttonsY, unit, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal(useBlocked ? "Исп.: НЕЛЬЗЯ" : "Исп.: МОЖНО"),
					b -> send(useBlocked ? ItemEncryption.ACT_USE_ALLOW : ItemEncryption.ACT_USE_BLOCK, id))
					.dimensions(left + (unit + 2) * 3, buttonsY, unit * 2 + 2, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("✖"), b -> send(ItemEncryption.ACT_REMOVE_ALL, id))
					.dimensions(left + (unit + 2) * 5, buttonsY, panel - (unit + 2) * 5, 18).build());
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close()).dimensions(cx - 50, this.height - 28, 100, 20).build());
		if (!filter.isEmpty()) {
			this.setFocused(this.search);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		this.scroll = Math.max(0, this.scroll - (int) Math.signum(amount));
		clearAndInit();
		return true;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		renderBackground(c);
		int cx = this.width / 2;
		int left = panelLeft();
		int panel = panelWidth();
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFD27A);
		List<String> ids = filtered();
		int rows = visibleRows();
		c.fill(left - 4, 74, left + panel + 4, 90 + rows * ROW, 0xB0101418);
		c.drawTextWithShadow(this.textRenderer, "Предмет и его отдельные разрешения", left + 4, 78, 0xFF9FB3BD);
		for (int i = 0; i < rows && this.scroll + i < ids.size(); i++) {
			String id = ids.get(this.scroll + i);
			int y = 90 + i * ROW;
			Identifier ident = Identifier.tryParse(id);
			if (ident != null && Registries.ITEM.containsId(ident)) {
				Item item = Registries.ITEM.get(ident);
				c.drawItem(new ItemStack(item), left, y);
				// администратору настоящее название и ID показываются открыто
				c.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(item.getName().getString(), panel - 24), left + 20, y + 1, 0xFFE8F4F2);
				c.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(id, panel - 24), left + 20, y + 11, 0xFF7E8A94);
			}
		}
		c.drawCenteredTextWithShadow(this.textRenderer, "В списках: " + ItemEncryption.allIds().size() + " • показано: " + ids.size()
				+ ". Исп.: НЕТ — нельзя ставить, активировать или тратить предмет.", cx, this.height - 46, 0xFF9FB3BD);
		super.render(c, mx, my, delta);
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}
}
