package dan.sonora.domain.playback

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class FadeCurveTest {

	@Test
	fun linear_endpointsAndMidpoint() {
		with(FadeCurve.LINEAR) {
			assertClose(0f, rising(0f))
			assertClose(1f, rising(1f))
			assertClose(0.5f, rising(0.5f))
			assertClose(1f, falling(0f))
			assertClose(0f, falling(1f))
		}
	}

	@Test
	fun equalPower_endpoints() {
		with(FadeCurve.EQUAL_POWER) {
			assertClose(0f, rising(0f))
			assertClose(1f, rising(1f))
			assertClose(1f, falling(0f))
			assertClose(0f, falling(1f))
		}
	}

	@Test
	fun equalPower_constantPowerAcrossOverlap() {
		val curve = FadeCurve.EQUAL_POWER
		var t = 0f
		while (t <= 1f) {
			val power = curve.rising(t) * curve.rising(t) + curve.falling(t) * curve.falling(t)
			assertClose(1f, power, tolerance = 1e-4f)
			t += 0.05f
		}
	}

	@Test
	fun clampsOutOfRangeInput() {
		assertClose(0f, FadeCurve.EQUAL_POWER.rising(-1f))
		assertClose(1f, FadeCurve.EQUAL_POWER.rising(2f))
	}

	private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-5f) {
		assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected but was $actual")
	}
}
