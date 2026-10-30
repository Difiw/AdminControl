package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HelpLists {
	public static final String ALL = "Все";

	private HelpLists() {
	}

	public static final class Entry {
		public final String command;
		public final String desc;
		public final String[] filters;

		public Entry(String command, String desc, String... filters) {
			this.command = command == null ? "" : command;
			this.desc = desc == null ? "" : desc;
			this.filters = filters == null ? new String[0] : filters;
		}

		public String id() {
			String raw = this.command.startsWith("/") ? this.command.substring(1) : this.command;
			int space = raw.indexOf(' ');
			return (space < 0 ? raw : raw.substring(0, space)).toLowerCase(Locale.ROOT);
		}

		public String line() {
			return this.desc.isBlank() ? this.command : this.command + " — " + this.desc;
		}

		public boolean hasFilter(String name) {
			if (name == null || name.isBlank() || name.equalsIgnoreCase(ALL)) {
				return true;
			}
			for (String filter : this.filters) {
				if (filter.equalsIgnoreCase(name)) {
					return true;
				}
			}
			return false;
		}
	}

	public static boolean matches(Entry entry, String query, String filter) {
		if (entry == null) {
			return false;
		}
		if (!entry.hasFilter(filter)) {
			return false;
		}
		if (query == null || query.isBlank()) {
			return true;
		}
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.startsWith("/")) {
			q = q.substring(1);
		}
		if (q.isEmpty()) {
			return true;
		}
		String id = entry.id();
		String usage = entry.command.toLowerCase(Locale.ROOT);
		String desc = entry.desc.toLowerCase(Locale.ROOT);
		return id.startsWith(q) || usage.startsWith("/" + q) || desc.contains(q);
	}

	public static String resolveFilter(List<Entry> entries, String raw) {
		if (raw == null || raw.isBlank()) {
			return ALL;
		}
		String q = raw.trim();
		if (q.equalsIgnoreCase(ALL)) {
			return ALL;
		}
		for (String name : filterNames(entries)) {
			if (name.equalsIgnoreCase(q)) {
				return name;
			}
		}
		return null;
	}

	public static List<String> filterNames(List<Entry> entries) {
		Set<String> names = new LinkedHashSet<>();
		names.add(ALL);
		if (entries != null) {
			for (Entry entry : entries) {
				for (String filter : entry.filters) {
					if (filter != null && !filter.isBlank()) {
						names.add(filter);
					}
				}
			}
		}
		return new ArrayList<>(names);
	}

	public static List<String> lines(List<Entry> entries) {
		List<String> lines = new ArrayList<>();
		if (entries != null) {
			for (Entry entry : entries) {
				lines.add(entry.line());
			}
		}
		return lines;
	}

	public static List<String> lines(List<Entry> entries, String query, String filter) {
		List<String> lines = new ArrayList<>();
		if (entries == null) {
			return lines;
		}
		for (Entry entry : entries) {
			if (matches(entry, query, filter)) {
				lines.add(entry.line());
			}
		}
		return lines;
	}

	public static List<String> admin(ServerCommandSource src) {
		return lines(adminEntries(src));
	}

	public static List<String> player() {
		return lines(playerEntries());
	}

	public static List<Entry> adminEntries(ServerCommandSource src) {
		List<Entry> out = new ArrayList<>();
		add(out, src, "/ahelp [поиск|фильтр]", "этот список, поиск и фильтр", "Справка");
		add(out, src, "/conahelp [поиск|фильтр]", "этот список в чат / консоль", "Справка", "YourControl");
		add(out, src, "/g <ник>", "телепорт к игроку", "ТП");
		add(out, src, "/gh <ник>", "телепорт игрока к себе", "ТП");
		add(out, src, "/sg <ник>", "тихий телепорт к игроку", "ТП");
		add(out, src, "/sgh <ник>", "тихий телепорт игрока к себе", "ТП");
		add(out, src, "/sp <ник>", "слежка (не на себе)", "ТП");
		add(out, src, "/spoff", "выйти из слежки", "ТП");
		add(out, src, "/banp <ник> <дни> <причина>", "временный бан", "Ban");
		add(out, src, "/unbanp <ник> <причина>", "снять бан", "Ban");
		add(out, src, "/banipp <ник|IP> <причина>", "бан по IP: ник — все его IP, либо один адрес", "Ban");
		add(out, src, "/unbanipp <ник|IP> <причина>", "снять бан по IP", "Ban");
		add(out, src, "/filecheck <ник>", "файлы игрока: ресурспаки, моды, шейдеры, конфиги — просмотр, поиск, скачивание", "Ban");
		add(out, src, "/buglist", "баг-лист: багаюз (без наказаний) и ошибки сервера — время, место, ник, описание", "Ban");
		add(out, src, "/anticheat", "античит: проверки, наказание за каждое нарушение (АФК, автокликер, X-Ray…) (только разработчик)", "Ban");
		add(out, src, "/actest", "тест античита на себе: без таймера; активный чит → кик и сброс", "Ban");
		add(out, src, "/ipcheck <ник|IP>", "совпадения по IP: все ники с теми же адресами", "Get", "Ban");
		add(out, src, "/warnp <ник> <причина>", "варн (3 варна = бан 10 дней)", "Ban");
		add(out, src, "/unwarnp <ник> <причина>", "снять варн", "Ban");
		add(out, src, "/jailp <ник> <минуты> <причина>", "тюрьма", "Jail");
		add(out, src, "/unjailp <ник> <причина>", "освободить", "Jail");
		add(out, src, "/akickp <ник> <причина>", "кик", "Ban");
		add(out, src, "/akill <ник>", "убить игрока", "Игрок");
		add(out, src, "/fz <ник> [1|2|3]", "заморозка: 1 — без движения, 2 — и без камеры, 3 — только чат", "Игрок", "Ban");
		add(out, src, "/unfz <ник>", "разморозить", "Игрок", "Ban");
		add(out, src, "/cw warn <ник> <предложение>", "устное предупреждение (в /punish и /get)", "Ban");
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.GA)) {
			add(out, "/bl [страница]", "чёрный список имён CW Expedition (с пагинацией)", "Сервер");
			add(out, "/bl add <ник>", "заблокировать существующий аккаунт: отключить, очистить данные после резервной копии", "Сервер");
			add(out, "/bl del <ник>", "снять блокировку аккаунта (данные не восстанавливаются)", "Сервер");
			add(out, "/bl reserve <ник>", "зарезервировать ещё не созданное имя", "Сервер");
			add(out, "/bl free <ник>", "снять резерв имени", "Сервер");
			if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.DEV)) add(out, "/delaccinfo yes true", "разрешить удаление данных аккаунтов из чёрного списка Expedition (необратимо, с резервной копией)", "Сервер");
			add(out, "/delstory <ник> <причина>", "полный сброс сюжетного профиля (основной профиль не трогается); пример: /delstory Aluya Тестовый сброс", "Сервер");
		}
		add(out, src, "/cw server stop <причина>", "остановка сервера через 10 сек", "Сервер");
		add(out, src, "/cw item <предмет> <true|false> [цель]", "запрет предмета (цель: players|admins|admin <ур.|ник>|player <ник>)", "Сервер");
		add(out, src, "/cw mod <мод> <true|false> [цель]", "запрет мода", "Сервер");
		add(out, src, "/cw command <команда> <true|false> [цель]", "запрет команды", "Сервер");
		add(out, src, "/cw list", "все запреты /cw", "Сервер");
		add(out, src, "/cw skill <ветка> <true|false> [all|admins|players]", "включить или отключить ветку навыков", "Навыки");
		add(out, src, "/cw board <true|false>", "надпись «Common World» над табло", "Сервер");
		add(out, src, "/cw password <true|false>", "система паролей", "Сервер");
		add(out, src, "/cw donate <ссылка>", "ссылка для /donate", "Сервер");
		add(out, src, "/cw event <название> <true|false>", "заблокировать или разблокировать мероприятие", "МП", "Сервер");
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.SENIOR)) {
			add(out, "/cw help|status|diagnose", "CW Expedition, минимум: Старший администратор", "CW Expedition");
			add(out, "/cw story status|flags <player>", "диагностика сюжета, минимум: Старший администратор", "CW Expedition");
			add(out, "/cw admin player|tp|bring|inventory view <player>", "помощь игрокам с аудитом, минимум: Старший администратор", "CW Expedition");
		}
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.ZGA)) {
			add(out, "/cw story start|set-stage|complete|reset <player> ...", "изменение прогресса, минимум: ЗГА", "CW Expedition");
			add(out, "/cw admin inventory give|take ... confirm", "изменение инвентаря, минимум: ЗГА", "CW Expedition");
			add(out, "/cw admin xp give ... confirm", "выдача XP, минимум: ЗГА", "CW Expedition");
		}
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.TECH)) {
			add(out, "/cw reload", "перечитать ресурсы, минимум: Тех. Администратор", "CW Expedition");
			add(out, "/cw story test enter|exit|status|set-stage|reset", "изолированный тест, минимум: Тех. Администратор", "CW Expedition");
		}
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.GA)) {
			add(out, "/cw tools give <all|selection|player_marker|npc_marker|story_tablet|npc_path|template_tp|test_tp|npc_pose|builder_marker> [player]", "инструменты разработки; доступны, пока разработчик в сети", "CW Expedition");
			add(out, "/cw template enter|leave", "вход/выход из строительного void-измерения", "CW Expedition");
			add(out, "/1, /2, /afill <blockstate>", "зафиксировать точки маркера и заполнить область", "CW Expedition");
			add(out, "/afill preview|confirm|cancel|clear|status", "проверка и управление пакетным заполнением", "CW Expedition");
			add(out, "/cw wand|pos1|pos2|point|selection", "кисточка и 3D-выделение, минимум: ГА", "CW Expedition");
			add(out, "/cw schem save|list|info|load|preview|paste|cancel", "Sponge .schem, минимум: ГА", "CW Expedition");
			add(out, "/cw npc create <name> <skin>; pose; animation; skin; dialogue", "NPC, постоянные скины, позы и анимации, минимум: ГА", "CW Expedition");
		}
		if (CommandUtil.hasRank(src, ru.admincontrol.data.AdminRanks.DEV)) {
			add(out, "/cw template marker", "выдать Маркер строителя; ПКМ по блоку, затем /1 или /2", "CW Expedition", "Разработчик");
			add(out, "/cw template invite <игрок>", "пригласить игрока на стройку на 2 минуты", "CW Expedition", "Разработчик");
			add(out, "/cw template tp <игрок>", "принудительно перенести игрока на стройку", "CW Expedition", "Разработчик");
			add(out, "/cw template kick <игрок>", "убрать игрока со стройки и вернуть в прежнюю точку", "CW Expedition", "Разработчик");
		}
		add(out, src, "/spawn <ник> <причина>", "на точку возрождения игрока", "ТП");
		add(out, src, "/get <ник>", "статистика и IP", "Get");
		add(out, src, "/getach <ник>", "достижения игрока", "Get");
		add(out, src, "/get clan <клан>", "логи клана: казна, склад, уровни, чат (с уровня Админ)", "Get", "Клан", "Логи");
		add(out, src, "/get clan info <клан>", "склад клана", "Get", "Клан");
		add(out, src, "/getclan <клан>", "состояние клана", "Get", "Клан");
		add(out, src, "/congetach <ник>", "достижения в чат / консоль", "Get", "YourControl");
		add(out, src, "/aplayer <ник>", "действия с игроком", "Игрок");
		add(out, src, "/askills <ник>", "дерево навыков игрока (только просмотр)", "Навыки", "Get");
		add(out, src, "/askillinfo <ник>", "баланс коинов и прогресс заданий навыков в чат", "Навыки", "Get");
		add(out, src, "/askilllist", "игроки с коинами и купленными навыками", "Навыки", "Get");
		add(out, src, "/askillgive <ник> <валюта> <кол-во>", "выдать коины навыков (life, ore, path, fight, shadow, mind)", "Навыки");
		add(out, src, "/askilltake <ник> <валюта> <кол-во>", "забрать коины навыков (не больше, чем есть)", "Навыки");
		add(out, src, "/congetclan <клан>", "клан в чат / консоль (YourControl)", "Get", "Клан", "YourControl");
		add(out, src, "/getclanwh <клан>", "склад клана", "Get", "Клан");
		add(out, src, "/congetclanwh <клан>", "склад клана в чат / консоль (YourControl)", "Get", "Клан", "YourControl");
		add(out, src, "/resetclan <клан>", "обнулить клан", "Клан");
		add(out, src, "/conget <ник>", "статистика в чат / консоль", "Get", "YourControl");
		add(out, src, "/amsg <ник> <текст>", "личное сообщение", "Игрок");
		add(out, src, "/aeat <ник> <0-20>", "сытость", "Игрок");
		add(out, src, "/adel <ник> <предмет> <кол-во|all>", "забрать предметы", "Инвентарь");
		add(out, src, "/getinv <ник>", "инвентарь", "Get", "Инвентарь");
		add(out, src, "/congetinv <ник>", "инвентарь в чат / консоль", "Get", "Инвентарь", "YourControl");
		add(out, src, "/punish <ник>", "история наказаний", "Ban", "Get");
		add(out, src, "/conpunish <ник>", "наказания в чат / консоль", "Ban", "Get", "YourControl");
		add(out, src, "/ahp <ник> <0-20>", "здоровье", "Игрок");
		add(out, src, "/afill <от> <до> <блок>", "заполнить область", "Мир");
		add(out, src, "/pmode <true|false>", "читы игроков", "Режим");
		add(out, src, "/amode <true|false>", "читы администрации", "Режим");
		add(out, src, "/nmode <ник> <true|false>", "включить или выключить чит-команды игроку", "Режим", "Игрок");
		add(out, src, "/acommand <команда> <off|on>", "запрет команды", "Сервер");
		add(out, src, "/chcommands", "выключенные команды", "Сервер");
		add(out, src, "/conchcommands", "выключенные команды в чат / консоль", "Сервер", "YourControl");
		add(out, src, "/abalance <ник>", "баланс игрока", "WC", "Get");
		add(out, src, "/conabalance <ник>", "баланс в чат / консоль (YourControl)", "WC", "Get", "YourControl");
		add(out, src, "/setluck <ник> <значение>", "удача игрока", "WC");
		add(out, src, "/aclear <ник>", "очистить инвентарь", "Инвентарь");
		add(out, src, "/setgamemode <ник> <режим>", "режим игры игроку", "Режим");
		add(out, src, "/acreative", "креатив себе", "Режим");
		add(out, src, "/asurvival", "выживание себе", "Режим");
		add(out, src, "/aspectator", "наблюдатель себе", "Режим");
		add(out, src, "/aadventure", "приключение себе", "Режим");
		add(out, src, "/agm [ник]", "режим бога себе или NPC/игроку", "Режим", "NPC");
		add(out, src, "/event", "мероприятие: запуск, а во время МП — кик, приз, перезапуск, ТП, завершение (организатор и куратор+)", "МП");
		add(out, src, "/box", "объявить бокс у глобального спавна", "МП");
		add(out, src, "/unbox", "отменить бокс (до спавна — вы/тех+, после — ГА+)", "МП");
		add(out, src, "/plcheck", "кто на МП и на тренировке", "МП", "Get");
		add(out, src, "/scheck", "статус МП, тренировок и бокса", "МП", "Get");
		add(out, src, "/fullstop", "стоп МП/тренировок/бокса + пауза (см. /dserver)", "МП", "Сервер");
		add(out, src, "/eventstop", "остановить текущее МП любого организатора", "МП");
		add(out, src, "/offplayer <ник>", "снять игрока с МП или тренировки", "МП");
		add(out, src, "/setcommandadmin <команда> <true|false> <ник>", "вкл/выкл команду одному админу", "Админы");
		add(out, src, "/adminlist", "все админы, включая оффлайн", "Админы", "Get");
		add(out, src, "/allevent", "мероприятие, вы тоже участник", "МП");
		add(out, src, "/ekick <ник>", "кикнуть участника с вашего МП", "МП");
		add(out, src, "/eventkick <ник>", "то же, что /ekick", "МП");
		add(out, src, "/cwarok [id]", "разрешить битву кланов", "Клан");
		add(out, src, "/cwardeny [id]", "отклонить битву кланов", "Клан");
		add(out, src, "/cwarstop", "остановить битву кланов", "Клан");
		add(out, src, "/acoins <ник>", "история World coin игрока", "WC", "Get");
		add(out, src, "/conacoins <ник>", "история World coin в чат / консоль", "WC", "Get", "YourControl");
		add(out, src, "/atime <set|add|query>", "время мира", "Мир");
		add(out, src, "/aweather <clear|rain|thunder>", "погода", "Мир");
		add(out, src, "/agive <ник> <предмет> <кол-во>", "выдать предмет", "Инвентарь");
		add(out, src, "/permbanp <ник> <причина>", "перманентный бан", "Ban");
		add(out, src, "/unpermbanp <ник> <причина>", "снять перманентный бан", "Ban");
		if (DataManager.get() == null || !DataManager.get().settings().addAdminLockedToStaff
				|| CommandUtil.canUse(src, "add_admin")) {
			add(out, src, "/add_admin <ник> [уровень]", "назначить администратора (пока команда открыта — можно сразу ГА)", "Админы");
		}
		add(out, src, "/set_admin <ник> <уровень>", "изменить уровень (4.5 — блогер)", "Админы");
		add(out, src, "/add_blogger <ник>", "выдать блогерскую админку (уровень 4.5)", "Админы");
		add(out, src, "/un_admin <ник> <причина>", "снять администратора", "Админы");
		add(out, src, "/addnpc <ник>", "создать NPC перед собой", "NPC");
		add(out, src, "/npclist", "список NPC", "NPC");
		add(out, src, "/connpclist", "список NPC в чат / консоль (YourControl)", "NPC", "YourControl");
		add(out, src, "/npchere <ник>", "переместить NPC к себе", "NPC");
		add(out, src, "/delnpc <ник>", "удалить NPC", "NPC");
		add(out, src, "/npc <ник> <команда|текст>", "команда или чат от имени NPC", "NPC");
		add(out, src, "/control <ник>", "играть за NPC: его WC, сила, HP, опыт, инвентарь, эффекты, дерево навыков, достижения (всё отдельно у NPC)", "NPC");
		add(out, src, "/uncontrol", "выйти из NPC", "NPC");
		add(out, src, "/setcasino", "казино (только /dserver, разраб)", "WC");
		add(out, src, "/tevent", "тестовое МП только для себя", "МП");
		add(out, src, "/setworldspawn", "общая точка появления мира", "Мир");
		add(out, src, "/atsetspawn <ник>", "установить игроку точку появления в вашем текущем месте (ГА/тех+)", "ТП", "Админы");
		add(out, src, "/all_kick <причина>", "кик всех", "Сервер", "Ban");
		add(out, src, "/aserver", "панель сервера (куратор+: авто-МП и авто-бокс)", "Сервер");
		add(out, src, "/stop_server", "выключить сервер", "Сервер");
		add(out, src, "/start_server", "открыть сервер", "Сервер");
		add(out, src, "/add_wight <ник>", "добавить в whitelist", "Сервер");
		add(out, src, "/del_wight <ник>", "убрать из whitelist", "Сервер");
		add(out, src, "/check_wight", "показать whitelist", "Сервер");
		add(out, src, "/banagm <ник>", "запретить AGM", "Режим");
		add(out, src, "/unbanagm <ник>", "разрешить AGM", "Режим");
		add(out, src, "/unagm", "снять AGM", "Режим");
		add(out, src, "/plog <ник> [поиск]", "логи игрока, поиск по времени/действию/тексту", "Логи");
		add(out, src, "/koto <ник>", "разбор, почему античит наказал (автокликер / x-ray)", "Логи", "Anti-Cheat");
		add(out, src, "/conkoto <ник>", "разбор античита в чат / консоль", "Логи", "YourControl", "Anti-Cheat");
		add(out, src, "/conplog <ник> [поиск]", "логи игрока в чат / консоль", "Логи", "YourControl");
		add(out, src, "/alog <ник> [поиск]", "логи администратора, поиск по времени/действию/тексту", "Логи");
		add(out, src, "/conalog <ник> [поиск]", "логи администратора в чат / консоль", "Логи", "YourControl");
		add(out, src, "/clearlog <ник|all>", "удалить логи игрока", "Логи");
		add(out, src, "/plogall", "общий лог всех игроков", "Логи");
		add(out, src, "/alogall", "общий лог всех администраторов", "Логи");
		add(out, src, "/plogsearch <запрос>", "поиск по логам всех игроков", "Логи");
		add(out, src, "/alogsearch <запрос>", "поиск по логам всех администраторов", "Логи");
		add(out, src, "/aweatherlog", "журнал погоды и ударов молний", "Логи", "Мир");
		add(out, src, "/aeventlog", "журнал МП: кто, когда, где, итог", "Логи", "МП");
		add(out, src, "/atraininglog", "журнал тренировок", "Логи", "МП");
		add(out, src, "/aboxlog", "журнал боксов", "Логи", "МП");
		add(out, src, "/clearweatherlog", "очистить журнал погоды", "Логи");
		add(out, src, "/cleareventlog", "очистить журнал МП", "Логи");
		add(out, src, "/cleartraininglog", "очистить журнал тренировок", "Логи");
		add(out, src, "/clearboxlog", "очистить журнал боксов", "Логи");
		add(out, src, "/astats", "ваша админ-статистика по дням", "Админы", "Get");
		add(out, src, "/admstats <ник>", "статистика администратора по дням", "Админы", "Get");
		add(out, src, "/aprivate", "система приватов", "Дом");
		add(out, src, "/aprivate info <ник>", "приват игрока", "Дом", "Get");
		add(out, src, "/aprivate list", "уровни приватов и статистика", "Дом");
		add(out, src, "/aprivate list players", "игроки с приватами", "Дом");
		add(out, src, "/aprivate remove <ник>", "снять приват игрока", "Дом");
		add(out, src, "/aprivate restore <ник>", "восстановить приват, снятый администратором", "Дом");
		add(out, src, "/setprice", "цены продажи", "WC");
		add(out, src, "/setcheck", "какие логи слать в чат", "Логи");
		add(out, src, "/create_rules", "создать rules.txt", "Сервер");
		add(out, src, "/key_rules", "зашифровать правила", "Сервер");
		add(out, src, "/unkey_rules", "расшифровать правила", "Сервер");
		add(out, src, "/addcoin <ник> <кол-во>", "выдать World coin", "WC");
		add(out, src, "/uncoin <ник> <кол-во>", "забрать World coin", "WC");
		add(out, src, "/setpower <ник> <сила>", "установить силу", "WC");
		add(out, src, "/unprivate <ник>", "снять приват дома", "Дом");
		add(out, src, "/acasino <ник> <true|false>", "казино игроку", "WC");
		add(out, src, "/aclearlog <ник|all>", "удалить админ-логи", "Логи");
		add(out, src, "/setnick <ник1> <ник2> <причина>", "передать инвентарь, опыт, WC, силу и удачу", "Игрок");
		add(out, src, "/areset <ник> <причина>", "обнулить аккаунт (наказания сохраняются)", "Игрок");
		add(out, src, "/aranks", "настройка уровней админов", "Админы");
		add(out, src, "/aroles", "создать свой уровень администрации", "Админы");
		add(out, src, "/nickfilter", "запрет символов в никах, белый список", "Админы");
		add(out, src, "/dserver", "полный контроль сервера", "Сервер");
		add(out, src, "/devent", "МП: настройки по умолчанию, набор, ломаемые блоки, свои МП, правка карты и черновики", "МП");
		add(out, src, "/dbuild <ник>", "позвать игрока или админа на стройку карты МП (креатив, пустой инвентарь)", "МП");
		add(out, src, "/dbuildkick <ник>", "убрать помощника со стройки карты МП (вещи и место вернутся)", "МП");
		add(out, src, "/all_clear", "очистить данные", "Сервер");
		add(out, src, "/all_unadm", "снять всех администраторов", "Админы");
		add(out, src, "/all_permban", "перманентный бан всех", "Ban");
		add(out, src, "/all_unpermban", "снять перманентные баны", "Ban");
		add(out, src, "/kill_server", "убить процесс сервера", "Сервер");
		add(out, src, "/revive_server", "восстановить сервер", "Сервер");
		add(out, src, "/undev", "снять с себя разработчика, стать ГА", "Админы");
		return out;
	}

	public static List<Entry> playerEntries() {
		List<Entry> out = new ArrayList<>();
		boolean cheats = DataManager.get() != null && DataManager.get().settings().playerCheats;
		add(out, "/phelp [поиск|фильтр]", "этот список, поиск и фильтр", "Справка");
		add(out, "/conphelp [поиск|фильтр]", "этот список в чат / консоль", "Справка", "YourControl");
		add(out, "/admins", "администрация в сети", "Get");
		add(out, "/conadmins", "админы в чат / консоль (YourControl)", "Get", "YourControl");
		add(out, "/players", "игроки в сети", "Get");
		add(out, "/conplayers", "игроки в чат / консоль (YourControl)", "Get", "YourControl");
		add(out, "/mystats", "ваша статистика", "Get");
		add(out, "/addhome", "добавить метку дома (максимум 1)", "Дом");
		add(out, "/delhome", "удалить метку дома", "Дом");
		add(out, "/sethome", "заменить метку дома", "Дом");
		if (cheats) {
			add(out, "/thome", "телепорт домой", "Дом", "ТП");
			add(out, "/tpi <ник|метка>", "телепорт к игроку или метке", "ТП");
			add(out, "/ttp <ник1> <ник2>", "телепорт игрока к игроку", "ТП");
		}
		add(out, "/home", "настройки дома и подсветка привата", "Дом");
		add(out, "/addp <название>", "добавить личную метку", "ТП");
		add(out, "/delp <название>", "удалить личную метку", "ТП");
		add(out, "/listp", "список меток", "ТП");
		add(out, "/msg <ник> <текст>", "личное сообщение", "Чат");
		add(out, "/tpa <ник>", "запрос телепорта к игроку (к админам 2+ уровня нельзя)", "ТП");
		add(out, "/donate", "ссылка на донат", "WC");
		add(out, "/tpahere <ник>", "запрос телепорта игрока к себе", "ТП");
		add(out, "/tpy", "принять запрос телепорта (коротко)", "ТП");
		add(out, "/ok", "то же, что /tpy", "ТП");
		add(out, "/tpn", "отклонить запрос телепорта (коротко)", "ТП");
		add(out, "/tpaccept", "принять запрос (длинный вариант /tpy)", "ТП");
		add(out, "/tpdeny", "отклонить запрос (длинный вариант /tpn)", "ТП");
		add(out, "/shop", "магазин за World coin", "WC");
		add(out, "/skills", "дерево навыков: ветки, коины, задания (рюкзак, верстак, автопечь — тут)", "Навыки");
		add(out, "/clan", "управление кланом", "Клан");
		add(out, "/clanwar <клан>", "вызвать клан на битву (решают админы)", "Клан");
		add(out, "/clanwar take [сумма]", "забрать WC из казны врага после поломки ядра", "Клан");
		add(out, "/cwar <клан>", "то же, что /clanwar", "Клан");
		add(out, "/top", "недельный топ", "Get");
		add(out, "/clanetop", "топ кланов за победы в МП и битвах", "Get", "Клан");
		add(out, "/conclanetop", "топ кланов в чат / консоль", "Get", "Клан", "YourControl");
		add(out, "/contop", "недельный топ в чат / консоль (YourControl)", "Get", "YourControl");
		add(out, "/gevent", "зайти на мероприятие", "МП");
		add(out, "/cevent", "уйти с мероприятия (организатор — стоп МП)", "МП");
		add(out, "/ptime", "дата и точное время (видно поверх любого окна)", "Мир");
		add(out, "/sell", "продать предметы за World coin (не в МП, не на тренировке, не в битве кланов)", "WC");
		add(out, "/auction", "аукцион игроков (не в МП, не на тренировке, не в битве кланов)", "WC");
		add(out, "/balance", "ваш баланс World coin", "WC", "Get");
		add(out, "/coins", "счёт, платежи игрокам и в клан, история World coin", "WC");
		add(out, "/training", "тренировка с мобами", "Тренировка");
		add(out, "/off", "выйти из тренировки", "Тренировка");
		add(out, "/board", "скрыть или показать табло", "Get");
		add(out, "/pay <ник> <кол-во>", "передать World coin", "WC");
		add(out, "/password <пароль> <повтор>", "создать аккаунт (только на входе)", "Аккаунт");
		add(out, "/l <ник> <пароль>", "войти в аккаунт (только на входе)", "Аккаунт");
		add(out, "/setpassword", "сменить пароль", "Аккаунт");
		add(out, "/fam <текст>", "чат клана", "Клан", "Чат");
		add(out, "/rules", "правила мира", "Справка");
		add(out, "/conrules", "правила в чат / консоль", "Справка", "YourControl");
		add(out, "/setspawn", "точка возрождения", "Дом");
		if (DataManager.get() != null && !DataManager.get().hasAnyAdmin()) {
			add(out, "/add_admin <ник> [уровень]", "первый администратор сразу становится ГА (уровень 8)", "Админы");
		}
		return out;
	}

	private static void add(List<Entry> out, ServerCommandSource src, String command, String desc, String... filters) {
		String[] parts = (command.startsWith("/") ? command.substring(1) : command).split("\\s+");
		String id = parts[0];
		if ("cw".equals(id) && parts.length > 1) {
			if ("npc".equals(parts[1])) {
				if (CommandUtil.canUseCw(src)) out.add(new Entry(command, desc, filters));
				return;
			}
			String sub = switch (parts[1]) {
				case "server" -> "cwserverstop";
				case "list" -> "cwitem";
				default -> "cw" + parts[1];
			};
			if (!CommandUtil.canUse(src, "cw") || !CommandUtil.canUse(src, sub)) {
				return;
			}
		}
		if ("aprivate".equals(id) && parts.length > 1 && ("remove".equals(parts[1]) || "restore".equals(parts[1]))
				&& !CommandUtil.canUse(src, "aprivate" + parts[1])) {
			return;
		}
		if (!CommandUtil.canUse(src, id)) {
			return;
		}
		out.add(new Entry(command, desc, filters));
	}

	private static void add(List<Entry> out, String command, String desc, String... filters) {
		out.add(new Entry(command, desc, filters));
	}
}
