package dan.sonora.domain.stats

/**
 * Listening statistics as the UI consumes them, independent of which backend
 * produced them. Every [StatsProvider] maps its wire format onto these types so no
 * provider-specific model reaches a ViewModel or composable.
 */

data class ProviderArtist(
	val name: String,
	val playCount: Int,
	/** Provider profile URL, also used to match against locally cached artists. */
	val url: String? = null,
	val mbid: String? = null
)

data class ProviderTrack(
	val name: String,
	/** Resolved by the provider's mapper; blank when the backend omits it. */
	val artistName: String,
	val playCount: Int,
	val url: String? = null,
	val mbid: String? = null
)

data class ProviderUserInfo(
	val username: String,
	val totalPlayCount: Int,
	/** Account creation time in epoch seconds, when the provider reports it. */
	val registeredAt: Long? = null
)

data class ProviderScrobble(
	/** Epoch seconds. */
	val timestamp: Long,
	val trackName: String,
	val artistName: String,
	val albumName: String? = null,
	val url: String? = null
)

/**
 * One page of scrobble history. [nextCursor] is opaque so providers can paginate by
 * page number (Last.fm) or by timestamp (ListenBrainz) without the caller knowing
 * which; null means the history is exhausted.
 */
data class ScrobblePage(
	val scrobbles: List<ProviderScrobble>,
	val nextCursor: String? = null,
	/** 0..1 for progress reporting, best-effort — providers that cannot know the
	 *  total may report 0f until the final page. */
	val progress: Float = 0f
)

enum class StatsPeriod {
	Week,
	Month,
	Quarter,
	HalfYear,
	Year,
	Overall
}
