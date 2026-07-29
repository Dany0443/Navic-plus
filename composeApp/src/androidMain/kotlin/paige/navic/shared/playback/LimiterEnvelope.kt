package paige.navic.shared.playback

import kotlin.math.exp

/** Stereo-linked gain envelope with instantaneous attenuation and exponential recovery. */
class LimiterEnvelope {
	private var releaseCoefficient = 0f
	var gain = 1f
		private set

	fun configure(sampleRate: Int, releaseMs: Int) {
		releaseCoefficient = exp(-1.0 / (sampleRate * releaseMs / 1_000.0)).toFloat()
		reset()
	}

	fun reset() {
		gain = 1f
	}

	fun next(requiredGain: Float): Float {
		gain = if (requiredGain < gain) {
			requiredGain
		} else {
			1f - (1f - gain) * releaseCoefficient
		}
		return gain
	}
}
