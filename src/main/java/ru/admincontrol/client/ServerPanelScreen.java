package ru.admincontrol.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.screen.ServerPanelScreenHandler;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Money;
import ru.admincontrol.util.NickColors;

public class ServerPanelScreen extends HandledScreen<ServerPanelScreenHandler> {
	private static final int ROW = 22;
	/** −1 = хаб модулей; иначе индекс модуля. */
	private int module = -1;
	private final AcScrollBar bar = new AcScrollBar();
	private int scroll;
	private int signature = Integer.MIN_VALUE;
	private final java.util.Map<TextFieldWidget, String> colorFields = new java.util.IdentityHashMap<>();

	private static final String[] DEV_MODULES = {
			"Сервер и доступ",
			"Экономика и аукцион",
			"Казино",
			"Мир и погода",
			"МП, тренировки и бокс",
			"Кланы и битвы",
			"Рюкзаки",
			"Цвета и теги",
			"Права и фичи",
			"Sync: тренировки и протеин",
			"CW Expedition / сюжет",
			"Настройки крафтов"
	};
	private static final String[] STAFF_MODULES = {
			"Сервер и доступ",
			"Экономика",
			"Мир",
			"МП и тренировки",
			"Права и фичи"
	};

	public ServerPanelScreen(ServerPanelScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 256;
		this.backgroundHeight = 230;
		this.playerInventoryTitleY = 1000;
	}

	private String[] modules() {
		return this.handler.isDev() ? DEV_MODULES : STAFF_MODULES;
	}

	private int totalRows() {
		if (this.module < 0) {
			return modules().length;
		}
		return moduleLogicals().length + 1;
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
		int sig = (this.handler.locked() ? 1 : 0)
				+ this.handler.lockMode() * 2
				+ this.handler.sellPrice() * 5
				+ this.handler.aucMin() * 7
				+ this.handler.aucMax() * 11
				+ this.handler.chance() * 13
				+ this.handler.casinoMinBet() * 83
				+ this.handler.casinoMin() * 17
				+ this.handler.casinoMax() * 19
				+ this.handler.timeOfDay()
				+ this.handler.weather() * 41
				+ this.handler.wightCount() * 43
				+ (this.handler.amode() ? 23 : 0)
				+ (this.handler.pmode() ? 29 : 0)
				+ (this.handler.casinoOn() ? 31 : 0)
				+ (this.handler.rulesEnc() ? 37 : 0)
				+ (this.handler.addAdminLocked() ? 47 : 0)
				+ (this.handler.isDev() ? 53 : 0)
				+ (this.handler.transfers() ? 59 : 0)
				+ (this.handler.auctionLimit() ? 61 : 0)
				+ (this.handler.warnBuy() ? 67 : 0)
				+ this.handler.warnPrice()
				+ this.handler.huntKills() * 71
				+ this.handler.huntSeconds() * 73
				+ this.handler.huntMob() * 79
				+ this.handler.warLoot()
				+ this.handler.warMax() * 3
				+ (this.handler.warAuto() ? 11 : 0)
				+ this.handler.warMin() * 13
				+ this.handler.warCore().hashCode()
				+ this.handler.warChallenge()
				+ this.handler.warCooldown()
				+ this.handler.moneyDelay()
				+ this.handler.itemDelay()
				+ this.handler.clickLimit()
				+ this.handler.acJail()
				+ (this.handler.anticheatOn() ? 101 : 0)
				+ (this.handler.xrayOn() ? 103 : 0)
				+ this.handler.boxRadius()
				+ this.handler.boxWarn()
				+ this.handler.boxClear()
				+ this.handler.boxKit()
				+ this.handler.boxKits()
				+ this.handler.clickHold()
				+ this.handler.xrayNeed()
				+ (this.handler.afkOn() ? 107 : 0)
				+ this.handler.afkMin()
				+ this.handler.xrayBanDays()
				+ this.handler.clickBanDays()
				+ this.handler.boxLife()
				+ this.handler.boxCooldown()
				+ this.handler.colorAdmin()
				+ this.handler.colorCurator()
				+ this.handler.colorZga()
				+ this.handler.colorGa()
				+ this.handler.colorBlogger() * 3
				+ this.handler.clanWinWc()
				+ this.handler.fullStopCooldown()
				+ this.handler.trainingDailyMax()
				+ (this.handler.proteinOn() ? 11 : 0)
				+ (this.handler.cwStoryOn() ? 127 : 0)
				+ (this.handler.betaOn() ? 131 : 0)
				+ this.module * 17;
		if (sig != this.signature) {
			this.signature = sig;
			if (!(this.getFocused() instanceof TextFieldWidget)) {
				rebuild();
			}
		}
	}

