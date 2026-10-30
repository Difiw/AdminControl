package ru.admincontrol.screen;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.CustomEvent;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.EventDefaults;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.EventCatalog;
import ru.admincontrol.util.EventMapEditor;
import ru.admincontrol.util.EventMaps;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.ItemLabels;
import ru.admincontrol.util.ScreenNav;
import ru.admincontrol.util.TrainingConfig;

import java.util.ArrayList;
import java.util.List;

/** /devent: настройки МП по умолчанию, набор, ломаемые блоки, свои МП и правка карты. */
public class DevEventScreenHandler extends ScreenHandler {
	/** 25 настроек и порядок кнопок МП: ORDER_BASE + позиция = индекс выбора (-1 — пусто). */
	public static final int PROPS = 25 + EventCatalog.MAX_CHOICES;
	public static final int TYPE = 0;
	public static final int PLAYERS = 1;
	public static final int REWARD = 2;
	public static final int POWER = 3;
	public static final int BUMPERS = 4;
	public static final int PRIZE1 = 5;
	public static final int PRIZE2 = 6;
	public static final int PRIZE3 = 7;
	public static final int LUCK = 8;
	public static final int HUNT_KILLS = 9;
	public static final int HUNT_SECONDS = 10;
	public static final int HUNT_MOB = 11;
	public static final int STRIKE_KILLS = 12;
	public static final int STRIKE_MINUTES = 13;
	public static final int MAP_SAVED = 14;
	public static final int EDIT = 15;
	public static final int EDIT_TYPE = 16;
	public static final int IS_CUSTOM = 17;
	public static final int BASE = 18;
	public static final int DRAFT = 19;
	public static final int PAGE = 20;
	public static final int BREAK_COUNT = 21;
	public static final int HELPERS = 22;
	public static final int CUSTOM_COUNT = 23;
	public static final int ORDER_SEL = 24;
	public static final int ORDER_BASE = 25;

	public static final int EDIT_NONE = 0;
	public static final int EDIT_MINE = 1;
	public static final int EDIT_OTHER = 2;

	public static final int PAGE_SETTINGS = 0;
	public static final int PAGE_BREAK = 1;
	/** Порядок кнопок выбора МП — открывается из общего списка. */
	public static final int PAGE_ORDER = 2;

	public static final int TYPE_BASE = 100;
	public static final int BACK = 0;
	public static final int PLAYERS_M = 2;
	public static final int PLAYERS_P = 3;
	public static final int REWARD_M = 4;
	public static final int REWARD_P = 5;
	public static final int REWARD_M10 = 6;
	public static final int REWARD_P10 = 7;
	public static final int POWER_M = 8;
	public static final int POWER_P = 9;
	public static final int POWER_M5 = 10;
	public static final int POWER_P5 = 11;
	public static final int BUMPERS_TOGGLE = 12;
	public static final int PRIZE1_M = 13;
	public static final int PRIZE1_P = 14;
	public static final int PRIZE2_M = 15;
	public static final int PRIZE2_P = 16;
	public static final int PRIZE3_M = 17;
	public static final int PRIZE3_P = 18;
	public static final int LUCK_M = 19;
	public static final int LUCK_P = 20;
	public static final int HUNT_KILLS_M = 21;
	public static final int HUNT_KILLS_P = 22;
	public static final int HUNT_TIME_M = 23;
	public static final int HUNT_TIME_P = 24;
	public static final int HUNT_MOB_NEXT = 25;
	public static final int STRIKE_KILLS_M = 26;
	public static final int STRIKE_KILLS_P = 27;
	public static final int STRIKE_TIME_M = 28;
	public static final int STRIKE_TIME_P = 29;
	public static final int OPEN_KIT = 30;
	public static final int RESET = 31;
	public static final int EDIT_MAP = 32;
	public static final int RESET_MAP = 33;
	public static final int SAVE_DRAFT = 34;
	public static final int NEW_CUSTOM = 35;
	public static final int BASE_NEXT = 36;
	public static final int DELETE_CUSTOM = 37;
	public static final int OPEN_BREAK = 38;
	public static final int DELETE_DRAFT = 39;
	public static final int SAVE_MAP = 40;
	public static final int CANCEL_MAP = 41;
	public static final int BREAK_HAND = 42;
	public static final int BREAK_LOOK = 43;
	public static final int BREAK_CLEAR = 44;
	public static final int OPEN_ORDER = 45;
	public static final int ORDER_LEFT = 46;
	public static final int ORDER_RIGHT = 47;
	public static final int ORDER_UP = 48;
	public static final int ORDER_DOWN = 49;
	public static final int ORDER_RESET = 127;
	/** id 50…99 — убрать блок из списка ломаемых (не больше EventCatalog.MAX_BREAKABLE). */
	public static final int BREAK_DEL_BASE = 50;
	/** На странице порядка id 50…76 — выбрать кнопку на позиции (id - 50). */
	public static final int ORDER_PICK_BASE = 50;

