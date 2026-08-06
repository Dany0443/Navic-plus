package dan.sonora.domain.manager

import android.content.Context

class AndroidAutoCacheStarredScheduler(
	private val context: Context
) : AutoCacheStarredScheduler {

	override fun schedule() {
		AutoCacheStarredWorker.schedule(context)
	}

	override fun cancel() {
		AutoCacheStarredWorker.cancel(context)
	}
}
