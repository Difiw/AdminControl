package ru.admincontrol.data;

/** Одно правило /cw item|mod|command. */
public class CwRule {
	/** item | mod | command */
	public String kind = "";
	public String key = "";
	public boolean allow;
	/** all | players | admins | level | nick */
	public String scope = "all";
	/** Уровень (1–9) для scope=level или ник для scope=nick. */
	public String arg = "";
	/** Ранг поставившего; на ранги выше правило не действует. null — старое правило, действует на всех. */
	public Integer by;

	public CwRule() {
	}

	public CwRule(String kind, String key, boolean allow, String scope, String arg) {
		this.kind = kind;
		this.key = key;
		this.allow = allow;
		this.scope = scope;
		this.arg = arg == null ? "" : arg;
	}
}
