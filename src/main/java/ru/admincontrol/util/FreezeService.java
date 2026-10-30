package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** /fz: 1 — без движения и ТП; 2 — ещё и без поворота камеры; 3 — всё кроме чата. */
public final class FreezeService {
	private static final Set<String> MOVE_COMMANDS = Set.of(
			"tpi", "ttp", "home", "thome", "tpa", "tpahere", "tpy", "tpaccept", "ok",
			"gevent", "training", "spawn", "tp", "teleport", "clan", "cwar", "clanwar");

	private FreezeService() {
	}

	public static int level(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isNpc(player)) {
			return 0;
		}
		PlayerRecord rec = DataManager.get().getOrCreate(player);
		return Math.max(0, Math.min(3, rec.freezeLevel));
	}

	public static boolean frozen(ServerPlayerEntity player) {
		return level(player) > 0;
	}

	/** Уровень 3: заблокировано всё, кроме чата. */
	public static boolean actionsBlocked(ServerPlayerEntity player) {
		return level(player) >= 3;
	}

	public static void freeze(ServerPlayerEntity target, int level, String by) {
		if (target == null || DataManager.get() == null) {
			return;
		}
		if (TrainingService.active(target)) {
			TrainingService.off(target);
		}
		if (EventService.member(target)) {
			EventService.forceLeave(target);
		}
		if (SpectateService.isSpectating(target)) {
			SpectateService.stop(target);
		}
		PlayerRecord rec = DataManager.get().getOrCreate(target);
		boolean already = rec.freezeLevel > 0;
		rec.freezeLevel = Math.max(1, Math.min(3, level));
		if (!already) {
			rec.freezeX = target.getX();
			rec.freezeY = target.getY();
			rec.freezeZ = target.getZ();
			rec.freezeDim = target.getWorld().getRegistryKey().getValue().toString();
		}
		rec.freezeYaw = target.getYaw();
		rec.freezePitch = target.getPitch();
		rec.freezeBy = by == null ? "" : by;
		target.stopRiding();
		target.setVelocity(0, 0, 0);
		target.velocityModified = true;
		if (rec.freezeLevel >= 3) {
			target.closeHandledScreen();
		}
		DataManager.get().saveAll();
		sync(target);
	}

	public static boolean unfreeze(ServerPlayerEntity target, PlayerRecord rec) {
		if (rec == null || rec.freezeLevel <= 0) {
			return false;
		}
		rec.freezeLevel = 0;
		rec.freezeBy = "";
		DataManager.get().saveAll();
		if (target != null) {
			sync(target);
		}
		return true;
	}

	public static void sync(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || player.networkHandler instanceof NpcNetworkHandler) {
			return;
		}
		var buf = PacketByteBufs.create();
		buf.writeVarInt(level(player));
		ServerPlayNetworking.send(player, ModPackets.FREEZE, buf);
	}

	public static void tick(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player)) {
				continue;
			}
			PlayerRecord rec = DataManager.get().getOrCreate(player);
			if (rec.freezeLevel > 0) {
				hold(player, rec);
			}
		}
	}

	private static void hold(ServerPlayerEntity player, PlayerRecord rec) {
		if (player.isDead() || player.isRemoved()) {
			return;
		}
		if (player.hasVehicle()) {
			player.stopRiding();
		}
		player.setVelocity(0, 0, 0);
		player.velocityModified = true;
		player.fallDistance = 0;
		player.setSprinting(false);
		ServerWorld world = SpawnService.resolveWorld(player.getServer(), rec.freezeDim);
		if (world == null) {
			world = player.getServerWorld();
		}
		boolean lockLook = rec.freezeLevel >= 2;
		if (player.getWorld() != world) {
			ServerWorld dest = world;
			TpGuard.bypass(() -> WorldGate.allow(() -> TeleportLog.silent(() ->
					player.teleport(dest, rec.freezeX, rec.freezeY, rec.freezeZ,
							lockLook ? rec.freezeYaw : player.getYaw(), lockLook ? rec.freezePitch : player.getPitch()))));
			return;
		}
		double dx = player.getX() - rec.freezeX;
		double dy = player.getY() - rec.freezeY;
		double dz = player.getZ() - rec.freezeZ;
		boolean moved = dx * dx + dy * dy + dz * dz > 0.0025;
		boolean turned = lockLook && (Math.abs(wrap(player.getYaw() - rec.freezeYaw)) > 0.5f
				|| Math.abs(player.getPitch() - rec.freezePitch) > 0.5f);
		if (!moved && !turned) {
			return;
		}
		TpGuard.bypass(() -> TeleportLog.silent(() -> {
			if (lockLook) {
				player.networkHandler.requestTeleport(rec.freezeX, rec.freezeY, rec.freezeZ, rec.freezeYaw, rec.freezePitch);
			} else {
				player.networkHandler.requestTeleport(rec.freezeX, rec.freezeY, rec.freezeZ, 0f, 0f,
						EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT));
			}
		}));
	}

	private static float wrap(float deg) {
		float d = deg % 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	/** Команда запрещена замороженному? Возвращает текст отказа или null. */
	public static String commandDeny(ServerPlayerEntity player, String name) {
		int level = level(player);
		if (level <= 0 || name == null) {
			return null;
		}
		if (level >= 3) {
			return "Вы заморожены (уровень 3): доступен только чат.";
		}
		String n = name.toLowerCase(Locale.ROOT);
		if (n.contains(":")) {
			n = n.substring(n.indexOf(':') + 1);
		}
		if (MOVE_COMMANDS.contains(n)) {
			return "Вы заморожены: телепорты, тренировки и мероприятия недоступны.";
		}
		return null;
	}

	public static boolean deny(ServerPlayerEntity player) {
		if (!frozen(player)) {
			return false;
		}
		Chat.send(player, Chat.red("Вы заморожены: тренировки, мероприятия и телепорты недоступны."));
		return true;
	}
}
