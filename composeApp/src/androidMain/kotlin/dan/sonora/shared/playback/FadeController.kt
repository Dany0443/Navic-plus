package dan.sonora.shared.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dan.sonora.domain.playback.FadeCurve

/** A sink for a single deck's volume. Implemented by wrapping `ExoPlayer.volume`. */
fun interface VolumeTarget {
	fun setVolume(volume: Float)
}

/** The two logical decks whose volumes the [FadeController] governs. */
enum class Deck { PRIMARY, SECONDARY }

/**
 * The single owner of every volume transition. **No other class writes `ExoPlayer.volume`.**
 *
 * The volume actually written to a deck is the product of independent factors, so that
 * ReplayGain, the fade envelope, ducking and the user volume never fight each other:
 *
 * ```
 * finalVolume(deck) = userVolume × replayGain(deck) × envelope(deck) × duck
 * ```
 *
 * Exactly one fade [Job] runs at a time; starting a new fade cancels the previous one, so
 * concurrent/overlapping fade loops are impossible. All mutation happens on the engine's
 * (playback-thread) [scope]; the class is not otherwise thread-safe.
 */
class FadeController(private val scope: CoroutineScope) {

	private var userVolume = 1f
	private var duck = 1f
	private val replayGain = floatArrayOf(1f, 1f)
	private val envelope = floatArrayOf(1f, 0f)
	private val targets = arrayOfNulls<VolumeTarget>(2)

	private var fadeJob: Job? = null

	/** Attach (or clear, with null) the volume sink for [deck] and apply its current level. */
	fun bind(deck: Deck, target: VolumeTarget?) {
		targets[deck.ordinal] = target
		write(deck)
	}

	fun setUserVolume(volume: Float) {
		userVolume = volume.coerceIn(0f, 1f)
		writeAll()
	}

	/** Ducking multiplier, e.g. ~0.2 during a transient "can duck" focus loss, 1.0 otherwise. */
	fun setDuck(factor: Float) {
		duck = factor.coerceIn(0f, 1f)
		writeAll()
	}

	fun setReplayGain(deck: Deck, gain: Float) {
		replayGain[deck.ordinal] = gain.coerceIn(0f, 1f)
		write(deck)
	}

	/** Set a deck's fade envelope immediately (cancels no running fade by itself). */
	fun setEnvelope(deck: Deck, level: Float) {
		envelope[deck.ordinal] = level.coerceIn(0f, 1f)
		write(deck)
	}

	/**
	 * Equal-power (or [curve]) crossfade: [out] envelope 1→0 while [in] envelope 0→1 over
	 * [durationMs]. Replaces any running fade. [onComplete] runs only on natural completion,
	 * not when cancelled/superseded.
	 */
	fun crossfade(
		out: Deck,
		into: Deck,
		durationMs: Long,
		curve: FadeCurve = FadeCurve.EQUAL_POWER,
		onComplete: () -> Unit = {},
	) {
		startFade(durationMs, onComplete) { t ->
			envelope[out.ordinal] = curve.falling(t); write(out)
			envelope[into.ordinal] = curve.rising(t); write(into)
		}
	}

	/** Ramp a single [deck] envelope to [target] over [durationMs] along [curve]. */
	fun fadeTo(
		deck: Deck,
		target: Float,
		durationMs: Long,
		curve: FadeCurve = FadeCurve.EQUAL_POWER,
		onComplete: () -> Unit = {},
	) {
		val from = envelope[deck.ordinal]
		val to = target.coerceIn(0f, 1f)
		startFade(durationMs, onComplete) { t ->
			val shaped = if (to >= from) curve.rising(t) else 1f - curve.falling(t)
			envelope[deck.ordinal] = (from + (to - from) * shaped).coerceIn(0f, 1f)
			write(deck)
		}
	}

	/** Cancel any running fade, leaving envelopes at their current values. */
	fun cancelFade() {
		fadeJob?.cancel()
		fadeJob = null
	}

	fun release() {
		cancelFade()
		targets[0] = null
		targets[1] = null
	}

	private inline fun startFade(
		durationMs: Long,
		noinline onComplete: () -> Unit,
		crossinline step: (t: Float) -> Unit,
	) {
		cancelFade()
		if (durationMs <= 0L) {
			step(1f)
			onComplete()
			return
		}
		fadeJob = scope.launch {
			val steps = (durationMs / TICK_MS).coerceAtLeast(1)
			for (i in 1..steps) {
				if (!isActive) return@launch
				step(i.toFloat() / steps)
				delay(TICK_MS)
			}
			step(1f)
			onComplete()
		}
	}

	private fun writeAll() {
		write(Deck.PRIMARY)
		write(Deck.SECONDARY)
	}

	private fun write(deck: Deck) {
		val i = deck.ordinal
		val v = (userVolume * replayGain[i] * envelope[i] * duck).coerceIn(0f, 1f)
		targets[i]?.setVolume(v)
	}

	private companion object {
		/** ~66 Hz update rate: smooth to the ear, negligible cost, single job. */
		const val TICK_MS = 15L
	}
}
