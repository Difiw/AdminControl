package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.screen.RoleEditorScreenHandler;
import ru.admincontrol.util.CustomRanks;
import ru.admincontrol.util.NickColors;

import java.util.ArrayList;
import java.util.List;

public class RoleEditorScreen extends HandledScreen<RoleEditorScreenHandler> {
	private static final int ROW = 20;
	private static final int VISIBLE = 8;
	private static final int LIST_W = 96;
	private static final int LABEL_W = 92;
	private static final int[] PALETTE = {0x55FFFF, 0x55FF55, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xFF55FF};
	private final List<ButtonWidget> listButtons = new ArrayList<>();
	private TextFieldWidget titleField;
	private TextFieldWidget roleField;
	private TextFieldWidget formatField;
	private TextFieldWidget colorField;
	private TextFieldWidget accessField;
	private ButtonWidget deleteButton;
	private boolean deleteArmed;
	private String status = "";
	private int scroll;

	public RoleEditorScreen(RoleEditorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 340;
		this.backgroundHeight = 232;
		this.playerInventoryTitleY = 1000;
	}

	private int fx() {
		return this.x + LIST_W + 16;
	}

	private int fieldX() {
		return fx() + LABEL_W;
	}

	private int fieldW() {
		return this.x + this.backgroundWidth - 8 - fieldX();
	}

	private int maxScroll() {
		return Math.max(0, this.handler.entries().size() - VISIBLE);
	}

	@Override
	protected void init() {
		super.init();
		RoleEditorScreenHandler.Entry e = this.handler.current();
		this.addDrawableChild(ButtonWidget.builder(Text.literal("§a+ Новый уровень"), b -> click(RoleEditorScreenHandler.NEW))
				.dimensions(this.x + 8, this.y + 24, LIST_W, 18).build());
		int index = this.handler.entries().indexOf(e);
		if (index >= VISIBLE) {
			this.scroll = Math.min(maxScroll(), index - VISIBLE + 1);
		}
		rebuildList();

		this.titleField = field(this.y + 24, CustomRanks.MAX_TITLE, e == null ? "" : e.title(), "Помощник модератора");
		this.roleField = field(this.y + 46, CustomRanks.MAX_ROLE, e == null ? "" : e.role(), "ПМ");
		this.formatField = field(this.y + 68, CustomRanks.MAX_FORMAT, e == null ? CustomRanks.DEFAULT_FORMAT : e.format(),
				CustomRanks.DEFAULT_FORMAT);
		this.colorField = new TextFieldWidget(this.textRenderer, fieldX(), this.y + 90, 56, 18, Text.empty());
		setup(this.colorField, 7, e == null ? NickColors.hex(CustomRanks.DEFAULT_COLOR) : NickColors.hex(e.color()), "#rrggbb");
		this.colorField.setTextPredicate(v -> v != null && v.matches("(?i)#?[0-9a-f]{0,6}"));
		this.accessField = new TextFieldWidget(this.textRenderer, fieldX(), this.y + 112, 40, 18, Text.empty());
		setup(this.accessField, 3, e == null ? "" : e.access(), "2.5");
		this.accessField.setTextPredicate(v -> v != null && v.matches("[0-9]?([.,][0-9]?)?"));

		boolean editable = e == null || e.editable();
		int bw = (fieldW() + LABEL_W - 4) / 2;
		int bx = fx();
		int by = this.y + 184;
		ButtonWidget save = ButtonWidget.builder(Text.literal(e == null ? "§aСоздать" : "§aСохранить"), b -> save())
				.dimensions(bx, by, bw, 18).build();
		save.active = editable && !this.handler.maxAccess().isEmpty();
		this.addDrawableChild(save);
		ButtonWidget perms = ButtonWidget.builder(Text.literal("Права команд…"), b -> click(RoleEditorScreenHandler.PERMS))
				.dimensions(bx + bw + 4, by, bw, 18).build();
		perms.active = e != null && editable;
		this.addDrawableChild(perms);
		ButtonWidget reset = ButtonWidget.builder(Text.literal("Подобрать права заново"), b -> click(RoleEditorScreenHandler.RESET))
				.dimensions(bx, by + 22, bw, 18).build();
		reset.active = e != null && editable;
		this.addDrawableChild(reset);
		this.deleteButton = ButtonWidget.builder(Text.literal("§cУдалить"), b -> delete())
				.dimensions(bx + bw + 4, by + 22, bw, 18).build();
		this.deleteButton.active = e != null && editable;
		this.addDrawableChild(this.deleteButton);
	}

