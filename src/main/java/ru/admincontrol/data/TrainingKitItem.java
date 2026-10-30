package ru.admincontrol.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class TrainingKitItem {
	public String id = "minecraft:iron_sword";
	public int count = 1;
	/** -1 авто; 0–8 пояс; 9 рюкзак; 40 левая рука; 100 шлем; 101 нагрудник; 102 поножи; 103 ботинки */
	public int slot = -1;
	public Map<String, Integer> enchants = new LinkedHashMap<>();
	/** NBT предмета без чар (оружие из модов и т.п.); пусто — обычный предмет. */
	public String nbt = "";
}
