package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.screen.AplayerScreenHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AplayerActions {
	private AplayerActions() {
	}

	public static List<Action> list(ServerPlayerEntity admin, String nick) {
		List<Action> out = new ArrayList<>();
		if (admin == null || DataManager.get() == null) {
			return out;
		}
		int rank = DataManager.get().getAdminLevel(admin);
		add(out, rank, "g", "ТП к игроку", null, AdminRanks.MOD, true, false);
		add(out, rank, "gh", "ТП игрока к себе", null, AdminRanks.MOD, true, false);
		add(out, rank, "sg", "Тихий ТП к игроку", null, AdminRanks.MOD, true, false);
		add(out, rank, "sgh", "Тихий ТП игрока к себе", null, AdminRanks.MOD, true, false);
		add(out, rank, "sp", "Слежка", null, AdminRanks.MOD, true, false);
		add(out, rank, "banp", "Бан", "дни причина", AdminRanks.MOD, false, true);
		add(out, rank, "unbanp", "Снять бан", "причина", AdminRanks.MOD, false, true);
		add(out, rank, "warnp", "Варн", "причина", AdminRanks.MOD, false, true);
		add(out, rank, "unwarnp", "Снять варн", "причина", AdminRanks.MOD, false, true);
		add(out, rank, "jailp", "Тюрьма", "минуты причина", AdminRanks.MOD, false, true);
		add(out, rank, "unjailp", "Освободить", "причина", AdminRanks.MOD, false, true);
		add(out, rank, "akickp", "Кик", "причина", AdminRanks.MOD, true, true);
		add(out, rank, "akill", "Убить", null, AdminRanks.MOD, true, true);
		add(out, rank, "spawn", "На спавн игрока", "причина", AdminRanks.MOD, true, false);
		add(out, rank, "get", "Статистика", null, AdminRanks.MOD, false, false);
		add(out, rank, "conget", "Статистика в чат", null, AdminRanks.MOD, false, false);
		add(out, rank, "amsg", "Сообщение", "текст", AdminRanks.MOD, true, false);
		add(out, rank, "aeat", "Сытость", "0-20", AdminRanks.MOD, true, false);
		add(out, rank, "adel", "Забрать предмет", "предмет кол-во|all", AdminRanks.MOD, true, false);
		add(out, rank, "nmode", "Читы игроку", "true|false", AdminRanks.MOD, false, false);
		add(out, rank, "getinv", "Инвентарь", null, AdminRanks.ADMIN, false, false);
		add(out, rank, "punish", "Наказания", null, AdminRanks.ADMIN, false, false);
		add(out, rank, "conpunish", "Наказания в чат", null, AdminRanks.ADMIN, false, false);
		add(out, rank, "ahp", "Здоровье", "0-20", AdminRanks.ADMIN, true, false);
		add(out, rank, "abalance", "Баланс WC", null, AdminRanks.ADMIN, false, false);
		add(out, rank, "setluck", "Удача", "значение", AdminRanks.ADMIN, false, false);
		add(out, rank, "aclear", "Очистить инвентарь", null, AdminRanks.SENIOR, true, false);
		add(out, rank, "setgamemode", "Режим игры", "survival|creative|spectator|adventure", AdminRanks.SENIOR, true, false);
		add(out, rank, "acoins", "История WC", null, AdminRanks.SENIOR, false, false);
		add(out, rank, "conacoins", "История WC в чат", null, AdminRanks.SENIOR, false, false);
		add(out, rank, "agive", "Выдать предмет", "предмет кол-во", AdminRanks.SENIOR, true, false);
		add(out, rank, "permbanp", "Пермабан", "причина", AdminRanks.SENIOR, false, true);
		add(out, rank, "unpermbanp", "Снять пермабан", "причина", AdminRanks.SENIOR, false, true);
		boolean canAddAdmin = rank >= AdminRanks.CURATOR
				&& (!DataManager.get().settings().addAdminLockedToStaff || rank >= AdminRanks.GA);
		if (canAddAdmin) {
			add(out, rank, "add_admin", "Назначить админом", "уровень", AdminRanks.CURATOR, false, false);
		}
		add(out, rank, "set_admin", "Уровень админа", "уровень", AdminRanks.CURATOR, false, false);
		add(out, rank, "un_admin", "Снять админа", "причина", AdminRanks.CURATOR, false, false);
		add(out, rank, "addnpc", "Создать NPC", null, AdminRanks.CURATOR, false, false);
		add(out, rank, "npclist", "Список NPC", null, AdminRanks.CURATOR, false, false);
		add(out, rank, "npchere", "NPC к себе", null, AdminRanks.CURATOR, false, false);
		add(out, rank, "delnpc", "Удалить NPC", null, AdminRanks.CURATOR, false, false);
		add(out, rank, "npc", "Команда от NPC", "команда или текст", AdminRanks.CURATOR, false, false);
		add(out, rank, "control", "Войти в NPC", null, AdminRanks.CURATOR, false, false);
		add(out, rank, "banagm", "Запретить AGM", null, AdminRanks.TECH, false, false);
		add(out, rank, "unbanagm", "Разрешить AGM", null, AdminRanks.TECH, false, false);
		add(out, rank, "unagm", "Снять AGM", null, AdminRanks.TECH, true, false);
		add(out, rank, "plog", "Логи игрока", null, AdminRanks.TECH, false, false);
		add(out, rank, "conplog", "Логи игрока в чат", null, AdminRanks.TECH, false, false);
		add(out, rank, "alog", "Логи администратора", null, AdminRanks.TECH, false, false);
		add(out, rank, "conalog", "Логи админа в чат", null, AdminRanks.TECH, false, false);
		add(out, rank, "clearlog", "Удалить логи игрока", null, AdminRanks.TECH, false, false);
		add(out, rank, "addcoin", "Выдать WC", "кол-во", AdminRanks.TECH, false, false);
		add(out, rank, "uncoin", "Забрать WC", "кол-во", AdminRanks.TECH, false, false);
		add(out, rank, "setpower", "Сила", "значение", AdminRanks.TECH, false, false);
		add(out, rank, "unprivate", "Снять приват дома", null, AdminRanks.TECH, false, false);
		add(out, rank, "acasino", "Казино игроку", "true|false", AdminRanks.TECH, false, false);
		add(out, rank, "add_wight", "В whitelist", null, AdminRanks.TECH, false, false);
		add(out, rank, "del_wight", "Из whitelist", null, AdminRanks.TECH, false, false);
		add(out, rank, "atsetspawn", "Спавн игрока сюда", null, AdminRanks.TECH, false, false);
		add(out, rank, "aclearlog", "Удалить админ-логи", null, AdminRanks.GA, false, false);
		add(out, rank, "getclan", "Просмотр клана", "клан", AdminRanks.CURATOR, false, false);
		add(out, rank, "getclanwh", "Склад клана", "клан", AdminRanks.CURATOR, false, false);
		add(out, rank, "areset", "Обнулить аккаунт", "причина", AdminRanks.GA, false, false);
		return out;
	}

	public static Action find(List<Action> actions, String id) {
		if (actions == null || id == null) {
			return null;
		}
		for (Action action : actions) {
			if (id.equalsIgnoreCase(action.id)) {
				return action;
			}
		}
		return null;
	}

	public static String commandLine(Action action, String nick, String extra) {
		if (action == null || nick == null || nick.isBlank()) {
			return null;
		}
		String rest = extra == null ? "" : extra.trim();
		if (action.hint != null && rest.isEmpty()) {
			return null;
		}
		StringBuilder line = new StringBuilder("/");
		line.append(action.id).append(' ').append(nick);
		if (!rest.isEmpty()) {
			line.append(' ').append(rest);
		}
		return line.toString();
	}

	public static void run(ServerPlayerEntity admin, String nick, String id, String extra) {
		if (admin == null || admin.getServer() == null || DataManager.get() == null) {
			return;
		}
		if (!(admin.currentScreenHandler instanceof AplayerScreenHandler handler)
				|| !handler.nick().equalsIgnoreCase(nick)) {
			return;
		}
		var source = admin.getCommandSource();
		if (!CommandUtil.requireAdmin(source)) {
			return;
		}
		Action action = find(list(admin, nick), id);
		if (action == null) {
			Chat.send(admin, Chat.red("Это действие вам недоступно."));
			return;
		}
		if (action.punish) {
			if (!CommandUtil.requireCanPunish(source, nick)) {
				return;
			}
		} else if (!CommandUtil.requireCanAffect(source, nick)) {
			return;
		}
		if (action.online && CommandUtil.requireOnline(source, nick) == null) {
			return;
		}
		String line = commandLine(action, handler.nick(), extra);
		if (line == null) {
			Chat.send(admin, Chat.yellow("Нужно указать: " + action.hint));
			return;
		}
		if (action.id.equals("g") || action.id.equals("gh") || action.id.equals("sg")
				|| action.id.equals("sgh") || action.id.equals("sp")) {
			admin.closeHandledScreen();
		}
		try {
			admin.getServer().getCommandManager().executeWithPrefix(source, line);
		} catch (RuntimeException ignored) {
			Chat.send(admin, Chat.red("Не удалось выполнить /" + action.id + "."));
		}
	}

	private static void add(List<Action> out, int rank, String id, String label, String hint,
			int ignored, boolean online, boolean punish) {
		if (!RankPerms.allowed(rank, id)) {
			return;
		}
		out.add(new Action(id, label, hint, AdminRanks.minForCommand(id), online, punish));
	}

	public static final class Action {
		public final String id;
		public final String label;
		public final String hint;
		public final int minRank;
		public final boolean online;
		public final boolean punish;

		public Action(String id, String label, String hint, int minRank, boolean online, boolean punish) {
			this.id = id.toLowerCase(Locale.ROOT);
			this.label = label;
			this.hint = hint;
			this.minRank = minRank;
			this.online = online;
			this.punish = punish;
		}

		public boolean needsExtra() {
			return this.hint != null && !this.hint.isBlank();
		}
	}
}
