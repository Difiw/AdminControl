package ru.admincontrol.emi.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.admincontrol.emi.EmiIncompat;

import java.util.List;

/** Экран отказа входа: перечисляет найденные несовместимые просмотрщики рецептов и объясняет, что делать. Ничего не удаляет. */
public final class CwEmiConflictScreen extends Screen {
	private final Screen parent;
	private final List<String> mods;

	public CwEmiConflictScreen(Screen parent, List<String> mods) {
		super(Text.translatable("cw_emi.conflict.title"));
		this.parent = parent;
		this.mods = mods;
	}

	@Override
	protected void init() {
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.toMenu"), b -> this.client.setScreen(parent))
				.dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		this.renderBackground(c);
		int y = Math.max(16, this.height / 2 - 70);
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y, 0xFFFF6A5A);
		y += 18;
		c.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("cw_emi.conflict.found"), this.width / 2, y, 0xFFE8F4F2);
		y += 14;
		for (String m : mods) {
			c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("• " + m), this.width / 2, y, 0xFFFFD34D);
			y += 11;
		}
		y += 8;
		for (var line : this.textRenderer.wrapLines(Text.literal(EmiIncompat.instruction()), Math.min(this.width - 40, 360))) {
			c.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, 0xFFE8F4F2);
			y += 11;
		}
		super.render(c, mx, my, delta);
	}
}
