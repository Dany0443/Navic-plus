package paige.navic.domain.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Volume ramp shapes for fades and crossfades.
 *
 * [rising] goes 0→1 and [falling] goes 1→0 over normalized time `t` in `[0, 1]`.
 *
 * [EQUAL_POWER] keeps `rising(t)^2 + falling(t)^2 == 1`, i.e. constant acoustic power through
 * an overlap, so a crossfade has no audible loudness dip in the middle ("true" Spotify-style).
 * [LINEAR] is a straight ramp and dips to ~0.5 power mid-crossfade.
 *
 * Pure and platform-neutral so the curve math is unit-tested independently of any player.
 */
enum class FadeCurve {
	LINEAR,
	EQUAL_POWER;

	/** Rising envelope 0→1 at normalized time [t]. */
	fun rising(t: Float): Float {
		val x = t.coerceIn(0f, 1f)
		return when (this) {
			LINEAR -> x
			EQUAL_POWER -> sin(x * HALF_PI)
		}
	}

	/** Falling envelope 1→0 at normalized time [t]. */
	fun falling(t: Float): Float {
		val x = t.coerceIn(0f, 1f)
		return when (this) {
			LINEAR -> 1f - x
			EQUAL_POWER -> cos(x * HALF_PI)
		}
	}

	private companion object {
		const val HALF_PI = (PI / 2).toFloat()
	}
}
