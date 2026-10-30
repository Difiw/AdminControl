package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Universal NPC dialogue. Opening view: name, text, answer buttons. After the first answer the window turns into a
 * scrollable conversation history. The NPC model is the real entity, head and eyes follow the cursor.
 */
public final class CwDialogueScreen extends Screen {
	public record Line(boolean player, String speaker, String text) {}
	public record Option(String button, String full) {}

	private static final CwUi.Icon[] ICONS = {CwUi.Icon.CHAT, CwUi.Icon.QUESTION, CwUi.Icon.DOTS};
	private static final int FACE = 24, PAD = 14;

	private final UUID nonce;
	private final int sequence, entityId;
	private final String npc, role, current;
	private final List<Line> history;
	private final List<Option> options;
	private int scroll;
	private boolean compact, stickBottom = true;
	private int panelX, panelY, panelW, panelH, listTop, listBottom;

	public CwDialogueScreen(UUID nonce, int sequence, int entityId, String npc, String role, String current, List<Line> history, List<Option> options) {
		super(Text.literal(npc));
		this.nonce = nonce;
		this.sequence = sequence;
		this.entityId = entityId;
		this.npc = npc;
		this.role = role;
		this.current = current;
		this.history = history;
		this.options = options;
	}

	private boolean conversation() { return history.size() > 1; }

	private LivingEntity entity() {
		if (client == null || client.world == null) return null;
		return client.world.getEntityById(entityId) instanceof LivingEntity living ? living : null;
	}

	@Override
	protected void init() {
		clearChildren();
		boolean row = conversation() && !compact && options.size() <= 3 && width >= 520;
		panelW = compact ? Math.min(320, width - 20) : Math.min(430, Math.max(250, width * 9 / 20));
		int buttonH = 22, gap = 4;
		int buttonsH = options.isEmpty() ? 0 : (row ? buttonH : options.size() * (buttonH + gap) - gap);
		if (compact) {
			int text = Math.min(3, CwUi.wrap(textRenderer, current, panelW - 2 * PAD).size()) * 11;
			panelH = 40 + text + 10 + buttonsH + 14;
			panelX = width - panelW - 10;
			panelY = height - panelH - 10;
			listTop = panelY + 40;
			listBottom = listTop + text;
		} else if (!conversation()) {
			int text = CwUi.textHeight(textRenderer, current, panelW - 2 * PAD - 12);
			panelH = 62 + text + 26 + buttonsH + 18;
			panelX = width >= 400 ? width - panelW - Math.max(10, Math.min(width / 14, 60)) : (width - panelW) / 2;
			panelY = Math.max(8, (height - panelH) / 2);
			listTop = panelY + 58;
			listBottom = listTop + text;
		} else {
			panelH = Math.min(height - 24, 430);
			panelX = width >= 400 ? width - panelW - Math.max(10, Math.min(width / 14, 60)) : (width - panelW) / 2;
			panelY = Math.max(8, (height - panelH) / 2);
			listTop = panelY + 56;
			listBottom = panelY + panelH - 18 - buttonsH - 10;
		}
		int bx = panelX + PAD, bw = panelW - 2 * PAD;
		int by = panelY + panelH - 14 - buttonsH;
		if (row) {
			int each = (bw - gap * (options.size() - 1)) / options.size();
			for (int i = 0; i < options.size(); i++) addOption(i, bx + i * (each + gap), by, each, buttonH);
		} else {
			for (int i = 0; i < options.size(); i++) addOption(i, bx, by + i * (buttonH + gap), bw, buttonH);
		}
		addDrawableChild(CwUi.Button.gold(panelX + panelW - 30, panelY + 10, 20, 20, "", CwUi.Icon.CLOSE, false, b -> close()));
		if (conversation() || compact)
			addDrawableChild(CwUi.Button.gold(panelX + panelW - 126, panelY + 10, 92, 20, compact ? "Развернуть" : "Свернуть", CwUi.Icon.NONE, false, b -> { compact = !compact; init(); }));
		if (stickBottom) scroll = Integer.MAX_VALUE;
	}

	private void addOption(int i, int x, int y, int w, int h) {
		final int index = i;
		addDrawableChild(CwUi.Button.gold(x, y, w, h, options.get(i).button(), ICONS[i % ICONS.length], i == 0, b -> choose(index)));
	}

	private void choose(int index) {
		var b = PacketByteBufs.create();
		b.writeUuid(nonce);
		b.writeVarInt(sequence);
		b.writeVarInt(index);
		ClientPlayNetworking.send(ModPackets.CW_DIALOGUE_CHOOSE, b);
	}

	@Override
	public void close() {
		var b = PacketByteBufs.create();
		b.writeUuid(nonce);
		b.writeVarInt(sequence);
		b.writeVarInt(-1);
		ClientPlayNetworking.send(ModPackets.CW_DIALOGUE_CHOOSE, b);
		super.close();
	}

