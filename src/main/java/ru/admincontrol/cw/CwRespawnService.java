package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death inside the story. The vanilla death never happens: the lethal hit is cancelled (health restored, nothing is
 * dropped, no vanilla death screen), the player is moved to the separate "waiting" dimension {@code admin_control:cw_limbo}
 * as a spectator and our own window «Вы погибли» opens at once. There are three actions: respawn from the AUTO point,
 * respawn from the MANUAL point, or leave the story. The window cannot be dismissed, so a dead player is never left
 * without a decision and never ends up in the main world. Offers are one-shot, a repeated packet does nothing.
 */
public final class CwRespawnService {
	public static final int CHOICE_AUTO = 0, CHOICE_MANUAL = 1, CHOICE_LEAVE = 2;
	public static final Identifier LIMBO = new Identifier(AdminControlMod.MOD_ID, "cw_limbo");
	private static final double LIMBO_X = .5, LIMBO_Y = 100, LIMBO_Z = .5;
	private record Offer(UUID nonce, boolean death) {}
	private record Death(Identifier world, double x, double y, double z, long worldTime) {}
	private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Death> DEATHS = new ConcurrentHashMap<>();
	private static final Set<UUID> QUEUE = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> WAITING = ConcurrentHashMap.newKeySet();

	private CwRespawnService() {}

	public static boolean pending(ServerPlayerEntity p) {
		if (p == null) return false;
		Offer o = OFFERS.get(p.getUuid());
		return QUEUE.contains(p.getUuid()) || (o != null && o.death());
	}

	public static boolean inLimbo(ServerPlayerEntity p) { return p != null && p.getWorld().getRegistryKey().getValue().equals(LIMBO); }

	/**
	 * Called at the start of a player death. Returns true when the death was taken over (the caller cancels it).
	 * Only players with an active story session inside their story world are affected.
	 */
	public static boolean interceptDeath(ServerPlayerEntity p) {
		if (p == null || !CwStoryRules.inside(p) || !CwStoryWorldService.hasSession(p.getUuid())) return false;
		p.setHealth(p.getMaxHealth());
		p.extinguish();
		QUEUE.add(p.getUuid());
		return true;
	}

