package ru.admincontrol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.screen.TrainingKitScreenHandler;
import ru.admincontrol.util.TrainingConfig;

public class TrainingKitScreen extends HandledScreen<TrainingKitScreenHandler> {
	private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");
	private static final Identifier GENERIC = VanillaInventoryGui.GENERIC_54;
	private static final int PANEL_X = 180;
	private static final int BTN_W = 132;
	private static final int BTN_H = 16;
	private final AcDropList enchants = new AcDropList();
	private final boolean[] pending = new boolean[TrainingConfig.ENCHANTS.length];
	private int pendingKind;

	public TrainingKitScreen(TrainingKitScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 324;
		this.backgroundHeight = 256;
		this.playerInventoryTitleY = 1000;
		this.titleY = 6;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.x + PANEL_X;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Вкладка ←"), b -> click(TrainingKitScreenHandler.TAB_M))
				.dimensions(x, this.y + 20, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Вкладка →"), b -> click(TrainingKitScreenHandler.TAB_P))
				.dimensions(x, this.y + 37, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Страница ←"), b -> click(TrainingKitScreenHandler.PAGE_M))
				.dimensions(x, this.y + 62, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Страница →"), b -> click(TrainingKitScreenHandler.PAGE_P))
				.dimensions(x, this.y + 79, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Кол-во −"), b -> click(TrainingKitScreenHandler.COUNT_M))
				.dimensions(x, this.y + 104, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Кол-во +"), b -> click(TrainingKitScreenHandler.COUNT_P))
				.dimensions(x, this.y + 121, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Чары"), b -> {
			if (this.enchants.open) {
				applyEnchants();
			} else {
				openEnchants();
			}
		}).dimensions(x, this.y + 146, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Сила −"), b -> click(TrainingKitScreenHandler.ENCH_M))
				.dimensions(x, this.y + 176, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Сила +"), b -> click(TrainingKitScreenHandler.ENCH_P))
				.dimensions(x, this.y + 193, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Очистить слот"), b -> click(TrainingKitScreenHandler.CLEAR))
				.dimensions(x, this.y + 214, BTN_W, BTN_H).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Из моего инвентаря"), b -> click(TrainingKitScreenHandler.FROM_INV))
				.dimensions(x, this.y + 234, BTN_W, BTN_H)
				.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
						"Заменить весь набор вещами из вашего инвентаря, брони и левой руки — вместе с NBT (оружие из модов).")))
				.build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private ItemStack selectedStack() {
		int screen = this.handler.kitSlotScreenIndex(this.handler.selected());
		if (screen < 0 || screen >= this.handler.slots.size()) {
			return ItemStack.EMPTY;
		}
		return this.handler.getSlot(screen).getStack();
	}

	private boolean hasEnchant(int index) {
		ItemStack stack = selectedStack();
		if (stack.isEmpty()) {
			return false;
		}
		var enc = TrainingConfig.enchant(TrainingConfig.enchantId(index));
		return enc != null && EnchantmentHelper.getLevel(enc, stack) > 0;
	}

	private void placeEnchants() {
		this.enchants.x = this.x + PANEL_X;
		this.enchants.y = this.y + 146 + BTN_H;
		this.enchants.w = BTN_W;
	}

	private void openEnchants() {
		this.pendingKind = this.handler.selKind();
		for (int i = 0; i < this.pending.length; i++) {
			this.pending[i] = hasEnchant(i);
		}
		this.enchants.open = true;
		this.enchants.scroll = Math.max(0, this.pendingKind - 2);
		placeEnchants();
	}

	private void applyEnchants() {
		this.enchants.open = false;
		this.enchants.dragging = false;
		boolean[] now = new boolean[this.pending.length];
		for (int i = 0; i < now.length; i++) {
			now[i] = hasEnchant(i);
		}
		for (int i = 0; i < this.pending.length; i++) {
			if (this.pending[i] != now[i]) {
				click(TrainingKitScreenHandler.TOGGLE_ENCH + i);
			}
		}
		click(TrainingKitScreenHandler.SET_KIND + this.pendingKind);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.enchants.open && this.enchants.inPanel(mouseX, mouseY)) {
			this.enchants.wheel(amount, TrainingConfig.ENCHANTS.length);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.enchants.open) {
			placeEnchants();
			int total = TrainingConfig.ENCHANTS.length;
			if (this.enchants.inBar(mouseX, mouseY) && this.enchants.maxScroll(total) > 0) {
				this.enchants.beginDrag(mouseY, total);
				return true;
			}
			int index = this.enchants.indexAt(mouseX, mouseY, total);
			if (index >= 0) {
				this.pending[index] = !this.pending[index];
				this.pendingKind = index;
				return true;
			}
			applyEnchants();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.enchants.open && this.enchants.dragging) {
			this.enchants.drag(mouseY, TrainingConfig.ENCHANTS.length);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.enchants.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
		if (this.enchants.open) {
			placeEnchants();
			int total = TrainingConfig.ENCHANTS.length;
			this.enchants.scroll = Math.max(0, Math.min(this.enchants.maxScroll(total), this.enchants.scroll));
			this.enchants.draw(context, total, AcDropList.ROWS);
			int textW = this.enchants.w - AcScrollBar.WIDTH - 6;
			int end = Math.min(total, this.enchants.scroll + AcDropList.ROWS);
			for (int i = this.enchants.scroll; i < end; i++) {
				int row = i - this.enchants.scroll;
				int ry = this.enchants.y + row * AcDropList.ROW_H;
				int bg = i == this.pendingKind ? 0xAA3A3A10 : ((row % 2 == 0) ? 0xAA202020 : 0xAA141414);
				context.fill( this.enchants.x + 1, ry, this.enchants.x + this.enchants.w - AcScrollBar.WIDTH - 1,
						ry + AcDropList.ROW_H - 1, bg);
				String mark = this.pending[i] ? "☑ " : "☐ ";
				String label = mark + TrainingConfig.enchantLabel(TrainingConfig.ENCHANTS[i]);
				java.util.List<OrderedText> parts = this.textRenderer.wrapLines(Text.literal(label), textW);
				if (!parts.isEmpty()) {
					context.drawText(this.textRenderer, parts.get(0), this.enchants.x + 4, ry + 4, AcTheme.TEXT, false);
				}
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.frameAround(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
		AcTheme.panel(context, this.x + 176, this.y, 148, this.backgroundHeight, 18);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, GENERIC);
		int top = 3 * 18 + 17;
		context.drawTexture(GENERIC, this.x, this.y, 0, 0, 176, top);
		RenderSystem.setShaderTexture(0, INVENTORY);
		context.drawTexture(INVENTORY, this.x, this.y + TrainingKitScreenHandler.INV_Y, 0, 0, 176, 166);
		if (this.client != null && this.client.player != null) {
			InventoryScreen.drawEntity(context, this.x + 51, this.y + TrainingKitScreenHandler.INV_Y + 75, 30,
					(float) (this.x + 51) - mouseX, (float) (this.y + TrainingKitScreenHandler.INV_Y + 75 - 50) - mouseY, this.client.player);
		}
		int sel = this.handler.selected();
		int screen = this.handler.kitSlotScreenIndex(sel);
		if (screen >= 0 && screen < this.handler.slots.size()) {
			Slot slot = this.handler.getSlot(screen);
			int sx = this.x + slot.x - 1;
			int sy = this.y + slot.y - 1;
			context.fill( sx, sy, sx + 18, sy + 18, 0x80FFFF55);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, AcTheme.ellipsize(this.textRenderer, this.title.getString(), 160), 8, 6, 0x404040, false);
		int x = PANEL_X;
		AcTheme.clipped(this.textRenderer, context, "Настройки", x, 6, BTN_W, AcTheme.TITLE);
		AcTheme.clipped(this.textRenderer, context, "Стр. " + (this.handler.page() + 1) + "/" + this.handler.pageCount(), x, 50, BTN_W, AcTheme.TEXT);
		AcTheme.clipped(this.textRenderer, context, "Кол-во: " + this.handler.selCount(), x, 92, BTN_W, AcTheme.TEXT);
		AcTheme.clipped(this.textRenderer, context, this.handler.selEnchantName(), x, 134, BTN_W, AcTheme.TEXT);
		AcTheme.clipped(this.textRenderer, context, "Сила: " + this.handler.selEnch(), x, 164, BTN_W, AcTheme.TEXT);
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		return mouseX < left || mouseY < top || mouseX >= left + this.backgroundWidth || mouseY >= top + this.backgroundHeight;
	}
}
