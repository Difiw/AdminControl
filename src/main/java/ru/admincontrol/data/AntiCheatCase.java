package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.List;

public class AntiCheatCase {
	public long at;
	public String cheat = "";
	public String verdict = "";
	public String trigger = "";
	public String world = "";
	public String pos = "";
	public String mode = "";
	public String hand = "";
	public int cps;
	public int limit;
	public int strikes;
	public List<String> why = new ArrayList<>();
}
