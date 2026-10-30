package ru.admincontrol.util;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventService {
	public static final int MIN_PLAYERS = 2;
	public static final String[] TYPES = {"Бой насмерть", "Сокровище", "Обвал", "Охота", "Дуэль на динамите", "Дуэль", "Гонка", "Мясорубка", "Кольцо лавы", "Самоцветы", "Отскок", "Футбол", "Страйкбол", "Царь горы", "Цветной пол"};
	/** Царь горы: секунд единоличного удержания вершины до победы и длительность боя. */
	public static final int HILL_GOAL = 60;
	public static final int HILL_MINUTES = 5;
	public static final String[] HUNT_MOBS = {"зомби", "скелеты", "пауки", "криперы", "утопленники"};
	public static final String[] DESCRIPTIONS = {
			"Бой насмерть. Каменная арена 25×25 и высотой 55 блоков: пол из каменного кирпича, борта из полированного глубинного сланца, сверху барьеры. Игроки получают алмазный комплект (защита 4, острота 5), щит, лук, арбалет и ядовитые стрелы. Смерть выбывает и возвращает обычный инвентарь. Побеждает последний выживший: World coin и сила из настроек меню. Запись: минимум 2 игрока. Если мест 20, ждать всех не нужно — бой начнётся, когда наберутся двое и истечёт таймер, места заполнятся, или организатор нажмёт «Начать».",
			"Сокровище. Песчаный двор 33×33 и высотой 32 блока с золотыми углами, колоннами и постаментом. На постаменте золотой блок — его нужно сломать первым. Смерть не выбивает: вы сразу возвращаетесь в бой с комплектом. 2 минуты на раунд. Если время вышло, побеждает тот, кто последним ударил соперника. Награда и сила задаются в этом меню. Минимум 2 участника; полный состав не обязателен.",
			"Обвал. Снежная арена 29×29 и высотой 20 блоков: три слоя снега над лавой, под лавой камень (лава не растекается), стены изо льда. Выдаётся лопата (эффективность 5). Ломайте снег под соперниками. Кто упал в лаву — выбывает. Побеждает последний на снегу. Для проверки карты организатор может нажать «Начать» даже без игроков и сам станет участником.",
			"Охота. Двое в броне со щитом и мечом или топором зачищают закрытую арену от мобов. Непрозрачная крыша и море-фонари: днём мобы не горят. Количество, время и тип мобов — в /aserver. Смерть любого — поражение команды. Нужное число убийств до таймера — победа обоих.",
			"Дуэль на динамите. Высоко в небе: кольцо из незерского кирпича и магмы, внутри пропасть. Размер арены 50–80 блоков — чем больше игроков, тем просторнее. У каждого свой остров 5×5: обсидиановая платформа и TNT сверху, ножка из плачущего обсидиана. Лук с бесконечностью и огнём, одна стрела. Попадание по TNT подрывает остров. Упал с острова или «погиб» — выбывает с МП без смерти.",
			"Дуэль. Та же каменная арена, что у боя насмерть, но с укрытиями и препятствиями: стены, углы и столбы, за которыми можно спрятаться. Смерть выбывает. Побеждает последний выживший.",
			"Гонка. Ледяная лента в воздухе (+50 блоков от точки запуска): ~550–600 блоков, синий лёд по траектории, красно-белая бровка, клетчатый финиш и фонари. Между прямыми — пустота, срезать путь нельзя. Без бортиков пола нет: вылетел с льда — выбыл. С бортиками — кварцевые стены. Лодка выдаётся и нужно ехать на ней: слез или лодка сломалась — выбыл. Призы WC за 1–3 место, без бортиков ещё удача. Финиш после круга.",
			"Мясорубка. Закрытая комната 15×15×10. Сразу появляются 50 мобов: пауки, скелеты, зомби и криперы. Все мобы атакуют всех игроков в коробке. Взрыв крипера не ломает стены и пол. Побеждает тот, кто выжил дольше всех: последний оставшийся (или умерший последним), но только если с начала боя прошло не меньше 30 секунд. Приз: World coin и удача.",
			"Кольцо лавы. Чёрная арена 35×35. Каждые несколько секунд с краёв внутрь пол проваливается в лавяной желоб на блок ниже. Под ареной обсидиан и бедрок — лава никуда не вытекает. Кто сгорел или выбыл — проиграл. Побеждает последний выживший в сужающемся круге. Комплект как у боя насмерть.",
			"Самоцветы. Призматический двор: по полу разбросаны изумрудные, алмазные, золотые и лазуритовые блоки. Ломайте их — каждый блок +1. Первый, кто наберёт 5 самоцветов, побеждает. Смерть не выбивает: вы сразу возвращаетесь с комплектом. Кирка выдаётся в наборе.",
			"Отскок. Центральный остров 9×9 и восемь золотых островков вокруг — между ними 1–3 блока, всё перепрыгивается. Палка с отдачей 10 — сбейте соперников вниз. Упал — выбыл. Побеждает последний на платформе.",
			"Футбол. Поле 80×60 (вдвое больше прежнего), заборы и небольшие трибуны, ворота и мяч. Только чётное число игроков (до 32). Двое — 1 на 1; четверо и больше — команды. Нечётное при старте: лишних снимают. Первый до 3 голов или больше голов за 4 минуты.",
			"Страйкбол. Огромная карта 150×150 под барьерным куполом — город после боя: промзона с ангаром и цехом (антресоль, кран-балка, люки в бункер и подземный ход, смотровая яма), улицы с тротуарами, фонарями и светофорами, дома в два-три этажа с комнатами, окнами и решётками, магазин, заправка, школа, площадь и стоянка. Пробоины в стенах, обрушенные углы и крыши, сгоревший дом, подбитый танк и автобус, горящие обломки и дым, воронки, окопы и стрелковые ячейки — в них можно спрятаться и выбраться по ступеням. Ломать можно только траву, цветы, стёкла и двери, остальное цело; после МП карта убирается целиком. Две команды — красные (запад) и синие (восток), у каждой своя база. Огонь по своим включён. После смерти — сразу на свою базу с набором. Побеждает команда, первой набравшая нужное число убийств соперников; если время вышло — у кого больше убийств. Набор, число убийств и время — /dserver → «Настройки МП и тренировок».",
			"Царь горы. Зелёная долина 41×41 под барьерным куполом: по краям мшистые стены и башни с фонарями, в центре ступенчатый холм из пяти ярусов, на вершине золотая площадка 5×5. Пока на вершине стоит ровно один игрок, ему каждую секунду идёт очко; если наверху двое и больше — вершина оспаривается и очки не идут никому. Первый, кто удержит вершину " + HILL_GOAL + " секунд, побеждает. Если за " + HILL_MINUTES + " минут никто не набрал — побеждает тот, у кого больше секунд (при равенстве — без победителя). Смерть не выбивает: вы сразу возвращаетесь к подножию с набором, очки сохраняются. Набор: железная броня, меч, щит и палка с отдачей — сталкивайте соперников с вершины.",
			"Цветной пол. Арена 31×31: пол из бетонных плиток восьми цветов висит над бассейном. Каждый раунд объявляется цвет (заголовок, полоса сверху и звук) — успейте встать на плитку этого цвета: через несколько секунд все остальные плитки исчезают. Упал вниз — выбыл с МП без смерти. Затем пол собирается заново с новым узором. С каждым раундом времени меньше, а плитки мельче: 3×3, с 5-го раунда 2×2, с 10-го — по одному блоку. Можно толкать соперников палкой с отдачей. Побеждает последний оставшийся на полу."
	};
	private static State state;
	private static java.util.Set<UUID> pendingRestrictedMembers;

	private EventService() {
	}

	public enum Phase {
		REGISTER, COUNTDOWN, FIGHT
	}

	public static final class State {
		public String name = TYPES[0];
		/** Правила МП (индекс в TYPES); у своих МП — правила, на которых они построены. */
		public int typeIndex;
		/** Индекс в EventCatalog и ключ карты / набора / ломаемых блоков. */
		public int choice;
		public String key = "0";
		public int maxPlayers = 8;
		public int reward = 16;
		public int powerTenths = 1;
		public UUID host;
		public String hostName = "";
		public String result = "";
		public UUID lastHit;
		public GameMode hostMode = GameMode.SURVIVAL;
		public double hostX;
		public double hostY;
		public double hostZ;
		public float hostYaw;
		public float hostPitch;
		public String hostDimension = "";
		public Phase phase = Phase.REGISTER;
		public int ticks = 45 * 20;
		public ServerBossBar bar;
		public MinecraftServer server;
		public EventArena arena;
		public String dimension = "";
		public boolean testSolo;
		public boolean privateEvent;
		public boolean hostPlays;
		public boolean raceBumpers = true;
		public int prize1 = 30;
		public int prize2 = 20;
		public int prize3 = 10;
		public int raceLuckTenths;
		public int fightAge;
		public UUID lastDeath;
		public int lastDeathAge;
		public final java.util.List<UUID> finishOrder = new java.util.ArrayList<>();
		public int raceGrace;
		public int huntKills;
		public int huntGoal = 15;
		public int ringAge;
		public int gemGoal = 5;
		public int scoreA;
		public int scoreB;
		public int strikeGoal = 30;
		public int footballFreeze;
		/** Цветной пол: номер раунда, стадия (0 — цвет объявлен, 1 — лишние плитки убраны), тики до смены, цвет. */
		public int partyRound;
		public int partyStage;
		public int partyTimer;
		public int partyColor;
		public UUID ballId;
		public Entity ballRef;
		public double ballVx;
		public double ballVy;
		public double ballVz;
		public String lastBar = "";
		public float lastBarPct = -1f;
		public final java.util.Set<UUID> huntMobs = ConcurrentHashMap.newKeySet();
		public final Map<UUID, Member> members = new ConcurrentHashMap<>();
		/** Непустой набор превращает МП в закрытую сессию только для перечисленных игроков. */
		public final java.util.Set<UUID> restrictedMembers = ConcurrentHashMap.newKeySet();
		/** Кому уже выдан приз вручную из /event — второй раз за это МП не выдаётся. */
		public final java.util.Set<UUID> awarded = ConcurrentHashMap.newKeySet();
		/** Администраторы, перенесённые на МП наблюдателями из /event: куда и в каком режиме вернуть. */
		public final Map<UUID, Watcher> watchers = new ConcurrentHashMap<>();
	}

	public static final class Watcher {
		public String dimension = "";
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		public GameMode mode = GameMode.SURVIVAL;
	}

	public static final class Member {
		public String inventoryNbt = "";
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		public String dimension = "";
		public float health = -1f;
		public int food = -1;
		public float saturation = -1f;
		public boolean alive = true;
		public boolean restored;
		public boolean raceArmed;
		public int gems;
		/** Царь горы: тики единоличного удержания вершины. */
		public int hill;
		public int team;
		public int teamIndex;
		public boolean footTrack;
		public double fx;
		public double fy;
		public double fz;
		public int kickCool;
		/** Страйкбол: выдача нового комплекта откладывается, чтобы клиентские моды оружия успели завершить use/aim state. */
		public int strikeRespawnTicks;
		public String strikeRespawnSubtitle = "";
	}

	public static boolean isFootballBall(Entity entity) {
		if (entity == null) {
			return false;
		}
		if (state != null && state.ballId != null && state.ballId.equals(entity.getUuid())) {
			return true;
		}
		if (!(entity instanceof net.minecraft.entity.mob.SlimeEntity)) {
			return false;
		}
		Text name = entity.getCustomName();
		return name != null && "Мяч".equals(name.getString());
	}

	public static State current() {
		return state;
	}

	public static boolean active() {
		return state != null;
	}

	public static boolean fighting() {
		return state != null && state.phase == Phase.FIGHT;
	}

	public static boolean isHost(ServerPlayerEntity player) {
		return player != null && state != null && player.getUuid().equals(state.host);
	}

	public static boolean canOpen(ServerPlayerEntity player) {
		return player != null;
	}

	public static int typeIndexOf(String name) {
		if (name == null) {
			return 0;
		}
		for (int i = 0; i < TYPES.length; i++) {
			if (TYPES[i].equals(name)) {
				return i;
			}
		}
		return 0;
	}

	public static String description(int index) {
		int i = Math.max(0, Math.min(DESCRIPTIONS.length - 1, index));
		return DESCRIPTIONS[i];
	}

	public static String shortDescription(int index) {
		String full = description(index);
		int dot = full.indexOf('.');
		if (dot > 0 && dot < 90) {
			return full.substring(0, dot + 1);
		}
		return full.length() <= 96 ? full : full.substring(0, 93) + "…";
	}

	public static boolean isSpleef() {
		return state != null && state.typeIndex == 2;
	}

	public static boolean isTreasure() {
		return state != null && state.typeIndex == 1;
	}

	public static boolean isHunt() {
		return state != null && state.typeIndex == 3;
	}

	public static boolean isDuel() {
		return state != null && state.typeIndex == 4;
	}

	public static boolean isCoverDuel() {
		return state != null && state.typeIndex == 5;
	}

	public static boolean isRace() {
		return state != null && state.typeIndex == 6;
	}

	public static boolean isMeat() {
		return state != null && state.typeIndex == 7;
	}

	public static boolean isRing() {
		return state != null && state.typeIndex == 8;
	}

	public static boolean isGems() {
		return state != null && state.typeIndex == 9;
	}

	public static boolean isKnock() {
		return state != null && state.typeIndex == 10;
	}

	public static boolean isFootball() {
		return state != null && state.typeIndex == 11;
	}

	public static boolean isStrike() {
		return state != null && state.typeIndex == 12;
	}

	public static boolean isHill() {
		return state != null && state.typeIndex == 13;
	}

	public static boolean isParty() {
		return state != null && state.typeIndex == 14;
	}

	public static boolean meatContains(BlockPos pos) {
		return isMeat() && state.arena != null && state.arena.contains(pos);
	}

	/** Игрок сейчас на МП: записан и ещё не выбыл. Выбывшего уже вернули домой — для него МП закончено. */
	public static boolean member(ServerPlayerEntity player) {
		Member m = state == null || player == null ? null : state.members.get(player.getUuid());
		return m != null && !m.restored;
	}

	/** Игрок сейчас на МП: участник, ещё не вернувшийся на точку входа, или организатор. */
	public static boolean busy(ServerPlayerEntity player) {
		if (state == null || player == null) {
			return false;
		}
		Member m = state.members.get(player.getUuid());
		return (m != null && !m.restored) || player.getUuid().equals(state.host);
	}

	/** Футбол и гонка: участники не получают урон от игроков и мобов, мобы их не атакуют. */
	public static boolean calm(ServerPlayerEntity player) {
		return (isFootball() || isRace()) && member(player);
	}

	private static int ownSpawns;

	public static boolean spawnBanned(net.minecraft.server.world.ServerWorld world, Entity entity) {
		State s = state;
		return ownSpawns == 0 && s != null && s.arena != null && entity instanceof MobEntity
				&& s.arena.inSpawnBan(world, entity.getBlockPos());
	}

	private static <T> T ownSpawn(java.util.function.Supplier<T> action) {
		ownSpawns++;
		try {
			return action.get();
		} finally {
			ownSpawns--;
		}
	}

	public static boolean isPrivate() {
		return state != null && state.privateEvent;
	}

	public static boolean quietActions(ServerPlayerEntity player) {
		if (state == null || player == null) {
			return false;
		}
		Member member = state.members.get(player.getUuid());
		return member != null && member.alive && state.phase != Phase.REGISTER;
	}

	public static String phaseLabel() {
		if (state == null) {
			return "нет";
		}
		return switch (state.phase) {
			case REGISTER -> "регистрация";
			case COUNTDOWN -> "отсчёт";
			case FIGHT -> "бой";
		};
	}

	public static int memberCount() {
		return state == null ? 0 : (int) state.members.values().stream().filter(m -> m.alive).count();
	}

	public static void start(ServerPlayerEntity host, String name, int maxPlayers, int reward, int powerTenths) {
		start(host, name, maxPlayers, reward, powerTenths, false);
	}

	public static void start(ServerPlayerEntity host, String name, int maxPlayers, int reward, int powerTenths,
			boolean privateEvent) {
		start(host, name, maxPlayers, reward, powerTenths, privateEvent, true, reward, Math.max(0, reward / 2), Math.max(0, reward / 3), 0, false);
	}

	public static void start(ServerPlayerEntity host, String name, int maxPlayers, int reward, int powerTenths,
			boolean privateEvent, boolean raceBumpers, int prize1, int prize2, int prize3, int raceLuckTenths) {
		start(host, name, maxPlayers, reward, powerTenths, privateEvent, raceBumpers, prize1, prize2, prize3, raceLuckTenths, false);
	}

	public static void start(ServerPlayerEntity host, String name, int maxPlayers, int reward, int powerTenths,
			boolean privateEvent, boolean raceBumpers, int prize1, int prize2, int prize3, int raceLuckTenths,
			boolean hostPlays) {
		int choice = EventCatalog.byName(name);
		start(host, choice < 0 ? 0 : choice, maxPlayers, reward, powerTenths, privateEvent, raceBumpers,
				prize1, prize2, prize3, raceLuckTenths, hostPlays);
	}

	/** choice — индекс в EventCatalog: стандартные МП, затем свои из /devent. */
	public static void start(ServerPlayerEntity host, int choice, int maxPlayers, int reward, int powerTenths,
			boolean privateEvent, boolean raceBumpers, int prize1, int prize2, int prize3, int raceLuckTenths,
			boolean hostPlays) {
		if (host == null) {
			return;
		}
		if (!EventCatalog.valid(choice)) {
			Chat.send(host, Chat.red("Такого мероприятия больше нет."));
			return;
		}
		if (state != null) {
			Chat.send(host, Chat.red("Мероприятие уже запущено."));
			return;
		}
		if (TrainingService.active(host)) {
			Chat.send(host, Chat.red("Сначала выйдите с тренировки (/off), затем запускайте МП."));
			return;
		}
		if (ClanWarService.active()) {
			Chat.send(host, Chat.red("Нельзя начать МП во время битвы кланов."));
			return;
		}
		if (EventMapEditor.active()) {
			Chat.send(host, Chat.red("Сейчас разработчик " + EventMapEditor.editorName()
					+ " правит карту МП — запуск после окончания правки."));
			return;
		}
		if (EventCatalog.blocked(choice)) {
			Chat.send(host, Chat.red("Мероприятие «" + EventCatalog.name(choice) + "» заблокировано (/cw event)."));
			return;
		}
		if (FullStopLock.deny(host) || ServerStopService.deny(host)) {
			return;
		}
		state = new State();
		if (pendingRestrictedMembers != null) state.restrictedMembers.addAll(pendingRestrictedMembers);
		state.name = EventCatalog.name(choice);
		state.typeIndex = EventCatalog.base(choice);
		state.choice = choice;
		state.key = EventCatalog.key(choice);
		state.maxPlayers = Math.max(1, Math.min(32, maxPlayers));
		if (state.typeIndex == 3) {
			state.maxPlayers = Math.min(2, Math.max(1, state.maxPlayers));
		}
		if (state.typeIndex == 11) {
			state.maxPlayers = Math.max(2, state.maxPlayers - (state.maxPlayers % 2));
		}
		if (privateEvent) {
			state.maxPlayers = 1;
		}
		state.reward = Math.max(0, Math.min(10000, reward));
		state.powerTenths = Math.max(0, Math.min(50, powerTenths));
		state.host = host.getUuid();
		state.hostMode = host.interactionManager.getGameMode();
		state.hostX = host.getX();
		state.hostY = host.getY();
		state.hostZ = host.getZ();
		state.hostYaw = host.getYaw();
		state.hostPitch = host.getPitch();
		state.hostDimension = host.getWorld().getRegistryKey().getValue().toString();
		state.server = host.getServer();
		state.dimension = host.getWorld().getRegistryKey().getValue().toString();
		state.phase = Phase.REGISTER;
		state.ticks = 45 * 20;
		state.arena = new EventArena();
		state.arena.raceBumpers = raceBumpers;
		state.raceBumpers = raceBumpers;
		state.prize1 = Math.max(0, prize1);
		state.prize2 = Math.max(0, prize2);
		state.prize3 = Math.max(0, prize3);
		state.raceLuckTenths = Math.max(0, raceLuckTenths);
		ServerWorld world = (ServerWorld) host.getWorld();
		state.arena.mapKey = state.key;
		state.arena.build(world, EventArena.adminStand(world, host.getBlockPos()), state.typeIndex);
		state.testSolo = privateEvent;
		state.privateEvent = privateEvent;
		state.hostPlays = hostPlays && !privateEvent;
		state.huntKills = 0;
		state.huntGoal = huntGoal();
		state.bar = new ServerBossBar(Text.literal(state.name).formatted(Formatting.GOLD),
				BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
		state.bar.setPercent(1f);
		syncBarPlayers();
		state.hostName = host.getGameProfile().getName();
		String mode = privateEvent ? "тестовое" : state.hostPlays ? "организатор играет" : "организатор наблюдает";
		JournalLog.add(JournalLog.EVENTS, "Запуск «" + state.name + "» · организатор " + state.hostName
				+ " · " + where() + " · мест " + state.maxPlayers + " · награда " + state.reward + " WC · " + mode);
		AdminNotify.playerAction(host, ru.admincontrol.data.ActionKind.ADMIN, "запустил МП «" + state.name + "» ("
				+ mode + ", мест " + state.maxPlayers + ", награда " + state.reward + " WC, " + where() + ")");
		AdminStats.onEvent(host);
		if (privateEvent) {
			enrollHostAsPlayer();
			beginCountdown();
			Chat.send(host, Chat.yellow("Тестовое МП только для вас («" + state.name + "»). Стоп: /cevent"));
			return;
		}
		if (state.hostPlays) {
			enrollHostAsPlayer();
			announceOpen();
			return;
		}
		host.changeGameMode(GameMode.SPECTATOR);
		TeleportLog.labeled("телепортировался на мероприятие (организатор)", () ->
				host.teleport(world, hostSpectateX(), hostSpectateY(), hostSpectateZ(), host.getYaw(), 40));
		announceOpen();
	}

	/** Запускает закрытую дуэль без награды; присоединиться могут только эти два игрока. */
	public static boolean startRestrictedDuel(ServerPlayerEntity challenger, ServerPlayerEntity target) {
		if (challenger == null || target == null || state != null) return false;
		pendingRestrictedMembers = java.util.Set.of(challenger.getUuid(), target.getUuid());
		try {
			start(challenger, 5, 2, 0, 0, false, true, 0, 0, 0, 0, true);
			return state != null && challenger.getUuid().equals(state.host)
					&& state.restrictedMembers.contains(target.getUuid());
		} finally {
			pendingRestrictedMembers = null;
		}
	}

	public static void startAuto(MinecraftServer server, int typeIndex, int reward) {
		if (server == null || state != null || ClanWarService.active() || FullStopLock.blocked() || EventMapEditor.active()
				|| blocked(typeIndex)) {
			return;
		}
		ServerWorld world = server.getOverworld();
		state = new State();
		int type = Math.max(0, Math.min(TYPES.length - 1, typeIndex));
		ru.admincontrol.data.EventDefaults defaults = ru.admincontrol.data.EventDefaults.of(type);
		state.name = TYPES[type];
		state.typeIndex = type;
		state.choice = type;
		state.key = String.valueOf(type);
		state.maxPlayers = defaults.players;
		state.reward = Math.max(0, Math.min(10000, reward));
		state.powerTenths = defaults.power;
		state.raceLuckTenths = defaults.luck;
		state.host = null;
		state.hostMode = GameMode.SURVIVAL;
		state.server = server;
		state.dimension = world.getRegistryKey().getValue().toString();
		state.hostDimension = state.dimension;
		state.phase = Phase.REGISTER;
		state.ticks = 45 * 20;
		state.arena = new EventArena();
		state.arena.raceBumpers = defaults.bumpers;
		state.raceBumpers = defaults.bumpers;
		state.prize1 = state.reward;
		state.prize2 = Math.max(0, state.reward / 2);
		state.prize3 = Math.max(0, state.reward / 3);
		BlockPos at = type == 12 ? EventArena.adminStand(world, world.getSpawnPos()) : world.getSpawnPos();
		state.arena.build(world, at, state.typeIndex);
		state.huntKills = 0;
		state.huntGoal = huntGoal();
		state.bar = new ServerBossBar(Text.literal(state.name).formatted(Formatting.GOLD),
				BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
		state.bar.setPercent(1f);
		syncBarPlayers();
		state.hostName = "авто-МП";
		JournalLog.add(JournalLog.EVENTS, "Запуск «" + state.name + "» · авто-МП · " + where()
				+ " · мест " + state.maxPlayers + " · награда " + state.reward + " WC");
		announceOpen();
	}

	private static String where() {
		if (state == null || state.arena == null) {
			return "—";
		}
		return state.dimension + " " + (int) Math.floor(state.arena.centerX()) + ", " + state.arena.minY + ", "
				+ (int) Math.floor(state.arena.centerZ());
	}

	private static void announceOpen() {
		if (state == null) {
			return;
		}
		announce(Chat.gold("════════════════════════════════"));
		announce(Chat.gold("  МЕРОПРИЯТИЕ: " + state.name));
		announce(Chat.yellow("  Мест: " + state.maxPlayers + "  •  минимум "
				+ MIN_PLAYERS + "  •  Награда: " + state.reward + " WC  •  сила +"
				+ String.format(java.util.Locale.US, "%.1f", state.powerTenths / 10.0)));
		announce(Chat.white("  Вход: /gevent    Выход: /cevent"));
		announce(Chat.gold("════════════════════════════════"));
	}

	public static boolean tryBegin(ServerPlayerEntity admin) {
		if (state == null || state.phase != Phase.REGISTER) {
			if (admin != null) {
				Chat.send(admin, Chat.red("Сейчас нельзя начать запись досрочно."));
			}
			return false;
		}
		if (memberCount() < MIN_PLAYERS) {
			enrollHostAsPlayer();
		}
		if (memberCount() < 1) {
			if (admin != null) {
				Chat.send(admin, Chat.red("Не удалось начать: нет участников."));
			}
			return false;
		}
		state.testSolo = memberCount() < MIN_PLAYERS;
		beginCountdown();
		if (state.testSolo) {
			announce(Chat.gold("Тест МП: организатор — единственный участник. Стоп: /cevent"));
		} else {
			announce(Chat.gold("Организатор начал мероприятие досрочно."));
		}
		return true;
	}

	private static void enrollHostAsPlayer() {
		if (state == null || state.host == null || state.server == null) {
			return;
		}
		if (state.members.containsKey(state.host)) {
			return;
		}
		ServerPlayerEntity host = state.server.getPlayerManager().getPlayer(state.host);
		if (host == null) {
			return;
		}
		Member member = new Member();
		member.inventoryNbt = InventoryUtil.serialize(host);
		member.x = state.hostX;
		member.y = state.hostY;
		member.z = state.hostZ;
		member.yaw = state.hostYaw;
		member.pitch = state.hostPitch;
		member.dimension = state.hostDimension.isBlank() ? state.dimension : state.hostDimension;
		member.health = host.getHealth();
		member.food = host.getHungerManager().getFoodLevel();
		member.saturation = host.getHungerManager().getSaturationLevel();
		if (!InventoryVault.save(host, InventoryVault.EVENT, member.inventoryNbt, member.dimension,
				member.x, member.y, member.z, member.yaw, member.pitch, host.interactionManager.getGameMode(),
				member.health, member.food, member.saturation)) {
			Chat.send(host, Chat.red("Не удалось сохранить ваш инвентарь на диск — участие в МП отменено."));
			return;
		}
		state.members.put(host.getUuid(), member);
		GodModeService.suspend(host, "МП");
		host.changeGameMode(GameMode.SURVIVAL);
		host.closeHandledScreen();
		ActivityLock.sync(host);
		teleportArena(host, state.members.size() - 1, "телепортировался на мероприятие");
		if (state.bar != null) {
			state.bar.addPlayer(host);
		}
		if (state.privateEvent) {
			Chat.send(host, Chat.yellow("Тест МП: вы единственный участник. /cevent завершит мероприятие."));
		} else if (state.hostPlays) {
			Chat.send(host, Chat.green("Вы участвуете в мероприятии как игрок. Стоп: /cevent"));
		} else {
			Chat.send(host, Chat.yellow("Тест МП: вы единственный участник. /cevent завершит мероприятие."));
		}
	}

	public static String currentName() {
		return state == null ? "" : state.name;
	}

	public static String currentHost() {
		return state == null ? "" : state.hostName;
	}

	public static void stop(MinecraftServer server) {
		stop(server, "остановлено");
	}

	/** {@code reason} попадает в журнал МП, например «остановил администратор Nick». */
	public static void stop(MinecraftServer server, String reason) {
		if (state == null) {
			return;
		}
		state.result = reason == null || reason.isBlank() ? "остановлено" : reason;
		boolean priv = state.privateEvent;
		UUID hostId = state.host;
		MinecraftServer srv = state.server;
		String name = state.name;
		finishCleanup(server, true);
		var msg = Chat.red("Мероприятие «" + name + "» остановлено.");
		if (priv && srv != null && hostId != null) {
			ServerPlayerEntity host = srv.getPlayerManager().getPlayer(hostId);
			if (host != null) {
				Chat.send(host, msg);
			}
		} else {
			Chat.broadcast(server, msg);
		}
	}

	/** Прогрев чанков арены, пока идёт 5-секундное ожидание входа. */
	public static void prewarmArena() {
		if (state == null || state.arena == null || state.server == null) {
			return;
		}
		ChunkPrewarm.hold(SpawnService.resolveWorld(state.server, state.dimension), state.arena.centerX(), state.arena.centerZ(), 3);
	}

	/** Человеческое название мира мероприятия. */
	public static String worldLabel(String dimension) {
		return switch (dimension == null ? "" : dimension) {
			case "minecraft:overworld", "" -> "обычном мире";
			case "minecraft:the_nether" -> "Аду";
			case "minecraft:the_end" -> "Энде";
			default -> "мире " + dimension;
		};
	}

	public static boolean join(ServerPlayerEntity player) {
		return joinError(player) == null && enroll(player);
	}

	public static String joinError(ServerPlayerEntity player) {
		if (player == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		if (state.privateEvent) {
			return "Это тестовое мероприятие администратора.";
		}
		String here = player.getWorld().getRegistryKey().getValue().toString();
		if (state.dimension != null && !state.dimension.isBlank() && !state.dimension.equals(here)) {
			return "Мероприятие проходит в " + worldLabel(state.dimension) + ". Зайдите в этот мир, чтобы участвовать.";
		}
		if (!state.restrictedMembers.isEmpty() && !state.restrictedMembers.contains(player.getUuid())) {
			return "Это закрытая дуэль других игроков.";
		}
		if (TrainingService.active(player)) {
			return "Сначала выйдите с тренировки (/off).";
		}
		if (ServerStopService.blocks(player)) {
			return "Сервер останавливается — мероприятия недоступны.";
		}
		if (FreezeService.frozen(player)) {
			return "Вы заморожены — мероприятия недоступны.";
		}
		if (ClanWarService.memberOfWar(player) || ClanWarService.jailed(player)) {
			return "Нельзя зайти на МП во время битвы кланов.";
		}
		if (state.host != null && player.getUuid().equals(state.host) && !state.hostPlays) {
			return "Вы организатор этого мероприятия.";
		}
		if (state.members.containsKey(player.getUuid())) {
			return "Вы уже на мероприятии.";
		}
		if (state.watchers.containsKey(player.getUuid())) {
			return "Вы наблюдаете за мероприятием — сначала вернитесь (/event → «Вернуться»).";
		}
		if (state.phase != Phase.REGISTER) {
			return "Регистрация закрыта, бой уже идёт.";
		}
		if (memberCount() >= state.maxPlayers) {
			return "Нет свободных мест.";
		}
		return null;
	}

	private static boolean enroll(ServerPlayerEntity player) {
		if (state == null || player == null) {
			return false;
		}
		Member member = new Member();
		member.inventoryNbt = InventoryUtil.serialize(player);
		member.x = player.getX();
		member.y = player.getY();
		member.z = player.getZ();
		member.yaw = player.getYaw();
		member.pitch = player.getPitch();
		member.dimension = player.getWorld().getRegistryKey().getValue().toString();
		member.health = player.getHealth();
		member.food = player.getHungerManager().getFoodLevel();
		member.saturation = player.getHungerManager().getSaturationLevel();
		if (!InventoryVault.save(player, InventoryVault.EVENT, member.inventoryNbt, member.dimension,
				member.x, member.y, member.z, member.yaw, member.pitch, player.interactionManager.getGameMode(),
				member.health, member.food, member.saturation)) {
			Chat.send(player, Chat.red("Не удалось сохранить ваш инвентарь на диск — вход на мероприятие отменён."));
			return false;
		}
		state.members.put(player.getUuid(), member);
		GodModeService.suspend(player, "МП");
		player.closeHandledScreen();
		ActivityLock.sync(player);
		teleportArena(player, state.members.size() - 1, "телепортировался на мероприятие");
		if (state.bar != null) {
			state.bar.addPlayer(player);
		}
		title(player, state.name, "Ожидание игроков…");
		announce(Chat.yellow(player.getGameProfile().getName()
				+ " записался (" + memberCount() + "/" + state.maxPlayers + ")."));
		if (memberCount() >= state.maxPlayers) {
			beginCountdown();
		}
		return true;
	}

	public static boolean leave(ServerPlayerEntity player, boolean fromCommand) {
		return leave(player, fromCommand, false);
	}

	public static boolean forceLeave(ServerPlayerEntity player) {
		return leave(player, false, true);
	}

	public static boolean leave(ServerPlayerEntity player, boolean fromCommand, boolean force) {
		if (state == null) {
			return false;
		}
		if (fromCommand && !force && state.phase == Phase.FIGHT) {
			return false;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null) {
			return false;
		}
		restore(player, member);
		member.alive = false;
		state.members.remove(player.getUuid());
		ActivityLock.sync(player);
		if (fromCommand) {
			Chat.send(player, Chat.green("Вы покинули мероприятие."));
		}
		if (state.phase == Phase.FIGHT) {
			checkWinner(player.getServer());
		} else if (state.phase == Phase.REGISTER) {
			updateBar();
		}
		return true;
	}

	public static String kick(ServerPlayerEntity admin, String nick) {
		if (admin == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		if (!canManage(admin)) {
			return state.host == null ? "Кикнуть с авто-МП может администратор."
					: "Кикать с МП может администратор, который его начал, или куратор администрации и выше.";
		}
		if (nick == null || nick.isBlank()) {
			return "Укажите ник.";
		}
		ServerPlayerEntity target = null;
		for (ServerPlayerEntity online : admin.getServer().getPlayerManager().getPlayerList()) {
			if (nick.equalsIgnoreCase(online.getGameProfile().getName())) {
				target = online;
				break;
			}
		}
		if (target == null) {
			return "Игрок «" + nick + "» не в сети.";
		}
		if (state.host != null && target.getUuid().equals(state.host) && !state.hostPlays) {
			return "Нельзя кикнуть организатора.";
		}
		if (!state.members.containsKey(target.getUuid())) {
			return "Игрок «" + target.getGameProfile().getName() + "» не записан на мероприятие.";
		}
		String kicked = target.getGameProfile().getName();
		boolean organizer = admin.getUuid().equals(state.host);
		announce(Chat.yellow((organizer ? "Организатор" : "Администратор " + admin.getGameProfile().getName())
				+ " исключил " + kicked + " с мероприятия."));
		Chat.send(target, Chat.red("Вас исключили с мероприятия."));
		JournalLog.add(JournalLog.EVENTS, "Кик с «" + state.name + "» · " + kicked + " · исключил "
				+ admin.getGameProfile().getName());
		leave(target, false);
		return null;
	}

	/**
	 * Организатор МП или куратор администрации и выше — управление идущим мероприятием из /event.
	 * У авто-МП организатора нет, поэтому им, как и раньше с киком, управляет любой администратор.
	 */
	public static boolean canManage(ServerPlayerEntity player) {
		if (player == null || state == null) {
			return false;
		}
		if (player.getUuid().equals(state.host)) {
			return true;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		if (state.host == null && data.isAdmin(player)) {
			return true;
		}
		return data.getAdminLevel(player) >= ru.admincontrol.data.AdminRanks.CURATOR;
	}

	/** type — индекс в EventCatalog (у стандартных МП совпадает с индексом в TYPES). */
	public static boolean blocked(int type) {
		return EventCatalog.blocked(type);
	}

	public static int blockedMask() {
		return EventCatalog.blockedMask();
	}

	public static void setBlocked(int type, boolean block) {
		EventCatalog.setBlocked(type, block);
	}

	public static int currentChoice() {
		return state == null ? -1 : state.choice;
	}

	public static String currentKey() {
		return state == null ? "" : state.key;
	}

	/** Участники в стабильном порядке — индексы кнопок кика и выдачи приза в /event. */
	public static java.util.List<UUID> roster() {
		java.util.List<UUID> out = new java.util.ArrayList<>();
		if (state == null) {
			return out;
		}
		for (UUID id : state.members.keySet()) {
			if (out.size() >= 32) {
				break;
			}
			out.add(id);
		}
		return out;
	}

	public static String memberName(UUID id) {
		if (state == null || id == null || state.server == null) {
			return "";
		}
		ServerPlayerEntity online = state.server.getPlayerManager().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		DataManager data = DataManager.get();
		String name = data == null ? null : data.findByUuid(id).map(r -> r.nickname).orElse(null);
		return name == null || name.isBlank() ? id.toString().substring(0, 8) : name;
	}

	public static boolean memberAlive(UUID id) {
		Member member = state == null || id == null ? null : state.members.get(id);
		return member != null && member.alive && !member.restored;
	}

	public static boolean awarded(UUID id) {
		return state != null && id != null && state.awarded.contains(id);
	}

	public static boolean watching(ServerPlayerEntity player) {
		return player != null && state != null && state.watchers.containsKey(player.getUuid());
	}

	public static int prizeAmount() {
		if (state == null) {
			return 0;
		}
		return isRace() ? state.prize1 : state.reward;
	}

	/** Ручная выдача приза из /event, если МП сломалось и не завершилось само. */
	public static String award(ServerPlayerEntity admin, java.util.List<UUID> ids) {
		if (admin == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		if (!canManage(admin)) {
			return "Выдать приз может администратор, который начал МП, или куратор администрации и выше.";
		}
		if (state.privateEvent) {
			return "На тестовом МП призы не выдаются.";
		}
		if (ids == null || ids.isEmpty()) {
			return "Отметьте игроков, которым выдать приз.";
		}
		int amount = prizeAmount();
		java.util.List<String> got = new java.util.ArrayList<>();
		java.util.List<String> skipped = new java.util.ArrayList<>();
		for (UUID id : ids) {
			if (id == null || !state.members.containsKey(id)) {
				continue;
			}
			String name = memberName(id);
			if (state.awarded.contains(id)) {
				skipped.add(name + " (уже выдан)");
				continue;
			}
			ServerPlayerEntity winner = state.server.getPlayerManager().getPlayer(id);
			if (winner == null) {
				skipped.add(name + " (не в сети)");
				continue;
			}
			state.awarded.add(id);
			if (amount > 0) {
				Economy.giveCurrency(winner, amount, "приз за мероприятие «" + state.name + "» (выдал "
						+ admin.getGameProfile().getName() + ")");
			}
			PlayerRecord record = DataManager.get().progress(winner);
			String bonus = "";
			if (isMeat()) {
				double luck = Math.max(0, state.raceLuckTenths) / 10.0;
				if (luck > 0) {
					double from = record.luck();
					record.luck = Math.round((from + luck) * 10.0) / 10.0;
					ProgressLog.luck(record, from, record.luck(), ProgressLog.EVENT);
					bonus = ", удача +" + String.format(java.util.Locale.US, "%.1f", luck);
				}
			} else if (state.powerTenths > 0) {
				double from = record.power();
				record.addEventPower(state.powerTenths / 10.0);
				ProgressLog.power(record, from, record.power(), ProgressLog.EVENT);
				bonus = ", сила +" + String.format(java.util.Locale.US, "%.1f", state.powerTenths / 10.0);
			}
			DataManager.get().markDirty();
			BalanceBoard.refresh(winner, true);
			title(winner, "ПРИЗ", "+" + Money.format(amount) + " WC");
			Chat.send(winner, Chat.green("Вам выдан приз за мероприятие: " + Money.format(amount) + " WC" + bonus + "."));
			got.add(name);
		}
		DataManager.get().saveIfDirty();
		if (!skipped.isEmpty()) {
			Chat.send(admin, Chat.yellow("Не выдано: " + String.join(", ", skipped) + "."));
		}
		if (got.isEmpty()) {
			return skipped.isEmpty() ? "Отмеченные игроки уже не на мероприятии." : null;
		}
		String list = String.join(", ", got);
		announce(Chat.gold("Приз мероприятия «" + state.name + "» выдан: " + list + " (+" + Money.format(amount) + " WC)."));
		Chat.send(admin, Chat.green("Приз выдан: " + list + "."));
		JournalLog.add(JournalLog.EVENTS, "Ручной приз «" + state.name + "» · " + list + " · " + amount
				+ " WC · выдал " + admin.getGameProfile().getName());
		AdminNotify.playerAction(admin, ru.admincontrol.data.ActionKind.ADMIN,
				"выдал приз МП «" + state.name + "»: " + list + " (" + amount + " WC)");
		return null;
	}

	/** Перезапуск идущего МП с теми же участниками: новая арена, сброс счёта, отсчёт заново. */
	public static String restart(ServerPlayerEntity admin) {
		if (admin == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		if (!canManage(admin)) {
			return "Перезапустить МП может администратор, который его начал, или куратор администрации и выше.";
		}
		if (state.phase == Phase.REGISTER) {
			return "Мероприятие ещё не началось — перезапускать нечего.";
		}
		MinecraftServer server = state.server;
		ServerWorld world = server == null ? null : SpawnService.resolveWorld(server, state.dimension);
		if (world == null || state.arena == null) {
			return "Мир мероприятия недоступен.";
		}
		java.util.List<ServerPlayerEntity> players = new java.util.ArrayList<>();
		for (UUID id : new java.util.ArrayList<>(state.members.keySet())) {
			Member member = state.members.get(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (member == null || player == null) {
				state.members.remove(id);
				continue;
			}
			if (member.restored) {
				if (!resnapshot(player, member)) {
					state.members.remove(id);
					Chat.send(player, Chat.red("Не удалось сохранить ваш инвентарь на диск — в перезапуск МП вы не попали."));
					continue;
				}
			} else {
				if (player.hasVehicle()) {
					player.stopRiding();
				}
				if (state.phase == Phase.FIGHT) {
					player.getInventory().clear();
				}
			}
			member.alive = true;
			member.gems = 0;
			member.hill = 0;
			member.raceArmed = false;
			member.footTrack = false;
			member.kickCool = 0;
			players.add(player);
		}
		if (players.isEmpty()) {
			stop(server, "перезапуск: нет участников в сети (" + admin.getGameProfile().getName() + ")");
			return "Участников в сети нет — мероприятие остановлено.";
		}
		discardFootball();
		clearHuntMobs();
		BlockPos stand = state.arena.stand;
		state.arena.sweepDebris();
		state.arena.remove();
		state.arena = new EventArena();
		state.arena.raceBumpers = state.raceBumpers;
		state.arena.mapKey = state.key;
		state.arena.build(world, stand, state.typeIndex);
		state.scoreA = 0;
		state.scoreB = 0;
		state.huntKills = 0;
		state.huntGoal = huntGoal();
		state.fightAge = 0;
		state.ringAge = 0;
		state.lastHit = null;
		state.lastDeath = null;
		state.lastDeathAge = 0;
		state.finishOrder.clear();
		state.raceGrace = 0;
		for (ServerPlayerEntity player : players) {
			player.clearStatusEffects();
			reviveInPlace(player);
			player.getHungerManager().setFoodLevel(20);
			player.getHungerManager().setSaturationLevel(5f);
			if (player.interactionManager.getGameMode() == GameMode.SPECTATOR) {
				player.changeGameMode(GameMode.SURVIVAL);
			}
			player.closeHandledScreen();
			ActivityLock.sync(player);
			if (state.bar != null) {
				state.bar.addPlayer(player);
			}
		}
		state.phase = Phase.REGISTER;
		beginCountdown();
		if (state == null) {
			return null;
		}
		announce(Chat.gold("Мероприятие «" + state.name + "» перезапущено с теми же участниками ("
				+ memberCount() + ")."));
		JournalLog.add(JournalLog.EVENTS, "Перезапуск «" + state.name + "» · " + admin.getGameProfile().getName()
				+ " · участников " + memberCount());
		AdminNotify.playerAction(admin, ru.admincontrol.data.ActionKind.ADMIN,
				"перезапустил МП «" + state.name + "» (участников " + memberCount() + ")");
		return null;
	}

	/** Выбывший участник уже вернулся домой — снова сохраняем его вещи и точку возврата перед перезапуском. */
	private static boolean resnapshot(ServerPlayerEntity player, Member member) {
		member.inventoryNbt = InventoryUtil.serialize(player);
		member.x = player.getX();
		member.y = player.getY();
		member.z = player.getZ();
		member.yaw = player.getYaw();
		member.pitch = player.getPitch();
		member.dimension = player.getWorld().getRegistryKey().getValue().toString();
		member.health = player.getHealth();
		member.food = player.getHungerManager().getFoodLevel();
		member.saturation = player.getHungerManager().getSaturationLevel();
		if (!InventoryVault.save(player, InventoryVault.EVENT, member.inventoryNbt, member.dimension,
				member.x, member.y, member.z, member.yaw, member.pitch, player.interactionManager.getGameMode(),
				member.health, member.food, member.saturation)) {
			return false;
		}
		member.restored = false;
		GodModeService.suspend(player, "МП");
		return true;
	}

	/** ТП на МП из /event: организатор — на точку наблюдения, остальные — наблюдателем с возвратом обратно. */
	public static String watch(ServerPlayerEntity admin) {
		if (admin == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		if (!canManage(admin)) {
			return "ТП на МП доступно администратору, который его начал, и куратору администрации и выше.";
		}
		if (state.members.containsKey(admin.getUuid())) {
			return "Вы участник мероприятия и уже на нём.";
		}
		ServerWorld world = state.server == null ? null : SpawnService.resolveWorld(state.server, state.dimension);
		if (world == null || state.arena == null) {
			return "Мир мероприятия недоступен.";
		}
		boolean host = admin.getUuid().equals(state.host);
		if (!host && !state.watchers.containsKey(admin.getUuid())) {
			if (TrainingService.active(admin)) {
				return "Сначала выйдите с тренировки (/off).";
			}
			if (EventMapEditor.participant(admin)) {
				return "Сначала закончите правку карты МП.";
			}
			if (FreezeService.frozen(admin)) {
				return "Вы заморожены.";
			}
			Watcher watcher = new Watcher();
			watcher.dimension = admin.getWorld().getRegistryKey().getValue().toString();
			watcher.x = admin.getX();
			watcher.y = admin.getY();
			watcher.z = admin.getZ();
			watcher.yaw = admin.getYaw();
			watcher.pitch = admin.getPitch();
			watcher.mode = admin.interactionManager.getGameMode();
			state.watchers.put(admin.getUuid(), watcher);
		}
		admin.changeGameMode(GameMode.SPECTATOR);
		TeleportLog.labeled("телепортировался на мероприятие (наблюдение)", () -> WorldGate.allow(() ->
				admin.teleport(world, hostSpectateX(), hostSpectateY(), hostSpectateZ(), admin.getYaw(), 40)));
		Chat.send(admin, Chat.yellow(host ? "Вы на точке наблюдения мероприятия."
				: "Вы наблюдаете за мероприятием. Вернуться: /event → «Вернуться»."));
		return null;
	}

	public static String unwatch(ServerPlayerEntity admin) {
		if (admin == null || state == null) {
			return "Сейчас нет мероприятия.";
		}
		Watcher watcher = state.watchers.remove(admin.getUuid());
		if (watcher == null) {
			return "Вы не наблюдаете за мероприятием.";
		}
		returnWatcher(admin, watcher);
		Chat.send(admin, Chat.green("Вы вернулись с мероприятия."));
		return null;
	}

	private static void returnWatcher(ServerPlayerEntity admin, Watcher watcher) {
		MinecraftServer server = admin.getServer();
		ServerWorld world = server == null ? null : SpawnService.resolveWorld(server, watcher.dimension);
		if (world == null) {
			world = (ServerWorld) admin.getWorld();
		}
		admin.changeGameMode(watcher.mode == null ? GameMode.SURVIVAL : watcher.mode);
		ServerWorld target = world;
		TeleportLog.labeled("вернулся с наблюдения за мероприятием", () -> WorldGate.allow(() ->
				admin.teleport(target, watcher.x, watcher.y, watcher.z, watcher.yaw, watcher.pitch)));
	}

	private static void releaseWatchers(MinecraftServer server) {
		if (state == null || state.watchers.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Watcher> entry : new java.util.ArrayList<>(state.watchers.entrySet())) {
			ServerPlayerEntity admin = server == null ? null : server.getPlayerManager().getPlayer(entry.getKey());
			if (admin != null) {
				returnWatcher(admin, entry.getValue());
			}
		}
		state.watchers.clear();
	}

	public static void showBar(ServerPlayerEntity player) {
		if (player == null || state == null || state.bar == null || state.privateEvent) {
			return;
		}
		if (ru.admincontrol.util.NpcService.isNpc(player)) {
			return;
		}
		state.bar.addPlayer(player);
	}

	private static void syncBarPlayers() {
		if (state == null || state.bar == null || state.server == null) {
			return;
		}
		if (state.privateEvent) {
			ServerPlayerEntity host = state.host == null ? null : state.server.getPlayerManager().getPlayer(state.host);
			if (host != null) {
				state.bar.addPlayer(host);
			}
			return;
		}
		for (ServerPlayerEntity player : state.server.getPlayerManager().getPlayerList()) {
			if (!ru.admincontrol.util.NpcService.isNpc(player)) {
				state.bar.addPlayer(player);
			}
		}
	}

	public static boolean divertDeath(ServerPlayerEntity player) {
		return divertDeath(player, null);
	}

	public static boolean divertDeath(ServerPlayerEntity player, net.minecraft.entity.damage.DamageSource source) {
		if (state == null || state.phase != Phase.FIGHT) {
			return false;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return false;
		}
		if (isStrike()) {
			strikeRespawn(player, member, source);
			return true;
		}
		if (isTreasure() || isGems() || isFootball() || isHill()) {
			giveKit(player);
			teleportArena(player, memberIndex(player.getUuid()), null);
			reviveInPlace(player);
			title(player, "ПАДЕНИЕ", isFootball() ? "Вернитесь в игру" : isGems() ? "Соберите самоцветы"
					: isHill() ? "Вершина ждёт: " + member.hill / 20 + "/" + HILL_GOAL + " с" : "Вернитесь за сокровищем");
			return true;
		}
		eliminate(player);
		return true;
	}

	private static void vacuumDrops(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1.25), e -> true)) {
			item.discard();
		}
	}

	public static void onDeath(ServerPlayerEntity player) {
		if (state == null || state.phase != Phase.FIGHT) {
			return;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return;
		}
		eliminate(player);
	}

	private static void eliminate(ServerPlayerEntity player) {
		if (state == null || player == null) {
			return;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return;
		}
		member.alive = false;
		state.lastDeath = player.getUuid();
		state.lastDeathAge = state.fightAge;
		if (player.hasVehicle()) {
			net.minecraft.entity.Entity vehicle = player.getVehicle();
			player.stopRiding();
			if (vehicle instanceof net.minecraft.entity.vehicle.BoatEntity) {
				vehicle.discard();
			}
		}
		restore(player, member);
		Chat.send(player, Chat.red("Вы выбыли с мероприятия."));
		if (isHunt()) {
			declareLose(player.getServer(), "команда потеряла участника");
			return;
		}
		checkWinner(player.getServer());
	}

	private static void reviveInPlace(ServerPlayerEntity player) {
		player.setHealth(player.getMaxHealth());
		player.fallDistance = 0;
		player.setVelocity(0, 0, 0);
		player.velocityModified = true;
		player.extinguish();
		player.hurtTime = 0;
		player.deathTime = 0;
	}

	public static boolean allowBreak(ServerPlayerEntity player, BlockPos pos) {
		if (state == null || state.arena == null || player == null || !state.arena.contains(pos)) {
			return true;
		}
		if (player.getUuid().equals(state.host) && !state.members.containsKey(player.getUuid())) {
			return false;
		}
		if (state.phase != Phase.FIGHT) {
			return false;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return false;
		}
		if (EventCatalog.breakable(state.key, player.getWorld().getBlockState(pos).getBlock())) {
			return true;
		}
		if (isTreasure()) {
			return state.arena.isTreasure(pos);
		}
		if (isGems()) {
			return state.arena.isGem(pos);
		}
		if (isSpleef()) {
			var block = player.getWorld().getBlockState(pos).getBlock();
			return block == Blocks.SNOW_BLOCK || block == Blocks.SNOW;
		}
		if (isStrike()) {
			return strikeBreakable(player.getWorld().getBlockState(pos));
		}
		return false;
	}

	/** Страйкбол: ломаются только трава, цветы, стёкла и двери. */
	private static boolean strikeBreakable(net.minecraft.block.BlockState s) {
		var block = s.getBlock();
		if (block == Blocks.GRASS_BLOCK || block == Blocks.GRASS || block == Blocks.TALL_GRASS
				|| block == Blocks.FERN || block == Blocks.LARGE_FERN) {
			return true;
		}
		if (block instanceof net.minecraft.block.FlowerBlock || block instanceof net.minecraft.block.TallFlowerBlock
				|| block instanceof net.minecraft.block.DoorBlock) {
			return true;
		}
		String id = net.minecraft.registry.Registries.BLOCK.getId(block).getPath();
		return id.equals("glass") || id.equals("glass_pane") || id.equals("tinted_glass") || id.endsWith("_stained_glass")
				|| id.endsWith("_stained_glass_pane");
	}

	public static void onBlockBroken(ServerPlayerEntity player, BlockPos pos) {
		if (player == null || pos == null || !fighting() || state.arena == null) {
			return;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return;
		}
		if (isTreasure() && state.arena.isTreasure(pos)) {
			vacuumDrops(player, pos);
			declareWinner(player.getServer(), player);
			return;
		}
		if (isGems() && state.arena.isGem(pos)) {
			vacuumDrops(player, pos);
			state.arena.collectGem(pos);
			member.gems++;
			title(player, "САМОЦВЕТ " + member.gems + "/" + state.gemGoal, "Ломайте цветные блоки");
			if (member.gems >= state.gemGoal) {
				declareWinner(player.getServer(), player);
			}
		}
	}

	public static void noteCombat(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
		if (!fighting() || attacker == null || victim == null) {
			return;
		}
		if (state.members.containsKey(attacker.getUuid()) && state.members.containsKey(victim.getUuid())) {
			state.lastHit = attacker.getUuid();
		}
	}

	public static void onHostJoin(ServerPlayerEntity player) {
		if (!isHost(player) || state.arena == null) {
			return;
		}
		if (state.hostPlays || state.members.containsKey(player.getUuid())) {
			return;
		}
		player.changeGameMode(GameMode.SPECTATOR);
		ServerWorld world = SpawnService.resolveWorld(player.getServer(), state.dimension);
		TeleportLog.labeled("телепортировался на мероприятие (организатор)", () ->
				player.teleport(world, hostSpectateX(), hostSpectateY(), hostSpectateZ(), player.getYaw(), 40));
		if (state.bar != null) {
			state.bar.addPlayer(player);
		}
	}

	public static void tick(MinecraftServer server) {
		if (state == null) {
			return;
		}
		if (state.arena != null) {
			if (isRace() && state.phase == Phase.FIGHT && state.raceGrace > 0) {
				state.raceGrace--;
			}
			for (UUID id : new ArrayList<>(state.members.keySet())) {
				if (state == null || state.arena == null) {
					return;
				}
				Member member = state.members.get(id);
				if (member == null || !member.alive) {
					continue;
				}
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				if (player == null) {
					continue;
				}
				if (member.strikeRespawnTicks > 0) {
					member.strikeRespawnTicks--;
					if (member.strikeRespawnTicks == 0) {
						finishStrikeRespawn(player, member);
					}
					continue;
				}
				state.arena.keepInside(player);
				if (state == null) {
					return;
				}
				if (state.phase == Phase.FIGHT && isSpleef() && state.arena.spleefFallen(player)) {
					onDeath(player);
				}
				if (state == null) {
					return;
				}
				if (state.phase == Phase.FIGHT && isDuel() && state.arena.duelFallen(player)) {
					title(player, "ВЫБЫЛ", "Падение");
					eliminate(player);
					continue;
				}
				if (state == null) {
					return;
				}
				if (state.phase == Phase.FIGHT && isKnock() && state.arena.knockFallen(player)) {
					title(player, "ВЫБЫЛ", "Падение с островка");
					eliminate(player);
					continue;
				}
				if (state == null) {
					return;
				}
				if (state.phase == Phase.FIGHT && isParty() && state.arena.partyFallen(player)) {
					title(player, "ВЫБЫЛ", "Упал с цветного пола");
					eliminate(player);
					continue;
				}
				if (state == null) {
					return;
				}
				if (state.phase == Phase.FIGHT && isRace()) {
					boolean off = state.arena.raceOffTrack(player);
					if (state.raceGrace <= 0 && (off || !inRaceBoat(player))) {
						title(player, "ВЫБЫЛ", off ? "Съезд с трассы" : "Без лодки");
						eliminate(player);
						continue;
					}
					if (state == null) {
						return;
					}
					member = state.members.get(id);
					if (member == null || !member.alive) {
						continue;
					}
					if (state.arena.raceCheckpoint(player)) {
						member.raceArmed = true;
					}
					if (member.raceArmed && state.arena.crossedFinish(player)) {
						noteRaceFinish(server, player);
					}
				}
			}
		}
		if (state == null) {
			return;
		}
		if (state.phase == Phase.REGISTER) {
			state.ticks--;
			updateBar();
			if (state.ticks <= 0) {
				if (memberCount() >= MIN_PLAYERS) {
					beginCountdown();
				} else {
					announce(Chat.red(state.name + " отменён: нужно минимум "
							+ MIN_PLAYERS + " игрока."));
					stop(server, "отменено: не набралось " + MIN_PLAYERS + " игрока");
				}
			}
			return;
		}
		if (state.phase == Phase.COUNTDOWN) {
			freezeAll(server);
			if (state.ticks % 20 == 0) {
				int sec = Math.max(1, (state.ticks + 19) / 20);
				for (UUID id : state.members.keySet()) {
					ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
					if (player != null) {
						title(player, String.valueOf(sec), "Приготовьтесь");
					}
				}
			}
			state.ticks--;
			updateBar();
			if (state.ticks <= 0) {
				beginFight(server);
			}
			return;
		}
		if (isTreasure()) {
			if (state.testSolo) {
				if (state.arena != null && !state.arena.treasurePresent()) {
					finishByLastHit(server);
					return;
				}
			} else {
				state.ticks--;
				if (state.arena != null && !state.arena.treasurePresent()) {
					finishByLastHit(server);
					return;
				}
				if (state.ticks <= 0) {
					finishByLastHit(server);
					return;
				}
			}
		} else if (isHunt()) {
			tickHunt(server);
		} else if (isMeat()) {
			state.fightAge++;
			tickMeatMobs(server);
			if (state.fightAge >= 30 * 20) {
				checkWinner(server);
			}
		} else if (isRing()) {
			state.ringAge++;
			if (state.ringAge > 0 && state.ringAge % (8 * 20) == 0 && state.arena != null) {
				state.arena.shrinkRing();
				announce(Chat.gold("Лава сужается!"));
			}
		} else if (isFootball()) {
			tickFootball(server);
		} else if (isStrike()) {
			tickStrike(server);
		} else if (isHill()) {
			tickHill(server);
		} else if (isParty()) {
			tickParty(server);
		}
		if (state == null) {
			return;
		}
		updateBar();
	}

	/** Царь горы: очко идёт, только пока на вершине ровно один живой участник. */
	private static void tickHill(MinecraftServer server) {
		state.ticks--;
		List<ServerPlayerEntity> top = new ArrayList<>();
		int living = 0;
		for (var entry : state.members.entrySet()) {
			if (!entry.getValue().alive) {
				continue;
			}
			living++;
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player != null && state.arena != null && state.arena.onHillTop(player)) {
				top.add(player);
			}
		}
		if (living == 0) {
			announce(Chat.gold("Мероприятие «" + state.name + "» завершено без победителя."));
			state.result = "без победителя";
			finishCleanup(server, true);
			return;
		}
		if (top.size() == 1) {
			ServerPlayerEntity king = top.get(0);
			Member member = state.members.get(king.getUuid());
			member.hill++;
			int sec = member.hill / 20;
			if (member.hill % 20 == 0) {
				king.sendMessage(Text.literal("♛ Вершина ваша: " + sec + "/" + HILL_GOAL + " с").formatted(Formatting.GOLD), true);
				if (sec == HILL_GOAL / 2 || sec == HILL_GOAL - 15 || sec == HILL_GOAL - 5) {
					announce(Chat.yellow(king.getGameProfile().getName() + " держит вершину: " + sec + "/" + HILL_GOAL + " с!"));
				}
			}
			if (sec >= HILL_GOAL) {
				announce(Chat.gold(king.getGameProfile().getName() + " удержал вершину " + HILL_GOAL + " секунд!"));
				declareWinner(server, king);
				return;
			}
		} else if (top.size() > 1 && state.ticks % 20 == 0) {
			for (ServerPlayerEntity player : top) {
				player.sendMessage(Text.literal("⚔ Вершина оспаривается — сбросьте соперников!").formatted(Formatting.RED), true);
			}
		}
		if (state.ticks <= 0) {
			finishHill(server);
		}
	}

	private static void finishHill(MinecraftServer server) {
		UUID best = null;
		int bestTicks = 0;
		boolean tie = false;
		for (var entry : state.members.entrySet()) {
			int t = entry.getValue().hill / 20;
			if (t > bestTicks) {
				best = entry.getKey();
				bestTicks = t;
				tie = false;
			} else if (t == bestTicks && t > 0) {
				tie = true;
			}
		}
		ServerPlayerEntity winner = best == null || tie ? null : server.getPlayerManager().getPlayer(best);
		if (winner != null && memberAlive(best)) {
			announce(Chat.gold("Время вышло. Дольше всех вершину держал " + winner.getGameProfile().getName()
					+ " — " + bestTicks + " с."));
			declareWinner(server, winner);
			return;
		}
		announce(Chat.gold("Время вышло. «" + state.name + "» завершено без победителя"
				+ (tie ? " — ничья по секундам на вершине." : ".")));
		state.result = tie ? "без победителя (ничья, по " + bestTicks + " с на вершине)" : "без победителя";
		finishCleanup(server, true);
	}

	private static int partyTile() {
		return state.partyRound >= 10 ? 1 : state.partyRound >= 5 ? 2 : 3;
	}

	/** Цветной пол: новый узор и цвет; с каждым раундом на поиск плитки меньше времени. */
	private static void startPartyRound(MinecraftServer server) {
		state.partyRound++;
		state.partyStage = 0;
		state.partyTimer = Math.max(30, 110 - (state.partyRound - 1) * 8);
		java.util.Random rng = new java.util.Random();
		if (state.arena != null) {
			if (state.partyRound > 1) {
				state.arena.paintParty(rng, partyTile(), false);
			}
			state.partyColor = state.arena.partyPickColor(rng);
		}
		String color = partyColorName().toUpperCase(java.util.Locale.ROOT);
		for (var entry : state.members.entrySet()) {
			ServerPlayerEntity player = entry.getValue().alive ? server.getPlayerManager().getPlayer(entry.getKey()) : null;
			if (player == null) {
				continue;
			}
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, 30, 6));
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
					Text.literal("■ " + color + " ■").formatted(partyColorFormat(), Formatting.BOLD)));
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
					Text.literal("Раунд " + state.partyRound + " — встаньте на этот цвет").formatted(Formatting.YELLOW)));
			player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
					net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.2f);
		}
	}

	private static void tickParty(MinecraftServer server) {
		if (state.partyRound == 0) {
			startPartyRound(server);
			return;
		}
		state.partyTimer--;
		if (state.partyStage == 0) {
			if (state.partyTimer % 20 == 0 && state.partyTimer > 0) {
				int sec = state.partyTimer / 20;
				for (var entry : state.members.entrySet()) {
					ServerPlayerEntity player = entry.getValue().alive ? server.getPlayerManager().getPlayer(entry.getKey()) : null;
					if (player != null) {
						player.sendMessage(Text.literal("■ " + partyColorName().toUpperCase(java.util.Locale.ROOT) + " ■  " + sec + " с")
								.formatted(partyColorFormat(), Formatting.BOLD), true);
						player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
								net.minecraft.sound.SoundCategory.MASTER, 0.8f, 1.0f + 0.1f * Math.min(5, sec));
					}
				}
			}
			if (state.partyTimer <= 0) {
				if (state.arena != null) {
					state.arena.dropParty(state.partyColor);
				}
				state.partyStage = 1;
				state.partyTimer = 60;
				for (var entry : state.members.entrySet()) {
					ServerPlayerEntity player = entry.getValue().alive ? server.getPlayerManager().getPlayer(entry.getKey()) : null;
					if (player != null) {
						player.sendMessage(Text.literal("СТОП!").formatted(Formatting.RED, Formatting.BOLD), true);
						player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
								net.minecraft.sound.SoundCategory.MASTER, 1.0f, 0.6f);
					}
				}
			}
			return;
		}
		if (state.partyTimer <= 0) {
			startPartyRound(server);
		}
	}

	private static String partyColorName() {
		int c = Math.max(0, Math.min(EventArena.PARTY_COLOR_NAMES.length - 1, state.partyColor));
		return EventArena.PARTY_COLOR_NAMES[c];
	}

	private static Formatting partyColorFormat() {
		int c = Math.max(0, Math.min(EventArena.PARTY_COLOR_FORMATS.length - 1, state.partyColor));
		return EventArena.PARTY_COLOR_FORMATS[c];
	}

	private static void beginCountdown() {
		if (state == null) {
			return;
		}
		if (isFootball() && !prepareFootball()) {
			return;
		}
		if (isStrike()) {
			prepareStrike();
		}
		state.phase = Phase.COUNTDOWN;
		state.ticks = 5 * 20;
		if (isDuel() && state.arena != null) {
			ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
			if (world != null) {
				state.arena.rebuildDuel(world, Math.max(1, memberCount()));
				ServerPlayerEntity host = state.host == null ? null : state.server.getPlayerManager().getPlayer(state.host);
				if (host != null && !state.members.containsKey(host.getUuid())) {
					ServerWorld dest = world;
					TeleportLog.labeled("телепортировался на мероприятие (организатор)", () ->
							host.teleport(dest, hostSpectateX(), hostSpectateY(), hostSpectateZ(), host.getYaw(), 40));
				}
			}
		}
		int i = 0;
		for (UUID id : state.members.keySet()) {
			ServerPlayerEntity player = state.server.getPlayerManager().getPlayer(id);
			if (player != null) {
				teleportArena(player, i++, "телепортировался на арену мероприятия");
			}
		}
		announce(Chat.gold("Регистрация закрыта. Бой через 5 секунд!"));
	}

	private static void beginFight(MinecraftServer server) {
		state.phase = Phase.FIGHT;
		if (isTreasure()) {
			state.ticks = 120 * 20;
			if (state.arena != null) {
				state.arena.placeTreasure();
			}
		} else if (isHunt()) {
			state.ticks = huntSeconds() * 20;
			state.huntKills = 0;
			state.huntGoal = huntGoal();
			for (int n = 0; n < 4; n++) {
				spawnHuntMob();
			}
		} else if (isMeat()) {
			state.fightAge = 0;
			state.lastDeath = null;
			state.lastDeathAge = 0;
			spawnMeatMobs();
		} else if (isRing()) {
			state.ringAge = 0;
		} else if (isGems()) {
			state.gemGoal = 5;
		} else if (isFootball()) {
			state.ticks = 4 * 60 * 20;
			state.scoreA = 0;
			state.scoreB = 0;
			state.footballFreeze = 40;
			spawnFootball();
		} else if (isStrike()) {
			state.ticks = strikeMinutes() * 60 * 20;
			state.scoreA = 0;
			state.scoreB = 0;
			state.strikeGoal = strikeKills();
		} else if (isHill()) {
			state.ticks = HILL_MINUTES * 60 * 20;
			for (Member m : state.members.values()) {
				m.hill = 0;
			}
		} else if (isParty()) {
			state.partyRound = 0;
			state.partyStage = 0;
			state.partyTimer = 0;
		}
		if (isRace()) {
			state.raceGrace = 30;
		}
		int i = 0;
		for (UUID id : state.members.keySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				continue;
			}
			Member member = state.members.get(id);
			giveKit(player);
			teleportArena(player, i++, "телепортировался на арену мероприятия");
			player.setHealth(player.getMaxHealth());
			if (isTreasure()) {
				title(player, "СОКРОВИЩЕ", "Сломайте золотой блок");
			} else if (isSpleef()) {
				title(player, "ОБВАЛ", "Ломайте снег под ногами");
			} else if (isHunt()) {
				title(player, "ОХОТА", "Убейте " + state.huntGoal + " мобов");
			} else if (isDuel()) {
				title(player, "ДУЭЛЬ", "Подорви остров");
			} else if (isRace()) {
				title(player, "ГОНКА", state.raceBumpers ? "С бортиками" : "Без бортиков");
				giveBoat(player);
			} else if (isMeat()) {
				title(player, "МЯСОРУБКА", "Продержитесь 30 сек");
			} else if (isRing()) {
				title(player, "КОЛЬЦО ЛАВЫ", "Не стойте у края");
			} else if (isGems()) {
				title(player, "САМОЦВЕТЫ", "Соберите " + state.gemGoal + " блоков");
			} else if (isKnock()) {
				title(player, "ОТСКОК", "Сбейте всех с островков");
			} else if (isFootball()) {
				int team = member == null ? 0 : member.team;
				title(player, "ФУТБОЛ", team == 0 ? "красные" : "синие");
				paintFootballKit(player, team);
			} else if (isStrike()) {
				int team = member == null ? 0 : member.team;
				title(player, "СТРАЙКБОЛ", (team == 0 ? "красные" : "синие") + " · до " + state.strikeGoal + " убийств");
				paintFootballKit(player, team);
			} else if (isHill()) {
				title(player, "ЦАРЬ ГОРЫ", "Удержите вершину " + HILL_GOAL + " с");
			} else if (isParty()) {
				title(player, "ЦВЕТНОЙ ПОЛ", "Встаньте на названный цвет");
			} else {
				title(player, "БОЙ!", "Победитель забирает всё");
			}
		}
		announce(Chat.gold(state.name + " начался!"));
	}

	private static void freezeAll(MinecraftServer server) {
		for (UUID id : state.members.keySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				player.setVelocity(0, 0, 0);
				player.velocityModified = true;
			}
		}
	}

	private static void teleportArena(ServerPlayerEntity player, int index) {
		teleportArena(player, index, null);
	}

	private static void teleportArena(ServerPlayerEntity player, int index, String reason) {
		if (state == null || state.arena == null) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(player.getServer(), state.dimension);
		double x;
		double y = state.arena.floorY();
		double z;
		float yaw = player.getYaw();
		if (isDuel() || isKnock()) {
			x = state.arena.duelX(index);
			z = state.arena.duelZ(index);
			yaw = state.arena.duelYaw(index);
		} else if (isRace()) {
			x = state.arena.raceSpawnX(index);
			z = state.arena.raceSpawnZ(index);
			yaw = state.arena.raceStartYaw;
		} else if (isFootball()) {
			Member member = state.members.get(player.getUuid());
			int team = member == null ? 0 : member.team;
			int size = 0;
			int slot = 0;
			for (var e : state.members.entrySet()) {
				if (e.getValue().team == team) {
					if (e.getKey().equals(player.getUuid())) {
						slot = size;
					}
					size++;
				}
			}
			x = state.arena.footballSpawnX(team);
			z = state.arena.footballSpawnZ(slot, Math.max(1, size));
			yaw = team == 0 ? -90 : 90;
		} else if (isStrike()) {
			Member member = state.members.get(player.getUuid());
			int team = member == null ? 0 : member.team;
			int slot = member == null ? index : member.teamIndex;
			x = state.arena.strikeSpawnX(team, slot);
			z = state.arena.strikeSpawnZ(slot);
			yaw = team == 0 ? -90 : 90;
		} else {
			double angle = index * (Math.PI * 2 / Math.max(1, state.maxPlayers));
			double r = state.arena.spawnRadius();
			x = state.arena.centerX() + Math.cos(angle) * r;
			z = state.arena.centerZ() + Math.sin(angle) * r;
		}
		final float face = yaw;
		Runnable go = () -> WorldGate.allow(() -> {
			player.teleport(world, x, y, z, face, 0);
			player.fallDistance = 0;
		});
		if (reason == null || reason.isBlank()) {
			TeleportLog.silent(go);
		} else {
			TeleportLog.labeled(reason, go);
		}
	}

	private static int memberIndex(UUID id) {
		int i = 0;
		for (UUID member : state.members.keySet()) {
			if (member.equals(id)) {
				return i;
			}
			i++;
		}
		return 0;
	}

	private static void restore(ServerPlayerEntity player, Member member) {
		if (player == null || member == null || member.restored) {
			return;
		}
		member.restored = true;
		InventoryUtil.restore(player, member.inventoryNbt);
		ServerWorld world = SpawnService.resolveWorld(player.getServer(), member.dimension);
		TeleportLog.labeled("телепортировался с мероприятия", () ->
				WorldGate.allow(() -> player.teleport(world, member.x, member.y, member.z, member.yaw, member.pitch)));
		reviveInPlace(player);
		InventoryVault.applyVitals(player, member.health, member.food, member.saturation);
		InventoryVault.clear(player);
		GodModeService.resume(player);
		ActivityLock.sync(player);
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		if (state == null || player == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		boolean host = isHost(player);
		Watcher watcher = state.watchers.remove(player.getUuid());
		if (watcher != null) {
			returnWatcher(player, watcher);
		}
		if (host && server != null) {
			Member member = state.members.remove(player.getUuid());
			if (member != null) {
				member.alive = false;
				restore(player, member);
			}
			String nick = player.getGameProfile().getName();
			boolean priv = state.privateEvent;
			stop(server, "остановлено без приза: организатор " + nick + " вышел с сервера");
			if (!priv) {
				Chat.broadcast(server, Chat.red("Организатор " + nick + " вышел с сервера — МП остановлено, приз никто не получает."));
			}
			return;
		}
		if (state.members.containsKey(player.getUuid())) {
			leave(player, false);
		}
	}

	private static void finishByLastHit(MinecraftServer server) {
		if (state == null) {
			return;
		}
		ServerPlayerEntity winner = state.lastHit == null ? null : server.getPlayerManager().getPlayer(state.lastHit);
		if (winner != null && state.members.containsKey(winner.getUuid())) {
			declareWinner(server, winner);
			return;
		}
		announce(Chat.gold("Мероприятие «" + state.name + "» завершено без победителя."));
		state.result = "без победителя";
		finishCleanup(server, true);
	}

	private static void checkWinner(MinecraftServer server) {
		if (state == null || isTreasure() || isHunt() || isGems() || isFootball() || isStrike() || isHill()) {
			return;
		}
		if (isMeat()) {
			checkMeatWinner(server);
			return;
		}
		if (isRace()) {
			int living = 0;
			for (Member m : state.members.values()) {
				if (m.alive) {
					living++;
				}
			}
			if (living == 0) {
				finishRace(server);
			}
			return;
		}
		List<UUID> alive = new ArrayList<>();
		for (var entry : state.members.entrySet()) {
			if (entry.getValue().alive) {
				alive.add(entry.getKey());
			}
		}
		if (alive.isEmpty()) {
			announce(Chat.gold("Мероприятие «" + state.name + "» завершено без победителя."));
			state.result = "без победителя";
			finishCleanup(server, false);
			return;
		}
		if (state.testSolo) {
			return;
		}
		if (alive.size() != 1) {
			return;
		}
		ServerPlayerEntity winner = server.getPlayerManager().getPlayer(alive.get(0));
		if (winner != null) {
			declareWinner(server, winner);
		} else {
			state.result = "без победителя (последний выживший вышел с сервера)";
			finishCleanup(server, false);
		}
	}

	private static void noteRaceFinish(MinecraftServer server, ServerPlayerEntity player) {
		if (state == null || player == null) {
			return;
		}
		if (state.finishOrder.contains(player.getUuid())) {
			return;
		}
		state.finishOrder.add(player.getUuid());
		int place = state.finishOrder.size();
		announce(Chat.gold(player.getGameProfile().getName() + " финишировал — " + place + " место."));
		int living = 0;
		for (Member m : state.members.values()) {
			if (m.alive) {
				living++;
			}
		}
		if (state.finishOrder.size() >= Math.max(1, living) || living <= 0) {
			finishRace(server);
		}
	}

	private static void finishRace(MinecraftServer server) {
		if (state == null) {
			return;
		}
		int[] prizes = { state.prize1, state.prize2, state.prize3 };
		StringBuilder places = new StringBuilder();
		for (int i = 0; i < state.finishOrder.size(); i++) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(state.finishOrder.get(i));
			if (p == null) {
				continue;
			}
			places.append(places.length() == 0 ? "" : ", ").append(i + 1).append(". ").append(p.getGameProfile().getName());
			int wc = i < prizes.length ? prizes[i] : 0;
			if (wc > 0) {
				Economy.giveCurrency(p, wc, "гонка " + (i + 1) + " место");
			}
			if (!state.raceBumpers && state.raceLuckTenths > 0 && DataManager.get() != null) {
				PlayerRecord rec = DataManager.get().progress(p);
				double from = rec.luck();
				rec.luck = Math.round((from + state.raceLuckTenths / 10.0) * 10.0) / 10.0;
				ProgressLog.luck(rec, from, rec.luck(), ProgressLog.EVENT);
				DataManager.get().markDirty();
			}
			title(p, i == 0 ? "1 МЕСТО" : (i + 1) + " МЕСТО", wc > 0 ? ("+" + Money.wc(wc)) : "Финиш");
			if (i == 0) {
				Achievements.onEventWin(p);
				ClanTop.mpWin(server, p, null);
			}
		}
		announce(Chat.gold("Гонка завершена."));
		state.result = places.length() == 0 ? "без победителя (никто не финишировал)" : "победители по местам: " + places;
		finishCleanup(server, true);
	}

	private static void giveBoat(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null) {
			return;
		}
		player.stopRiding();
		var boat = net.minecraft.entity.EntityType.BOAT.create(player.getWorld());
		if (boat == null) {
			return;
		}
		boat.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0);
		player.getWorld().spawnEntity(boat);
		player.startRiding(boat, true);
	}

	private static boolean inRaceBoat(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		net.minecraft.entity.Entity vehicle = player.getVehicle();
		return vehicle instanceof net.minecraft.entity.vehicle.BoatEntity && vehicle.isAlive() && !vehicle.isRemoved();
	}

	private static double hostSpectateX() {
		return state != null && state.arena != null && isRace() ? state.arena.raceSpectateX() : state.arena.centerX();
	}

	private static double hostSpectateY() {
		if (state == null || state.arena == null) {
			return 0;
		}
		if (isRace()) {
			return state.arena.raceSpectateY();
		}
		if (isStrike()) {
			return state.arena.minY + EventArena.STRIKE_HEIGHT - 4;
		}
		if (isHill()) {
			return state.arena.minY + 14;
		}
		if (isParty()) {
			return state.arena.minY + 17;
		}
		return isDuel() || state.typeIndex == 4 ? state.arena.minY + 9 : state.arena.minY + 8;
	}

	private static double hostSpectateZ() {
		return state != null && state.arena != null && isRace() ? state.arena.raceSpectateZ() : state.arena.centerZ();
	}

	private static void declareWinner(MinecraftServer server, ServerPlayerEntity winner) {
		if (state == null || winner == null) {
			return;
		}
		Member member = state.members.get(winner.getUuid());
		if (member != null) {
			restore(winner, member);
			// A player-created beta duel is deliberately progression-neutral: it must not
			// grant currency, power/luck, achievements or clan rating.
			if (!state.restrictedMembers.isEmpty()) {
				announce(Chat.gold("Победитель дуэли: " + winner.getGameProfile().getName() + " (без награды)."));
				title(winner, "ПОБЕДА", "Дуэль без награды");
				state.result = "победитель дуэли: " + winner.getGameProfile().getName();
				finishCleanup(server, true);
				return;
			}
			Economy.giveCurrency(winner, state.reward, "награда за мероприятие");
			if (DataManager.get() != null) {
				PlayerRecord record = DataManager.get().progress(winner);
				if (isMeat()) {
					double luck = Math.max(0, state.raceLuckTenths) / 10.0;
					if (luck > 0) {
						double from = record.luck();
						record.luck = Math.round((from + luck) * 10.0) / 10.0;
						ProgressLog.luck(record, from, record.luck(), ProgressLog.EVENT);
					}
					DataManager.get().markDirty();
					DataManager.get().saveIfDirty();
					BalanceBoard.refresh(winner, true);
					announce(Chat.gold("Победитель «" + state.name + "»: "
							+ winner.getGameProfile().getName() + "  +" + state.reward + " WC  •  удача "
							+ String.format(java.util.Locale.US, "%.1f", record.luck())));
					title(winner, "ПОБЕДА", "Удача " + String.format(java.util.Locale.US, "%.1f", record.luck()));
				} else {
					double from = record.power();
					record.addEventPower(state.powerTenths / 10.0);
					ProgressLog.power(record, from, record.power(), ProgressLog.EVENT);
					DataManager.get().markDirty();
					DataManager.get().saveIfDirty();
					BalanceBoard.refresh(winner, true);
					announce(Chat.gold("Победитель «" + state.name + "»: "
							+ winner.getGameProfile().getName() + "  +" + state.reward + " WC  •  сила "
							+ String.format(java.util.Locale.US, "%.1f", record.power())));
					title(winner, "ПОБЕДА", "Сила " + String.format(java.util.Locale.US, "%.1f", record.power()));
				}
			}
			Achievements.onEventWin(winner);
			ClanTop.mpWin(server, winner, null);
		}
		state.result = "победитель: " + winner.getGameProfile().getName();
		finishCleanup(server, true);
	}

	private static void finishCleanup(MinecraftServer server, boolean restoreMembers) {
		if (state == null) {
			return;
		}
		JournalLog.add(JournalLog.EVENTS, "Итог «" + state.name + "» · организатор " + state.hostName
				+ " · участников " + state.members.size() + " · "
				+ (state.result == null || state.result.isBlank() ? "завершено" : state.result));
		ArrayList<ServerPlayerEntity> unlock = new ArrayList<>();
		if (restoreMembers) {
			for (UUID id : new ArrayList<>(state.members.keySet())) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				if (player != null) {
					Member member = state.members.get(id);
					if (member != null && !member.restored) {
						restore(player, member);
					}
					if (state.bar != null) {
						state.bar.removePlayer(player);
					}
					unlock.add(player);
				}
			}
			state.members.clear();
		}
		releaseWatchers(server);
		discardFootball();
		if (state.arena != null) {
			state.arena.sweepDebris();
			clearHuntMobs();
			state.arena.remove();
		}
		clearBar();
		UUID hostId = state.host;
		GameMode hostMode = state.hostMode;
		double hx = state.hostX;
		double hy = state.hostY;
		double hz = state.hostZ;
		float hyaw = state.hostYaw;
		float hpitch = state.hostPitch;
		String hdim = state.hostDimension;
		state = null;
		if (hostId != null) {
			ServerPlayerEntity host = server.getPlayerManager().getPlayer(hostId);
			if (host != null) {
				if (hostMode != null) {
					host.changeGameMode(hostMode);
				}
				ServerWorld world = SpawnService.resolveWorld(server, hdim);
				if (world != null) {
					WorldGate.allow(() -> host.teleport(world, hx, hy, hz, hyaw, hpitch));
				}
				if (!unlock.contains(host)) {
					unlock.add(host);
				}
			}
		}
		for (ServerPlayerEntity player : unlock) {
			ActivityLock.sync(player);
		}
	}

	private static void updateBar() {
		if (state == null || state.bar == null) {
			return;
		}
		syncBarPlayers();
		int online = memberCount();
		String text;
		float pct = 1f;
		if (state.phase == Phase.REGISTER) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			text = "Регистрация " + online + "/" + state.maxPlayers + "  •  от "
					+ MIN_PLAYERS + "  •  " + sec + " сек";
			pct = Math.min(1f, state.ticks / (45f * 20f));
		} else if (state.phase == Phase.COUNTDOWN) {
			text = "Старт через " + Math.max(1, (state.ticks + 19) / 20) + "…";
			pct = Math.min(1f, state.ticks / (5f * 20f));
		} else if (isTreasure()) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			text = "Сокровище  •  " + sec + " сек";
			pct = Math.min(1f, state.ticks / (120f * 20f));
		} else if (isHunt()) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			text = "Охота  " + state.huntKills + "/" + state.huntGoal + "  •  " + sec + " сек";
			pct = state.huntGoal <= 0 ? 1f : Math.min(1f, state.huntKills / (float) state.huntGoal);
		} else if (isMeat()) {
			int sec = Math.max(0, 30 - state.fightAge / 20);
			text = "Мясорубка  •  " + online + " живых  •  "
					+ (state.fightAge >= 30 * 20 ? "можно победить" : ("ещё " + sec + " с"));
			pct = Math.min(1f, state.fightAge / (30f * 20f));
		} else if (isDuel() || isCoverDuel()) {
			text = "Дуэль  •  осталось " + online;
			pct = state.maxPlayers == 0 ? 1f : online / (float) state.maxPlayers;
		} else if (isSpleef()) {
			text = "Обвал  •  осталось " + online;
			pct = state.maxPlayers == 0 ? 1f : online / (float) state.maxPlayers;
		} else if (isFootball()) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			text = "Футбол  " + state.scoreA + ":" + state.scoreB + "  •  " + sec + " сек";
			pct = Math.min(1f, state.ticks / (4f * 60f * 20f));
		} else if (isStrike()) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			text = "Страйкбол  красные " + state.scoreA + " : " + state.scoreB + " синие  •  до "
					+ state.strikeGoal + "  •  " + (sec / 60) + ":" + String.format("%02d", sec % 60);
			pct = Math.min(1f, Math.max(state.scoreA, state.scoreB) / (float) Math.max(1, state.strikeGoal));
		} else if (isHill()) {
			int sec = Math.max(0, (state.ticks + 19) / 20);
			UUID leader = null;
			int lead = 0;
			for (var entry : state.members.entrySet()) {
				if (entry.getValue().hill > lead) {
					lead = entry.getValue().hill;
					leader = entry.getKey();
				}
			}
			text = "Царь горы  •  " + (leader == null ? "вершина свободна" : "лидер " + memberName(leader) + " "
					+ lead / 20 + "/" + HILL_GOAL + " с") + "  •  " + (sec / 60) + ":" + String.format("%02d", sec % 60);
			pct = Math.min(1f, lead / (HILL_GOAL * 20f));
		} else if (isParty()) {
			String stage = state.partyRound == 0 ? "приготовьтесь"
					: state.partyStage == 0 ? "цвет: " + partyColorName() : "держитесь!";
			text = "Цветной пол  •  раунд " + Math.max(1, state.partyRound) + "  •  " + stage + "  •  осталось " + online;
			pct = state.partyStage == 0 && state.partyRound > 0
					? Math.min(1f, state.partyTimer / (float) Math.max(30, 110 - (state.partyRound - 1) * 8)) : 1f;
		} else {
			text = "Бой  •  осталось " + online;
			pct = state.maxPlayers == 0 ? 1f : online / (float) state.maxPlayers;
		}
		pct = Math.max(0.05f, pct);
		if (text.equals(state.lastBar) && Math.abs(pct - state.lastBarPct) < 0.01f) {
			return;
		}
		state.lastBar = text;
		state.lastBarPct = pct;
		state.bar.setName(Text.literal(text).formatted(Formatting.GOLD));
		state.bar.setPercent(pct);
	}

	private static void clearBar() {
		if (state != null && state.bar != null) {
			state.bar.clearPlayers();
			state.bar.setVisible(false);
		}
	}

	private static void title(ServerPlayerEntity player, String big, String small) {
		boolean count = EventFx.isCountdown(big);
		player.networkHandler.sendPacket(count
				? new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, 16, 4)
				: new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(6, 40, 12));
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
				Text.literal(big).formatted(count ? Formatting.YELLOW : Formatting.GOLD, Formatting.BOLD)));
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
				Text.literal(small).formatted(Formatting.YELLOW)));
		EventFx.onTitle(player, big, small);
	}

	private static void giveKit(ServerPlayerEntity player) {
		if (state == null) {
			TrainingConfig.giveEventKit(player, 0);
			return;
		}
		TrainingConfig.giveKit(player, TrainingConfig.kitOf(state.key));
	}

	private static boolean prepareFootball() {
		if (state == null || state.server == null) {
			return false;
		}
		java.util.List<UUID> ids = new java.util.ArrayList<>(state.members.keySet());
		if (state.privateEvent || state.testSolo) {
			int i = 0;
			for (UUID id : ids) {
				Member member = state.members.get(id);
				if (member != null) {
					member.team = i % 2;
					member.teamIndex = i / 2;
					i++;
				}
			}
			return !ids.isEmpty();
		}
		java.util.Random rng = new java.util.Random();
		while (ids.size() % 2 != 0 && ids.size() > 0) {
			java.util.List<UUID> drop = new java.util.ArrayList<>();
			for (UUID id : ids) {
				if (state.host == null || !id.equals(state.host)) {
					drop.add(id);
				}
			}
			if (drop.isEmpty()) {
				break;
			}
			UUID id = drop.get(rng.nextInt(drop.size()));
			ServerPlayerEntity player = state.server.getPlayerManager().getPlayer(id);
			ids.remove(id);
			if (player != null) {
				announce(Chat.yellow(player.getGameProfile().getName() + " снят с футбола: нужно чётное число игроков."));
				Chat.send(player, Chat.red("Вас сняли с МП, чтобы уравнять состав."));
				forceLeave(player);
			} else {
				state.members.remove(id);
			}
		}
		ids = new java.util.ArrayList<>(state.members.keySet());
		if (ids.size() < 2 || ids.size() % 2 != 0) {
			announce(Chat.red("Футбол отменён: нужно минимум двое и чётное число игроков."));
			stop(state.server, "отменено: нужно чётное число игроков (минимум 2)");
			return false;
		}
		java.util.Collections.shuffle(ids, rng);
		int half = ids.size() / 2;
		int a = 0;
		int b = 0;
		for (int i = 0; i < ids.size(); i++) {
			Member member = state.members.get(ids.get(i));
			if (member == null) {
				continue;
			}
			if (i < half) {
				member.team = 0;
				member.teamIndex = a++;
			} else {
				member.team = 1;
				member.teamIndex = b++;
			}
		}
		announce(Chat.gold("Команды: красные " + a + "  •  синие " + b
				+ (ids.size() == 2 ? " (каждый сам за себя)" : "")));
		return true;
	}

	private static void spawnFootball() {
		if (state == null || state.arena == null || state.server == null) {
			return;
		}
		discardFootball();
		ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
		if (world == null) {
			return;
		}
		net.minecraft.entity.mob.SlimeEntity ball = EntityType.SLIME.create(world);
		if (ball == null) {
			return;
		}
		ball.setSize(1, true);
		ball.setAiDisabled(true);
		ball.setPersistent();
		ball.setCustomName(Text.literal("Мяч").formatted(Formatting.WHITE, Formatting.BOLD));
		ball.setCustomNameVisible(true);
		ball.setInvulnerable(true);
		ball.setSilent(true);
		ball.setNoGravity(true);
		ball.noClip = true;
		ball.addCommandTag("ac_football");
		ball.refreshPositionAndAngles(state.arena.centerX(), state.arena.floorY() + 0.55, state.arena.centerZ(), 0, 0);
		ownSpawn(() -> world.spawnEntity(ball));
		state.ballId = ball.getUuid();
		state.ballRef = ball;
		state.ballVx = 0;
		state.ballVy = 0;
		state.ballVz = 0;
	}

	private static void discardFootball() {
		if (state == null) {
			return;
		}
		Entity entity = state.ballRef;
		if (entity == null && state.server != null && state.ballId != null) {
			ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
			if (world != null) {
				entity = world.getEntity(state.ballId);
			}
		}
		if (entity != null) {
			entity.discard();
		}
		state.ballId = null;
		state.ballRef = null;
		state.ballVx = 0;
		state.ballVy = 0;
		state.ballVz = 0;
	}

	private static void resetFootball() {
		if (state == null || state.arena == null) {
			return;
		}
		state.footballFreeze = 40;
		int i = 0;
		for (UUID id : state.members.keySet()) {
			ServerPlayerEntity player = state.server.getPlayerManager().getPlayer(id);
			if (player != null) {
				Member member = state.members.get(id);
				teleportArena(player, i++, null);
				player.setVelocity(0, 0, 0);
				player.velocityModified = true;
				if (member != null) {
					member.footTrack = false;
					member.kickCool = 0;
				}
			}
		}
		spawnFootball();
	}

	private static final double BALL_FRICTION = 0.94;
	private static final double BALL_MAX_SPEED = 1.0;
	private static final double KICK_MIN_BLOCKS = 8.0;
	private static final double KICK_MAX_BLOCKS = 14.0;

	/** Удар: мяч прокатится случайно на 8–14 блоков (без учёта отскоков от бортов). */
	private static void launchBall(double nx, double nz, boolean lift) {
		double blocks = java.util.concurrent.ThreadLocalRandom.current().nextDouble(KICK_MIN_BLOCKS, KICK_MAX_BLOCKS);
		double power = Math.min(BALL_MAX_SPEED, blocks * (1.0 - BALL_FRICTION) / BALL_FRICTION);
		state.ballVx = nx * power;
		state.ballVz = nz * power;
		state.ballVy = lift ? 0.22 : 0.08;
	}

	/** Удар рукой по мячу — пинок по направлению взгляда. */
	public static void kickByHit(ServerPlayerEntity player, Entity ball) {
		if (state == null || player == null || ball == null || !isFootball() || state.footballFreeze > 0
				|| !isFootballBall(ball)) {
			return;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive || member.kickCool > 0 || player.squaredDistanceTo(ball) > 3.5 * 3.5) {
			return;
		}
		double yaw = Math.toRadians(player.getYaw());
		double nx = -Math.sin(yaw);
		double nz = Math.cos(yaw);
		launchBall(nx, nz, player.getPitch() < -15f);
		member.kickCool = 14;
	}

	private static void tickFootball(MinecraftServer server) {
		if (state == null || state.arena == null) {
			return;
		}
		state.ticks--;
		if (state.footballFreeze > 0) {
			state.footballFreeze--;
		}
		ServerWorld world = SpawnService.resolveWorld(server, state.dimension);
		Entity raw = state.ballRef;
		if (raw == null || raw.isRemoved() || !(raw instanceof net.minecraft.entity.mob.SlimeEntity)) {
			raw = world == null || state.ballId == null ? null : world.getEntity(state.ballId);
		}
		if (!(raw instanceof net.minecraft.entity.mob.SlimeEntity ball) || !ball.isAlive()) {
			if (state.footballFreeze <= 0) {
				spawnFootball();
			}
			if (state.ticks <= 0) {
				finishFootball(server);
			}
			return;
		}
		state.ballRef = ball;
		ball.setAiDisabled(true);
		ball.setInvulnerable(true);
		ball.setNoGravity(true);
		ball.noClip = true;
		double floor = state.arena.floorY() + 0.55;
		if (state.footballFreeze > 0) {
			state.ballVx = 0;
			state.ballVy = 0;
			state.ballVz = 0;
			ball.setVelocity(0, 0, 0);
			ball.velocityModified = true;
			ball.refreshPositionAndAngles(state.arena.centerX(), floor, state.arena.centerZ(), 0, 0);
		} else {
			for (UUID id : state.members.keySet()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				Member member = state.members.get(id);
				if (player == null || member == null || !member.alive) {
					continue;
				}
				if (member.kickCool > 0) {
					member.kickCool--;
				}
				if (!member.footTrack) {
					member.footTrack = true;
					member.fx = player.getX();
					member.fy = player.getY();
					member.fz = player.getZ();
					continue;
				}
				double mx = player.getX() - member.fx;
				double mz = player.getZ() - member.fz;
				double my = player.getY() - member.fy;
				member.fx = player.getX();
				member.fy = player.getY();
				member.fz = player.getZ();
				double run = Math.sqrt(mx * mx + mz * mz);
				double footDy = Math.abs(player.getY() - ball.getY());
				double reach = (player.getWidth() + ball.getWidth()) * 0.5 + 0.35;
				if (member.kickCool > 0 || player.squaredDistanceTo(ball) > reach * reach
						|| run < 0.04 || footDy > 1.35) {
					continue;
				}
				double toBallX = ball.getX() - player.getX();
				double toBallZ = ball.getZ() - player.getZ();
				if (mx * toBallX + mz * toBallZ <= 0) {
					continue;
				}
				launchBall(mx / run, mz / run, my > 0.08);
				member.kickCool = 14;
			}
			state.ballVx *= BALL_FRICTION;
			state.ballVz *= BALL_FRICTION;
			state.ballVy = (state.ballVy - 0.045) * 0.96;
			double x = ball.getX() + state.ballVx;
			double y = ball.getY() + state.ballVy;
			double z = ball.getZ() + state.ballVz;
			double minX = state.arena.minX + 2.2;
			double maxX = state.arena.maxX - 1.2;
			double minZ = state.arena.minZ + 2.2;
			double maxZ = state.arena.maxZ - 1.2;
			if (x < minX) {
				x = minX;
				state.ballVx = Math.abs(state.ballVx) * 0.45;
			} else if (x > maxX) {
				x = maxX;
				state.ballVx = -Math.abs(state.ballVx) * 0.45;
			}
			if (z < minZ) {
				z = minZ;
				state.ballVz = Math.abs(state.ballVz) * 0.45;
			} else if (z > maxZ) {
				z = maxZ;
				state.ballVz = -Math.abs(state.ballVz) * 0.45;
			}
			if (y < floor) {
				y = floor;
				state.ballVy = Math.abs(state.ballVy) * 0.28;
				if (state.ballVy < 0.03) {
					state.ballVy = 0;
				}
			}
			double speed = Math.sqrt(state.ballVx * state.ballVx + state.ballVz * state.ballVz);
			if (speed > BALL_MAX_SPEED) {
				double s = BALL_MAX_SPEED / speed;
				state.ballVx *= s;
				state.ballVz *= s;
			}
			if (speed < 0.02 && Math.abs(state.ballVy) < 0.02) {
				state.ballVx = 0;
				state.ballVz = 0;
				state.ballVy = 0;
			}
			ball.setPosition(x, y, z);
			ball.setVelocity(state.ballVx, state.ballVy, state.ballVz);
			ball.velocityModified = true;
			int goal = state.arena.footballGoal(ball.getX(), ball.getY(), ball.getZ());
			if (goal >= 0) {
				if (goal == 0) {
					state.scoreB++;
					announce(Chat.gold("Гол синих!  " + state.scoreA + ":" + state.scoreB));
				} else {
					state.scoreA++;
					announce(Chat.gold("Гол красных!  " + state.scoreA + ":" + state.scoreB));
				}
				if (state.scoreA >= 3 || state.scoreB >= 3) {
					finishFootball(server);
					return;
				}
				resetFootball();
			}
		}
		if (state.ticks <= 0) {
			finishFootball(server);
		}
	}

	private static void finishFootball(MinecraftServer server) {
		if (state == null) {
			return;
		}
		discardFootball();
		if (state.scoreA == state.scoreB) {
			announce(Chat.gold("Футбол: ничья " + state.scoreA + ":" + state.scoreB + "."));
			state.result = "ничья " + state.scoreA + ":" + state.scoreB;
			finishCleanup(server, true);
			return;
		}
		int win = state.scoreA > state.scoreB ? 0 : 1;
		announce(Chat.gold("Победа " + (win == 0 ? "красных" : "синих") + "  "
				+ state.scoreA + ":" + state.scoreB));
		List<String> winners = new ArrayList<>();
		for (var entry : new java.util.ArrayList<>(state.members.entrySet())) {
			if (entry.getValue().team != win) {
				continue;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player != null) {
				winners.add(player.getGameProfile().getName());
				Economy.giveCurrency(player, state.reward, "награда за мероприятие");
				if (DataManager.get() != null) {
					PlayerRecord record = DataManager.get().progress(player);
					double from = record.power();
					record.addEventPower(state.powerTenths / 10.0);
					ProgressLog.power(record, from, record.power(), ProgressLog.EVENT);
					DataManager.get().markDirty();
					ClanTop.mpWin(server, player, null);
				}
				Achievements.onEventWin(player);
				title(player, "ПОБЕДА", state.scoreA + ":" + state.scoreB);
			}
		}
		if (DataManager.get() != null) {
			DataManager.get().saveIfDirty();
		}
		state.result = "победа " + (win == 0 ? "красных" : "синих") + " " + state.scoreA + ":" + state.scoreB
				+ (winners.isEmpty() ? "" : " (" + String.join(", ", winners) + ")");
		finishCleanup(server, true);
	}

	private static void paintFootballKit(ServerPlayerEntity player, int team) {
		int color = team == 0 ? 0xCC2222 : 0x2255CC;
		for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[] {
				net.minecraft.entity.EquipmentSlot.HEAD,
				net.minecraft.entity.EquipmentSlot.CHEST,
				net.minecraft.entity.EquipmentSlot.LEGS,
				net.minecraft.entity.EquipmentSlot.FEET
		}) {
			var stack = player.getEquippedStack(slot);
			if (stack.getItem() instanceof net.minecraft.item.DyeableItem dye) {
				dye.setColor(stack, color);
			}
		}
		player.playerScreenHandler.sendContentUpdates();
	}

	public static boolean ignorePvp(ServerPlayerEntity a, ServerPlayerEntity b) {
		if (!isFootball() || a == null || b == null) {
			return false;
		}
		Member ma = state.members.get(a.getUuid());
		Member mb = state.members.get(b.getUuid());
		return ma != null && mb != null && ma.team == mb.team && state.members.size() > 2;
	}

	public static int strikeKills() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return s == null ? 30 : Math.max(1, Math.min(500, s.eventStrikeKills));
	}

	public static int strikeMinutes() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return s == null ? 15 : Math.max(1, Math.min(120, s.eventStrikeMinutes));
	}

	private static void prepareStrike() {
		if (state == null) {
			return;
		}
		List<UUID> ids = new ArrayList<>(state.members.keySet());
		java.util.Collections.shuffle(ids);
		int a = 0;
		int b = 0;
		for (int i = 0; i < ids.size(); i++) {
			Member member = state.members.get(ids.get(i));
			if (member == null) {
				continue;
			}
			member.team = i % 2;
			member.teamIndex = member.team == 0 ? a++ : b++;
		}
		announce(Chat.gold("Команды страйкбола: красные " + a + "  •  синие " + b));
	}

	private static void strikeRespawn(ServerPlayerEntity player, Member member, net.minecraft.entity.damage.DamageSource source) {
		if (member.strikeRespawnTicks > 0) {
			return;
		}
		Entity raw = source == null ? null : source.getAttacker();
		if (!(raw instanceof ServerPlayerEntity) && player.getAttacker() instanceof ServerPlayerEntity last) {
			raw = last;
		}
		ServerPlayerEntity killer = raw instanceof ServerPlayerEntity sp && sp != player ? sp : null;
		Member km = killer == null ? null : state.members.get(killer.getUuid());
		String sub;
		if (km != null && km.alive && km.team != member.team) {
			if (km.team == 0) {
				state.scoreA++;
			} else {
				state.scoreB++;
			}
			killer.sendMessage(Text.literal("+1 убийство  •  красные " + state.scoreA + " : " + state.scoreB + " синие")
					.formatted(Formatting.GREEN), true);
			sub = "Вас убил " + killer.getGameProfile().getName();
		} else if (km != null && km.team == member.team) {
			sub = "Огонь по своим: " + killer.getGameProfile().getName();
		} else {
			sub = "Возврат на базу";
		}
		// onDeath выполняется внутри damage()/сетевой обработки Point Blank. Если здесь же очистить оружие,
		// клиент Point Blank продолжает анимацию старого предмета с уже удалённым GunClientState и падает
		// в FirstPersonWalkingAnimationHandler. Сначала безопасно завершаем использование, а замену предметов
		// выполняем спустя два серверных тика.
		player.stopUsingItem();
		reviveInPlace(player);
		player.setAttacker(null);
		member.strikeRespawnSubtitle = sub;
		member.strikeRespawnTicks = 2;
	}

	private static void finishStrikeRespawn(ServerPlayerEntity player, Member member) {
		if (state == null || state.phase != Phase.FIGHT || player == null || member == null || !member.alive) {
			return;
		}
		player.stopUsingItem();
		player.clearStatusEffects();
		giveKit(player);
		paintFootballKit(player, member.team);
		teleportArena(player, memberIndex(player.getUuid()), null);
		reviveInPlace(player);
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(5f);
		player.setAttacker(null);
		title(player, "УБИТ", member.strikeRespawnSubtitle == null ? "Возврат на базу" : member.strikeRespawnSubtitle);
		member.strikeRespawnSubtitle = "";
	}

	private static void tickStrike(MinecraftServer server) {
		if (state == null) {
			return;
		}
		state.ticks--;
		if (state.scoreA >= state.strikeGoal || state.scoreB >= state.strikeGoal) {
			finishStrike(server, -1);
			return;
		}
		if (!state.testSolo && state.ticks % 20 == 0) {
			int red = 0;
			int blue = 0;
			for (Member m : state.members.values()) {
				if (!m.alive) {
					continue;
				}
				if (m.team == 0) {
					red++;
				} else {
					blue++;
				}
			}
			if (red == 0 || blue == 0) {
				announce(Chat.yellow("В одной из команд не осталось игроков."));
				finishStrike(server, red == 0 && blue == 0 ? -1 : (red == 0 ? 1 : 0));
				return;
			}
		}
		if (state.ticks <= 0) {
			finishStrike(server, -1);
		}
	}

	/** forced: 0/1 — победа команды без учёта счёта, -1 — по счёту. */
	private static void finishStrike(MinecraftServer server, int forced) {
		if (state == null) {
			return;
		}
		int win = forced >= 0 ? forced : (state.scoreA == state.scoreB ? -1 : (state.scoreA > state.scoreB ? 0 : 1));
		String score = state.scoreA + ":" + state.scoreB;
		if (win < 0) {
			announce(Chat.gold("Страйкбол: ничья " + score + "."));
			state.result = "ничья " + score;
			finishCleanup(server, true);
			return;
		}
		announce(Chat.gold("Страйкбол: победа " + (win == 0 ? "красных" : "синих") + "  " + score));
		List<String> winners = new ArrayList<>();
		java.util.Set<String> once = ClanTop.newOnce();
		for (var entry : new ArrayList<>(state.members.entrySet())) {
			if (entry.getValue().team != win || !entry.getValue().alive) {
				continue;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			winners.add(player.getGameProfile().getName());
			Economy.giveCurrency(player, state.reward, "награда за мероприятие");
			if (DataManager.get() != null) {
				PlayerRecord record = DataManager.get().progress(player);
				double from = record.power();
				record.addEventPower(state.powerTenths / 10.0);
				ProgressLog.power(record, from, record.power(), ProgressLog.EVENT);
				DataManager.get().markDirty();
			}
			ClanTop.mpWin(server, player, once);
			Achievements.onEventWin(player);
			title(player, "ПОБЕДА", score);
		}
		if (DataManager.get() != null) {
			DataManager.get().saveIfDirty();
		}
		state.result = "победа " + (win == 0 ? "красных" : "синих") + " " + score
				+ (winners.isEmpty() ? "" : " (" + String.join(", ", winners) + ")");
		finishCleanup(server, true);
	}

	public static int huntGoal() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return s == null ? 15 : Math.max(1, Math.min(200, s.eventHuntKills));
	}

	public static int huntSeconds() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		return s == null ? 180 : Math.max(30, Math.min(900, s.eventHuntSeconds));
	}

	public static int huntMob() {
		ModSettings s = DataManager.get() == null ? null : DataManager.get().settings();
		int i = s == null ? 0 : s.eventHuntMob;
		return Math.max(0, Math.min(HUNT_MOBS.length - 1, i));
	}

	public static String huntMobLabel() {
		return HUNT_MOBS[huntMob()];
	}

	public static void onHostileKilled(ServerPlayerEntity player, Entity entity) {
		if (!fighting() || !isHunt() || player == null || entity == null) {
			return;
		}
		Member member = state.members.get(player.getUuid());
		if (member == null || !member.alive) {
			return;
		}
		if (!(entity instanceof HostileEntity)) {
			return;
		}
		if (state.arena != null && !state.arena.contains(entity.getBlockPos())) {
			return;
		}
		state.huntKills++;
		state.huntMobs.remove(entity.getUuid());
		if (state.huntKills >= state.huntGoal) {
			declareTeamWin(player.getServer());
		}
	}

	public static boolean primeTntFromArrow(PersistentProjectileEntity arrow, BlockPos pos) {
		if (arrow == null || pos == null || arrow.getWorld() == null || arrow.getWorld().isClient) {
			return false;
		}
		if (!(arrow.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		if (!fighting() || !isDuel() || state.arena == null || !state.arena.contains(pos)) {
			return false;
		}
		if (!world.getBlockState(pos).isOf(Blocks.TNT)) {
			return false;
		}
		LivingEntity owner = arrow.getOwner() instanceof LivingEntity living ? living : null;
		if (!arrow.isRemoved()) {
			arrow.discard();
		}
		world.getServer().execute(() -> {
			if (!world.getBlockState(pos).isOf(Blocks.TNT)) {
				return;
			}
			world.removeBlock(pos, false);
			TntEntity tnt = new TntEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, owner);
			tnt.setFuse(40);
			world.spawnEntity(tnt);
		});
		return true;
	}

	private static void tickHunt(MinecraftServer server) {
		if (!state.testSolo) {
			state.ticks--;
			if (state.ticks <= 0) {
				declareLose(server, "время вышло");
				return;
			}
		}
		if (state.ticks % 40 == 0) {
			spawnHuntMob();
		}
		if (state.arena != null) {
			state.huntMobs.removeIf(id -> {
				ServerWorld world = SpawnService.resolveWorld(server, state.dimension);
				return world == null || world.getEntity(id) == null || !world.getEntity(id).isAlive();
			});
		}
	}

	private static void checkMeatWinner(MinecraftServer server) {
		if (state == null) {
			return;
		}
		List<UUID> alive = new ArrayList<>();
		for (var entry : state.members.entrySet()) {
			if (entry.getValue().alive) {
				alive.add(entry.getKey());
			}
		}
		if (alive.size() > 1) {
			return;
		}
		if (alive.size() == 1) {
			if (state.fightAge < 30 * 20) {
				return;
			}
			ServerPlayerEntity winner = server.getPlayerManager().getPlayer(alive.get(0));
			if (winner != null) {
				declareWinner(server, winner);
			}
			return;
		}
		if (state.lastDeath != null && state.lastDeathAge >= 30 * 20) {
			ServerPlayerEntity winner = server.getPlayerManager().getPlayer(state.lastDeath);
			if (winner != null) {
				declareWinner(server, winner);
				return;
			}
		}
		if (state.fightAge >= 30 * 20) {
			announce(Chat.gold("Мясорубка завершена без победителя: никто не продержался 30 секунд."));
			state.result = "без победителя (никто не продержался 30 секунд)";
			finishCleanup(server, false);
		}
	}

	private static void spawnMeatMobs() {
		if (state == null || state.arena == null || state.server == null) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
		if (world == null) {
			return;
		}
		EntityType<?>[] types = { EntityType.SPIDER, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.CREEPER };
		for (int i = 0; i < 50; i++) {
			int x = state.arena.minX + 2 + world.random.nextInt(Math.max(1, state.arena.maxX - state.arena.minX - 3));
			int z = state.arena.minZ + 2 + world.random.nextInt(Math.max(1, state.arena.maxZ - state.arena.minZ - 3));
			BlockPos pos = new BlockPos(x, (int) state.arena.floorY(), z);
			EntityType<?> type = types[i % types.length];
			Entity spawned = ownSpawn(() -> type.spawn(world, pos, SpawnReason.EVENT));
			if (spawned instanceof MobEntity mob) {
				mob.setPersistent();
				state.huntMobs.add(mob.getUuid());
			}
		}
		tickMeatMobs(state.server);
	}

	private static void tickMeatMobs(MinecraftServer server) {
		if (state == null || state.arena == null || server == null) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(server, state.dimension);
		if (world == null) {
			return;
		}
		List<ServerPlayerEntity> prey = new ArrayList<>();
		for (UUID id : state.members.keySet()) {
			Member member = state.members.get(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (member != null && member.alive && player != null && state.arena.contains(player.getBlockPos())) {
				prey.add(player);
			}
		}
		state.huntMobs.removeIf(id -> {
			Entity entity = world.getEntity(id);
			return entity == null || !entity.isAlive() || entity.isRemoved();
		});
		int n = 0;
		for (UUID id : new ArrayList<>(state.huntMobs)) {
			Entity entity = world.getEntity(id);
			if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
				continue;
			}
			state.arena.keepMobInside(mob);
			if (prey.isEmpty()) {
				mob.setTarget(null);
				continue;
			}
			if (state.fightAge % 20 == 0 || mob.getTarget() == null || !mob.getTarget().isAlive()) {
				mob.setTarget(prey.get(n % prey.size()));
			}
			n++;
		}
	}

	private static void spawnHuntMob() {
		if (state == null || state.arena == null || state.server == null) {
			return;
		}
		if (state.huntMobs.size() >= 8) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
		if (world == null) {
			return;
		}
		EntityType<?> type = switch (huntMob()) {
			case 1 -> EntityType.SKELETON;
			case 2 -> EntityType.SPIDER;
			case 3 -> EntityType.CREEPER;
			case 4 -> EntityType.DROWNED;
			default -> EntityType.ZOMBIE;
		};
		int cx = (int) Math.floor(state.arena.centerX());
		int cz = (int) Math.floor(state.arena.centerZ());
		int x = cx + world.random.nextInt(9) - 4;
		int z = cz + world.random.nextInt(9) - 4;
		x = Math.max(state.arena.minX + 2, Math.min(state.arena.maxX - 2, x));
		z = Math.max(state.arena.minZ + 2, Math.min(state.arena.maxZ - 2, z));
		BlockPos pos = new BlockPos(x, (int) state.arena.floorY(), z);
		Entity spawned = ownSpawn(() -> type.spawn(world, pos, SpawnReason.EVENT));
		if (spawned instanceof MobEntity mob) {
			mob.setPersistent();
			state.huntMobs.add(mob.getUuid());
			ServerPlayerEntity target = null;
			for (UUID id : state.members.keySet()) {
				ServerPlayerEntity p = state.server.getPlayerManager().getPlayer(id);
				if (p != null && state.members.get(id).alive) {
					target = p;
					break;
				}
			}
			if (target != null) {
				mob.setTarget(target);
			}
		}
	}

	private static void clearHuntMobs() {
		if (state == null || state.server == null) {
			return;
		}
		ServerWorld world = SpawnService.resolveWorld(state.server, state.dimension);
		if (world == null) {
			return;
		}
		for (UUID id : new ArrayList<>(state.huntMobs)) {
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
		state.huntMobs.clear();
	}

	private static void declareTeamWin(MinecraftServer server) {
		if (state == null) {
			return;
		}
		java.util.Set<String> once = ClanTop.newOnce();
		List<String> winners = new ArrayList<>();
		for (UUID id : new ArrayList<>(state.members.keySet())) {
			Member member = state.members.get(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || member == null || !member.alive) {
				continue;
			}
			winners.add(player.getGameProfile().getName());
			restore(player, member);
			Economy.giveCurrency(player, state.reward, "награда за мероприятие");
			if (DataManager.get() != null) {
				PlayerRecord record = DataManager.get().progress(player);
				double from = record.power();
				record.addEventPower(state.powerTenths / 10.0);
				ProgressLog.power(record, from, record.power(), ProgressLog.EVENT);
				DataManager.get().markDirty();
				DataManager.get().saveIfDirty();
				BalanceBoard.refresh(player, true);
				title(player, "ПОБЕДА", "Сила " + String.format(java.util.Locale.US, "%.1f", record.power()));
				announce(Chat.gold("Команда победила в «" + state.name + "»: "
						+ player.getGameProfile().getName() + "  +" + Money.wc(state.reward)));
			}
			Achievements.onEventWin(player);
			ClanTop.mpWin(server, player, once);
		}
		state.result = winners.isEmpty() ? "без победителя" : "победила команда: " + String.join(", ", winners);
		finishCleanup(server, false);
	}

	private static void declareLose(MinecraftServer server, String reason) {
		if (state == null) {
			return;
		}
		announce(Chat.red("Мероприятие «" + state.name + "» проиграно: " + reason + "."));
		state.result = "без победителя (проиграно: " + reason + ")";
		finishCleanup(server, true);
	}

	private static void announce(net.minecraft.text.Text msg) {
		if (state == null || state.server == null || msg == null) {
			return;
		}
		if (state.privateEvent) {
			ServerPlayerEntity host = state.host == null ? null : state.server.getPlayerManager().getPlayer(state.host);
			if (host != null) {
				Chat.send(host, msg);
			}
			return;
		}
		if (!state.restrictedMembers.isEmpty()) {
			for (UUID id : state.restrictedMembers) {
				ServerPlayerEntity player = state.server.getPlayerManager().getPlayer(id);
				if (player != null) Chat.send(player, msg);
			}
			return;
		}
		Chat.broadcast(state.server, msg);
	}
}
