package ru.admincontrol.util;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

public final class GodModeService {
	private GodModeService() {
	}

	public static boolean toggle(ServerPlayerEntity player) {
		if (ru.admincontrol.cw.CwStoryRules.denyFeature(player, "AGM")) { forceOff(player, false); return false; }
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (record.agmBanned) {
			return false;
		}
		record.agmEnabled = !record.agmEnabled;
		DataManager.get().saveAll();
		return true;
	}

	public static boolean enabled(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (ru.admincontrol.cw.CwStoryRules.inside(player)) return false;
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		return data.getOrCreate(player).agmEnabled;
	}

	public static boolean absorb(ServerPlayerEntity player, DamageSource source) {
		if (!enabled(player)) {
			return false;
		}
		player.setHealth(player.getMaxHealth());
		player.extinguish();
		player.fallDistance = 0;
		return source != null;
	}

	public static void disable(ServerPlayerEntity player) {
		forceOff(player, true);
	}

	/** Вход на МП/тренировку: выключить AGM и запомнить, что его надо вернуть после выхода. */
	public static void suspend(ServerPlayerEntity player, String during) {
		if (player == null) {
			return;
		}
		DataManager data = DataManager.get();
		if (data != null) {
			PlayerRecord record = data.getOrCreate(player);
			if (record.agmEnabled) {
				record.agmRestore = true;
				Chat.send(player, Chat.yellow("AGM выключен на время " + during + " и включится снова после выхода."));
			}
		}
		forceOff(player, true);
	}

	/** Выход с МП/тренировки: вернуть AGM, если он был включён до входа. */
	public static void resume(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		if (player == null || data == null) {
			return;
		}
		PlayerRecord record = data.getOrCreate(player);
		if (!record.agmRestore) {
			return;
		}
		record.agmRestore = false;
		if (!record.agmBanned) {
			record.agmEnabled = true;
			Chat.send(player, Chat.green("AGM снова включён (был включён до входа)."));
		}
		data.saveAll();
	}

	public static void forceOff(ServerPlayerEntity player, boolean save) {
		if (player == null) {
			return;
		}
		if (DataManager.get() != null) {
			PlayerRecord record = DataManager.get().getOrCreate(player);
			record.agmEnabled = false;
			if (save) {
				DataManager.get().saveAll();
			}
		}
		player.setInvulnerable(false);
		boolean flying = player.getAbilities().flying;
		boolean inv = player.getAbilities().invulnerable;
		player.getAbilities().invulnerable = false;
		player.getAbilities().flying = false;
		if (save || flying || inv) {
			player.sendAbilitiesUpdate();
		}
	}

	public static void setBanned(PlayerRecord record, boolean banned) {
		if (record == null) {
			return;
		}
		record.agmBanned = banned;
		if (banned) {
			record.agmEnabled = false;
			record.agmRestore = false;
		}
		if (DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}
}
