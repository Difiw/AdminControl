package ru.admincontrol.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import ru.admincontrol.lumira.client.LumiraHud;

import java.util.ArrayList;
import java.util.List;

/**
 * Story chat: one compact translucent block with a thin purple ornamental frame, centred above the hotbar and shown
 * in the build, test and play story worlds only. It replaces the vanilla chat there (messages are captured instead of
 * being added to the left-hand chat, so nothing is shown twice) and reads the vanilla chat options: text/background
 * opacity, scale, width, focused height and visibility. At most {@link #MAX_VISIBLE} lines are visible while the chat is
 * closed; opening the chat shows the history and the mouse wheel scrolls it.
 */
@Environment(EnvType.CLIENT)
public final class CwStoryChat {
	public static final int MAX_VISIBLE = 5;
	private static final int MAX_MESSAGES = 300;
	private static final int FADE_TICKS = 200;
	private static final int LINE_H = 9;
	private static final int PAD_X = 6, PAD_Y = 4;
	private static final int FRAME = 0xB77CFF;

	private record Message(Text text, long tick) {}
	private record Row(OrderedText text, long tick) {}
	private record Hit(int x1, int y1, int x2, int y2, OrderedText text) {}

	private static final List<Message> MESSAGES = new ArrayList<>();
	private static final List<Hit> HITS = new ArrayList<>();
	private static int scrollLines;
	private static float shownHeight = -1f;
	private static float slide;
	private static int lastRowCount;
	private static long lastCountTick = -1;

	private CwStoryChat() {}

	/** The story chat replaces the vanilla chat inside the story worlds (build, test and play) only. */
	public static boolean active() {
		return LumiraHud.inCwWorld();
	}

	public static synchronized void add(Text text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = mc.inGameHud == null ? 0 : mc.inGameHud.getTicks();
		MESSAGES.add(new Message(text, now));
		while (MESSAGES.size() > MAX_MESSAGES) MESSAGES.remove(0);
		if (scrollLines > 0) scrollLines++; // keep the position of the history the player is reading
	}

	public static synchronized void clear() {
		MESSAGES.clear();
		HITS.clear();
		scrollLines = 0;
		shownHeight = -1f;
		lastRowCount = 0;
	}

	public static synchronized void scroll(int amount) {
		scrollLines = Math.max(0, scrollLines + (int) Math.signum(amount) * 3);
	}

	public static synchronized void resetScroll() { scrollLines = 0; }

	/** Vanilla chat fade: fully visible for the first 100 ticks, then a short squared fade. */
	static double fade(long age) {
		double d = 1.0 - (double) age / FADE_TICKS;
		d = MathHelper.clamp(d * 10.0, 0.0, 1.0);
		return d * d;
	}

	/** Pure layout rule: both focused and closed heights follow the corresponding vanilla chat setting. */
	static int visibleRows(int available, boolean focused, int configuredRows) {
		return Math.min(available, Math.max(1, configuredRows));
	}

