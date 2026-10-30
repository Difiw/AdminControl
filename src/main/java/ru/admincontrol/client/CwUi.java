package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Shared dark-fantasy look for every CW screen: NPC dialogues, Louis' prompt and the story tablet. */
public final class CwUi {
	public static final int SHADE = 0x88120B05;
	public static final int PANEL = 0xF2130F0C;
	public static final int PANEL_SOFT = 0xCC141922;
	public static final int GOLD = 0xFFC9A66B;
	public static final int GOLD_DIM = 0xFF6E5A3A;
	public static final int GOLD_FILL = 0xFFD9C394;
	public static final int TEXT = 0xFFEAE3D4;
	public static final int TEXT_DIM = 0xFF9C9686;
	public static final int TEXT_DARK = 0xFF1B1710;
	public static final int PLAYER = 0xFF8FC8FF;
	public static final int NPC = 0xFFE8C98F;
	public static final int BLUE = 0xFF3C8DFF;
	public static final int BLUE_DIM = 0xFF1F4F8F;
	public static final int BLUE_PANEL = 0xE6091019;
	public static final int BLUE_PANEL_SOFT = 0xCC101A28;

	public enum Icon { NONE, CHAT, QUESTION, DOTS, CLOSE, PLAY, PLUS, GEAR }

	private CwUi() {}

	public static void border(DrawContext c, int x, int y, int w, int h, int color) {
		c.fill(x, y, x + w, y + 1, color);
		c.fill(x, y + h - 1, x + w, y + h, color);
		c.fill(x, y, x + 1, y + h, color);
		c.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Gold framed dialog panel with L-shaped corner ornaments, as on the dialogue references. */
	public static void fancyPanel(DrawContext c, int x, int y, int w, int h) {
		c.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
		c.fill(x, y, x + w, y + h, PANEL);
		border(c, x, y, w, h, GOLD_DIM);
		border(c, x + 3, y + 3, w - 6, h - 6, 0x553D3222);
		corner(c, x - 1, y - 1, 1, 1, GOLD);
		corner(c, x + w + 1, y - 1, -1, 1, GOLD);
		corner(c, x - 1, y + h + 1, 1, -1, GOLD);
		corner(c, x + w + 1, y + h + 1, -1, -1, GOLD);
	}

	private static void corner(DrawContext c, int x, int y, int dx, int dy, int color) {
		for (int i = 0; i < 12; i++) {
			c.fill(Math.min(x, x + dx * (i + 1)), y, Math.max(x, x + dx * (i + 1)), y + dy, color);
			c.fill(x, Math.min(y, y + dy * (i + 1)), x + dx, Math.max(y, y + dy * (i + 1)), color);
		}
	}

	/** Blue tablet panel with glow border. */
	public static void tabletPanel(DrawContext c, int x, int y, int w, int h, boolean accent) {
		c.fill(x, y, x + w, y + h, BLUE_PANEL);
		border(c, x, y, w, h, accent ? BLUE : BLUE_DIM);
		if (accent) border(c, x - 1, y - 1, w + 2, h + 2, 0x553C8DFF);
	}

	public static void divider(DrawContext c, int centerX, int y, int halfWidth) {
		c.fill(centerX - halfWidth, y, centerX - 6, y + 1, GOLD_DIM);
		c.fill(centerX + 6, y, centerX + halfWidth, y + 1, GOLD_DIM);
		c.fill(centerX - 1, y - 2, centerX + 1, y + 3, GOLD);
		c.fill(centerX - 2, y - 1, centerX + 2, y + 2, GOLD);
	}

	/** Leaf-sprig emblem in front of NPC names: a curved stem with leaves on both sides. */
	public static void sprig(DrawContext c, int x, int y) {
		for (int i = 0; i < 16; i++) c.fill(x + i, y + 16 - i, x + i + 1, y + 17 - i, GOLD);
		for (int t : new int[]{4, 7, 10}) {
			// stem point (t, 16 - t); leaf up-left and leaf to the right
			for (int k = 1; k <= 4; k++) {
				c.fill(x + t - k, y + 16 - t - k / 2, x + t - k + 1, y + 17 - t - k / 2, GOLD_DIM);
				c.fill(x + t + k, y + 16 - t + k / 2, x + t + k + 1, y + 17 - t + k / 2, GOLD_DIM);
			}
		}
	}

	/** Tiny corner flourish used at the bottom-right of dialog frames. */
	public static void flourish(DrawContext c, int right, int bottom) {
		for (int i = 0; i < 22; i++) c.fill(right - 4 - i, bottom - 5 - i / 3, right - 3 - i, bottom - 4 - i / 3, GOLD_DIM);
		for (int i = 0; i < 12; i++) c.fill(right - 5 - i / 3, bottom - 4 - i, right - 4 - i / 3, bottom - 3 - i, GOLD_DIM);
		for (int i = 0; i < 4; i++) c.fill(right - 14 - i, bottom - 11 - i, right - 13 - i, bottom - 10 - i, GOLD);
	}

	public static List<OrderedText> wrap(TextRenderer tr, String text, int width) {
		List<OrderedText> lines = new ArrayList<>();
		if (text == null) return lines;
		for (String paragraph : text.split("\n", -1)) {
			if (paragraph.isEmpty()) { lines.add(OrderedText.EMPTY); continue; }
			lines.addAll(tr.wrapLines(Text.literal(paragraph), Math.max(20, width)));
		}
		return lines;
	}

	public static int textHeight(TextRenderer tr, String text, int width) {
		return wrap(tr, text, width).size() * 11;
	}

	public static int drawWrapped(DrawContext c, TextRenderer tr, String text, int x, int y, int width, int color) {
		for (OrderedText line : wrap(tr, text, width)) { c.drawTextWithShadow(tr, line, x, y, color); y += 11; }
		return y;
	}

	public static void drawScaled(DrawContext c, TextRenderer tr, String text, int x, int y, float scale, int color) {
		c.getMatrices().push();
		c.getMatrices().translate(x, y, 0);
		c.getMatrices().scale(scale, scale, 1f);
		c.drawTextWithShadow(tr, text, 0, 0, color);
		c.getMatrices().pop();
	}

	/** Nearest NPC-like player entity (story NPCs are fake players), otherwise null. */
	public static LivingEntity nearestNpc(MinecraftClient client) {
		if (client == null || client.world == null || client.player == null) return null;
		LivingEntity best = null;
		double bestDistance = 24 * 24;
		for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
			if (other == client.player) continue;
			double d = other.squaredDistanceTo(client.player);
			if (d < bestDistance) { bestDistance = d; best = other; }
		}
		return best;
	}

