package ru.admincontrol.data;

/** Запись баг-листа: багаюз, найденный античитом, или ошибка сервера. */
public final class BugReport {
	public long id;
	/** Когда баг замечен впервые. */
	public long at;
	/** Когда повторился в последний раз (для ошибок сервера). */
	public long lastAt;
	public int count = 1;
	public String type = "";
	/** Игрок, у которого или из-за которого баг; пусто — у ошибок сервера. */
	public String nick = "";
	public String uuid = "";
	public String dimension = "";
	public int x;
	public int y;
	public int z;
	public boolean hasPos;
	public String description = "";
	/** Подпись ошибки сервера — одинаковые ошибки складываются в одну запись. */
	public String signature = "";
}