	private int historyHeight() {
		int h = 0;
		for (Line l : history) h += 16 + CwUi.wrap(textRenderer, l.text(), panelW - 2 * PAD - FACE - 22).size() * 11 + 8;
		return h;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		if (!conversation() || compact) return false;
		int max = Math.max(0, historyHeight() - (listBottom - listTop));
		int base = scroll == Integer.MAX_VALUE ? max : scroll;
		scroll = Math.max(0, Math.min(max, base - (int) Math.signum(amount) * 22));
		stickBottom = scroll >= max;
		return true;
	}

	@Override public boolean shouldPause() { return false; }

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, compact ? 0x22000000 : CwUi.SHADE);
		if (!compact) CwUi.drawNpc(c, entity(), 0, 0, Math.min(width, panelX + 24), height, mx, my);
		CwUi.fancyPanel(c, panelX, panelY, panelW, panelH);
		CwUi.sprig(c, panelX + 16, panelY + 11);
		CwUi.drawScaled(c, textRenderer, npc, panelX + 40, panelY + 10, 1.4f, CwUi.TEXT);
		if (!role.isBlank()) c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(role, panelW - 150), panelX + 40, panelY + 27, CwUi.TEXT_DIM);
		c.fill(panelX + PAD, panelY + 43, panelX + panelW - PAD, panelY + 44, CwUi.GOLD_DIM);
		if (compact) {
			var lines = CwUi.wrap(textRenderer, current, panelW - 2 * PAD);
			for (int i = 0; i < Math.min(3, lines.size()); i++) c.drawTextWithShadow(textRenderer, lines.get(i), panelX + PAD, listTop + i * 11, CwUi.TEXT);
		} else if (!conversation()) {
			CwUi.drawWrapped(c, textRenderer, current, panelX + PAD + 6, panelY + 58, panelW - 2 * PAD - 12, CwUi.TEXT);
			CwUi.divider(c, panelX + panelW / 2, listBottom + 12, panelW / 2 - 30);
		} else {
			renderHistory(c);
		}
		super.render(c, mx, my, delta);
	}

	private void renderHistory(DrawContext c) {
		int max = Math.max(0, historyHeight() - (listBottom - listTop));
		if (scroll == Integer.MAX_VALUE || stickBottom) scroll = max;
		scroll = Math.max(0, Math.min(max, scroll));
		LivingEntity npcEntity = entity();
		MinecraftClient mc = MinecraftClient.getInstance();
		c.enableScissor(panelX + 6, listTop, panelX + panelW - 6, listBottom);
		int y = listTop - scroll;
		int textX = panelX + PAD + FACE + 10, textW = panelW - 2 * PAD - FACE - 22;
		for (Line l : history) {
			var lines = CwUi.wrap(textRenderer, l.text(), textW);
			int h = 16 + lines.size() * 11 + 8;
			if (y + h > listTop && y < listBottom) {
				if (l.player()) c.fill(panelX + 8, y - 3, panelX + panelW - 16, y + h - 4, 0x33324B66);
				CwUi.face(c, l.player() ? mc.player : npcEntity, panelX + PAD, y, FACE);
				c.drawTextWithShadow(textRenderer, l.speaker(), textX, y, l.player() ? CwUi.PLAYER : CwUi.NPC);
				int ty = y + 12;
				for (var line : lines) { c.drawTextWithShadow(textRenderer, line, textX, ty, CwUi.TEXT); ty += 11; }
			}
			y += h;
		}
		c.disableScissor();
		if (max > 0) {
			int trackH = listBottom - listTop, thumbH = Math.max(18, trackH * trackH / (trackH + max));
			int thumbY = listTop + (trackH - thumbH) * scroll / max;
			c.fill(panelX + panelW - 12, listTop, panelX + panelW - 9, listBottom, 0x55000000);
			c.fill(panelX + panelW - 12, thumbY, panelX + panelW - 9, thumbY + thumbH, CwUi.GOLD_DIM);
		}
	}

	public static void receive(MinecraftClient client, net.minecraft.network.PacketByteBuf b) {
		UUID nonce = b.readUuid();
		if (nonce.getMostSignificantBits() == 0 && nonce.getLeastSignificantBits() == 0) {
			client.execute(() -> { if (client.currentScreen instanceof CwDialogueScreen) client.setScreen(null); });
			return;
		}
		int seq = b.readVarInt(), entity = b.readVarInt();
		String npc = b.readString(64), role = b.readString(128), current = b.readString(4096);
		int hn = Math.min(100, b.readVarInt());
		List<Line> history = new ArrayList<>();
		for (int i = 0; i < hn; i++) history.add(new Line(b.readBoolean(), b.readString(64), b.readString(4096)));
		int on = Math.min(12, b.readVarInt());
		List<Option> options = new ArrayList<>();
		for (int i = 0; i < on; i++) options.add(new Option(b.readString(256), b.readString(4096)));
		client.execute(() -> {
			boolean wasCompact = client.currentScreen instanceof CwDialogueScreen old && old.compact;
			var screen = new CwDialogueScreen(nonce, seq, entity, npc, role, current, List.copyOf(history), List.copyOf(options));
			screen.compact = wasCompact;
			client.setScreen(screen);
		});
	}
}
