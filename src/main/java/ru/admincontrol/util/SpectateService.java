package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.InspectInventoryFactory;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слежка: админ в режиме наблюдателя стоит у глаз цели и сам вращает камеру.
 * Выход — только /spoff (кнопки «Выйти» нет).
 */
public final class SpectateService {
	private static final Map<UUID, Saved> SAVED = new ConcurrentHashMap<>();

	private SpectateService() {
	}

	private static final class Saved {
		GameMode mode;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		String dimension;
		UUID target;
		int ticks;
		final ItemStack[] hotbar = new ItemStack[10];
		int selected = -1;
	}

	public static boolean start(ServerPlayerEntity admin, ServerPlayerEntity target) {
		if (admin == null || target == null) {
			return false;
		}
		if (admin.getUuid().equals(target.getUuid())) {
			return false;
		}
		NpcService.stopControl(admin);
		Saved saved = SAVED.computeIfAbsent(admin.getUuid(), id -> new Saved());
		if (saved.target == null) {
			saved.mode = admin.interactionManager.getGameMode();
			saved.x = admin.getX();
			saved.y = admin.getY();
			saved.z = admin.getZ();
			saved.yaw = admin.getYaw();
			saved.pitch = admin.getPitch();
			saved.dimension = admin.getWorld().getRegistryKey().getValue().toString();
		}
		saved.target = target.getUuid();
		saved.ticks = 0;
		saved.selected = -1;
		java.util.Arrays.fill(saved.hotbar, null);
		admin.changeGameMode(GameMode.SPECTATOR);
		follow(admin, target, true);
		syncHud(admin, true);
		sendInfo(admin, target);
		sendHotbar(admin, target, saved);
		return true;
	}

	/** Клавиша E во время слежки: инвентарь цели только для просмотра. */
	public static void openTargetInventory(ServerPlayerEntity admin) {
		Saved saved = admin == null ? null : SAVED.get(admin.getUuid());
		if (saved == null || saved.target == null || admin.getServer() == null) {
			return;
		}
		ServerPlayerEntity target = admin.getServer().getPlayerManager().getPlayer(saved.target);
		if (target == null) {
			return;
		}
		ScreenNav.openRoot(admin, new InspectInventoryFactory(target));
		AdminNotify.record(admin.getCommandSource(), "GETINV", target.getGameProfile().getName(),
				"просмотр инвентаря при слежке");
	}

	public static boolean stop(ServerPlayerEntity admin) {
		if (admin == null) {
			return false;
		}
		Saved saved = SAVED.remove(admin.getUuid());
		syncHud(admin, false);
		if (saved == null) {
			return false;
		}
		admin.setCameraEntity(admin);
		ServerWorld world = SpawnService.resolveWorld(admin.getServer(), saved.dimension);
		if (world == null) {
			world = admin.getServerWorld();
		}
		ServerWorld returnWorld = world;
		TpGuard.bypass(() -> WorldGate.allow(() -> admin.teleport(returnWorld, saved.x, saved.y, saved.z, saved.yaw, saved.pitch)));
		if (saved.mode != null) {
			admin.changeGameMode(saved.mode);
		}
		return true;
	}

	public static void tick(ServerPlayerEntity admin) {
		Saved saved = SAVED.get(admin.getUuid());
		if (saved == null || saved.target == null || admin.getServer() == null) {
			return;
		}
		ServerPlayerEntity target = admin.getServer().getPlayerManager().getPlayer(saved.target);
		if (target == null) {
			stop(admin);
			Chat.send(admin, Chat.yellow("Игрок вышел из сети. Слежка прекращена."));
			return;
		}
		if (admin.interactionManager.getGameMode() != GameMode.SPECTATOR) {
			admin.changeGameMode(GameMode.SPECTATOR);
		}
		if (admin.getCameraEntity() != admin) {
			admin.setCameraEntity(admin);
		}
		follow(admin, target, false);
		sendHotbar(admin, target, saved);
		if (++saved.ticks % 5 == 0) {
			sendInfo(admin, target);
		}
	}

