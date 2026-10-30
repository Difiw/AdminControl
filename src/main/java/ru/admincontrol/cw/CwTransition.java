package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.net.ModPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Smooth transitions into, out of and inside Люмира. The client fades to a themed overlay (which also covers the
 * vanilla "loading terrain" screen); the server waits a few ticks so the fade is visible, performs the switch and
 * tells the client to fade back in. Clients without the overlay get the switch immediately.
 */
public final class CwTransition {
	public static final int DELAY_TICKS = 8;
	private record Task(long due, UUID player, Runnable run) {}
	private static final List<Task> TASKS = new ArrayList<>();

	private CwTransition() {}

	private static void send(ServerPlayerEntity p, boolean begin, String title, String subtitle) {
		var buf = PacketByteBufs.create();
		buf.writeBoolean(begin);
		buf.writeString(title == null ? "" : title, 48);
		buf.writeString(subtitle == null ? "" : subtitle, 96);
		ServerPlayNetworking.send(p, ModPackets.CW_TRANSITION, buf);
	}

	/** Fade out, run {@code action} on the server thread a few ticks later, then fade back in. */
	public static void play(ServerPlayerEntity p, String title, String subtitle, Runnable action) {
		MinecraftServer server = p.getServer();
		if (server == null || !ServerPlayNetworking.canSend(p, ModPackets.CW_TRANSITION)) { action.run(); return; }
		send(p, true, title, subtitle);
		synchronized (TASKS) { TASKS.add(new Task(server.getTicks() + DELAY_TICKS, p.getUuid(), () -> {
			try { action.run(); }
			catch (RuntimeException e) { AdminControlMod.LOGGER.error("[CW] transition action failed player={}", p.getUuidAsString(), e); }
			finally { if (p.networkHandler != null) send(p, false, "", ""); }
		})); }
	}

	public static void tick(MinecraftServer server) {
		if (server == null) return;
		List<Task> due = new ArrayList<>();
		synchronized (TASKS) {
			TASKS.removeIf(t -> { if (t.due() <= server.getTicks()) { due.add(t); return true; } return false; });
		}
		for (Task t : due) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(t.player());
			if (p != null) t.run().run(); // a player who disconnected meanwhile simply keeps their previous state
		}
	}
}