	private void rebuild() {
		this.colorFields.clear();
		this.clearChildren();
		int vis = visibleRows();
		for (int i = 0; i < vis; i++) {
			int row = this.scroll + i;
			if (row >= totalRows()) {
				break;
			}
			addRow(row, this.y + 28 + i * ROW);
		}
	}

	/** Логические строки текущего модуля (без античита — он в /anticheat). */
	private int[] moduleLogicals() {
		boolean dev = this.handler.isDev();
		if (!dev) {
			return switch (this.module) {
				case 0 -> new int[] {0, 1, 2, 3, 17, 18, 19};
				case 1 -> new int[] {5, 6, 7, 20, 25, 26};
				case 2 -> new int[] {13, 14, 15, 16, 28};
			case 3 -> new int[] {23, 27, 89, 100, 101};
			case 4 -> new int[] {22, 24, 54};
			default -> new int[0];
			};
		}
		return switch (this.module) {
			case 0 -> new int[] {0, 1, 2, 3, 17, 18, 19, 34, 35, 36, 37, 38, 39};
			case 1 -> new int[] {5, 6, 7, 20, 25, 26, 40, 41};
			case 2 -> new int[] {8, 9, 10, 11, 12, 21, 55, 63, 64};
			case 3 -> new int[] {13, 14, 15, 16, 28};
			case 4 -> new int[] {117, 23, 27, 69, 70, 71, 72, 116, 73, 74, 75, 76, 89, 100};
			case 5 -> new int[] {42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 56, 57, 58, 59, 60, 61, 62, 88};
			case 6 -> new int[] {29, 30, 31, 32, 33};
			case 7 -> new int[] {83, 84, 85, 86, 87, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99};
			case 8 -> new int[] {22, 24, 54};
			case 9 -> new int[] {100, 101};
			case 10 -> new int[] {102, 111, 103, 104, 105, 106, 107, 108, 109, 110, 112};
			case 11 -> new int[] {114, 115, 113};
			default -> new int[0];
		};
	}

	private int logicalRow(int visual) {
		if (this.module < 0) {
			return -1;
		}
		if (visual == 0) {
			return -2; // назад
		}
		int[] rows = moduleLogicals();
		int i = visual - 1;
		return i >= 0 && i < rows.length ? rows[i] : -1;
	}

