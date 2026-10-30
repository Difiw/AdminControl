package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Leaving Люмира always goes through a confirmation window; the answer is checked against a one-shot nonce. */
public final class CwLeaveService {
	private static final Map<UUID, UUID> OFFERS = new ConcurrentHashMap<>();

	private CwLeaveService() {}

	/** /storyexit: ask the client to confirm (clients without the window leave right away). */
	public static String request(ServerPlayerEntity p) {
		if (!CwStoryRules.inside(p) && !CwRespawnService.inLimbo(p)) return "Вы сейчас не находитесь в Люмире.";
		if (!ServerPlayNetworking.canSend(p, ModPackets.CW_LEAVE_STATE)) { leave(p); return null; }
		UUID nonce = UUID.randomUUID();
		OFFERS.put(p.getUuid(), nonce);
		var buf = PacketByteBufs.create();
		buf.writeUuid(nonce);
		ServerPlayNetworking.send(p, ModPackets.CW_LEAVE_STATE, buf);
		return null;
	}

	public static void answer(ServerPlayerEntity p, UUID nonce, boolean confirm) {
		UUID expected = OFFERS.get(p.getUuid());
		if (expected == null || !expected.equals(nonce) || !OFFERS.remove(p.getUuid(), expected)) return;
		if (confirm) leave(p);
	}

	/** Fade out, save the exit state and go back to the ordinary world. */
	public static void leave(ServerPlayerEntity p) {
		CwTransition.play(p, "Возвращение", "Вы покидаете Люмиру", () -> {
			String error = CwRespawnService.inLimbo(p) ? CwStoryWorldService.leaveWithoutSaving(p) : CwStoryWorldService.leaveStory(p);
			if (error != null) p.sendMessage(Text.literal(error), false);
			else p.sendMessage(Text.literal("Прогресс в Люмире сохранён. Вы вернулись в обычный мир."), false);
		});
	}

	public static void clear(ServerPlayerEntity p) { if (p != null) OFFERS.remove(p.getUuid()); }
}
