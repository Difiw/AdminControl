package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

public final class AutoEventService {
	private AutoEventService() {
	}

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		ModSettings s = PlayFeatures.s();
		tickAutoEvent(server, s);
		tickAutoBox(server, s);
	}

	private static void tickAutoEvent(MinecraftServer server, ModSettings s) {
		if (!s.autoEventEnabled) {
			return;
		}
		if (EventService.active() || ClanWarService.active() || EventMapEditor.active()) {
			return;
		}
		if (FullStopLock.blocked()) {
			return;
		}
		int online = 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!NpcService.isNpc(player) && !AuthService.pending(player)) {
				online++;
			}
		}
		if (online < EventService.MIN_PLAYERS) {
			return;
		}
		boolean cycle = s.autoEventType < 0 || s.autoEventType >= EventService.TYPES.length;
		int builtInMask = (1 << EventService.TYPES.length) - 1;
		if (cycle ? Integer.bitCount(EventService.blockedMask() & builtInMask) >= EventService.TYPES.length
				: EventService.blocked(s.autoEventType)) {
			return;
		}
		long now = System.currentTimeMillis();
		int interval = Math.max(10, s.autoEventMinutes) * 60_000;
		int warn = Math.max(1, s.autoEventWarnMinutes) * 60_000;
		if (s.autoEventNextAt <= 0L) {
			s.autoEventNextAt = now + interval;
			s.autoEventWarned = false;
			DataManager.get().markDirty();
			return;
		}
		long left = s.autoEventNextAt - now;
		if (left <= warn && left > 0 && !s.autoEventWarned) {
			s.autoEventWarned = true;
			Chat.broadcast(server, Chat.gold("Авто-мероприятие через " + s.autoEventWarnMinutes
					+ " мин. Вход: /gevent"));
			DataManager.get().markDirty();
		}
		if (left > 0) {
			return;
		}
		int type = s.autoEventType;
		if (cycle) {
			type = Math.floorMod(s.autoEventCycle++, EventService.TYPES.length);
			for (int n = 0; n < EventService.TYPES.length && EventService.blocked(type); n++) {
				type = Math.floorMod(s.autoEventCycle++, EventService.TYPES.length);
			}
		}
		EventService.startAuto(server, type, Math.max(0, s.autoEventReward));
		s.autoEventNextAt = now + interval;
		s.autoEventWarned = false;
		DataManager.get().markDirty();
	}

	private static void tickAutoBox(MinecraftServer server, ModSettings s) {
		if (!s.autoBoxEnabled || BoxEventService.active()) {
			return;
		}
		long now = System.currentTimeMillis();
		int interval = Math.max(10, s.autoBoxMinutes) * 60_000;
		int warn = Math.max(1, s.boxWarnMinutes) * 60_000;
		if (s.autoBoxNextAt <= 0L) {
			s.autoBoxNextAt = now + interval;
			s.autoBoxWarned = false;
			DataManager.get().markDirty();
			return;
		}
		long left = s.autoBoxNextAt - now;
		if (left <= warn && left > 0 && !s.autoBoxWarned) {
			s.autoBoxWarned = true;
			Chat.broadcast(server, Chat.gold("Авто-бокс через " + s.boxWarnMinutes + " мин."));
			DataManager.get().markDirty();
		}
		if (left > 0) {
			return;
		}
		String error = BoxEventService.start(server, null);
		if (error == null) {
			s.autoBoxNextAt = now + interval;
			s.autoBoxWarned = false;
			DataManager.get().markDirty();
		}
	}
}
