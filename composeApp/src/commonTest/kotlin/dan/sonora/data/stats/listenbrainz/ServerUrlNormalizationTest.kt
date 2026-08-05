package dan.sonora.data.stats.listenbrainz

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerUrlNormalizationTest {

	@Test
	fun keepsAWellFormedUrlUnchanged() {
		assertEquals(
			"https://api.listenbrainz.org",
			"https://api.listenbrainz.org".normalizeServerUrl()
		)
	}

	@Test
	fun addsSchemeToBareHost() {
		assertEquals("https://lb.example.org", "lb.example.org".normalizeServerUrl())
	}

	@Test
	fun stripsTrailingSlashSoPathsDoNotDouble() {
		assertEquals(
			"https://api.listenbrainz.org",
			"https://api.listenbrainz.org/".normalizeServerUrl()
		)
	}

	@Test
	fun trimsSurroundingWhitespaceFromPastedInput() {
		assertEquals(
			"https://api.listenbrainz.org",
			"  https://api.listenbrainz.org  ".normalizeServerUrl()
		)
	}

	@Test
	fun preservesExplicitHttpForSelfHostedInstances() {
		assertEquals("http://192.168.1.10:8100", "http://192.168.1.10:8100".normalizeServerUrl())
	}

	@Test
	fun fallsBackToTheHostedInstanceWhenBlank() {
		assertEquals(ListenBrainzAuthStore.DEFAULT_SERVER_URL, "   ".normalizeServerUrl())
	}
}
