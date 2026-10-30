package ru.admincontrol.data;

public enum ActionKind {
	ANTICHEAT("Anti-Cheat"),
	USE_ITEM("Использовал предмет"),
	HIT_BLOCK("Удар по блоку"),
	ATTACK_MOB("Атака моба"),
	ATTACK_PLAYER("Атака игрока"),
	PICKUP("Подобрал предмет"),
	DROP("Выбросил предмет"),
	DAMAGE("Получил урон"),
	CREATIVE_TAKE("Взял из креатива"),
	CREATIVE_DELETE("Удалил из креатива"),
	TELEPORT("Телепорт"),
	COMMAND("Команда"),
	COMMAND_FAIL("Команда не удалась"),
	XP("Опыт"),
	ACHIEVEMENT("Достижение"),
	CRAFT("Крафт"),
	WORKSTATION("Стол (верстак и др.)"),
	HOME("Метка дома"),
	SPAWN("Точка спавна"),
	WAYPOINT("Метки"),
	WORLD("Смена мира"),
	PORTAL("Портал"),
	CHAT("Чат"),
	JOIN("Вход / выход"),
	DEATH("Смерть"),
	SCREEN("Открыл интерфейс"),
	ENCHANT("Зачарование / наковальня"),
	PLACE("Разместил блок"),
	BREAK("Сломал блок"),
	KILL_PEACEFUL("Убил мирного моба"),
	KILL_MONSTER("Убил монстра"),
	CONSUME("Еда / зелье"),
	SLEEP("Сон"),
	RESPAWN("Возрождение"),
	INVENTORY("Инвентарь"),
	FISHING("Рыбалка"),
	CASINO("Казино"),
	ADMIN("Действие админа"),
	MONEY("World coin"),
	POWER("Сила"),
	LUCK("Удача"),
	CLAN("Клан");

	public final String label;

	ActionKind(String label) {
		this.label = label;
	}

	public static ActionKind byOrdinal(int index) {
		ActionKind[] all = values();
		if (index < 0 || index >= all.length) {
			return null;
		}
		return all[index];
	}
}