	private final PropertyDelegate props = new ArrayPropertyDelegate(PROPS);
	private final ServerPlayerEntity viewer;
	private final DevEventFactory owner;
	private int type;
	private int page;
	private int orderSel = -1;
	private final List<String> customNames = new ArrayList<>();
	private final List<String> customDescs = new ArrayList<>();
	private final List<Integer> customBases = new ArrayList<>();
	private final List<String> breakable = new ArrayList<>();

	public static DevEventScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int n = buf.readVarInt();
		int[] data = new int[PROPS];
		for (int i = 0; i < n; i++) {
			int v = buf.readVarInt();
			if (i < PROPS) {
				data[i] = v;
			}
		}
		DevEventScreenHandler handler = new DevEventScreenHandler(syncId, null, null, data[TYPE], data[PAGE]);
		int customs = Math.min(EventCatalog.MAX_CUSTOM, buf.readVarInt());
		for (int i = 0; i < customs; i++) {
			handler.customNames.add(buf.readString(EventCatalog.NAME_MAX * 4));
			handler.customDescs.add(buf.readString(EventCatalog.DESC_MAX * 4));
			handler.customBases.add(buf.readVarInt());
		}
		int blocks = Math.min(EventCatalog.MAX_BREAKABLE, buf.readVarInt());
		for (int i = 0; i < blocks; i++) {
			handler.breakable.add(buf.readString(256));
		}
		handler.load(data);
		return handler;
	}

	/** Данные открытия: числовые настройки, затем свои МП и список ломаемых блоков выбранного МП. */
	public static void write(PacketByteBuf buf, ServerPlayerEntity opener, int type, int page) {
		int[] data = snapshot(opener, type, page);
		buf.writeVarInt(data.length);
		for (int value : data) {
			buf.writeVarInt(value);
		}
		List<CustomEvent> customs = EventCatalog.customs();
		int count = Math.min(EventCatalog.MAX_CUSTOM, customs.size());
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			CustomEvent c = customs.get(i);
			buf.writeString(EventCatalog.name(EventCatalog.builtIn() + i), EventCatalog.NAME_MAX * 4);
			String desc = c.description == null ? "" : c.description;
			buf.writeString(desc.length() > EventCatalog.DESC_MAX ? desc.substring(0, EventCatalog.DESC_MAX) : desc,
					EventCatalog.DESC_MAX * 4);
			buf.writeVarInt(EventCatalog.clampBase(c.base));
		}
		List<String> blocks = EventCatalog.valid(type) ? EventCatalog.breakable(EventCatalog.key(type)) : List.of();
		buf.writeVarInt(Math.min(EventCatalog.MAX_BREAKABLE, blocks.size()));
		for (int i = 0; i < blocks.size() && i < EventCatalog.MAX_BREAKABLE; i++) {
			buf.writeString(blocks.get(i), 256);
		}
	}

	public DevEventScreenHandler(int syncId, ServerPlayerEntity viewer, DevEventFactory owner, int type, int page) {
		super(ModScreenHandlers.DEV_EVENT, syncId);
		this.viewer = viewer;
		this.owner = owner;
		this.type = viewer == null || EventCatalog.valid(type) ? type : -1;
		this.page = this.type < 0 ? (page == PAGE_ORDER ? PAGE_ORDER : PAGE_SETTINGS) : page == PAGE_ORDER ? PAGE_SETTINGS : page;
		this.addProperties(this.props);
		if (viewer != null) {
			load(snapshot(viewer, this.type, this.page));
		}
	}

	public static int[] snapshot(ServerPlayerEntity viewer, int type, int page) {
		int[] d = new int[PROPS];
		boolean ok = EventCatalog.valid(type);
		int base = ok ? EventCatalog.base(type) : 0;
		String key = ok ? EventCatalog.key(type) : "0";
		EventDefaults def = EventDefaults.of(key, base);
		d[TYPE] = ok ? type : -1;
		d[PLAYERS] = def.players;
		d[REWARD] = def.reward;
		d[POWER] = def.power;
		d[BUMPERS] = def.bumpers ? 1 : 0;
		d[PRIZE1] = def.prize1;
		d[PRIZE2] = def.prize2;
		d[PRIZE3] = def.prize3;
		d[LUCK] = def.luck;
		d[HUNT_KILLS] = EventService.huntGoal();
		d[HUNT_SECONDS] = EventService.huntSeconds();
		d[HUNT_MOB] = EventService.huntMob();
		d[STRIKE_KILLS] = EventService.strikeKills();
		d[STRIKE_MINUTES] = EventService.strikeMinutes();
		d[MAP_SAVED] = ok && EventMaps.exists(key) ? 1 : 0;
		d[EDIT] = editState(viewer);
		d[EDIT_TYPE] = EventMapEditor.type();
		d[IS_CUSTOM] = ok && EventCatalog.isCustom(type) ? 1 : 0;
		d[BASE] = base;
		d[DRAFT] = ok && EventMaps.hasDraft(key) ? 1 : 0;
		d[PAGE] = ok ? page : page == PAGE_ORDER ? PAGE_ORDER : PAGE_SETTINGS;
		d[BREAK_COUNT] = ok ? EventCatalog.breakable(key).size() : 0;
		d[HELPERS] = EventMapEditor.helperCount();
		d[CUSTOM_COUNT] = Math.min(EventCatalog.MAX_CUSTOM, EventCatalog.customs().size());
		d[ORDER_SEL] = -1;
		List<Integer> order = EventCatalog.order();
		for (int i = 0; i < EventCatalog.MAX_CHOICES; i++) {
			d[ORDER_BASE + i] = i < order.size() ? order.get(i) : -1;
		}
		return d;
	}

	private static int editState(ServerPlayerEntity viewer) {
		if (!EventMapEditor.active()) {
			return EDIT_NONE;
		}
		return EventMapEditor.editing(viewer) ? EDIT_MINE : EDIT_OTHER;
	}

	private void load(int[] data) {
		for (int i = 0; i < PROPS; i++) {
			this.props.set(i, data != null && i < data.length ? data[i] : 0);
		}
	}

	public int get(int index) {
		return this.props.get(index);
	}

	public int type() {
		return this.props.get(TYPE);
	}

	public int base() {
		return this.props.get(BASE);
	}

	public boolean custom() {
		return this.props.get(IS_CUSTOM) != 0;
	}

	public int page() {
		return this.props.get(PAGE);
	}

	/** Название МП по индексу выбора (на клиенте свои МП приходят с данными открытия). */
	public String choiceName(int choice) {
		if (choice >= 0 && choice < EventService.TYPES.length) {
			return EventService.TYPES[choice];
		}
		int i = choice - EventService.TYPES.length;
		return i >= 0 && i < this.customNames.size() ? this.customNames.get(i) : "—";
	}

	public String choiceDesc(int choice) {
		int i = choice - EventService.TYPES.length;
		return i >= 0 && i < this.customDescs.size() ? this.customDescs.get(i) : "";
	}

	public int customCount() {
		return this.customNames.size();
	}

	/** Сколько кнопок в списке выбора МП (на клиенте). */
	public int orderCount() {
		return Math.min(EventCatalog.MAX_CHOICES, EventService.TYPES.length + this.customNames.size());
	}

	/** МП на позиции pos списка выбора (порядок из /devent). */
	public int orderAt(int pos) {
		int choice = pos >= 0 && pos < EventCatalog.MAX_CHOICES ? this.props.get(ORDER_BASE + pos) : -1;
		return choice < 0 ? pos : choice;
	}

	public int orderSelected() {
		return this.props.get(ORDER_SEL);
	}

	public List<String> breakable() {
		return this.breakable;
	}

	@Override
	public void sendContentUpdates() {
		if (this.viewer != null) {
			this.props.set(EDIT, editState(this.viewer));
			this.props.set(EDIT_TYPE, EventMapEditor.type());
			this.props.set(HELPERS, EventMapEditor.helperCount());
		}
		super.sendContentUpdates();
	}

	private void remember() {
		if (this.owner != null) {
			this.owner.remember(this.type, this.page);
		}
	}

	/** Пересоздать экран — нужны свежие названия и список блоков, которые не помещаются в числовые свойства. */
	private boolean reopen(ServerPlayerEntity admin) {
		remember();
		ScreenNav.reopen(admin);
		return true;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin) || DataManager.get() == null
				|| !CommandUtil.canUse(admin.getCommandSource(), "devent")) {
			return false;
		}
		if (id == SAVE_MAP || id == CANCEL_MAP || id == SAVE_DRAFT) {
			int mode = id == SAVE_MAP ? EventMapEditor.FINISH_PUBLISH
					: id == SAVE_DRAFT ? EventMapEditor.FINISH_DRAFT : EventMapEditor.FINISH_DISCARD;
			EventMapEditor.finish(admin, mode);
			return true;
		}
		if (id == OPEN_ORDER) {
			this.type = -1;
			this.page = PAGE_ORDER;
			this.orderSel = -1;
			remember();
			load(snapshot(admin, this.type, this.page));
			return true;
		}
		if (this.type < 0 && this.page == PAGE_ORDER && id != BACK) {
			return applyOrder(admin, id);
		}
		if (id >= TYPE_BASE && id < TYPE_BASE + EventCatalog.count()) {
			this.type = id - TYPE_BASE;
			this.page = PAGE_SETTINGS;
		} else if (id == NEW_CUSTOM) {
			CustomEvent c = EventCatalog.create(EventCatalog.valid(this.type) ? EventCatalog.base(this.type) : 0);
			if (c == null) {
				Chat.send(admin, Chat.red("Своих МП уже " + EventCatalog.MAX_CUSTOM + " — больше нельзя."));
				return true;
			}
			this.type = EventCatalog.byKey(c.id);
			this.page = PAGE_SETTINGS;
			Chat.send(admin, Chat.green("Создано своё МП «" + c.name + "». Задайте название, описание, правила и карту."));
			return reopen(admin);
		} else if (id == BACK) {
			if (this.page != PAGE_SETTINGS) {
				this.page = PAGE_SETTINGS;
			} else {
				this.type = -1;
			}
		} else if (!EventCatalog.valid(this.type)) {
			return false;
		} else {
			Boolean result = apply(admin, id);
			if (result == null) {
				return true;
			}
			if (!result) {
				return false;
			}
		}
		remember();
		load(snapshot(admin, this.type, this.page));
		return true;
	}

	/** Страница порядка: выбор кнопки и сдвиг на место / на строку сетки (в строке две кнопки). */
	private boolean applyOrder(ServerPlayerEntity admin, int id) {
		int count = EventCatalog.count();
		if (id >= ORDER_PICK_BASE && id < ORDER_PICK_BASE + count) {
			int pos = id - ORDER_PICK_BASE;
			this.orderSel = this.orderSel == pos ? -1 : pos;
		} else if (id == ORDER_LEFT || id == ORDER_RIGHT || id == ORDER_UP || id == ORDER_DOWN) {
			if (this.orderSel < 0 || this.orderSel >= count) {
				Chat.send(admin, Chat.red("Сначала нажмите МП, которое нужно сдвинуть."));
				return true;
			}
			int delta = id == ORDER_LEFT ? -1 : id == ORDER_RIGHT ? 1 : id == ORDER_UP ? -2 : 2;
			this.orderSel = EventCatalog.moveOrder(this.orderSel, delta);
		} else if (id == ORDER_RESET) {
			EventCatalog.resetOrder();
			this.orderSel = -1;
			Chat.send(admin, Chat.yellow("Порядок кнопок МП снова стандартный."));
		} else {
			return false;
		}
		load(snapshot(admin, this.type, this.page));
		this.props.set(ORDER_SEL, this.orderSel);
		return true;
	}

	/** true — обновить свойства, null — экран уже пересоздан, false — неизвестная кнопка. */
	private Boolean apply(ServerPlayerEntity admin, int id) {
		int t = this.type;
		String key = EventCatalog.key(t);
		int base = EventCatalog.base(t);
		String name = EventCatalog.name(t);
		boolean custom = EventCatalog.isCustom(t);
		ModSettings s = DataManager.get().settings();
		if (id >= BREAK_DEL_BASE && id < BREAK_DEL_BASE + EventCatalog.MAX_BREAKABLE) {
			EventCatalog.removeBreakable(key, id - BREAK_DEL_BASE);
			reopen(admin);
			return null;
		}
		switch (id) {
			case OPEN_KIT -> {
				ScreenNav.open(admin, new TrainingKitFactory(key));
				return null;
			}
			case OPEN_BREAK -> {
				this.page = PAGE_BREAK;
				reopen(admin);
				return null;
			}
			case BREAK_HAND -> {
				ItemStack stack = admin.getMainHandStack();
				if (!(stack.getItem() instanceof BlockItem blockItem)) {
					Chat.send(admin, Chat.red("Возьмите блок в основную руку."));
					return true;
				}
				addBreakable(admin, key, name, blockItem.getBlock());
				reopen(admin);
				return null;
			}
			case BREAK_LOOK -> {
				HitResult hit = admin.raycast(8.0, 0f, false);
				if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
					Chat.send(admin, Chat.red("Посмотрите на блок (до 8 блоков) и нажмите ещё раз."));
					return true;
				}
				BlockState state = admin.getWorld().getBlockState(blockHit.getBlockPos());
				addBreakable(admin, key, name, state.getBlock());
				reopen(admin);
				return null;
			}
			case BREAK_CLEAR -> {
				EventCatalog.clearBreakable(key);
				Chat.send(admin, Chat.yellow("«" + name + "»: список ломаемых блоков очищен."));
				reopen(admin);
				return null;
			}
			case EDIT_MAP -> {
				String err = EventMapEditor.start(admin, t);
				if (err != null) {
					Chat.send(admin, Chat.red(err));
				}
				return true;
			}
			case RESET_MAP -> {
				if (key.equals(EventMapEditor.key())) {
					Chat.send(admin, Chat.red("Эту карту сейчас правят — сначала закончите правку."));
				} else if (EventMaps.delete(key)) {
					Chat.send(admin, Chat.yellow("«" + name + "»: снова стандартная карта."));
				} else {
					Chat.send(admin, Chat.red("Не удалось удалить сохранённую карту «" + name + "»."));
				}
				return true;
			}
			case DELETE_DRAFT -> {
				if (key.equals(EventMapEditor.key())) {
					Chat.send(admin, Chat.red("Эту карту сейчас правят — сначала закончите правку."));
				} else {
					EventMaps.deleteDraft(key);
					Chat.send(admin, Chat.yellow("«" + name + "»: черновик карты удалён."));
				}
				return true;
			}
			case BASE_NEXT -> {
				if (!custom) {
					return false;
				}
				String busy = busy(key);
				if (busy != null) {
					Chat.send(admin, Chat.red(busy));
					return true;
				}
				EventCatalog.nextBase(t);
				Chat.send(admin, Chat.yellow("«" + name + "»: правила как у «" + EventService.TYPES[EventCatalog.base(t)]
						+ "». Своя карта и черновик ставятся, только если их размер подходит под эти правила."));
				return true;
			}
			case DELETE_CUSTOM -> {
				if (!custom) {
					return false;
				}
				String busy = busy(key);
				if (busy != null) {
					Chat.send(admin, Chat.red(busy));
					return true;
				}
				EventCatalog.delete(t);
				Chat.send(admin, Chat.yellow("Своё МП «" + name + "» удалено вместе с картой, черновиком, набором и настройками."));
				this.type = -1;
				this.page = PAGE_SETTINGS;
				reopen(admin);
				return null;
			}
			case RESET -> {
				EventDefaults.reset(key);
				if (!custom && t == 3) {
					s.eventHuntKills = 15;
					s.eventHuntSeconds = 180;
					s.eventHuntMob = 0;
					TrainingConfig.refreshHuntDefaultKit();
				} else if (!custom && t == 12) {
					s.eventStrikeKills = 30;
					s.eventStrikeMinutes = 15;
				}
				DataManager.get().saveAll();
				Chat.send(admin, Chat.yellow("«" + name + "»: настройки по умолчанию сброшены."));
				return true;
			}
			case HUNT_KILLS_M, HUNT_KILLS_P ->
					s.eventHuntKills = Math.max(1, Math.min(200, s.eventHuntKills + (id == HUNT_KILLS_M ? -1 : 1)));
			case HUNT_TIME_M, HUNT_TIME_P ->
					s.eventHuntSeconds = Math.max(30, Math.min(900, s.eventHuntSeconds + (id == HUNT_TIME_M ? -15 : 15)));
			case HUNT_MOB_NEXT -> {
				s.eventHuntMob = (s.eventHuntMob + 1) % EventService.HUNT_MOBS.length;
				TrainingConfig.refreshHuntDefaultKit();
				Chat.send(admin, Chat.yellow("Охота: мобы «" + EventService.huntMobLabel() + "», набор обновлён."));
			}
			case STRIKE_KILLS_M, STRIKE_KILLS_P -> {
				int step = s.eventStrikeKills >= 50 ? 10 : 5;
				s.eventStrikeKills = Math.max(5, Math.min(500, s.eventStrikeKills + (id == STRIKE_KILLS_M ? -step : step)));
			}
			case STRIKE_TIME_M, STRIKE_TIME_P ->
					s.eventStrikeMinutes = Math.max(1, Math.min(120, s.eventStrikeMinutes + (id == STRIKE_TIME_M ? -1 : 1)));
			default -> {
				if (!applyDefault(EventDefaults.edit(key, base), base, id)) {
					return false;
				}
			}
		}
		DataManager.get().saveAll();
		return true;
	}

	/** Своё МП нельзя менять по правилам или удалять, пока его карту правят или оно идёт. */
	private static String busy(String key) {
		if (key.equals(EventMapEditor.key())) {
			return "Карту этого МП сейчас правят — сначала закончите правку.";
		}
		if (EventService.active() && key.equals(EventService.currentKey())) {
			return "Это МП сейчас идёт — дождитесь окончания.";
		}
		return null;
	}

	private static void addBreakable(ServerPlayerEntity admin, String key, String name, net.minecraft.block.Block block) {
		String err = EventCatalog.addBreakable(key, block);
		if (err != null) {
			Chat.send(admin, Chat.red(err));
		} else {
			Chat.send(admin, Chat.green("«" + name + "»: участники смогут ломать «" + ItemLabels.of(block) + "»."));
		}
	}

	private static boolean applyDefault(EventDefaults d, int t, int id) {
		int step = t == 11 ? 2 : 1;
		switch (id) {
			case PLAYERS_M -> d.players -= step;
			case PLAYERS_P -> d.players += step;
			case REWARD_M -> d.reward -= 1;
			case REWARD_P -> d.reward += 1;
			case REWARD_M10 -> d.reward -= 10;
			case REWARD_P10 -> d.reward += 10;
			case POWER_M -> d.power -= 1;
			case POWER_P -> d.power += 1;
			case POWER_M5 -> d.power -= 5;
			case POWER_P5 -> d.power += 5;
			case BUMPERS_TOGGLE -> d.bumpers = !d.bumpers;
			case PRIZE1_M -> d.prize1 -= 5;
			case PRIZE1_P -> d.prize1 += 5;
			case PRIZE2_M -> d.prize2 -= 5;
			case PRIZE2_P -> d.prize2 += 5;
			case PRIZE3_M -> d.prize3 -= 5;
			case PRIZE3_P -> d.prize3 += 5;
			case LUCK_M -> d.luck -= 1;
			case LUCK_P -> d.luck += 1;
			default -> {
				return false;
			}
		}
		d.clamp(t);
		return true;
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
