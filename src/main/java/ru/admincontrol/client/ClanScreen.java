package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.ClanScreenHandler;
import ru.admincontrol.util.Money;

import java.util.List;

/** Окно клана: вкладки слева, содержимое справа. */
public class ClanScreen extends HandledScreen<ClanScreenHandler> {
	private static final int W = 344;
	private static final int H = 220;
	private static final int HEAD = 22;
	private static final int SIDE_X = 6;
	private static final int SIDE_W = 80;
	private static final int TAB_Y = 28;
	private static final int TAB_STEP = 19;
	private static final int BODY_X = 92;
	private static final int BODY_Y = 28;
	private static final int PAD = 6;
	private static final int BODY_W = W - BODY_X - 8;
	private static final int CX = BODY_X + PAD;
	private static final int CW = BODY_W - PAD * 2;

	private static final int INFO_ROW = 14;
	private static final int MEMBER_ROWS = 7;
	private static final int MEMBER_ROW = 17;
	private static final int MEMBERS_Y = 48;
	private static final int UPG_ROW = 24;
	private static final int FLAG_ROW = 13;
	private static final int FLAGS_Y = 52;
	private static final int COL_W = 27;

	private enum Tab {
		INFO("Главная"), MEMBERS("Участники"), BANK("Казна"), UPGRADES("Улучшения"),
		RANKS("Доступы"), TOP("Рейтинг"), CREATE("Создать клан");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private TextFieldWidget field;
	private Tab tab = Tab.INFO;
	private int scroll;