	/** Moves queued players to the waiting dimension; runs at the end of the server tick, outside of damage handling. */
	public static void tick(MinecraftServer server) {
		if (server == null || QUEUE.isEmpty()) return;
		for (UUID id : QUEUE) {
			QUEUE.remove(id);
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p == null || !CwStoryRules.inside(p)) continue;
			enterLimbo(server, p);
		}
	}

	private static void enterLimbo(MinecraftServer server, ServerPlayerEntity p) {
		AdminControlMod.LOGGER.info("[CW] story death player={} -> waiting dimension", p.getUuidAsString());
		DEATHS.put(p.getUuid(), new Death(p.getWorld().getRegistryKey().getValue(), p.getX(), p.getY(), p.getZ(), p.getWorld().getTime()));
		CwStoryWorldService.saveStoryProfileNow(p); // story profile = the moment of death with full health; used if the player leaves
		CwTransition.play(p, "Вы погибли", "", () -> {
			if (!p.isAlive() || !CwStoryRules.inside(p)) return;
			if (!sendToLimbo(p)) AdminControlMod.LOGGER.error("[CW] waiting dimension {} is not loaded; offering the window in place", LIMBO);
			WAITING.add(p.getUuid());
			sendOffer(p, true);
		});
	}

	/** Puts the player into the waiting dimension as a spectator. Returns false when that dimension is not loaded. */
	public static boolean sendToLimbo(ServerPlayerEntity p) {
		MinecraftServer server = p.getServer();
		ServerWorld limbo = server == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, LIMBO));
		if (limbo == null) return false;
		limbo.getChunk(net.minecraft.util.math.BlockPos.ofFloored(LIMBO_X, LIMBO_Y, LIMBO_Z));
		p.closeHandledScreen();
		p.stopRiding();
		ru.admincontrol.util.WorldGate.allow(() -> p.teleport(limbo, LIMBO_X, LIMBO_Y, LIMBO_Z, 0, 0));
		p.changeGameMode(GameMode.SPECTATOR);
		return true;
	}


	/** /storyload: the save window at any time inside Люмира (the automatic save is always reachable). */
	public static String openSaves(ServerPlayerEntity p) {
		if (!CwStoryRules.inside(p) || !p.isAlive()) return "Окно сохранений доступно только в Люмире.";
		if (pending(p)) return "Сейчас окно сохранений недоступно.";
		sendOffer(p, false);
		return null;
	}

	/** Rejoin while waiting (disconnect on the death window, server restart): show the window again. */
	public static void reopen(ServerPlayerEntity p) {
		if (p == null) return;
		WAITING.add(p.getUuid());
		sendOffer(p, true);
	}

	/** Safety net for deaths that still went through vanilla respawn for a player with a story session. */
	public static void afterRespawn(ServerPlayerEntity p) {
		if (p == null || !CwStoryWorldService.hasSession(p.getUuid())) return;
		if (!CwStoryRules.inside(p)) {
			AdminControlMod.LOGGER.warn("[CW] vanilla respawn left the story world player={}; pulling back", p.getUuidAsString());
			String error = CwStoryWorldService.returnToStorySpawn(p);
			if (error != null) { p.sendMessage(Text.literal("[CW] " + error), false); return; }
		}
		CwCheckpointService.bindSpawnAnchor(p);
		sendOffer(p, true);
	}

	private static void sendOffer(ServerPlayerEntity p, boolean death) {
		var auto = CwCheckpointService.status(p, CwCheckpointService.Slot.AUTO);
		var manual = CwCheckpointService.status(p, CwCheckpointService.Slot.MANUAL);
		UUID nonce = UUID.randomUUID();
		OFFERS.put(p.getUuid(), new Offer(nonce, death));
		PacketByteBuf b = PacketByteBufs.create();
		b.writeUuid(nonce);
		b.writeBoolean(death);
		write(b, auto);
		write(b, manual);
		ServerPlayNetworking.send(p, ModPackets.CW_RESPAWN_STATE, b);
	}

	private static void write(PacketByteBuf b, CwCheckpointService.Status s) {
		b.writeBoolean(s.available());
		if (s.available()) { b.writeVarLong(s.ageSeconds()); b.writeString(s.detail() == null ? "" : s.detail(), 64); }
		else b.writeString(s.reason(), 32);
	}

	public static void choose(ServerPlayerEntity p, UUID nonce, int choice) {
		Offer offer = OFFERS.get(p.getUuid());
		if (offer == null || !offer.nonce().equals(nonce) || choice < CHOICE_AUTO || choice > CHOICE_LEAVE) {
			p.sendMessage(Text.literal("Выбор возрождения устарел."), true);
			return;
		}
		if (!OFFERS.remove(p.getUuid(), offer)) return; // already consumed by a duplicate packet
		String who = p.getUuidAsString();
		if (choice == CHOICE_LEAVE) {
			CwTransition.play(p, "Возвращение", "Вы покидаете Люмиру", () -> {
				String error = inLimbo(p) ? CwStoryWorldService.leaveWithoutSaving(p) : CwStoryWorldService.leaveStory(p);
				if (error != null) { p.sendMessage(Text.literal(error), false); if (CwStoryRules.inside(p) || inLimbo(p)) sendOffer(p, offer.death()); return; }
				WAITING.remove(p.getUuid());
				p.sendMessage(Text.literal("Прогресс в Люмире сохранён. Вы вернулись в обычный мир."), false);
				CwAudit.writeSystem(p.getName().getString(), "leave from death window", who, "ok");
			});
			return;
		}
		CwCheckpointService.Slot slot = choice == CHOICE_AUTO ? CwCheckpointService.Slot.AUTO : CwCheckpointService.Slot.MANUAL;
		if (!(CwStoryRules.inside(p) || inLimbo(p)) || !p.isAlive()) { p.sendMessage(Text.literal("Возрождение сейчас недоступно."), true); return; }
		CwTransition.play(p, "Люмира", "Возрождение", () -> {
			String error = CwCheckpointService.restore(p, CwCheckpointService.slotSnapshot(p, slot));
			if (error != null) {
				AdminControlMod.LOGGER.warn("[CW] respawn {} refused player={}: {}", slot, who, error);
				p.sendMessage(Text.literal(error), false);
				sendOffer(p, offer.death()); // show the window again with the real state of both slots
				return;
			}
			WAITING.remove(p.getUuid());
			AdminControlMod.LOGGER.info("[CW] respawn {} ok player={}", slot, who);
			CwAudit.writeSystem(p.getName().getString(), "respawn restore", slot.name(), "ok");
		});
	}

	/** Removes anything a vanilla death dropped near the death spot once a snapshot has been loaded (no duplicates). */
	public static void cleanupDeathDrops(ServerPlayerEntity p) {
		Death death = p == null ? null : DEATHS.remove(p.getUuid());
		if (death == null || p.getServer() == null) return;
		ServerWorld world = p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, death.world()));
		if (world == null) return;
		long elapsed = Math.max(0L, world.getTime() - death.worldTime()) + 40L;
		Box area = new Box(death.x() - 16, death.y() - 16, death.z() - 16, death.x() + 16, death.y() + 16, death.z() + 16);
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, area, e -> e.age <= elapsed)) item.discard();
		for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, area, e -> e.age <= elapsed)) orb.discard();
	}

	public static void clear(ServerPlayerEntity p) {
		if (p == null) return;
		OFFERS.remove(p.getUuid());
		QUEUE.remove(p.getUuid());
		DEATHS.remove(p.getUuid());
	}
}
