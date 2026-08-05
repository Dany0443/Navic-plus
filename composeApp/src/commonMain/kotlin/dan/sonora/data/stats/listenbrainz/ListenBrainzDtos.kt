package dan.sonora.data.stats.listenbrainz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ListenBrainz wire format. These types stay inside this package — everything outside
 * it consumes the mapped `Provider*` models from `domain.stats`.
 *
 * Every optional field carries a default: ListenBrainz omits keys entirely for listens
 * that were never matched to MusicBrainz, and a missing key without a default is a
 * deserialization failure rather than a null.
 */

@Serializable
internal data class ListensResponse(val payload: ListensPayload)

@Serializable
internal data class ListensPayload(
	val count: Int = 0,
	val listens: List<ListenBrainzListen> = emptyList(),
	/** Epoch seconds of the newest listen the account has, across all pages. */
	@SerialName("latest_listen_ts") val latestListenTs: Long? = null
)

@Serializable
internal data class ListenBrainzListen(
	@SerialName("listened_at") val listenedAt: Long? = null,
	@SerialName("track_metadata") val trackMetadata: TrackMetadata = TrackMetadata()
)

@Serializable
internal data class TrackMetadata(
	@SerialName("track_name") val trackName: String = "",
	@SerialName("artist_name") val artistName: String = "",
	@SerialName("release_name") val releaseName: String? = null,
	@SerialName("additional_info") val additionalInfo: AdditionalInfo = AdditionalInfo(),
	@SerialName("mbid_mapping") val mbidMapping: MbidMapping? = null
)

@Serializable
internal data class AdditionalInfo(
	@SerialName("recording_mbid") val recordingMbid: String? = null,
	@SerialName("artist_mbids") val artistMbids: List<String> = emptyList()
)

@Serializable
internal data class MbidMapping(
	@SerialName("recording_mbid") val recordingMbid: String? = null,
	@SerialName("artist_mbids") val artistMbids: List<String> = emptyList()
)

@Serializable
internal data class ListenCountResponse(val payload: ListenCountPayload)

@Serializable
internal data class ListenCountPayload(val count: Int = 0)

@Serializable
internal data class ArtistStatsResponse(val payload: ArtistStatsPayload)

@Serializable
internal data class ArtistStatsPayload(
	val artists: List<ListenBrainzArtist> = emptyList(),
	@SerialName("total_artist_count") val totalArtistCount: Int = 0
)

@Serializable
internal data class ListenBrainzArtist(
	@SerialName("artist_name") val artistName: String = "",
	@SerialName("artist_mbid") val artistMbid: String? = null,
	@SerialName("listen_count") val listenCount: Int = 0
)

@Serializable
internal data class RecordingStatsResponse(val payload: RecordingStatsPayload)

@Serializable
internal data class RecordingStatsPayload(
	val recordings: List<ListenBrainzRecording> = emptyList(),
	@SerialName("total_recording_count") val totalRecordingCount: Int = 0
)

@Serializable
internal data class ListenBrainzRecording(
	@SerialName("track_name") val trackName: String = "",
	@SerialName("artist_name") val artistName: String = "",
	@SerialName("recording_mbid") val recordingMbid: String? = null,
	@SerialName("listen_count") val listenCount: Int = 0
)
