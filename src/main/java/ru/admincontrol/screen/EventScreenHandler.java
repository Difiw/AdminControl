package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.ScreenNav;

public class EventScreenHandler extends ScreenHandler {
	public static final int TYPE_BASE = 100;
	public static final int TYPE = 0;
	public static final int PLAYERS = 1;
	public static final int REWARD = 2;
	public static final int RUNNING = 3;
	public static final int MEMBERS = 4;
	public static final int PHASE = 5;
	public static final int POWER = 6;
	public static final int ADMIN_ONLY = 7;
	public static final int BUMPERS = 8;
	public static final int PRIZE1 = 9;
	public static final int PRIZE2 = 10;
	public static final int PRIZE3 = 11;
	public static final int LUCK = 12;
	public static final int MANAGER = 13;
	public static final int WATCHING = 14;
	public static final int SEL_LO = 15;
	public static final int SEL_HI = 16;
	public static final int BLOCKED = 17;
	public static final int BASE = 18;
	public static final int BLOCKED_HI = 19;

	public static final int PAGE_MAIN = 0;
	public static final int PAGE_MEMBERS = 1;
	public static final int PAGE_PRIZE = 2;
	public static final int OPEN_MEMBERS = 25;
	public static final int OPEN_PRIZE = 26;
	public static final int RESTART = 27;
	public static final int WATCH = 28;
	public static final int AWARD = 29;
	public static final int KICK_BASE = 30;
	public static final int SEL_BASE = 62;
	public static final int ROSTER_MAX = 32;

	private final PropertyDelegate props = new ArrayPropertyDelegate(20);
	private final ServerPlayerEntity viewer;
	private final int page;
	private final java.util.List<java.util.UUID> roster = new java.util.ArrayList<>();
	private final java.util.List<String> rosterNames = new java.util.ArrayList<>();
	private final java.util.List<Boolean> rosterAlive = new java.util.ArrayList<>();
	private final java.util.List<Boolean> rosterAwarded = new java.util.ArrayList<>();
	/** Свои МП из /devent (название, описание, правила) — идут в списке выбора после стандартных. */
	private final java.util.List<String> customNames = new java.util.ArrayList<>();
	private final java.util.List<String> customDescs = new java.util.ArrayList<>();
	private final java.util.List<Integer> customBases = new java.util.ArrayList<>();
	private final java.util.List<Integer> order = new java.util.ArrayList<>();
	private int selection;
	/** Индекс выбора в EventCatalog (стандартные, затем свои МП). */
	private int typeIndex;
	private int players = 8;
	private int reward = 16;
	private int powerTenths = 1;
	private boolean adminOnly;
	private boolean hostPlays;
	private boolean raceBumpers = true;
	private int prize1 = 30;
	private int prize2 = 20;
	private int prize3 = 10;
	private int raceLuck = 0;

