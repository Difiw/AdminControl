package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.net.ModPackets;

/**
 * Экран входа / создания пароля: пока он открыт, игрок заморожен и бессмертен, всё остальное на сервере заблокировано.
 * Вход — ник и пароль; первый заход — пароль и повтор пароля.
 */
public class AuthScreen extends Screen {
	private static final Identifier BG = new Identifier(AdminControlMod.MOD_ID, "textures/gui/login_bg.png");
	private static final int W = 1280;
	private static final int H = 720;
	private static final int FIELD_BG = 0xFF1B1C21;
	private static final int GOLD = 0xFFB98A4A;
	private static String pendingMessage = "";

	private final boolean registered;
	/** Вход: [ник, пароль]. Создание: [пароль, повтор]. */
	private final StringBuilder[] fields = {new StringBuilder(), new StringBuilder()};
	private int focus;
	private boolean show;
	private long lastSubmit;
	private String message;
	private float s;
	private float ox;
	private float oy;

	public AuthScreen(boolean registered) {
		super(Text.literal(registered ? "Вход" : "Создание пароля"));
		this.registered = registered;
		this.message = pendingMessage;
		pendingMessage = "";
		if (registered) {
			String name = MinecraftClient.getInstance().getSession().getUsername();
			if (name != null) {
				this.fields[0].append(name);
				this.focus = 1;
			}
		}
	}

