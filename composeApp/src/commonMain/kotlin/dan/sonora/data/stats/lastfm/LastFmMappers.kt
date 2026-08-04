package dan.sonora.data.stats.lastfm

import dan.sonora.domain.stats.ProviderArtist
import dan.sonora.domain.stats.ProviderScrobble
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.domain.stats.ProviderUserInfo
import dan.sonora.domain.stats.StatsPeriod

/** Last.fm reports play counts as strings; treat anything unparseable as zero. */
private fun String.toPlayCount(): Int = toIntOrNull() ?: 0

internal fun LastFmArtist.toProvider() = ProviderArtist(
	name = name,
	playCount = playcount.toPlayCount(),
	url = url,
	mbid = mbid?.takeIf { it.isNotBlank() }
)

internal fun LastFmTrack.toProvider() = ProviderTrack(
	name = name,
	// Last.fm returns the artist as either `name` or the `#text` alias depending on
	// the endpoint; collapse both here so the UI has a single field.
	artistName = artist?.let { it.name.ifBlank { it.text } }.orEmpty(),
	playCount = playcount.toPlayCount(),
	url = url
)

internal fun LastFmUserInfo.toProvider() = ProviderUserInfo(
	username = name,
	totalPlayCount = playcount.toPlayCount(),
	registeredAt = registered.unixtime.toLongOrNull()
)

/** Returns null for now-playing entries, which have no timestamp and are not history. */
internal fun RecentTrack.toProviderOrNull(): ProviderScrobble? {
	if (attr?.nowplaying == "true") return null
	val uts = date?.uts?.toLongOrNull() ?: return null
	return ProviderScrobble(
		timestamp = uts,
		trackName = name,
		artistName = artist.text,
		albumName = album.text.takeIf { it.isNotBlank() },
		url = url
	)
}

/** Null means "no period parameter", which Last.fm treats as overall. */
internal fun StatsPeriod.toLastFmPeriod(): String? = when (this) {
	StatsPeriod.Week -> "7day"
	StatsPeriod.Month -> "1month"
	StatsPeriod.Quarter -> "3month"
	StatsPeriod.HalfYear -> "6month"
	StatsPeriod.Year -> "12month"
	StatsPeriod.Overall -> null
}
