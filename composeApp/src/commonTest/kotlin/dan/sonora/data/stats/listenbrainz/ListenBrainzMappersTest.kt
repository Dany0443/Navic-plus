package dan.sonora.data.stats.listenbrainz

import dan.sonora.domain.stats.StatsPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListenBrainzMappersTest {

	@Test
	fun mapsListenToProviderScrobble() {
		val scrobble = listen(
			listenedAt = 1771414109,
			trackName = "Some Resolve",
			artistName = "Röyksopp",
			releaseName = "Profound Mysteries II"
		).toProviderOrNull()

		assertEquals(1771414109, scrobble?.timestamp)
		assertEquals("Some Resolve", scrobble?.trackName)
		assertEquals("Röyksopp", scrobble?.artistName)
		assertEquals("Profound Mysteries II", scrobble?.albumName)
	}

	@Test
	fun dropsListenWithoutTimestamp() {
		// The "playing now" endpoint shares this shape and omits listened_at.
		assertNull(listen(listenedAt = null, trackName = "Some Resolve").toProviderOrNull())
	}

	@Test
	fun dropsListenWithoutTrackName() {
		assertNull(listen(listenedAt = 1771414109, trackName = "").toProviderOrNull())
	}

	@Test
	fun treatsBlankReleaseNameAsNoAlbum() {
		val scrobble = listen(
			listenedAt = 1771414109,
			trackName = "Some Resolve",
			releaseName = ""
		).toProviderOrNull()

		assertNull(scrobble?.albumName)
	}

	@Test
	fun prefersMbidMappingOverClientSuppliedRecordingId() {
		// mbid_mapping is ListenBrainz's own match and is more reliable than whatever
		// the submitting client put in additional_info.
		val scrobble = ListenBrainzListen(
			listenedAt = 1771414109,
			trackMetadata = TrackMetadata(
				trackName = "Some Resolve",
				artistName = "Röyksopp",
				additionalInfo = AdditionalInfo(recordingMbid = "client-supplied"),
				mbidMapping = MbidMapping(recordingMbid = "authoritative")
			)
		).toProviderOrNull()

		assertEquals("https://musicbrainz.org/recording/authoritative", scrobble?.url)
	}

	@Test
	fun fallsBackToAdditionalInfoWhenUnmatched() {
		val scrobble = ListenBrainzListen(
			listenedAt = 1771414109,
			trackMetadata = TrackMetadata(
				trackName = "Some Resolve",
				additionalInfo = AdditionalInfo(recordingMbid = "client-supplied")
			)
		).toProviderOrNull()

		assertEquals("https://musicbrainz.org/recording/client-supplied", scrobble?.url)
	}

	@Test
	fun leavesUrlNullWhenNothingMatchedMusicBrainz() {
		val scrobble = listen(listenedAt = 1771414109, trackName = "Some Resolve").toProviderOrNull()
		assertNull(scrobble?.url)
	}

	@Test
	fun mapsArtistStats() {
		val artist = ListenBrainzArtist(
			artistName = "Wax Tailor",
			artistMbid = "c24b47a1-2f52-44f3-a3c6-374dca844731",
			listenCount = 4298
		).toProvider()

		assertEquals("Wax Tailor", artist.name)
		assertEquals(4298, artist.playCount)
		assertEquals("c24b47a1-2f52-44f3-a3c6-374dca844731", artist.mbid)
	}

	@Test
	fun treatsBlankMbidAsAbsent() {
		val artist = ListenBrainzArtist(artistName = "Wax Tailor", artistMbid = "").toProvider()

		assertNull(artist.mbid)
		assertNull(artist.url)
	}

	@Test
	fun mapsEveryPeriodToARangeListenBrainzAccepts() {
		// These exact strings were verified against the live API; `half_yearly` in
		// particular does not follow the pattern of the others.
		assertEquals("week", StatsPeriod.Week.toListenBrainzRange())
		assertEquals("month", StatsPeriod.Month.toListenBrainzRange())
		assertEquals("quarter", StatsPeriod.Quarter.toListenBrainzRange())
		assertEquals("half_yearly", StatsPeriod.HalfYear.toListenBrainzRange())
		assertEquals("year", StatsPeriod.Year.toListenBrainzRange())
		assertEquals("all_time", StatsPeriod.Overall.toListenBrainzRange())
	}

	private fun listen(
		listenedAt: Long?,
		trackName: String = "Some Resolve",
		artistName: String = "Röyksopp",
		releaseName: String? = null
	) = ListenBrainzListen(
		listenedAt = listenedAt,
		trackMetadata = TrackMetadata(
			trackName = trackName,
			artistName = artistName,
			releaseName = releaseName
		)
	)
}
