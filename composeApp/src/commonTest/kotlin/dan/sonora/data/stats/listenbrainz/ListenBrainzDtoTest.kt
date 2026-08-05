package dan.sonora.data.stats.listenbrainz

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ListenBrainz omits keys entirely for anything it could not match, so every optional
 * field carries a default. A missing key without one is a deserialization failure, which
 * would abort a sync partway and leave an incomplete history behind.
 */
class ListenBrainzDtoTest {

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun parsesAMinimalListenWithEveryOptionalKeyAbsent() {
		val payload = json.decodeFromString<ListensResponse>(
			"""{"payload":{"listens":[{"listened_at":1771414109,
			   "track_metadata":{"track_name":"Some Resolve"}}]}}"""
		).payload

		val listen = payload.listens.single()
		assertEquals(1771414109, listen.listenedAt)
		assertEquals("Some Resolve", listen.trackMetadata.trackName)
		assertEquals("", listen.trackMetadata.artistName)
		assertNull(listen.trackMetadata.releaseName)
		assertNull(listen.trackMetadata.mbidMapping)
	}

	@Test
	fun parsesAnEmptyPayload() {
		// The 204 path substitutes this body for accounts with too little history.
		val payload = json.decodeFromString<ListensResponse>("""{"payload":{}}""").payload

		assertTrue(payload.listens.isEmpty())
		assertEquals(0, payload.count)
		assertNull(payload.latestListenTs)
	}

	@Test
	fun parsesEmptyStatsPayloads() {
		assertTrue(
			json.decodeFromString<ArtistStatsResponse>("""{"payload":{}}""").payload.artists.isEmpty()
		)
		assertTrue(
			json.decodeFromString<RecordingStatsResponse>("""{"payload":{}}""").payload.recordings.isEmpty()
		)
		assertEquals(0, json.decodeFromString<ListenCountResponse>("""{"payload":{}}""").payload.count)
	}

	@Test
	fun ignoresUnknownKeysSoNewApiFieldsDoNotBreakSync() {
		val payload = json.decodeFromString<ListensResponse>(
			"""{"payload":{"count":1,"some_new_field":"x","listens":[
			   {"listened_at":1771414109,"inserted_at":1771414107,"recording_msid":"abc",
			    "track_metadata":{"track_name":"Some Resolve","brand_new":{"a":1}}}]}}"""
		).payload

		assertEquals("Some Resolve", payload.listens.single().trackMetadata.trackName)
	}

	@Test
	fun parsesRealisticListenWithFullMetadata() {
		val payload = json.decodeFromString<ListensResponse>(
			"""{"payload":{"count":1,"latest_listen_ts":1771414109,"listens":[{
			   "listened_at":1771414109,
			   "track_metadata":{
			     "track_name":"Some Resolve","artist_name":"Röyksopp",
			     "release_name":"Profound Mysteries II",
			     "additional_info":{"recording_mbid":"30d08f4c","artist_mbids":["1c70a3fc"]},
			     "mbid_mapping":{"recording_mbid":"30d08f4c","artist_mbids":["1c70a3fc"]}}}]}}"""
		).payload

		assertEquals(1771414109, payload.latestListenTs)
		val metadata = payload.listens.single().trackMetadata
		assertEquals("Röyksopp", metadata.artistName)
		assertEquals("Profound Mysteries II", metadata.releaseName)
		assertEquals("30d08f4c", metadata.mbidMapping?.recordingMbid)
	}

	@Test
	fun parsesStatsWithMissingOptionalMbids() {
		val artists = json.decodeFromString<ArtistStatsResponse>(
			"""{"payload":{"artists":[{"artist_name":"Wax Tailor","listen_count":4298}],
			   "total_artist_count":8629}}"""
		).payload.artists

		assertEquals("Wax Tailor", artists.single().artistName)
		assertNull(artists.single().artistMbid)
	}
}
