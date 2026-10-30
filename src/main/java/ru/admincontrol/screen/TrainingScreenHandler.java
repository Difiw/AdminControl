package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.BotMatch;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.TrainingConfig;
import ru.admincontrol.util.TrainingService;

public class TrainingScreenHandler extends ScreenHandler {
	public static final int TYPE_BASE = 0;
	public static final int TIME_BASE = 10;
	public static final int START = 30;
	/** Левая карточка «С мобами». */
	public static final int OPEN_CLASSIC = 40;
	/** Левая карточка «С ботами». */
	public static final int OPEN_BOTS = 41;
	public static final int BACK = 42;
	public static final int BOT_MODE_BASE = 100;
	public static final int DIFF_BASE = 120;
	public static final int ALLY_MINUS = 130;
	public static final int ALLY_PLUS = 131;
	public static final int ENEMY_MINUS = 132;
	public static final int ENEMY_PLUS = 133;
	public static final int BOT_START = 134;

	private final PropertyDelegate props = new ArrayPropertyDelegate(12);
	private int type;
	private int time = 1;
	/** 1 — с мобами, 2 — с ботами. */
	private int page = 1;
	private int botMode;
	private int diff = 1;
	private int allies = BotMatch.DEF_ALLIES[0];
	private int enemies = BotMatch.DEF_ENEMIES[0];
	private final ServerPlayerEntity owner;

	public static TrainingScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new TrainingScreenHandler(syncId);
	}

	public TrainingScreenHandler(int syncId) {
		this(syncId, null);
	}

	public TrainingScreenHandler(int syncId, PlayerEntity owner) {
		super(ModScreenHandlers.TRAINING, syncId);
		this.owner = owner instanceof ServerPlayerEntity sp ? sp : null;
		this.addProperties(this.props);
		sync();
	}

	public int type() {
		return this.props.get(0);
	}

	public int minutes() {
		return this.props.get(1);
	}

	public int coins() {
		return this.props.get(2);
	}

	public int powerTenths() {
		return this.props.get(3);
	}

	public int page() {
		return this.props.get(4);
	}

	public boolean botAllowed() {
		return this.props.get(5) != 0;
	}

	public int botMode() {
		return this.props.get(6);
	}

	public int difficulty() {
		return this.props.get(7);
	}

	public int allies() {
		return this.props.get(8);
	}

	public int enemies() {
		return this.props.get(9);
	}

	public int botCoins() {
		return this.props.get(10);
	}

	public int botPowerTenths() {
		return this.props.get(11);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (id == OPEN_CLASSIC) {
			this.page = 1;
		} else if (id == OPEN_BOTS) {
			if (!botAllowedServer()) {
				Chat.send(serverPlayer, Chat.red("Тренировки с ботами сейчас недоступны."));
				return true;
			}
			this.page = 2;
		} else if (id >= BOT_MODE_BASE && id < BOT_MODE_BASE + BotMatch.NAMES.length) {
			this.botMode = id - BOT_MODE_BASE;
			this.allies = BotMatch.DEF_ALLIES[this.botMode];
			this.enemies = BotMatch.DEF_ENEMIES[this.botMode];
		} else if (id >= DIFF_BASE && id < DIFF_BASE + 3) {
			this.diff = id - DIFF_BASE;
		} else if (id == ALLY_MINUS) {
			this.allies = Math.max(0, this.allies - 1);
		} else if (id == ALLY_PLUS) {
			this.allies = Math.min(8, this.allies + 1);
		} else if (id == ENEMY_MINUS) {
			if (!BotMatch.enemiesFixed(this.botMode)) {
				this.enemies = Math.max(1, this.enemies - 1);
			}
		} else if (id == ENEMY_PLUS) {
			if (!BotMatch.enemiesFixed(this.botMode)) {
				this.enemies = Math.min(16, this.enemies + 1);
			}
		} else if (id == BOT_START) {
			if (!botAllowedServer()) {
				Chat.send(serverPlayer, Chat.red("Тренировки с ботами сейчас недоступны."));
				return true;
			}
			if (!canEnter(serverPlayer)) {
				return true;
			}
			TrainingService.requestBots(serverPlayer, new TrainingService.BotSetup(this.botMode, this.diff, this.allies,
					BotMatch.enemiesFixed(this.botMode) ? BotMatch.DEF_ENEMIES[this.botMode] : this.enemies));
			return true;
		} else if (id >= TYPE_BASE && id < TYPE_BASE + TrainingConfig.TYPES.length) {
			this.type = id - TYPE_BASE;
		} else if (id >= TIME_BASE && id < TIME_BASE + TrainingConfig.MINUTES.length) {
			this.time = id - TIME_BASE;
		} else if (id == START) {
			if (!canEnter(serverPlayer)) {
				return true;
			}
			String kind = TrainingConfig.TYPES[Math.max(0, Math.min(TrainingConfig.TYPES.length - 1, this.type))];
			int minutes = TrainingConfig.MINUTES[Math.max(0, Math.min(TrainingConfig.MINUTES.length - 1, this.time))];
			TrainingService.request(serverPlayer, kind, minutes);
			return true;
		} else {
			return false;
		}
		sync();
		return true;
	}

	private static boolean canEnter(ServerPlayerEntity serverPlayer) {
		if (TrainingService.active(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red("Вы уже на тренировке."));
			return false;
		}
		if (ru.admincontrol.util.EventService.member(serverPlayer)
				|| ru.admincontrol.util.EventService.isHost(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red("Нельзя зайти на тренировку во время мероприятия."));
			return false;
		}
		if (ru.admincontrol.util.ClanWarService.memberOfWar(serverPlayer)
				|| ru.admincontrol.util.ClanWarService.jailed(serverPlayer)) {
			Chat.send(serverPlayer, Chat.red("Нельзя зайти на тренировку во время битвы кланов."));
			return false;
		}
		return true;
	}

	private void sync() {
		String kind = TrainingConfig.TYPES[Math.max(0, Math.min(TrainingConfig.TYPES.length - 1, this.type))];
		int minutes = TrainingConfig.MINUTES[Math.max(0, Math.min(TrainingConfig.MINUTES.length - 1, this.time))];
		this.props.set(0, this.type);
		this.props.set(1, minutes);
		this.props.set(2, TrainingConfig.coins(kind, minutes));
		this.props.set(3, TrainingConfig.powerTenths(kind, minutes));
		boolean allowed = botAllowedServer();
		this.props.set(4, allowed || this.page != 2 ? this.page : 1);
		this.props.set(5, allowed ? 1 : 0);
		this.props.set(6, this.botMode);
		this.props.set(7, this.diff);
		this.props.set(8, this.allies);
		this.props.set(9, BotMatch.enemiesFixed(this.botMode) ? BotMatch.DEF_ENEMIES[this.botMode] : this.enemies);
		this.props.set(10, BotMatch.coins(this.botMode, this.diff, this.allies, this.enemies));
		this.props.set(11, BotMatch.powerTenths(this.botMode, this.diff));
	}

	/** Режимы с ботами открыты, когда разработчик включил бета-версии в /dserver. */
	private static boolean botAllowedServer() {
		return DataManager.get() != null && DataManager.get().settings().betaFeaturesEnabled;
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
