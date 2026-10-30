package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwAnimationTest {
	private static CwAnimation.Cycle armCycle(boolean loop) {
		CwAnimation.Cycle c = new CwAnimation.Cycle();
		c.loop = loop;
		c.frames.add(frame(0));
		c.frames.add(frame(-90));
		c.frames.add(frame(0));
		for (var f : c.frames) { f.durationMs = 1000; f.easing = "LINEAR"; }
		return c;
	}

	private static CwAnimation.Frame frame(float armX) {
		CwAnimation.Frame f = new CwAnimation.Frame();
		f.bones.put("right_arm", new CwAnimation.Rot(armX, 0, 0));
		return f;
	}

	@Test
	void interpolatesBetweenPoses() {
		var c = armCycle(true);
		assertEquals(-45f, CwAnimation.sample(c, 1500, "right_arm").x, 0.01f);
		assertEquals(-90f, CwAnimation.sample(c, 2000, "right_arm").x, 0.01f);
		assertEquals(0f, CwAnimation.sample(c, 0, "head").x, 0.0001f);
	}

	@Test
	void loopWrapsAndNonLoopHoldsLastFrame() {
		assertEquals(CwAnimation.sample(armCycle(true), 500, "right_arm").x, CwAnimation.sample(armCycle(true), 3500, "right_arm").x, 0.01f);
		assertEquals(0f, CwAnimation.sample(armCycle(false), 99_000, "right_arm").x, 0.01f);
	}

	@Test
	void parseSanitisesUntrustedJson() {
		var c = CwAnimation.parse("{\"frames\":[{\"durationMs\":1,\"bones\":{\"head\":{\"x\":999},\"evil\":{\"x\":1}}}]}");
		assertEquals(50, c.frames.get(0).durationMs);
		assertEquals(180f, c.frames.get(0).bones.get("head").x, 0.001f);
		assertTrue(!c.frames.get(0).bones.containsKey("evil"));
		assertTrue(CwAnimation.parse("not json").frames.isEmpty());
	}

	@Test
	void roundTripKeepsRotations() {
		var again = CwAnimation.parse(CwAnimation.toJson(armCycle(true)));
		assertEquals(3, again.frames.size());
		assertEquals(-90f, again.frames.get(1).bones.get("right_arm").x, 0.001f);
	}
}
