package dan.sonora.domain.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dan.sonora.util.core.Logger
import java.util.concurrent.TimeUnit

actual class InsightsAutoSyncScheduler(private val context: Context) {

	actual fun scheduleAutoSync(enabled: Boolean, intervalMinutes: Long) {
		val workManager = WorkManager.getInstance(context)
		if (!enabled) {
			Logger.i("InsightsAutoSyncScheduler", "Cancelling periodic Insights background sync...")
			workManager.cancelUniqueWork(WORK_NAME)
			return
		}

		val sanitizedInterval = intervalMinutes.coerceAtLeast(15)
		Logger.i("InsightsAutoSyncScheduler", "Scheduling periodic Insights background sync every $sanitizedInterval minutes")

		val constraints = Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.build()

		val periodicWork = PeriodicWorkRequestBuilder<InsightsSyncWorker>(
			sanitizedInterval, TimeUnit.MINUTES
		)
			.setConstraints(constraints)
			.build()

		workManager.enqueueUniquePeriodicWork(
			WORK_NAME,
			ExistingPeriodicWorkPolicy.UPDATE,
			periodicWork
		)
	}

	companion object {
		const val WORK_NAME = "InsightsAutoSyncWorker"
	}
}
