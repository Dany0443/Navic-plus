package dan.sonora.domain.manager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import dan.sonora.data.database.dao.SongDao
import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.util.core.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AutoCacheStarredWorker(
	appContext: Context,
	params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

	private val songDao: SongDao by inject()
	private val downloadManager: DownloadManager by inject()
	private val networkPolicy: DownloadNetworkPolicy by inject()
	private val preferenceManager: PreferenceManager by inject()

	override suspend fun doWork(): Result {
		Logger.i("AutoCacheStarredWorker", "Starting auto-cache starred songs worker...")
		if (!preferenceManager.autoCacheStarredWifi) {
			Logger.i("AutoCacheStarredWorker", "Auto-cache disabled in preferences.")
			return Result.success()
		}

		if (!networkPolicy.canExecuteDownload(DownloadTrigger.AUTOMATIC_BACKGROUND)) {
			Logger.w("AutoCacheStarredWorker", "Not on Wi-Fi/unmetered network, retrying later.")
			return Result.retry()
		}

		return try {
			val starredEntities = songDao.getPendingStarredDownloads()
			val missingDownloads = starredEntities
				.map { it.toDomainModel() }
				.filter { !downloadManager.isDownloaded(it.id) }

			if (missingDownloads.isEmpty()) {
				Logger.i("AutoCacheStarredWorker", "No missing starred downloads found.")
				return Result.success()
			}

			Logger.i("AutoCacheStarredWorker", "Throttled auto-caching ${missingDownloads.size} missing starred songs (concurrency=1)...")
			for (song in missingDownloads) {
				if (!networkPolicy.canExecuteDownload(DownloadTrigger.AUTOMATIC_BACKGROUND)) {
					Logger.w("AutoCacheStarredWorker", "Network changed during auto-cache batch execution, stopping.")
					break
				}

				downloadManager.downloadSong(song).join()

				// Lightweight throttle delay between consecutive downloads to keep CPU/network impact low
				delay(500L)
			}

			Logger.i("AutoCacheStarredWorker", "Throttled auto-cache starred worker complete.")
			Result.success()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("AutoCacheStarredWorker", "Failed to auto-cache starred tracks: ${e.message}", e)
			Result.failure()
		}
	}

	companion object {
		const val WORK_NAME = "auto_cache_starred_worker"

		fun schedule(context: Context) {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.UNMETERED)
				.setRequiresBatteryNotLow(true)
				.setRequiresStorageNotLow(true)
				.build()

			val workRequest = PeriodicWorkRequestBuilder<AutoCacheStarredWorker>(
				repeatInterval = 6, TimeUnit.HOURS
			)
				.setConstraints(constraints)
				.setBackoffCriteria(
					BackoffPolicy.EXPONENTIAL,
					WorkRequest.MIN_BACKOFF_MILLIS,
					TimeUnit.MILLISECONDS
				)
				.build()

			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				workRequest
			)
		}

		fun cancel(context: Context) {
			WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
		}
	}
}
