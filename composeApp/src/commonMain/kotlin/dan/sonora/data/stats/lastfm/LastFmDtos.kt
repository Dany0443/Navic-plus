package dan.sonora.data.stats.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Last.fm wire format. These types stay inside this package — everything outside it
 * consumes the mapped `Provider*` models from `domain.stats`.
 *
 * Note `playcount` is a string in Last.fm's JSON; [LastFmMappers] parses it.
 */

@Serializable
data class LastFmArtist(
	val name: String,
	val url: String,
	val playcount: String,
	val mbid: String? = null
)

@Serializable
data class LastFmTrack(
	val name: String,
	val url: String,
	val playcount: String,
	val artist: LastFmTrackArtist? = null
)

@Serializable
data class LastFmTrackArtist(
	val name: String = "",
	@SerialName("#text") val text: String = ""
)

@Serializable
internal data class TopArtistsResponse(val topartists: Artists)

@Serializable
internal data class Artists(val artist: List<LastFmArtist>)

@Serializable
internal data class TopTracksResponse(val toptracks: Tracks)

@Serializable
internal data class Tracks(val track: List<LastFmTrack>)

@Serializable
data class RecentTracksResponse(val recenttracks: RecentTracks)

@Serializable
data class RecentTracks(
	val track: List<RecentTrack>,
	@SerialName("@attr") val attr: RecentTracksAttr
)

@Serializable
data class RecentTracksAttr(
	val page: String,
	val total: String,
	val user: String,
	val perPage: String,
	val totalPages: String
)

@Serializable
data class RecentTrack(
	val artist: RecentTrackArtist,
	val name: String,
	val album: RecentTrackAlbum,
	val url: String,
	val date: RecentTrackDate? = null,
	@SerialName("@attr") val attr: NowPlayingAttr? = null
)

@Serializable
data class RecentTrackArtist(
	val mbid: String,
	@SerialName("#text") val text: String
)

@Serializable
data class RecentTrackAlbum(
	val mbid: String,
	@SerialName("#text") val text: String
)

@Serializable
data class RecentTrackDate(
	val uts: String,
	@SerialName("#text") val text: String
)

@Serializable
data class NowPlayingAttr(
	val nowplaying: String
)

@Serializable
internal data class UserInfoResponse(val user: LastFmUserInfo)

@Serializable
data class LastFmUserInfo(
	val name: String,
	val playcount: String,
	val registered: Registered
)

@Serializable
data class Registered(
	val unixtime: String
)
