package dan.sonora.domain.stats

import kotlinx.coroutines.flow.StateFlow

/**
 * A source of listening statistics. Last.fm is one implementation; ListenBrainz,
 * Libre.fm and Maloja are intended to be others. Implementations own their wire
 * format and expose only [ProviderArtist]/[ProviderTrack]/[ProviderUserInfo]/
 * [ProviderScrobble], so the UI never depends on a specific backend.
 *
 * Adding a provider should require writing an implementation and registering it in
 * [StatsProviderRegistry] — no UI changes.
 */
interface StatsProvider {
	/** Stable id, persisted in preferences and stored on each cached scrobble row. */
	val id: String

	/** Human-readable name shown in the UI, e.g. "Last.fm". */
	val displayName: String

	/** Whether the user has connected this provider and it can serve data. */
	val isConnected: StateFlow<Boolean>

	/** The connected account name, or null when not connected. */
	val accountName: String?

	/**
	 * URL to open for browser-based authorization, or null for providers that
	 * connect by entering a username or token. Lets the onboarding choose a flow
	 * without knowing which provider it is offering.
	 */
	val authorizationUrl: String?

	/**
	 * Forget this provider's credentials. Callers are responsible for clearing the
	 * cached scrobbles and sync metadata that belong to it — see
	 * [InsightsRepository.disconnect], which does both.
	 */
	fun disconnect()

	suspend fun getUserInfo(): ProviderUserInfo

	suspend fun getTopArtists(period: StatsPeriod = StatsPeriod.Overall): List<ProviderArtist>

	suspend fun getTopTracks(period: StatsPeriod = StatsPeriod.Overall): List<ProviderTrack>

	/**
	 * One page of listening history. Pass [since] (epoch seconds) to fetch only newer
	 * scrobbles, and [cursor] from the previous [ScrobblePage] to continue paging.
	 */
	suspend fun getRecentScrobbles(since: Long? = null, cursor: String? = null): ScrobblePage
}
