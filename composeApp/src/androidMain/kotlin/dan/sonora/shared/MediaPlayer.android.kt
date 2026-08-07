package dan.sonora.shared

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil3.imageLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dan.sonora.data.database.dao.AlbumDao
import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.domain.manager.AndroidScrobbleManager
import dan.sonora.domain.manager.ConnectivityManager
import dan.sonora.domain.manager.DownloadManager
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.manager.SnackBarManager
import dan.sonora.domain.manager.SyncManager
import dan.sonora.domain.models.DomainAlbum
import dan.sonora.domain.models.DomainExplicitStatus
import dan.sonora.domain.models.DomainRadio
import dan.sonora.domain.models.DomainSong
import dan.sonora.domain.models.DomainSongCollection
import dan.sonora.domain.repositories.PlayerStateRepository
import dan.sonora.shared.playback.ReplayGainTags
import dan.sonora.ui.core.PlayerUiState
import dan.sonora.util.core.Logger
import dan.sonora.util.core.ResourceProvider
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import coil3.PlatformContext as CoilPlatformContext

import dan.sonora.domain.repositories.SongRepository

class AndroidMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
	private val application: Application,
	private val albumDao: AlbumDao,
	private val platformContext: CoilPlatformContext,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager
) {
	private var controller: MediaController? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null

	init {
		connectToService()
	}

	private fun connectToService() {
		viewModelScope.launch {
			val sessionToken = PlaybackService.newSessionToken(application)
			controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
			controllerFuture?.addListener({
				controller = controllerFuture?.get()
				setupController()
			}, MoreExecutors.directExecutor())
		}
	}

	private fun getStreamInfo(id: String): Triple<Uri, Int, String?> {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid
		}
		val uri = sessionManager.api.getStreamUrl(id, bitrate, container?.takeIf { it.isNotBlank() })
			.toUri()
			.buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
		return Triple(uri, bitrate, container)
	}

	private fun getStreamUrl(id: String): Uri {
		return getStreamInfo(id).first
	}

	private fun setupController() {
		viewModelScope.launch {
			controller?.apply {
				addListener(object : Player.Listener {
					override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
						updatePlaybackState()
						skipUnavailableSong()
						checkAndAutoFillQueue()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						if (isPlaying) startProgressLoop() else saveStateNow()
						val intent =
							Intent("${application.packageName}.NOW_PLAYING_UPDATED").apply {
								setPackage(application.packageName)
								putExtra("isPlaying", isPlaying)
								putExtra(
									"title",
									_uiState.value.currentSong?.title ?: "Unknown song"
								)
								putExtra(
									"artist",
									_uiState.value.currentSong?.artistName ?: "Unknown artist"
								)
								putExtra(
									"artUrl",
									_uiState.value.currentSong?.coverArtId?.let {
										sessionManager.getCoverArtUrl(it)
									})
							}

						application.sendBroadcast(intent)
						updatePlaybackState()
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
						updatePlaybackState()
					}

					override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
						_uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
					}

					override fun onRepeatModeChanged(repeatMode: Int) {
						_uiState.update { it.copy(repeatMode = repeatMode) }
					}

					override fun onTracksChanged(tracks: Tracks) {
						updatePlaybackProperties(tracks)
					}

					override fun onTimelineChanged(timeline: Timeline, reason: Int) {
						updatePlaybackState()
					}
				})
				updatePlaybackState()
				updatePlaybackProperties(currentTracks)

				downloadManager.allDownloads.first()
				pendingSyncState?.let { state ->
					syncPlayerWithState(state)
					pendingSyncState = null
				}
				checkAndAutoFillQueue()

				combine(
					downloadManager.downloadedSongs,
					connectivityManager.isCellular,
					snapshotFlow { preferenceManager.streamingQualityWifi },
					snapshotFlow { preferenceManager.streamingQualityCellular },
					snapshotFlow { preferenceManager.isAdvancedTranscodingActive },
					snapshotFlow { preferenceManager.customMaxBitrateWifi },
					snapshotFlow { preferenceManager.customMaxBitrateCellular },
					snapshotFlow { preferenceManager.customFormatWifi },
					snapshotFlow { preferenceManager.customFormatCellular }
				) { it }.collectLatest { args ->
					@Suppress("UNCHECKED_CAST")
					val downloadedMap = args[0] as Map<String, String>
					val player = controller ?: return@collectLatest
					val currentIndex = player.currentMediaItemIndex

					for (i in 0 until player.mediaItemCount) {
						if (i == currentIndex) continue

						val item = player.getMediaItemAt(i)
						val id = item.mediaId
						val localPath = downloadedMap[id]

						val isCurrentlyLocal = item.localConfiguration?.uri?.scheme == "file"

						val newItem = if (localPath != null) {
							if (!isCurrentlyLocal) {
								item.buildUpon()
									.setUri(File(localPath).toUri())
									.build()
							} else null
						} else {
							val newUri = getStreamUrl(id)
							if (isCurrentlyLocal || item.localConfiguration?.uri != newUri) {
								item.buildUpon()
									.setUri(newUri)
									.build()
							} else null
						}

						if (newItem != null) {
							player.replaceMediaItem(i, newItem)
						}
					}
				}
			}
		}
	}

	/**
	 * strategically skip around in the queue until the
	 * current song is available while avoiding infinite
	 * loops
	 *
	 * this **INTENTIONALLY** does not check for if the song
	 * is not downloaded and if the device is offline
	 *
	 * this used to check for that but because there have
	 * been cases where the device is falsely identified
	 * as being offline that's no longer the case, so we
	 * just try to play the song anyway
	 */
	private fun skipUnavailableSong() {
		val currentSong = _uiState.value.currentSong ?: return
		if (!isExplicit(currentSong)) return
		Logger.i("MediaPlayer", "trying to skip unavailable song")
		val queue = _uiState.value.queue
		val currentIdx = queue.indexOf(currentSong)

		// look for the next available song, wrapping around, but stop before
		// we loop back past our own starting point
		val nextAvailableIdx = (1..queue.size)
			.map { offset -> (currentIdx + offset) % queue.size }
			.firstOrNull { index -> !isExplicit(queue[index]) }

		if (nextAvailableIdx == null) {
			Logger.i("MediaPlayer", "pausing because this song is unavailable and there isn't anything to skip to")
			controller?.pause()
			return
		}

		if (nextAvailableIdx <= currentIdx) {
			Logger.i("MediaPlayer", "skipping and pausing because the last song in the queue was unavailable")
			controller?.seekTo(nextAvailableIdx, 0L)
			controller?.pause()
		} else {
			// just skip to the next song
			controller?.seekTo(nextAvailableIdx, 0L)
		}
	}

	private fun refreshCurrentCollection(albumId: String) {
		if (loadingCollectionId == albumId) return
		loadingCollectionId = albumId

		viewModelScope.launch {
			runCatching {
				val album = albumDao.getAlbumById(albumId)

				_uiState.update { it.copy(currentCollection = album?.toDomainModel()) }
			}.onFailure {
				loadingCollectionId = null
			}
		}
	}

	private fun updatePlaybackState() {
		val controller = controller ?: return
		val index = controller.currentMediaItemIndex
		if (index == C.INDEX_UNSET) return

		val currentSong = _uiState.value.queue.getOrNull(index)

		val derivedCollection = currentSong?.let { song ->
			val stateCollection = _uiState.value.currentCollection

			if (stateCollection?.id == song.albumId.toString()) {
				stateCollection
			} else {
				refreshCurrentCollection(song.albumId.toString())
				null
			}
		}

		val metadata = controller.currentMediaItem?.mediaMetadata
		val requestedBitrate = metadata?.extras?.getInt(EXTRA_REQUESTED_BITRATE, -1)?.takeIf { it != -1 }
		val requestedMimeType = metadata?.extras?.getString(EXTRA_REQUESTED_MIME_TYPE)

		_uiState.update { state ->
			state.copy(
				currentIndex = index,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = !controller.playWhenReady,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode,
				requestedBitrate = requestedBitrate,
				requestedMimeType = requestedMimeType
			)
		}
		updateProgress()
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		viewModelScope.launch {
			val player = controller

			if (player == null) {
				pendingSyncState = state
				return@launch
			}

			if (state.queue.isEmpty() || player.mediaItemCount > 0) {
				updatePlaybackState()
				return@launch
			}

			val mediaItems = withContext(Dispatchers.Default) {
				state.queue.map { it.toMediaItem() }
			}

			player.setMediaItems(mediaItems)

			player.shuffleModeEnabled = state.isShuffleEnabled
			player.repeatMode = state.repeatMode
			player.playbackParameters = PlaybackParameters(state.playbackSpeed)

			val index = if (state.currentIndex in mediaItems.indices) state.currentIndex else 0

			val songDurationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L

			val position = if (songDurationMs > 0) {
				(state.progress * songDurationMs).toLong()
			} else {
				0L
			}

			player.seekTo(index, position)
			player.prepare()
			if (!state.isPaused) {
				player.play()
			}
		}
	}

	private fun startProgressLoop() {
		viewModelScope.launch {
			while (controller?.isPlaying == true) {
				val player = controller ?: break
				val duration = player.duration
				if (duration > 0) {
					val progress =
						(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
					_uiState.update { it.copy(progress = progress) }
				}
				delay(200.milliseconds)
			}
		}
	}

	private fun updateProgress() {
		controller?.let { player ->
			val duration = player.duration
			if (duration > 0) {
				val pos = player.currentPosition
				val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
			}
		}
	}

	@OptIn(UnstableApi::class)
	private fun updatePlaybackProperties(tracks: Tracks) {
		val audioGroup =
			tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
		if (audioGroup != null) {
			for (i in 0 until audioGroup.length) {
				if (audioGroup.isTrackSelected(i)) {
					val format = audioGroup.getTrackFormat(i)
					Logger.i("MediaPlayer", "Active Track Format: $format")
					_uiState.update { state ->
						state.copy(
							playbackBitrate = format.bitrate.takeIf { it > 0 },
							playbackSampleRate = format.sampleRate.takeIf { it > 0 },
							playbackMimeType = format.sampleMimeType
						)
					}
					break
				}
			}
		}
	}

	override fun addToQueueSingle(song: DomainSong, notify: Boolean) {
		viewModelScope.launch {
			controller?.addMediaItem(song.toMediaItem())
			_uiState.update { state ->
				val newQueue = state.queue + song
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) song else state.currentSong
				)
			}
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun addToQueue(collection: DomainSongCollection, notify: Boolean) {
		addToQueue(
			if (collection is DomainAlbum) collection.songs.sortedWith(
				compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)
			) else collection.songs,
			notify
		)
	}

	override fun addToQueue(songs: List<DomainSong>, notify: Boolean) {
		viewModelScope.launch {
			val items = songs.map { it.toMediaItem() }
			controller?.addMediaItems(items)
			_uiState.update { state ->
				val newQueue = state.queue + songs
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) songs.firstOrNull() else state.currentSong
				)
			}
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun removeFromQueue(index: Int) {
		viewModelScope.launch {
			controller?.removeMediaItem(index)
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply { removeAt(index) }
				val newIndex = when {
					index < state.currentIndex -> state.currentIndex - 1
					index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(
						newQueue.size - 1
					)

					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
		viewModelScope.launch {
			controller?.moveMediaItem(fromIndex, toIndex)
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply {
					val item = removeAt(fromIndex)
					add(toIndex, item)
				}
				val newIndex = when (state.currentIndex) {
					fromIndex -> toIndex
					in (fromIndex + 1)..toIndex -> state.currentIndex - 1
					in toIndex until fromIndex -> state.currentIndex + 1
					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun clearQueue() {
		viewModelScope.launch {
			_uiState.update {
				it.copy(
					queue = emptyList(),
					currentSong = null,
					currentIndex = -1,
					progress = 0f
				)
			}
			controller?.clearMediaItems()
		}
	}

	override fun playCollectionAt(collection: DomainSongCollection, index: Int) {
		viewModelScope.launch {
			val newCollection = if (collection is DomainAlbum) {
				collection.songs.sortedWith(compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				))
			} else {
				collection.songs
			}
			if (newCollection.isEmpty()) return@launch

			val targetIndex = index.coerceIn(0, newCollection.lastIndex)
			val mediaItems = withContext(Dispatchers.Default) {
				newCollection.map { it.toMediaItem() }
			}

			controller?.let { player ->
				player.setMediaItems(mediaItems, targetIndex, 0L)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = newCollection,
					currentCollection = collection,
					currentIndex = targetIndex,
					currentSong = newCollection[targetIndex],
					isPaused = false,
					progress = 0f
				)
			}
		}
	}

	override fun playAt(index: Int) {
		viewModelScope.launch {
			controller?.let { player ->
				if (index in 0 until player.mediaItemCount) {
					player.seekTo(index, 0L)
					player.play()
				}
			}
		}
	}

	override fun playNextSingle(song: DomainSong) {
		viewModelScope.launch {
			controller?.addMediaItem(
				_uiState.value.currentIndex + 1,
				withContext(Dispatchers.Default) { song.toMediaItem() }
			)
			_uiState.update { state ->
				val newQueue =
					if (state.queue.isEmpty())
						state.queue + song
					else
						state.queue.slice(0..state.currentIndex) + song + state.queue.slice(state.currentIndex + 1..<state.queue.size)
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) song else state.currentSong
				)
			}
			snackBarManager.notifyPlayNext()
		}
	}

	override fun playNext(collection: DomainSongCollection) {
		viewModelScope.launch {
			val (items, newCollection) = withContext(Dispatchers.Default) {
				val newCollection =
					if (collection is DomainAlbum) collection.songs.sortedWith(
						compareBy(
							{ it.discNumber },
							{ it.trackNumber }
						)) else collection.songs
				newCollection.map { it.toMediaItem() } to newCollection
			}
			controller?.addMediaItems(_uiState.value.currentIndex + 1, items)
			_uiState.update { state ->
				val newQueue =
					if (state.queue.isEmpty())
						state.queue + newCollection
					else
						state.queue.slice(0..state.currentIndex) + newCollection + state.queue.slice(
							state.currentIndex + 1..<state.queue.size
						)
				state.copy(
					queue = newQueue,
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) newCollection.firstOrNull() else state.currentSong
				)
			}
			snackBarManager.notifyPlayNext()
		}
	}

	override fun playRadio(radio: DomainRadio) {
		viewModelScope.launch {
			val radioId = "radio_${radio.name.hashCode()}"

			val dummyRadioSong = DomainSong(
				id = radioId,
				title = radio.name,
				artistName = "Live Radio",
				albumId = "radio_album",
				albumTitle = "Live Stream",
				duration = Duration.ZERO,
				trackNumber = 1,
				coverArtId = null,
				artistId = "",
				parentId = "",
				comment = null,
				discNumber = null,
				isrc = emptyList(),
				year = null,
				genre = null,
				genres = emptyList(),
				moods = emptyList(),
				bpm = null,
				contributors = emptyList(),
				playCount = 0,
				userRating = 0,
				averageRating = null,
				bitRate = null,
				bitDepth = null,
				sampleRate = null,
				audioChannelCount = null,
				replayGain = null,
				fileSize = 0,
				fileExtension = "",
				mimeType = "",
				filePath = radio.streamUrl,
				starredAt = null,
				musicBrainzId = null,
				explicitStatus = DomainExplicitStatus.Unknown
			)

			val metadata = MediaMetadata.Builder()
				.setTitle(radio.name)
				.setArtist("Live Radio")
				.setIsPlayable(true)
				.build()

			val mediaItem = MediaItem.Builder()
				.setUri(radio.streamUrl)
				.setMediaId("radio_${radio.name.hashCode()}")
				.setMediaMetadata(metadata)
				.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
				.build()

			controller?.let { player ->
				player.stop()
				player.clearMediaItems()
				player.setMediaItem(mediaItem)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = listOf(dummyRadioSong),
					currentIndex = 0,
					currentSong = dummyRadioSong,
					isLoading = true
				)
			}
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		viewModelScope.launch {
			val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
				val songs = collection.songs.shuffled()
				songs to songs.map { it.toMediaItem() }
			}

			controller?.let { player ->
				player.shuffleModeEnabled = false
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = shuffledSongs,
					currentIndex = 0,
					currentSong = shuffledSongs.firstOrNull()
				)
			}
		}
	}

	override fun pause() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.pause()
		}
	}

	override fun resume() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.play()
		}
	}

	override fun next() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
		}
	}

	override fun previous() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val controller = controller ?: return@launch
			if (controller.hasPreviousMediaItem() && controller.currentPosition <= 1000) {
				controller.seekToPreviousMediaItem()
			} else {
				controller.seekTo(0)
			}
		}
	}

	override fun toggleShuffle() {
		viewModelScope.launch {
			controller?.let { player ->
				player.shuffleModeEnabled = !player.shuffleModeEnabled
			}
		}
	}

	override fun toggleRepeat() {
		viewModelScope.launch {
			controller?.let { player ->
				player.repeatMode = when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
			}
		}
	}

	override fun seek(normalized: Float) {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.let {
				val target = (it.duration * normalized).toLong()
				it.seekTo(target)
				_uiState.update { state ->
					state.copy(progress = normalized)
				}
				saveStateNow()
			}
		}
	}

	override fun onCleared() {
		viewModelScope.launch {
			saveStateNow()
			super.onCleared()
			controllerFuture?.let { MediaController.releaseFuture(it) }
		}
	}

	override fun setPlaybackSpeed(value: Float) {
		viewModelScope.launch {
			controller?.setPlaybackSpeed(value)
		}
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	private fun DomainSong.toMediaItem(): MediaItem {
		val metadataBuilder = MediaMetadata.Builder()
			.setTitle(title)
			.setSubtitle(artistName)
			.setArtist(artistName)
			.setAlbumTitle(albumTitle)
			.setDurationMs(duration.inWholeMilliseconds)
			.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

		val localArtworkUri = coverArtId?.let { coverId ->
			val diskCache = platformContext.imageLoader.diskCache
			val snapshot = diskCache?.openSnapshot(coverId) ?: return@let null
			val fileUri = snapshot.data.toFile().toURI().toString().toUri()
			snapshot.close()
			fileUri
		}

		metadataBuilder.setArtworkUri(
			localArtworkUri ?: coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() }
		)

		var requestedBitrate: Int? = null
		var requestedMimeType: String? = null

		val uri = when {
			id.startsWith("local_") && !filePath.isNullOrEmpty() -> {
				File(filePath).toUri()
			}

			id.startsWith("radio_") && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

			else -> {
				val download = downloadManager.downloadedSongs.value[id]
				if (download != null) {
					requestedBitrate = download.bitrate
					requestedMimeType = download.format
					File(download.filePath!!).toUri()
				} else {
					val (streamUri, bitrate, container) = getStreamInfo(id)
					requestedBitrate = bitrate
					requestedMimeType = container
					streamUri
				}
			}
		}

		val extras = ReplayGainTags.writeInto(Bundle(), replayGain).apply {
			requestedBitrate?.let { putInt(EXTRA_REQUESTED_BITRATE, it) }
			requestedMimeType?.let { putString(EXTRA_REQUESTED_MIME_TYPE, it) }
		}

		metadataBuilder.setExtras(extras)
		val metadata = metadataBuilder.build()

		val builder = MediaItem.Builder()
			.setUri(uri)
			.setMediaId(id)
			.setMediaMetadata(metadata)

		if (id.startsWith("radio_")) {
			builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
		}

		return builder.build()
	}

	companion object {
		private const val EXTRA_REQUESTED_BITRATE = "requested_bitrate"
		private const val EXTRA_REQUESTED_MIME_TYPE = "requested_mime_type"
	}
}
