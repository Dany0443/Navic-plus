package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.ScrobbleDao
import paige.navic.data.database.entities.ScrobbleEntity

class LastFmSyncManager(
	private val lastFmManager: LastFmManager,
	private val scrobbleDao: ScrobbleDao
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _isSyncing = MutableStateFlow(false)
	val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

	private val _syncProgress = MutableStateFlow(0f)
	val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

	fun syncRecentScrobbles() {
		if (_isSyncing.value || !lastFmManager.isAuthenticated.value) return

		scope.launch(Dispatchers.IO) {
			_isSyncing.value = true
			_syncProgress.value = 0f
			try {
				val latestScrobbleTimestamp = scrobbleDao.getLatestScrobbleTimestamp()
				var page = 1
				var totalPages = 1
				var hasMore = true

				while (hasMore) {
					// We fetch from latest timestamp to only get new scrobbles
					val response = lastFmManager.getRecentTracks(
						limit = 200,
						page = page,
						from = latestScrobbleTimestamp?.plus(1)
					)

					totalPages = response.recenttracks.attr.totalPages.toIntOrNull() ?: 1

					val entities = response.recenttracks.track
						.filter { it.attr?.nowplaying != "true" } // Skip currently playing
						.mapNotNull { track ->
							val uts = track.date?.uts?.toLongOrNull() ?: return@mapNotNull null
							ScrobbleEntity(
								timestamp = uts,
								trackName = track.name,
								artistName = track.artist.text,
								albumName = track.album.text.takeIf { it.isNotBlank() },
								url = track.url,
								coverArtUrl = null // We don't have this in recenttracks reliably
							)
						}

					if (entities.isNotEmpty()) {
						scrobbleDao.insertScrobbles(entities)
					}

					_syncProgress.value = if (totalPages > 0) page.toFloat() / totalPages.toFloat() else 1f

					page++
					if (page > totalPages) {
						hasMore = false
					}
				}
			} catch (error: CancellationException) {
				throw error
			} catch (_: Exception) {
				// Sync can be retried later; do not log account or response details.
			} finally {
				_isSyncing.value = false
				_syncProgress.value = 1f
			}
		}
	}
}
