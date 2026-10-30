package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.ActivitySettingsScreenHandler;
import ru.admincontrol.util.EventService;

public class ActivitySettingsScreen extends HandledScreen<ActivitySettingsScreenHandler> {
	private static final int ROW = 22;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private int signature = Integer.MIN_VALUE;

	public ActivitySettingsScreen(ActivitySettingsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private int totalRows() {
		return 2 + EventService.TYPES.length + 7;
	}

	private int visibleRows() {
		return 8;
	}

	private int maxScroll() {
		return Math.max(0, totalRows() - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void syncUi() {
		int sig = this.handler.huntKills() * 3 + this.handler.huntSeconds() * 7 + this.handler.huntMobLabel().hashCode()
				+ this.handler.strikeKills() * 131 + this.handler.strikeMinutes() * 7919
				+ this.handler.trainingDailyMax() * 17 + this.handler.trainingSpawnRadius() * 31;
		if (sig != this.signature) {
			this.signature = sig;
			rebuild();
		}
	}

	private void rebuild() {
		this.clearChildren();
		for (int i = 0; i < visibleRows(); i++) {
			int row = this.scroll + i;
			if (row >= totalRows()) {
				break;
			}
			addRow(row, this.y + 28 + i * ROW);
		}
	}

	private void addRow(int row, int y) {
		int x = this.x + 8;
		int w = this.backgroundWidth - 28;
		String[] kits = EventService.TYPES;
		int kitCount = kits.length;
		if (row == 0) {
			toggle(x, y, w, "Награды тренировки", ActivitySettingsScreenHandler.OPEN_TRAINING_REWARDS);
		} else if (row == 1) {
			toggle(x, y, w, "Набор тренировки", ActivitySettingsScreenHandler.OPEN_TRAINING_KIT);
		} else if (row >= 2 && row < 2 + kitCount) {
			int kit = row - 2;
			toggle(x, y, w, "Набор МП: " + kits[kit].toLowerCase(), ActivitySettingsScreenHandler.OPEN_KIT_BASE + kit);
		} else if (row == 2 + kitCount) {
			pair(x, y, "Охота убийств: " + this.handler.huntKills(),
					ActivitySettingsScreenHandler.HUNT_KILLS_M, ActivitySettingsScreenHandler.HUNT_KILLS_P);
		} else if (row == 3 + kitCount) {
			pair(x, y, "Охота время: " + this.handler.huntSeconds() + " с",
					ActivitySettingsScreenHandler.HUNT_TIME_M, ActivitySettingsScreenHandler.HUNT_TIME_P);
		} else if (row == 4 + kitCount) {
			toggle(x, y, w, "Охота мобы: " + this.handler.huntMobLabel(), ActivitySettingsScreenHandler.HUNT_MOB);
		} else if (row == 5 + kitCount) {
			pair(x, y, "Страйкбол убийств: " + this.handler.strikeKills(),
					ActivitySettingsScreenHandler.STRIKE_KILLS_M, ActivitySettingsScreenHandler.STRIKE_KILLS_P);
		} else if (row == 6 + kitCount) {
			pair(x, y, "Страйкбол время: " + this.handler.strikeMinutes() + " мин",
					ActivitySettingsScreenHandler.STRIKE_TIME_M, ActivitySettingsScreenHandler.STRIKE_TIME_P);
		} else if (row == 7 + kitCount) {
			pair(x, y, "Тренировок в день (база): " + this.handler.trainingDailyMax(),
					ActivitySettingsScreenHandler.TRAIN_DAILY_M, ActivitySettingsScreenHandler.TRAIN_DAILY_P);
		} else if (row == 8 + kitCount) {
			pair(x, y, "Радиус тренировок: " + this.handler.trainingSpawnRadius() + " бл.",
					ActivitySettingsScreenHandler.TRAIN_RADIUS_M, ActivitySettingsScreenHandler.TRAIN_RADIUS_P);
		}
	}

	private void toggle(int x, int y, int w, String label, int id) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> click(id)).dimensions(x, y, w, 18).build());
	}

	private void pair(int x, int y, String label, int minus, int plus) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(minus)).dimensions(x, y, 22, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
		}).dimensions(x + 26, y, this.backgroundWidth - 84, 18).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(plus)).dimensions(x + this.backgroundWidth - 54, y, 22, 18).build());
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
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, this.x + this.backgroundWidth - 12, this.y + 28, visibleRows() * ROW)) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, this.y + 28, visibleRows() * ROW, maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && maxScroll() > 0) {
			int next = this.bar.click(mouseY, this.y + 28, visibleRows() * ROW, maxScroll());
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
		this.syncUi();
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
		AcTheme.titleCentered(this.textRenderer, context, "МП и тренировки", 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, visibleRows() * ROW, maxScroll(), visibleRows(), this.scroll);
		}
	}
}