	public static void setMessage(String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof AuthScreen screen) {
			screen.message = text == null ? "" : text;
		} else {
			pendingMessage = text == null ? "" : text;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void layout() {
		this.s = Math.max(this.width / (float) W, this.height / (float) H);
		this.ox = (this.width - W * this.s) / 2f;
		this.oy = (this.height - H * this.s) / 2f;
	}

	private double mx(double x) {
		return (x - this.ox) / this.s;
	}

	private double my(double y) {
		return (y - this.oy) / this.s;
	}

	// геометрия полей (координаты макета 1280×720)
	private int fieldTop(int i) {
		return this.registered ? (i == 0 ? 354 : 436) : (i == 0 ? 388 : 462);
	}

	private int fieldBottom(int i) {
		return this.registered ? fieldTop(i) + 63 : fieldTop(i) + 56;
	}

	private boolean masked(int i) {
		return this.registered ? i == 1 && !this.show : !this.show;
	}

	private int buttonTop() {
		return this.registered ? 522 : 534;
	}

	private int buttonBottom() {
		return this.registered ? 607 : 604;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		layout();
		context.fill(0, 0, this.width, this.height, 0xFF000000);
		var m = context.getMatrices();
		m.push();
		m.translate(this.ox, this.oy, 0);
		m.scale(this.s, this.s, 1f);
		context.drawTexture(BG, 0, 0, W, H, 0f, 0f, W, H, W, H);
		boolean hover = inButton(mx(mouseX), my(mouseY));
		if (this.registered) {
			// стираем «Никнейм» / «Пароль» из картинки, поля рисуем сами
			context.fill(472, 362, 862, 410, FIELD_BG);
			context.fill(472, 444, 862, 492, FIELD_BG);
			if (this.show) {
				context.fill(812, 450, 858, 488, FIELD_BG);
				drawText(context, "abc", 818, 460, 2.0f, 0xFFB9BDC6);
			}
			drawField(context, 0, "Никнейм", 478, 372);
			drawField(context, 1, "Пароль", 478, 454);
			drawCentered(context, "Введите ник и пароль от аккаунта", 640, 628, 1.6f, 0xFFD8D2C4);
			if (hover) {
				context.fill(412, 526, 872, 604, 0x22FFFFFF);
			}
		} else {
			drawRegisterPanel(context, hover);
		}
		if (!this.message.isEmpty()) {
			drawCentered(context, this.message, 640, 654, 1.7f, 0xFFFF6B6B);
		}
		drawText(context, "Выйти с сервера", 20, 690, 1.4f, 0xFF9AA0AA);
		m.pop();
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawRegisterPanel(DrawContext context, boolean hover) {
		context.fill(386, 326, 894, 640, 0xFF121317);
		frame(context, 386, 326, 508, 314, 3, GOLD);
		frame(context, 392, 332, 496, 302, 1, 0xFF3A2E1A);
		drawCentered(context, "СОЗДАНИЕ ПАРОЛЯ", 640, 344, 2.4f, 0xFFFFD27A);
		for (int i = 0; i < 2; i++) {
			int top = fieldTop(i);
			context.fill(412, top, 868, fieldBottom(i), FIELD_BG);
			frame(context, 412, top, 456, fieldBottom(i) - top, 3, this.focus == i ? 0xFFE3B866 : GOLD);
			drawField(context, i, i == 0 ? "Придумайте пароль" : "Повторите пароль", 430, top + 14);
		}
		drawText(context, this.show ? "Скрыть" : "Показать", 778, fieldTop(0) + 20, 1.5f, 0xFFB9BDC6);
		int top = buttonTop();
		int bottom = buttonBottom();
		context.fill(408, top, 872, bottom, hover ? 0xFFC83030 : 0xFFA82424);
		frame(context, 408, top, 464, bottom - top, 4, 0xFFD6A24A);
		drawCentered(context, "СОЗДАТЬ АККАУНТ", 640, top + (bottom - top) / 2 - 11, 2.6f, 0xFFFFFFFF);
		drawCentered(context, "Пароль от 3 до 32 символов. Запомните его — он нужен при каждом входе.", 640, 612, 1.3f, 0xFFD8D2C4);
	}

	private static void frame(DrawContext c, int x, int y, int w, int h, int t, int color) {
		c.fill(x, y, x + w, y + t, color);
		c.fill(x, y + h - t, x + w, y + h, color);
		c.fill(x, y + t, x + t, y + h - t, color);
		c.fill(x + w - t, y + t, x + w, y + h - t, color);
	}

	private void drawField(DrawContext context, int i, String placeholder, int x, int y) {
		float scale = 3.0f;
		String value = this.fields[i].toString();
		String shown = masked(i) ? "•".repeat(value.length()) : value;
		if (value.isEmpty()) {
			drawText(context, placeholder, x, y, this.registered ? scale : 2.4f, 0xFF8D9099);
		} else {
			drawText(context, shown, x, y, this.registered ? scale : 2.4f, 0xFFFFFFFF);
		}
		if (this.focus == i && (System.currentTimeMillis() / 500L) % 2L == 0L) {
			float sc = this.registered ? scale : 2.4f;
			int w = (int) (this.textRenderer.getWidth(shown) * sc);
			context.fill(x + w + 2, y - 2, x + w + 5, y + (int) (9 * sc) + 2, 0xFFFFFFFF);
		}
	}

	private void drawText(DrawContext context, String text, int x, int y, float scale, int color) {
		var m = context.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(scale, scale, 1f);
		context.drawText(this.textRenderer, text, 0, 0, color, true);
		m.pop();
	}

	private void drawCentered(DrawContext context, String text, int cx, int y, float scale, int color) {
		int w = (int) (this.textRenderer.getWidth(text) * scale);
		drawText(context, text, cx - w / 2, y, scale, color);
	}

	private boolean inButton(double x, double y) {
		return this.registered
				? x >= 405 && x <= 878 && y >= buttonTop() && y <= buttonBottom()
				: x >= 408 && x <= 872 && y >= buttonTop() && y <= buttonBottom();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		double x = mx(mouseX);
		double y = my(mouseY);
		if (this.registered) {
			if (x >= 812 && x <= 862 && y >= 446 && y <= 490) {
				this.show = !this.show;
				return true;
			}
		} else if (x >= 770 && x <= 868 && y >= fieldTop(0) && y <= fieldBottom(0)) {
			this.show = !this.show;
			return true;
		}
		for (int i = 0; i < 2; i++) {
			if (x >= 412 && x <= 868 && y >= fieldTop(i) && y <= fieldBottom(i)) {
				this.focus = i;
				return true;
			}
		}
		if (inButton(x, y)) {
			submit();
			return true;
		}
		if (x >= 15 && x <= 190 && y >= 684 && y <= 712 && this.client != null && this.client.getNetworkHandler() != null) {
			this.client.getNetworkHandler().getConnection().disconnect(Text.literal("Вы отключились на экране входа."));
		}
		return true;
	}

	private int maxLen() {
		return this.registered && this.focus == 0 ? 16 : 32;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (chr < 32 || chr == 127) {
			return false;
		}
		StringBuilder target = this.fields[this.focus];
		if (target.length() < maxLen()) {
			target.append(chr);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		StringBuilder target = this.fields[this.focus];
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (target.length() > 0) {
				target.setLength(target.length() - 1);
			}
		} else if (keyCode == GLFW.GLFW_KEY_TAB) {
			this.focus = (this.focus + 1) % 2;
		} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.focus == 0 && this.fields[1].length() == 0) {
				this.focus = 1;
			} else {
				submit();
			}
		} else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && this.client != null) {
			String clip = this.client.keyboard.getClipboard();
			for (int i = 0; i < clip.length() && target.length() < maxLen(); i++) {
				char c = clip.charAt(i);
				if (c >= 32 && c != 127) {
					target.append(c);
				}
			}
		}
		return true;
	}

	private void submit() {
		long now = System.currentTimeMillis();
		if (now - this.lastSubmit < 600L) {
			return;
		}
		this.lastSubmit = now;
		String nick;
		String pass;
		if (this.registered) {
			nick = this.fields[0].toString();
			pass = this.fields[1].toString();
			if (pass.isEmpty()) {
				this.message = "Введите пароль.";
				return;
			}
		} else {
			pass = this.fields[0].toString();
			String repeat = this.fields[1].toString();
			if (pass.length() < 3 || pass.length() > 32) {
				this.message = "Пароль: от 3 до 32 символов.";
				return;
			}
			if (!pass.equals(repeat)) {
				this.message = "Пароли не совпадают.";
				return;
			}
			nick = MinecraftClient.getInstance().getSession().getUsername();
		}
		this.message = "";
		var buf = PacketByteBufs.create();
		buf.writeString(nick == null ? "" : nick, 32);
		buf.writeString(pass, 64);
		ClientPlayNetworking.send(ModPackets.AUTH_SUBMIT, buf);
	}
}
