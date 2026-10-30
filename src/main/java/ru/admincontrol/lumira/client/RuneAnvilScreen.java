package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import ru.admincontrol.lumira.block.RuneAnvilBlock;
import ru.admincontrol.lumira.block.RuneAnvilLogic;
import ru.admincontrol.lumira.screen.RuneAnvilHandler;

/**
 * Окно рунной наковальни: предмет, материал, топливо; светящаяся шкала рунной энергии; предпросмотр ремонта;
 * кнопки «Ремонтировать» и «До конца».
 */
@Environment(EnvType.CLIENT)
public final class RuneAnvilScreen extends HandledScreen<RuneAnvilHandler> {
	private static final int SLATE = 0xF0161B21, SLATE2 = 0xF0222A32, TEAL = 0xFF3FE0D0, SILVER = 0xFFB9C6CE, AMBER = 0xFFF2B23A;

	public RuneAnvilScreen(RuneAnvilHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 200;
		this.backgroundHeight = 198;
	}

	@Override
	protected void init() {
		super.init();
		this.titleX = 8;
		this.titleY = 5;
		this.playerInventoryTitleY = 1000;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Ремонтировать"), b -> click(RuneAnvilHandler.BTN_ONCE))
				.dimensions(this.x + 108, this.y + 70, 80, 16).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("До конца"), b -> click(RuneAnvilHandler.BTN_ALL))
				.dimensions(this.x + 108, this.y + 90, 80, 16).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
		int x = this.x, y = this.y, w = this.backgroundWidth, h = this.backgroundHeight;
		c.fillGradient(x, y, x + w, y + h, SLATE2, SLATE);
		c.drawBorder(x, y, w, h, SILVER);
		c.drawBorder(x + 2, y + 2, w - 4, h - 4, 0xFF2B6F6A);
		// наковальня-рисунок и дуги рун вокруг слотов
		c.fill(x + 6, y + 18, x + 194, y + 112, 0xC00C1014);
		c.drawBorder(x + 6, y + 18, 188, 94, 0xFF39505A);
		for (Slot slot : this.handler.slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			int bx = x + slot.x, by = y + slot.y;
			boolean special = slot.id < RuneAnvilHandler.PLAYER_START;
			c.fill(bx - 1, by - 1, bx + 17, by + 17, special ? TEAL : 0xFF2B3138);
			c.fill(bx, by, bx + 16, by + 16, special ? 0xFF1B2A30 : 0xFF4B555F);
		}
		c.drawText(this.textRenderer, "+", x + 82, y + 44, TEAL, false);
		c.drawText(this.textRenderer, "Предмет", x + 46, y + 26, 0xFF9FB3BD, false);
		c.drawText(this.textRenderer, "Материал", x + 96, y + 26, 0xFF9FB3BD, false);
		c.drawText(this.textRenderer, "Топливо", x + 68, y + 96, 0xFF9FB3BD, false);
		// шкала энергии: янтарная, светится при наличии топлива
		int energy = this.handler.energy();
		int gx = x + 168, gy = y + 24, gh = 44;
		c.fill(gx, gy, gx + 12, gy + gh, 0xFF0B0E11);
		c.drawBorder(gx, gy, 12, gh, SILVER);
		int fill = Math.min(gh - 2, (int) ((gh - 2) * Math.min(1.0, energy / 64.0)));
		if (fill > 0) {
			c.fillGradient(gx + 1, gy + gh - 1 - fill, gx + 11, gy + gh - 1, 0xFFFFD96A, 0xFF9A5F10);
		}
		for (int i = 1; i < 4; i++) {
			c.fill(gx, gy + i * gh / 4, gx + 3, gy + i * gh / 4 + 1, SILVER);
		}
		c.drawText(this.textRenderer, String.valueOf(energy), gx - 2, gy + gh + 3, energy > 0 ? AMBER : 0xFFFF5A4F, false);
		c.drawText(this.textRenderer, "ед.", gx + 14, gy + gh + 3, 0xFF7E8A94, false);
		// предпросмотр
		ItemStack target = this.handler.getSlot(RuneAnvilHandler.ITEM).getStack();
		String preview;
		if (target.isEmpty()) {
			preview = "Положите предмет Люмиры";
		} else if (RuneAnvilBlock.repairMaterial(target) == null) {
			preview = "Не обслуживается наковальней";
		} else if (!target.isDamageable() || target.getDamage() == 0) {
			preview = "Предмет цел";
		} else {
			int max = target.getMaxDamage();
			int cur = max - target.getDamage();
			int next = Math.min(max, cur + RuneAnvilLogic.restore(target));
			preview = "Прочность " + cur + " → " + next + " / " + max;
		}
		c.drawText(this.textRenderer, preview, x + 12, y + 104, 0xFFE8F4F2, false);
	}

	@Override
	protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
		c.drawText(this.textRenderer, "Рунная наковальня", this.titleX, this.titleY, TEAL, false);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		renderBackground(c);
		super.render(c, mouseX, mouseY, delta);
		drawMouseoverTooltip(c, mouseX, mouseY);
	}
}
