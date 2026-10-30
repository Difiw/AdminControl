package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import ru.admincontrol.util.PunishmentNotice;

import java.util.ArrayList;
import java.util.List;

public class PunishmentScreen extends Screen {
	private static final int PANEL_W = 300;
	private static final int LABEL_W = 136;
	private static final int LINE = 10;
	private static final int PAD = 5;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_GAP = 4;
	private static final int BUTTON_AREA = BUTTON_H * 2 + BUTTON_GAP;
	private static final String FOOTER =
			"Если вы не согласны с выданным вам наказанием, обратитесь в Discord в связь с техническим администратором.";

	private final Screen parent;
	private final PunishmentNotice notice;
	private final List<OrderedText> footerLines = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int valueX;
	private int valueW;

	public PunishmentScreen(Screen parent, PunishmentNotice notice) {
		super(Text.literal(notice.title()));
		this.parent = parent;
		this.notice = notice;
	}

	@Override
	protected void init() {
		this.footerLines.clear();
		this.panelW = PANEL_W;
		this.valueX = 12 + LABEL_W;
		this.valueW = this.panelW - this.valueX - 12;
		this.footerLines.addAll(wrap(FOOTER, this.panelW - 24));
		this.panelH = 30
				+ rowH("Ваш никнейм", this.notice.player)
				+ rowH("Никнейм администратора", this.notice.admin)
				+ rowH("Причина", this.notice.reason)
				+ rowH("Срок", this.notice.duration)
				+ 6 + rowH("Время выдачи", this.notice.issued)
				+ 6 + this.footerLines.size() * LINE
				+ 10 + BUTTON_AREA + 10;
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = Math.max(8, (this.height - this.panelH) / 2);
		int bx = this.panelX + (this.panelW - 220) / 2;
		int by = this.panelY + this.panelH - 10 - BUTTON_AREA;
		if (this.notice.reconnect) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Подключиться к серверу снова"), b -> reconnect())
					.dimensions(bx, by, 220, BUTTON_H).build());
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Вернуться к серверам"), b -> back())
				.dimensions(bx, by + BUTTON_H + BUTTON_GAP, 220, BUTTON_H).build());
	}

	private List<OrderedText> wrap(String text, int width) {
		return this.textRenderer.wrapLines(Text.literal(text == null ? "" : text), Math.max(8, width));
	}

	private int rowH(String label, String value) {
		int valueH = PAD + Math.max(1, wrap(value, this.valueW).size()) * LINE + 3;
		int labelH = PAD + Math.max(1, wrap(label, LABEL_W - 4).size()) * LINE + 3;
		return Math.max(valueH, labelH);
	}

	private void reconnect() {
		if (this.client == null) {
			return;
		}
		ServerInfo info = this.client.getCurrentServerEntry();
		Screen next = this.parent != null ? this.parent : new MultiplayerScreen(new TitleScreen());
		if (info == null || info.address == null || info.address.isBlank()) {
			this.client.setScreen(next);
			return;
		}
		ConnectScreen.connect(next, this.client, ServerAddress.parse(info.address), info, false);
	}

	private void back() {
		if (this.client != null) {
			this.client.setScreen(this.parent != null ? this.parent : new MultiplayerScreen(new TitleScreen()));
		}
	}

	@Override
	public void close() {
		back();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		AcTheme.panel(context, this.panelX, this.panelY, this.panelW, this.panelH, 22);
		AcTheme.titleCentered(this.textRenderer, context, this.notice.title(), this.panelX, this.panelY + 7, this.panelW);
		int y = this.panelY + 30;
		y = pair(context, y, "Ваш никнейм", this.notice.player);
		y = pair(context, y, "Никнейм администратора", this.notice.admin);
		y = pair(context, y, "Причина", this.notice.reason);
		y = pair(context, y, "Срок", this.notice.duration);
		y += 6;
		y = pair(context, y, "Время выдачи", this.notice.issued);
		y += 6;
		for (OrderedText line : this.footerLines) {
			context.drawText(this.textRenderer, line, this.panelX + 12, y, AcTheme.MUTED, false);
			y += LINE;
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private int pair(DrawContext context, int y, String label, String value) {
		List<OrderedText> lines = wrap(value, this.valueW);
		int h = PAD + Math.max(1, lines.size()) * LINE + 3;
		AcTheme.row(context, this.panelX + 8, y, this.panelW - 18, h - 2);
		List<OrderedText> labels = wrap(label, LABEL_W - 4);
		int ly = y + PAD;
		for (OrderedText line : labels) {
			context.drawText(this.textRenderer, line, this.panelX + 12, ly, AcTheme.MUTED, false);
			ly += LINE;
		}
		int yy = y + PAD;
		for (OrderedText line : lines) {
			context.drawText(this.textRenderer, line, this.panelX + this.valueX, yy, AcTheme.TEXT, false);
			yy += LINE;
		}
		return y + rowH(label, value);
	}

	@Override
	public boolean shouldPause() {
		return true;
	}
}
