package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.admincontrol.mixin.client.HandledScreenAccessor;

/** Маленькая кнопка «Назад» над левым верхним углом рюкзака, верстака и автопечи: закрывает окно и открывает инвентарь. */
public final class BackButton extends ButtonWidget {
	private static final int SIZE = 14;
	private static final int BOOK_W = 147;
	private final HandledScreen<?> screen;

	public BackButton(HandledScreen<?> screen) {
		super(0, 0, SIZE, SIZE, Text.literal("Назад"), b -> back(), DEFAULT_NARRATION_SUPPLIER);
		this.screen = screen;
		this.setTooltip(Tooltip.of(Text.literal("Назад в инвентарь")));
		place();
	}

	private static void back() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		client.player.closeHandledScreen();
		if (!ActivityLock.active()) {
			client.setScreen(new InventoryScreen(client.player));
		}
	}

	private void place() {
		HandledScreenAccessor access = (HandledScreenAccessor) this.screen;
		int left = access.adminControl$getX();
		int x = left;
		if (this.screen instanceof RecipeBookProvider book && book.getRecipeBookWidget().isOpen()) {
			x = left - BOOK_W - 2;
		}
		this.setX(Math.max(2, x));
		this.setY(Math.max(2, access.adminControl$getY() - SIZE - 2));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		place();
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean hot = this.isHovered() || this.isFocused();
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;
		context.fill(x, y, x + w, y + h, 0xFF000000);
		context.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFFD8D8D8 : 0xFF9C9C9C);
		context.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
		context.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
		context.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
		context.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
		int color = 0xFF3F3F3F;
		context.fill(x + 5, y + 6, x + 11, y + 8, color);
		for (int i = 0; i < 4; i++) {
			context.fill(x + 3 + i, y + 6 - i, x + 4 + i, y + 8 + i, color);
		}
	}
}
