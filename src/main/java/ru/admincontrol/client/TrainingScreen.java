package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.screen.TrainingScreenHandler;
import ru.admincontrol.util.BotMatch;
import ru.admincontrol.util.Money;
import ru.admincontrol.util.TrainingConfig;

/**
 * Экран тренировки. Вся раскладка задана в координатах макета 1672×941 и масштабируется под окно.
 * Картинки режимов и декоративные плашки вырезаны из макета, остальное рисуется кодом.
 */
public class TrainingScreen extends HandledScreen<TrainingScreenHandler> {
	private static final int W = 1672;
	private static final int H = 941;
	private static final String TEX = "textures/gui/training/";

	private static final int PANEL = 0xFF0A0F24;
	private static final int PANEL_DARK = 0xFF080D1F;
	private static final int PURPLE = 0xFF8A5BFF;
	private static final int PURPLE_DIM = 0xFF3A2E7A;
	private static final int CYAN = 0xFF2FD7FF;
	private static final int STEEL = 0xFF3A4568;
	private static final int TITLE = 0xFFD9C4FF;
	private static final int TEXT_MAIN = 0xFFE8EEF8;
	private static final int GOLD = 0xFFFFC94A;

	private static final int[] CARD_X = {589, 837, 1085, 1333};
	private static final int[] CARD_Y = {195, 457};
	private static final int CARD_W = 231;
	private static final int CARD_H = 240;

	private static final Item[] TYPE_ICONS = {Items.ZOMBIE_HEAD, Items.SKELETON_SKULL, Items.SPIDER_EYE};
	private static final Item[] MODE_ICONS = {Items.IRON_SWORD, Items.SHIELD, Items.WHITE_BANNER, Items.END_CRYSTAL,
			Items.ZOMBIE_HEAD, Items.CLOCK, Items.BOW, Items.NETHER_STAR};

	private float s = 1f;
	private float ox;
	private float oy;
	private double mx;
	private double my;

	public TrainingScreen(TrainingScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 10;
		this.backgroundHeight = 10;
		this.playerInventoryTitleY = 10000;
		this.titleY = 10000;
	}

	private void layout() {
		this.s = Math.min(this.width / (float) W, this.height / (float) H) * 0.97f;
		this.ox = (this.width - W * this.s) / 2f;
		this.oy = (this.height - H * this.s) / 2f;
	}

	private static Identifier tex(String name) {
		return new Identifier(AdminControlMod.MOD_ID, TEX + name + ".png");
	}

	private boolean bots() {
		return this.handler.page() == 2 && this.handler.botAllowed();
	}

	// ---------------------------------------------------------------- drawing helpers

	private static void frame(DrawContext c, int x, int y, int w, int h, int t, int color) {
		c.fill(x, y, x + w, y + t, color);
		c.fill(x, y + h - t, x + w, y + h, color);
		c.fill(x, y + t, x + t, y + h - t, color);
		c.fill(x + w - t, y + t, x + w, y + h - t, color);
	}

	private static void glow(DrawContext c, int x, int y, int w, int h, int rgb) {
		for (int i = 1; i <= 5; i++) {
			int a = 0x50 - i * 0x0E;
			frame(c, x - i * 2, y - i * 2, w + i * 4, h + i * 4, 2, (a << 24) | rgb);
		}
	}

	private void text(DrawContext c, String text, int x, int y, float scale, int color) {
		var m = c.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(scale, scale, 1f);
		c.drawText(this.textRenderer, text, 0, 0, color, true);
		m.pop();
	}

	private void centered(DrawContext c, String text, int cx, int y, float scale, int color) {
		text(c, text, cx - (int) (this.textRenderer.getWidth(text) * scale / 2f), y, scale, color);
	}

	private void item(DrawContext c, Item item, int x, int y, float scale) {
		var m = c.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(scale, scale, 1f);
		c.drawItem(new ItemStack(item), 0, 0);
		m.pop();
	}

	private void image(DrawContext c, String name, int x, int y, int w, int h) {
		c.drawTexture(tex(name), x, y, w, h, 0f, 0f, w, h, w, h);
	}

