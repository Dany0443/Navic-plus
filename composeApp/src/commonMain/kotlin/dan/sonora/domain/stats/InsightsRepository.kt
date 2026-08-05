package dan.sonora.domain.stats

import dan.sonora.data.database.dao.ScrobbleDao
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.domain.manager.ScrobbleSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The single entry point for listening statistics.
 *
 * Callers ask for stats and get whatever the active provider holds; they never name a
 * provider, so the UI contains no provider-specific logic. Every read is scoped to the
 * active provider's id, which is what keeps caches from ever being merged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsRepository(
	private val registry: StatsProviderRegistry,
	private val scrobbleDao: ScrobbleDao,
	private val syncManager: ScrobbleSyncManager,
	private val syncStore: ProviderSyncStore
) {
	val activeProvider: StateFlow<ActiveProvider> = registry.activeProvider

	/** Every registered provider, connected or not — drives onboarding and settings. */
	val providers: List<StatsProvider> = registry.providers

	init {
		// Sync timestamps live on disk; load them so "Last sync" is correct before any
		// sync has run this session.
		syncStore.refresh(providers.map { it.id })
	}

	val connectedProviders: StateFlow<List<StatsProvider>> = registry.connectedProviders

	/** Ids of providers currently syncing. */
	val syncingProviders: StateFlow<Set<String>> = syncManager.syncingProviders

	/** Why each provider's last sync failed, keyed by provider id. */
	val syncErrors: StateFlow<Map<String, String>> = syncManager.syncErrors

	val isSyncing: Flow<Boolean> = activeProvider.flatMapLatest { active ->
		val id = active.id ?: return@flatMapLatest flowOf(false)
		syncManager.syncingProviders.map { id in it }
	}

	val syncProgress: Flow<Float> = activeProvider.flatMapLatest { active ->
		val id = active.id ?: return@flatMapLatest flowOf(0f)
		syncManager.syncProgress.map { it[id] ?: 0f }
	}

	fun observeScrobbles(limit: Int): Flow<List<ScrobbleEntity>> =
		activeProvider.flatMapLatest { active ->
			val id = active.id ?: return@flatMapLatest flowOf(emptyList())
			scrobbleDao.getRecentScrobbles(id, limit)
		}

	fun observeScrobbleCount(): Flow<Int> =
		activeProvider.flatMapLatest { active ->
			val id = active.id ?: return@flatMapLatest flowOf(0)
			scrobbleDao.getTotalScrobbleCount(id)
		}

	suspend fun getScrobblesSince(sinceTimestamp: Long): List<ScrobbleEntity> {
		val id = activeProvider.value.id ?: return emptyList()
		return scrobbleDao.getScrobblesSinceList(id, sinceTimestamp)
	}

	suspend fun getUserInfo(): ProviderUserInfo = requireActive().getUserInfo()

	suspend fun getTopArtists(period: StatsPeriod = StatsPeriod.Overall): List<ProviderArtist> =
		requireActive().getTopArtists(period)

	suspend fun getTopTracks(period: StatsPeriod = StatsPeriod.Overall): List<ProviderTrack> =
		requireActive().getTopTracks(period)

	fun syncActiveProvider() = syncManager.syncActiveProvider()

	fun sync(providerId: String) = syncManager.sync(providerId)

	fun clearSyncError(providerId: String) = syncManager.clearError(providerId)

	/**
	 * Connects a provider that authenticates by username, then syncs it so Insights has
	 * data to show immediately. Throws if the username cannot be verified, leaving the
	 * provider disconnected.
	 */
	suspend fun connectWithUsername(providerId: String, username: String, serverUrl: String) {
		val provider = providers.firstOrNull { it.id == providerId }
		val connectable = provider as? UsernameConnectableProvider
			?: throw IllegalArgumentException("$providerId does not connect by username")

		connectable.connect(username, serverUrl)
		registry.setActive(providerId)
		syncManager.sync(provider)
	}

	fun setActive(providerId: String) = registry.setActive(providerId)

	/**
	 * Forgets a provider entirely: credentials, its cached scrobbles, and its sync
	 * metadata. Other providers' caches are untouched. If this was the active provider,
	 * the registry hands off to another connected one or falls back to
	 * [ActiveProvider.None].
	 */
	suspend fun disconnect(providerId: String) {
		val provider = providers.firstOrNull { it.id == providerId } ?: return
		provider.disconnect()
		scrobbleDao.deleteByProvider(providerId)
		syncStore.clear(providerId)
		registry.onDisconnected(providerId)
	}

	fun lastSyncedAt(providerId: String): Long? = syncStore.get(providerId)

	val lastSyncedAt: StateFlow<Map<String, Long>> = syncStore.lastSyncedAt

	private fun requireActive(): StatsProvider =
		activeProvider.value.providerOrNull ?: throw NoActiveProviderException()
}
