package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.AntiCheatRule;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.AntiCheatRules;
import ru.admincontrol.util.AntiCheatService;
import ru.admincontrol.util.Chat;

/** /anticheat: проверки античита, наказания за каждое нарушение и тест на себе (меняет только разработчик). */
public class AntiCheatScreenHandler extends ScreenHandler {
	/** Античит сам наказывает, когда уверен. */
	public static final int ENABLED = 0;
	/** Проверка читов: файлы игры и подозрения — только сообщения администрации, работает и без античита. */
	public static final int FILE_SCAN = 1;
	public static final int XRAY = 2;
	public static final int CLICKS = 3;
	public static final int REPEAT = 4;
	public static final int XRAY_HIDDEN = 5;
	public static final int XRAY_WINDOW = 6;
	public static final int AFK = 7;
	public static final int AFK_MIN = 8;
	public static final int STAFF = 9;
	public static final int TEST_MIN = 10;
	/** Секунд до конца теста на себе у того, кто открыл экран; 0 — теста нет. */
	public static final int TEST = 11;
	public static final int BUGS = 12;
	public static final int BUG_ERRORS = 13;
	public static final int SETTINGS = 14;
	/** Свойства правил: вид наказания — RULE_BASE + i*2, срок — RULE_BASE + i*2 + 1. */
	public static final int RULE_BASE = SETTINGS;
	public static final int COUNT = RULE_BASE + AntiCheatRules.KEYS.length * 2;

	/** id кнопок правил: вид ◀/▶ — KIND_BASE + i*2 (+1), срок −/+ — AMOUNT_BASE + i*2 (+1). */
	public static final int KIND_BASE = 40;
	public static final int AMOUNT_BASE = 60;

	/** id кнопки строки настроек: строка * 2 — «−», строка * 2 + 1 — «+»; у переключателей любое из двух. */
	public static int minus(int row) {
		return row * 2;
	}

	public static int plus(int row) {
		return row * 2 + 1;
	}

	public static boolean isToggle(int row) {
		return row == ENABLED || row == XRAY || row == AFK || row == FILE_SCAN || row == STAFF || row == TEST
				|| row == BUGS || row == BUG_ERRORS;
	}

	private final PropertyDelegate props;

