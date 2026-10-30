package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;

import java.util.List;
import java.util.UUID;

/**
 * Save selection window shown after a death in the story («Вы погибли»); nothing is loaded automatically. The server sends
 * the real state of the two save slots (automatic, manual); an unavailable slot gets a disabled button and a reason. The window
 * cannot be dismissed; the only other action is leaving the story (with confirmation).
 */
public final class CwRespawnScreen extends Screen {
	public static final int CHOICE_AUTO = 0, CHOICE_MANUAL = 1, CHOICE_LEAVE = 2;
	private static final int BUTTON_W = 132, BUTTON_H = 22, PAD = 20, CONFIRM_MS = 5000;
	private static final String[] TITLES = {"Автоматическое сохранение", "Ваша точка возрождения"};
	private static final String[] DESCRIPTIONS = {
			"Последнее автоматически созданное сохранение.",
			"Вернуться к последней установленной вами точке возрождения."};

	/** One save slot as reported by the server. {@code reason} is a server reason code when not available. */
	public record Slot(boolean available, long ageSeconds, String detail, String reason) {}

	private final UUID nonce;

	private final Slot[] slots;
	private boolean answered;
	private CwUi.Button leaveButton;
	private int panelX, panelY, panelW, panelH;
	private final int[] rowY = new int[2];
	private boolean wide, describe;

	private final boolean death;

	public CwRespawnScreen(UUID nonce, boolean death, Slot auto, Slot manual) {
		super(Text.literal(death ? "Вы погибли" : "Сохранения"));
		this.death = death;
		this.nonce = nonce;

		this.slots = new Slot[] {auto, manual};
	}

	private int textWidth() { return wide ? panelW - 2 * PAD - BUTTON_W - 14 : panelW - 2 * PAD; }

	private int rowHeight(int i) {
		int lines = describe ? textRenderer.wrapLines(Text.literal(DESCRIPTIONS[i]), Math.max(40, textWidth())).size() : 0;
		return 8 + 12 + lines * 11 + 12 + (wide ? 0 : BUTTON_H + 6) + 8;
	}

	private int rowsHeight() { return rowHeight(0) + rowHeight(1); }

	@Override
	protected void init() {
		clearChildren();
		panelW = Math.min(520, width - 24);
		wide = panelW >= 400;
		describe = true;
		int header = 48, footer = 8 + BUTTON_H + 16;
		panelH = header + rowsHeight() + footer;
		if (panelH > height - 8) { describe = false; panelH = header + rowsHeight() + footer; }
		panelX = (width - panelW) / 2;
		panelY = Math.max(4, (height - panelH) / 2);
		int y = panelY + header;
		for (int i = 0; i < 2; i++) {
			rowY[i] = y;
			addRowButton(slots[i], y, rowHeight(i), i);
			y += rowHeight(i);
		}
		int by = panelY + panelH - 16 - BUTTON_H, bw = Math.min(260, panelW - 2 * PAD);
		if (death) {
			leaveButton = CwUi.Button.gold(panelX + (panelW - bw) / 2, by, bw, BUTTON_H, "Покинуть Люмиру", CwUi.Icon.NONE, false, b -> leave());
			addDrawableChild(leaveButton);
		} else {
			int half = (panelW - 2 * PAD - 8) / 2;
			addDrawableChild(CwUi.Button.gold(panelX + PAD, by, half, BUTTON_H, "Закрыть", CwUi.Icon.CLOSE, false, b -> close()));
			leaveButton = CwUi.Button.gold(panelX + PAD + half + 8, by, half, BUTTON_H, "Покинуть Люмиру", CwUi.Icon.NONE, false, b -> leave());
			addDrawableChild(leaveButton);
		}
	}

	private void addRowButton(Slot slot, int y, int rowH, int choice) {
		int x = wide ? panelX + panelW - PAD - BUTTON_W : panelX + PAD;
		int w = wide ? BUTTON_W : panelW - 2 * PAD;
		int by = wide ? y + (rowH - BUTTON_H) / 2 : y + rowH - 8 - BUTTON_H;
		var button = CwUi.Button.gold(x, by, w, BUTTON_H, death ? "Возродиться" : "Загрузить", CwUi.Icon.PLAY, true, b -> choose(choice));
		button.active = slot.available();
		addDrawableChild(button);
	}

