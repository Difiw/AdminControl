package ru.admincontrol.util;

import ru.admincontrol.data.ClanRecord;

/** Территория дома клана. Физические постройки не ставятся. */
public final class ClanBuildings {
	private ClanBuildings() {
	}

	public static void rebuildAll(net.minecraft.server.world.ServerWorld world, ClanRecord clan) {
	}

	public static int territorySize(ClanRecord clan) {
		if (clan == null || !clan.homeBought) {
			return 9;
		}
		return Math.min(512, 16 + Math.max(1, clan.hallLevel) * 8);
	}
}
