package ru.admincontrol.skill;

import net.minecraft.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Быстрый доступ к итогам дерева навыков из миксинов. Серверные значения заполняет сервер из сохранённых данных
 * (для админа, управляющего NPC, — дерево этого NPC), клиентские — пакет от сервера (прыжок и скорость добычи).
 */
public final class SkillEffects {
	private static final Map<UUID, double[]> SERVER = new ConcurrentHashMap<>();
	private static volatile UUID clientSelf;
	private static volatile double clientJump;
	private static volatile double clientMine;
	private static volatile double clientAir;

	private SkillEffects() {
	}

	public static void setServer(UUID uuid, double[] totals) {
		if (uuid != null && totals != null) {
			SERVER.put(uuid, totals);
		}
	}

	public static void clearServer(UUID uuid) {
		if (uuid != null) {
			SERVER.remove(uuid);
		}
	}

	public static void clearAllServer() {
		SERVER.clear();
	}

	public static double server(Entity entity, SkillTree.Stat stat) {
		if (entity == null || stat == null) {
			return 0;
		}
		double[] totals = SERVER.get(entity.getUuid());
		return totals == null || stat.ordinal() >= totals.length ? 0 : totals[stat.ordinal()];
	}

	public static void setClient(UUID self, double jump, double mine, double air) {
		clientSelf = self;
		clientJump = Math.max(0, Math.min(SkillTree.Stat.JUMP.cap, jump));
		clientMine = Math.max(0, Math.min(SkillTree.Stat.MINING.cap, mine));
		clientAir = Math.max(0, Math.min(SkillTree.Stat.AIR.cap, air));
	}

	public static void resetClient() {
		clientSelf = null;
		clientJump = 0;
		clientMine = 0;
		clientAir = 0;
	}

	public static double clientAir(Entity entity) {
		return entity != null && entity.getUuid().equals(clientSelf) ? clientAir : 0;
	}

	public static double clientJump(Entity entity) {
		return entity != null && entity.getUuid().equals(clientSelf) ? clientJump : 0;
	}

	public static double clientMine(Entity entity) {
		return entity != null && entity.getUuid().equals(clientSelf) ? clientMine : 0;
	}
}
