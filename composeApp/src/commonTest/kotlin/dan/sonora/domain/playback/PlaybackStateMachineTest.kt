package dan.sonora.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateMachineTest {

	@Test
	fun startsIdle() {
		assertEquals(PlaybackState.Idle, PlaybackStateMachine().current)
	}

	@Test
	fun legalPath_idleToPlaying() {
		val fsm = PlaybackStateMachine()
		assertTrue(fsm.transitionTo(PlaybackState.Preparing))
		assertTrue(fsm.transitionTo(PlaybackState.Ready))
		assertTrue(fsm.transitionTo(PlaybackState.Playing))
		assertEquals(PlaybackState.Playing, fsm.current)
	}

	@Test
	fun illegalTransition_isRejected_andStateUnchanged() {
		val fsm = PlaybackStateMachine()
		// Idle -> Playing is not a declared edge.
		assertFalse(fsm.transitionTo(PlaybackState.Playing))
		assertEquals(PlaybackState.Idle, fsm.current)
	}

	@Test
	fun errorAndStopping_reachableFromAnywhere() {
		val fsm = PlaybackStateMachine()
		fsm.transitionTo(PlaybackState.Preparing)
		assertTrue(fsm.transitionTo(PlaybackState.Error))
		val fsm2 = PlaybackStateMachine()
		fsm2.transitionTo(PlaybackState.Preparing)
		fsm2.transitionTo(PlaybackState.Ready)
		fsm2.transitionTo(PlaybackState.Playing)
		assertTrue(fsm2.transitionTo(PlaybackState.Stopping))
	}

	@Test
	fun sameStateTransition_isNoOpSuccess() {
		val fsm = PlaybackStateMachine()
		assertTrue(fsm.transitionTo(PlaybackState.Idle))
		assertEquals(PlaybackState.Idle, fsm.current)
	}

	@Test
	fun seekingOverlay_resolvesToPreviousStable() {
		val fsm = PlaybackStateMachine()
		fsm.transitionTo(PlaybackState.Preparing)
		fsm.transitionTo(PlaybackState.Ready)
		fsm.transitionTo(PlaybackState.Playing)
		assertTrue(fsm.transitionTo(PlaybackState.Seeking))
		assertTrue(fsm.resolveOverlay())
		assertEquals(PlaybackState.Playing, fsm.current)
	}

	@Test
	fun crossfadingOverlay_resolvesBackToPlaying() {
		val fsm = PlaybackStateMachine()
		fsm.transitionTo(PlaybackState.Preparing)
		fsm.transitionTo(PlaybackState.Ready)
		fsm.transitionTo(PlaybackState.Playing)
		fsm.transitionTo(PlaybackState.Crossfading)
		assertTrue(fsm.resolveOverlay())
		assertEquals(PlaybackState.Playing, fsm.current)
	}

	@Test
	fun resolveOverlay_whenNotOverlay_isNoOp() {
		val fsm = PlaybackStateMachine()
		fsm.transitionTo(PlaybackState.Preparing)
		fsm.transitionTo(PlaybackState.Ready)
		assertFalse(fsm.resolveOverlay())
		assertEquals(PlaybackState.Ready, fsm.current)
	}

	@Test
	fun reset_returnsToIdle() {
		val fsm = PlaybackStateMachine()
		fsm.transitionTo(PlaybackState.Preparing)
		fsm.transitionTo(PlaybackState.Ready)
		fsm.reset()
		assertEquals(PlaybackState.Idle, fsm.current)
	}
}
