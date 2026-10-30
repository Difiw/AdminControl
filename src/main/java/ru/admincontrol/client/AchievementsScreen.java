package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.AchievementsScreenHandler;

public class AchievementsScreen extends HandledScreen<AchievementsScreenHandler> {
	private int scroll;

	public AchievementsScreen(AchievementsScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 320;
		this.backgroundHeight = 220;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		int vis = 8;
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= this.handler.ids.size()) {
				break;
			}
			if (!this.handler.curator) {
				continue;
			}
			boolean done = this.handler.done.get(index);
			int row = index;
			this.addDrawableChild(ButtonWidget.builder(Text.literal(done ? "Забрать" : "Выдать"), b -> {
				var buf = PacketByteBufs.create();
				buf.writeString(this.handler.nick, 16);
				buf.writeString(this.handler.ids.get(row), 128);
				buf.writeBoolean(!done);
				ClientPlayNetworking.send(ModPackets.ACH_TOGGLE, buf);
			}).dimensions(this.x + 230, this.y + 36 + i * 20, 78, 18).build());
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int max = Math.max(0, this.handler.ids.size() - 8);
		this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(amount)));
		rebuild();
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, this.backgroundWidth, this.backgroundHeight, 22);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Достижения  " + this.handler.nick, 8, 6, this.backgroundWidth - 16);
		int vis = 8;
		for (int i = 0; i < vis; i++) {
			int index = this.scroll + i;
			if (index >= this.handler.titles.size()) {
				break;
			}
			boolean done = this.handler.done.get(index);
			AcTheme.clipped(this.textRenderer, context, (done ? "● " : "○ ") + this.handler.titles.get(index),
					12, 40 + i * 20, this.handler.curator ? 210 : 296, done ? AcTheme.GREEN : AcTheme.MUTED);
		}
	}
}
