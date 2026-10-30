package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.BugListScreenHandler;

import java.util.List;

/** /buglist: список багов и ошибок → карточка записи (время, место, ник, описание), телепорт, удаление. */
public class BugListScreen extends HandledScreen<BugListScreenHandler> {
	private static final int W = 360;
	private static final int H = 250;
	private static final int TOP = 28;
	private static final int ROW = 20;
	private static final int COLOR = 0xC8742E;

	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	/** Открытая карточка; -1 — список. */
	private int open = -1;
	private boolean confirmClear;

	public BugListScreen(BugListScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = W;
		this.backgroundHeight = H;
		this.playerInventoryTitleY = 1000;
	}

	private List<BugListScreenHandler.Entry> entries() {
		return this.handler.entries();
	}

	private int visibleRows() {
		return (H - TOP - 32) / ROW;
	}

	private int maxScroll() {
		return Math.max(0, entries().size() - visibleRows());
	}

	private int footerY() {
		return this.y + H - 24;
	}

	private static String shortTime(String time) {
		return time.length() >= 16 ? time.substring(0, 5) + " " + time.substring(11, 16) : time;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		int fw = W - 16;
		int fx = this.x + 8;
		if (this.open >= 0 && this.open < entries().size()) {
			BugListScreenHandler.Entry e = entries().get(this.open);
			int n = 1 + (e.hasPos() ? 1 : 0) + (this.handler.canDelete() ? 1 : 0);
			int bw = (fw - (n - 1) * 4) / n;
			int bx = fx;
			button("К списку", bx, footerY(), bw, () -> {
				this.open = -1;
				rebuild();
			});
			bx += bw + 4;
			if (e.hasPos()) {
				button("Телепорт", bx, footerY(), bw, () -> send(BugListScreenHandler.OP_TELEPORT, e.id()));
				bx += bw + 4;
			}
			if (this.handler.canDelete()) {
				button("Удалить", bx, footerY(), bw, () -> send(BugListScreenHandler.OP_DELETE, e.id()));
			}
			AcNavButtons.add(this);
			return;
		}
		this.open = -1;
		this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
		int w = W - 28;
		for (int i = 0; i < visibleRows(); i++) {
			int index = this.scroll + i;
			if (index >= entries().size()) {
				break;
			}
			BugListScreenHandler.Entry e = entries().get(index);
			String who = e.nick().isEmpty() ? "" : e.nick() + " · ";
			String label = shortTime(e.time()) + "  " + who + e.type() + (e.count() > 1 ? " ×" + e.count() : "");
			button(AcTheme.ellipsize(this.textRenderer, label, w - 8), this.x + 8, this.y + TOP + i * ROW, w, () -> {
				this.open = index;
				this.confirmClear = false;
				rebuild();
			});
		}
		if (this.handler.canDelete() && !entries().isEmpty()) {
			int bw = 120;
			button(this.confirmClear ? "§cТочно очистить?" : "Очистить список", fx + fw - bw, footerY(), bw, () -> {
				if (this.confirmClear) {
					send(BugListScreenHandler.OP_CLEAR, 0);
				} else {
					this.confirmClear = true;
					rebuild();
				}
			});
		}
		AcNavButtons.add(this);
	}

	private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
		return this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
				.dimensions(x, y, w, 18).build());
	}

	private void send(int op, long id) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(op);
		buf.writeLong(id);
		ClientPlayNetworking.send(ModPackets.BUG_ACTION, buf);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.open >= 0) {
			return true;
		}
		int next = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.open < 0 && maxScroll() > 0
				&& this.bar.inTrack(mouseX, mouseY, this.x + W - 12, this.y + TOP, visibleRows() * ROW)) {
			this.bar.dragging = true;
			this.scroll = this.bar.click(mouseY, this.y + TOP, visibleRows() * ROW, maxScroll());
			rebuild();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && this.open < 0 && maxScroll() > 0) {
			int next = this.bar.click(mouseY, this.y + TOP, visibleRows() * ROW, maxScroll());
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
		AcTheme.panel(context, this.x, this.y, W, H, 20);
		AcTheme.footer(context, this.x, this.y, W, H);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		int w = W - 16;
		if (this.open >= 0 && this.open < entries().size()) {
			BugListScreenHandler.Entry e = entries().get(this.open);
			AcTheme.titleCentered(this.textRenderer, context, "#" + e.id() + " · " + e.type(), 8, 6, w);
			int y = TOP;
			AcTheme.clipped(this.textRenderer, context, "Время: " + e.time(), 8, y, w, AcTheme.TEXT);
			y += 12;
			if (e.count() > 1) {
				AcTheme.clipped(this.textRenderer, context, "Повторов: " + e.count() + ", последний: " + e.last(), 8, y, w, AcTheme.TEXT);
				y += 12;
			}
			AcTheme.clipped(this.textRenderer, context, "Игрок: " + (e.nick().isEmpty() ? "— (ошибка сервера)" : e.nick()),
					8, y, w, AcTheme.TEXT);
			y += 12;
			AcTheme.clipped(this.textRenderer, context, "Место: " + (e.hasPos() ? e.where() : "—"), 8, y, w, AcTheme.TEXT);
			y += 18;
			AcTheme.clipped(this.textRenderer, context, "Описание:", 8, y, w, COLOR);
			y += 12;
			AcTheme.wrapped(this.textRenderer, context, e.description(), 8, y, w, AcTheme.TEXT);
			return;
		}
		AcTheme.titleCentered(this.textRenderer, context, "Баг-лист", 8, 6, w);
		if (entries().isEmpty()) {
			AcTheme.wrapped(this.textRenderer, context, "Багов и ошибок пока нет. Поиск багаюза и запись ошибок сервера "
					+ "включаются в /anticheat (разработчик).", 8, TOP + 4, w, 0x8A96A8);
		}
		String count = this.handler.total() > entries().size()
				? "Записей: " + this.handler.total() + " (показаны новые " + entries().size() + ")"
				: "Записей: " + this.handler.total();
		AcTheme.clipped(this.textRenderer, context, count, 8, H - 19, this.handler.canDelete() ? w - 126 : w, 0x8A96A8);
		if (maxScroll() > 0) {
			this.bar.draw(context, W - 12, TOP, visibleRows() * ROW, maxScroll(), visibleRows(), this.scroll);
		}
	}
}
