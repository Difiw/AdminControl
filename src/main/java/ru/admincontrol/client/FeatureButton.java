package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.mixin.client.HandledScreenAccessor;
import ru.admincontrol.net.ModPackets;

/**
 * Вкладка слева от окна (инвентарь, рюкзак, верстак, автопечь) в стиле окон Minecraft, с иконкой предмета.
 * Вкладка текущего окна вдавлена и примыкает к нему; клик по другой мгновенно переключает окно.
 */
public final class FeatureButton extends ButtonWidget {
	private static final int SIZE = 22;
	private static final int GAP = 2;
	private static final int BOOK_W = 147;
	private final HandledScreen<?> screen;
	private final int index;
	private final boolean current;
	private final ItemStack icon;

	private FeatureButton(HandledScreen<?> screen, int index, boolean current, String title, ItemStack icon, Identifier packet) {
		super(0, 0, SIZE, SIZE, Text.literal(title), b -> {
			if (!current) {
				ClientPlayNetworking.send(packet, PacketByteBufs.empty());
			}
		}, DEFAULT_NARRATION_SUPPLIER);
		this.screen = screen;
		this.index = index;
		this.current = current;
		this.icon = icon;
		this.setTooltip(Tooltip.of(Text.literal(current ? title + "\nОткрыто сейчас" : title + "\nНажмите, чтобы открыть")));
		place();
	}

	/** Кнопка для функции {@code id} или {@code null}, если такой функции нет. */
	public static FeatureButton of(HandledScreen<?> screen, int index, String id, String currentId) {
		boolean current = id.equals(currentId);
		return switch (id) {
			case "backpack" -> new FeatureButton(screen, index, current, "Рюкзак", new ItemStack(Items.BUNDLE),
					ModPackets.OPEN_BACKPACK);
			case "workbench" -> new FeatureButton(screen, index, current, "Верстак", new ItemStack(Items.CRAFTING_TABLE),
					ModPackets.OPEN_WORKBENCH);
			case "furnace" -> new FeatureButton(screen, index, current, "Автопечь", new ItemStack(Items.FURNACE),
					ModPackets.OPEN_FURNACE);
			default -> null;
		};
	}

	private void place() {
		HandledScreenAccessor access = (HandledScreenAccessor) this.screen;
		int left = access.adminControl$getX();
		int x = left - SIZE + 1;
		if (this.screen instanceof RecipeBookProvider book && book.getRecipeBookWidget().isOpen()) {
			x = left - BOOK_W - 2 - SIZE;
		}
		this.setX(Math.max(2, x));
		this.setY(access.adminControl$getY() + 4 + this.index * (SIZE + GAP));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		place();
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		boolean hot = !this.current && (this.isHovered() || this.isFocused());
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;
		int right = x + w + (this.current ? 2 : 0);
		int body = this.current ? 0xFFC6C6C6 : (hot ? 0xFFD8D8D8 : 0xFF9C9C9C);
		context.fill(x, y, right, y + h, 0xFF000000);
		context.fill(x + 1, y + 1, right - (this.current ? 0 : 1), y + h - 1, body);
		if (this.current) {
			context.fill(x + 1, y + 1, right, y + 3, 0xFFFFFFFF);
			context.fill(x + 1, y + 1, x + 3, y + h - 1, 0xFFFFFFFF);
			context.fill(x + 3, y + h - 3, right, y + h - 1, 0xFF555555);
		} else {
			context.fill(x + 1, y + 1, right - 1, y + 2, 0xFFFFFFFF);
			context.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
			context.fill(x + 2, y + h - 2, right - 1, y + h - 1, 0xFF555555);
			context.fill(right - 2, y + 2, right - 1, y + h - 1, 0xFF555555);
		}
		context.drawItem(this.icon, x + (w - 16) / 2, y + (h - 16) / 2);
	}
}
