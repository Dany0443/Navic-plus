package dan.sonora.data.stats.lastfm

import dan.sonora.domain.stats.ProviderArtist
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.domain.stats.ProviderUserInfo
import dan.sonora.domain.stats.ScrobblePage
import dan.sonora.domain.stats.StatsPeriod
import dan.sonora.domain.stats.StatsProvider
import kotlinx.coroutines.flow.StateFlow

class LastFmStatsProvider(
	private val api: LastFmApi,
	private val authStore: LastFmAuthStore
) : StatsProvider {

	override val id: String = ID

	override val displayName: String = "Last.fm"

	override val isConnected: StateFlow<Boolean> = authStore.isAuthenticated

	override val accountName: String?
		get() = authStore.username

	override val authorizationUrl: String
		get() = authStore.authorizationUrl

	override fun disconnect() = authStore.clear()

	override suspend fun getUserInfo(): ProviderUserInfo =
		api.getUserInfo().toProvider()

	override suspend fun getTopArtists(period: StatsPeriod): List<ProviderArtist> =
		api.getTopArtists(period.toLastFmPeriod()).map { it.toProvider() }

	override suspend fun getTopTracks(period: StatsPeriod): List<ProviderTrack> =
		api.getTopTracks(period.toLastFmPeriod()).map { it.toProvider() }

	override suspend fun getRecentScrobbles(since: Long?, cursor: String?): ScrobblePage {
		val page = cursor?.toIntOrNull() ?: 1
		val response = api.getRecentTracks(limit = PAGE_SIZE, page = page, from = since)
		val totalPages = response.recenttracks.attr.totalPages.toIntOrNull() ?: 1

		return ScrobblePage(
			scrobbles = response.recenttracks.track.mapNotNull { it.toProviderOrNull() },
			nextCursor = (page + 1).takeIf { it <= totalPages }?.toString(),
			progress = if (totalPages > 0) page.toFloat() / totalPages.toFloat() else 1f
		)
	}

	companion object {
		const val ID = "lastfm"
		private const val PAGE_SIZE = 200
	}
}
