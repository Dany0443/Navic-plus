package dan.sonora.domain.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dan.sonora.domain.playback.PlaybackState.Buffering
import dan.sonora.domain.playback.PlaybackState.Crossfading
import dan.sonora.domain.playback.PlaybackState.Ended
import dan.sonora.domain.playback.PlaybackState.Error
import dan.sonora.domain.playback.PlaybackState.Idle
import dan.sonora.domain.playback.PlaybackState.Paused
import dan.sonora.domain.playback.PlaybackState.Playing
import dan.sonora.domain.playback.PlaybackState.Preparing
import dan.sonora.domain.playback.PlaybackState.Ready
import dan.sonora.domain.playback.PlaybackState.Seeking
import dan.sonora.domain.playback.PlaybackState.Stopping

/**
 * A small, explicit finite-state machine for playback.
 *
 * Transitions are validated against an allow-list so illegal moves are rejected (and
 * observable via the return value) instead of silently corrupting state. [Error], [Stopping]
 * and [Preparing] are reachable from any state (a fatal error, a stop, or loading a brand-new
 * source can all happen at any time); every other edge is declared in [transitions].
 *
 * Platform-neutral and side-effect free (no logging, no player calls) so it is trivially
 * unit-tested. The engine owns one instance and drives it from coordinator/player callbacks.
 *
 * Not thread-safe: owned by the engine and driven on the playback thread.
 */
class PlaybackStateMachine(initial: PlaybackState = Idle) {

	private val _state = MutableStateFlow(initial)
	val state: StateFlow<PlaybackState> = _state.asStateFlow()

	val current: PlaybackState get() = _state.value

	/** The last non-overlay state, used to resolve [Seeking]/[Buffering]/[Crossfading]. */
	private var stable: PlaybackState = if (initial.isOverlay) Ready else initial

	/**
	 * Attempt to move to [target].
	 *
	 * @return true if the transition was legal (or a no-op) and applied, false if rejected.
	 */
	fun transitionTo(target: PlaybackState): Boolean {
		val from = _state.value
		if (target == from) return true
		if (!isAllowed(from, target)) return false
		if (!target.isOverlay) stable = target
		_state.value = target
		return true
	}

	/**
	 * Resolve an active overlay ([Seeking]/[Buffering]/[Crossfading]) back to the stable state
	 * it was layered on top of. No-op when not currently in an overlay.
	 */
	fun resolveOverlay(): Boolean {
		if (!_state.value.isOverlay) return false
		return transitionTo(stable)
	}

	fun reset() {
		stable = Idle
		_state.value = Idle
	}

	private fun isAllowed(from: PlaybackState, to: PlaybackState): Boolean {
		if (to == Error || to == Stopping || to == Preparing) return true
		return to in transitions.getValue(from)
	}

	private companion object {
		/**
		 * Declared edges (excluding the universal [Error]/[Stopping]/[Preparing] targets). Kept
		 * exhaustive so every state has an entry and the machine is fully specified.
		 */
		val transitions: Map<PlaybackState, Set<PlaybackState>> = mapOf(
			Idle to setOf(Preparing),
			Preparing to setOf(Ready, Buffering, Playing, Idle),
			Ready to setOf(Playing, Paused, Buffering, Seeking, Ended, Preparing),
			Playing to setOf(Paused, Buffering, Seeking, Crossfading, Ended, Ready),
			Paused to setOf(Playing, Seeking, Buffering, Ready, Preparing),
			Seeking to setOf(Playing, Paused, Buffering, Ready, Ended),
			Buffering to setOf(Playing, Paused, Ready, Seeking, Ended),
			Crossfading to setOf(Playing, Paused, Buffering, Seeking, Ready, Ended),
			Stopping to setOf(Idle),
			Ended to setOf(Preparing, Playing, Ready, Seeking, Idle),
			Error to setOf(Idle, Preparing),
		)
	}
}
