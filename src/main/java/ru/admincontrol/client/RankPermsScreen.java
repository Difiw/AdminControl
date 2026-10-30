package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.screen.RankPermsScreenHandler;
import ru.admincontrol.util.RankPerms;

public class RankPermsScreen extends HandledScreen<RankPermsScreenHandler> {
	private static final int ROW = 20;
	private static int savedRank = Integer.MIN_VALUE;
	private static int savedScroll;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;

	public RankPermsScreen(RankPermsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private boolean listMode() {
		return this.handler.rank() < 0;
	}

	private int total() {
		return listMode() ? this.handler.rows().size() + 1 : this.handler.rows().size();
	}

	private int visibleRows() {
		return 8;
	}

	private int maxScroll() {
		return Math.max(0, total() - visibleRows());
	}

	@Override
	protected void init() {
		super.init();
		if (this.handler.rank() == savedRank) {
			this.scroll = Math.max(0, Math.min(maxScroll(), savedScroll));
		}
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		int vis = visibleRows();
		if (listMode()) {
			for (int i = 0; i < vis; i++) {
				int index = this.scroll + i;
				int y = this.y + 28 + i * ROW;
				var levels = this.handler.rows();
				if (index < levels.size()) {
					int click = index;
					this.addDrawableChild(ButtonWidget.builder(Text.literal(levels.get(index).label), b -> click(click))
							.dimensions(this.x + 8, y, this.backgroundWidth - 28, 18).build());
				} else if (index == levels.size()) {
					this.addDrawableChild(ButtonWidget.builder(Text.literal("§cСбросить всё"),
									b -> click(RankPermsScreenHandler.RESET))
							.dimensions(this.x + 8, y, this.backgroundWidth - 28, 18).build());
				}
			}
			return;
		}
		var rows = this.handler.rows();
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= rows.size()) {
				break;
			}
			RankPerms.Row row = rows.get(index);
			int y = this.y + 28 + i * ROW;
			if (row.pair) {
				int minus = "_ban".equals(row.id) ? RankPermsScreenHandler.BAN_M : RankPermsScreenHandler.JAIL_M;
				int plus = "_ban".equals(row.id) ? RankPermsScreenHandler.BAN_P : RankPermsScreenHandler.JAIL_P;
				this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> click(minus))
						.dimensions(this.x + 8, y, 22, 18).build());
				this.addDrawableChild(ButtonWidget.builder(Text.literal(row.label), b -> {
				}).dimensions(this.x + 34, y, this.backgroundWidth - 92, 18).build());
				this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> click(plus))
						.dimensions(this.x + this.backgroundWidth - 54, y, 22, 18).build());
			} else {
				int toggle = RankPermsScreenHandler.TOGGLE_BASE + index;
				String mark = row.on ? "§aвкл" : "§cвыкл";
				this.addDrawableChild(ButtonWidget.builder(Text.literal(row.label + "  " + mark), b -> click(toggle))
						.dimensions(this.x + 8, y, this.backgroundWidth - 28, 18).build());
			}
		}
	}

	private int barX() {
		return this.x + this.backgroundWidth - 12;
	}

	private int barY() {
		return this.y + 28;
	}

	private int barH() {
		return visibleRows() * ROW;
	}

	private void click(int id) {
		savedRank = this.handler.rank();
		savedScroll = this.scroll;
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
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, barX(), barY(), barH())) {
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
		String title = this.title.getString();
		AcTheme.titleCentered(this.textRenderer, context, title, 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
