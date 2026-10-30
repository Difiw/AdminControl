package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Фон как у сундука / нижней части инвентаря: клетки и рамки, без персонажа и крафта.
 */
public final class VanillaInventoryGui {
	public static final Identifier GENERIC_54 = new Identifier("textures/gui/container/generic_54.png");

	private VanillaInventoryGui() {
	}

	public static int height(int rows) {
		return 114 + rows * 18;
	}

	public static int playerInvY(int rows, int row) {
		return 103 + (rows - 4) * 18 + row * 18;
	}

	public static int hotbarY(int rows) {
		return 161 + (rows - 4) * 18;
	}

	public static void draw(DrawContext context, int x, int y, int width, int rows) {
		int top = rows * 18 + 17;
		context.drawTexture(GENERIC_54, x, y, 0, 0, width, top);
		context.drawTexture(GENERIC_54, x, y + top, 0, 126, width, 96);
	}

	/** Только шапка и инвентарь игрока — без ряда контейнера. */
	public static void drawPlayerInventoryOnly(DrawContext context, int x, int y, int width) {
		context.drawTexture(GENERIC_54, x, y, 0, 0, width, 17);
		context.drawTexture(GENERIC_54, x, y + 17, 0, 126, width, 96);
	}
}
