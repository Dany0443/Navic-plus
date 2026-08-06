package dan.sonora.domain.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class AndroidLibrarySyncEnqueuer(
	private val context: Context
) : LibrarySyncEnqueuer {

	override fun enqueue() {
		val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
			.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
			.setConstraints(
				Constraints.Builder()
					.setRequiredNetworkType(NetworkType.CONNECTED)
					.build()
			)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
	}

	companion object {
		const val WORK_NAME = "LibrarySync"
	}
}
