package ru.admincontrol.data;

/** Наказание античита за одно нарушение: вид (AntiCheatRules.NONE…PERMBAN) и срок. */
public final class AntiCheatRule {
	public int kind;
	/** Срок бана, дни. */
	public int days = 1;
	/** Срок jail, минуты. */
	public int minutes = 5;

	public AntiCheatRule() {
	}

	public AntiCheatRule(int kind, int days, int minutes) {
		this.kind = kind;
		this.days = days;
		this.minutes = minutes;
	}
}
