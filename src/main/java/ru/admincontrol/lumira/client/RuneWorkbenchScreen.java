package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.item.NecklaceItem;
import ru.admincontrol.lumira.recipe.RuneShapedRecipe;
import ru.admincontrol.lumira.screen.RuneWorkbenchHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно рунного верстака: сетка 4×4, справа светящаяся руническая печать над результатом, слева список рецептов по
 * вкладкам (инструменты, броня, украшения, прочее). Клик по рецепту раскладывает ингредиенты из инвентаря.
 */
@Environment(EnvType.CLIENT)
public final class RuneWorkbenchScreen extends HandledScreen<RuneWorkbenchHandler> {
	private static final int SLATE = 0xF0161B21, SLATE2 = 0xF0222A32, TEAL = 0xFF3FE0D0, SILVER = 0xFFB9C6CE, COPPER = 0xFFC9763E;
	private static final String[] TAB_NAMES = {"Инструменты", "Броня", "Украшения", "Прочее"};
	private int tab;
	private final List<int[]> iconRects = new ArrayList<>();
	private final List<Integer> iconIndex = new ArrayList<>();

	public RuneWorkbenchScreen(RuneWorkbenchHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 276;
		this.backgroundHeight = 210;
	}

	@Override
	protected void init() {
		super.init();
		this.titleX = 8;
		this.titleY = 4;
		this.playerInventoryTitleY = 1000;
	}

	private static int category(ItemStack out) {
		if (out.getItem() instanceof ArmorItem) {
			return 1;
		}
		if (out.getItem() instanceof NecklaceItem) {
			return 2;
		}
		if (out.getItem() instanceof ToolItem) {
			return 0;
		}
		return 3;
	}

	private ItemStack tabIcon(int i) {
		return new ItemStack(switch (i) {
			case 0 -> LumiraContent.item("starsilver_pickaxe");
			case 1 -> LumiraContent.item("twilight_iron_helmet");
			case 2 -> LumiraContent.item("necklace_dew");
			default -> LumiraContent.item("echo_focus");
		});
	}

	@Override
	protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
		int x = this.x, y = this.y, w = this.backgroundWidth, h = this.backgroundHeight;
		c.fillGradient(x, y, x + w, y + h, SLATE2, SLATE);
		c.drawBorder(x, y, w, h, SILVER);
		c.drawBorder(x + 2, y + 2, w - 4, h - 4, 0xFF2B6F6A);
		// угловые руны
		for (int[] corner : new int[][]{{x + 4, y + 4}, {x + w - 8, y + 4}, {x + 4, y + h - 8}, {x + w - 8, y + h - 8}}) {
			c.fill(corner[0], corner[1], corner[0] + 4, corner[1] + 4, TEAL);
			c.fill(corner[0] + 1, corner[1] + 1, corner[0] + 3, corner[1] + 3, SLATE);
		}
		// левая панель рецептов
		c.fill(x + 6, y + 14, x + 100, y + 122, 0xC00C1014);
		c.drawBorder(x + 6, y + 14, 94, 108, 0xFF39505A);
		for (int i = 0; i < 4; i++) {
			int tx = x + 8 + i * 23;
			boolean active = i == tab;
			c.fill(tx, y + 16, tx + 21, y + 34, active ? 0xFF1F5E5A : 0xFF222A32);
			c.drawBorder(tx, y + 16, 21, 18, active ? TEAL : 0xFF39505A);
			c.drawItem(tabIcon(i), tx + 3, y + 17);
		}
		// список рецептов выбранной вкладки
		this.iconRects.clear();
		this.iconIndex.clear();
		if (this.client != null && this.client.world != null) {
			List<RuneShapedRecipe> all = RuneWorkbenchHandler.runeRecipes(this.client.world);
			int shown = 0;
			for (int i = 0; i < all.size() && shown < 20; i++) {
				ItemStack out = all.get(i).getOutput(this.client.world.getRegistryManager());
				if (category(out) != tab) {
					continue;
				}
				int ix = x + 9 + (shown % 4) * 22, iy = y + 38 + (shown / 4) * 20;
				c.fill(ix, iy, ix + 20, iy + 18, 0xFF1A2128);
				c.drawBorder(ix, iy, 20, 18, 0xFF39505A);
				c.drawItem(out, ix + 2, iy + 1);
				this.iconRects.add(new int[]{ix, iy, 20, 18});
				this.iconIndex.add(i);
				shown++;
			}
		}
		// сетка 4×4, результат и печать
		c.fill(x + 104, y + 30, x + 184, y + 110, 0xC00C1014);
		c.drawBorder(x + 104, y + 30, 80, 80, 0xFF39505A);
		for (Slot slot : this.handler.slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			c.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF2B3138);
			c.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF4B555F);
		}
		long t = System.currentTimeMillis();
		double pulse = 0.5 + 0.5 * Math.sin(t / 380.0);
		int cx = x + 232, cy = y + 78;
		for (int r = 22; r >= 10; r -= 4) {
			int alpha = (int) (30 + 40 * pulse * (22 - r) / 12.0 + 20);
			c.fill(cx - r, cy - r, cx + r, cy + r, (alpha << 24) | 0x3FE0D0);
		}
		c.drawBorder(cx - 22, cy - 22, 44, 44, TEAL);
		c.drawText(this.textRenderer, "▶", x + 192, y + 74, TEAL, false);
		c.drawText(this.textRenderer, "Ячейки 4×4", x + 118, y + 114, 0xFF7E8A94, false);
	}

	@Override
	protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
		c.drawText(this.textRenderer, "Рунный верстак", this.titleX, this.titleY, TEAL, false);
		c.drawText(this.textRenderer, TAB_NAMES[this.tab], 106, 18, 0xFFE8F4F2, false);
		c.drawText(this.textRenderer, "Клик по рецепту — разложить", 106, 120, 0xFF7E8A94, false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = 0; i < 4; i++) {
			int tx = this.x + 8 + i * 23, ty = this.y + 16;
			if (mx >= tx && mx < tx + 21 && my >= ty && my < ty + 18) {
				this.tab = i;
				return true;
			}
		}
		for (int i = 0; i < this.iconRects.size(); i++) {
			int[] r = this.iconRects.get(i);
			if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3] && this.client.interactionManager != null) {
				this.client.interactionManager.clickButton(this.handler.syncId, this.iconIndex.get(i));
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		renderBackground(c);
		super.render(c, mouseX, mouseY, delta);
		for (int i = 0; i < this.iconRects.size(); i++) {
			int[] r = this.iconRects.get(i);
			if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
				List<RuneShapedRecipe> all = RuneWorkbenchHandler.runeRecipes(this.client.world);
				c.drawItemTooltip(this.textRenderer, all.get(this.iconIndex.get(i)).getOutput(this.client.world.getRegistryManager()), mouseX, mouseY);
			}
		}
		drawMouseoverTooltip(c, mouseX, mouseY);
	}
}
