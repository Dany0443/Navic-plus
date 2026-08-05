package dan.sonora.data.stats.listenbrainz

import dan.sonora.domain.stats.ProviderArtist
import dan.sonora.domain.stats.ProviderScrobble
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.domain.stats.StatsPeriod

internal fun ListenBrainzArtist.toProvider() = ProviderArtist(
	name = artistName,
	playCount = listenCount,
	url = artistMbid?.takeIf { it.isNotBlank() }?.let { "https://musicbrainz.org/artist/$it" },
	mbid = artistMbid?.takeIf { it.isNotBlank() }
)

internal fun ListenBrainzRecording.toProvider() = ProviderTrack(
	name = trackName,
	artistName = artistName,
	playCount = listenCount,
	url = recordingMbid?.takeIf { it.isNotBlank() }?.let { "https://musicbrainz.org/recording/$it" },
	mbid = recordingMbid?.takeIf { it.isNotBlank() }
)

/**
 * Returns null for entries without a timestamp — the "playing now" endpoint shares this
 * shape and omits `listened_at`, and an undated listen is not history.
 */
internal fun ListenBrainzListen.toProviderOrNull(): ProviderScrobble? {
	val listenedAt = listenedAt ?: return null
	val metadata = trackMetadata
	if (metadata.trackName.isBlank()) return null

	// `mbid_mapping` is ListenBrainz's own match against MusicBrainz and is more
	// reliable than whatever the submitting client sent in `additional_info`.
	val recordingMbid = metadata.mbidMapping?.recordingMbid?.takeIf { it.isNotBlank() }
		?: metadata.additionalInfo.recordingMbid?.takeIf { it.isNotBlank() }

	return ProviderScrobble(
		timestamp = listenedAt,
		trackName = metadata.trackName,
		artistName = metadata.artistName,
		albumName = metadata.releaseName?.takeIf { it.isNotBlank() },
		url = recordingMbid?.let { "https://musicbrainz.org/recording/$it" }
	)
}

/** ListenBrainz names its stat windows differently from Last.fm's `7day`/`1month`. */
internal fun StatsPeriod.toListenBrainzRange(): String = when (this) {
	StatsPeriod.Week -> "week"
	StatsPeriod.Month -> "month"
	StatsPeriod.Quarter -> "quarter"
	StatsPeriod.HalfYear -> "half_yearly"
	StatsPeriod.Year -> "year"
	StatsPeriod.Overall -> "all_time"
}