	public static AntiCheatScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new AntiCheatScreenHandler(syncId, new ArrayPropertyDelegate(COUNT));
	}

	public static AntiCheatScreenHandler createServer(int syncId, ServerPlayerEntity viewer) {
		return new AntiCheatScreenHandler(syncId, new PropertyDelegate() {
			@Override
			public int get(int index) {
				DataManager data = DataManager.get();
				if (data == null) {
					return 0;
				}
				if (index == TEST) {
					return AntiCheatService.testSecondsLeft(viewer);
				}
				return read(data.settings(), index);
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return COUNT;
			}
		});
	}

	private AntiCheatScreenHandler(int syncId, PropertyDelegate props) {
		super(ModScreenHandlers.ANTICHEAT, syncId);
		this.props = props;
		this.addProperties(props);
	}

	public int get(int row) {
		return this.props.get(row);
	}

	public int ruleKind(int i) {
		return this.props.get(RULE_BASE + i * 2);
	}

	public int ruleAmount(int i) {
		return this.props.get(RULE_BASE + i * 2 + 1);
	}

	private static int read(ModSettings s, int row) {
		if (row >= RULE_BASE && row < COUNT) {
			AntiCheatRule rule = AntiCheatRules.rule(AntiCheatRules.KEYS[(row - RULE_BASE) / 2]);
			if ((row - RULE_BASE) % 2 == 0) {
				return rule.kind;
			}
			return rule.kind == AntiCheatRules.JAIL ? rule.minutes : rule.days;
		}
		return switch (row) {
			case ENABLED -> s.anticheatEnabled ? 1 : 0;
			case XRAY -> s.anticheatXray ? 1 : 0;
			case CLICKS -> Math.max(0, s.anticheatClicksPerSec);
			case REPEAT -> Math.max(30, s.anticheatRepeatSeconds);
			case XRAY_HIDDEN -> Math.max(12, s.anticheatXrayHidden);
			case XRAY_WINDOW -> Math.max(30, s.anticheatXrayWindowSec);
			case AFK -> s.afkKickEnabled ? 1 : 0;
			case AFK_MIN -> Math.max(1, s.afkMinutes);
			case FILE_SCAN -> s.fileScanEnabled ? 1 : 0;
			case STAFF -> s.anticheatPunishStaff ? 1 : 0;
			case TEST_MIN -> Math.max(1, Math.min(60, s.anticheatTestMinutes));
			case BUGS -> s.bugSearchEnabled ? 1 : 0;
			case BUG_ERRORS -> s.bugServerErrors ? 1 : 0;
			default -> 0;
		};
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(admin) < AdminRanks.DEV) {
			Chat.send(admin, Chat.red("Настройки античита меняет только разработчик."));
			return false;
		}
		String label;
		if (id >= KIND_BASE && id < KIND_BASE + AntiCheatRules.KEYS.length * 2) {
			int i = (id - KIND_BASE) / 2;
			String key = AntiCheatRules.KEYS[i];
			AntiCheatRules.cycleKind(key, (id - KIND_BASE) % 2 == 1);
			label = "Наказание «" + AntiCheatRules.LABELS[i] + "»: " + AntiCheatRules.verdict(AntiCheatRules.rule(key));
		} else if (id >= AMOUNT_BASE && id < AMOUNT_BASE + AntiCheatRules.KEYS.length * 2) {
			int i = (id - AMOUNT_BASE) / 2;
			String key = AntiCheatRules.KEYS[i];
			if (!AntiCheatRules.shiftAmount(key, (id - AMOUNT_BASE) % 2 == 1)) {
				return false;
			}
			label = "Наказание «" + AntiCheatRules.LABELS[i] + "»: " + AntiCheatRules.verdict(AntiCheatRules.rule(key));
		} else {
			int row = id / 2;
			if (row < 0 || row >= SETTINGS) {
				return false;
			}
			if (row == TEST) {
				boolean on = AntiCheatService.toggleTest(admin);
				ActivityLog.admin(admin.getGameProfile().getName(), on ? "Включил тест античита на себе" : "Выключил тест античита на себе");
				return true;
			}
			label = applySetting(DataManager.get().settings(), row, id % 2 == 1);
			if (label == null) {
				return false;
			}
		}
		DataManager.get().saveAll();
		String nick = admin.getGameProfile().getName();
		ActivityLog.admin(nick, "Изменил настройку античита: " + label);
		AdminNotify.toAdmins(admin.getServer(), null, Chat.gold("[Античит] " + nick + " изменил: " + label));
		return true;
	}

	private static String applySetting(ModSettings s, int row, boolean up) {
		switch (row) {
			case ENABLED -> {
				s.anticheatEnabled = !s.anticheatEnabled;
				return "Античит (наказания): " + onOff(s.anticheatEnabled);
			}
			case XRAY -> {
				s.anticheatXray = !s.anticheatXray;
				return "Проверка X-Ray: " + onOff(s.anticheatXray);
			}
			case CLICKS -> {
				int cur = Math.max(0, s.anticheatClicksPerSec);
				int next = up ? (cur <= 0 ? 25 : Math.min(80, cur + 1)) : (cur <= 25 ? 0 : cur - 1);
				s.anticheatClicksPerSec = next;
				return "Автокликер: лимит " + (next <= 0 ? "выкл" : next + " к/с");
			}
			case REPEAT -> {
				s.anticheatRepeatSeconds = clamp(read(s, REPEAT) + (up ? 30 : -30), 30, 3600);
				return "Автокликер: ступени сбрасываются через " + s.anticheatRepeatSeconds + " сек";
			}
			case XRAY_HIDDEN -> {
				s.anticheatXrayHidden = clamp(read(s, XRAY_HIDDEN) + (up ? 1 : -1), 12, 40);
				return "X-Ray: скрытой руды " + s.anticheatXrayHidden;
			}
			case XRAY_WINDOW -> {
				s.anticheatXrayWindowSec = clamp(read(s, XRAY_WINDOW) + (up ? 10 : -10), 30, 600);
				return "X-Ray: окно " + s.anticheatXrayWindowSec + " сек";
			}
			case AFK -> {
				s.afkKickEnabled = !s.afkKickEnabled;
				return "Проверка АФК: " + onOff(s.afkKickEnabled);
			}
			case AFK_MIN -> {
				s.afkMinutes = clamp(read(s, AFK_MIN) + (up ? 1 : -1), 1, 60);
				return "АФК: наказание через " + s.afkMinutes + " мин";
			}
			case FILE_SCAN -> {
				s.fileScanEnabled = !s.fileScanEnabled;
				return "Проверка читов (сообщения): " + onOff(s.fileScanEnabled);
			}
			case STAFF -> {
				s.anticheatPunishStaff = !s.anticheatPunishStaff;
				return "Наказывать администрацию: " + onOff(s.anticheatPunishStaff);
			}
			case TEST_MIN -> {
				return "Таймера больше нет — используйте команду /actest";
			}
			case BUGS -> {
				s.bugSearchEnabled = !s.bugSearchEnabled;
				return "Поиск багаюза (в баг-лист): " + onOff(s.bugSearchEnabled);
			}
			case BUG_ERRORS -> {
				s.bugServerErrors = !s.bugServerErrors;
				return "Ошибки сервера в баг-лист: " + onOff(s.bugServerErrors);
			}
			default -> {
				return null;
			}
		}
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String onOff(boolean on) {
		return on ? "вкл" : "выкл";
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
