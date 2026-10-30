package ru.admincontrol.net;

import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;

public final class ModPackets {
	public static final Identifier BOARD = new Identifier(AdminControlMod.MOD_ID, "board");
	/** /ptime: время сервера (мс), смещение пояса сервера (с) и сколько секунд показывать дату и время поверх экрана. */
	public static final Identifier PTIME = new Identifier(AdminControlMod.MOD_ID, "ptime");
	public static final Identifier AUTH_LOCK = new Identifier(AdminControlMod.MOD_ID, "auth_lock");
	/** C2S: ник и пароль с экрана входа. */
	public static final Identifier AUTH_SUBMIT = new Identifier(AdminControlMod.MOD_ID, "auth_submit");
	/** S2C: текст ошибки/подсказки для экрана входа. */
	public static final Identifier AUTH_MSG = new Identifier(AdminControlMod.MOD_ID, "auth_msg");
	public static final Identifier ACTIVITY_LOCK = new Identifier(AdminControlMod.MOD_ID, "activity_lock");
	public static final Identifier EVENT_CANCEL = new Identifier(AdminControlMod.MOD_ID, "event_cancel");
	public static final Identifier SPECTATE = new Identifier(AdminControlMod.MOD_ID, "spectate");
	public static final Identifier SPECTATE_EXIT = new Identifier(AdminControlMod.MOD_ID, "spectate_exit");
	public static final Identifier NPC_CONTROL = new Identifier(AdminControlMod.MOD_ID, "npc_control");
	public static final Identifier NPC_CONTROL_EXIT = new Identifier(AdminControlMod.MOD_ID, "npc_control_exit");
	public static final Identifier NPC_SKIN = new Identifier(AdminControlMod.MOD_ID, "npc_skin");
	public static final Identifier HOME_OUTLINE = new Identifier(AdminControlMod.MOD_ID, "home_outline");
	public static final Identifier APLAYER_RUN = new Identifier(AdminControlMod.MOD_ID, "aplayer_run");
	public static final Identifier SCREEN_BACK = new Identifier(AdminControlMod.MOD_ID, "screen_back");
	public static final Identifier SCREEN_REOPEN = new Identifier(AdminControlMod.MOD_ID, "screen_reopen");
	public static final Identifier SCREEN_BUTTON = new Identifier(AdminControlMod.MOD_ID, "screen_button");
	public static final Identifier CLAN_SYNC = new Identifier(AdminControlMod.MOD_ID, "clan_sync");
	public static final Identifier CLAN_ACTION = new Identifier(AdminControlMod.MOD_ID, "clan_action");
	public static final Identifier CLAN_TAGS = new Identifier(AdminControlMod.MOD_ID, "clan_tags");
	public static final Identifier SET_PASSWORD = new Identifier(AdminControlMod.MOD_ID, "set_password");
	public static final Identifier OPEN_BACKPACK = new Identifier(AdminControlMod.MOD_ID, "open_backpack");
	public static final Identifier ACH_TOGGLE = new Identifier(AdminControlMod.MOD_ID, "ach_toggle");
	public static final Identifier UI_VALUE = new Identifier(AdminControlMod.MOD_ID, "ui_value");
	public static final Identifier ROLE_SAVE = new Identifier(AdminControlMod.MOD_ID, "role_save");
	public static final Identifier XRAY_PACK = new Identifier(AdminControlMod.MOD_ID, "xray_pack");
	/** Сервер → клиент: режим /actest (быстрый рескан читов) и принудительная проверка. */
	public static final Identifier XRAY_TEST = new Identifier(AdminControlMod.MOD_ID, "xray_test");
	public static final Identifier FREEZE = new Identifier(AdminControlMod.MOD_ID, "freeze");
	public static final Identifier FEATURES = new Identifier(AdminControlMod.MOD_ID, "features");
	public static final Identifier OPEN_WORKBENCH = new Identifier(AdminControlMod.MOD_ID, "open_workbench");
	public static final Identifier OPEN_FURNACE = new Identifier(AdminControlMod.MOD_ID, "open_furnace");
	public static final Identifier SPECTATE_INFO = new Identifier(AdminControlMod.MOD_ID, "spectate_info");
	public static final Identifier SPECTATE_HOTBAR = new Identifier(AdminControlMod.MOD_ID, "spectate_hotbar");
	public static final Identifier SPECTATE_INV = new Identifier(AdminControlMod.MOD_ID, "spectate_inv");
	public static final Identifier FILES_REQUEST = new Identifier(AdminControlMod.MOD_ID, "files_request");
	public static final Identifier FILES_LIST = new Identifier(AdminControlMod.MOD_ID, "files_list");
	public static final Identifier FILE_PULL = new Identifier(AdminControlMod.MOD_ID, "file_pull");
	public static final Identifier FILE_DATA = new Identifier(AdminControlMod.MOD_ID, "file_data");
	public static final Identifier FILES_ADMIN = new Identifier(AdminControlMod.MOD_ID, "files_admin");
	public static final Identifier FILES_VIEW = new Identifier(AdminControlMod.MOD_ID, "files_view");
	public static final Identifier FILE_SAVE = new Identifier(AdminControlMod.MOD_ID, "file_save");
	public static final Identifier FILE_SCAN_REQUEST = new Identifier(AdminControlMod.MOD_ID, "file_scan_request");
	public static final Identifier FILE_SCAN_RESULT = new Identifier(AdminControlMod.MOD_ID, "file_scan_result");
	public static final Identifier SKILL_STATE = new Identifier(AdminControlMod.MOD_ID, "skill_state");
	public static final Identifier SKILL_BUY = new Identifier(AdminControlMod.MOD_ID, "skill_buy");
	public static final Identifier SKILL_REFRESH = new Identifier(AdminControlMod.MOD_ID, "skill_refresh");
	public static final Identifier SKILL_CLOSE = new Identifier(AdminControlMod.MOD_ID, "skill_close");
	public static final Identifier SKILL_SELF = new Identifier(AdminControlMod.MOD_ID, "skill_self");
	public static final Identifier SKILL_NAMES = new Identifier(AdminControlMod.MOD_ID, "skill_names");
	public static final Identifier SKILL_OPEN = new Identifier(AdminControlMod.MOD_ID, "skill_open");
	public static final Identifier CASINO_WHEEL = new Identifier(AdminControlMod.MOD_ID, "casino_wheel");
	public static final Identifier CASINO_SPIN = new Identifier(AdminControlMod.MOD_ID, "casino_spin");
	public static final Identifier DEV_EVENT_TEXT = new Identifier(AdminControlMod.MOD_ID, "dev_event_text");
	public static final Identifier BUG_ACTION = new Identifier(AdminControlMod.MOD_ID, "bug_action");
	public static final Identifier CASINO_RESULT = new Identifier(AdminControlMod.MOD_ID, "casino_result");
	public static final Identifier CASINO_STATE = new Identifier(AdminControlMod.MOD_ID, "casino_state");
	public static final Identifier CASINO_SELECT = new Identifier(AdminControlMod.MOD_ID, "casino_select");
	public static final Identifier CASINO_BALANCE = new Identifier(AdminControlMod.MOD_ID, "casino_balance");
	/** Server -> client: a short-lived, server-authoritative CW test launch offer. */
	public static final Identifier CW_TEST_OFFER = new Identifier(AdminControlMod.MOD_ID, "cw_test_offer");
	/** Client -> server: accept/cancel the offer. The nonce prevents forged or stale launches. */
	public static final Identifier CW_TEST_START = new Identifier(AdminControlMod.MOD_ID, "cw_test_start");
	public static final Identifier CW_STORY_START = new Identifier(AdminControlMod.MOD_ID, "cw_story_start");
	public static final Identifier CW_HELLO = new Identifier(AdminControlMod.MOD_ID, "cw_hello");
	public static final Identifier CW_HELLO_ACK = new Identifier(AdminControlMod.MOD_ID, "cw_hello_ack");
	public static final Identifier CW_GRAPH_DATA = new Identifier(AdminControlMod.MOD_ID, "cw_graph_data");
	public static final Identifier CW_GRAPH_SAVE = new Identifier(AdminControlMod.MOD_ID, "cw_graph_save");
	public static final Identifier CW_GRAPH_TEST = new Identifier(AdminControlMod.MOD_ID, "cw_graph_test");
	public static final Identifier CW_SCHEM_PREVIEW = new Identifier(AdminControlMod.MOD_ID, "cw_schem_preview");
	public static final Identifier CW_SCHEM_TRANSFORM = new Identifier(AdminControlMod.MOD_ID, "cw_schem_transform");
	public static final Identifier CW_DIALOGUE_STATE = new Identifier(AdminControlMod.MOD_ID, "cw_dialogue_state");
	public static final Identifier CW_DIALOGUE_CHOOSE = new Identifier(AdminControlMod.MOD_ID, "cw_dialogue_choose");
	public static final Identifier CW_RESPAWN_STATE = new Identifier(AdminControlMod.MOD_ID, "cw_respawn_state");
	public static final Identifier CW_RESPAWN_CHOOSE = new Identifier(AdminControlMod.MOD_ID, "cw_respawn_choose");
	public static final Identifier CW_TRANSITION = new Identifier(AdminControlMod.MOD_ID, "cw_transition");
	public static final Identifier CW_LEAVE_STATE = new Identifier(AdminControlMod.MOD_ID, "cw_leave_state");
	public static final Identifier CW_LEAVE_CHOOSE = new Identifier(AdminControlMod.MOD_ID, "cw_leave_choose");
	/** Client -> server: bind (or clear) an animation cycle on a named NPC. */
	public static final Identifier CW_ANIM_BIND = new Identifier(AdminControlMod.MOD_ID, "cw_anim_bind");
	/** Server -> client: open the animation editor for the NPC the admin clicked with the pose tool. */
	public static final Identifier CW_ANIM_OPEN = new Identifier(AdminControlMod.MOD_ID, "cw_anim_open");
	/** Server -> client: the animation cycle currently bound to a named NPC (blank = none). */
	public static final Identifier CW_ANIM_SYNC = new Identifier(AdminControlMod.MOD_ID, "cw_anim_sync");

	private ModPackets() {
	}
}