	private void leave() {
		if (client != null) client.setScreen(new CwLeaveConfirmScreen(() -> choose(CHOICE_LEAVE), () -> client.setScreen(this)));
	}

	private void choose(int choice) {
		if (answered) return;
		answered = true;
		var b = PacketByteBufs.create();
		b.writeUuid(nonce);
		b.writeVarInt(choice);
		ClientPlayNetworking.send(ModPackets.CW_RESPAWN_CHOOSE, b);
		if (client != null) client.setScreen(null);
	}

	@Override public boolean shouldCloseOnEsc() { return !death; }
	@Override public boolean shouldPause() { return false; }

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		c.fill(0, 0, width, height, 0x66050709);
		CwUi.fancyPanel(c, panelX, panelY, panelW, panelH);
		CwUi.sprig(c, panelX + 18, panelY + 14);
		CwUi.drawScaled(c, textRenderer, death ? "Вы погибли" : "Сохранения", panelX + 42, panelY + 9, 1.4f, CwUi.TEXT);
		c.drawTextWithShadow(textRenderer, death ? "Куда вернуться?" : "Какое сохранение загрузить?", panelX + 42, panelY + 27, CwUi.TEXT_DIM);
		c.fill(panelX + 16, panelY + 42, panelX + panelW - 16, panelY + 43, CwUi.GOLD_DIM);
		for (int i = 0; i < 2; i++) {
			drawRow(c, slots[i], rowY[i], i);
			if (i > 0) CwUi.divider(c, panelX + panelW / 2, rowY[i], panelW / 2 - 24);
		}
		CwUi.divider(c, panelX + panelW / 2, panelY + panelH - 16 - BUTTON_H - 8, panelW / 2 - 24);
		super.render(c, mx, my, d);
	}

	private void drawRow(DrawContext c, Slot slot, int y, int index) {
		int x = panelX + PAD, ty = y + 8;
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(TITLES[index], textWidth()), x, ty, slot.available() ? CwUi.NPC : CwUi.TEXT_DIM);
		ty += 12;
		if (describe) {
			List<OrderedText> lines = textRenderer.wrapLines(Text.literal(DESCRIPTIONS[index]), Math.max(40, textWidth()));
			for (OrderedText line : lines) { c.drawTextWithShadow(textRenderer, line, x, ty, slot.available() ? CwUi.TEXT_DIM : 0xFF6F6A5E); ty += 11; }
		}
		String status = slot.available()
				? "Время: " + age(slot.ageSeconds()) + (slot.detail().isBlank() ? "" : " • " + slot.detail())
				: reasonText(slot.reason(), index);
		c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, textWidth()), x, ty, slot.available() ? CwUi.TEXT : 0xFFB5766B);
	}

	static String reasonText(String reason, int index) {
		return switch (reason == null ? "" : reason) {
			case "missing" -> index == 0 ? "Автосохранение ещё не создано" : "Ручная точка ещё не установлена";
			case "foreign" -> "Сохранение относится к другой ветке Люмиры";
			case "world" -> "Эта часть Люмиры сейчас недоступна";
			default -> "Сохранение повреждено";
		};
	}

	static String age(long seconds) {
		long s = Math.max(0, seconds);
		if (s < 60) return plural(s, "секунду", "секунды", "секунд") + " назад";
		if (s < 3600) return plural(s / 60, "минуту", "минуты", "минут") + " назад";
		return plural(s / 3600, "час", "часа", "часов") + " назад";
	}

	static String plural(long n, String one, String few, String many) {
		long m10 = n % 10, m100 = n % 100;
		String form = m10 == 1 && m100 != 11 ? one : (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many);
		return n + " " + form;
	}

	public static Slot readSlot(PacketByteBuf b) {
		return b.readBoolean() ? new Slot(true, b.readVarLong(), b.readString(64), "") : new Slot(false, 0, "", b.readString(32));
	}
}
