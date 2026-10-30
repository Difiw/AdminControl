package ru.admincontrol.cw;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import ru.admincontrol.util.NpcPlayerEntity;
import ru.admincontrol.util.NpcStore;

/** Серверный контроллер сохраняемых поз и простых анимаций NPC. */
public final class CwNpcPoseService {
	private CwNpcPoseService() {}

	public static String setPose(NpcPlayerEntity npc, String pose) {
		if (npc == null) return "NPC не найден.";
		if (!java.util.Set.of("straight", "look_left", "look_right", "head_up", "head_down").contains(pose))
			return "Поза: straight, look_left, look_right, head_up или head_down.";
		NpcStore.Saved saved = NpcStore.get(npc.getGameProfile().getName());
		String animation = saved == null ? "none" : saved.animation;
		boolean loop = saved != null && saved.animationLoop;
		NpcStore.setPose(npc.getGameProfile().getName(), pose, animation, loop);
		applyPose(npc, pose);
		return null;
	}

	public static String animate(NpcPlayerEntity npc, String animation, boolean loop) {
		if (npc == null) return "NPC не найден.";
		if (!java.util.Set.of("none", "wave_left", "wave_right").contains(animation))
			return "Анимация: wave_left, wave_right или none.";
		NpcStore.Saved saved = NpcStore.get(npc.getGameProfile().getName());
		String pose = saved == null || saved.pose == null ? "straight" : saved.pose;
		NpcStore.setPose(npc.getGameProfile().getName(), pose, animation, loop && !"none".equals(animation));
		if (!"none".equals(animation)) npc.swingHand("wave_left".equals(animation) ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
		return null;
	}

	public static void tick(NpcPlayerEntity npc) {
		NpcStore.Saved saved = NpcStore.get(npc.getGameProfile().getName());
		if (saved == null) return;
		applyPose(npc, saved.pose);
		if (saved.animationLoop && npc.age % 24 == 0) {
			npc.swingHand("wave_left".equals(saved.animation) ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
		} else if (!saved.animationLoop && saved.animation != null && !"none".equals(saved.animation) && npc.age % 24 == 1) {
			NpcStore.setPose(saved.nick, saved.pose, "none", false);
		}
	}

	private static void applyPose(ServerPlayerEntity npc, String pose) {
		float body = npc.getYaw();
		float head = body;
		float pitch = 0f;
		if ("look_left".equals(pose)) head -= 45f;
		if ("look_right".equals(pose)) head += 45f;
		if ("head_up".equals(pose)) pitch = -30f;
		if ("head_down".equals(pose)) pitch = 30f;
		npc.setBodyYaw(body);
		npc.setHeadYaw(head);
		npc.setPitch(pitch);
	}
}
