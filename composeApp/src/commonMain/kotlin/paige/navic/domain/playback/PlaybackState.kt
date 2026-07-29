package paige.navic.domain.playback

/**
 * Explicit playback states for the engine.
 *
 * This is the single internal truth for "what is playback doing right now", replacing the
 * scattered `isPaused` / `isLoading` / `isPlaying` booleans of the legacy design. The Android
 * layer maps these to Media3's `Player.STATE_*` + `playWhenReady` in `CrossfadePlayer`.
 *
 * [Seeking], [Buffering] and [Crossfading] are transient *overlays*: they sit on top of a
 * stable state ([Playing]/[Paused]/[Ready]) and resolve back to it when they finish.
 */
enum class PlaybackState {
	/** Nothing prepared; no current item, or fully released. */
	Idle,

	/** A source is being prepared but is not yet playable. */
	Preparing,

	/** Prepared and playable, awaiting a play/pause decision. */
	Ready,

	/** Actively rendering audio. */
	Playing,

	/** Prepared and playable but intentionally not advancing. */
	Paused,

	/** A seek is in progress (overlay). */
	Seeking,

	/** Rebuffering after a stall (overlay). */
	Buffering,

	/** Two decks are overlapping during a crossfade (overlay). */
	Crossfading,

	/** Stopping and tearing down toward [Idle]. */
	Stopping,

	/** Reached the end of the queue with nothing left to play. */
	Ended,

	/** A fatal playback error occurred. */
	Error;

	val isOverlay: Boolean get() = this == Seeking || this == Buffering || this == Crossfading

	/** True when the intent is for audio to be advancing. */
	val isActive: Boolean get() = this == Playing || this == Crossfading
}
