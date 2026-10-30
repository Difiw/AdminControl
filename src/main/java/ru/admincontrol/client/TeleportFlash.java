package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Переход при телепорте своего игрока: затемнение, расходящееся кольцо света и мерцающие искры. */
public final class TeleportFlash {
	private static final long MS = 650;
	private static final double JUMP = 24;
	private static Vec3d last;
	private static RegistryKey<World> lastDim;
	private static long start = -1;

	private TeleportFlash() {
	}

	/** No white flash in or around the story worlds: the flash would reveal that the player changed world. */
	private static boolean story(RegistryKey<World> key) {
		return key != null && "admin_control".equals(key.getValue().getNamespace()) && (key.getValue().getPath().startsWith("cw_story_") || key.getValue().getPath().equals("cw_limbo") || key.getValue().getPath().equals("cw_builder"));
	}

	public static void reset() {
		last = null;
		lastDim = null;
		start = -1;
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			reset();
			return;
		}
		Vec3d pos = client.player.getPos();
		RegistryKey<World> dim = client.world.getRegistryKey();
		if (last != null && !SpectateHud.spectating() && !CwTransitionOverlay.active() && !story(dim) && !story(lastDim)
				&& (dim != lastDim || pos.squaredDistanceTo(last) > JUMP * JUMP)) {
			start = Util.getMeasuringTimeMs();
		}
		last = pos;
		lastDim = dim;
	}

	public static void render(DrawContext context) {
		if (start < 0) {
			return;
		}
		double t = (Util.getMeasuringTimeMs() - start) / (double) MS;
		if (t >= 1) {
			start = -1;
			return;
		}
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		int cx = w / 2;
		int cy = h / 2;
		double k = 1 - t;
		context.fill(0, 0, w, h, ((int) (0xD0 * k * k) << 24) | 0x0C0618);
		int edge = (int) (0x90 * k);
		int band = Math.max(8, h / 6);
		context.fillGradient(0, 0, w, band, (edge << 24) | 0x2A1650, 0x002A1650);
		context.fillGradient(0, h - band, w, h, 0x002A1650, (edge << 24) | 0x2A1650);
		double e = 1 - Math.pow(1 - t, 3);
		double r = 14 + Math.max(w, h) * 0.55 * e;
		int ringA = (int) (0xC0 * k);
		for (int i = 0; i < 72; i++) {
			double q = i * Math.PI * 2 / 72;
			int x = cx + (int) Math.round(Math.cos(q) * r);
			int y = cy + (int) Math.round(Math.sin(q) * r * 0.62);
			context.fill(x - 1, y - 1, x + 2, y + 2, (ringA << 24) | 0xE6D8FF);
		}
		double r2 = r * 0.6;
		for (int i = 0; i < 40; i++) {
			double q = i * Math.PI * 2 / 40 + t * 2;
			int x = cx + (int) Math.round(Math.cos(q) * r2);
			int y = cy + (int) Math.round(Math.sin(q) * r2 * 0.62);
			context.fill(x, y, x + 1, y + 1, ((int) (0xA0 * k) << 24) | 0xB58CFF);
		}
		for (int i = 0; i < 26; i++) {
			double f = Math.sin(i * 78.233) * 43758.5453;
			f -= Math.floor(f);
			double g = Math.sin(i * 12.989) * 24634.6345;
			g -= Math.floor(g);
			int x = (int) (f * w);
			int y = (int) (g * h - t * 40);
			int a = (int) (0xD0 * k * (0.5 + 0.5 * Math.sin(t * 20 + i)));
			context.fill(x, y, x + 2, y + 2, (Math.max(0, a) << 24) | 0xFFFFFF);
		}
	}
}
