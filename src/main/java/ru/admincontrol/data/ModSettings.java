package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModSettings {
	/** Шифрование названий предметов (список «зашифрованных» предметов, настраивается в /dserver). */
	public boolean encryptItems = true;
	/** null — список ещё не создан, при первом запуске заполняется предметами по умолчанию. */
	public List<String> encryptedItems = null;
	/** Шифрование картинки предмета (вместо иконки показывается «неизвестный предмет»). */
	public boolean encryptIcons = true;
	public List<String> iconEncryptedItems = null;
	/** Не отправлять клиентам рецепты зашифрованных предметов (EMI/REI не покажут крафт). */
	public boolean blockRecipeView = true;
	/** Отдельный список скрытых описаний (третье независимое разрешение: картинка / название / описание). */
	public List<String> descEncryptedItems = null;
	/** Предметы, которые нельзя использовать: ПКМ, ставить как блок или применять к сущности. */
	public List<String> useBlockedItems = null;
	/** Одноразовая миграция: деревянные предметы больше не исключаются из скрытия. */
	public boolean encryptDefaultDenyMigrated = false;
	/** Крафт монет сюжета (бесформенный): id предметов, пусто — рецепт выключен. */
	public List<String> coinLyraRecipe = new ArrayList<>();
	public int coinLyraCount = 1;
	public List<String> coinMyrtleRecipe = new ArrayList<>();
	public int coinMyrtleCount = 1;
	/** CW Expedition is opt-in: live story interactions stay disabled until a developer enables them. */
	public boolean cwStoryEnabled = false;
	/** CW EMI: серверная часть просмотрщика предметов и рецептов (рукопожатие, запросы). */
	public boolean cwEmiEnabled = true;
	/** CW EMI: отключать игроков, не подтвердивших проверку совместимости за 10 секунд (по умолчанию выкл., чтобы не выгнать клиентов без мода). */
	public boolean cwEmiEnforce = false;
	/** Relative to the dedicated-server root; absolute paths and traversal are rejected. */
	public String cwStoryResourcePath = "mods/cw-expedition";
	public int cwFillConfirmVolume = 32768;
	public int cwFillMaxVolume = 1000000;
	/** Number of timestamped CW backups retained; at least two are always kept. */
	public int cwBackupRetention = 10;
	/** Client atmosphere defaults sent with the CW protocol. */
	public int cwSkyCycleSeconds = 180;
	public int cwSkyIntensityPercent = 100;
	public boolean cwSkyReducedEffects = false;
	public boolean adminCheats = false;
	public boolean playerCheats = false;
	public boolean firstAdminAssigned = false;
	public List<String> adminUuids = new ArrayList<>();
	public List<String> adminNicks = new ArrayList<>();
	public Map<String, Integer> adminLevelsByUuid = new LinkedHashMap<>();
	public Map<String, Integer> adminLevelsByNick = new LinkedHashMap<>();
	public String headAdminUuid = "";
	public String headAdminNick = "";
	/** Закреплённые разработчики: не сбрасываются при входе, пока не /undev или кнопка в /dserver. */
	public List<String> developerUuids = new ArrayList<>();
	public List<String> developerNicks = new ArrayList<>();
	/** none | stop | kill */
	public String serverLockMode = "none";
	public String serverLockReason = "";
	/** Старое поле — подхватывается при загрузке. */
	public boolean serverLocked = false;
	public List<String> wightUuids = new ArrayList<>();
	public List<String> wightNicks = new ArrayList<>();
	public List<String> disabledPlayerCommands = new ArrayList<>();
	public List<String> disabledEveryoneCommands = new ArrayList<>();
	public List<IpBan> ipBans = new ArrayList<>();
	public int defaultSellPrice = 1;
	public Map<String, Integer> itemPrices = new LinkedHashMap<>();
	/** Мин. цена аукциона в % от /sell (0–1000). */
	public int auctionMinPercent = 100;
	/** Макс. цена аукциона в % от /sell (0–1000). */
	public int auctionMaxPercent = 150;
	public boolean rulesEncrypted = false;
	public boolean addAdminLockedToStaff = false;
	public boolean casinoEnabled = true;
	/** Множитель казино в десятых: 0 = проигрыш, 10 = ×1.0 */
	public int casinoMinTenths = 0;
	public int casinoMaxTenths = 25;
	/** Шанс победы казино 0–100. Чем выше — чуть мягче, дом всё равно в плюсе. */
	public Integer casinoChance = 15;
	/** Минимальная ставка казино в WC. */
	public int casinoMinBet = 0;
	/** 2 = жёсткая экономика казино (дом всегда в плюсе). */
	public int casinoOddsVersion = 0;
	/** 1 = урезаны шансы предметного казино. */
	public int casinoItemOddsVersion = 0;
	/** Секунд между ставками на деньги. 0 = без задержки. */
	public int casinoMoneyDelaySec = 3;
	/** Секунд между ставками на предметы. 0 = без задержки. */
	public int casinoItemDelaySec = 8;
	public boolean anticheatEnabled = true;
	public boolean anticheatXray = true;
	/** Кликов в секунду, выше — кик. 0 = не проверять клики. */
	public int anticheatClicksPerSec = 25;
	/** Сколько секунд подряд превышать лимит CPS. */
	public int anticheatClickHoldSec = 2;
	/** Jail за повтор автокликера, минуты. */
	public int anticheatJailMinutes = 5;
	/** Окно повторного срабатывания, секунды. */
	public int anticheatRepeatSeconds = 300;
	/** Сколько скрытой руды за окно — подозрение x-ray. */
	public int anticheatXrayHidden = 16;
	public int anticheatXrayWindowSec = 90;
	/** Бан за x-ray / подсветку читов, дни. */
	public int anticheatXrayBanDays = 30;
	/** Бан за 3-е срабатывание автокликера, дни. */
	public int anticheatClickBanDays = 1;
	/** 1 = лимит кликов поднят с устаревших 5. */
	public int anticheatClickVersion = 1;
	/** Проверка файлов игры у игроков (mods, resourcepacks, shaderpacks…): только сообщает администрации. */
	public boolean fileScanEnabled = true;
	public boolean afkKickEnabled = true;
	/** Минут без действий до кика за АФК. 0 = выкл. */
	public int afkMinutes = 7;
	/** Наказания античита по нарушениям (ключи — AntiCheatRules.KEYS). */
	public java.util.Map<String, AntiCheatRule> anticheatRules = new java.util.LinkedHashMap<>();
	/** Наказывать ли античитом администрацию (иначе — только сообщение). */
	public boolean anticheatPunishStaff = false;
	/** Сколько минут длится «тест на себе» из /anticheat. */
	public int anticheatTestMinutes = 10;
	/** Поиск багаюза: находки пишутся в баг-лист (/buglist), наказаний нет. */
	public boolean bugSearchEnabled = true;
	/** Ошибки из логов сервера тоже пишутся в баг-лист. */
	public boolean bugServerErrors = true;
	public boolean playerTransfers = true;
	public boolean auctionPriceLimit = true;
	public boolean warnBuyEnabled = false;
	public int warnBuyPrice = 100;
	public java.util.Map<String, Integer> trainingCoins = new java.util.LinkedHashMap<>();
	public java.util.Map<String, Integer> trainingPowerTenths = new java.util.LinkedHashMap<>();
	public java.util.List<TrainingKitItem> trainingKit = new java.util.ArrayList<>();
	public java.util.Map<String, java.util.List<TrainingKitItem>> eventKits = new java.util.LinkedHashMap<>();
	public java.util.Map<String, java.util.List<TrainingKitItem>> boxKits = new java.util.LinkedHashMap<>();
	public int boxRadius = 100;
	public int boxWarnMinutes = 5;
	public int boxClearSeconds = 90;
	/** Сколько минут бокс стоит в мире после появления, трогали или нет. */
	public int boxLifetimeMinutes = 10;
	/** Минуты между завершением бокса и стартом следующего. 0 = без паузы. */
	public int boxCooldownMinutes = 30;
	/** Когда разрешён следующий бокс; хранится, чтобы перезапуск сервера не сбрасывал паузу. */
	public long boxNextAllowedAt;
	public int boxKitEditIndex;
	/** Последний фактически выбранный набор бокса: исключается из следующего случайного выбора. */
	public String boxLastKitKey = "";
	/** Бета тренировок с ботами глобально выключена, пока администратор явно её не включит. */
	public boolean botTrainingEnabled = false;
	/** Общий рубильник экспериментальных пользовательских функций; по умолчанию выключен. */
	public boolean betaFeaturesEnabled = false;
	/** Настройки карт тренировок с ботами, ключ — индекс типа МП строкой. */
	public java.util.Map<String, BotTrainingConfig> botTrainingEvents = new java.util.LinkedHashMap<>();

	public static final class BotTrainingConfig {
		public boolean enabled;
		public int allies;
		public int enemies = 3;
		/** 0 — простой, 1 — средний, 2 — сложный. */
		public int difficulty = 1;
		public int reward;
	}
	public int eventHuntKills = 15;
	public int eventHuntSeconds = 180;
	public int eventHuntMob = 0;
	public int eventStrikeKills = 30;
	public int eventStrikeMinutes = 15;
	/** Настройки МП по умолчанию по типу (индекс в EventService.TYPES), задаются в /devent. */
	public java.util.Map<String, EventDefaults> eventDefaults = new java.util.LinkedHashMap<>();
	/** Индексы мероприятий, заблокированных через /cw event. */
	public java.util.List<Integer> blockedEvents = new java.util.ArrayList<>();
	/** Свои МП из /devent. */
	public java.util.List<CustomEvent> customEvents = new java.util.ArrayList<>();
	public int customEventSeq;
	/** Порядок кнопок выбора МП (ключи МП) из /devent; null — стандартный порядок. */
	public java.util.List<String> eventOrder;
	/** Переопределения команд блогера из /aranks (id → вкл/выкл); нет ключа — стандартные правила. */
	public java.util.Map<String, Boolean> bloggerCommandFlags = new java.util.LinkedHashMap<>();
	/** Запрещённые фрагменты ника; null — ещё не задавались, используется список по умолчанию. */
	public java.util.List<String> nickForbidden;
	/** Ники из белого списка фильтра (без учёта регистра) — не банятся за спецсимволы. */
	public java.util.List<String> nickWhitelist = new java.util.ArrayList<>();
	/** Блоки, которые участники могут ломать на МП, по ключу МП («0»…«12», «c1»…), — id блоков. */
	public java.util.Map<String, java.util.List<String>> eventBreakable = new java.util.LinkedHashMap<>();
	public int clanWarLootSeconds = 120;
	public int clanWarMaxMinutes = 30;
	public boolean clanWarAutoApprove = true;
	public int clanWarMinOnline = 2;
	public String clanWarCoreBlock = "minecraft:netherite_block";
	public int clanWarChallengeSeconds = 180;
	public int clanWarCooldownMinutes = 15;
	public java.util.List<String> disabledWorlds = new java.util.ArrayList<>();
	public java.util.Map<String, Integer> commandMinRanks = new java.util.LinkedHashMap<>();
	public java.util.Map<String, Integer> rankBanDays = new java.util.LinkedHashMap<>();
	public java.util.Map<String, Integer> rankJailMinutes = new java.util.LinkedHashMap<>();
	public Integer punishStaffMin = 40;

	public boolean dailyEnabled = true;
	public boolean dailyJoinOn = true;
	public int dailyJoinWc = 5;
	public boolean dailyBreakOn = true;
	public int dailyBreakGoal = 200;
	public int dailyBreakWc = 8;
	public boolean dailyKillOn = true;
	public int dailyKillGoal = 15;
	public int dailyKillWc = 8;
	public boolean dailyPlaceOn = true;
	public int dailyPlaceGoal = 100;
	public int dailyPlaceWc = 6;
	public boolean dailyPlayOn = true;
	public int dailyPlayMin = 30;
	public int dailyPlayWc = 10;
	public boolean dailyChatOn = true;
	public int dailyChatGoal = 10;
	public int dailyChatWc = 4;
	public boolean dailyCasinoOn = true;
	public int dailyCasinoGoal = 3;
	public int dailyCasinoWc = 8;
	public boolean dailyEatOn = true;
	public int dailyEatGoal = 5;
	public int dailyEatWc = 5;
	public boolean dailyPvpOn = false;
	public int dailyPvpGoal = 1;
	public int dailyPvpWc = 12;
	public boolean tpaEnabled = true;
	public int tpaSeconds = 60;
	public boolean shopEnabled = true;
	public int shopHomePrice = 50;
	public int shopLuckPrice = 40;
	public int shopPowerPrice = 40;
	public boolean clansEnabled = true;
	public int clanCreatePrice = 100;
	public int clanMaxMembers = 8;
	public boolean autoEventEnabled = false;
	public int autoEventMinutes = 120;
	public int autoEventWarnMinutes = 5;
	public int autoEventType = 0;
	public int autoEventReward = 16;
	public int autoEventCycle;
	public long autoEventNextAt;
	public boolean autoEventWarned;
	public boolean autoBoxEnabled = false;
	public int autoBoxMinutes = 180;
	public long autoBoxNextAt;
	public boolean autoBoxWarned;
	/** Секунды паузы после /fullstop на МП и тренировки. 0 = нет, -1 = бесконечно. По умолчанию 90. */
	public int fullStopCooldownSec = 90;
	/** Базовый дневной лимит тренировок (ветка Sync может добавить сверху). Минимум 1. */
	public int trainingDailyMax = 5;
	/** Радиус случайного размещения арены тренировки относительно мирового спавна. */
	public int trainingSpawnRadius = 1000;
	/** Протеиновые батончики и коктейли. */
	public boolean proteinEnabled = true;
	/** Награда WC в казну клана за победу в МП или битве. 0 = без награды. */
	public int clanWinWc = 5000;
	public int colorAdmin = 0x3498DB;
	public int colorCurator = 0x6F0735;
	public int colorBlogger = 0xFFD700;
	public int colorZga = 0xE91E63;
	public int colorGa = 0xDD1F40;
	/** Текст в [скобках] чата по ключу ранга: mod, smod, admin, senior, curator, zga, tech, ga, dev. */
	public Map<String, String> rankChatTags = new LinkedHashMap<>();
	/** Ветки дерева навыков, отключённые через /cw skill: для игроков и для администрации. */
	public java.util.List<String> skillBranchesOffPlayers = new java.util.ArrayList<>();
	public java.util.List<String> skillBranchesOffAdmins = new java.util.ArrayList<>();
	/** «ветка:players» / «ветка:admins» → ранг того, кто отключил; младший не может это изменить. */
	public Map<String, Integer> skillBranchRanks = new LinkedHashMap<>();
	/** Административные уровни, созданные в /aroles. */
	public java.util.List<CustomRank> customRanks = new java.util.ArrayList<>();
	/** Ник админа (lower) → команда → true/false. */
	public Map<String, Map<String, Boolean>> adminCommandFlags = new LinkedHashMap<>();
	public boolean weeklyTopEnabled = true;
	public int weeklyTopWc = 50;
	public String weeklyKey = "";
	public boolean thomeUnlocked = false;
	public List<ClanRecord> clans = new ArrayList<>();
	public List<String> sellBanned = new ArrayList<>();
	public int shopHomePrivatePrice = 80;
	public int shopHomeSizePrice = 25;
	public int shopBackpackPrice1 = 50;
	public int shopBackpackPrice2 = 80;
	public int shopBackpackPrice3 = 120;
	public int shopBackpackPrice4 = 180;
	public int shopBackpackPrice5 = 250;
	public int backpackSlots1 = 9;
	public int backpackSlots2 = 18;
	public int backpackSlots3 = 27;
	public int backpackSlots4 = 36;
	public int backpackSlots5 = 45;
	public int clanHomePrice = 200;
	public int clanHomePrivatePrice = 150;
	public int clanBankPrice = 300;
	public int clanWarehousePrice1 = 80;
	public int clanWarehousePrice2 = 120;
	public int clanWarehousePrice3 = 180;
	public int clanWarehousePrice4 = 250;
	public int clanWarehousePrice5 = 350;
	public int clanWarehouseSlots1 = 9;
	public int clanWarehouseSlots2 = 18;
	public int clanWarehouseSlots3 = 27;
	public int clanWarehouseSlots4 = 36;
	public int clanWarehouseSlots5 = 54;

	public int shopPrivatePrice1 = 150;
	public int shopPrivatePrice2 = 300;
	public int shopPrivatePrice3 = 600;
	public int shopPrivatePrice4 = 1200;
	public int shopPrivatePrice5 = 2500;

	public int privatePrice(int level) {
		return clampPrice(switch (Math.max(1, Math.min(5, level))) {
			case 2 -> shopPrivatePrice2;
			case 3 -> shopPrivatePrice3;
			case 4 -> shopPrivatePrice4;
			case 5 -> shopPrivatePrice5;
			default -> shopPrivatePrice1;
		});
	}

	public void addPrivatePrice(int level, int delta) {
		switch (Math.max(1, Math.min(5, level))) {
			case 2 -> shopPrivatePrice2 = clampPrice(shopPrivatePrice2 + delta);
			case 3 -> shopPrivatePrice3 = clampPrice(shopPrivatePrice3 + delta);
			case 4 -> shopPrivatePrice4 = clampPrice(shopPrivatePrice4 + delta);
			case 5 -> shopPrivatePrice5 = clampPrice(shopPrivatePrice5 + delta);
			default -> shopPrivatePrice1 = clampPrice(shopPrivatePrice1 + delta);
		}
	}

	private static int clampPrice(int value) {
		return Math.max(0, Math.min(30000, value));
	}

	public int backpackPrice(int level) {
		return switch (Math.max(1, Math.min(5, level))) {
			case 2 -> shopBackpackPrice2;
			case 3 -> shopBackpackPrice3;
			case 4 -> shopBackpackPrice4;
			case 5 -> shopBackpackPrice5;
			default -> shopBackpackPrice1;
		};
	}

	public int backpackSlots(int level) {
		return switch (Math.max(1, Math.min(5, level))) {
			case 2 -> backpackSlots2;
			case 3 -> backpackSlots3;
			case 4 -> backpackSlots4;
			case 5 -> backpackSlots5;
			default -> backpackSlots1;
		};
	}

	public int clanWarehousePrice(int level) {
		int lv = Math.max(1, level);
		if (lv <= 5) {
			return switch (lv) {
				case 2 -> clanWarehousePrice2;
				case 3 -> clanWarehousePrice3;
				case 4 -> clanWarehousePrice4;
				case 5 -> clanWarehousePrice5;
				default -> clanWarehousePrice1;
			};
		}
		return Math.max(1, clanWarehousePrice5) * (lv - 4);
	}

	public int clanWarehouseSlots(int level) {
		int lv = Math.max(1, level);
		if (lv <= 5) {
			return switch (lv) {
				case 2 -> clanWarehouseSlots2;
				case 3 -> clanWarehouseSlots3;
				case 4 -> clanWarehouseSlots4;
				case 5 -> clanWarehouseSlots5;
				default -> clanWarehouseSlots1;
			};
		}
		return Math.min(216, clanWarehouseSlots5 + (lv - 5) * 9);
	}

	public String casinoItemSymbols = "★,♦,♠,♥,♣,A,K,Q,J,10,9,8,6,5,4,3,2,1,$,#";
	public int casinoPairChance = 6;
	public int casinoTripleChance = 2;
	public int casinoJackpotChance = 1;
	public int casinoPairPrize = 2;
	public int casinoTriplePrize = 4;
	public int casinoJackpotPrize = 8;
	public String casinoSpecials = "777:1:8";
	/** Ники игроков, которые хоть раз заходили (не NPC). */
	public List<String> usedPlayerNicks = new ArrayList<>();

	/** Заголовок «Common World» над табло. /cw board. */
	public boolean boardTitleEnabled = false;
	/** Система паролей при входе. /cw password. */
	public boolean passwordEnabled = false;
	/** Ссылка для /donate. Пусто — ссылка ещё не задана. */
	public String donateUrl = "";
	/** Правила /cw item, /cw mod, /cw command. */
	public List<CwRule> cwRules = new ArrayList<>();
	public int shopWorkbenchPrice = 100;
	/** Цена каждого уровня автопечи (индекс 0 = ур. 1). */
	public List<Integer> furnacePrices = new ArrayList<>(DEFAULT_FURNACE_PRICES);
	/** Секунд на один предмет на каждом уровне. */
	public List<Integer> furnaceSeconds = new ArrayList<>(DEFAULT_FURNACE_SECONDS);
	/** Сколько печей в одном интерфейсе на каждом уровне. */
	public List<Integer> furnaceCount = new ArrayList<>(DEFAULT_FURNACE_COUNT);

	private static final List<Integer> DEFAULT_FURNACE_PRICES = List.of(100, 160, 240, 340, 480, 650, 850, 1100, 1400);
	private static final List<Integer> DEFAULT_FURNACE_SECONDS = List.of(15, 10, 7, 5, 7, 5, 8, 5, 4);
	private static final List<Integer> DEFAULT_FURNACE_COUNT = List.of(1, 1, 1, 1, 2, 2, 3, 3, 3);

	/** В старых настройках было 5 уровней автопечи — недостающие уровни добавляются со значениями по умолчанию. */
	public void extendFurnaceLevels() {
		furnacePrices = extend(furnacePrices, DEFAULT_FURNACE_PRICES);
		furnaceSeconds = extend(furnaceSeconds, DEFAULT_FURNACE_SECONDS);
		furnaceCount = extend(furnaceCount, DEFAULT_FURNACE_COUNT);
	}

	private static List<Integer> extend(List<Integer> list, List<Integer> defaults) {
		List<Integer> out = list == null ? new ArrayList<>() : new ArrayList<>(list);
		for (int i = out.size(); i < defaults.size(); i++) {
			out.add(defaults.get(i));
		}
		return out;
	}

	public int furnaceMaxLevel() {
		int n = Math.min(furnaceSeconds == null ? 0 : furnaceSeconds.size(),
				furnacePrices == null ? 0 : furnacePrices.size());
		return Math.max(0, n);
	}

	public int furnacePrice(int level) {
		if (furnacePrices == null || level < 1 || level > furnacePrices.size()) {
			return 0;
		}
		return Math.max(0, furnacePrices.get(level - 1));
	}

	public int furnaceSecondsFor(int level) {
		if (furnaceSeconds == null || furnaceSeconds.isEmpty() || level < 1) {
			return 15;
		}
		return Math.max(1, furnaceSeconds.get(Math.min(level, furnaceSeconds.size()) - 1));
	}

	public int furnaceCountFor(int level) {
		if (furnaceCount == null || furnaceCount.isEmpty() || level < 1) {
			return 1;
		}
		return Math.max(1, Math.min(3, furnaceCount.get(Math.min(level, furnaceCount.size()) - 1)));
	}
}