	public ClanScreen(ClanScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = W;
		this.backgroundHeight = H;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	public void syncFromServer(PacketByteBuf buf) {
		String keep = this.field == null ? "" : this.field.getText();
		boolean focus = this.field != null && this.field.isFocused();
		this.handler.applyPacket(buf);
		rebuild();
		if (this.field != null) {
			this.field.setText(keep);
			if (focus) {
				this.field.setFocused(true);
				this.setFocused(this.field);
			}
		}
	}

	private Tab[] tabs() {
		if (!this.handler.inClan) {
			return new Tab[]{Tab.CREATE, Tab.TOP};
		}
		if (this.handler.can(ClanRecord.GRANT)) {
			return new Tab[]{Tab.INFO, Tab.MEMBERS, Tab.BANK, Tab.UPGRADES, Tab.RANKS, Tab.TOP};
		}
		return new Tab[]{Tab.INFO, Tab.MEMBERS, Tab.BANK, Tab.UPGRADES, Tab.TOP};
	}

	private void fixTab() {
		for (Tab t : tabs()) {
			if (t == this.tab) {
				return;
			}
		}
		this.tab = tabs()[0];
	}

	private void rebuild() {
		this.clearChildren();
		this.field = null;
		if (!this.handler.enabled) {
			return;
		}
		fixTab();
		Tab[] list = tabs();
		for (int i = 0; i < list.length; i++) {
			Tab t = list[i];
			ButtonWidget b = btn(t.label, this.x + SIDE_X, this.y + TAB_Y + i * TAB_STEP, SIDE_W, 16, w -> {
				this.tab = t;
				this.scroll = 0;
				rebuild();
			});
			b.active = t != this.tab;
		}
		if (this.handler.inClan) {
			int bottom = this.y + H - 8 - 16;
			if (this.handler.owner) {
				btn("Распустить", this.x + SIDE_X, bottom, SIDE_W, 16, b -> click(ClanScreenHandler.DISBAND));
			} else {
				btn("Покинуть", this.x + SIDE_X, bottom, SIDE_W, 16, b -> click(ClanScreenHandler.LEAVE));
			}
			if (this.handler.canLogs) {
				btn("Логи клана", this.x + SIDE_X, bottom - TAB_STEP, SIDE_W, 16, b -> click(ClanScreenHandler.LOGS));
			}
		}
		switch (this.tab) {
			case INFO -> buildInfo();
			case MEMBERS -> buildMembers();
			case BANK -> buildBank();
			case UPGRADES -> buildUpgrades();
			case RANKS -> buildRanks();
			case CREATE -> buildCreate();
			default -> {
			}
		}
	}

	// ---------- вкладки: кнопки ----------

	private void buildInfo() {
		int left = this.x + CX;
		int y2 = this.y + H - 8 - PAD - 16;
		int y1 = y2 - 20;
		boolean tp = this.handler.hasHome && this.handler.can(ClanRecord.TP);
		boolean wh = this.handler.warehouseLevel > 0
				&& (this.handler.can(ClanRecord.WH_PUT) || this.handler.can(ClanRecord.WH_TAKE));
		int half = (CW - 4) / 2;
		ButtonWidget home = btn("ТП на дом клана", left, y2, half, 16, b -> click(ClanScreenHandler.HOME));
		home.active = tp;
		ButtonWidget store = btn("Склад клана", left + half + 4, y2, half, 16, b -> click(ClanScreenHandler.WAREHOUSE));
		store.active = wh;
		if (this.handler.owner) {
			addField(left, y1, CW - 100);
			btn("Переименовать", left + CW - 96, y1, 96, 16, b -> send("rename"));
		}
	}

	private void buildMembers() {
		int left = this.x + CX;
		this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
		if (maxScroll() > 0) {
			btn("▲", left + CW - 34, this.y + BODY_Y + 4, 16, 12, b -> scrollBy(-1));
			btn("▼", left + CW - 16, this.y + BODY_Y + 4, 16, 12, b -> scrollBy(1));
		}
		for (int i = 0; i < MEMBER_ROWS; i++) {
			int index = this.scroll + i;
			if (index >= this.handler.members.size()) {
				break;
			}
			int rank = this.handler.memberRanks.get(index);
			if (!this.handler.canManage(rank)) {
				continue;
			}
			int rowY = this.y + MEMBERS_Y + i * MEMBER_ROW + 1;
			int bx = left + CW - 50;
			ButtonWidget up = btn("▲", bx, rowY, 16, 14, b -> click(ClanScreenHandler.PROMOTE_BASE + index));
			up.active = rank + 1 < this.handler.myRank && rank < ClanRecord.DEPUTY;
			ButtonWidget down = btn("▼", bx + 17, rowY, 16, 14, b -> click(ClanScreenHandler.DEMOTE_BASE + index));
			down.active = rank > 1;
			btn("✕", bx + 34, rowY, 16, 14, b -> click(ClanScreenHandler.KICK_BASE + index));
		}
		if (this.handler.can(ClanRecord.INVITE)) {
			int y = this.y + H - 8 - PAD - 16;
			addField(left, y, CW - 90);
			btn("Пригласить", left + CW - 86, y, 86, 16, b -> send("invite"));
		}
	}

	private void buildBank() {
		int left = this.x + CX;
		int each = (CW - 8) / 3;
		int dep = this.y + 98;
		int wit = this.y + 138;
		int[] dIds = {ClanScreenHandler.DEP_1, ClanScreenHandler.DEP_10, ClanScreenHandler.DEP_50};
		int[] wIds = {ClanScreenHandler.WITH_1, ClanScreenHandler.WITH_10, ClanScreenHandler.WITH_50};
		String[] amounts = {"1", "10", "50"};
		for (int i = 0; i < 3; i++) {
			int id = dIds[i];
			ButtonWidget b = btn("+" + amounts[i] + " WC", left + i * (each + 4), dep, each, 16, w -> click(id));
			b.active = this.handler.can(ClanRecord.DEPOSIT);
		}
		for (int i = 0; i < 3; i++) {
			int id = wIds[i];
			ButtonWidget b = btn("−" + amounts[i] + " WC", left + i * (each + 4), wit, each, 16, w -> click(id));
			b.active = this.handler.can(ClanRecord.WITHDRAW);
		}
	}

	private void buildUpgrades() {
		boolean spend = this.handler.can(ClanRecord.SPEND);
		boolean home = this.handler.hasHome;
		int bx = this.x + CX + CW - 72;
		upgradeBtn(0, home ? "Куплен" : "Купить", spend && !home, ClanScreenHandler.BUY_HOME, bx);
		upgradeBtn(1, this.handler.homePrivate ? "Куплен" : "Купить", spend && home && !this.handler.homePrivate,
				ClanScreenHandler.BUY_PRIV, bx);
		upgradeBtn(2, "Улучшить", spend && home, ClanScreenHandler.UPGRADE_HALL, bx);
		upgradeBtn(3, "Улучшить", spend && home, ClanScreenHandler.UPGRADE_WH, bx);
		upgradeBtn(4, this.handler.bankLevel <= 0 ? "Построить" : "Улучшить", spend && home,
				ClanScreenHandler.BUY_BANK, bx);
		upgradeBtn(5, "Улучшить", spend && this.handler.bankLevel > 0, ClanScreenHandler.UPGRADE_CAP, bx);
	}

	private void upgradeBtn(int row, String label, boolean active, int id, int bx) {
		ButtonWidget b = btn(label, bx, this.y + BODY_Y + PAD + row * UPG_ROW + 3, 72, 16, w -> click(id));
		b.active = active;
	}

	private void buildRanks() {
		for (int i = 0; i < ClanRecord.FLAG_ORDER.length; i++) {
			int flag = ClanRecord.FLAG_ORDER[i];
			int bit = Integer.numberOfTrailingZeros(flag);
			int rowY = this.y + FLAGS_Y + i * FLAG_ROW;
			for (int rank = 1; rank <= ClanRecord.DEPUTY; rank++) {
				boolean on = (this.handler.rankFlags[rank] & flag) != 0;
				int id = ClanScreenHandler.RANK_FLAG_BASE + rank * 32 + bit;
				ButtonWidget toggle = btn(on ? "§a✔" : "§c✘", this.x + colX(rank), rowY, COL_W, 12, b -> click(id));
				toggle.active = rank < this.handler.myRank && this.handler.can(flag);
			}
		}
	}

	private static int colX(int rank) {
		return CX + CW - (ClanRecord.DEPUTY - rank + 1) * (COL_W + 2);
	}

	private void buildCreate() {
		int left = this.x + CX;
		addField(left, this.y + 82, CW);
		btn("Создать  •  " + Money.wc(this.handler.price), left, this.y + 104, CW, 16, b -> send("create"));
		if (this.handler.hasInvite) {
			btn("Принять приглашение", left, this.y + 130, CW, 16, b -> click(ClanScreenHandler.ACCEPT));
		}
	}

	// ---------- отрисовка ----------

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, W, H, HEAD);
		if (!this.handler.enabled) {
			return;
		}
		AcTheme.row(context, this.x + SIDE_X - 2, this.y + BODY_Y - 2, SIDE_W + 4, H - BODY_Y - 6);
		AcTheme.row(context, this.x + BODY_X, this.y + BODY_Y - 2, BODY_W, H - BODY_Y - 6);
		Tab[] list = tabs();
		for (int i = 0; i < list.length; i++) {
			if (list[i] == this.tab) {
				int ty = this.y + TAB_Y + i * TAB_STEP;
				context.fill(this.x + SIDE_X - 2, ty, this.x + SIDE_X, ty + 16, 0xFF000000 | AcTheme.GOLD);
			}
		}
		int left = this.x + CX;
		if (this.tab == Tab.INFO) {
			for (int i = 0; i < 8; i++) {
				if (i % 2 == 0) {
					AcTheme.row(context, left - 2, this.y + BODY_Y + PAD + i * INFO_ROW - 2, CW + 4, INFO_ROW);
				}
			}
		} else if (this.tab == Tab.MEMBERS) {
			for (int i = 0; i < MEMBER_ROWS; i++) {
				if (this.scroll + i < this.handler.members.size() && i % 2 == 0) {
					AcTheme.row(context, left - 2, this.y + MEMBERS_Y + i * MEMBER_ROW, CW + 4, MEMBER_ROW);
				}
			}
		} else if (this.tab == Tab.UPGRADES) {
			for (int i = 0; i < 6; i++) {
				if (i % 2 == 0) {
					AcTheme.row(context, left - 2, this.y + BODY_Y + PAD + i * UPG_ROW, CW + 4, UPG_ROW);
				}
			}
		} else if (this.tab == Tab.RANKS) {
			for (int i = 0; i < ClanRecord.FLAG_ORDER.length; i++) {
				if (i % 2 == 0) {
					AcTheme.row(context, left - 2, this.y + FLAGS_Y + i * FLAG_ROW - 1, CW + 4, FLAG_ROW);
				}
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		if (!this.handler.enabled) {
			AcTheme.titleCentered(this.textRenderer, context, "Кланы выключены", 8, 7, W - 16);
			return;
		}
		if (this.handler.inClan) {
			AcTheme.clipped(this.textRenderer, context, "Клан «" + this.handler.name + "»", 8, 7, W - 130, AcTheme.GOLD);
			String me = "Вы: " + ClanRecord.rankLabel(this.handler.myRank);
			context.drawText(this.textRenderer, me, W - 10 - this.textRenderer.getWidth(me), 7, AcTheme.MUTED, false);
		} else {
			AcTheme.title(this.textRenderer, context, "Кланы", 8, 7);
		}
		switch (this.tab) {
			case INFO -> drawInfo(context);
			case MEMBERS -> drawMembers(context);
			case BANK -> drawBank(context);
			case UPGRADES -> drawUpgrades(context);
			case RANKS -> drawRanks(context);
			case TOP -> drawTop(context);
			case CREATE -> drawCreate(context);
		}
	}

	private void info(DrawContext context, int row, String label, String value, int color) {
		int y = BODY_Y + PAD + row * INFO_ROW + 1;
		AcTheme.clipped(this.textRenderer, context, label, CX + 2, y, 110, AcTheme.MUTED);
		AcTheme.clipped(this.textRenderer, context, value, CX + 116, y, CW - 118, color);
	}

	private void drawInfo(DrawContext context) {
		int online = 0;
		for (Boolean on : this.handler.memberOnline) {
			if (Boolean.TRUE.equals(on)) {
				online++;
			}
		}
		info(context, 0, "Глава", this.handler.ownerNick, AcTheme.GOLD);
		info(context, 1, "Участники", this.handler.members.size() + "  ·  в сети " + online, AcTheme.TEXT);
		info(context, 2, "Ваш уровень", ClanRecord.rankLabel(this.handler.myRank), AcTheme.TEXT);
		info(context, 3, "Казна", Money.wc(this.handler.treasury()) + " / " + Money.wc(this.handler.treasuryCap),
				AcTheme.GOLD);
		info(context, 4, "Очки за неделю", this.handler.weekPoints + place(this.handler.weekPlace), AcTheme.GOLD);
		info(context, 5, "  победы недели", "МП " + this.handler.weekMp + "  ·  битвы " + this.handler.weekWar,
				AcTheme.TEXT);
		info(context, 6, "Очки всего", this.handler.totalPoints + place(this.handler.totalPlace), AcTheme.GOLD);
		info(context, 7, "  победы всего", "МП " + this.handler.totalMp + "  ·  битвы " + this.handler.totalWar,
				AcTheme.TEXT);
	}

	private static String place(int place) {
		return place > 0 ? "   (" + place + " место)" : "";
	}

	private void drawMembers(DrawContext context) {
		int hy = BODY_Y + 6;
		AcTheme.clipped(this.textRenderer, context, "Ник", CX + 2, hy, 80, AcTheme.GOLD);
		AcTheme.clipped(this.textRenderer, context, "Уровень", CX + 88, hy, 60, AcTheme.GOLD);
		AcTheme.clipped(this.textRenderer, context, "Статус", CX + 150, hy, 34, AcTheme.GOLD);
		if (this.handler.members.isEmpty()) {
			AcTheme.clipped(this.textRenderer, context, "В клане пока никого.", CX + 2, MEMBERS_Y + 4, CW, AcTheme.MUTED);
		}
		for (int i = 0; i < MEMBER_ROWS; i++) {
			int index = this.scroll + i;
			if (index >= this.handler.members.size()) {
				break;
			}
			int rank = this.handler.memberRanks.get(index);
			boolean on = index < this.handler.memberOnline.size() && this.handler.memberOnline.get(index);
			int y = MEMBERS_Y + i * MEMBER_ROW + 5;
			AcTheme.clipped(this.textRenderer, context, this.handler.members.get(index), CX + 2, y, 84,
					rank >= ClanRecord.DEPUTY ? AcTheme.GOLD : AcTheme.TEXT);
			AcTheme.clipped(this.textRenderer, context, ClanRecord.rankLabel(rank), CX + 88, y, 60, AcTheme.MUTED);
			AcTheme.clipped(this.textRenderer, context, on ? "в сети" : "—", CX + 150, y, 34,
					on ? AcTheme.GOLD : AcTheme.MUTED);
		}
		if (maxScroll() > 0) {
			String pages = (this.scroll + 1) + "–" + Math.min(this.handler.members.size(), this.scroll + MEMBER_ROWS)
					+ " из " + this.handler.members.size();
			AcTheme.clipped(this.textRenderer, context, pages, CX + 2, MEMBERS_Y + MEMBER_ROWS * MEMBER_ROW + 2, CW,
					AcTheme.MUTED);
		}
		if (!this.handler.can(ClanRecord.INVITE)) {
			AcTheme.clipped(this.textRenderer, context, "Приглашать может уровень с доступом «Приглашать в клан».",
					CX + 2, H - 8 - PAD - 12, CW, AcTheme.MUTED);
		}
	}

	private void drawBank(DrawContext context) {
		AcTheme.clippedCentered(this.textRenderer, context, "Казна клана", CX, BODY_Y + PAD + 2, CW, AcTheme.MUTED);
		AcTheme.clippedCentered(this.textRenderer, context,
				Money.wc(this.handler.treasury()) + "  /  " + Money.wc(this.handler.treasuryCap),
				CX, BODY_Y + PAD + 16, CW, AcTheme.GOLD);
		AcTheme.clippedCentered(this.textRenderer, context,
				"Банк ур. " + this.handler.bankLevel + "  ·  лимит казны ур. " + this.handler.treasuryLevel,
				CX, BODY_Y + PAD + 30, CW, AcTheme.MUTED);
		AcTheme.clipped(this.textRenderer, context, this.handler.can(ClanRecord.DEPOSIT)
				? "Положить из своего баланса:" : "Положить в казну — нет доступа", CX + 2, 86, CW,
				this.handler.can(ClanRecord.DEPOSIT) ? AcTheme.TEXT : AcTheme.MUTED);
		AcTheme.clipped(this.textRenderer, context, this.handler.can(ClanRecord.WITHDRAW)
				? "Снять себе на баланс:" : "Снять из казны — нет доступа", CX + 2, 126, CW,
				this.handler.can(ClanRecord.WITHDRAW) ? AcTheme.TEXT : AcTheme.MUTED);
		AcTheme.wrapped(this.textRenderer, context,
				"Каждое пополнение и снятие записывается в логи клана.", CX + 2, 166, CW, AcTheme.MUTED);
	}

	private void upgrade(DrawContext context, int row, String name, String state) {
		int y = BODY_Y + PAD + row * UPG_ROW;
		AcTheme.clipped(this.textRenderer, context, name, CX + 2, y + 3, CW - 80, AcTheme.TEXT);
		AcTheme.clipped(this.textRenderer, context, state, CX + 2, y + 13, CW - 80, AcTheme.MUTED);
	}

	private void drawUpgrades(DrawContext context) {
		int[] p = this.handler.prices;
		boolean home = this.handler.hasHome;
		upgrade(context, 0, "Дом клана", home ? "куплен" : "цена " + Money.wc(p[ClanScreenHandler.P_HOME]));
		upgrade(context, 1, "Приват дома", this.handler.homePrivate ? "куплен"
				: (home ? "цена " + Money.wc(p[ClanScreenHandler.P_PRIV]) : "нужен дом клана"));
		upgrade(context, 2, "Ратуша  ·  ур. " + Math.max(1, this.handler.hallLevel),
				home ? "след. уровень " + Money.wc(p[ClanScreenHandler.P_HALL]) : "нужен дом клана");
		upgrade(context, 3, "Склад  ·  ур. " + this.handler.warehouseLevel,
				home ? "след. уровень " + Money.wc(p[ClanScreenHandler.P_WH]) : "нужен дом клана");
		upgrade(context, 4, "Банк  ·  ур. " + this.handler.bankLevel,
				home ? (this.handler.bankLevel <= 0 ? "постройка " : "след. уровень ")
						+ Money.wc(p[ClanScreenHandler.P_BANK]) : "нужен дом клана");
		upgrade(context, 5, "Лимит казны  ·  ур. " + this.handler.treasuryLevel,
				this.handler.bankLevel > 0 ? "след. уровень " + Money.wc(p[ClanScreenHandler.P_CAP]) : "нужен банк");
		String note = this.handler.can(ClanRecord.SPEND)
				? "Оплата из казны клана."
				: "Покупать может уровень с доступом «Использовать казну».";
		AcTheme.clipped(this.textRenderer, context, note, CX + 2, BODY_Y + PAD + 6 * UPG_ROW + 6, CW, AcTheme.MUTED);
	}

	private void drawRanks(DrawContext context) {
		AcTheme.clipped(this.textRenderer, context, "Менять можно только уровни ниже своего.", CX + 2, BODY_Y + 4,
				CW, AcTheme.MUTED);
		String[] heads = {"1", "2", "3", "Зам"};
		for (int r = 1; r <= ClanRecord.DEPUTY; r++) {
			String h = heads[r - 1];
			context.drawText(this.textRenderer, h, colX(r) + (COL_W - this.textRenderer.getWidth(h)) / 2,
					FLAGS_Y - 11, AcTheme.GOLD, false);
		}
		AcTheme.clipped(this.textRenderer, context, "Доступ", CX + 2, FLAGS_Y - 11, 80, AcTheme.GOLD);
		for (int i = 0; i < ClanRecord.FLAG_ORDER.length; i++) {
			AcTheme.clipped(this.textRenderer, context, ClanRecord.flagLabel(ClanRecord.FLAG_ORDER[i]),
					CX + 2, FLAGS_Y + i * FLAG_ROW + 2, colX(1) - CX - 6, AcTheme.TEXT);
		}
	}

	private void drawTop(DrawContext context) {
		int half = (CW - 6) / 2;
		int lx = CX + 2;
		int rx = CX + half + 8;
		int y0 = BODY_Y + PAD;
		AcTheme.clipped(this.textRenderer, context, "За неделю", lx, y0, half, AcTheme.GOLD);
		AcTheme.clipped(this.textRenderer, context, "За всё время", rx, y0, half, AcTheme.GOLD);
		topColumn(context, this.handler.topWeek, this.handler.topWeekPoints, lx, y0 + 14, half);
		topColumn(context, this.handler.topTotal, this.handler.topTotalPoints, rx, y0 + 14, half);
		int by = H - 8 - PAD - 22;
		if (this.handler.inClan) {
			AcTheme.clipped(this.textRenderer, context, "Ваш клан: неделя " + this.handler.weekPoints
					+ place(this.handler.weekPlace) + "  ·  всего " + this.handler.totalPoints, CX + 2, by, CW, AcTheme.TEXT);
		}
		AcTheme.clipped(this.textRenderer, context, "Победа в МП — 10 очков, в битве кланов — 30.",
				CX + 2, by + 11, CW, AcTheme.MUTED);
	}

	private void topColumn(DrawContext context, List<String> names, List<Integer> points, int x, int y, int w) {
		if (names.isEmpty()) {
			AcTheme.clipped(this.textRenderer, context, "Пока никого.", x, y, w, AcTheme.MUTED);
			return;
		}
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			boolean mine = this.handler.inClan && name.equalsIgnoreCase(this.handler.name);
			String pts = String.valueOf(points.get(i));
			int pw = this.textRenderer.getWidth(pts);
			int yy = y + i * 12;
			AcTheme.clipped(this.textRenderer, context, (i + 1) + ". " + name, x, yy, w - pw - 6,
					mine ? AcTheme.GOLD : AcTheme.TEXT);
			context.drawText(this.textRenderer, pts, x + w - pw, yy, AcTheme.GOLD, false);
		}
	}

