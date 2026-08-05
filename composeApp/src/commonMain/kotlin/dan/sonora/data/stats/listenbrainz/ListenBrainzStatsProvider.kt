package dan.sonora.data.stats.listenbrainz

import dan.sonora.domain.stats.ProviderArtist
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.domain.stats.ProviderUserInfo
import dan.sonora.domain.stats.ScrobblePage
import dan.sonora.domain.stats.StatsPeriod
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.UsernameConnectableProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * ListenBrainz as a statistics source.
 *
 * Listening data is public, so connecting only requires a username: there is no OAuth
 * handshake, token or password. [authorizationUrl] is therefore null, which is how the
 * onboarding knows to ask for a username instead of opening a browser.
 */
class ListenBrainzStatsProvider(
	private val api: ListenBrainzApi,
	private val authStore: ListenBrainzAuthStore
) : StatsProvider, UsernameConnectableProvider {

	override val id: String = ID

	override val displayName: String = "ListenBrainz"

	override val isConnected: StateFlow<Boolean> = authStore.isConnected

	override val accountName: String?
		get() = authStore.username

	/** Null: ListenBrainz connects by username, not through a browser. */
	override val authorizationUrl: String? = null

	override val defaultServerUrl: String = ListenBrainzAuthStore.DEFAULT_SERVER_URL

	// Progress counters for the current sync run. Plain fields are safe here because
	// ScrobbleSyncManager runs at most one sync per provider at a time.

	private var fetchedThisRun = 0

	/** Total listens on the account, read once per run as the progress denominator. */
	private var totalThisRun = 0

	override suspend fun connect(username: String, serverUrl: String) {
		val trimmed = username.trim()
		require(trimmed.isNotBlank()) { "Enter a ListenBrainz username" }

		val resolvedServer = serverUrl.normalizeServerUrl()
		if (!api.userExists(trimmed, resolvedServer)) {
			throw ListenBrainzUnknownUserException(trimmed)
		}
		authStore.connect(trimmed, resolvedServer)
	}

	override fun disconnect() = authStore.clear()

	override suspend fun getUserInfo(): ProviderUserInfo = ProviderUserInfo(
		username = authStore.username.orEmpty(),
		totalPlayCount = api.getListenCount()
	)

	override suspend fun getTopArtists(period: StatsPeriod): List<ProviderArtist> =
		api.getTopArtists(period.toListenBrainzRange()).map { it.toProvider() }

	override suspend fun getTopTracks(period: StatsPeriod): List<ProviderTrack> =
		api.getTopRecordings(period.toListenBrainzRange()).map { it.toProvider() }

	/**
	 * ListenBrainz has no page numbers: history is walked backwards by passing the
	 * oldest timestamp seen so far as `max_ts`, so the cursor is that timestamp.
	 */
	override suspend fun getRecentScrobbles(since: Long?, cursor: String?): ScrobblePage {
		val maxTs = cursor?.toLongOrNull()
		if (maxTs == null) {
			// First page of a run: reset progress and read the denominator once.
			fetchedThisRun = 0
			totalThisRun = runCatching { api.getListenCount() }.getOrDefault(0)
		}

		// `min_ts` is exclusive, and the caller's `since` is already "one past the
		// newest row held locally", so step back to avoid skipping that boundary listen.
		val payload = api.getListens(maxTs = maxTs, minTs = since?.minus(1))
		val scrobbles = payload.listens.mapNotNull { it.toProviderOrNull() }

		// Page by the raw listen timestamps, not the mapped ones: an entry dropped by
		// the mapper still has to advance the cursor or paging would loop on it.
		val oldest = payload.listens.mapNotNull { it.listenedAt }.minOrNull()

		// An empty page means the history in this direction is exhausted. A partial page
		// also ends the walk: a further request could only return listens older than the
		// oldest one seen, and there were not enough left to fill this page.
		val exhausted = oldest == null || payload.listens.size < ListenBrainzApi.PAGE_SIZE

		fetchedThisRun += scrobbles.size
		val fetched = fetchedThisRun
		val total = totalThisRun

		return ScrobblePage(
			scrobbles = scrobbles,
			nextCursor = if (exhausted) null else oldest.toString(),
			progress = when {
				exhausted -> 1f
				total > 0 -> (fetched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
				else -> 0f
			}
		)
	}

	companion object {
		const val ID = "listenbrainz"
	}
}
