package ru.admincontrol.cw;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkSectionPos;
import ru.admincontrol.AdminControlMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side diagnostics for CW transitions. Details go to the log; players only ever see a short code. */
public final class CwDiagnostics {
	private static final long REPEAT_MS = 60_000L;
	private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

	private CwDiagnostics() {}

	private static final ThreadLocal<Object[]> LEAVING = new ThreadLocal<>();
	/** How many times the ticket-manager guard repaired a chunk-watch desync since start (diagnostics and the selftest). */
	public static volatile int repairs;

	public static void beginLeave(ChunkSectionPos section, ServerPlayerEntity player) { LEAVING.set(new Object[] {section, player}); }
	public static void endLeave() { LEAVING.remove(); }

	/** Called from the ticket-manager redirect: the player and section of the leave in progress. */
	public static void chunkWatchDesync() {
		Object[] ctx = LEAVING.get();
		if (ctx == null || !(ctx[1] instanceof ServerPlayerEntity player)) { AdminControlMod.LOGGER.warn("[CW] chunk-watch desync repaired (no player context)", new Throwable("call path")); return; }
		chunkWatchDesync(player, (ChunkSectionPos) ctx[0]);
	}

	/** The chunk-watching registry of a world did not know the section a player was leaving (see ChunkTicketManagerMixin). */
	public static void chunkWatchDesync(ServerPlayerEntity player, ChunkSectionPos section) {
		repairs++;
		long now = System.currentTimeMillis();
		Long prev = LAST.get(player.getUuid());
		if (prev != null && now - prev < REPEAT_MS) return;
		LAST.put(player.getUuid(), now);
		String transition = CwPlayerState.currentTransition(player.getUuid());
		AdminControlMod.LOGGER.warn("[CW] chunk-watch desync repaired player={} world={} section={} pos={},{},{} mode={} transition={} builder={} story={}",
				player.getUuidAsString(), player.getWorld().getRegistryKey().getValue(), section,
				player.getBlockX(), player.getBlockY(), player.getBlockZ(), CwPlayerState.mode(player.getUuid()), transition == null ? "-" : transition,
				CwBuilderWorld.inside(player), CwStoryRules.inside(player), new Throwable("call path"));
	}

	public static void forget(UUID player) { LAST.remove(player); }
}
