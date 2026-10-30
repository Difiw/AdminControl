package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.admincontrol.screen.PlayFeaturesScreenHandler;
import ru.admincontrol.util.Money;

public class PlayFeaturesScreen extends HandledScreen<PlayFeaturesScreenHandler> {
	private static final int ROW = 22;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private int signature = Integer.MIN_VALUE;

	public PlayFeaturesScreen(PlayFeaturesScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private int totalRows() {
		return 56;
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
		int sig = (this.handler.daily() ? 1 : 0) + this.handler.joinWc()
				+ this.handler.breakGoal() + this.handler.breakWc()
				+ this.handler.killGoal() + this.handler.killWc()
				+ (this.handler.tpa() ? 3 : 0) + this.handler.tpaSec()
				+ (this.handler.shop() ? 5 : 0) + this.handler.homePrice()
				+ this.handler.luckPrice() + this.handler.powerPrice()
				+ (this.handler.clans() ? 7 : 0) + this.handler.clanPrice() + this.handler.clanMax()
				+ (this.handler.auto() ? 11 : 0) + this.handler.autoMin() + this.handler.autoType()
				+ this.handler.autoReward() + (this.handler.week() ? 13 : 0) + this.handler.weekWc()
				+ (this.handler.homeFree() ? 17 : 0)
				+ this.handler.privatePrice(1) + this.handler.privatePrice(2) * 3 + this.handler.privatePrice(3) * 5
				+ this.handler.privatePrice(4) * 7 + this.handler.privatePrice(5) * 11
				+ this.handler.pack1() + this.handler.pack2() + this.handler.pack3()
				+ this.handler.pack4() + this.handler.pack5()
				+ (this.handler.joinOn() ? 23 : 0) + (this.handler.breakOn() ? 29 : 0)
				+ (this.handler.killOn() ? 31 : 0) + (this.handler.placeOn() ? 37 : 0)
				+ this.handler.placeGoal() + this.handler.placeWc()
				+ (this.handler.playOn() ? 41 : 0) + this.handler.playMin() + this.handler.playWc()
				+ (this.handler.chatOn() ? 43 : 0) + this.handler.chatGoal() + this.handler.chatWc()
				+ (this.handler.casOn() ? 47 : 0) + this.handler.casGoal() + this.handler.casWc()
				+ (this.handler.eatOn() ? 53 : 0) + this.handler.eatGoal() + this.handler.eatWc()
				+ (this.handler.pvpOn() ? 59 : 0) + this.handler.pvpGoal() + this.handler.pvpWc()
				+ (this.handler.autoBox() ? 61 : 0) + this.handler.autoBoxMin() + this.handler.boxCooldown();
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
		PlayFeaturesScreenHandler h = this.handler;
		switch (row) {
			case 0 -> toggle(x, y, w, "Ежедневки: " + onOff(h.daily()), PlayFeaturesScreenHandler.DAILY);
			case 1 -> toggle(x, y, w, "Задание «вход»: " + onOff(h.joinOn()), PlayFeaturesScreenHandler.JOIN_ON);
			case 2 -> pair(x, y, "Награда за вход: " + Money.wc(h.joinWc()), PlayFeaturesScreenHandler.JOIN_M, PlayFeaturesScreenHandler.JOIN_P);
			case 3 -> toggle(x, y, w, "Задание «блоки»: " + onOff(h.breakOn()), PlayFeaturesScreenHandler.BREAK_ON);
			case 4 -> pair(x, y, "Цель блоков: " + h.breakGoal(), PlayFeaturesScreenHandler.BREAK_G_M, PlayFeaturesScreenHandler.BREAK_G_P);
			case 5 -> pair(x, y, "Награда блоков: " + Money.wc(h.breakWc()), PlayFeaturesScreenHandler.BREAK_W_M, PlayFeaturesScreenHandler.BREAK_W_P);
			case 6 -> toggle(x, y, w, "Задание «мобы»: " + onOff(h.killOn()), PlayFeaturesScreenHandler.KILL_ON);
			case 7 -> pair(x, y, "Цель мобов: " + h.killGoal(), PlayFeaturesScreenHandler.KILL_G_M, PlayFeaturesScreenHandler.KILL_G_P);
			case 8 -> pair(x, y, "Награда мобов: " + Money.wc(h.killWc()), PlayFeaturesScreenHandler.KILL_W_M, PlayFeaturesScreenHandler.KILL_W_P);
			case 9 -> toggle(x, y, w, "Задание «постройка»: " + onOff(h.placeOn()), PlayFeaturesScreenHandler.PLACE_ON);
			case 10 -> pair(x, y, "Цель поставлено: " + h.placeGoal(), PlayFeaturesScreenHandler.PLACE_G_M, PlayFeaturesScreenHandler.PLACE_G_P);
			case 11 -> pair(x, y, "Награда постройки: " + Money.wc(h.placeWc()), PlayFeaturesScreenHandler.PLACE_W_M, PlayFeaturesScreenHandler.PLACE_W_P);
			case 12 -> toggle(x, y, w, "Задание «онлайн»: " + onOff(h.playOn()), PlayFeaturesScreenHandler.PLAY_ON);
			case 13 -> pair(x, y, "Цель онлайн: " + h.playMin() + " мин", PlayFeaturesScreenHandler.PLAY_G_M, PlayFeaturesScreenHandler.PLAY_G_P);
			case 14 -> pair(x, y, "Награда онлайна: " + Money.wc(h.playWc()), PlayFeaturesScreenHandler.PLAY_W_M, PlayFeaturesScreenHandler.PLAY_W_P);
			case 15 -> toggle(x, y, w, "Задание «чат»: " + onOff(h.chatOn()), PlayFeaturesScreenHandler.CHAT_ON);
			case 16 -> pair(x, y, "Цель сообщений: " + h.chatGoal(), PlayFeaturesScreenHandler.CHAT_G_M, PlayFeaturesScreenHandler.CHAT_G_P);
			case 17 -> pair(x, y, "Награда чата: " + Money.wc(h.chatWc()), PlayFeaturesScreenHandler.CHAT_W_M, PlayFeaturesScreenHandler.CHAT_W_P);
			case 18 -> toggle(x, y, w, "Задание «казино»: " + onOff(h.casOn()), PlayFeaturesScreenHandler.CAS_ON);
			case 19 -> pair(x, y, "Цель игр казино: " + h.casGoal(), PlayFeaturesScreenHandler.CAS_G_M, PlayFeaturesScreenHandler.CAS_G_P);
			case 20 -> pair(x, y, "Награда казино: " + Money.wc(h.casWc()), PlayFeaturesScreenHandler.CAS_W_M, PlayFeaturesScreenHandler.CAS_W_P);
			case 21 -> toggle(x, y, w, "Задание «еда»: " + onOff(h.eatOn()), PlayFeaturesScreenHandler.EAT_ON);
			case 22 -> pair(x, y, "Цель приёмов пищи: " + h.eatGoal(), PlayFeaturesScreenHandler.EAT_G_M, PlayFeaturesScreenHandler.EAT_G_P);
			case 23 -> pair(x, y, "Награда еды: " + Money.wc(h.eatWc()), PlayFeaturesScreenHandler.EAT_W_M, PlayFeaturesScreenHandler.EAT_W_P);
			case 24 -> toggle(x, y, w, "Задание «PvP»: " + onOff(h.pvpOn()), PlayFeaturesScreenHandler.PVP_ON);
			case 25 -> pair(x, y, "Цель убийств игроков: " + h.pvpGoal(), PlayFeaturesScreenHandler.PVP_G_M, PlayFeaturesScreenHandler.PVP_G_P);
			case 26 -> pair(x, y, "Награда PvP: " + Money.wc(h.pvpWc()), PlayFeaturesScreenHandler.PVP_W_M, PlayFeaturesScreenHandler.PVP_W_P);
			case 27 -> toggle(x, y, w, "TPA: " + onOff(h.tpa()), PlayFeaturesScreenHandler.TPA);
			case 28 -> pair(x, y, "TPA секунд: " + h.tpaSec(), PlayFeaturesScreenHandler.TPA_M, PlayFeaturesScreenHandler.TPA_P);
			case 29 -> toggle(x, y, w, "/thome без читов: " + onOff(h.homeFree()), PlayFeaturesScreenHandler.HOME_FREE);
			case 30 -> toggle(x, y, w, "Магазин: " + onOff(h.shop()), PlayFeaturesScreenHandler.SHOP);
			case 31 -> pair(x, y, "Цена слота дома: " + Money.wc(h.homePrice()), PlayFeaturesScreenHandler.HOME_M, PlayFeaturesScreenHandler.HOME_P);
			case 32 -> pair(x, y, "Цена удачи: " + Money.wc(h.luckPrice()), PlayFeaturesScreenHandler.LUCK_M, PlayFeaturesScreenHandler.LUCK_P);
			case 33 -> pair(x, y, "Цена силы: " + Money.wc(h.powerPrice()), PlayFeaturesScreenHandler.POWER_M, PlayFeaturesScreenHandler.POWER_P);
			case 34 -> toggle(x, y, w, "Кланы: " + onOff(h.clans()), PlayFeaturesScreenHandler.CLAN);
			case 35 -> pair(x, y, "Цена клана: " + Money.wc(h.clanPrice()), PlayFeaturesScreenHandler.CLAN_P_M, PlayFeaturesScreenHandler.CLAN_P_P);
			case 36 -> pair(x, y, "Макс. в клане: " + h.clanMax(), PlayFeaturesScreenHandler.CLAN_MAX_M, PlayFeaturesScreenHandler.CLAN_MAX_P);
			case 37 -> toggle(x, y, w, "Авто-МП: " + onOff(h.auto()), PlayFeaturesScreenHandler.AUTO);
			case 38 -> pair(x, y, "Интервал МП: " + h.autoMin() + " мин", PlayFeaturesScreenHandler.AUTO_M, PlayFeaturesScreenHandler.AUTO_P);
			case 39 -> toggle(x, y, w, "Тип МП: " + h.autoTypeLabel(), PlayFeaturesScreenHandler.AUTO_TYPE);
			case 40 -> pair(x, y, "Награда МП: " + Money.wc(h.autoReward()), PlayFeaturesScreenHandler.AUTO_R_M, PlayFeaturesScreenHandler.AUTO_R_P);
			case 41 -> toggle(x, y, w, "Недельный топ: " + onOff(h.week()), PlayFeaturesScreenHandler.WEEK);
			case 42 -> pair(x, y, "Награда топа: " + Money.wc(h.weekWc()), PlayFeaturesScreenHandler.WEEK_M, PlayFeaturesScreenHandler.WEEK_P);
			case 43 -> pair(x, y, "Приват ур.1 (медь): " + Money.wc(h.privatePrice(1)), PlayFeaturesScreenHandler.PRIV_M, PlayFeaturesScreenHandler.PRIV_P);
			case 44 -> pair(x, y, "Приват ур.2 (золото): " + Money.wc(h.privatePrice(2)), PlayFeaturesScreenHandler.SIZE_M, PlayFeaturesScreenHandler.SIZE_P);
			case 45 -> pair(x, y, "Приват ур.3 (железо): " + Money.wc(h.privatePrice(3)), PlayFeaturesScreenHandler.PRIV3_M, PlayFeaturesScreenHandler.PRIV3_P);
			case 46 -> pair(x, y, "Приват ур.4 (алмаз): " + Money.wc(h.privatePrice(4)), PlayFeaturesScreenHandler.PRIV4_M, PlayFeaturesScreenHandler.PRIV4_P);
			case 47 -> pair(x, y, "Приват ур.5 (незерит): " + Money.wc(h.privatePrice(5)), PlayFeaturesScreenHandler.PRIV5_M, PlayFeaturesScreenHandler.PRIV5_P);
			case 48 -> pair(x, y, "Рюкзак 1 ур.: " + Money.wc(h.pack1()), PlayFeaturesScreenHandler.PACK1_M, PlayFeaturesScreenHandler.PACK1_P);
			case 49 -> pair(x, y, "Рюкзак 2 ур.: " + Money.wc(h.pack2()), PlayFeaturesScreenHandler.PACK2_M, PlayFeaturesScreenHandler.PACK2_P);
			case 50 -> pair(x, y, "Рюкзак 3 ур.: " + Money.wc(h.pack3()), PlayFeaturesScreenHandler.PACK3_M, PlayFeaturesScreenHandler.PACK3_P);
			case 51 -> pair(x, y, "Рюкзак 4 ур.: " + Money.wc(h.pack4()), PlayFeaturesScreenHandler.PACK4_M, PlayFeaturesScreenHandler.PACK4_P);
			case 52 -> pair(x, y, "Рюкзак 5 ур.: " + Money.wc(h.pack5()), PlayFeaturesScreenHandler.PACK5_M, PlayFeaturesScreenHandler.PACK5_P);
			case 53 -> toggle(x, y, w, "Авто-бокс: " + onOff(h.autoBox()), PlayFeaturesScreenHandler.AUTO_BOX);
			case 54 -> pair(x, y, "Интервал бокса: " + h.autoBoxMin() + " мин", PlayFeaturesScreenHandler.AUTO_BOX_M, PlayFeaturesScreenHandler.AUTO_BOX_P);
			case 55 -> pair(x, y, "Пауза между боксами: "
					+ (h.boxCooldown() == 0 ? "выкл" : h.boxCooldown() + " мин"),
					PlayFeaturesScreenHandler.BOX_COOLDOWN_M, PlayFeaturesScreenHandler.BOX_COOLDOWN_P);
			default -> {
			}
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

	private static String onOff(boolean on) {
		return on ? "§aвкл" : "§cвыкл";
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
		AcTheme.titleCentered(this.textRenderer, context, "Игрокам (ЗГА+)", 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, visibleRows() * ROW, maxScroll(), visibleRows(), this.scroll);
		}
	}
}
