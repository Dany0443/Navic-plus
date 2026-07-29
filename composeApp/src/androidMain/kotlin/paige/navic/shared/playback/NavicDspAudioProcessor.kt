package paige.navic.shared.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import paige.navic.domain.models.settings.EqualizerSettings
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Per-deck hook for Navic's future PCM DSP chain.
 *
 * The chain applies shelves, five peaking filters, preamp, then optional loudness protection.
 * reusable output buffer is owned by [BaseAudioProcessor], so processing performs no
 * application allocations.
 */
class NavicDspAudioProcessor(
	private val settingsProvider: EqualizerSettingsProvider,
) : BaseAudioProcessor() {

	private var settingsSnapshot: EqualizerSettings = settingsProvider.snapshot()
	private val bassCoefficients = BiquadCoefficients()
	private val trebleCoefficients = BiquadCoefficients()
	private val peakingCoefficients = Array(BAND_COUNT) { BiquadCoefficients() }
	private val bassFilter = BiquadFilter(bassCoefficients)
	private val trebleFilter = BiquadFilter(trebleCoefficients)
	private val peakingFilters = Array(BAND_COUNT) { index -> BiquadFilter(peakingCoefficients[index]) }
	private val limiter = LimiterProcessor()

	private var currentPreampGain = 1f
	private var currentBassDb = 0f
	private var currentTrebleDb = 0f
	private var targetBassDb = 0f
	private var targetTrebleDb = 0f
	private val currentPeakingDb = FloatArray(BAND_COUNT)
	private val targetPeakingDb = FloatArray(BAND_COUNT)
	private val peakingDbStep = FloatArray(BAND_COUNT)
	private val peakingRampFramesRemaining = IntArray(BAND_COUNT)
	private val appliedPeakingDb = FloatArray(BAND_COUNT) { Float.NaN }
	private var bassDbStep = 0f
	private var trebleDbStep = 0f
	private var bassRampFramesRemaining = 0
	private var trebleRampFramesRemaining = 0
	private var appliedBassDb = Float.NaN
	private var appliedTrebleDb = Float.NaN
	private var controlIntervalFrames = 1
	private var toneRampFrames = 1
	private var framesUntilControlUpdate = 0
	private var equalizerWasEnabled = settingsSnapshot.enabled

	override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = when (inputAudioFormat.encoding) {
		C.ENCODING_PCM_16BIT,
		C.ENCODING_PCM_FLOAT -> inputAudioFormat

		else -> AudioFormat.NOT_SET
	}

	override fun onFlush(streamMetadata: StreamMetadata) {
		settingsSnapshot = settingsProvider.snapshot()
		currentPreampGain = preampGain(settingsSnapshot)
		val initialBassDb = bassGain(settingsSnapshot)
		val initialTrebleDb = trebleGain(settingsSnapshot)
		currentBassDb = initialBassDb
		currentTrebleDb = initialTrebleDb
		targetBassDb = initialBassDb
		targetTrebleDb = initialTrebleDb
		bassRampFramesRemaining = 0
		trebleRampFramesRemaining = 0
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			val initialPeakingDb = peakingGain(settingsSnapshot, bandIndex)
			currentPeakingDb[bandIndex] = initialPeakingDb
			targetPeakingDb[bandIndex] = initialPeakingDb
			peakingRampFramesRemaining[bandIndex] = 0
			appliedPeakingDb[bandIndex] = Float.NaN
			bandIndex++
		}
		controlIntervalFrames = (inputAudioFormat.sampleRate / CONTROL_RATE_HZ).coerceAtLeast(1)
		toneRampFrames = (inputAudioFormat.sampleRate * TONE_RAMP_DURATION_MS / 1_000).coerceAtLeast(1)
		framesUntilControlUpdate = 0
		equalizerWasEnabled = settingsSnapshot.enabled
		bassFilter.configure(inputAudioFormat.channelCount)
		limiter.configure(inputAudioFormat.sampleRate)
		trebleFilter.configure(inputAudioFormat.channelCount)
		bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			peakingFilters[bandIndex].configure(inputAudioFormat.channelCount)
			bandIndex++
		}
		appliedBassDb = Float.NaN
		appliedTrebleDb = Float.NaN
		updateToneCoefficients()
	}

	override fun queueInput(inputBuffer: ByteBuffer) {
		settingsSnapshot = settingsProvider.snapshot()
		if (!settingsSnapshot.enabled && equalizerWasEnabled) {
			resetEqualizer()
			currentPreampGain = 1f
		}
		equalizerWasEnabled = settingsSnapshot.enabled
		updateToneTargets(settingsSnapshot)
		val targetPreampGain = preampGain(settingsSnapshot)

		val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
		when (inputAudioFormat.encoding) {
			C.ENCODING_PCM_16BIT -> processPcm16Bit(inputBuffer, outputBuffer, targetPreampGain)
			C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, targetPreampGain)
			else -> outputBuffer.put(inputBuffer)
		}
		outputBuffer.flip()
	}

	private fun processPcm16Bit(
		inputBuffer: ByteBuffer,
		outputBuffer: ByteBuffer,
		targetGain: Float,
	) {
		val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
		val rampFrames = rampFrameCount(frameCount)
		val gainStep = if (rampFrames > 0) (targetGain - currentPreampGain) / rampFrames else 0f
		var frameIndex = 0
		var gain = currentPreampGain

		while (frameIndex < frameCount) {
			if (framesUntilControlUpdate == 0) {
				advanceToneControl()
				framesUntilControlUpdate = controlIntervalFrames
			}
			val framesToProcess = minOf(frameCount - frameIndex, framesUntilControlUpdate)
			var controlFrameIndex = 0
			while (controlFrameIndex < framesToProcess) {
				if (frameIndex < rampFrames) gain += gainStep else gain = targetGain
				processPcm16Frame(inputBuffer, outputBuffer, gain)
				frameIndex++
				controlFrameIndex++
			}
			framesUntilControlUpdate -= framesToProcess
		}

		currentPreampGain = targetGain
	}

	private fun processPcmFloat(
		inputBuffer: ByteBuffer,
		outputBuffer: ByteBuffer,
		targetGain: Float,
	) {
		val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
		val rampFrames = rampFrameCount(frameCount)
		val gainStep = if (rampFrames > 0) (targetGain - currentPreampGain) / rampFrames else 0f
		var frameIndex = 0
		var gain = currentPreampGain

		while (frameIndex < frameCount) {
			if (framesUntilControlUpdate == 0) {
				advanceToneControl()
				framesUntilControlUpdate = controlIntervalFrames
			}
			val framesToProcess = minOf(frameCount - frameIndex, framesUntilControlUpdate)
			var controlFrameIndex = 0
			while (controlFrameIndex < framesToProcess) {
				if (frameIndex < rampFrames) gain += gainStep else gain = targetGain
				processFloatFrame(inputBuffer, outputBuffer, gain)
				frameIndex++
				controlFrameIndex++
			}
			framesUntilControlUpdate -= framesToProcess
		}

		currentPreampGain = targetGain
	}

	private fun processPcm16Frame(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, gain: Float) {
		if (inputAudioFormat.channelCount == STEREO_CHANNEL_COUNT) {
			val left = processEqualizer(inputBuffer.short.toFloat() / PCM_16_SCALE, 0) * gain
			val right = processEqualizer(inputBuffer.short.toFloat() / PCM_16_SCALE, 1) * gain
			limiter.process(left, right, settingsSnapshot.limiter)
			outputBuffer.putShort(toPcm16(limiter.outputLeft))
			outputBuffer.putShort(toPcm16(limiter.outputRight))
			return
		}
		var channelIndex = 0
		while (channelIndex < inputAudioFormat.channelCount) {
			val input = inputBuffer.short.toFloat() / PCM_16_SCALE
			outputBuffer.putShort(toPcm16(processEqualizer(input, channelIndex) * gain))
			channelIndex++
		}
	}

	private fun processFloatFrame(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, gain: Float) {
		if (inputAudioFormat.channelCount == STEREO_CHANNEL_COUNT) {
			val left = processEqualizer(inputBuffer.float, 0) * gain
			val right = processEqualizer(inputBuffer.float, 1) * gain
			limiter.process(left, right, settingsSnapshot.limiter)
			outputBuffer.putFloat(limiter.outputLeft)
			outputBuffer.putFloat(limiter.outputRight)
			return
		}
		var channelIndex = 0
		while (channelIndex < inputAudioFormat.channelCount) {
			outputBuffer.putFloat((processEqualizer(inputBuffer.float, channelIndex) * gain).coerceIn(-1f, 1f))
			channelIndex++
		}
	}

	private fun processEqualizer(input: Float, channelIndex: Int): Float {
		if (!settingsSnapshot.enabled) return input
		var filtered = bassFilter.process(input, channelIndex)
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			filtered = peakingFilters[bandIndex].process(filtered, channelIndex)
			bandIndex++
		}
		return trebleFilter.process(filtered, channelIndex)
	}

	private fun resetEqualizer() {
		bassFilter.reset()
		trebleFilter.reset()
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			peakingFilters[bandIndex].reset()
			bandIndex++
		}
	}

	private fun rampFrameCount(frameCount: Int): Int = minOf(
		frameCount,
		(inputAudioFormat.sampleRate * GAIN_RAMP_DURATION_MS / 1_000).coerceAtLeast(1),
	)

	private fun preampGain(settings: EqualizerSettings): Float = if (settings.enabled) {
		10f.pow(settings.preampDb / 20f)
	} else {
		1f
	}

	private fun bassGain(settings: EqualizerSettings): Float =
		if (settings.enabled) settings.bassDb.coerceIn(MIN_TONE_GAIN_DB, MAX_TONE_GAIN_DB) else 0f

	private fun trebleGain(settings: EqualizerSettings): Float =
		if (settings.enabled) settings.trebleDb.coerceIn(MIN_TONE_GAIN_DB, MAX_TONE_GAIN_DB) else 0f

	private fun peakingGain(settings: EqualizerSettings, bandIndex: Int): Float = if (settings.enabled) {
		when (bandIndex) {
			0 -> settings.fiveBandGainsDb.band1Db
			1 -> settings.fiveBandGainsDb.band2Db
			2 -> settings.fiveBandGainsDb.band3Db
			3 -> settings.fiveBandGainsDb.band4Db
			else -> settings.fiveBandGainsDb.band5Db
		}.coerceIn(MIN_TONE_GAIN_DB, MAX_TONE_GAIN_DB)
	} else {
		0f
	}

	private fun updateToneTargets(settings: EqualizerSettings) {
		setBassTarget(bassGain(settings))
		setTrebleTarget(trebleGain(settings))
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			setPeakingTarget(bandIndex, peakingGain(settings, bandIndex))
			bandIndex++
		}
	}

	private fun setBassTarget(target: Float) {
		if (target == targetBassDb) return
		targetBassDb = target
		bassRampFramesRemaining = toneRampFrames
		bassDbStep = (target - currentBassDb) / toneRampFrames
	}

	private fun setTrebleTarget(target: Float) {
		if (target == targetTrebleDb) return
		targetTrebleDb = target
		trebleRampFramesRemaining = toneRampFrames
		trebleDbStep = (target - currentTrebleDb) / toneRampFrames
	}

	private fun setPeakingTarget(bandIndex: Int, target: Float) {
		if (target == targetPeakingDb[bandIndex]) return
		targetPeakingDb[bandIndex] = target
		peakingRampFramesRemaining[bandIndex] = toneRampFrames
		peakingDbStep[bandIndex] = (target - currentPeakingDb[bandIndex]) / toneRampFrames
	}

	private fun advanceToneControl() {
		advanceBassRamp(controlIntervalFrames)
		advanceTrebleRamp(controlIntervalFrames)
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			advancePeakingRamp(bandIndex, controlIntervalFrames)
			bandIndex++
		}
		updateToneCoefficients()
	}

	private fun advanceBassRamp(frames: Int) {
		if (bassRampFramesRemaining == 0) return
		val advancedFrames = minOf(frames, bassRampFramesRemaining)
		currentBassDb += bassDbStep * advancedFrames
		bassRampFramesRemaining -= advancedFrames
		if (bassRampFramesRemaining == 0) currentBassDb = targetBassDb
	}

	private fun advanceTrebleRamp(frames: Int) {
		if (trebleRampFramesRemaining == 0) return
		val advancedFrames = minOf(frames, trebleRampFramesRemaining)
		currentTrebleDb += trebleDbStep * advancedFrames
		trebleRampFramesRemaining -= advancedFrames
		if (trebleRampFramesRemaining == 0) currentTrebleDb = targetTrebleDb
	}

	private fun advancePeakingRamp(bandIndex: Int, frames: Int) {
		if (peakingRampFramesRemaining[bandIndex] == 0) return
		val advancedFrames = minOf(frames, peakingRampFramesRemaining[bandIndex])
		currentPeakingDb[bandIndex] += peakingDbStep[bandIndex] * advancedFrames
		peakingRampFramesRemaining[bandIndex] -= advancedFrames
		if (peakingRampFramesRemaining[bandIndex] == 0) {
			currentPeakingDb[bandIndex] = targetPeakingDb[bandIndex]
		}
	}

	private fun updateToneCoefficients() {
		if (currentBassDb != appliedBassDb) {
			RbjBiquadCoefficients.lowShelf(
				destination = bassCoefficients,
				sampleRate = inputAudioFormat.sampleRate,
				frequencyHz = BASS_FREQUENCY_HZ,
				gainDb = currentBassDb,
			)
			appliedBassDb = currentBassDb
		}
		if (currentTrebleDb != appliedTrebleDb) {
			RbjBiquadCoefficients.highShelf(
				destination = trebleCoefficients,
				sampleRate = inputAudioFormat.sampleRate,
				frequencyHz = TREBLE_FREQUENCY_HZ,
				gainDb = currentTrebleDb,
			)
			appliedTrebleDb = currentTrebleDb
		}
		var bandIndex = 0
		while (bandIndex < BAND_COUNT) {
			if (currentPeakingDb[bandIndex] != appliedPeakingDb[bandIndex]) {
				RbjBiquadCoefficients.peaking(
					destination = peakingCoefficients[bandIndex],
					sampleRate = inputAudioFormat.sampleRate,
					frequencyHz = PEAKING_FREQUENCIES_HZ[bandIndex],
					gainDb = currentPeakingDb[bandIndex],
					quality = PEAKING_QUALITY,
				)
				appliedPeakingDb[bandIndex] = currentPeakingDb[bandIndex]
			}
			bandIndex++
		}
	}

	private fun toPcm16(sample: Float): Short = (sample.coerceIn(-1f, 1f) * PCM_16_SCALE)
		.roundToInt()
		.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
		.toShort()

	private companion object {
		const val GAIN_RAMP_DURATION_MS = 5
		const val TONE_RAMP_DURATION_MS = 25
		const val CONTROL_RATE_HZ = 250
		const val BASS_FREQUENCY_HZ = 100.0
		const val TREBLE_FREQUENCY_HZ = 8_000.0
		const val BAND_COUNT = 5
		val PEAKING_FREQUENCIES_HZ = doubleArrayOf(60.0, 230.0, 910.0, 3_600.0, 14_000.0)
		const val PEAKING_QUALITY = 1.0
		const val MIN_TONE_GAIN_DB = -10f
		const val MAX_TONE_GAIN_DB = 10f
		const val PCM_16_SCALE = 32_768f
		const val STEREO_CHANNEL_COUNT = 2
	}
}
