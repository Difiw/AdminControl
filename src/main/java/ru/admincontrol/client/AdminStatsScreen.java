package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.AdminStatsScreenHandler;

public class AdminStatsScreen extends HandledScreen<AdminStatsScreenHandler> {
	private static final int ROW = 20;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;

	public AdminStatsScreen(AdminStatsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 270;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private int visibleRows() {
		return 7;
	}

	private int maxScroll() {
		return Math.max(0, this.handler.labels().size() - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		int top = this.y + 28;
		this.addDrawableChild(ButtonWidget.builder(
						Text.literal(this.handler.nick() + " · вход: " + this.handler.lastJoin()),
						b -> {
						})
				.dimensions(this.x + 8, top, this.backgroundWidth - 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(
						Text.literal(this.handler.online()
								? this.handler.nick() + " · сейчас онлайн"
								: this.handler.nick() + " · выход: " + this.handler.lastLeave()),
						b -> {
						})
				.dimensions(this.x + 8, top + 17, this.backgroundWidth - 28, 16).build());
		this.addDrawableChild(ButtonWidget.builder(
						Text.literal("Входы и выходы " + this.handler.nick()),
						b -> click(AdminStatsScreenHandler.SESSIONS))
				.dimensions(this.x + 8, top + 35, this.backgroundWidth - 28, 18).build());

		int listY = top + 56;
		var labels = this.handler.labels();
		for (int i = 0; i < visibleRows(); i++) {
			int index = this.scroll + i;
			if (index >= labels.size()) {
				break;
			}
			int id = index;
			this.addDrawableChild(ButtonWidget.builder(Text.literal(labels.get(index)), b -> click(id))
					.dimensions(this.x + 8, listY + i * ROW, this.backgroundWidth - 28, 18).build());
		}
		if (this.handler.pages() > 1) {
			int y = listY + visibleRows() * ROW + 2;
			ButtonWidget newer = ButtonWidget.builder(Text.literal("« Новее"), b -> click(AdminStatsScreenHandler.NEWER))
					.dimensions(this.x + 8, y, 80, 18).build();
			newer.active = this.handler.page() > 0;
			this.addDrawableChild(newer);
			ButtonWidget older = ButtonWidget.builder(Text.literal("Старше »"), b -> click(AdminStatsScreenHandler.OLDER))
					.dimensions(this.x + this.backgroundWidth - 100, y, 80, 18).build();
			older.active = this.handler.page() < this.handler.pages() - 1;
			this.addDrawableChild(older);
		}
	}

	private int barY() {
		return this.y + 28 + 56;
	}

	private int barH() {
		return visibleRows() * ROW;
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, this.x + this.backgroundWidth - 12, barY(), barH())) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, barY(), barH(), maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && maxScroll() > 0) {
			int next = this.bar.click(mouseY, barY(), barH(), maxScroll());
			if (next != this.scroll) {
				this.scroll = next;
				rebuild();
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.bar.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 20);
		AcTheme.footer(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		String title = "Статистика " + this.handler.nick() + " — выберите день";
		AcTheme.titleCentered(this.textRenderer, context, title, 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 84, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
