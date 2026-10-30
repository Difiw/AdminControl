package ru.admincontrol.cw;

import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Per personal/test instance day clock. The time lives in the owner's {@link CwStoryService.Progress} and never reads
 * or writes the overworld time. Each story slot belongs to exactly one active player, so one player's clock cannot
 * change another player's (or the main world's) sky. The client receives it through ordinary time-update packets.
 */
public final class CwStoryClock {
	public static final long DAY_LENGTH = 24000L;
	private static final long SYNC_INTERVAL_TICKS = 20L;
	private CwStoryClock() {}

	/** Server-side time of day of a story dimension; the owner is the only player inside. */
	public static long time(World world) {
		if (world == null || !CwStoryRules.inside(world)) return CwStoryService.DEFAULT_STORY_TIME;
		if (world instanceof ServerWorld sw) {
			for (ServerPlayerEntity p : sw.getPlayers()) return Math.floorMod(CwStoryService.session(p).storyTime, DAY_LENGTH);
		}
		return CwStoryService.DEFAULT_STORY_TIME;
	}

	public static void tick(MinecraftServer server) {
		if (server == null) return;
		boolean sync = server.getTicks() % SYNC_INTERVAL_TICKS == 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (CwStoryRules.inside(p)) {
			CwStoryService.Progress progress = CwStoryService.session(p);
			if (!progress.paused) { progress.storyTime = Math.floorMod(progress.storyTime + 1, DAY_LENGTH); progress.activeTicks++; }
			if (sync) sync(p, progress);
		}
	}

	/**
	 * Sends this player's story time to this player only. It always carries "daylight cycle on", so the client keeps
	 * advancing the sky smoothly between packets even if the overworld game rule is off.
	 */
	public static void sync(ServerPlayerEntity player, CwStoryService.Progress progress) {
		if (player == null || player.networkHandler == null) return;
		player.networkHandler.sendPacket(new WorldTimeUpdateS2CPacket(player.getWorld().getTime(), Math.floorMod(progress.storyTime, DAY_LENGTH), true));
	}

	public static boolean set(ServerPlayerEntity player, long ticks) {
		if (!CwStoryRules.inside(player)) return false;
		CwStoryService.Progress p = CwStoryService.session(player);
		p.storyTime = Math.floorMod(ticks, DAY_LENGTH);
		CwStoryService.touch(p, "admin:time"); CwStoryService.save();
		sync(player, p);
		return true;
	}
}
