package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActivityCheck {
	private ActivityCheck() {
	}

	public static List<String> players(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		lines.add("=== На МП ===");
		if (!EventService.active()) {
			lines.add("Сейчас МП нет.");
		} else {
			EventService.State st = EventService.current();
			lines.add("«" + st.name + "»  (" + EventService.phaseLabel() + ")");
			List<String> nicks = eventNicks(server);
			if (nicks.isEmpty()) {
				lines.add("Игроков нет.");
			} else {
				for (String nick : nicks) {
					lines.add("• " + nick);
				}
			}
		}
		lines.add("");
		lines.add("=== На тренировке ===");
		List<String> train = TrainingService.listLines(server);
		if (train.isEmpty()) {
			lines.add("Никого нет.");
		} else {
			lines.addAll(train);
		}
		return lines;
	}

	public static List<String> status(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		if (!EventService.active()) {
			lines.add("МП: не идёт.");
		} else {
			EventService.State st = EventService.current();
			lines.add("МП: «" + st.name + "», стадия «" + EventService.phaseLabel() + "», игроков "
					+ EventService.memberCount() + ".");
			for (String nick : eventNicks(server)) {
				lines.add("  • " + nick);
			}
		}
		List<String> train = TrainingService.listLines(server);
		if (train.isEmpty()) {
			lines.add("Тренировки: никого нет.");
		} else {
			lines.add("Тренировки (" + train.size() + "):");
			for (String line : train) {
				lines.add("  " + line);
			}
		}
		if (!BoxEventService.active()) {
			lines.add("Бокс: не идёт.");
		} else {
			lines.add("Бокс: " + BoxEventService.statusLine());
		}
		if (ClanWarService.active()) {
			lines.add("Битва кланов: идёт.");
		} else {
			lines.add("Битва кланов: не идёт.");
		}
		if (FullStopLock.blocked()) {
			lines.add("Пауза после /fullstop: " + FullStopLock.denyMessage());
		}
		return lines;
	}

	public static List<String> fullStop(MinecraftServer server) {
		List<String> done = new ArrayList<>();
		if (EventService.active()) {
			EventService.stop(server, "остановлено: полная остановка активностей");
			done.add("МП остановлено.");
		}
		if (EventMapEditor.active()) {
			EventMapEditor.stopAll(server, "полная остановка активностей");
			done.add("Правка карты МП прервана.");
		}
		if (!TrainingService.listLines(server).isEmpty()) {
			TrainingService.stopAll(server);
			done.add("Тренировки сняты.");
		}
		if (BoxEventService.active()) {
			BoxEventService.forceCancel(server);
			done.add("Бокс снят.");
		}
		if (ClanWarService.active()) {
			ClanWarService.stop(server, "/fullstop");
			done.add("Битва кланов остановлена.");
		}
		if (done.isEmpty()) {
			done.add("Нечего останавливать: МП, тренировок и бокса нет.");
		}
		FullStopLock.arm();
		int cool = FullStopLock.cooldownSec();
		if (cool == 0) {
			done.add("Пауза МП/тренировок: выкл.");
		} else if (cool < 0) {
			done.add("Пауза МП/тренировок: ∞ (снять в /dserver).");
		} else {
			done.add("Пауза МП/тренировок: " + FullStopLock.formatSec(cool) + ".");
		}
		return done;
	}

	private static List<String> eventNicks(MinecraftServer server) {
		List<String> nicks = new ArrayList<>();
		if (!EventService.active() || server == null) {
			return nicks;
		}
		EventService.State st = EventService.current();
		for (UUID id : st.members.keySet()) {
			String nick = nickOf(server, id);
			if (!nick.isBlank()) {
				nicks.add(nick);
			}
		}
		if (st.host != null && !st.members.containsKey(st.host)) {
			String host = nickOf(server, st.host);
			if (!host.isBlank()) {
				nicks.add(host + " (организатор)");
			}
		}
		return nicks;
	}

	private static String nickOf(MinecraftServer server, UUID id) {
		if (id == null) {
			return "";
		}
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		if (DataManager.get() != null) {
			for (PlayerRecord record : DataManager.get().records()) {
				if (record != null && id.toString().equalsIgnoreCase(record.uuid)) {
					return record.nickname;
				}
			}
		}
		return "";
	}
}
