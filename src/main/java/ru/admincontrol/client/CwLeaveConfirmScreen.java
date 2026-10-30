package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** "Покинуть Люмиру?" confirmation in the style of the save window. Nothing happens until one of the two buttons is pressed. */
public final class CwLeaveConfirmScreen extends Screen {
	private static final int BUTTON_H = 22, PAD = 20;
	private final Runnable onConfirm, onBack;
	private boolean answered;
	private int panelX, panelY, panelW, panelH;

	public CwLeaveConfirmScreen(Runnable onConfirm, Runnable onBack) {
		super(Text.literal("Покинуть Люмиру?"));
		this.onConfirm = onConfirm;
		this.onBack = onBack;
	}

	@Override
	protected void init() {
		clearChildren();
		panelW = Math.min(420, width - 24);
		int textH = CwUi.textHeight(textRenderer, "Ваш прогресс в Люмире будет сохранён. При следующем входе вы продолжите с этого места.", panelW - 2 * PAD);
		panelH = 56 + textH + 14 + BUTTON_H * 2 + 8 + 16;
		panelX = (width - panelW) / 2;
		panelY = Math.max(4, (height - panelH) / 2);
		int bx = panelX + PAD, bw = panelW - 2 * PAD, by = panelY + panelH - 16 - BUTTON_H * 2 - 8;
		addDrawableChild(CwUi.Button.gold(bx, by, bw, BUTTON_H, "Остаться в Люмире", CwUi.Icon.PLAY, true, b -> answer(false)));
		addDrawableChild(CwUi.Button.gold(bx, by + BUTTON_H + 8, bw, BUTTON_H, "Покинуть Люмиру", CwUi.Icon.CLOSE, false, b -> answer(true)));
	}

	private void answer(boolean leave) {
		if (answered) return;
		answered = true;
		if (leave) { if (client != null) client.setScreen(null); onConfirm.run(); }
		else onBack.run();
	}

	@Override public boolean shouldCloseOnEsc() { return false; }
	@Override public boolean shouldPause() { return false; }

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		c.fill(0, 0, width, height, 0x88050709);
		CwUi.fancyPanel(c, panelX, panelY, panelW, panelH);
		CwUi.sprig(c, panelX + 18, panelY + 14);
		CwUi.drawScaled(c, textRenderer, "Покинуть Люмиру?", panelX + 42, panelY + 9, 1.4f, CwUi.TEXT);
		c.drawTextWithShadow(textRenderer, "Вы вернётесь в обычный мир", panelX + 42, panelY + 27, CwUi.TEXT_DIM);
		c.fill(panelX + 16, panelY + 42, panelX + panelW - 16, panelY + 43, CwUi.GOLD_DIM);
		CwUi.drawWrapped(c, textRenderer, "Ваш прогресс в Люмире будет сохранён. При следующем входе вы продолжите с этого места.", panelX + PAD, panelY + 52, panelW - 2 * PAD, CwUi.TEXT);
		super.render(c, mx, my, d);
	}
}
