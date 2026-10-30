package ru.admincontrol.client;

public final class ActivityLock {
	private static boolean locked;

	private ActivityLock() {
	}

	public static void set(boolean value) {
		locked = value;
	}

	public static boolean active() {
		return locked;
	}
}
