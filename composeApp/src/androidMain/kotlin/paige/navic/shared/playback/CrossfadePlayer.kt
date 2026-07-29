package paige.navic.shared.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Tracks
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import paige.navic.domain.playback.PlaybackState
import paige.navic.domain.playback.QueueEntry
import paige.navic.domain.playback.RepeatMode

/**
 * The single public `Player`, backed by [SimpleBasePlayer].
 *
 * This is the only player the `MediaSession`, media notification, Android Auto, Bluetooth,
 * headset controls, widgets, scrobbler and UI ever see — the two internal `ExoPlayer` decks
 * stay hidden inside [PlaybackEngine]. It has no playback logic of its own: [getState] projects
 * the engine's queue/state into a Media3 [State], and each `handle*` command is routed to the
 * engine (which mutates on the same playback thread, then calls [invalidateState]).
 *
 * Because Media3's `SimpleBasePlayer` navigates its timeline strictly linearly (it has no real
 * shuffle order), shuffle/repeat ordering is owned by the engine's `QueueManager`; next/previous
 * commands are intercepted here and delegated rather than resolved from the timeline.
 */
class CrossfadePlayer(
	looper: Looper,
	private val engine: PlaybackEngine,
) : SimpleBasePlayer(looper) {

	/**
	 * Bridge so the engine (which lives outside this class) can request a state refresh;
	 * `invalidateState()` is `protected` in [SimpleBasePlayer]. Must be called on the player thread.
	 */
	fun requestInvalidateState() = invalidateState()

	override fun getState(): State {
		val snapshot = engine.queueSnapshot
		val mappedState = mapPlaybackState(engine.playbackState)
		val builder = State.Builder()
			.setAvailableCommands(AVAILABLE_COMMANDS)
			.setPlaybackState(mappedState)
			.setPlayWhenReady(engine.playWhenReady(), engine.playWhenReadyReason())
			.setShuffleModeEnabled(snapshot.shuffleEnabled)
			.setRepeatMode(mapRepeatToMedia3(snapshot.repeatMode))
			.setPlaybackParameters(engine.playbackParameters)
			.setVolume(engine.volume)
			// isLoading is only valid when not in STATE_IDLE or STATE_ENDED
			.setIsLoading(engine.isLoading && mappedState != Player.STATE_IDLE && mappedState != Player.STATE_ENDED)
			.setContentPositionMs(PositionSupplier { engine.positionMs })
			.setContentBufferedPositionMs(PositionSupplier { engine.bufferedPositionMs })
			.setMaxSeekToPreviousPositionMs(MAX_SEEK_TO_PREVIOUS_MS)

		// playerError must only be set when playbackState is STATE_IDLE
		if (mappedState == Player.STATE_IDLE) engine.error?.let { builder.setPlayerError(it) }

		if (snapshot.entries.isNotEmpty()) {
			val current = snapshot.currentIndex.coerceIn(0, snapshot.entries.lastIndex)
			builder.setPlaylist(
				snapshot.entries.mapIndexed { index, entry -> mediaItemData(entry, index == current) },
			)
			builder.setCurrentMediaItemIndex(current)
		}
		return builder.build()
	}

	private fun mediaItemData(entry: QueueEntry<MediaItem>, isCurrent: Boolean): MediaItemData {
		val metadataMs = entry.item.mediaMetadata.durationMs ?: C.TIME_UNSET
		val durationMs = if (isCurrent && engine.currentDurationMs != C.TIME_UNSET) {
			engine.currentDurationMs
		} else {
			metadataMs
		}
		return MediaItemData.Builder(entry.id)
			.setMediaItem(entry.item)
			.setMediaMetadata(entry.item.mediaMetadata)
			.setDurationUs(if (durationMs > 0) durationMs * 1_000 else C.TIME_UNSET)
			.setIsSeekable(durationMs > 0)
			.setIsDynamic(false)
			.apply {
				if (isCurrent && engine.currentTracksSnapshot != Tracks.EMPTY) {
					setTracks(engine.currentTracksSnapshot)
				}
			}
			.build()
	}

	// region command handlers → engine

	override fun handlePrepare(): ListenableFuture<*> {
		engine.prepare()
		return done()
	}

	override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
		engine.setPlayWhenReady(playWhenReady)
		return done()
	}

	override fun handleStop(): ListenableFuture<*> {
		engine.stop()
		return done()
	}

	override fun handleRelease(): ListenableFuture<*> {
		engine.release()
		return done()
	}

	override fun handleSetMediaItems(
		mediaItems: List<MediaItem>,
		startIndex: Int,
		startPositionMs: Long,
	): ListenableFuture<*> {
		val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
		val position = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
		engine.setItems(mediaItems, index, position)
		return done()
	}

	override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
		engine.addItems(index, mediaItems)
		return done()
	}

	override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
		engine.moveItems(fromIndex, toIndex, newIndex)
		return done()
	}

	override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
		engine.removeItems(fromIndex, toIndex)
		return done()
	}

	override fun handleReplaceMediaItems(
		fromIndex: Int,
		toIndex: Int,
		mediaItems: List<MediaItem>,
	): ListenableFuture<*> {
		engine.replaceItems(fromIndex, toIndex, mediaItems)
		return done()
	}

	override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
		when (seekCommand) {
			COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.seekToNext()
			COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> engine.seekToPrevious()
			else -> {
				val position = if (positionMs == C.TIME_UNSET) 0L else positionMs
				engine.seekTo(mediaItemIndex, position)
			}
		}
		return done()
	}

	override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
		engine.setShuffle(shuffleModeEnabled)
		return done()
	}

	override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
		engine.setRepeat(mapRepeatToDomain(repeatMode))
		return done()
	}

	override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
		engine.setPlaybackParameters(playbackParameters)
		return done()
	}

	override fun handleSetVolume(volume: Float): ListenableFuture<*> {
		engine.setVolume(volume)
		return done()
	}

	// endregion

	private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

	private companion object {
		const val MAX_SEEK_TO_PREVIOUS_MS = 3_000L

		val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
			.addAll(
				COMMAND_PLAY_PAUSE,
				COMMAND_PREPARE,
				COMMAND_STOP,
				COMMAND_SEEK_TO_DEFAULT_POSITION,
				COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
				COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
				COMMAND_SEEK_TO_PREVIOUS,
				COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
				COMMAND_SEEK_TO_NEXT,
				COMMAND_SEEK_TO_MEDIA_ITEM,
				COMMAND_SEEK_BACK,
				COMMAND_SEEK_FORWARD,
				COMMAND_SET_SPEED_AND_PITCH,
				COMMAND_SET_SHUFFLE_MODE,
				COMMAND_SET_REPEAT_MODE,
				COMMAND_GET_CURRENT_MEDIA_ITEM,
				COMMAND_GET_TIMELINE,
				COMMAND_GET_METADATA,
				COMMAND_SET_PLAYLIST_METADATA,
				COMMAND_SET_MEDIA_ITEM,
				COMMAND_CHANGE_MEDIA_ITEMS,
				COMMAND_GET_TRACKS,
				COMMAND_GET_AUDIO_ATTRIBUTES,
				COMMAND_SET_VOLUME,
				COMMAND_RELEASE,
			)
			.build()

		fun mapPlaybackState(state: PlaybackState): Int = when (state) {
			PlaybackState.Idle, PlaybackState.Stopping, PlaybackState.Error -> Player.STATE_IDLE
			PlaybackState.Preparing, PlaybackState.Buffering -> Player.STATE_BUFFERING
			PlaybackState.Ready, PlaybackState.Playing, PlaybackState.Paused,
			PlaybackState.Seeking, PlaybackState.Crossfading -> Player.STATE_READY
			PlaybackState.Ended -> Player.STATE_ENDED
		}

		fun mapRepeatToMedia3(mode: RepeatMode): Int = when (mode) {
			RepeatMode.OFF -> Player.REPEAT_MODE_OFF
			RepeatMode.ONE -> Player.REPEAT_MODE_ONE
			RepeatMode.ALL -> Player.REPEAT_MODE_ALL
		}

		fun mapRepeatToDomain(mode: Int): RepeatMode = when (mode) {
			Player.REPEAT_MODE_ONE -> RepeatMode.ONE
			Player.REPEAT_MODE_ALL -> RepeatMode.ALL
			else -> RepeatMode.OFF
		}
	}
}