	private static boolean in(double px, double py, int x, int y, int w, int h) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		layout();
		this.mx = (mouseX - this.ox) / this.s;
		this.my = (mouseY - this.oy) / this.s;
		// затемнённая основная игра за экраном, а не чёрная заливка
		this.renderBackground(context);
		context.fillGradient(0, 0, this.width, this.height, 0x50040612, 0x70060820);
		var m = context.getMatrices();
		m.push();
		m.translate(this.ox, this.oy, 0);
		m.scale(this.s, this.s, 1f);
		drawLeft(context);
		drawRight(context);
		drawBottom(context);
		m.pop();
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
	}

	private void drawLeft(DrawContext c) {
		c.fill(72, 45, 545, 880, PANEL);
		frame(c, 72, 45, 473, 835, 3, PURPLE_DIM);
		image(c, "title_left", 85, 40, 460, 175);
		boolean allowed = this.handler.botAllowed();
		boolean botsOn = bots();
		// С мобами
		image(c, "cat_mobs", 110, 260, 398, 238);
		boolean hoverMobs = in(this.mx, this.my, 95, 250, 425, 255);
		if (!botsOn) {
			glow(c, 106, 256, 406, 246, 0x8A5BFF);
			frame(c, 104, 254, 410, 250, 4, PURPLE);
		} else {
			frame(c, 106, 256, 406, 246, 3, hoverMobs ? 0xFF7A88B8 : STEEL);
		}
		// С ботами
		image(c, "cat_bots", 107, 545, 401, 255);
		c.fill(107, 800, 508, 826, PANEL_DARK);
		if (botsOn) {
			glow(c, 103, 541, 409, 289, 0x2FD7FF);
			frame(c, 101, 539, 413, 293, 4, CYAN);
		} else {
			frame(c, 103, 541, 409, 289, 3, in(this.mx, this.my, 95, 530, 425, 320) && allowed ? 0xFF7A88B8 : STEEL);
		}
		if (!allowed) {
			c.fill(107, 545, 508, 826, 0xB0050812);
			centered(c, "ЗАКРЫТО", 307, 650, 4.0f, 0xFFFF8A8A);
			centered(c, "Недоступно сейчас", 307, 705, 2.0f, 0xFFB9C0D8);
		}
	}

	private void drawRight(DrawContext c) {
		c.fill(560, 60, 1595, 730, PANEL);
		frame(c, 560, 60, 1035, 670, 3, PURPLE_DIM);
		frame(c, 566, 66, 1023, 658, 1, 0xFF241D55);
		// диамант-орнамент сверху
		c.fill(1025, 50, 1049, 74, PURPLE);
		c.fill(1030, 55, 1044, 69, 0xFF1A1050);
		boolean botsOn = bots();
		text(c, botsOn ? "ТРЕНИРОВКА С БОТАМИ" : "ТРЕНИРОВКА С МОБАМИ", 640, 98, 4.0f, TITLE);
		c.fill(630, 160, 1140, 162, PURPLE);
		c.fill(1136, 156, 1144, 166, PURPLE);
		if (botsOn) {
			drawTabs(c);
			drawModeGrid(c);
		} else {
			drawMobTypes(c);
		}
	}

	private void drawTabs(DrawContext c) {
		int[] xs = {1173, 1293, 1430};
		int[] ws = {112, 128, 122};
		String[] names = {"Простой", "Средний", "Сложный"};
		int[] fill = {0xFF0B2B22, 0xFF241060, 0xFF3A1414};
		int[] line = {0xFF1F8A55, PURPLE, 0xFFB13A3A};
		int[] txt = {0xFF58E0A0, 0xFFE3D4FF, 0xFFFF9A9A};
		int sel = this.handler.difficulty();
		for (int i = 0; i < 3; i++) {
			int y = i == sel ? 98 : 100;
			int h = i == sel ? 54 : 50;
			c.fill(xs[i], y, xs[i] + ws[i], y + h, fill[i]);
			if (i == sel) {
				glow(c, xs[i], y, ws[i], h, line[i] & 0xFFFFFF);
				frame(c, xs[i], y, ws[i], h, 4, line[i]);
			} else {
				frame(c, xs[i], y, ws[i], h, 3, line[i] & 0xAAFFFFFF);
			}
			centered(c, names[i], xs[i] + ws[i] / 2, y + h / 2 - 8, 2.0f, txt[i]);
		}
	}

	private void drawModeGrid(DrawContext c) {
		int sel = this.handler.botMode();
		for (int i = 0; i < 8; i++) {
			int x = CARD_X[i % 4];
			int y = CARD_Y[i / 4];
			boolean hover = in(this.mx, this.my, x, y, CARD_W, CARD_H);
			c.fill(x, y, x + CARD_W, y + CARD_H, 0xFF0C1226);
			image(c, "mode" + i, x + 4, y + 4, 223, 160);
			if (hover) {
				c.fill(x + 4, y + 4, x + 227, y + 164, 0x18FFFFFF);
			}
			if (i == sel) {
				glow(c, x, y, CARD_W, CARD_H, 0xB07CFF);
				frame(c, x, y, CARD_W, CARD_H, 4, 0xFFC4A0FF);
				c.fill(x + CARD_W / 2 - 9, y - 9, x + CARD_W / 2 + 9, y + 9, 0xFFC4A0FF);
				c.fill(x + CARD_W / 2 - 5, y - 5, x + CARD_W / 2 + 5, y + 5, 0xFF1A1050);
			} else {
				frame(c, x, y, CARD_W, CARD_H, 3, hover ? 0xFF5A6598 : 0xFF2C3354);
			}
			int cx = x + CARD_W / 2;
			centered(c, BotMatch.NAMES[i], cx, y + 172, 1.7f, i == sel ? 0xFFFFFFFF : TEXT_MAIN);
			image(c, "icon_allies", cx - 62, y + 198, 28, 28);
			text(c, String.valueOf(BotMatch.DEF_ALLIES[i]), cx - 28, y + 203, 2.2f, TEXT_MAIN);
			image(c, "icon_enemies", cx + 12, y + 198, 28, 28);
			text(c, BotMatch.DEF_ENEMIES[i] == 0 ? "∞" : String.valueOf(BotMatch.DEF_ENEMIES[i]), cx + 46, y + 203, 2.2f, TEXT_MAIN);
		}
	}

	private void drawMobTypes(DrawContext c) {
		int sel = this.handler.type();
		for (int i = 0; i < TrainingConfig.TYPES.length; i++) {
			int x = CARD_X[i];
			int y = CARD_Y[0];
			boolean hover = in(this.mx, this.my, x, y, CARD_W, CARD_H);
			c.fillGradient(x, y, x + CARD_W, y + CARD_H, 0xFF141A38, 0xFF0A0F24);
			item(c, TYPE_ICONS[i], x + CARD_W / 2 - 56, y + 30, 7f);
			if (i == sel) {
				glow(c, x, y, CARD_W, CARD_H, 0xB07CFF);
				frame(c, x, y, CARD_W, CARD_H, 4, 0xFFC4A0FF);
			} else {
				frame(c, x, y, CARD_W, CARD_H, 3, hover ? 0xFF5A6598 : 0xFF2C3354);
			}
			centered(c, TrainingConfig.TYPE_LABELS[i], x + CARD_W / 2, y + 180, 2.4f, i == sel ? 0xFFFFFFFF : TEXT_MAIN);
		}
		text(c, "Время тренировки", 589, 450, 2.4f, 0xFFA8B4D8);
		int sm = this.handler.minutes();
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			int x = 589 + i * 140;
			int y = 490;
			boolean on = TrainingConfig.MINUTES[i] == sm;
			boolean hover = in(this.mx, this.my, x, y, 128, 80);
			c.fill(x, y, x + 128, y + 80, on ? 0xFF241060 : 0xFF0C1226);
			if (on) {
				glow(c, x, y, 128, 80, 0x8A5BFF);
			}
			frame(c, x, y, 128, 80, on ? 4 : 3, on ? PURPLE : (hover ? 0xFF5A6598 : 0xFF2C3354));
			centered(c, TrainingConfig.MINUTES[i] + " мин", x + 64, y + 30, 2.2f, on ? 0xFFFFFFFF : TEXT_MAIN);
		}
		centered(c, "Мобы нападают волнами, пока идёт таймер. Выйти: /off", 1076, 620, 2.0f, 0xFFA8B4D8);
	}

	private void drawBottom(DrawContext c) {
		boolean botsOn = bots();
		if (botsOn) {
			image(c, "rewards", 562, 748, 352, 134);
			text(c, "≈ " + this.handler.botCoins() + " WC", 800, 770, 1.8f, GOLD);
			image(c, "spinners", 922, 745, 322, 135);
			c.fill(978, 826, 1030, 862, 0xFF0A1020);
			c.fill(1142, 826, 1194, 862, 0xFF0A1020);
			centered(c, String.valueOf(this.handler.allies()), 1004, 833, 3.0f, 0xFFFFFFFF);
			int mode = this.handler.botMode();
			centered(c, BotMatch.enemiesFixed(mode) && mode == 5 ? "∞" : String.valueOf(this.handler.enemies()), 1168, 833, 3.0f,
					BotMatch.enemiesFixed(mode) ? 0xFF8A93B0 : 0xFFFFFFFF);
		} else {
			c.fill(562, 748, 1245, 880, PANEL);
			frame(c, 562, 748, 683, 132, 3, PURPLE_DIM);
			text(c, "НАГРАДА", 590, 772, 2.4f, 0xFFA8B4D8);
			text(c, Money.wc(this.handler.coins()), 590, 812, 3.6f, GOLD);
			text(c, "сила +" + String.format(java.util.Locale.US, "%.1f", this.handler.powerTenths() / 10.0), 900, 818, 2.6f, 0xFF79E6FF);
		}
		image(c, "btn_back", 1255, 765, 128, 95);
		image(c, "btn_start", 1385, 755, 208, 112);
		if (in(this.mx, this.my, 1255, 765, 128, 95)) {
			c.fill(1257, 767, 1381, 858, 0x22FFFFFF);
		}
		if (in(this.mx, this.my, 1385, 755, 208, 112)) {
			c.fill(1388, 760, 1590, 864, 0x22FFFFFF);
		}
	}

	// ---------------------------------------------------------------- input

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		double x = (mouseX - this.ox) / this.s;
		double y = (mouseY - this.oy) / this.s;
		if (in(x, y, 95, 250, 425, 255)) {
			click(TrainingScreenHandler.OPEN_CLASSIC);
			return true;
		}
		if (in(x, y, 95, 530, 425, 320)) {
			click(TrainingScreenHandler.OPEN_BOTS);
			return true;
		}
		if (in(x, y, 1255, 765, 128, 95)) {
			this.close();
			return true;
		}
		if (bots()) {
			return clickBots(x, y);
		}
		return clickMobs(x, y);
	}

	private boolean clickBots(double x, double y) {
		int[] xs = {1173, 1293, 1430};
		int[] ws = {112, 128, 122};
		for (int i = 0; i < 3; i++) {
			if (in(x, y, xs[i], 98, ws[i], 56)) {
				click(TrainingScreenHandler.DIFF_BASE + i);
				return true;
			}
		}
		for (int i = 0; i < 8; i++) {
			if (in(x, y, CARD_X[i % 4], CARD_Y[i / 4], CARD_W, CARD_H)) {
				click(TrainingScreenHandler.BOT_MODE_BASE + i);
				return true;
			}
		}
		if (in(x, y, 938, 827, 36, 36)) {
			click(TrainingScreenHandler.ALLY_MINUS);
		} else if (in(x, y, 1034, 827, 36, 36)) {
			click(TrainingScreenHandler.ALLY_PLUS);
		} else if (in(x, y, 1101, 827, 36, 36)) {
			click(TrainingScreenHandler.ENEMY_MINUS);
		} else if (in(x, y, 1197, 827, 36, 36)) {
			click(TrainingScreenHandler.ENEMY_PLUS);
		} else if (in(x, y, 1385, 755, 208, 112)) {
			click(TrainingScreenHandler.BOT_START);
		}
		return true;
	}

	private boolean clickMobs(double x, double y) {
		for (int i = 0; i < TrainingConfig.TYPES.length; i++) {
			if (in(x, y, CARD_X[i], CARD_Y[0], CARD_W, CARD_H)) {
				click(TrainingScreenHandler.TYPE_BASE + i);
				return true;
			}
		}
		for (int i = 0; i < TrainingConfig.MINUTES.length; i++) {
			if (in(x, y, 589 + i * 140, 490, 128, 80)) {
				click(TrainingScreenHandler.TIME_BASE + i);
				return true;
			}
		}
		if (in(x, y, 1385, 755, 208, 112)) {
			click(TrainingScreenHandler.START);
		}
		return true;
	}
}
