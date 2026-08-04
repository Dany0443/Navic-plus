package dan.sonora.shared.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalized coefficients shared by every channel of one biquad filter.
 *
 * They are mutable so coefficient updates do not allocate on the audio thread.
 */
class BiquadCoefficients {
	var b0: Float = 1f
	var b1: Float = 0f
	var b2: Float = 0f
	var a1: Float = 0f
	var a2: Float = 0f

	fun setNormalized(
		b0: Double,
		b1: Double,
		b2: Double,
		a0: Double,
		a1: Double,
		a2: Double,
	) {
		if (!a0.isFinite() || a0 == 0.0) {
			setUnity()
			return
		}

		val inverseA0 = 1.0 / a0
		val normalizedB0 = b0 * inverseA0
		val normalizedB1 = b1 * inverseA0
		val normalizedB2 = b2 * inverseA0
		val normalizedA1 = a1 * inverseA0
		val normalizedA2 = a2 * inverseA0
		if (
			!normalizedB0.isFinite() || !normalizedB1.isFinite() || !normalizedB2.isFinite() ||
			!normalizedA1.isFinite() || !normalizedA2.isFinite()
		) {
			setUnity()
			return
		}

		this.b0 = normalizedB0.toFloat()
		this.b1 = normalizedB1.toFloat()
		this.b2 = normalizedB2.toFloat()
		this.a1 = normalizedA1.toFloat()
		this.a2 = normalizedA2.toFloat()
	}

	fun setUnity() {
		b0 = 1f
		b1 = 0f
		b2 = 0f
		a1 = 0f
		a2 = 0f
	}
}

/**
 * A reusable transposed direct-form II biquad.
 *
 * Coefficients are shared across channels; each channel owns independent delay state to preserve
 * stereo imaging and avoid cross-channel contamination.
 */
class BiquadFilter(
	private val coefficients: BiquadCoefficients,
) {
	private var z1 = FloatArray(0)
	private var z2 = FloatArray(0)

	fun configure(channelCount: Int) {
		if (z1.size != channelCount) {
			z1 = FloatArray(channelCount)
			z2 = FloatArray(channelCount)
		} else {
			reset()
		}
	}

	fun reset() {
		z1.fill(0f)
		z2.fill(0f)
	}

	fun process(input: Float, channel: Int): Float {
		val output = coefficients.b0 * input + z1[channel]
		z1[channel] = coefficients.b1 * input - coefficients.a1 * output + z2[channel]
		z2[channel] = coefficients.b2 * input - coefficients.a2 * output
		return output
	}
}

/** RBJ cookbook coefficient generators used by Sonora's tone controls and future graphic EQ. */
object RbjBiquadCoefficients {

	fun lowShelf(
		destination: BiquadCoefficients,
		sampleRate: Int,
		frequencyHz: Double,
		gainDb: Float,
		shelfSlope: Double = 1.0,
	) = shelf(destination, sampleRate, frequencyHz, gainDb, shelfSlope, highShelf = false)

	fun highShelf(
		destination: BiquadCoefficients,
		sampleRate: Int,
		frequencyHz: Double,
		gainDb: Float,
		shelfSlope: Double = 1.0,
	) = shelf(destination, sampleRate, frequencyHz, gainDb, shelfSlope, highShelf = true)

	/** Ready for the future five-band graphic EQ. */
	fun peaking(
		destination: BiquadCoefficients,
		sampleRate: Int,
		frequencyHz: Double,
		gainDb: Float,
		quality: Double,
	) {
		val gain = gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB).toDouble()
		if (gain == 0.0) {
			destination.setUnity()
			return
		}

		val omega = angularFrequency(sampleRate, frequencyHz)
		val alpha = sin(omega) / (2.0 * quality.coerceAtLeast(MIN_QUALITY))
		val amplitude = 10.0.pow(gain / 40.0)
		val cosine = cos(omega)
		destination.setNormalized(
			b0 = 1.0 + alpha * amplitude,
			b1 = -2.0 * cosine,
			b2 = 1.0 - alpha * amplitude,
			a0 = 1.0 + alpha / amplitude,
			a1 = -2.0 * cosine,
			a2 = 1.0 - alpha / amplitude,
		)
	}

	private fun shelf(
		destination: BiquadCoefficients,
		sampleRate: Int,
		frequencyHz: Double,
		gainDb: Float,
		shelfSlope: Double,
		highShelf: Boolean,
	) {
		val gain = gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB).toDouble()
		if (gain == 0.0) {
			destination.setUnity()
			return
		}

		val amplitude = 10.0.pow(gain / 40.0)
		val omega = angularFrequency(sampleRate, frequencyHz)
		val sine = sin(omega)
		val cosine = cos(omega)
		val slope = shelfSlope.coerceAtLeast(MIN_SHELF_SLOPE)
		val alpha = sine / 2.0 * sqrt((amplitude + 1.0 / amplitude) * (1.0 / slope - 1.0) + 2.0)
		val beta = 2.0 * sqrt(amplitude) * alpha
		val plus = amplitude + 1.0
		val minus = amplitude - 1.0

		if (highShelf) {
			destination.setNormalized(
				b0 = amplitude * (plus + minus * cosine + beta),
				b1 = -2.0 * amplitude * (minus + plus * cosine),
				b2 = amplitude * (plus + minus * cosine - beta),
				a0 = plus - minus * cosine + beta,
				a1 = 2.0 * (minus - plus * cosine),
				a2 = plus - minus * cosine - beta,
			)
		} else {
			destination.setNormalized(
				b0 = amplitude * (plus - minus * cosine + beta),
				b1 = 2.0 * amplitude * (minus - plus * cosine),
				b2 = amplitude * (plus - minus * cosine - beta),
				a0 = plus + minus * cosine + beta,
				a1 = -2.0 * (minus + plus * cosine),
				a2 = plus + minus * cosine - beta,
			)
		}
	}

	private fun angularFrequency(sampleRate: Int, frequencyHz: Double): Double {
		val safeSampleRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE).toDouble()
		val safeFrequency = frequencyHz.coerceIn(MIN_FREQUENCY_HZ, safeSampleRate * MAX_NYQUIST_FRACTION)
		return 2.0 * PI * safeFrequency / safeSampleRate
	}

	private const val MAX_GAIN_DB = 10f
	private const val MIN_SAMPLE_RATE = 8_000
	private const val MIN_FREQUENCY_HZ = 20.0
	private const val MAX_NYQUIST_FRACTION = 0.45
	private const val MIN_SHELF_SLOPE = 0.1
	private const val MIN_QUALITY = 0.1
}
