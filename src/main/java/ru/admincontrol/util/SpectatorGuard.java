package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;

/** Правила слежки: /sp только за другим игроком; /aspectator — себе. */
public final class SpectatorGuard {
	private SpectatorGuard() {
	}

	public static boolean blockSelfSp(ServerPlayerEntity admin, ServerPlayerEntity target) {
		if (admin == null || target == null) {
			return false;
		}
		if (!admin.getUuid().equals(target.getUuid())) {
			return false;
		}
		Chat.send(admin, Chat.red("/sp нельзя на себе. Для своего наблюдателя: /aspectator."));
		return true;
	}
}