	/**
	 * Draws a live 3D bust inside the rectangle. The offsets handed to the vanilla renderer are clamped so the
	 * head/eyes follow the cursor smoothly but never twist past a comfortable angle.
	 */
	public static void drawNpc(DrawContext c, LivingEntity entity, int left, int top, int right, int bottom, int mouseX, int mouseY) {
		if (entity == null || right - left < 40 || bottom - top < 40) return;
		int h = bottom - top;
		int size = Math.max(40, (int) (h * 0.62f));
		int cx = (left + right) / 2;
		int feet = top + (int) (size * 1.95f) + 6;
		float fx = clamp(cx - mouseX, -(right - left) * 0.6f, (right - left) * 0.6f);
		float fy = clamp(top + h * 0.3f - mouseY, -h * 0.35f, h * 0.35f);
		c.enableScissor(left, top, right, bottom);
		try {
			InventoryScreen.drawEntity(c, cx, feet, size, fx, fy, entity);
		} catch (Exception ignored) {
		}
		c.disableScissor();
	}

	private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

	/** NPC / player head from the live skin texture, falling back to a plain tile. */
	public static void face(DrawContext c, LivingEntity entity, int x, int y, int size) {
		c.fill(x - 1, y - 1, x + size + 1, y + size + 1, GOLD_DIM);
		Identifier tex = null;
		if (entity instanceof AbstractClientPlayerEntity p) tex = p.getSkinTexture();
		if (tex == null) { c.fill(x, y, x + size, y + size, 0xFF2A2E38); return; }
		c.drawTexture(tex, x, y, size, size, 8, 8, 8, 8, 64, 64);
		c.drawTexture(tex, x, y, size, size, 40, 8, 8, 8, 64, 64);
	}

