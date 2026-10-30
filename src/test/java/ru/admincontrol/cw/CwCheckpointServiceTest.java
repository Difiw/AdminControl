package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CwCheckpointServiceTest {
	private static final long MINUTE = 20L * 60L;
	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String WORLD = "admin_control:cw_story_3";

	private static CwStoryService.Snapshot snapshot() {
		var s = new CwStoryService.Snapshot();
		s.kind = "auto"; s.ownerUuid = PLAYER.toString(); s.storyId = "default"; s.instanceId = "inst-1";
		s.world = WORLD; s.x = 10.5; s.y = 70; s.z = -4.5; s.profileNbt = "{Health:20.0f}";
		return s;
	}

	@Test void intervalIsSevenMinutesOfServerTicks() { assertEquals(7 * MINUTE, CwCheckpointService.INTERVAL); assertEquals(8400L, CwCheckpointService.INTERVAL); }

	@Test void manualSaveDoesNotMoveScheduledDeadline() {
		long deadline = CwCheckpointService.INTERVAL;                       // AUTO at t=0 -> next at t=7 min
		assertFalse(CwCheckpointService.autosaveDue(3 * MINUTE, deadline)); // MANUAL at t=3 min: nothing is due, deadline untouched
		assertFalse(CwCheckpointService.autosaveDue(7 * MINUTE - 1, deadline));
		assertTrue(CwCheckpointService.autosaveDue(7 * MINUTE, deadline));  // AUTO still fires at t=7 min
		assertEquals(14 * MINUTE, CwCheckpointService.rescheduled(deadline, 7 * MINUTE)); // next is t=14, never t=10
	}

	@Test void nextCycleIsMeasuredFromOriginalDeadline() {
		assertEquals(CwCheckpointService.INTERVAL * 2, CwCheckpointService.nextDeadline(CwCheckpointService.INTERVAL));
		assertEquals(CwCheckpointService.INTERVAL * 2, CwCheckpointService.rescheduled(CwCheckpointService.INTERVAL, CwCheckpointService.INTERVAL + 3));
	}

	@Test void longLagDoesNotCauseCatchUpBurst() {
		long deadline = CwCheckpointService.INTERVAL, active = 5 * CwCheckpointService.INTERVAL;
		long next = CwCheckpointService.rescheduled(deadline, active);
		assertTrue(next > active);
		assertFalse(CwCheckpointService.autosaveDue(active, next));
	}

	@Test void validSnapshotOfTheOwnInstancePasses() {
		assertNull(CwCheckpointService.ownershipProblem(PLAYER, snapshot(), WORLD, "inst-1", "default"));
	}

	@Test void legacySnapshotWithoutBindingsIsAcceptedInItsOwnWorld() {
		var s = snapshot(); s.ownerUuid = null; s.instanceId = null; s.storyId = null;
		assertNull(CwCheckpointService.ownershipProblem(PLAYER, s, WORLD, "inst-1", "default"));
	}

	@Test void missingSnapshotIsReported() { assertEquals(CwCheckpointService.REASON_MISSING, CwCheckpointService.ownershipProblem(PLAYER, null, WORLD, "inst-1", "default")); }

	@Test void snapshotOfAnotherPlayerIsRejected() {
		var s = snapshot(); s.ownerUuid = UUID.randomUUID().toString();
		assertEquals(CwCheckpointService.REASON_FOREIGN, CwCheckpointService.ownershipProblem(PLAYER, s, WORLD, "inst-1", "default"));
	}

	@Test void snapshotOfAnEarlierInstanceOrSlotOfTheSamePlayerIsRebased() {
		var otherInstance = snapshot(); otherInstance.instanceId = "inst-2";
		assertNull(CwCheckpointService.ownershipProblem(PLAYER, otherInstance, WORLD, "inst-1", "default"));
		var otherStory = snapshot(); otherStory.storyId = "other";
		assertNull(CwCheckpointService.ownershipProblem(PLAYER, otherStory, WORLD, "inst-1", "default"));
		assertNull(CwCheckpointService.ownershipProblem(PLAYER, snapshot(), "admin_control:cw_story_4", "inst-1", "default"));
	}

	@Test void noStoryInstanceMeansWorldUnavailable() {
		assertEquals(CwCheckpointService.REASON_WORLD, CwCheckpointService.ownershipProblem(PLAYER, snapshot(), null, null, null));
	}

	@Test void corruptSnapshotsAreRejectedAndNeverFallBackToTheMainWorld() {
		var blank = snapshot(); blank.profileNbt = " ";
		assertEquals(CwCheckpointService.REASON_CORRUPT, CwCheckpointService.ownershipProblem(PLAYER, blank, WORLD, "inst-1", "default"));
		var overworld = snapshot(); overworld.world = "minecraft:overworld";
		assertEquals(CwCheckpointService.REASON_CORRUPT, CwCheckpointService.ownershipProblem(PLAYER, overworld, WORLD, "inst-1", "default"));
		var nan = snapshot(); nan.x = Double.NaN;
		assertEquals(CwCheckpointService.REASON_CORRUPT, CwCheckpointService.ownershipProblem(PLAYER, nan, WORLD, "inst-1", "default"));
		var void_ = snapshot(); void_.y = -5000;
		assertEquals(CwCheckpointService.REASON_CORRUPT, CwCheckpointService.ownershipProblem(PLAYER, void_, WORLD, "inst-1", "default"));
	}

	@Test void storyWorldIdsAreRecognised() {
		assertTrue(CwStoryRules.isStoryWorldId("admin_control:cw_story_0"));
		assertTrue(CwStoryRules.isStoryWorldId("admin_control:cw_story_15"));
		assertFalse(CwStoryRules.isStoryWorldId("admin_control:cw_story_16"));
		assertFalse(CwStoryRules.isStoryWorldId("minecraft:overworld"));
		assertFalse(CwStoryRules.isStoryWorldId(null));
	}

	@Test void newStoryStartsInBrightDaytime() {
		assertEquals(1000L, new CwStoryService.Progress().storyTime);
		assertTrue(CwStoryService.DEFAULT_STORY_TIME >= 0 && CwStoryService.DEFAULT_STORY_TIME < 12000);
	}

	@Test void snapshotSchemaIsVersionedAndCopyKeepsBindings() {
		var copy = snapshot().copy();
		assertEquals(CwStoryService.Snapshot.CURRENT_SCHEMA, copy.schemaVersion);
		assertEquals(PLAYER.toString(), copy.ownerUuid);
		assertEquals("inst-1", copy.instanceId);
	}
}
