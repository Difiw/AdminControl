package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import ru.admincontrol.mixin.client.HandledScreenAccessor;

/**
 * Золотая кнопка «Древо навыков» слева от окна (инвентарь, рюкзак, верстак, автопечь), внизу: сверху эффекты EMI,
 * справа — список EMI, слева вверху — вкладки рюкзака/верстака/автопечи. При открытой книге рецептов кнопка уходит левее книги.
 */
public final class SkillTreeButton extends ButtonWidget {
	private static final int SIZE = 22;
	private static final int BOOK_W = 147;
	private static final ItemStack ICON = new ItemStack(Items.OAK_SAPLING);
	private final HandledScreen<?> screen;

	public SkillTreeButton(HandledScreen<?> screen) {
		super(0, 0, SIZE, SIZE, Text.literal("Древо навыков"), b -> SkillClient.openFromInventory(MinecraftClient.getInstance()),
				DEFAULT_NARRATION_SUPPLIER);
		this.screen = screen;
		this.setTooltip(Tooltip.of(Text.literal("Древо навыков\nНавыки, задания и валюты (/skills)")));
		place();
	}

	private void place() {
		HandledScreenAccessor access = (HandledScreenAccessor) this.screen;
		int left = access.adminControl$getX();
		int top = access.adminControl$getY();
		int bh = access.adminControl$getBackgroundHeight();
		int x = left - SIZE - 4;
		int y = top + bh - SIZE - 4;
		if (this.screen instanceof RecipeBookProvider book && book.getRecipeBookWidget().isOpen()) {
			x = left - BOOK_W - 2 - SIZE - 4;
			if (x < 2) {
				x = left - BOOK_W - 2;
				y = top - SIZE - 4;
			}
		}
		this.setX(Math.max(2, x));
		this.setY(Math.max(2, y));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		place();
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;
		double wave = (Math.sin((Util.getMeasuringTimeMs() % 1600L) / 1600.0 * Math.PI * 2.0) + 1.0) / 2.0;
		int glow = (int) (60 + 120 * wave);
		context.fill(x - 2, y - 2, x + w + 2, y + h + 2, (glow << 24) | 0xFFC21A);
		boolean hot = this.isHovered() || this.isFocused();
		context.fillGradient(x, y, x + w, y + h, hot ? 0xFF8A6512 : 0xFF5C420A, hot ? 0xFF5A3F06 : 0xFF2E2003);
		int edge = 0xFF000000 | (0xFF << 16) | ((0xC0 + (int) (0x30 * wave)) << 8) | (int) (0x40 * wave);
		context.drawBorder(x, y, w, h, edge);
		context.drawItem(ICON, x + (w - 16) / 2, y + (h - 16) / 2);
	}
}
