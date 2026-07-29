package paige.navic.shared.playback

import paige.navic.domain.models.settings.LimiterSettings
import kotlin.math.abs
import kotlin.math.pow

/**
 * A zero-allocation, stereo-linked look-ahead peak limiter.
 *
 * Samples and their gain values are delayed together by five milliseconds. This allows a peak
 * to set its attenuation before it reaches the output; normal material therefore remains at
 * unity gain. The detector uses the linked maximum of the two channels.
 */
class LimiterProcessor {
	private val envelope = LimiterEnvelope()
	private var delayLeft = FloatArray(0)
	private var delayRight = FloatArray(0)
	private var delayGain = FloatArray(0)
	private var writeIndex = 0
	private var frameCount = 0
	private var threshold = 1f
	private var kneeLower = 1f
	private var kneeUpper = 1f
	private var ceiling = 1f
	private var appliedThresholdDb = Float.NaN
	private var wasEnabled = false

	var outputLeft = 0f
		private set
	var outputRight = 0f
		private set

	fun configure(sampleRate: Int) {
		frameCount = (sampleRate * LOOK_AHEAD_MS / 1_000).coerceAtLeast(1)
		delayLeft = FloatArray(frameCount)
		delayRight = FloatArray(frameCount)
		delayGain = FloatArray(frameCount) { 1f }
		envelope.configure(sampleRate, RELEASE_MS)
		writeIndex = 0
		wasEnabled = false
	}

	fun process(left: Float, right: Float, settings: LimiterSettings) {
		if (!settings.enabled) {
			wasEnabled = false
			outputLeft = left
			outputRight = right
			return
		}
		if (!wasEnabled) {
			reset()
			wasEnabled = true
		}
		if (settings.thresholdDb != appliedThresholdDb) {
			updateThreshold(settings.thresholdDb)
			appliedThresholdDb = settings.thresholdDb
		}

		val peak = maxOf(abs(left), abs(right))
		val gain = envelope.next(requiredGain(peak))
		val outputIndex = (writeIndex + 1) % frameCount
		outputLeft = (delayLeft[outputIndex] * delayGain[outputIndex]).coerceIn(-ceiling, ceiling)
		outputRight = (delayRight[outputIndex] * delayGain[outputIndex]).coerceIn(-ceiling, ceiling)
		delayLeft[writeIndex] = left
		delayRight[writeIndex] = right
		delayGain[writeIndex] = gain
		writeIndex = outputIndex
	}

	private fun reset() {
		envelope.reset()
		var index = 0
		while (index < frameCount) {
			delayLeft[index] = 0f
			delayRight[index] = 0f
			delayGain[index] = 1f
			index++
		}
		writeIndex = 0
	}

	private fun updateThreshold(thresholdDb: Float) {
		val clampedDb = thresholdDb.coerceIn(LimiterSettings.MIN_THRESHOLD_DB, LimiterSettings.MAX_THRESHOLD_DB)
		threshold = dbToLinear(clampedDb)
		val halfKnee = KNEE_DB / 2f
		kneeLower = dbToLinear((clampedDb - halfKnee).coerceAtLeast(LimiterSettings.MIN_THRESHOLD_DB))
		kneeUpper = dbToLinear((clampedDb + halfKnee).coerceAtMost(LimiterSettings.MAX_THRESHOLD_DB))
		ceiling = dbToLinear(OUTPUT_CEILING_DB)
	}

	private fun requiredGain(peak: Float): Float {
		if (peak <= kneeLower) return 1f
		val hardLimit = minOf(threshold, ceiling)
		val hardGain = (hardLimit / peak).coerceAtMost(1f)
		if (peak >= kneeUpper) return hardGain
		val position = ((peak - kneeLower) / (kneeUpper - kneeLower)).coerceIn(0f, 1f)
		val smoothPosition = position * position * (3f - 2f * position)
		return 1f + (hardGain - 1f) * smoothPosition
	}

	private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

	private companion object {
		const val LOOK_AHEAD_MS = 5
		const val RELEASE_MS = 120
		const val KNEE_DB = 2f
		const val OUTPUT_CEILING_DB = -0.5f
	}
}