	public static EventScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int type = buf.readVarInt();
		int maxPlayers = buf.readVarInt();
		int reward = buf.readVarInt();
		boolean running = buf.readBoolean();
		int members = buf.readVarInt();
		int phase = buf.readVarInt();
		int power = buf.readVarInt();
		boolean only = buf.readBoolean();
		boolean bumpers = buf.readBoolean();
		int p1 = buf.readVarInt();
		int p2 = buf.readVarInt();
		int p3 = buf.readVarInt();
		int luck = buf.readVarInt();
		boolean hostPlays = buf.readBoolean();
		int page = buf.readVarInt();
		EventScreenHandler handler = new EventScreenHandler(syncId, only, hostPlays, null, page);
		int count = Math.min(ROSTER_MAX, buf.readVarInt());
		for (int i = 0; i < count; i++) {
			handler.rosterNames.add(buf.readString(64));
			handler.rosterAlive.add(buf.readBoolean());
			handler.rosterAwarded.add(buf.readBoolean());
		}
		int customs = Math.min(ru.admincontrol.util.EventCatalog.MAX_CUSTOM, buf.readVarInt());
		for (int i = 0; i < customs; i++) {
			handler.customNames.add(buf.readString(ru.admincontrol.util.EventCatalog.NAME_MAX * 4));
			handler.customDescs.add(buf.readString(DESC_WIRE));
			handler.customBases.add(buf.readVarInt());
		}
		int ordered = Math.min(ru.admincontrol.util.EventCatalog.MAX_CHOICES, buf.readVarInt());
		for (int i = 0; i < ordered; i++) {
			handler.order.add(buf.readVarInt());
		}
		handler.props.set(TYPE, type);
		handler.props.set(BASE, handler.choiceBase(type));
		handler.props.set(PLAYERS, maxPlayers);
		handler.props.set(REWARD, reward);
		handler.props.set(RUNNING, running ? 1 : 0);
		handler.props.set(MEMBERS, members);
		handler.props.set(PHASE, phase);
		handler.props.set(POWER, power);
		handler.props.set(ADMIN_ONLY, only ? 1 : 0);
		handler.raceBumpers = bumpers;
		handler.prize1 = p1;
		handler.prize2 = p2;
		handler.prize3 = p3;
		handler.raceLuck = luck;
		handler.props.set(BUMPERS, handler.raceBumpers ? 1 : 0);
		handler.props.set(PRIZE1, handler.prize1);
		handler.props.set(PRIZE2, handler.prize2);
		handler.props.set(PRIZE3, handler.prize3);
		handler.props.set(LUCK, handler.raceLuck);
		return handler;
	}

	/** Состав для страниц кика и приза: пишется в данные открытия экрана вслед за настройками. */
	public static void writeRoster(PacketByteBuf buf) {
		java.util.List<java.util.UUID> ids = EventService.roster();
		buf.writeVarInt(ids.size());
		for (java.util.UUID id : ids) {
			String name = EventService.memberName(id);
			buf.writeString(name.length() > 64 ? name.substring(0, 64) : name, 64);
			buf.writeBoolean(EventService.memberAlive(id));
			buf.writeBoolean(EventService.awarded(id));
		}
	}

	private static final int DESC_WIRE = 4096;

	/** Свои МП для списка выбора: пишутся в данные открытия вслед за составом. */
	public static void writeCatalog(PacketByteBuf buf) {
		int builtIn = ru.admincontrol.util.EventCatalog.builtIn();
		int n = ru.admincontrol.util.EventCatalog.count() - builtIn;
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			int choice = builtIn + i;
			buf.writeString(ru.admincontrol.util.EventCatalog.name(choice), ru.admincontrol.util.EventCatalog.NAME_MAX * 4);
			String desc = ru.admincontrol.util.EventCatalog.description(choice);
			buf.writeString(desc.length() > 1000 ? desc.substring(0, 1000) : desc, DESC_WIRE);
			buf.writeVarInt(ru.admincontrol.util.EventCatalog.base(choice));
		}
		java.util.List<Integer> order = ru.admincontrol.util.EventCatalog.order();
		buf.writeVarInt(order.size());
		for (int choice : order) {
			buf.writeVarInt(choice);
		}
	}

	private void loadCatalog() {
		int builtIn = ru.admincontrol.util.EventCatalog.builtIn();
		for (int choice = builtIn; choice < ru.admincontrol.util.EventCatalog.count(); choice++) {
			this.customNames.add(ru.admincontrol.util.EventCatalog.name(choice));
			this.customDescs.add(ru.admincontrol.util.EventCatalog.description(choice));
			this.customBases.add(ru.admincontrol.util.EventCatalog.base(choice));
		}
		this.order.addAll(ru.admincontrol.util.EventCatalog.order());
	}

	/** Сколько МП в списке выбора. */
	public int choiceCount() {
		return EventService.TYPES.length + this.customNames.size();
	}

	/** МП на позиции pos в списке кнопок (порядок из /devent); без порядка — по номеру. */
	public int choiceAt(int pos) {
		if (this.order.size() == choiceCount() && pos >= 0 && pos < this.order.size()) {
			return this.order.get(pos);
		}
		return pos;
	}

	public String choiceName(int choice) {
		if (choice >= 0 && choice < EventService.TYPES.length) {
			return EventService.TYPES[choice];
		}
		int i = choice - EventService.TYPES.length;
		return i >= 0 && i < this.customNames.size() ? this.customNames.get(i) : "—";
	}

	public String choiceDescription(int choice) {
		if (choice >= 0 && choice < EventService.TYPES.length) {
			return EventService.description(choice);
		}
		int i = choice - EventService.TYPES.length;
		return i >= 0 && i < this.customDescs.size() ? this.customDescs.get(i) : "";
	}

	/** Правила МП: индекс стандартного МП, механику которого оно использует. */
	public int choiceBase(int choice) {
		if (choice >= 0 && choice < EventService.TYPES.length) {
			return choice;
		}
		int i = choice - EventService.TYPES.length;
		return i >= 0 && i < this.customBases.size() ? this.customBases.get(i) : 0;
	}

	public boolean customChoice(int choice) {
		return choice >= EventService.TYPES.length && choice < choiceCount();
	}

	/** Ключ настроек МП (как в /devent); только на сервере. */
	private String choiceKey() {
		return ru.admincontrol.util.EventCatalog.key(this.typeIndex);
	}

	public EventScreenHandler(int syncId) {
		this(syncId, false, false);
	}

	public EventScreenHandler(int syncId, boolean adminOnly) {
		this(syncId, adminOnly, false);
	}

	public EventScreenHandler(int syncId, boolean adminOnly, boolean hostPlays) {
		this(syncId, adminOnly, hostPlays, null, PAGE_MAIN);
	}

	public EventScreenHandler(int syncId, boolean adminOnly, boolean hostPlays, ServerPlayerEntity viewer, int page) {
		super(ModScreenHandlers.EVENT, syncId);
		this.adminOnly = adminOnly;
		this.hostPlays = hostPlays;
		this.viewer = viewer;
		this.page = page;
		if (viewer != null) {
			this.roster.addAll(EventService.roster());
			loadCatalog();
		}
		applyDefaults();
		this.addProperties(this.props);
		syncFromState();
	}

	private void syncFromState() {
		if (EventService.active() && EventService.current() != null) {
			EventService.State st = EventService.current();
			this.props.set(TYPE, st.choice);
			this.props.set(BASE, st.typeIndex);
			this.props.set(PLAYERS, st.maxPlayers);
			this.props.set(REWARD, st.reward);
			this.props.set(RUNNING, 1);
			this.props.set(MEMBERS, EventService.memberCount());
			this.props.set(PHASE, st.phase.ordinal());
			this.props.set(POWER, st.powerTenths);
			this.props.set(ADMIN_ONLY, st.privateEvent || this.adminOnly ? 1 : 0);
			this.props.set(BUMPERS, st.raceBumpers ? 1 : 0);
			this.props.set(PRIZE1, st.prize1);
			this.props.set(PRIZE2, st.prize2);
			this.props.set(PRIZE3, st.prize3);
			this.props.set(LUCK, st.raceLuckTenths);
		} else {
			this.props.set(TYPE, this.typeIndex);
			this.props.set(BASE, choiceBase(this.typeIndex));
			this.props.set(PLAYERS, this.players);
			this.props.set(REWARD, this.reward);
			this.props.set(RUNNING, 0);
			this.props.set(MEMBERS, 0);
			this.props.set(PHASE, 0);
			this.props.set(POWER, this.powerTenths);
			this.props.set(ADMIN_ONLY, this.adminOnly ? 1 : 0);
			this.props.set(BUMPERS, this.raceBumpers ? 1 : 0);
			this.props.set(PRIZE1, this.prize1);
			this.props.set(PRIZE2, this.prize2);
			this.props.set(PRIZE3, this.prize3);
			this.props.set(LUCK, this.raceLuck);
		}
		if (this.viewer != null) {
			this.props.set(MANAGER, EventService.canManage(this.viewer) ? 1 : 0);
			this.props.set(WATCHING, EventService.watching(this.viewer) ? 1 : 0);
			this.props.set(SEL_LO, this.selection & 0xFFFF);
			this.props.set(SEL_HI, (this.selection >>> 16) & 0xFFFF);
			int blocked = EventService.blockedMask();
			this.props.set(BLOCKED, blocked & 0xFFFF);
			this.props.set(BLOCKED_HI, (blocked >>> 16) & 0xFFFF);
		}
	}

	public int page() {
		return this.page;
	}

	public int rosterSize() {
		return this.rosterNames.size();
	}

	public String rosterName(int i) {
		return i >= 0 && i < this.rosterNames.size() ? this.rosterNames.get(i) : "";
	}

	public boolean rosterAlive(int i) {
		return i >= 0 && i < this.rosterAlive.size() && this.rosterAlive.get(i);
	}

	public boolean rosterAwarded(int i) {
		return i >= 0 && i < this.rosterAwarded.size() && this.rosterAwarded.get(i);
	}

	public boolean manager() {
		return this.props.get(MANAGER) != 0;
	}

	public boolean watching() {
		return this.props.get(WATCHING) != 0;
	}

	public int selectionMask() {
		return (this.props.get(SEL_LO) & 0xFFFF) | ((this.props.get(SEL_HI) & 0xFFFF) << 16);
	}

	public int blockedMask() {
		return (this.props.get(BLOCKED) & 0xFFFF) | ((this.props.get(BLOCKED_HI) & 0xFFFF) << 16);
	}

	public boolean selected(int i) {
		return i >= 0 && i < ROSTER_MAX && (selectionMask() >>> i & 1) != 0;
	}

	public int selectedCount() {
		int n = 0;
		for (int i = 0; i < rosterSize(); i++) {
			if (selected(i)) {
				n++;
			}
		}
		return n;
	}

	public boolean blocked(int type) {
		return type >= 0 && type < 32 && (blockedMask() >>> type & 1) != 0;
	}

	/** Сумма ручного приза из /event: у гонки — приз за 1 место, у остальных — награда. */
	public int prizeAmount() {
		return race() ? prize1() : reward();
	}

	public boolean adminOnly() {
		return this.props.get(ADMIN_ONLY) != 0;
	}

	public boolean running() {
		return this.props.get(RUNNING) != 0;
	}

	public int typeIndex() {
		return this.props.get(TYPE);
	}

	public int players() {
		return this.props.get(PLAYERS);
	}

	public int reward() {
		return this.props.get(REWARD);
	}

	public int powerTenths() {
		return this.props.get(POWER);
	}

	public String powerLabel() {
		return String.format(java.util.Locale.US, "+%.1f", powerTenths() / 10.0);
	}

	public int members() {
		return this.props.get(MEMBERS);
	}

	public boolean raceBumpers() {
		return this.props.get(BUMPERS) != 0;
	}

	public int prize1() {
		return this.props.get(PRIZE1);
	}

	public int prize2() {
		return this.props.get(PRIZE2);
	}

	public int prize3() {
		return this.props.get(PRIZE3);
	}

	public int raceLuck() {
		return this.props.get(LUCK);
	}

	public String luckLabel() {
		return String.format(java.util.Locale.US, "+%.1f", raceLuck() / 10.0);
	}

	/** Правила выбранного или идущего МП (индекс стандартного МП). */
	public int base() {
		return this.props.get(BASE);
	}

	public boolean race() {
		return base() == 6;
	}

	public boolean meat() {
		return base() == 7;
	}

	public String phase() {
		int p = this.props.get(PHASE);
		return switch (p) {
			case 1 -> "отсчёт";
			case 2 -> "бой";
			default -> this.running() ? "регистрация" : "нет";
		};
	}

	public boolean registering() {
		return running() && this.props.get(PHASE) == 0;
	}

	public String typeName() {
		return choiceName(Math.max(0, Math.min(choiceCount() - 1, typeIndex())));
	}

	public String description() {
		return choiceDescription(Math.max(0, Math.min(choiceCount() - 1, typeIndex())));
	}

	@Override
	public void sendContentUpdates() {
		syncFromState();
		super.sendContentUpdates();
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin)) {
			return false;
		}
		if (id == 10) {
			if (EventService.active()) {
				if (!EventService.canManage(admin)) {
					Chat.send(admin, Chat.red("Закончить МП может администратор, который его начал, или куратор администрации и выше."));
					return true;
				}
				EventService.stop(admin.getServer(), "остановил администратор " + admin.getGameProfile().getName());
			}
			admin.closeHandledScreen();
			return true;
		}
		if (EventService.active() && id >= 11 && id < SEL_BASE + ROSTER_MAX) {
			return manage(admin, id);
		}
		if (EventService.active()) {
			Chat.send(admin, Chat.red("Мероприятие уже запущено."));
			return true;
		}
		if (id >= TYPE_BASE && id < TYPE_BASE + choiceCount()) {
			this.typeIndex = id - TYPE_BASE;
			applyDefaults();
			syncFromState();
			return true;
		}
		switch (id) {
			case 0 -> {
				this.typeIndex = Math.max(0, this.typeIndex - 1);
				applyDefaults();
			}
			case 1 -> {
				this.typeIndex = Math.min(choiceCount() - 1, this.typeIndex + 1);
				applyDefaults();
			}
			case 2 -> {
				int step = choiceBase(this.typeIndex) == 11 ? 2 : 1;
				this.players = Math.max(minPlayers(), this.players - step);
				clampPlayers();
			}
			case 3 -> {
				int step = choiceBase(this.typeIndex) == 11 ? 2 : 1;
				this.players = Math.min(maxPlayers(), this.players + step);
				clampPlayers();
			}
			case 4 -> this.reward = Math.max(0, this.reward - 1);
			case 5 -> this.reward = Math.min(10000, this.reward + 1);
			case 6 -> this.reward = Math.max(0, this.reward - 10);
			case 7 -> this.reward = Math.min(10000, this.reward + 10);
			case 8 -> {
				if (ru.admincontrol.util.TrainingService.active(admin)) {
					Chat.send(admin, Chat.red("Сначала выйдите с тренировки (/off)."));
					return true;
				}
				EventService.start(admin, this.typeIndex, this.players, this.reward, this.powerTenths, this.adminOnly,
						this.raceBumpers, this.prize1, this.prize2, this.prize3, this.raceLuck, this.hostPlays);
				admin.closeHandledScreen();
			}
			case 12 -> this.powerTenths = Math.max(0, this.powerTenths - 1);
			case 13 -> this.powerTenths = Math.min(50, this.powerTenths + 1);
			case 14 -> this.powerTenths = Math.max(0, this.powerTenths - 5);
			case 15 -> this.powerTenths = Math.min(50, this.powerTenths + 5);
			case 16 -> this.raceBumpers = !this.raceBumpers;
			case 17 -> this.prize1 = Math.max(0, this.prize1 - 5);
			case 18 -> this.prize1 = Math.min(10000, this.prize1 + 5);
			case 19 -> this.prize2 = Math.max(0, this.prize2 - 5);
			case 20 -> this.prize2 = Math.min(10000, this.prize2 + 5);
			case 21 -> this.prize3 = Math.max(0, this.prize3 - 5);
			case 22 -> this.prize3 = Math.min(10000, this.prize3 + 5);
			case 23 -> this.raceLuck = Math.max(0, this.raceLuck - 1);
			case 24 -> this.raceLuck = Math.min(50, this.raceLuck + 1);
			default -> {
				return false;
			}
		}
		syncFromState();
		return true;
	}

	private boolean manage(ServerPlayerEntity admin, int id) {
		if (id == 11) {
			if (!EventService.canManage(admin)) {
				Chat.send(admin, Chat.red("Начать МП досрочно может администратор, который его начал, или куратор администрации и выше."));
				return true;
			}
			EventService.tryBegin(admin);
			return true;
		}
		if (id < OPEN_MEMBERS) {
			Chat.send(admin, Chat.red("Мероприятие уже запущено."));
			return true;
		}
		if (!EventService.canManage(admin)) {
			Chat.send(admin, Chat.red("Управлять МП может администратор, который его начал, или куратор администрации и выше."));
			return true;
		}
		String error = null;
		switch (id) {
			case OPEN_MEMBERS -> {
				ScreenNav.replace(admin, new EventFactory(this.adminOnly, this.hostPlays, PAGE_MEMBERS));
				return true;
			}
			case OPEN_PRIZE -> {
				ScreenNav.replace(admin, new EventFactory(this.adminOnly, this.hostPlays, PAGE_PRIZE));
				return true;
			}
			case RESTART -> {
				error = EventService.restart(admin);
				if (error == null) {
					admin.closeHandledScreen();
				}
			}
			case WATCH -> {
				error = EventService.watching(admin) ? EventService.unwatch(admin) : EventService.watch(admin);
				if (error == null) {
					admin.closeHandledScreen();
				}
			}
			case AWARD -> {
				java.util.List<java.util.UUID> picked = new java.util.ArrayList<>();
				for (int i = 0; i < this.roster.size(); i++) {
					if ((this.selection >>> i & 1) != 0) {
						picked.add(this.roster.get(i));
					}
				}
				error = EventService.award(admin, picked);
				if (EventService.active()) {
					ScreenNav.replace(admin, new EventFactory(this.adminOnly, this.hostPlays, PAGE_PRIZE));
				}
			}
			default -> {
				if (id >= KICK_BASE && id < KICK_BASE + ROSTER_MAX) {
					int i = id - KICK_BASE;
					if (i >= this.roster.size()) {
						return true;
					}
					error = EventService.kick(admin, EventService.memberName(this.roster.get(i)));
					if (EventService.active()) {
						ScreenNav.replace(admin, new EventFactory(this.adminOnly, this.hostPlays, PAGE_MEMBERS));
					}
				} else if (id >= SEL_BASE && id < SEL_BASE + ROSTER_MAX) {
					int i = id - SEL_BASE;
					if (i < this.roster.size() && !EventService.awarded(this.roster.get(i))) {
						this.selection ^= 1 << i;
						syncFromState();
					}
					return true;
				} else {
					return false;
				}
			}
		}
		if (error != null) {
			Chat.send(admin, Chat.red(error));
		}
		return true;
	}

	/** Настройки выбранного МП по умолчанию (/devent). */
	private void applyDefaults() {
		int base = choiceBase(this.typeIndex);
		ru.admincontrol.data.EventDefaults d = this.viewer != null
				? ru.admincontrol.data.EventDefaults.of(choiceKey(), base)
				: ru.admincontrol.data.EventDefaults.builtIn(base);
		this.players = d.players;
		this.reward = d.reward;
		this.powerTenths = d.power;
		this.raceBumpers = d.bumpers;
		this.prize1 = d.prize1;
		this.prize2 = d.prize2;
		this.prize3 = d.prize3;
		this.raceLuck = d.luck;
		clampPlayers();
	}

	private boolean duoType() {
		return choiceBase(this.typeIndex) == 3;
	}

	private int minPlayers() {
		if (this.adminOnly) {
			return 1;
		}
		return duoType() ? 2 : EventService.MIN_PLAYERS;
	}

	private int maxPlayers() {
		if (this.adminOnly) {
			return 1;
		}
		return duoType() ? 2 : 32;
	}

	private void clampPlayers() {
		this.players = Math.max(minPlayers(), Math.min(maxPlayers(), this.players));
		if (!this.adminOnly && choiceBase(this.typeIndex) == 11 && (this.players % 2) != 0) {
			this.players = Math.max(minPlayers(), this.players - 1);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
