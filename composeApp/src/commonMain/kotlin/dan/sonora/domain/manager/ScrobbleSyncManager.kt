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

	private val _syncErrors = MutableStateFlow(emptyMap<String, String>())

	/** Why a provider's last sync failed, so a partial import is never shown as a full one. */
	val syncErrors: StateFlow<Map<String, String>> = _syncErrors.asStateFlow()

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
			_syncErrors.value = _syncErrors.value - provider.id
			try {
				// Incremental sync can only extend history upward from the newest row
				// held locally, so it is only safe once the whole history has been
				// imported at least once. Before that, ask for everything: a previous
				// run that failed partway has already committed its newest pages, and
				// asking only for newer scrobbles would strand everything below them.
				val backfilled = syncStore.isBackfillComplete(provider.id)
				val since = if (backfilled) {
					scrobbleDao.getLatestScrobbleTimestamp(provider.id)?.plus(1)
				} else {
					null
				}
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

				// Only reached when every page was walked without throwing, which is
				// what makes the incremental watermark trustworthy from here on.
				syncStore.setBackfillComplete(provider.id)
				syncStore.set(provider.id, Clock.System.now().toEpochMilliseconds() / 1000)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				// Surfaced rather than swallowed: a silent partial import is
				// indistinguishable from a complete one, which is how an incomplete
				// history went unnoticed for a release. The message carries no account
				// details or response bodies.
				_syncErrors.value = _syncErrors.value +
					(provider.id to (error.message ?: "Sync failed"))
			} finally {
				_syncingProviders.value = _syncingProviders.value - provider.id
				_syncProgress.value = _syncProgress.value + (provider.id to 1f)
			}
		}
	}

	/** Clears a provider's error once the user has seen it. */
	fun clearError(providerId: String) {
		_syncErrors.value = _syncErrors.value - providerId
	}
}
