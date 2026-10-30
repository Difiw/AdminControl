package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwPlayerStateTest {
	final Path dir = tempDir();
	final UUID player = UUID.randomUUID();

	{ CwPlayerState.configure(dir); } // constructor-time setup so the tests also run under the project's MiniRunner
	private static Path tempDir() { try { return java.nio.file.Files.createTempDirectory("cwstate"); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }

	/** What a server restart does: memory is gone, files stay. */
	private void restart() { CwPlayerState.configure(dir); }

	@Test void enterBuildAndLeaveFollowTheDocumentedPhases() {
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(player));
		var begin = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		assertTrue(begin.ok());
		assertEquals(CwPlayerState.Mode.ENTERING_BUILD, CwPlayerState.mode(player));
		assertTrue(CwPlayerState.commit(begin.transition()));
		assertEquals(CwPlayerState.Mode.BUILD, CwPlayerState.mode(player));
		var exit = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_BUILD);
		assertEquals(CwPlayerState.Mode.EXITING, CwPlayerState.mode(player));
		CwPlayerState.commit(exit.transition());
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(player));
	}

	@Test void doubleClickAndRacingTransitionsAreRejected() {
		var first = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		assertTrue(first.ok());
		var second = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		assertFalse(second.ok());
		assertTrue(second.error().contains(first.transition().id));
		assertFalse(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_BUILD).ok(), "logout racing the entry must not start a second transition");
		CwPlayerState.commit(first.transition());
		assertFalse(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).ok(), "already in BUILD");
	}

	@Test void interruptedTransitionSurvivesRestartAndBlocksNewEntries() {
		var begin = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_STORY);
		assertTrue(begin.ok());
		restart(); // process killed in the middle of ENTERING_STORY
		assertEquals(CwPlayerState.Mode.ENTERING_STORY, CwPlayerState.mode(player));
		assertTrue(CwPlayerState.interrupted(player));
		assertEquals(begin.transition().id, CwPlayerState.currentTransition(player));
		assertFalse(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).ok());
		// recovery is always allowed to unwind
		var exit = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_STORY);
		assertTrue(exit.ok());
		CwPlayerState.commit(exit.transition());
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(player));
		assertFalse(CwPlayerState.interrupted(player));
	}

	@Test void killDuringExitingIsDetectedAfterRestart() {
		CwPlayerState.commit(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).transition());
		assertTrue(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_BUILD).ok());
		restart();
		assertEquals(CwPlayerState.Mode.EXITING, CwPlayerState.mode(player));
		assertTrue(CwPlayerState.interrupted(player));
	}

	@Test void abortRestoresTheStableModeAndFreesTheGuard() {
		var begin = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		CwPlayerState.abort(begin.transition(), "target world missing", CwPlayerState.Mode.NORMAL);
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(player));
		assertFalse(CwPlayerState.busy(player));
		assertTrue(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).ok());
	}

	@Test void administratorMayTestTheStoryFromBuildAndReturnsToBuild() {
		CwPlayerState.commit(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).transition());
		var enter = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_STORY);
		assertTrue(enter.ok());
		assertEquals(CwPlayerState.Mode.BUILD, enter.transition().returnMode);
		CwPlayerState.commit(enter.transition());
		assertEquals(CwPlayerState.Mode.STORY, CwPlayerState.mode(player));
		var exit = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_STORY, CwPlayerState.Mode.BUILD);
		CwPlayerState.commit(exit.transition());
		assertEquals(CwPlayerState.Mode.BUILD, CwPlayerState.mode(player));
	}

	@Test void profilesOfTwoPlayersAreIndependent() {
		UUID other = UUID.randomUUID();
		CwPlayerState.commit(CwPlayerState.begin(player, "A", CwPlayerState.Kind.ENTER_STORY).transition());
		assertEquals(CwPlayerState.Mode.STORY, CwPlayerState.mode(player));
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(other));
		assertTrue(CwPlayerState.begin(other, "B", CwPlayerState.Kind.ENTER_STORY).ok(), "a transition of one player never blocks another");
	}

	@Test void releaseOnDisconnectKeepsPhaseForRecovery() {
		var begin = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		CwPlayerState.release(player, "disconnect");
		assertFalse(CwPlayerState.busy(player));
		assertEquals(CwPlayerState.Mode.ENTERING_BUILD, CwPlayerState.mode(player));
		assertNotNull(begin.transition());
	}

	@Test void journalRecordsEveryStepWithTheTransitionId() {
		var begin = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD);
		CwPlayerState.step(begin.transition(), "NORMAL profile stored");
		CwPlayerState.commit(begin.transition());
		String journal = CwPlayerState.readJournal();
		assertTrue(journal.contains("\"BEGIN\"") && journal.contains("\"STEP\"") && journal.contains("\"COMMIT\""));
		assertTrue(journal.contains(begin.transition().id));
		assertEquals(3, CwPlayerState.tail(player, 10).size());
	}

	@Test void forceOverridesAStuckStateAndNeverReturnsATransientOne() {
		CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_STORY);
		assertTrue(CwPlayerState.force(player, CwPlayerState.Mode.ENTERING_STORY, "bad request"));
		assertEquals(CwPlayerState.Mode.NORMAL, CwPlayerState.mode(player));
		assertNull(CwPlayerState.currentTransition(player));
	}

	@Test void damagedStateFileFallsBackToBackup() throws Exception {
		CwPlayerState.commit(CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.ENTER_BUILD).transition());
		var exit = CwPlayerState.begin(player, "Alex", CwPlayerState.Kind.EXIT_BUILD);
		java.nio.file.Files.writeString(dir.resolve("cw-player-state").resolve(player + ".json"), "{ not json");
		restart();
		// the last good copy (BUILD, written before EXITING) is used instead of silently resetting the player to NORMAL
		assertEquals(CwPlayerState.Mode.BUILD, CwPlayerState.mode(player));
		assertNotNull(exit.transition());
	}
}
