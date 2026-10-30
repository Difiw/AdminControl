package ru.admincontrol.data;

/** Заблокированный IP-адрес (без порта). */
public class IpBan {
	public String ip = "";
	public String reason = "";
	public String adminNick = "";
	/** Ник, по которому был выдан бан (пусто, если банили сам IP). */
	public String sourceNick = "";
	public long createdAt;
}
