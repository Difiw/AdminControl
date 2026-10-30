package ru.admincontrol.data;

public class PlayerWaypoint {
	public String name = "";
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;
	public String dimension = "minecraft:overworld";

	public String coordsLabel() {
		return Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
	}
}