	private void addRow(int row, int y) {
		int x = this.x + 8;
		int w = this.backgroundWidth - 28;
		if (this.module < 0) {
			String[] mods = modules();
			if (row >= 0 && row < mods.length) {
				int m = row;
				this.addDrawableChild(ButtonWidget.builder(Text.literal("▸ " + mods[m]), b -> {
					this.module = m;
					this.scroll = 0;
					rebuild();
				}).dimensions(x, y, w, 18).build());
			}
			return;
		}
		int logical = logicalRow(row);
		if (logical == -2) {
			this.addDrawableChild(ButtonWidget.builder(Text.literal("← Назад к модулям"), b -> {
				this.module = -1;
				this.scroll = 0;
				rebuild();
			}).dimensions(x, y, w, 18).build());
			return;
		}
		String lock = switch (this.handler.lockMode()) {
			case 2 -> "§4Kill-закрыт";
			case 1 -> "§cВход закрыт";
			default -> "§aСервер открыт";
		};
		String weather = switch (this.handler.weather()) {
			case 2 -> "гроза";
			case 1 -> "дождь";
			default -> "ясно";
		};
		switch (logical) {
			case 0 -> toggle(x, y, w, lock + "  (нажми: закрыть вход)", ServerPanelScreenHandler.STOP_SERVER);
			case 1 -> toggle(x, y, w, "Открыть сервер /start_server", ServerPanelScreenHandler.START_SERVER);
			case 2 -> toggle(x, y, w, "Читы админов: " + onOff(this.handler.amode()), ServerPanelScreenHandler.AMODE);
			case 3 -> toggle(x, y, w, "Читы игроков: " + onOff(this.handler.pmode()), ServerPanelScreenHandler.PMODE);
			case 5 -> pair(x, y, "Sell по умолч.: " + Money.wc(this.handler.sellPrice()),
					ServerPanelScreenHandler.SELL_M, ServerPanelScreenHandler.SELL_P);
			case 6 -> pair(x, y, "Аукцион мин %: " + this.handler.aucMin(),
					ServerPanelScreenHandler.AMIN_M, ServerPanelScreenHandler.AMIN_P);
			case 7 -> pair(x, y, "Аукцион макс %: " + this.handler.aucMax(),
					ServerPanelScreenHandler.AMAX_M, ServerPanelScreenHandler.AMAX_P);
			case 8 -> toggle(x, y, w, "Казино: " + onOff(this.handler.casinoOn()), ServerPanelScreenHandler.CASINO);
			case 9 -> pair(x, y, "Казино мин ×" + CasinoService.formatMult(this.handler.casinoMin()),
					ServerPanelScreenHandler.CMIN_M, ServerPanelScreenHandler.CMIN_P);
			case 10 -> pair(x, y, "Казино макс ×" + CasinoService.formatMult(this.handler.casinoMax()),
					ServerPanelScreenHandler.CMAX_M, ServerPanelScreenHandler.CMAX_P);
			case 11 -> adjust(x, y, "Шанс казино: " + this.handler.chance() + "%",
					ServerPanelScreenHandler.CH_M10, ServerPanelScreenHandler.CH_M,
					ServerPanelScreenHandler.CH_P, ServerPanelScreenHandler.CH_P10);
			case 12 -> adjust(x, y, "Казино мин ставка: " + Money.wc(this.handler.casinoMinBet()),
					ServerPanelScreenHandler.CASINO_BET_M1000, ServerPanelScreenHandler.CASINO_BET_M,
					ServerPanelScreenHandler.CASINO_BET_P, ServerPanelScreenHandler.CASINO_BET_P1000);
			case 13 -> {
				btn("Создать rules", ServerPanelScreenHandler.RULES_CREATE, x, y, 78);
				btn(this.handler.rulesEnc() ? "Шифр ✓" : "Шифр", ServerPanelScreenHandler.RULES_KEY, x + 82, y, 70);
				btn("Расшифр.", ServerPanelScreenHandler.RULES_UNKEY, x + 156, y, 70);
			}
			case 14 -> {
				btn("День", ServerPanelScreenHandler.TIME_DAY, x, y, 52);
				btn("Полдень", ServerPanelScreenHandler.TIME_NOON, x + 56, y, 56);
				btn("Ночь", ServerPanelScreenHandler.TIME_NIGHT, x + 116, y, 52);
				btn("Полночь", ServerPanelScreenHandler.TIME_MIDNIGHT, x + 172, y, 54);
			}
			case 15 -> {
				btn("Ясно", ServerPanelScreenHandler.WEATHER_CLEAR, x, y, 72);
				btn("Дождь", ServerPanelScreenHandler.WEATHER_RAIN, x + 76, y, 72);
				btn("Гроза (" + weather + ")", ServerPanelScreenHandler.WEATHER_THUNDER, x + 152, y, 74);
			}
			case 16 -> toggle(x, y, w, "Точка спавна мира здесь", ServerPanelScreenHandler.WORLD_SPAWN);
			case 17 -> toggle(x, y, w, "Кик всех (кроме Тех+)", ServerPanelScreenHandler.ALL_KICK);
			case 18 -> toggle(x, y, w, "Whitelist: " + this.handler.wightCount() + "  (показать)", ServerPanelScreenHandler.CHECK_WIGHT);
			case 19 -> toggle(x, y, w, "/add_admin игрокам: "
							+ (this.handler.addAdminLocked() ? "§cзакрыт" : "§aоткрыт"),
					ServerPanelScreenHandler.LOCK_ADD_ADMIN);
			case 20 -> {
				btn("Цены /setprice", ServerPanelScreenHandler.OPEN_PRICE, x, y, 110);
				btn("Запрет /sell", ServerPanelScreenHandler.OPEN_SELL_BAN, x + 114, y, 114);
			}
			case 21 -> toggle(x, y, w, "Казино /setcasino", ServerPanelScreenHandler.OPEN_CASINO);
			case 22 -> {
				btn("Логи /setcheck", ServerPanelScreenHandler.OPEN_CHECK, x, y, 110);
				btn("Команды /chcommands", ServerPanelScreenHandler.OPEN_CHCOMMANDS, x + 114, y, 114);
			}
			case 23 -> toggle(x, y, w, "Мероприятие /event", ServerPanelScreenHandler.OPEN_EVENT);
			case 24 -> toggle(x, y, w, "Игрокам (куратор+): ежедневки, TPA, магазин, кланы, авто-МП, авто-бокс",
					ServerPanelScreenHandler.OPEN_PLAY_FEATURES);
			case 25 -> toggle(x, y, w, "Переводы игроков: " + onOff(this.handler.transfers()),
					ServerPanelScreenHandler.PAY_TRANSFERS);
			case 26 -> toggle(x, y, w, "Лимит цен аукциона: " + onOff(this.handler.auctionLimit()),
					ServerPanelScreenHandler.AUCTION_LIMIT);
			case 27 -> toggle(x, y, w, "Настройки МП и тренировок", ServerPanelScreenHandler.OPEN_ACTIVITY);
			case 28 -> toggle(x, y, w, "Доступные миры", ServerPanelScreenHandler.OPEN_WORLDS);
			case 29 -> pair(x, y, "Рюкзак слоты 1: " + this.handler.packSlots(1),
					ServerPanelScreenHandler.PACK_SLOT_BASE, ServerPanelScreenHandler.PACK_SLOT_BASE + 1);
			case 30 -> pair(x, y, "Рюкзак слоты 2: " + this.handler.packSlots(2),
					ServerPanelScreenHandler.PACK_SLOT_BASE + 2, ServerPanelScreenHandler.PACK_SLOT_BASE + 3);
			case 31 -> pair(x, y, "Рюкзак слоты 3: " + this.handler.packSlots(3),
					ServerPanelScreenHandler.PACK_SLOT_BASE + 4, ServerPanelScreenHandler.PACK_SLOT_BASE + 5);
			case 32 -> pair(x, y, "Рюкзак слоты 4: " + this.handler.packSlots(4),
					ServerPanelScreenHandler.PACK_SLOT_BASE + 6, ServerPanelScreenHandler.PACK_SLOT_BASE + 7);
			case 33 -> pair(x, y, "Рюкзак слоты 5: " + this.handler.packSlots(5),
					ServerPanelScreenHandler.PACK_SLOT_BASE + 8, ServerPanelScreenHandler.PACK_SLOT_BASE + 9);
			case 34 -> toggle(x, y, w, "§4Kill-закрытие (только разраб)", ServerPanelScreenHandler.KILL_LOCK);
			case 35 -> toggle(x, y, w, "Revive / открыть всем", ServerPanelScreenHandler.REVIVE);
			case 36 -> toggle(x, y, w, "Очистить инвентари всех", ServerPanelScreenHandler.ALL_CLEAR);
			case 37 -> toggle(x, y, w, "Снять всех админов", ServerPanelScreenHandler.ALL_UNADM);
			case 38 -> {
				btn("Пермабан всех", ServerPanelScreenHandler.ALL_PERM, x, y, 112);
				btn("Снять масс. пермабан", ServerPanelScreenHandler.ALL_UNPERM, x + 116, y, 112);
			}
			case 39 -> toggle(x, y, w, "Снять с себя разраба → ГА", ServerPanelScreenHandler.UNDEV);
			case 40 -> toggle(x, y, w, "Снятие варна за WC: " + onOff(this.handler.warnBuy()),
					ServerPanelScreenHandler.WARN_BUY);
			case 41 -> pair(x, y, "Цена варна: " + Money.wc(this.handler.warnPrice()),
					ServerPanelScreenHandler.WARN_PRICE_M, ServerPanelScreenHandler.WARN_PRICE_P);
			case 42 -> pair(x, y, "Дом клана: " + Money.wc(this.handler.clanHome()),
					ServerPanelScreenHandler.CLAN_HOME_M, ServerPanelScreenHandler.CLAN_HOME_P);
			case 43 -> pair(x, y, "Приват дома клана: " + Money.wc(this.handler.clanPriv()),
					ServerPanelScreenHandler.CLAN_PRIV_M, ServerPanelScreenHandler.CLAN_PRIV_P);
			case 44 -> pair(x, y, "Склад клана 1 цена: " + Money.wc(this.handler.whPrice(1)),
					ServerPanelScreenHandler.WH_PRICE_BASE, ServerPanelScreenHandler.WH_PRICE_BASE + 1);
			case 45 -> pair(x, y, "Склад клана 2 цена: " + Money.wc(this.handler.whPrice(2)),
					ServerPanelScreenHandler.WH_PRICE_BASE + 2, ServerPanelScreenHandler.WH_PRICE_BASE + 3);
			case 46 -> pair(x, y, "Склад клана 3 цена: " + Money.wc(this.handler.whPrice(3)),
					ServerPanelScreenHandler.WH_PRICE_BASE + 4, ServerPanelScreenHandler.WH_PRICE_BASE + 5);
			case 47 -> pair(x, y, "Склад клана 4 цена: " + Money.wc(this.handler.whPrice(4)),
					ServerPanelScreenHandler.WH_PRICE_BASE + 6, ServerPanelScreenHandler.WH_PRICE_BASE + 7);
			case 48 -> pair(x, y, "Склад клана 5 цена: " + Money.wc(this.handler.whPrice(5)),
					ServerPanelScreenHandler.WH_PRICE_BASE + 8, ServerPanelScreenHandler.WH_PRICE_BASE + 9);
			case 49 -> pair(x, y, "Склад слоты 1: " + this.handler.whSlots(1),
					ServerPanelScreenHandler.WH_SLOT_BASE, ServerPanelScreenHandler.WH_SLOT_BASE + 1);
			case 50 -> pair(x, y, "Склад слоты 2: " + this.handler.whSlots(2),
					ServerPanelScreenHandler.WH_SLOT_BASE + 2, ServerPanelScreenHandler.WH_SLOT_BASE + 3);
			case 51 -> pair(x, y, "Склад слоты 3: " + this.handler.whSlots(3),
					ServerPanelScreenHandler.WH_SLOT_BASE + 4, ServerPanelScreenHandler.WH_SLOT_BASE + 5);
			case 52 -> pair(x, y, "Склад слоты 4: " + this.handler.whSlots(4),
					ServerPanelScreenHandler.WH_SLOT_BASE + 6, ServerPanelScreenHandler.WH_SLOT_BASE + 7);
			case 53 -> pair(x, y, "Склад слоты 5: " + this.handler.whSlots(5),
					ServerPanelScreenHandler.WH_SLOT_BASE + 8, ServerPanelScreenHandler.WH_SLOT_BASE + 9);
			case 54 -> toggle(x, y, w, "Админ-команды (права уровней)", ServerPanelScreenHandler.OPEN_ARANKS);
			case 55 -> toggle(x, y, w, "Предметное казино (символы, шансы, призы)", ServerPanelScreenHandler.OPEN_CASINO_ITEM);
			case 56 -> pair(x, y, "Битва кланов: грабёж " + this.handler.warLoot() + " сек",
					ServerPanelScreenHandler.CWAR_LOOT_M, ServerPanelScreenHandler.CWAR_LOOT_P);
			case 57 -> pair(x, y, "Битва кланов: макс. " + this.handler.warMax() + " мин",
					ServerPanelScreenHandler.CWAR_MAX_M, ServerPanelScreenHandler.CWAR_MAX_P);
			case 58 -> toggle(x, y, w, "Авто-битва (по 2+ в сети): " + onOff(this.handler.warAuto()),
					ServerPanelScreenHandler.CWAR_AUTO);
			case 59 -> pair(x, y, "Битва: мин. игроков онлайн в каждом клане: " + this.handler.warMin(),
					ServerPanelScreenHandler.CWAR_MIN_M, ServerPanelScreenHandler.CWAR_MIN_P);
			case 60 -> toggle(x, y, w, "Ядро битвы: " + this.handler.warCore(), ServerPanelScreenHandler.CWAR_CORE);
			case 61 -> pair(x, y, "Заявка битвы живёт " + this.handler.warChallenge() + " сек",
					ServerPanelScreenHandler.CWAR_CH_M, ServerPanelScreenHandler.CWAR_CH_P);
			case 62 -> pair(x, y, "Кулдаун битвы кланов: " + this.handler.warCooldown() + " мин",
					ServerPanelScreenHandler.CWAR_CD_M, ServerPanelScreenHandler.CWAR_CD_P);
			case 63 -> pair(x, y, "Казино деньги: пауза " + ru.admincontrol.util.CasinoService.delayLabel(this.handler.moneyDelay()),
					ServerPanelScreenHandler.MONEY_DELAY_M, ServerPanelScreenHandler.MONEY_DELAY_P);
			case 64 -> pair(x, y, "Казино предметы: пауза " + ru.admincontrol.util.CasinoService.delayLabel(this.handler.itemDelay()),
					ServerPanelScreenHandler.ITEM_DELAY_M, ServerPanelScreenHandler.ITEM_DELAY_P);
			case 65 -> pair(x, y, "Античит: мин. к/с " + (this.handler.clickLimit() <= 0 ? "выкл" : String.valueOf(this.handler.clickLimit())),
					ServerPanelScreenHandler.CLICK_M, ServerPanelScreenHandler.CLICK_P);
			case 66 -> pair(x, y, "Античит: jail " + this.handler.acJail() + " мин",
					ServerPanelScreenHandler.AC_JAIL_M, ServerPanelScreenHandler.AC_JAIL_P);
			case 67 -> toggle(x, y, w, "Античит: " + onOff(this.handler.anticheatOn()), ServerPanelScreenHandler.ANTICHEAT);
			case 68 -> toggle(x, y, w, "Проверка x-ray: " + onOff(this.handler.xrayOn()), ServerPanelScreenHandler.XRAY);
			case 69 -> pair(x, y, "Бокс: радиус " + this.handler.boxRadius(),
					ServerPanelScreenHandler.BOX_R_M, ServerPanelScreenHandler.BOX_R_P);
			case 70 -> pair(x, y, "Бокс: ожидание " + this.handler.boxWarn() + " мин",
					ServerPanelScreenHandler.BOX_WARN_M, ServerPanelScreenHandler.BOX_WARN_P);
			case 71 -> pair(x, y, "Бокс: после лута " + this.handler.boxClear() + " сек",
					ServerPanelScreenHandler.BOX_CLEAR_M, ServerPanelScreenHandler.BOX_CLEAR_P);
			case 72 -> pair(x, y, "Бокс: живёт " + this.handler.boxLife() + " мин",
					ServerPanelScreenHandler.BOX_LIFE_M, ServerPanelScreenHandler.BOX_LIFE_P);
			case 73 -> pair(x, y, "Бокс: набор " + (this.handler.boxKit() + 1) + "/" + this.handler.boxKits(),
					ServerPanelScreenHandler.BOX_KIT_M, ServerPanelScreenHandler.BOX_KIT_P);
			case 74 -> toggle(x, y, w, "Открыть лут бокса (набор " + (this.handler.boxKit() + 1) + ")",
					ServerPanelScreenHandler.OPEN_BOX_KIT);
			case 75 -> toggle(x, y, w, "Добавить набор лута бокса", ServerPanelScreenHandler.BOX_KIT_ADD);
			case 76 -> toggle(x, y, w, "Удалить текущий набор лута бокса", ServerPanelScreenHandler.BOX_KIT_DEL);
			case 77 -> pair(x, y, "Античит: сек. подряд " + this.handler.clickHold(),
					ServerPanelScreenHandler.CLICK_HOLD_M, ServerPanelScreenHandler.CLICK_HOLD_P);
			case 78 -> pair(x, y, "Античит x-ray: скрытой руды " + this.handler.xrayNeed(),
					ServerPanelScreenHandler.XRAY_NEED_M, ServerPanelScreenHandler.XRAY_NEED_P);
			case 79 -> toggle(x, y, w, "Проверка АФК: " + onOff(this.handler.afkOn()), ServerPanelScreenHandler.AFK);
			case 80 -> pair(x, y, "АФК: наказание через " + this.handler.afkMin() + " мин",
					ServerPanelScreenHandler.AFK_M, ServerPanelScreenHandler.AFK_P);
			case 81 -> pair(x, y, "Античит читы: бан " + this.handler.xrayBanDays() + " дн",
					ServerPanelScreenHandler.XRAY_BAN_M, ServerPanelScreenHandler.XRAY_BAN_P);
			case 82 -> pair(x, y, "Автокликер 3-й раз: бан " + this.handler.clickBanDays() + " дн",
					ServerPanelScreenHandler.CLICK_BAN_M, ServerPanelScreenHandler.CLICK_BAN_P);
			case 83 -> colorRow(x, y, "Ник обычных админов", NickColors.hex(this.handler.colorAdmin()), "nick_color_admin");
			case 84 -> colorRow(x, y, "Ник куратора", NickColors.hex(this.handler.colorCurator()), "nick_color_curator");
			case 85 -> colorRow(x, y, "Ник ЗГА/тех", NickColors.hex(this.handler.colorZga()), "nick_color_zga");
			case 86 -> colorRow(x, y, "Ник ГА/разраба", NickColors.hex(this.handler.colorGa()), "nick_color_ga");
			case 87 -> colorRow(x, y, "Ник блогера", NickColors.hex(this.handler.colorBlogger()), "nick_color_blogger");
			case 88 -> pair(x, y, "Клан-победитель: " + Money.wc(this.handler.clanWinWc()) + " в казну",
					ServerPanelScreenHandler.CLAN_WIN_M, ServerPanelScreenHandler.CLAN_WIN_P);
			case 89 -> adjust(x, y, "Пауза после /fullstop: "
							+ ru.admincontrol.util.FullStopLock.formatSec(this.handler.fullStopCooldown()),
					ServerPanelScreenHandler.FULLSTOP_CD_M30, ServerPanelScreenHandler.FULLSTOP_CD_M,
					ServerPanelScreenHandler.FULLSTOP_CD_P, ServerPanelScreenHandler.FULLSTOP_CD_P30);
			case 90 -> tagRow(x, y, "[] Модератор", this.handler.rankTag(0), "rank_tag_mod");
			case 91 -> tagRow(x, y, "[] Ст. модератор", this.handler.rankTag(1), "rank_tag_smod");
			case 92 -> tagRow(x, y, "[] Админ", this.handler.rankTag(2), "rank_tag_admin");
			case 93 -> tagRow(x, y, "[] Ст. администратор", this.handler.rankTag(3), "rank_tag_senior");
			case 94 -> tagRow(x, y, "[] Блогер", this.handler.rankTag(9), "rank_tag_blogger");
			case 95 -> tagRow(x, y, "[] Куратор", this.handler.rankTag(4), "rank_tag_curator");
			case 96 -> tagRow(x, y, "[] ЗГА", this.handler.rankTag(5), "rank_tag_zga");
			case 97 -> tagRow(x, y, "[] Тех", this.handler.rankTag(6), "rank_tag_tech");
			case 98 -> tagRow(x, y, "[] ГА", this.handler.rankTag(7), "rank_tag_ga");
			case 99 -> tagRow(x, y, "[] Разработчик", this.handler.rankTag(8), "rank_tag_dev");
			case 100 -> pair(x, y, "Тренировок в день (база): " + this.handler.trainingDailyMax(),
					ServerPanelScreenHandler.TRAIN_DAILY_M, ServerPanelScreenHandler.TRAIN_DAILY_P);
			case 101 -> toggle(x, y, w, "Протеиновые батончики/коктейли: " + onOff(this.handler.proteinOn()),
					ServerPanelScreenHandler.PROTEIN_ON);
			case 102 -> toggle(x, y, w, "Сюжет CW Expedition: " + onOff(this.handler.cwStoryOn()),
					ServerPanelScreenHandler.CW_STORY_TOGGLE);
			case 103 -> toggle(x, y, w, "Войти / выйти из мира шаблонов", ServerPanelScreenHandler.CW_BUILDER_ENTER);
			case 104 -> toggle(x, y, w, "Создать главного NPC Luya", ServerPanelScreenHandler.CW_LUA_CREATE);
			case 105 -> toggle(x, y, w, "Найти Luya (переместить сюда)", ServerPanelScreenHandler.CW_LUA_FIND);
			case 106 -> toggle(x, y, w, "Удалить Luya", ServerPanelScreenHandler.CW_LUA_REMOVE);
			case 107 -> toggle(x, y, w, "Перечитать PNG-скины", ServerPanelScreenHandler.CW_SKINS_RELOAD);
			case 108 -> toggle(x, y, w, "Проверить/перечитать ресурсы сюжета", ServerPanelScreenHandler.CW_RESOURCES_RELOAD);
			case 109 -> toggle(x, y, w, "Luya: махать правой рукой", ServerPanelScreenHandler.CW_LUA_WAVE);
			case 110 -> toggle(x, y, w, "Luya: сбросить позу и анимацию", ServerPanelScreenHandler.CW_LUA_RESET_POSE);
			case 116 -> pair(x, y, "Бокс: пауза между боксами "
						+ (this.handler.boxCooldown() == 0 ? "выкл" : this.handler.boxCooldown() + " мин"),
					ServerPanelScreenHandler.BOX_COOLDOWN_M, ServerPanelScreenHandler.BOX_COOLDOWN_P);
			case 117 -> toggle(x, y, w, "Бета-версии (тренировки с ботами, дуэли): " + onOff(this.handler.betaOn()),
					ServerPanelScreenHandler.BETA_TOGGLE);
			case 111 -> toggle(x, y, w, "Объявить игрокам текущее состояние сюжета", ServerPanelScreenHandler.CW_STORY_ANNOUNCE);
			case 112 -> this.addDrawableChild(ButtonWidget.builder(Text.literal("Настроить список зашифрованных предметов"),
					b -> {
						ru.admincontrol.lumira.client.EncryptedItemsScreen.requestSync();
						this.client.setScreen(new ru.admincontrol.lumira.client.EncryptedItemsScreen(this));
					}).dimensions(x, y, w, 18).build());
			case 113 -> this.addDrawableChild(ButtonWidget.builder(Text.literal("Создать/изменить крафт Coin лиры и Coin мирты"),
					b -> {
						ru.admincontrol.lumira.client.CoinRecipeScreen.requestSync();
						this.client.setScreen(new ru.admincontrol.lumira.client.CoinRecipeScreen(this));
					}).dimensions(x, y, w, 18).build());
			case 114 -> this.addDrawableChild(ButtonWidget.builder(Text.literal("Редактор крафтов: все рецепты мода"),
					b -> {
						ru.admincontrol.lumira.client.RecipeEditorScreen.requestSync();
						this.client.setScreen(new ru.admincontrol.lumira.client.RecipeEditorScreen(this, ""));
					}).dimensions(x, y, w, 18).build());
			case 115 -> this.addDrawableChild(ButtonWidget.builder(Text.literal("Рецепты деревянных предметов"),
					b -> {
						ru.admincontrol.lumira.client.RecipeEditorScreen.requestSync();
						this.client.setScreen(new ru.admincontrol.lumira.client.RecipeEditorScreen(this, "planks"));
					}).dimensions(x, y, w, 18).build());
			default -> {
			}
		}
	}

