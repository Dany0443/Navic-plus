package dan.sonora.domain.manager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dan.sonora.domain.stats.InsightsRepository
import dan.sonora.domain.stats.StatsProviderRegistry
import dan.sonora.domain.stats.providerOrNull
import dan.sonora.util.core.Logger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background WorkManager job for periodic automatic Insights synchronization.
 *
 * Runs only when network is connected and respects battery optimizations, provider
 * rate limits, and Mutex/Atomic locks.
 */
class InsightsSyncWorker(
	appContext: Context,
	params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

	private val preferenceManager: PreferenceManager by inject()
	private val registry: StatsProviderRegistry by inject()
	private val syncManager: ScrobbleSyncManager by inject()
	private val insightsRepository: InsightsRepository by inject()

	override suspend fun doWork(): ListenableWorker.Result {
		Logger.i("InsightsSyncWorker", "Starting periodic background Insights sync...")

		if (!isSyncingLock.compareAndSet(false, true)) {
			Logger.i("InsightsSyncWorker", "Sync operation already in progress across repository. Skipping duplicate execution.")
			return ListenableWorker.Result.success()
		}

		try {
			if (!preferenceManager.insightsAutoSyncEnabled) {
				Logger.i("InsightsSyncWorker", "Insights auto-sync is disabled in settings. Skipping.")
				return ListenableWorker.Result.success()
			}

			val connectedProviders = registry.providers.filter { it.isConnected.value }
			if (connectedProviders.isEmpty()) {
				Logger.i("InsightsSyncWorker", "No connected stats providers found. Skipping.")
				return ListenableWorker.Result.success()
			}

			for (provider in connectedProviders) {
				try {
					if (provider.id in syncManager.syncingProviders.value) {
						Logger.i("InsightsSyncWorker", "Provider ${provider.id} is already syncing. Skipping duplicate sync.")
						continue
					}

					Logger.i("InsightsSyncWorker", "Triggering background sync for provider: ${provider.id}")
					syncManager.sync(provider)

					// Await sync completion for this provider
					syncManager.syncingProviders.first { provider.id !in it }
					Logger.i("InsightsSyncWorker", "Background sync completed for provider: ${provider.id}")
				} catch (e: Exception) {
					Logger.e("InsightsSyncWorker", "Background sync failed for provider ${provider.id}", e)
				}
			}

			// Refresh active provider's cached insights data after successful background sync
			runCatching {
				val active = insightsRepository.activeProvider.value.providerOrNull
				if (active != null && active.isConnected.value) {
					insightsRepository.getUserInfo()
					insightsRepository.getTopArtists()
					insightsRepository.getTopTracks()
				}
			}

			return ListenableWorker.Result.success()
		} finally {
			isSyncingLock.set(false)
		}
	}

	companion object {
		private val isSyncingLock = AtomicBoolean(false)
	}
}