	public static synchronized void render(DrawContext context, int currentTick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		HITS.clear();
		if (mc.options.getChatVisibility().getValue() == net.minecraft.client.option.ChatVisibility.HIDDEN) return;
		boolean focused = mc.currentScreen instanceof ChatScreen;
		TextRenderer tr = mc.textRenderer;
		double scale = mc.options.getChatScale().getValue();
		double textOpacity = mc.options.getChatOpacity().getValue() * 0.9 + 0.1;
		double bgOpacity = mc.options.getTextBackgroundOpacity().getValue();
		int screenW = context.getScaledWindowWidth(), screenH = context.getScaledWindowHeight();
		int wrapW = MathHelper.floor(ChatHud.getWidth(mc.options.getChatWidth().getValue()) / scale);
		wrapW = Math.max(60, Math.min(wrapW, (int) ((screenW - 24) / scale) - 2 * PAD_X));

		// wrapped rows, oldest first
		List<Row> rows = new ArrayList<>();
		for (Message m : MESSAGES) {
			for (OrderedText line : ChatMessages.breakRenderedChatMessageLines(m.text(), wrapW, tr)) rows.add(new Row(line, m.tick()));
		}
		int configuredRows = Math.max(1, (int) (ChatHud.getHeight((focused
				? mc.options.getChatHeightFocused().getValue()
				: mc.options.getChatHeightUnfocused().getValue())) / (double) LINE_H));
		List<Row> shown = new ArrayList<>();
		if (focused) {
			int end = Math.max(0, rows.size() - scrollLines);
			scrollLines = Math.min(scrollLines, Math.max(0, rows.size() - visibleRows(rows.size(), true, configuredRows)));
			end = Math.max(0, rows.size() - scrollLines);
			int begin = Math.max(0, end - visibleRows(rows.size(), true, configuredRows));
			shown.addAll(rows.subList(begin, end));
		} else {
			scrollLines = 0;
			// the sixth row is kept while it slides out so the oldest line is pushed up smoothly instead of vanishing
			int from = Math.max(0, rows.size() - (configuredRows + 1));
			for (Row r : rows.subList(from, rows.size())) if (fade(currentTick - r.tick()) > 0.0) shown.add(r);
		}
		boolean overflow = !focused && shown.size() > configuredRows;
		int count = Math.min(shown.size(), configuredRows);
		if (shown.isEmpty()) { shownHeight = -1f; lastRowCount = 0; return; }

		if (!focused && lastCountTick != currentTick) {
			if (rows.size() > lastRowCount && lastRowCount > 0) slide = LINE_H * Math.min(3, rows.size() - lastRowCount);
			lastRowCount = rows.size();
			lastCountTick = currentTick;
		}
		slide = Math.max(0f, slide - mc.getLastFrameDuration() * 2.2f);

		float targetH = count * LINE_H + 2 * PAD_Y;
		shownHeight = shownHeight < 0 ? targetH : shownHeight + (targetH - shownHeight) * Math.min(1f, mc.getLastFrameDuration() * 0.5f);
		int boxH = Math.round(shownHeight);

		int widest = 0;
		for (Row r : shown) widest = Math.max(widest, tr.getWidth(r.text()));
		int boxW = Math.max(120, Math.min(wrapW, widest) + 2 * PAD_X);
		boolean heldItem = mc.player != null && (!mc.player.getMainHandStack().isEmpty() || !mc.player.getOffHandStack().isEmpty());
		// The custom hotbar occupies the same bottom area as vanilla. Keep the chat clear of it and reserve another
		// line whenever an item tooltip can be shown, avoiding the overlap visible in the original HUD.
		// Keep the message close to the item-name line without touching the hotbar.
		// The previous 58px reserve made even a one-line message float near the middle of the screen.
		int bottom = screenH - 34 - (heldItem ? 12 : 0) - (mc.interactionManager != null && mc.interactionManager.hasExperienceBar() ? 14 : 0);

		context.getMatrices().push();
		context.getMatrices().translate(screenW / 2f, bottom, 0f);
		context.getMatrices().scale((float) scale, (float) scale, 1f);
		int x0 = -boxW / 2, y1 = 0, y0 = -boxH;
		double newest = 1.0;
		if (!focused) {
			newest = 0.0;
			for (Row r : shown) newest = Math.max(newest, fade(currentTick - r.tick()));
		}
		int bgA = (int) (255.0 * bgOpacity * newest);
		if (bgA > 3) context.fill(x0, y0, x0 + boxW, y1, bgA << 24);
		int frameA = (int) (255.0 * MathHelper.clamp(bgOpacity * 1.6, 0.0, 1.0) * newest);
		if (frameA > 3) drawFrame(context, x0, y0, boxW, boxH, frameA << 24 | FRAME);

		context.enableScissor(
				(int) Math.floor(screenW / 2.0 + x0 * scale), (int) Math.floor(bottom - boxH * scale),
				(int) Math.ceil(screenW / 2.0 + (x0 + boxW) * scale), bottom);
		int y = y1 - PAD_Y - LINE_H;
		for (int i = shown.size() - 1; i >= 0; i--) {
			Row r = shown.get(i);
			int fromEnd = shown.size() - 1 - i;
			float ry = y - fromEnd * LINE_H + (focused ? 0 : slide);
			double a = focused ? 1.0 : fade(currentTick - r.tick());
			if (overflow && fromEnd == configuredRows) a *= Math.max(0.0, 1.0 - slide / LINE_H); // the line being pushed out
			int alpha = (int) (255.0 * textOpacity * a);
			if (alpha > 3) {
				context.drawTextWithShadow(tr, r.text(), x0 + PAD_X, Math.round(ry), 0xFFFFFF | alpha << 24);
				HITS.add(new Hit((int) (screenW / 2.0 + (x0 + PAD_X) * scale), (int) (bottom + ry * scale),
						(int) (screenW / 2.0 + (x0 + PAD_X + tr.getWidth(r.text())) * scale), (int) (bottom + (ry + LINE_H) * scale), r.text()));
			}
		}
		context.disableScissor();
		context.getMatrices().pop();
	}

	/** Thin frame with a diamond in every corner and in the middle of the top and bottom edges. */
	private static void drawFrame(DrawContext c, int x, int y, int w, int h, int argb) {
		c.fill(x, y, x + w, y + 1, argb);
		c.fill(x, y + h - 1, x + w, y + h, argb);
		c.fill(x, y, x + 1, y + h, argb);
		c.fill(x + w - 1, y, x + w, y + h, argb);
		int[][] marks = {{x, y}, {x + w - 1, y}, {x, y + h - 1}, {x + w - 1, y + h - 1}, {x + w / 2, y}, {x + w / 2, y + h - 1}};
		for (int i = 0; i < marks.length; i++) diamond(c, marks[i][0], marks[i][1], i >= 4 ? 3 : 2, argb);
	}

	private static void diamond(DrawContext c, int cx, int cy, int r, int argb) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			c.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
		}
	}

	/** Click events and hover styles of the story chat lines (vanilla looks them up in its own, empty, list). */
	public static synchronized Style styleAt(double mouseX, double mouseY) {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Hit h : HITS) {
			if (mouseX >= h.x1() && mouseX < h.x2() && mouseY >= h.y1() && mouseY < h.y2()) {
				double scale = mc.options.getChatScale().getValue();
				return mc.textRenderer.getTextHandler().getStyleAt(h.text(), (int) ((mouseX - h.x1()) / scale));
			}
		}
		return null;
	}
}
