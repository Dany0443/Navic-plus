package dan.sonora.domain.manager

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dan.sonora.util.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
actual class PlaybackCacheManager(
	private val context: Context,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val cacheDir = File(context.cacheDir, "played_media_cache")

	private val _cacheSizeFormatted = MutableStateFlow("0 MB")
	actual val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted

	@get:Synchronized
	var simpleCache: SimpleCache? = null
		private set

	init {
		refreshCacheSize()
	}

	@Synchronized
	fun getOrCreateSimpleCache(): SimpleCache {
		simpleCache?.let { return it }

		val maxMb = preferenceManager.maxMediaCacheSizeMb
		val maxBytes = if (maxMb > 0) maxMb * 1024L * 1024L else Long.MAX_VALUE
		val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
		val databaseProvider = StandaloneDatabaseProvider(context)

		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}

		val cache = SimpleCache(cacheDir, evictor, databaseProvider)
		simpleCache = cache
		return cache
	}

	actual fun refreshCacheSize() {
		scope.launch {
			val bytes = calculateDirectorySize(cacheDir)
			val mb = bytes.toDouble() / (1024 * 1024)
			_cacheSizeFormatted.value = if (mb >= 1024) {
				val gb = mb / 1024
				"${(gb * 100).toInt() / 100.0} GB"
			} else {
				"${mb.toInt()} MB"
			}
		}
	}

	actual fun clearCache() {
		scope.launch {
			try {
				synchronized(this@PlaybackCacheManager) {
					simpleCache?.release()
					simpleCache = null
				}
				if (cacheDir.exists()) {
					cacheDir.deleteRecursively()
					cacheDir.mkdirs()
				}
				refreshCacheSize()
				Logger.i("PlaybackCacheManager", "Cleared played_media_cache successfully")
			} catch (e: Exception) {
				Logger.e("PlaybackCacheManager", "Failed to clear played_media_cache", e)
			}
		}
	}

	private fun calculateDirectorySize(dir: File): Long {
		if (!dir.exists()) return 0L
		var total = 0L
		val files = dir.listFiles() ?: return 0L
		for (file in files) {
			total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
		}
		return total
	}
}
