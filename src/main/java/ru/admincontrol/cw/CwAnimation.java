package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pose / animation-cycle model shared by the editor, the server (validation, persistence) and the client renderer.
 * A frame is a full target pose; {@code durationMs} is the time spent moving from the previous frame into it.
 * Rotations are degrees added on top of the vanilla model angles.
 */
public final class CwAnimation {
	public static final List<String> BONES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "hat", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants");
	public static final List<String> EASINGS = List.of("SMOOTH", "LINEAR", "EASE_IN", "EASE_OUT", "STEP");
	public static final int MAX_FRAMES = 64, MAX_JSON = 60_000;
	public static final float LIMIT = 180f;

	public static final class Rot {
		public float x, y, z;
		public Rot() {}
		public Rot(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
		public Rot copy() { return new Rot(x, y, z); }
		public boolean zero() { return Math.abs(x) < 0.05f && Math.abs(y) < 0.05f && Math.abs(z) < 0.05f; }
	}

	public static final class Frame {
		public int durationMs = 400;
		public String easing = "SMOOTH";
		public Map<String, Rot> bones = new LinkedHashMap<>();
		public Frame copy() {
			Frame f = new Frame();
			f.durationMs = durationMs;
			f.easing = easing;
			for (var e : bones.entrySet()) f.bones.put(e.getKey(), e.getValue().copy());
			return f;
		}
	}

	public static final class Cycle {
		public int schemaVersion = 1;
		public String name = "Новый цикл";
		public boolean loop = true;
		public List<Frame> frames = new ArrayList<>();
		public Cycle copy() { return parse(toJson(this)); }
		public long totalMs() { long t = 0; for (Frame f : frames) t += f.durationMs; return t; }
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private CwAnimation() {}

	public static String toJson(Cycle cycle) { return GSON.toJson(cycle); }

	/** Parses and sanitises untrusted JSON. Never returns null; invalid input yields an empty cycle. */
	public static Cycle parse(String json) {
		Cycle cycle = null;
		try {
			if (json != null && !json.isBlank() && json.length() <= MAX_JSON) cycle = GSON.fromJson(json, Cycle.class);
		} catch (Exception ignored) {
		}
		if (cycle == null) cycle = new Cycle();
		sanitize(cycle);
		return cycle;
	}

	public static void sanitize(Cycle cycle) {
		if (cycle.name == null || cycle.name.isBlank()) cycle.name = "Новый цикл";
		if (cycle.name.length() > 48) cycle.name = cycle.name.substring(0, 48);
		if (cycle.frames == null) cycle.frames = new ArrayList<>();
		if (cycle.frames.size() > MAX_FRAMES) cycle.frames = new ArrayList<>(cycle.frames.subList(0, MAX_FRAMES));
		cycle.frames.removeIf(f -> f == null);
		for (Frame f : cycle.frames) {
			f.durationMs = Math.max(50, Math.min(60_000, f.durationMs));
			if (f.easing == null || !EASINGS.contains(f.easing)) f.easing = "SMOOTH";
			Map<String, Rot> clean = new LinkedHashMap<>();
			if (f.bones != null)
				for (var e : f.bones.entrySet()) {
					if (e.getValue() == null || !BONES.contains(e.getKey())) continue;
					Rot r = e.getValue();
					r.x = clamp(r.x);
					r.y = clamp(r.y);
					r.z = clamp(r.z);
					clean.put(e.getKey(), r);
				}
			f.bones = clean;
		}
	}

	private static float clamp(float v) { return Float.isNaN(v) ? 0f : Math.max(-LIMIT, Math.min(LIMIT, v)); }

	private static float ease(String easing, float t) {
		t = Math.max(0f, Math.min(1f, t));
		return switch (easing == null ? "SMOOTH" : easing) {
			case "LINEAR" -> t;
			case "EASE_IN" -> t * t;
			case "EASE_OUT" -> 1f - (1f - t) * (1f - t);
			case "STEP" -> t >= 1f ? 1f : 0f;
			default -> t * t * (3f - 2f * t);
		};
	}

	private static Rot at(Frame f, String bone) {
		Rot r = f == null ? null : f.bones.get(bone);
		return r == null ? ZERO : r;
	}

	private static final Rot ZERO = new Rot();

	/** Interpolated pose of one bone at time {@code tMs}. A looping cycle wraps; a non-looping one holds its last frame. */
	public static Rot sample(Cycle cycle, long tMs, String bone) {
		if (cycle == null || cycle.frames.isEmpty()) return ZERO;
		long total = cycle.totalMs();
		long t = tMs;
		if (cycle.loop) t = ((t % total) + total) % total;
		else if (t >= total) return at(cycle.frames.get(cycle.frames.size() - 1), bone);
		else if (t < 0) t = 0;
		long start = 0;
		for (int i = 0; i < cycle.frames.size(); i++) {
			Frame f = cycle.frames.get(i);
			if (t < start + f.durationMs || i == cycle.frames.size() - 1) {
				Frame prev = i > 0 ? cycle.frames.get(i - 1) : (cycle.loop ? cycle.frames.get(cycle.frames.size() - 1) : null);
				float k = ease(f.easing, (float) (t - start) / f.durationMs);
				Rot a = at(prev, bone), b = at(f, bone);
				return new Rot(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
			}
			start += f.durationMs;
		}
		return ZERO;
	}

	public static Map<String, Rot> pose(Cycle cycle, long tMs) {
		Map<String, Rot> out = new LinkedHashMap<>();
		for (String bone : BONES) {
			Rot r = sample(cycle, tMs, bone);
			if (!r.zero()) out.put(bone, r);
		}
		return out;
	}
}
