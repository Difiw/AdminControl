package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.util.CweNotice;

import java.util.List;

/**
 * "АККАУНТ НЕДОСТУПЕН": shown only for refusals caused by the Common World: Expedition name blacklist. Every string is
 * fixed here; the only server-provided values are the validated name, kind and listing time (see {@link CweNotice}).
 * It never shows who listed the name. Ordinary bans/jails keep their own screen.
 */
public final class CweBlockedScreen extends Screen {
	private static final Identifier BACKGROUND = new Identifier("admin_control", "textures/gui/luya_bg.png");
	private static final int BG_W = 1672, BG_H = 941, LABEL_W = 96, ROW_PAD = 5;
	private static final int RED = 0xFFFF4B4B, BLUE = 0xFF3C8DFF, BLUE_DIM = 0xFF1F4F8F, PANEL = 0xEE070C14, LINE = 0xFF1B2A40;
	private static final String REASON = "Имя зарезервировано для сюжетного контента Common World: Expedition.";
	private static final String INFO = "Создание и вход под этим именем запрещены. Если вы считаете это ошибкой, обратитесь к администрации сервера.";

	private final Screen parent;
	private final CweNotice.Data data;
	private int panelX, panelY, panelW, panelH, rowsY, infoY, infoH;

	public CweBlockedScreen(Screen parent, CweNotice.Data data) {
		super(Text.literal("Аккаунт недоступен"));
		this.parent = parent;
		this.data = data;
	}

	private boolean reserved() { return data.kind() == CweNotice.Kind.RESERVED; }

	private String[][] rows() {
		return new String[][] {
				{"Имя аккаунта", data.name(), null},
				{"Статус", reserved() ? "Имя зарезервировано" : "Аккаунт заблокирован", reserved() ? "Аккаунт не создан." : "Вход и создание под этим именем запрещены."},
				{"Причина", REASON, null},
				{"Дата внесения", CweNotice.formatDate(data.listedAt()), null},
		};
	}

	private int valueW() { return panelW - 2 * 16 - LABEL_W - 8; }

	private List<OrderedText> wrap(String text, int width) { return textRenderer.wrapLines(Text.literal(text), Math.max(40, width)); }

	private int rowH(String[] r) {
		int lines = wrap(r[1], valueW()).size() + (r[2] == null ? 0 : wrap(r[2], valueW()).size());
		return ROW_PAD * 2 + lines * 10;
	}

	@Override
	protected void init() {
		panelW = Math.min(440, width - 16);
		int h = 56;
		for (String[] r : rows()) h += rowH(r);
		infoH = wrap(INFO, panelW - 2 * 16 - 8).size() * 10 + 16;
		panelH = h + 10 + infoH + 14;
		panelH = Math.min(panelH, height - 40);
		panelX = Math.max(4, Math.min((width - panelW) / 2 + (width >= 700 ? panelW / 3 : 0), width - panelW - 4));
		panelY = Math.max(24, (height - panelH - 30) / 2);
		int bw = Math.min(220, panelW - 20);
		addDrawableChild(ButtonWidget.builder(Text.literal("Вернуться к серверам"), b -> back())
				.dimensions(panelX + (panelW - bw) / 2, Math.min(height - 26, panelY + panelH + 8), bw, 20).build());
	}

	private void back() {
		if (client == null) return;
		client.setScreen(parent != null ? parent : new MultiplayerScreen(new TitleScreen()));
	}

	@Override public boolean shouldCloseOnEsc() { return true; }
	@Override public void close() { back(); }

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		double scale = Math.max(width / (double) BG_W, height / (double) BG_H);
		int w = (int) Math.ceil(BG_W * scale), h = (int) Math.ceil(BG_H * scale);
		c.drawTexture(BACKGROUND, Math.min(0, (int) ((width - w) * 0.25)), (height - h) / 2, w, h, 0, 0, BG_W, BG_H, BG_W, BG_H);
		c.fill(0, 0, width, height, 0x88050A12);
		c.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		CwUi.border(c, panelX, panelY, panelW, panelH, BLUE);
		CwUi.border(c, panelX - 1, panelY - 1, panelW + 2, panelH + 2, 0x553C8DFF);
		lock(c, panelX + 18, panelY + 10);
		CwUi.drawScaled(c, textRenderer, "АККАУНТ", panelX + 60, panelY + 12, 1.6f, CwUi.TEXT);
		CwUi.drawScaled(c, textRenderer, "НЕДОСТУПЕН", panelX + 60 + (int) (textRenderer.getWidth("АККАУНТ ") * 1.6f), panelY + 12, 1.6f, RED);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Данное имя внесено в чёрный список Common World: Expedition.", panelW - 76), panelX + 60, panelY + 34, CwUi.TEXT_DIM);
		int y = panelY + 52;
		for (String[] r : rows()) {
			int rh = rowH(r);
			c.fill(panelX + 12, y, panelX + panelW - 12, y + 1, LINE);
			c.drawTextWithShadow(textRenderer, r[0], panelX + 16, y + ROW_PAD, CwUi.TEXT_DIM);
			int ty = y + ROW_PAD, vx = panelX + 16 + LABEL_W;
			boolean status = "Статус".equals(r[0]);
			for (OrderedText line : wrap(r[1], valueW())) { c.drawTextWithShadow(textRenderer, line, vx, ty, status ? RED : CwUi.TEXT); ty += 10; }
			if (r[2] != null) for (OrderedText line : wrap(r[2], valueW())) { c.drawTextWithShadow(textRenderer, line, vx, ty, CwUi.TEXT_DIM); ty += 10; }
			y += rh;
		}
		c.fill(panelX + 12, y, panelX + panelW - 12, y + 1, LINE);
		int iy = y + 8;
		CwUi.border(c, panelX + 12, iy, panelW - 24, infoH, BLUE_DIM);
		int ty = iy + 8;
		for (OrderedText line : wrap(INFO, panelW - 2 * 16 - 8)) { c.drawTextWithShadow(textRenderer, line, panelX + 20, ty, 0xFF9FC4F0); ty += 10; }
		super.render(c, mx, my, delta);
	}

	/** Pixel-art padlock with a red cross, drawn from rectangles so no extra texture is required. */
	private void lock(DrawContext c, int x, int y) {
		c.fill(x + 8, y, x + 12, y + 4, 0xFFB03A48);
		c.fill(x + 8, y, x + 10, y + 12, 0xFFB03A48);
		c.fill(x + 22, y, x + 26, y + 12, 0xFFB03A48);
		c.fill(x + 8, y, x + 26, y + 4, 0xFFB03A48);
		c.fill(x, y + 12, x + 34, y + 38, 0xFFE0485A);
		CwUi.border(c, x, y + 12, 34, 26, 0xFFFF8A94);
		for (int i = 0; i < 12; i++) {
			c.fill(x + 11 + i, y + 18 + i, x + 13 + i, y + 20 + i, 0xFF2A0A10);
			c.fill(x + 22 - i, y + 18 + i, x + 24 - i, y + 20 + i, 0xFF2A0A10);
		}
	}
}
