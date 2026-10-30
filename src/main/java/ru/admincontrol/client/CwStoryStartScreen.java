package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;

import java.util.UUID;

/**
 * Louis' prompt. Three states chosen by the server:
 * A - unavailable (only "Закрыть"), B - first launch ("Начать"/"Закрыть"), C - progress exists ("Продолжить"/"Закрыть").
 * Admin test mode keeps its extra "Начать заново" button.
 */
public final class CwStoryStartScreen extends Screen {
	private static final net.minecraft.util.Identifier BACKGROUND = new net.minecraft.util.Identifier("admin_control", "textures/gui/luya_bg.png");
	private static final int BG_W = 1672, BG_H = 941;
	private final UUID nonce;
	private final String npcName, description;
	private final boolean hasProgress, tester, canEnter;
	private boolean answered;
	private int panelX, panelY, panelW, panelH;
	private LivingEntity npc;
	private final int npcEntityId;

	public CwStoryStartScreen(UUID nonce, String title, String description, boolean hasProgress, boolean tester, boolean canEnter, int npcEntityId) {
		super(Text.literal(title));
		this.npcEntityId = npcEntityId;
		this.nonce = nonce;
		this.npcName = title.contains("—") ? title.substring(0, title.indexOf('—')).trim() : title;
		this.description = stripSpeaker(description);
		this.hasProgress = hasProgress;
		this.tester = tester;
		this.canEnter = canEnter;
	}

	private static String stripSpeaker(String text) {
		if (text == null) return "";
		int quote = text.indexOf("«");
		if (text.startsWith("Луи:") && quote >= 0 && text.endsWith("»")) return text.substring(quote + 1, text.length() - 1);
		return text;
	}

	@Override
	protected void init() {
		npc = client != null && client.world != null && client.world.getEntityById(npcEntityId) instanceof LivingEntity e ? e : CwUi.nearestNpc(client);
		panelW = Math.min(380, Math.max(250, width * 2 / 5));
		panelX = width >= 400 ? width - panelW - Math.max(10, Math.min(width / 12, 60)) : (width - panelW) / 2;
		int inner = panelW - 56;
		int textH = CwUi.textHeight(textRenderer, description, inner);
		int buttons = tester && hasProgress && canEnter ? 3 : (canEnter ? 2 : 1);
		int rows = canEnter ? buttons : 1;
		panelH = 64 + textH + 26 + rows * 26 + 14;
		panelY = Math.max(8, (height - panelH) / 2);
		if (hasProgress == false || !canEnter) addDrawableChild(CwUi.Button.gold(panelX + panelW - 26, panelY + 8, 16, 16, "", CwUi.Icon.CLOSE, false, b -> answer(0)));
		int bx = panelX + 28, bw = panelW - 56, by = panelY + panelH - 14 - rows * 26 + 4;
		if (!canEnter) {
			int w = Math.min(bw, 150);
			addDrawableChild(CwUi.Button.gold(panelX + (panelW - w) / 2, by, w, 22, "Закрыть", CwUi.Icon.CLOSE, false, b -> answer(0)));
		} else if (!hasProgress) {
			addDrawableChild(CwUi.Button.gold(bx, by, bw, 22, "Начать", CwUi.Icon.CHAT, true, b -> answer(1)));
			addDrawableChild(CwUi.Button.gold(bx, by + 26, bw, 22, "Закрыть", CwUi.Icon.CLOSE, false, b -> answer(0)));
		} else {
			addDrawableChild(CwUi.Button.gold(bx, by, bw, 22, "Продолжить", CwUi.Icon.PLAY, true, b -> answer(2)));
			int next = by + 26;
			if (tester) {
				addDrawableChild(CwUi.Button.gold(bx, next, bw, 22, "Начать заново", CwUi.Icon.CHAT, false, b -> answer(1)));
				next += 26;
			}
			addDrawableChild(CwUi.Button.gold(bx, next, bw, 22, "Закрыть", CwUi.Icon.CLOSE, false, b -> answer(0)));
		}
	}

	private void answer(int action) {
		if (answered) return;
		answered = true;
		var buf = PacketByteBufs.create();
		buf.writeUuid(nonce);
		buf.writeVarInt(action);
		ClientPlayNetworking.send(tester ? ModPackets.CW_TEST_START : ModPackets.CW_STORY_START, buf);
		if (client != null) client.setScreen(null);
	}

	/** Louis' own painted scene, scaled to cover the whole screen with the character kept on the left. */
	private void drawBackground(DrawContext c) {
		double scale = Math.max(width / (double) BG_W, height / (double) BG_H);
		int w = (int) Math.ceil(BG_W * scale), h = (int) Math.ceil(BG_H * scale);
		int x = Math.min(0, (int) ((width - w) * 0.25)), y = (height - h) / 2;
		c.drawTexture(BACKGROUND, x, y, w, h, 0, 0, BG_W, BG_H, BG_W, BG_H);
		c.fill(0, 0, width, height, 0x33000000);
	}

	@Override public void close() { answer(0); }
	@Override public boolean shouldPause() { return false; }

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		drawBackground(c);
		CwUi.fancyPanel(c, panelX, panelY, panelW, panelH);
		CwUi.flourish(c, panelX + panelW - 4, panelY + panelH - 4);
		CwUi.sprig(c, panelX + 22, panelY + 17);
		CwUi.drawScaled(c, textRenderer, npcName, panelX + 46, panelY + 18, 1.6f, CwUi.TEXT);
		c.fill(panelX + 24, panelY + 44, panelX + panelW - 24, panelY + 45, CwUi.GOLD_DIM);
		CwUi.drawWrapped(c, textRenderer, description, panelX + 28, panelY + 58, panelW - 56, CwUi.TEXT);
		CwUi.divider(c, panelX + panelW / 2, panelY + panelH - 14 - (canEnter ? (tester && hasProgress ? 3 : 2) : 1) * 26 - 8, panelW / 2 - 36);
		super.render(c, mouseX, mouseY, delta);
	}
}
