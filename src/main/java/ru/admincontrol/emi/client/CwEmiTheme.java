package ru.admincontrol.emi.client;

import net.minecraft.client.gui.DrawContext;
import ru.admincontrol.lumira.client.LumiraHud;

/**
 * Две темы без настроек: в обычном мире панель выглядит как ванильное окно Creative (серая со скошенными краями),
 * в сюжетных измерениях и мире стройки — тёмная сланцевая с серебристо-бирюзовой рамкой, как инвентарь сюжета.
 */
public final class CwEmiTheme {
	public final boolean vanilla;
	public final int accent, text, sub, slotHover, locked, separator, slotFill;

	private CwEmiTheme(boolean vanilla, int accent, int text, int sub, int slotHover, int locked, int separator, int slotFill) {
		this.vanilla = vanilla;
		this.accent = accent;
		this.text = text;
		this.sub = sub;
		this.slotHover = slotHover;
		this.locked = locked;
		this.separator = separator;
		this.slotFill = slotFill;
	}

	private static final CwEmiTheme STORY = new CwEmiTheme(false, 0xFF3FE0D0, 0xFFE8F4F2, 0xFF9DB0B8, 0xFF4B7C80, 0xFF7A8890, 0xFF2B6F6A, 0xFF4B555F);
	private static final CwEmiTheme VANILLA = new CwEmiTheme(true, 0xFFFFFFFF, 0xFF404040, 0xFF6B6B6B, 0xFFB4B4B4, 0xFF7A7A7A, 0xFF8B8B8B, 0xFF8B8B8B);

	public static CwEmiTheme current() {
		return LumiraHud.active() || LumiraHud.inCwWorld() ? STORY : VANILLA;
	}

	public boolean story() {
		return !vanilla;
	}

	/** Альфа-канал цвета масштабируется настройкой прозрачности панелей. */
	public static int alpha(int argb) {
		int a = (argb >>> 24) * CwEmiConfig.get().opacity / 100;
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public void panel(DrawContext c, int x, int y, int w, int h) {
		if (vanilla) {
			c.fill(x, y, x + w, y + h, alpha(0xFF000000));
			c.fill(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0xFFFFFFFF));
			c.fill(x + 2, y + 2, x + w - 1, y + h - 1, alpha(0xFF555555));
			c.fill(x + 2, y + 2, x + w - 2, y + h - 2, alpha(0xFFC6C6C6));
			return;
		}
		c.fillGradient(x, y, x + w, y + h, alpha(0xF0222A32), alpha(0xF0161B21));
		c.drawBorder(x, y, w, h, 0xFFB9C6CE);
		c.drawBorder(x + 2, y + 2, w - 4, h - 4, 0xFF2B6F6A);
		for (int[] k : new int[][]{{x + 4, y + 4}, {x + w - 8, y + 4}, {x + 4, y + h - 8}, {x + w - 8, y + h - 8}}) {
			c.fill(k[0], k[1], k[0] + 4, k[1] + 4, accent);
			c.fill(k[0] + 1, k[1] + 1, k[0] + 3, k[1] + 3, 0xFF161B21);
		}
	}

	/** Вдавленная ячейка (как слот инвентаря). */
	public void slot(DrawContext c, int x, int y, int size, boolean hover) {
		slot(c, x, y, size, hover, size);
	}

	public void slot(DrawContext c, int x, int y, int w, boolean hover, int h) {
		if (vanilla) {
			c.fill(x, y, x + w, y + h, 0xFF373737);
			c.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
			c.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? slotHover : slotFill);
			return;
		}
		c.fill(x, y, x + w, y + h, 0xFF2B3138);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? slotHover : slotFill);
		if (hover) {
			c.drawBorder(x, y, w, h, accent);
		}
	}

	public void button(DrawContext c, int x, int y, int w, int h, boolean hover, boolean active) {
		if (vanilla) {
			int face = !active ? 0xFF8B8B8B : hover ? 0xFF8D8DBC : 0xFF6F6F6F;
			c.fill(x, y, x + w, y + h, 0xFF000000);
			c.fill(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xFFFFFFFF : 0xFFA0A0A0);
			c.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF373737);
			c.fill(x + 2, y + 2, x + w - 2, y + h - 2, face);
			return;
		}
		c.fill(x, y, x + w, y + h, hover && active ? 0xFF2B6F6A : 0xFF1B2A30);
		c.drawBorder(x, y, w, h, !active ? 0xFF39505A : hover ? accent : 0xFF2B6F6A);
	}

	/** Цвет текста на кнопке. */
	public int buttonText(boolean active) {
		return vanilla ? (active ? 0xFFFFFFFF : 0xFFA0A0A0) : (active ? text : locked);
	}

	/** Текст на панели: в ванильной теме без тени, в сюжетной — светлый. */
	public void text(DrawContext c, net.minecraft.client.font.TextRenderer font, net.minecraft.text.Text t, int x, int y, int color) {
		c.drawText(font, t, x, y, color, false);
	}
}