	public static void icon(DrawContext c, TextRenderer tr, Icon icon, int cx, int cy, int color) {
		switch (icon) {
			case CHAT -> {
				c.fill(cx - 5, cy - 4, cx + 6, cy + 3, color);
				c.fill(cx - 4, cy + 3, cx - 1, cy + 5, color);
				c.fill(cx - 3, cy - 1, cx - 2, cy, TEXT_DARK);
				c.fill(cx, cy - 1, cx + 1, cy, TEXT_DARK);
				c.fill(cx + 3, cy - 1, cx + 4, cy, TEXT_DARK);
			}
			case QUESTION -> c.drawCenteredTextWithShadow(tr, "?", cx, cy - 4, color);
			case DOTS -> c.drawCenteredTextWithShadow(tr, "...", cx, cy - 4, color);
			case CLOSE -> {
				for (int i = -3; i <= 3; i++) {
					c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
					c.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
				}
			}
			case PLAY -> {
				for (int i = 0; i < 5; i++) c.fill(cx - 3 + i, cy - 5 + i, cx - 2 + i, cy + 5 - i, color);
			}
			case PLUS -> {
				c.fill(cx - 4, cy, cx + 5, cy + 1, color);
				c.fill(cx, cy - 4, cx + 1, cy + 5, color);
			}
			case GEAR -> {
				c.fill(cx - 4, cy - 4, cx + 5, cy + 5, color);
				c.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF101822);
			}
			default -> {
			}
		}
	}

	/** Dialogue / tablet button with normal, hover, focus and disabled states. */
	public static final class Button extends ButtonWidget {
		private final Icon icon;
		private final boolean primary, tablet;

		public Button(int x, int y, int w, int h, String label, Icon icon, boolean primary, boolean tablet, PressAction action) {
			super(x, y, w, h, Text.literal(label), action, DEFAULT_NARRATION_SUPPLIER);
			this.icon = icon;
			this.primary = primary;
			this.tablet = tablet;
		}

		public static Button gold(int x, int y, int w, int h, String label, Icon icon, boolean primary, PressAction action) {
			return new Button(x, y, w, h, label, icon, primary, false, action);
		}

		public static Button blue(int x, int y, int w, int h, String label, Icon icon, boolean primary, PressAction action) {
			return new Button(x, y, w, h, label, icon, primary, true, action);
		}

		@Override
		public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			boolean hot = active && (isHovered() || isFocused());
			int fill, edge, text;
			if (!active) {
				fill = 0xAA11141A; edge = 0xFF3A3A3A; text = 0xFF6F6F6F;
			} else if (tablet) {
				fill = primary || hot ? 0xDD1A3E70 : 0xCC101828;
				edge = hot || primary ? BLUE : BLUE_DIM;
				text = TEXT;
			} else if (primary || hot) {
				fill = GOLD_FILL; edge = 0xFFFFE9B5; text = TEXT_DARK;
			} else {
				fill = 0xCC10131A; edge = GOLD_DIM; text = TEXT;
			}
			c.fill(getX(), getY(), getX() + width, getY() + height, fill);
			border(c, getX(), getY(), width, height, edge);
			if (primary && !tablet) border(c, getX() + 2, getY() + 2, width - 4, height - 4, 0x66B89A5B);
			String label = getMessage().getString();
			int labelWidth = tr.getWidth(label);
			int iconSpace = icon == Icon.NONE ? 0 : 16;
			int startX = getX() + (width - labelWidth - iconSpace) / 2;
			if (icon != Icon.NONE) icon(c, tr, icon, startX + 6, getY() + height / 2, text);
			c.drawText(tr, tr.trimToWidth(label, Math.max(8, width - iconSpace - 6)), startX + iconSpace, getY() + (height - 8) / 2, text, false);
		}
	}
}
