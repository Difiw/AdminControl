package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.network.PacketByteBuf;

/**
 * Themed fade used for every switch into, out of and inside Люмира. It is drawn over the HUD and over every screen,
 * which also hides the vanilla "loading terrain" screen: the overlay stays fully opaque while that screen is open and
 * fades out only afterwards.
 */
public final class CwTransitionOverlay {
	private static final long FADE_IN_MS = 350, MIN_HOLD_MS = 1100, FADE_OUT_MS = 800, TIMEOUT_MS = 9000;
	private static long beginAt = -1, fadeOutAt = -1;
	private static boolean endRequested;
	private static String title = "", subtitle = "";

	private CwTransitionOverlay() {}

	public static void receive(MinecraftClient client, PacketByteBuf b) {
		boolean begin = b.readBoolean();
		String t = b.readString(48), s = b.readString(96);
		client.execute(() -> {
			if (begin) { beginAt = System.currentTimeMillis(); fadeOutAt = -1; endRequested = false; title = t; subtitle = s; }
			else endRequested = true;
		});
	}

	public static boolean active() { return beginAt >= 0; }

	/** Клиентский переход без сообщения сервера: затемнение держится, пока открыт экран загрузки местности. */
	public static void beginLocal() {
		if (beginAt >= 0) return;
		beginAt = System.currentTimeMillis();
		fadeOutAt = -1;
		endRequested = true;
		title = "";
		subtitle = "";
	}

	public static void reset() { beginAt = -1; fadeOutAt = -1; endRequested = false; }

	private static float alpha(long now) {
		if (beginAt < 0) return 0f;
		if (fadeOutAt >= 0) {
			float a = 1f - (now - fadeOutAt) / (float) FADE_OUT_MS;
			if (a <= 0f) { reset(); return 0f; }
			return a;
		}
		boolean terrain = MinecraftClient.getInstance().currentScreen instanceof DownloadingTerrainScreen;
		if ((endRequested && now - beginAt >= MIN_HOLD_MS && !terrain) || now - beginAt > TIMEOUT_MS) { fadeOutAt = now; return 1f; }
		return Math.min(1f, (now - beginAt) / (float) FADE_IN_MS);
	}

	public static void render(DrawContext c, int width, int height) {
		long now = System.currentTimeMillis();
		float a = alpha(now);
		if (a <= 0f) return;
		float eased = a * a * (3f - 2f * a);
		int alpha = Math.min(255, (int) (eased * 255));
		c.fill(0, 0, width, height, (alpha << 24) | 0x050709);
		int textAlpha = Math.min(255, (int) (eased * eased * 255));
		if (textAlpha <= 8) return;
		var tr = MinecraftClient.getInstance().textRenderer;
		int cx = width / 2, cy = height / 2;
		double t = now / 1000.0;
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4);
		int unit = Math.min(width, height);

		// Soft golden glow: stacked translucent frames that breathe with the pulse.
		for (int i = 0; i < 6; i++) {
			int r = (int) (unit * (0.10 + 0.06 * i) * (0.92 + 0.08 * pulse));
			int g = (int) (textAlpha * (0.05 - 0.007 * i) * (0.6 + 0.4 * pulse));
			if (g > 0) c.fill(cx - r, cy - (int) (r * 0.62), cx + r, cy + (int) (r * 0.62), (g << 24) | 0xC9A66B);
		}
		// Two counter-rotating rune rings.
		for (int ring = 0; ring < 2; ring++) {
			double radius = unit * (ring == 0 ? 0.30 : 0.21), dir = ring == 0 ? 1 : -1.3;
			int dots = ring == 0 ? 48 : 32;
			for (int i = 0; i < dots; i++) {
				double q = i * Math.PI * 2 / dots + t * 0.55 * dir;
				int x = cx + (int) Math.round(Math.cos(q) * radius), y = cy + (int) Math.round(Math.sin(q) * radius * 0.62);
				boolean rune = i % 4 == 0;
				int da = (int) (textAlpha * (rune ? 0.95f : 0.45f) * (0.7f + 0.3f * (float) Math.sin(t * 3 + i)));
				c.fill(x, y, x + (rune ? 3 : 2), y + (rune ? 3 : 2), (Math.max(0, da) << 24) | (rune ? 0xE8C98F : 0xC9A66B));
			}
		}
		// Motes spiralling upward.
		for (int i = 0; i < 28; i++) {
			double life = (t * 0.35 + i / 28.0) % 1.0, q = i * 2.399 + t * 0.9, rad = unit * 0.28 * (1 - life);
			int x = cx + (int) Math.round(Math.cos(q) * rad), y = cy + (int) (unit * 0.20) - (int) (life * unit * 0.45) + (int) (Math.sin(q) * rad * 0.2);
			int sa = (int) (textAlpha * Math.sin(life * Math.PI) * 0.85);
			c.fill(x, y, x + 2, y + 2, (Math.max(0, sa) << 24) | 0xF3DFAE);
		}
		int half = (int) (Math.min(width / 3, 110) * Math.min(1f, (now - Math.max(beginAt, 0)) / 900f));
		int gold = (textAlpha << 24) | 0xC9A66B, dim = (textAlpha << 24) | 0x9C9686;
		CwUi.divider(c, cx, cy + 14, Math.max(8, half));
		c.getMatrices().push();
		c.getMatrices().translate(cx, cy - 18, 0);
		c.getMatrices().scale(2.2f, 2.2f, 1f);
		c.drawCenteredTextWithShadow(tr, title, 0, 0, gold);
		c.getMatrices().pop();
		if (!subtitle.isEmpty()) c.drawCenteredTextWithShadow(tr, subtitle, cx, cy + 24, dim);
	}
}
