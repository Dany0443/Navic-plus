package dan.sonora.domain.manager

import kotlinx.coroutines.flow.StateFlow

expect class PlaybackCacheManager {
	val cacheSizeFormatted: StateFlow<String>
	fun clearCache()
	fun refreshCacheSize()
	fun isTrackCached(songId: String): Boolean
}