	private TextFieldWidget field(int y, int max, String text, String hint) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX(), y, fieldW(), 18, Text.empty());
		setup(field, max, text, hint);
		return field;
	}

	private void setup(TextFieldWidget field, int max, String text, String hint) {
		field.setMaxLength(max);
		field.setText(text == null ? "" : text);
		field.setEditableColor(0xE8EEF8);
		field.setDrawsBackground(true);
		AcHint.apply(field, hint, v -> this.status = "");
		this.addDrawableChild(field);
	}

	private void rebuildList() {
		for (ButtonWidget b : this.listButtons) {
			this.remove(b);
		}
		this.listButtons.clear();
		List<RoleEditorScreenHandler.Entry> entries = this.handler.entries();
		for (int i = 0; i < VISIBLE; i++) {
			int index = this.scroll + i;
			if (index >= entries.size()) {
				break;
			}
			RoleEditorScreenHandler.Entry e = entries.get(index);
			String mark = e.key().equals(this.handler.selected()) ? "§e" : e.editable() ? "" : "§7";
			String label = AcTheme.ellipsize(this.textRenderer, e.access() + "  " + e.title(), LIST_W - 8);
			int id = RoleEditorScreenHandler.SELECT_BASE + index;
			ButtonWidget b = ButtonWidget.builder(Text.literal(mark + label), w -> click(id))
					.dimensions(this.x + 8, this.y + 46 + i * ROW, LIST_W, 18).build();
			this.listButtons.add(this.addDrawableChild(b));
		}
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private void delete() {
		if (!this.deleteArmed) {
			this.deleteArmed = true;
			this.deleteButton.setMessage(Text.literal("§cТочно удалить?"));
			return;
		}
		click(RoleEditorScreenHandler.DELETE);
	}

	private void save() {
		String title = this.titleField.getText().trim();
		String role = this.roleField.getText().trim();
		String format = this.formatField.getText().trim();
		if (title.isEmpty()) {
			this.status = "Укажите название уровня.";
			return;
		}
		if (role.isEmpty()) {
			this.status = "Укажите название роли.";
			return;
		}
		if (!format.isEmpty() && !format.contains("{сообщение}")) {
			this.status = "В формате нужен {сообщение}.";
			return;
		}
		if (NickColors.parse(this.colorField.getText()) == null) {
			this.status = "Цвет — шесть цифр, например #55ffff.";
			return;
		}
		Integer tenths = CustomRanks.parseAccess(this.accessField.getText());
		Integer max = CustomRanks.parseAccess(this.handler.maxAccess());
		if (tenths == null || max == null || tenths > max) {
			this.status = "Уровень доступа: от 1 до " + this.handler.maxAccess() + ", например 2 или 2.5.";
			return;
		}
		RoleEditorScreenHandler.Entry e = this.handler.current();
		var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeString(e == null ? "" : e.key(), 16);
		buf.writeString(title, 64);
		buf.writeString(role, 64);
		buf.writeString(format, 128);
		buf.writeString(this.colorField.getText().trim(), 16);
		buf.writeString(this.accessField.getText().trim(), 8);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.ROLE_SAVE, buf);
		this.status = "";
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < this.x + 8 + LIST_W + 4) {
			int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
			if (next != this.scroll) {
				this.scroll = next;
				rebuildList();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int sx = fieldX() + 62;
		int sy = this.y + 93;
		for (int i = 0; i < PALETTE.length; i++) {
			int x0 = sx + i * 14;
			if (mouseX >= x0 && mouseX < x0 + 12 && mouseY >= sy && mouseY < sy + 12) {
				this.colorField.setText(NickColors.hex(PALETTE[i]));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode != GLFW.GLFW_KEY_ESCAPE && this.getFocused() instanceof TextFieldWidget field) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				save();
				return true;
			}
			if (field.keyPressed(keyCode, scanCode, modifiers) || field.isActive()) {
				return true;
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
		int sx = fieldX() + 62;
		int sy = this.y + 93;
		for (int i = 0; i < PALETTE.length; i++) {
			int x0 = sx + i * 14;
			context.fill(x0 - 1, sy - 1, x0 + 13, sy + 13, 0xFF20242C);
			context.fill(x0, sy, x0 + 12, sy + 12, 0xFF000000 | PALETTE[i]);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, this.title.getString(), 8, 6, this.backgroundWidth - 16);
		int lx = LIST_W + 16;
		int[] rows = {24, 46, 68, 90, 112};
		String[] labels = {"Название уровня", "Название роли", "Формат чата", "Цвет роли", "Уровень доступа"};
		for (int i = 0; i < rows.length; i++) {
			AcTheme.clipped(this.textRenderer, context, labels[i], lx, rows[i] + 5, LABEL_W - 4, 0xC8D2E0);
		}
		String max = this.handler.maxAccess();
		AcTheme.clipped(this.textRenderer, context, max.isEmpty() ? "" : "от 1 до " + max + " (2, 2.5…)",
				LABEL_W + lx + 46, 117, fieldW() - 46, 0x8A96A8);
		if (this.handler.entries().isEmpty()) {
			AcTheme.wrapped(this.textRenderer, context, "Созданных уровней пока нет.", 8, 50, LIST_W, 0x8A96A8);
		}

		int colorRgb = NickColors.parse(this.colorField.getText()) == null ? CustomRanks.DEFAULT_COLOR
				: NickColors.parse(this.colorField.getText());
		String nick = this.client != null && this.client.player != null ? this.client.player.getGameProfile().getName() : "Ник";
		String role = this.roleField.getText().isBlank() ? "Роль" : this.roleField.getText().trim();
		Text preview = CustomRanks.chatLine(this.formatField.getText().trim(), role, colorRgb, nick, "Привет!");
		context.drawText(this.textRenderer, Text.literal("Чат: ").append(preview), lx, 138, 0xC8D2E0, false);

		RoleEditorScreenHandler.Entry e = this.handler.current();
		String info;
		if (!this.status.isEmpty()) {
			info = "§c" + this.status;
		} else if (e == null) {
			info = "Права подберутся по уровню доступа; потом их можно менять.";
		} else {
			info = "Ключ " + e.key() + " · админов: " + e.holders() + " · команд: " + e.commands()
					+ (e.editable() ? "" : " · §cтолько просмотр");
		}
		AcTheme.wrapped(this.textRenderer, context, info, lx, 152, this.backgroundWidth - lx - 8, 0x8A96A8);
		if (e != null) {
			AcTheme.clipped(this.textRenderer, context, "Выдать: /set_admin <ник> " + e.key(), lx, 172,
					this.backgroundWidth - lx - 8, 0x8A96A8);
		}
	}
}
