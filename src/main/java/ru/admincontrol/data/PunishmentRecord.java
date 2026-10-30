package ru.admincontrol.data;

import java.util.UUID;

/**
 * Одна запись в истории наказаний игрока. Хранится бессрочно, даже после снятия.
 */
public class PunishmentRecord {
	public String id = UUID.randomUUID().toString();
	public PunishmentType type = PunishmentType.WARN;
	public boolean active = true;
	public String reason = "";
	public String adminNick = "";
	public String adminUuid = "";
	public long createdAt = System.currentTimeMillis();
	public Integer durationDays;
	public Integer durationMinutes;
	public Long expiresAt;

	public boolean isExpired(long now) {
		return expiresAt != null && expiresAt > 0 && now >= expiresAt;
	}

	public String durationLabel() {
		if (type == PunishmentType.PERMABAN) {
			return "навсегда";
		}
		if (type == PunishmentType.JAIL && durationMinutes != null) {
			int minutes = durationMinutes;
			String suffix = minutes == 1 ? "минуту" : (minutes >= 2 && minutes <= 4 ? "минуты" : "минут");
			return minutes + " " + suffix;
		}
		if (durationDays == null) {
			return "—";
		}
		int days = durationDays;
		String suffix = days == 1 ? "день" : (days >= 2 && days <= 4 ? "дня" : "дней");
		return days + " " + suffix;
	}
}
