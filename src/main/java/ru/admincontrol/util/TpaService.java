package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerWaypoint;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {
	private static final Map<UUID, Request> PENDING = new ConcurrentHashMap<>();

	private TpaService() {
	}

	private static final class Request {
		UUID from;
		UUID to;
		boolean here;
		long expireAt;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		PENDING.remove(id);
		PENDING.entrySet().removeIf(e -> id.equals(e.getValue().to) || id.equals(e.getValue().from));
	}

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Request>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Request req = it.next().getValue();
			if (req.expireAt <= now) {
				it.remove();
				ServerPlayerEntity from = server.getPlayerManager().getPlayer(req.from);
				if (from != null) {
					Chat.send(from, Chat.yellow("Запрос телепорта истек."));
				}
			}
		}
	}

	public static String request(ServerPlayerEntity from, ServerPlayerEntity to, boolean here) {
		if (!PlayFeatures.s().tpaEnabled) {
			boolean toAdmin = DataManager.get() != null && DataManager.get().isAdmin(to)
					&& !DataManager.get().isAdmin(from);
			if (!toAdmin) {
				return "Запросы телепорта выключены.";
			}
		}
		if (from == null || to == null) {
			return "Игрок не в сети.";
		}
		if (ClanWarService.jailed(from) || ClanWarService.jailed(to)) {
			return ClanWarService.jailMessage();
		}
		if (from.getUuid().equals(to.getUuid())) {
			return "Нельзя отправить запрос себе.";
		}
		if (!here && !TpGuard.canReach(from, to)) {
			return "Нельзя телепортироваться к администратору более высокого уровня.";
		}
		Request req = new Request();
		req.from = from.getUuid();
		req.to = to.getUuid();
		req.here = here;
		req.expireAt = System.currentTimeMillis() + Math.max(10, PlayFeatures.s().tpaSeconds) * 1000L;
		PENDING.put(to.getUuid(), req);
		String verb = here ? "просит телепортировать вас к себе" : "просит телепортироваться к вам";
		String extra = "";
		if (DataManager.get() != null && DataManager.get().isAdmin(to)
				&& !DataManager.get().isAdmin(from)) {
			extra = " К администратору без вашего разрешения телепорт запрещён.";
		}
		Chat.send(to, Chat.gold(from.getGameProfile().getName() + " " + verb + "." + extra
				+ " Принять: /tpy  Отклонить: /tpn"));
		return null;
	}

	public static String accept(ServerPlayerEntity target) {
		if (target == null || target.getServer() == null) {
			return "Только игрок.";
		}
		Request req = PENDING.remove(target.getUuid());
		if (req == null) {
			return "Нет входящего запроса.";
		}
		ServerPlayerEntity from = target.getServer().getPlayerManager().getPlayer(req.from);
		if (from == null) {
			return "Игрок уже не в сети.";
		}
		if (ClanWarService.jailed(from) || ClanWarService.jailed(target)) {
			return ClanWarService.jailMessage();
		}
		ServerPlayerEntity mover = req.here ? target : from;
		if (FreezeService.frozen(mover)) {
			return "Игрок " + mover.getGameProfile().getName() + " заморожен — телепорт невозможен.";
		}
		if (!req.here && !TpGuard.canReach(from, target)) {
			return "Нельзя телепортироваться к администратору более высокого уровня.";
		}
		if (req.here) {
			Chat.send(from, Chat.green(target.getGameProfile().getName() + " принял запрос — телепорт к вам через "
					+ TeleportWarmup.SECONDS + " сек."));
			TeleportWarmup.start(target, "к " + from.getGameProfile().getName(), () -> {
				if (from.isRemoved() || from.networkHandler == null) {
					Chat.send(target, Chat.red("Игрок " + from.getGameProfile().getName() + " вышел — телепорт отменён."));
					return;
				}
				teleport(target, from);
				Chat.send(target, Chat.green("Вы телепортированы к " + from.getGameProfile().getName() + "."));
			});
			return "Запрос принят.";
		}
		Chat.send(from, Chat.green(target.getGameProfile().getName() + " принял запрос."));
		TeleportWarmup.start(from, "к " + target.getGameProfile().getName(), () -> {
			if (target.isRemoved() || target.networkHandler == null) {
				Chat.send(from, Chat.red("Игрок " + target.getGameProfile().getName() + " вышел — телепорт отменён."));
				return;
			}
			if (!TpGuard.canReach(from, target)) {
				Chat.send(from, Chat.red("Нельзя телепортироваться к администратору более высокого уровня."));
				return;
			}
			teleport(from, target);
			Chat.send(from, Chat.green("Вы телепортированы к " + target.getGameProfile().getName() + "."));
		});
		return "Запрос принят.";
	}

	public static String deny(ServerPlayerEntity target) {
		Request req = PENDING.remove(target.getUuid());
		if (req == null) {
			return "Нет входящего запроса.";
		}
		if (target.getServer() != null) {
			ServerPlayerEntity from = target.getServer().getPlayerManager().getPlayer(req.from);
			if (from != null) {
				Chat.send(from, Chat.yellow(target.getGameProfile().getName() + " отклонил запрос."));
			}
		}
		return "Запрос отклонён.";
	}

	private static void teleport(ServerPlayerEntity moving, ServerPlayerEntity dest) {
		TpGuard.guarded(() -> WorldGate.allow(() -> moving.teleport(dest.getServerWorld(), dest.getX(), dest.getY(), dest.getZ(),
				dest.getYaw(), dest.getPitch())));
	}

	public static PlayerWaypoint here(ServerPlayerEntity player) {
		PlayerWaypoint point = new PlayerWaypoint();
		point.x = player.getX();
		point.y = player.getY();
		point.z = player.getZ();
		point.yaw = player.getYaw();
		point.pitch = player.getPitch();
		point.dimension = player.getWorld().getRegistryKey().getValue().toString();
		return point;
	}
}
