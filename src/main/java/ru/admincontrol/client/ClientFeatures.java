package ru.admincontrol.client;

import java.util.ArrayList;
import java.util.List;

/** Купленные кнопки инвентаря в порядке покупки и уровень заморозки. */
public final class ClientFeatures {
	private static final List<String> ORDER = new ArrayList<>();
	private static int freeze;

	private ClientFeatures() {
	}

	public static synchronized void setOrder(List<String> ids) {
		ORDER.clear();
		if (ids != null) {
			ORDER.addAll(ids);
		}
	}

	public static synchronized List<String> order() {
		return new ArrayList<>(ORDER);
	}

	public static void setFreeze(int level) {
		freeze = Math.max(0, Math.min(3, level));
	}

	public static int freeze() {
		return freeze;
	}

	public static void reset() {
		setOrder(null);
		freeze = 0;
	}
}
