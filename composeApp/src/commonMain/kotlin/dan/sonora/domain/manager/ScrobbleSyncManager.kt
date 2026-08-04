package dan.sonora.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dan.sonora.data.database.dao.ScrobbleDao
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.domain.stats.ActiveProvider
import dan.sonora.domain.stats.ProviderSyncStore
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.StatsProviderRegistry
import kotlin.time.Clock

/**
 * Pulls listening history from a stats provider into the local cache.
 *
 * Sync is always scoped to one provider: "Sync now" on a provider's settings page
 * touches only that provider's cache. Progress is tracked per provider so two can sync
 * independently without one reflecting the other's state.
 */
class ScrobbleSyncManager(
	private val registry: StatsProviderRegistry,
	private val scrobbleDao: ScrobbleDao,
	private val syncStore: ProviderSyncStore
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val _syncingProviders = MutableStateFlow(emptySet<String>())
	val syncingProviders: StateFlow<Set<String>> = _syncingProviders.asStateFlow()

	private val _syncProgress = MutableStateFlow(emptyMap<String, Float>())
	val syncProgress: StateFlow<Map<String, Float>> = _syncProgress.asStateFlow()

	/** Syncs whichever provider is currently active, if any. */
	fun syncActiveProvider() {
		val provider = (registry.activeProvider.value as? ActiveProvider.Connected)?.provider ?: return
		sync(provider)
	}

	fun sync(providerId: String) {
		registry.providers.firstOrNull { it.id == providerId }?.let(::sync)
	}

	fun sync(provider: StatsProvider) {
		if (provider.id in _syncingProviders.value || !provider.isConnected.value) return

		scope.launch(Dispatchers.IO) {
			_syncingProviders.value = _syncingProviders.value + provider.id
			_syncProgress.value = _syncProgress.value + (provider.id to 0f)
			try {
				// Only fetch scrobbles newer than what we already hold for this provider.
				val since = scrobbleDao.getLatestScrobbleTimestamp(provider.id)?.plus(1)
				var cursor: String? = null

				do {
					val page = provider.getRecentScrobbles(since = since, cursor = cursor)

					val entities = page.scrobbles.map { scrobble ->
						ScrobbleEntity(
							provider = provider.id,
							timestamp = scrobble.timestamp,
							trackName = scrobble.trackName,
							artistName = scrobble.artistName,
							albumName = scrobble.albumName,
							url = scrobble.url,
							coverArtUrl = null
						)
					}
					if (entities.isNotEmpty()) {
						scrobbleDao.insertScrobbles(entities)
					}

					_syncProgress.value = _syncProgress.value + (provider.id to page.progress)
					cursor = page.nextCursor
				} while (cursor != null)

				syncStore.set(provider.id, Clock.System.now().toEpochMilliseconds() / 1000)
			} catch (error: CancellationException) {
				throw error
			} catch (_: Exception) {
				// Sync can be retried later; do not log account or response details.
			} finally {
				_syncingProviders.value = _syncingProviders.value - provider.id
				_syncProgress.value = _syncProgress.value + (provider.id to 1f)
			}
		}
	}
}
