package ru.admincontrol.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Магическое небо «Рощи Люмиры»: только в сюжетных и строительном измерениях. Днём — медленно переливающаяся
 * бирюза и сирень, на закате розовое, ночью — глубокий индиго с бирюзовым отсветом. Яркость берётся из ванильного
 * неба, поэтому смена дня и ночи сохраняется.
 */
@Mixin(ClientWorld.class)
public abstract class LumiraSkyMixin {
	private static boolean admin_control$magic(ClientWorld world) {
		var id = world.getRegistryKey().getValue();
		return id.getNamespace().equals("admin_control") && (id.getPath().startsWith("cw_story_") || id.getPath().equals("cw_builder"));
	}

	private static Vec3d admin_control$lerp(Vec3d a, Vec3d b, double t) {
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void admin_control$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		ClientWorld world = (ClientWorld) (Object) this;
		if (!admin_control$magic(world)) {
			return;
		}
		Vec3d base = cir.getReturnValue();
		// Постоянное небо: насыщенный синий (как ночью на эталонном скриншоте); днём чуть ярче, ночью чуть темнее.
		double day = Math.max(0.0, Math.min(1.0, base.y / 0.62));
		double k = 0.80 + 0.45 * day;
		cir.setReturnValue(new Vec3d(Math.min(1.0, 0.16 * k), Math.min(1.0, 0.45 * k), Math.min(1.0, 0.72 * k)));
	}

	@Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
	private void admin_control$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		ClientWorld world = (ClientWorld) (Object) this;
		if (!admin_control$magic(world)) {
			return;
		}
		Vec3d base = cir.getReturnValue();
		double wave = 0.5 + 0.5 * Math.sin(world.getTime() / 500.0 + 0.8);
		Vec3d tint = admin_control$lerp(new Vec3d(1.0, 0.82, 0.96), new Vec3d(0.80, 0.96, 1.0), wave);
		cir.setReturnValue(new Vec3d(base.x * tint.x, base.y * tint.y, base.z * tint.z));
	}
}
