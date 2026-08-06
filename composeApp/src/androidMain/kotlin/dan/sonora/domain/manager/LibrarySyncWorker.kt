package dan.sonora.domain.manager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dan.sonora.domain.repositories.DbRepository
import dan.sonora.util.core.Logger
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LibrarySyncWorker(
	appContext: Context,
	params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

	private val repository: DbRepository by inject()
	private val syncManager: SyncManager by inject()

	override suspend fun doWork(): Result {
		Logger.i("LibrarySyncWorker", "Starting full library sync...")
		return try {
			val result = repository.syncEverything { progress, message ->
				syncManager.updateSyncProgress(progress, message)
			}
			val error = result.exceptionOrNull()?.message
			syncManager.completeSync(if (result.isSuccess) null else error ?: "Sync failed")
			Logger.i("LibrarySyncWorker", if (result.isSuccess) "Full library sync complete." else "Full library sync failed: $error")
			if (result.isSuccess) Result.success() else Result.failure()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			syncManager.completeSync(e.message ?: "Sync failed")
			Logger.e("LibrarySyncWorker", "Full library sync failed: ${e.message}", e)
			Result.failure()
		}
	}
}