	private void drawCreate(DrawContext context) {
		AcTheme.clipped(this.textRenderer, context, "Создать свой клан", CX + 2, BODY_Y + PAD + 2, CW, AcTheme.GOLD);
		AcTheme.wrapped(this.textRenderer, context,
				"Название 3–16 символов. Создатель становится главой (5 уровень). Если вас пригласили — примите приглашение.",
				CX + 2, BODY_Y + PAD + 16, CW, AcTheme.MUTED);
	}

	// ---------- общее ----------

	private String fieldHint() {
		if (this.tab == Tab.CREATE) {
			return "Название клана";
		}
		if (this.tab == Tab.INFO) {
			return "Новое название клана";
		}
		return "Ник игрока";
	}

	private int maxScroll() {
		return Math.max(0, this.handler.members.size() - MEMBER_ROWS);
	}

	private void scrollBy(int delta) {
		int next = Math.max(0, Math.min(maxScroll(), this.scroll + delta));
		if (next != this.scroll) {
			this.scroll = next;
			rebuild();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.tab == Tab.MEMBERS && maxScroll() > 0) {
			scrollBy(amount > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	private void addField(int x, int y, int w) {
		this.field = new TextFieldWidget(this.textRenderer, x, y, w, 16, Text.empty());
		this.field.setMaxLength(16);
		AcHint.apply(this.field, fieldHint());
		this.addDrawableChild(this.field);
	}

	private ButtonWidget btn(String text, int x, int y, int w, int h, ButtonWidget.PressAction action) {
		return this.addDrawableChild(ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build());
	}

	private void click(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	private void send(String action) {
		if (this.client == null || this.client.getNetworkHandler() == null) {
			return;
		}
		String extra = this.field == null ? "" : this.field.getText().trim();
		var buf = PacketByteBufs.create();
		buf.writeString(action == null ? "" : action, 16);
		buf.writeString(extra, 32);
		ClientPlayNetworking.send(ModPackets.CLAN_ACTION, buf);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.field != null && this.field.isFocused() && keyCode != 256
				&& this.field.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (this.field != null && this.field.isFocused() && keyCode != 256) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.field != null && this.field.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.field != null) {
			AcHint.tick(this.field, fieldHint());
		}
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}
}
