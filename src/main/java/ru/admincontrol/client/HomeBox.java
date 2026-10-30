package ru.admincontrol.client;

public final class HomeBox {
	public static boolean visible;
	public static int minX;
	public static int minY;
	public static int minZ;
	public static int maxX;
	public static int maxY;
	public static int maxZ;
	public static String dimension = "";

	private HomeBox() {
	}

	public static void clear() {
		visible = false;
	}
}
