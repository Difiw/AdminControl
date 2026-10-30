package ru.admincontrol.data;

/**
 * Активный бан для быстрого lookup при входе (bans.json).
 */
public class BanEntry {
	public String uuid = "";
	public String nickname = "";
	public boolean jail;
	/** Устаревшее поле: варн больше не блокирует вход. */
	public boolean warn;
	public boolean permanent;
	public String reason = "";
	public String adminNick = "";
	public long createdAt;
	public Long expiresAt;
	public String recordId = "";
	public boolean fromAllPermab;
	/** Бан выдан фильтром ников за запрещённые символы — можно снять автоматически. */
	public boolean nickFilter;
}