	private void colorRow(int x, int y, String label, String hex, String kind) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
		}).dimensions(x, y, this.backgroundWidth - 110, 18).build());
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x + this.backgroundWidth - 106, y, 78, 18, Text.empty());
		field.setMaxLength(7);
		field.setText(hex == null ? "" : hex);
		field.setEditableColor(0xE8EEF8);
		field.setDrawsBackground(true);
		field.setTextPredicate(v -> v != null && v.matches("(?i)#?[0-9a-f]{0,6}"));
		AcHint.apply(field, "#rrggbb");
		this.colorFields.put(field, kind);
		this.addDrawableChild(field);
	}

	private void tagRow(int x, int y, String label, String text, String kind) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
		}).dimensions(x, y, this.backgroundWidth - 130, 18).build());
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, x + this.backgroundWidth - 126, y, 98, 18, Text.empty());
		field.setMaxLength(24);
		field.setText(text == null ? "" : text);
		field.setEditableColor(0xE8EEF8);
		field.setDrawsBackground(true);
		AcHint.apply(field, "текст");
		this.colorFields.put(field, kind);
		this.addDrawableChild(field);
	}

	private void sendColor(TextFieldWidget field) {
		String kind = this.colorFields.get(field);
		if (kind == null || this.client == null) {
			return;
		}
		var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeString(kind, 32);
		buf.writeString(field.getText() == null ? "" : field.getText(), 32);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ru.admincontrol.net.ModPackets.UI_VALUE, buf);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.getFocused() instanceof TextFieldWidget field) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				sendColor(field);
				field.setFocused(false);
				this.setFocused(null);
				return true;
			}
			if (field.keyPressed(keyCode, scanCode, modifiers) || field.isActive()) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.getFocused() instanceof TextFieldWidget field && field.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
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

	private void adjust(int x, int y, String label, int m10, int m1, int p1, int p10) {
		btn("−−", m10, x, y, 28);
		btn("−", m1, x + 30, y, 22);
		this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
		}).dimensions(x + 54, y, this.backgroundWidth - 140, 18).build());
		btn("+", p1, x + this.backgroundWidth - 86, y, 22);
		btn("++", p10, x + this.backgroundWidth - 62, y, 28);
	}

	private void btn(String text, int id, int x, int y, int w) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> click(id)).dimensions(x, y, w, 18).build());
	}

	private static String onOff(boolean on) {
		return on ? "§aвкл" : "§cвыкл";
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
		String title;
		if (this.module < 0) {
			title = this.handler.isDev() ? "Модули /dserver" : "Модули /aserver";
		} else {
			String[] mods = modules();
			title = this.module >= 0 && this.module < mods.length ? mods[this.module] : "Настройки";
		}
		AcTheme.titleCentered(this.textRenderer, context, title, 8, 6, this.backgroundWidth - 16);
		if (maxScroll() > 0) {
			this.bar.draw(context, this.backgroundWidth - 12, 28, barH(), maxScroll(), visibleRows(), this.scroll);
		}
	}
}
