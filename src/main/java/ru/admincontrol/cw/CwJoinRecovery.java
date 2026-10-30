package ru.admincontrol.cw;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.AdminControlMod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Восстановление игрока, вошедшего прямо в строительный/сюжетный мир, выполняется не в тик входа, а чуть позже.
 * Перенос в другое измерение в тот же тик, пока клиент ещё принимает первые пакеты чанков, оставлял клиенту чанки
 * чужого измерения: ошибки IndexOutOfBounds при разборе чанков и «дыра» вместо мира (баг-лист, повторы сотнями).
 * Состояние восстановления хранится на диске, поэтому сбой за эти секунды ничего не теряет: оно выполнится при следующем входе.
 */
public final class CwJoinRecovery {
	public static final int DELAY_TICKS = 60;

	private static final class Pending {
		final ServerPlayerEntity player;
		final long due;
		final boolean wasInvulnerable;

		Pending(ServerPlayerEntity player, long due) {
			this.player = player;
			this.due = due;
			this.wasInvulnerable = player.isInvulnerable();
		}
	}

	private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

	private CwJoinRecovery() {
	}

	/** Ставит восстановление в очередь; на время ожидания игрок неуязвим (он стоит там, где вышел). */
	public static synchronized void schedule(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		Pending p = new Pending(player, player.getServer().getTicks() + DELAY_TICKS);
		PENDING.put(player.getUuid(), p);
		player.setInvulnerable(true);
	}

	/** Игрок вышел до выполнения восстановления: возвращаем флаг неуязвимости, чтобы он не попал в сохранение. */
	public static synchronized void release(ServerPlayerEntity player) {
		Pending p = player == null ? null : PENDING.remove(player.getUuid());
		if (p != null) {
			player.setInvulnerable(p.wasInvulnerable);
		}
	}

	public static void tick(MinecraftServer server) {
		java.util.List<Pending> ready = new java.util.ArrayList<>();
		synchronized (CwJoinRecovery.class) {
			if (PENDING.isEmpty()) {
				return;
			}
			Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
			while (it.hasNext()) {
				Pending p = it.next().getValue();
				if (p.player.isRemoved() || p.player.networkHandler == null || server.getPlayerManager().getPlayer(p.player.getUuid()) != p.player) {
					it.remove(); // игрок вышел: его профиль сохранит обработчик выхода
					continue;
				}
				if (server.getTicks() >= p.due) {
					ready.add(p);
					it.remove();
				}
			}
		}
		for (Pending p : ready) {
			ServerPlayerEntity player = p.player;
			try {
				try {
					CwStoryWorldService.onJoin(player);
				} catch (RuntimeException e) {
					AdminControlMod.LOGGER.error("CW story join recovery failed player={}", player.getUuidAsString(), e);
				}
				try {
					CwBuilderWorld.recoverOnJoin(player);
				} catch (RuntimeException e) {
					AdminControlMod.LOGGER.error("CW builder join recovery failed player={}", player.getUuidAsString(), e);
				}
			} finally {
				player.setInvulnerable(p.wasInvulnerable);
			}
		}
	}
}
