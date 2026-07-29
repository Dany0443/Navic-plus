package paige.navic.domain.playback

/**
 * Repeat behaviour for the playback queue.
 *
 * This is the engine's platform-neutral repeat model; the Android layer maps it to Media3's
 * `Player.REPEAT_MODE_*` constants at the edge (in `CrossfadePlayer`).
 */
enum class RepeatMode {
	/** Stop after the last item in play order. */
	OFF,

	/** Repeat the current item on natural end; manual next/previous still advances. */
	ONE,

	/** Wrap around to the start of play order after the last item. */
	ALL,
}
