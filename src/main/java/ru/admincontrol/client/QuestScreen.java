package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import ru.admincontrol.skill.SkillTree;

/** Отдельный полноэкранный журнал заданий без элементов дерева навыков. */
public final class QuestScreen extends Screen {
	private SkillClient.State state;
	private SkillTree.Currency selected = SkillTree.CURRENCIES.get(0);
	private int scroll;
	private int maxScroll;
	private int panelX;
	private int panelW;
	private int contentTop;
	private int contentBottom;
	private int headerBottom;

	public QuestScreen(SkillClient.State state) {
		super(Text.literal("Задания"));
		this.state = state;
	}

	public void onState(SkillClient.State state) {
		this.state = state;
	}

	@Override
	protected void init() {
		this.panelW = Math.min(680, this.width - 20);
		this.panelX = (this.width - this.panelW) / 2;
		int columns = this.panelW >= 430 ? 4 : 2;
		int rows = (SkillTree.CURRENCIES.size() + columns - 1) / columns;
		int tabW = Math.max(62, (this.panelW - 8) / columns);
		int helpLines = Math.max(1, this.textRenderer.wrapLines(Text.literal(
				"Ничего принимать не нужно — выполняйте цель, прогресс и награды считаются автоматически."),
				this.panelW - 20).size());
		this.headerBottom = 33 + helpLines * 10;
		int tabsTop = this.headerBottom + 4;
		for (int i = 0; i < SkillTree.CURRENCIES.size(); i++) {
			SkillTree.Currency currency = SkillTree.CURRENCIES.get(i);
			int col = i % columns;
			int row = i / columns;
			this.addDrawableChild(ButtonWidget.builder(Text.literal(currency.title()), b -> {
				this.selected = currency;
				this.scroll = 0;
			}).dimensions(this.panelX + 4 + col * tabW, tabsTop + row * 22, tabW - 4, 20).build());
		}
		this.contentTop = tabsTop + rows * 22 + 6;
		this.contentBottom = Math.max(this.contentTop + 40, this.height - 30);
		this.addDrawableChild(ButtonWidget.builder(Text.literal("← Дерево навыков"), b -> {
			if (this.client != null) this.client.setScreen(new SkillTreeScreen(this.state));
		}).dimensions(this.panelX, this.height - 24, 110, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Обновить"), b -> SkillClient.refresh())
				.dimensions(this.panelX + this.panelW - 164, this.height - 24, 76, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(this.panelX + this.panelW - 84, this.height - 24, 84, 20).build());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		SkillClient.closed();
		super.close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		AcTheme.panel(context, this.panelX, 8, this.panelW, this.headerBottom - 8, 0);
		context.drawText(this.textRenderer, "ЗАДАНИЯ И НАГРАДЫ", this.panelX + 10, 14, AcTheme.TITLE, false);
		AcTheme.wrapped(this.textRenderer, context,
				"Ничего принимать не нужно — выполняйте цель, прогресс и награды считаются автоматически.",
				this.panelX + 10, 27, this.panelW - 20, 0xBBBBBB);
		AcTheme.panel(context, this.panelX, this.contentTop, this.panelW, this.contentBottom - this.contentTop, 0);
		context.enableScissor(this.panelX + 2, this.contentTop + 2, this.panelX + this.panelW - 2, this.contentBottom - 2);
		int y = this.contentTop + 10 - this.scroll;
		y = section(context, y, "ЕЖЕДНЕВНОЕ ЗАДАНИЕ", "Обновляется каждый день");
		for (SkillTree.Daily d : SkillTree.DAILIES) {
			if (d.currency() == this.selected) y = dailyCard(context, d, y);
		}
		y += 5;
		y = section(context, y, "ДОЛГОСРОЧНЫЕ ЗАДАНИЯ", "Несколько этапов, каждый этап даёт награду");
		for (SkillTree.Quest q : SkillTree.QUESTS) {
			if (q.currency() == this.selected) y = questCard(context, q, y);
		}
		this.maxScroll = Math.max(0, y + this.scroll - this.contentBottom + 8);
		this.scroll = Math.min(this.scroll, this.maxScroll);
		context.disableScissor();
		if (this.maxScroll > 0) {
			int h = this.contentBottom - this.contentTop - 8;
			int bar = Math.max(20, h * h / (h + this.maxScroll));
			int by = this.contentTop + 4 + (h - bar) * this.scroll / this.maxScroll;
			context.fill(this.panelX + this.panelW - 6, this.contentTop + 4, this.panelX + this.panelW - 3, this.contentBottom - 4, 0x40333333);
			context.fill(this.panelX + this.panelW - 6, by, this.panelX + this.panelW - 3, by + bar, 0xFFFFD34D);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private int section(DrawContext context, int y, String title, String subtitle) {
		context.drawText(this.textRenderer, title, this.panelX + 12, y, AcTheme.TITLE, false);
		context.drawText(this.textRenderer, subtitle, this.panelX + 12 + this.textRenderer.getWidth(title) + 8, y, 0x888888, false);
		return y + 16;
	}

	private int dailyCard(DrawContext context, SkillTree.Daily d, int y) {
		SkillClient.DailyState ds = this.state.dailies.get(d.id());
		long value = ds == null ? 0 : ds.progress();
		boolean done = ds != null && ds.done();
		int h = 62;
		card(context, y, h, done);
		context.drawText(this.textRenderer, d.title().replace("Сегодня: ", ""), this.panelX + 14, y + 7, 0xFFFFFF, false);
		context.drawText(this.textRenderer, done ? "ГОТОВО • награда получена" : "Награда: +" + d.reward() + " " + d.currency().title(),
				this.panelX + 14, y + 19, done ? 0x55FF77 : AcTheme.TITLE, false);
		progress(context, y + 45, value, d.goal(), d.divisor(), d.unit(), done);
		return y + h + 7;
	}

	private int questCard(DrawContext context, SkillTree.Quest q, int y) {
		SkillClient.QuestState qs = this.state.quests.get(q.id());
		long value = qs == null ? 0 : qs.progress();
		int stage = qs == null ? 0 : qs.stage();
		boolean done = stage >= q.stages();
		int descLines = Math.max(1, this.textRenderer.wrapLines(Text.literal(q.description()), this.panelW - 32).size());
		int h = 78 + descLines * 10;
		card(context, y, h, done);
		String stageText = done ? "ВСЕ ЭТАПЫ ВЫПОЛНЕНЫ" : "Этап " + (stage + 1) + " из " + q.stages();
		context.drawText(this.textRenderer, q.title(), this.panelX + 14, y + 7, done ? 0x55FF77 : 0xFFFFFF, false);
		context.drawText(this.textRenderer, stageText, this.panelX + this.panelW - 14 - this.textRenderer.getWidth(stageText), y + 7,
				done ? 0x55FF77 : 0xAAAAAA, false);
		int dy = AcTheme.wrapped(this.textRenderer, context, q.description(), this.panelX + 14, y + 23, this.panelW - 28, 0xAAAAAA);
		if (done) {
			context.drawText(this.textRenderer, "✓ Задание завершено", this.panelX + 14, dy + 5, 0x55FF77, false);
		} else {
			context.drawText(this.textRenderer, "Награда этапа: +" + q.rewards()[stage] + " " + q.currency().title(), this.panelX + 14, dy + 5, AcTheme.TITLE, false);
			progress(context, dy + 31, value, q.goals()[stage], q.divisor(), q.unit(), false);
		}
		return y + h + 7;
	}

	private void card(DrawContext context, int y, int h, boolean done) {
		int x = this.panelX + 8;
		int w = this.panelW - 18;
		context.fill(x, y, x + w, y + h, done ? 0x80204328 : 0x80222222);
		context.fill(x, y, x + 3, y + h, done ? 0xFF55CC77 : 0xFF000000 | this.selected.color());
	}

	private void progress(DrawContext context, int y, long value, long goal, int divisor, String unit, boolean done) {
		int x = this.panelX + 14;
		int w = this.panelW - 32;
		double ratio = goal <= 0 ? 1 : Math.min(1.0, value / (double) goal);
		context.fill(x, y, x + w, y + 5, 0xFF333333);
		context.fill(x, y, x + (int) (w * ratio), y + 5, done ? 0xFF55CC77 : 0xFF000000 | this.selected.color());
		String text = SkillTree.format(Math.min(value, goal), divisor, unit) + " / " + SkillTree.format(goal, divisor, unit);
		context.drawText(this.textRenderer, text, x, y - 9, 0xDDDDDD, false);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= this.panelX && mouseX <= this.panelX + this.panelW && mouseY >= this.contentTop && mouseY <= this.contentBottom) {
			this.scroll = MathHelper.clamp(this.scroll - (int) (amount * 28), 0, this.maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}
}