	private static void follow(ServerPlayerEntity admin, ServerPlayerEntity target, boolean force) {
		if (admin.getWorld() != target.getWorld()) {
			TpGuard.bypass(() -> WorldGate.allow(() -> admin.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(),
					admin.getYaw(), admin.getPitch())));
			return;
		}
		double dist = admin.squaredDistanceTo(target);
		if (!force && dist < 0.0004) {
			return;
		}
		// Клиент с модом сам держит камеру у цели с интерполяцией; сервер поправляет только при большом расхождении.
		if (!force && dist < 64.0 && ServerPlayNetworking.canSend(admin, ModPackets.SPECTATE_HOTBAR)) {
			return;
		}
		TpGuard.bypass(() -> WorldGate.allow(() -> admin.networkHandler.requestTeleport(target.getX(), target.getY(), target.getZ(), 0f, 0f,
				EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT))));
	}

	public static boolean isSpectating(ServerPlayerEntity admin) {
		return admin != null && SAVED.containsKey(admin.getUuid());
	}

	private static void syncHud(ServerPlayerEntity admin, boolean on) {
		if (admin == null || admin.networkHandler == null || admin.networkHandler instanceof NpcNetworkHandler) {
			return;
		}
		var buf = PacketByteBufs.create();
		buf.writeBoolean(on);
		ServerPlayNetworking.send(admin, ModPackets.SPECTATE, buf);
	}

	private static void sendHotbar(ServerPlayerEntity admin, ServerPlayerEntity target, Saved saved) {
		if (admin.networkHandler == null || admin.networkHandler instanceof NpcNetworkHandler
				|| !ServerPlayNetworking.canSend(admin, ModPackets.SPECTATE_HOTBAR)) {
			return;
		}
		PlayerInventory inv = target.getInventory();
		boolean changed = saved.selected != inv.selectedSlot;
		for (int i = 0; i < 10; i++) {
			ItemStack now = i < 9 ? inv.getStack(i) : target.getOffHandStack();
			ItemStack was = saved.hotbar[i];
			if (was == null || !ItemStack.areEqual(was, now)) {
				saved.hotbar[i] = now.copy();
				changed = true;
			}
		}
		if (!changed) {
			return;
		}
		saved.selected = inv.selectedSlot;
		var buf = PacketByteBufs.create();
		buf.writeVarInt(inv.selectedSlot);
		for (ItemStack stack : saved.hotbar) {
			buf.writeItemStack(stack);
		}
		ServerPlayNetworking.send(admin, ModPackets.SPECTATE_HOTBAR, buf);
	}

	private static void sendInfo(ServerPlayerEntity admin, ServerPlayerEntity target) {
		if (admin.networkHandler == null || admin.networkHandler instanceof NpcNetworkHandler
				|| DataManager.get() == null) {
			return;
		}
		PlayerRecord rec = DataManager.get().getOrCreate(target);
		String clan = ClanService.nameOf(rec.clanId);
		if (clan == null || clan.isBlank() || "—".equals(clan)) {
			clan = "нет";
		}
		var buf = PacketByteBufs.create();
		buf.writeString(target.getGameProfile().getName(), 16);
		buf.writeVarInt(Math.max(0, Economy.balance(target)));
		buf.writeDouble(rec.luck());
		buf.writeString(clan.length() > 32 ? clan.substring(0, 32) : clan, 32);
		buf.writeDouble(rec.power());
		buf.writeVarInt(target.getHungerManager().getFoodLevel());
		buf.writeFloat(target.getHealth());
		buf.writeDouble(target.getX());
		buf.writeDouble(target.getY());
		buf.writeDouble(target.getZ());
		buf.writeVarInt(target.experienceLevel);
		ServerPlayNetworking.send(admin, ModPackets.SPECTATE_INFO, buf);
	}
}
