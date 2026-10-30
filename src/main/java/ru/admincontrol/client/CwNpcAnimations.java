package ru.admincontrol.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import ru.admincontrol.cw.CwAnimation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client side: animation cycles bound to NPCs, plus the editor's one-shot viewport pose override. */
public final class CwNpcAnimations {
	private static final Map<String, CwAnimation.Cycle> BOUND = new ConcurrentHashMap<>();
	private static final long EPOCH = System.currentTimeMillis();
	/** Set by the editor only for the duration of one entity draw call. */
	private static LivingEntity previewEntity;
	private static Map<String, CwAnimation.Rot> previewPose;

	private CwNpcAnimations() {}

	public static void receive(String npcName, String json) {
		String key = npcName.toLowerCase(Locale.ROOT);
		CwAnimation.Cycle cycle = CwAnimation.parse(json);
		if (json == null || json.isBlank() || cycle.frames.isEmpty()) BOUND.remove(key); else BOUND.put(key, cycle);
	}

	public static void clear() { BOUND.clear(); }

	public static void beginPreview(LivingEntity entity, Map<String, CwAnimation.Rot> pose) { previewEntity = entity; previewPose = pose; }
	public static void endPreview() { previewEntity = null; previewPose = null; }

	/** Called after vanilla has posed the model. Adds the cycle / preview rotations on top. */
	public static void apply(PlayerEntityModel<?> model, LivingEntity entity) {
		Map<String, CwAnimation.Rot> pose = null;
		if (entity == previewEntity && previewPose != null) {
			pose = previewPose;
		} else if (entity instanceof AbstractClientPlayerEntity player && !BOUND.isEmpty()) {
			CwAnimation.Cycle cycle = BOUND.get(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
			if (cycle != null) pose = CwAnimation.pose(cycle, System.currentTimeMillis() - EPOCH + (entity.getUuid().hashCode() & 0x3FF));
		}
		if (pose == null || pose.isEmpty()) return;
		add(model.head, pose.get("head"));
		add(model.body, pose.get("body"));
		add(model.rightArm, pose.get("right_arm"));
		add(model.leftArm, pose.get("left_arm"));
		add(model.rightLeg, pose.get("right_leg"));
		add(model.leftLeg, pose.get("left_leg"));
		// Overlay layers were copied from the base parts before our offsets; copy again, then add their own.
		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightPants.copyTransform(model.rightLeg);
		model.leftPants.copyTransform(model.leftLeg);
		add(model.hat, pose.get("hat"));
		add(model.jacket, pose.get("jacket"));
		add(model.rightSleeve, pose.get("right_sleeve"));
		add(model.leftSleeve, pose.get("left_sleeve"));
		add(model.rightPants, pose.get("right_pants"));
		add(model.leftPants, pose.get("left_pants"));
	}

	private static void add(ModelPart part, CwAnimation.Rot r) {
		if (part == null || r == null) return;
		part.pitch += (float) Math.toRadians(r.x);
		part.yaw += (float) Math.toRadians(r.y);
		part.roll += (float) Math.toRadians(r.z);
	}
}
