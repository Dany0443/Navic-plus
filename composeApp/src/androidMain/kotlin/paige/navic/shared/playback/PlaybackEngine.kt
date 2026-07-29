package paige.navic.shared.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.playback.PlaybackState
import paige.navic.domain.playback.PlaybackStateMachine
import paige.navic.domain.playback.QueueManager
import paige.navic.domain.playback.QueueSnapshot
import paige.navic.domain.playback.RepeatMode

/**
 * Composition root and orchestrator for playback.
 *
 * Wires the [QueueManager] (source of truth), [PlaybackStateMachine] (explicit state),
 * [PlayerCoordinator] (two hidden decks), [FadeController] (sole volume writer),
 * [ReplayGainManager] and [AudioFocusManager], and exposes a single [CrossfadePlayer] as the
 * public `Player`. It owns one [CoroutineScope] on the playback (main) thread; there is no
 * `GlobalScope`, no polling — crossfade timing is scheduled from the READY duration event.
 *
 * All mutation happens on the playback thread (Media3's application looper), so state is
 * consistent without locking.
 */
class PlaybackEngine(
	context: Context,
	playerFactory: ExoPlayerFactory,
	private val preferenceManager: PreferenceManager,
	looper: Looper = Looper.getMainLooper(),
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val queue = QueueManager<MediaItem>()
	private val stateMachine = PlaybackStateMachine()
	private val fade = FadeController(scope)
	private val replayGain = ReplayGainManager(preferenceManager)
	private val coordinator = PlayerCoordinator(playerFactory, DeckEvents())
	private val focus = AudioFocusManager(context, FocusEvents())

	private var playWhenReady = false
	private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
	private var userVolume = 1f
	private var parameters = PlaybackParameters.DEFAULT
	private var playerError: PlaybackException? = null

	/** Id of the entry currently loaded on the primary deck, or null when nothing is loaded. */
	private var loadedEntryId: Long? = null
	private var currentTracks: Tracks = Tracks.EMPTY

	private var preloadJob: Job? = null
	private var fadeStartJob: Job? = null

	/** Effective (clamped) crossfade for the current track; set when transitions are scheduled. */
	private var activeCrossfadeMs = 0L

	/** The only `Player` exposed outside the engine. Constructed last: its `getState()` reads
	 *  the fields declared above, which must already be initialized. */
	val player: CrossfadePlayer = CrossfadePlayer(looper, this)

	// region state exposed to CrossfadePlayer (read on the playback thread)

	val queueSnapshot: QueueSnapshot<MediaItem> get() = queue.snapshot
	val playbackState: PlaybackState get() = stateMachine.current
	val currentTracksSnapshot: Tracks get() = currentTracks
	val positionMs: Long get() = coordinator.positionMs
	val bufferedPositionMs: Long get() = coordinator.bufferedPositionMs
	val currentDurationMs: Long get() = coordinator.durationMs
	val isLoading: Boolean get() = coordinator.isLoading
	val volume: Float get() = userVolume
	val playbackParameters: PlaybackParameters get() = parameters
	val error: PlaybackException? get() = playerError
	fun playWhenReady(): Boolean = playWhenReady
	fun playWhenReadyReason(): Int = playWhenReadyReason

	// endregion

	// region commands invoked by CrossfadePlayer

	fun prepare() {
		val current = queue.snapshot.currentEntry ?: return
		if (loadedEntryId != current.id) loadCurrent(0L) else coordinator.primary.prepare()
	}

	fun setPlayWhenReady(value: Boolean) {
		if (value) {
			resumePlayback(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
		} else {
			focus.abandonFocus()
			playWhenReady = false
			playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
			pauseInternal()
			invalidate()
		}
	}

	fun stop() {
		cancelScheduling()
		fade.cancelFade()
		focus.abandonFocus()
		playWhenReady = false
		loadedEntryId = null
		stateMachine.transitionTo(PlaybackState.Stopping)
		stateMachine.transitionTo(PlaybackState.Idle)
		coordinator.stop()
		invalidate()
	}

	fun release() {
		cancelScheduling()
		fade.release()
		coordinator.release()
		focus.release()
		scope.cancel()
	}

	fun setItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
		queue.setItems(items, startIndex)
		if (queue.snapshot.isEmpty) resetToIdle() else loadCurrent(startPositionMs)
	}

	fun addItems(index: Int, items: List<MediaItem>) {
		val wasEmpty = queue.snapshot.isEmpty
		queue.insertAt(index, items)
		if (wasEmpty && playWhenReady) loadCurrent(0L) else onQueueEdited()
	}

	fun removeItems(fromIndex: Int, toIndex: Int) {
		val removedLoaded = queue.snapshot.entries.subList(
			fromIndex.coerceIn(0, queue.snapshot.size),
			toIndex.coerceIn(0, queue.snapshot.size),
		).any { it.id == loadedEntryId }
		for (i in (toIndex - 1) downTo fromIndex) queue.removeAt(i)
		when {
			queue.snapshot.isEmpty -> resetToIdle()
			removedLoaded -> loadCurrent(0L)
			else -> onQueueEdited()
		}
	}

	fun moveItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
		val span = toIndex - fromIndex
		repeat(span) { offset -> queue.move(fromIndex + offset, newIndex + offset) }
		onQueueEdited()
	}

	fun replaceItems(fromIndex: Int, toIndex: Int, items: List<MediaItem>) {
		val count = toIndex - fromIndex
		val overlap = minOf(count, items.size)
		for (k in 0 until overlap) queue.replaceAt(fromIndex + k, items[k])
		if (items.size < count) {
			for (i in (toIndex - 1) downTo (fromIndex + overlap)) queue.removeAt(i)
		} else if (items.size > count) {
			queue.insertAt(toIndex, items.subList(count, items.size))
		}
		if (queue.snapshot.currentIndex in fromIndex until toIndex && overlap > 0) {
			loadCurrent(coordinator.positionMs)
		} else {
			onQueueEdited()
		}
	}

	fun seekTo(mediaItemIndex: Int, positionMs: Long) {
		val entries = queue.snapshot.entries
		if (mediaItemIndex !in entries.indices) return
		val target = entries[mediaItemIndex]
		if (mediaItemIndex == queue.snapshot.currentIndex && target.id == loadedEntryId) {
			abortCrossfade()
			stateMachine.transitionTo(PlaybackState.Seeking)
			coordinator.seekPrimary(positionMs.coerceAtLeast(0))
			stateMachine.resolveOverlay()
			scheduleTransitions(coordinator.durationMs)
			invalidate()
		} else {
			queue.seekToIndex(mediaItemIndex)
			loadCurrent(positionMs.coerceAtLeast(0))
		}
	}

	fun seekToNext() {
		if (queue.advanceToNext(auto = false)) loadCurrent(0L)
	}

	fun seekToPrevious() {
		val position = coordinator.positionMs
		if (position > MAX_SEEK_TO_PREVIOUS_MS || queue.peekPreviousIndex() == null) {
			seekTo(queue.snapshot.currentIndex, 0L)
		} else if (queue.advanceToPrevious()) {
			loadCurrent(0L)
		}
	}

	fun setShuffle(enabled: Boolean) {
		queue.setShuffleEnabled(enabled)
		onNextTargetChanged()
	}

	fun setRepeat(mode: RepeatMode) {
		queue.setRepeatMode(mode)
		onNextTargetChanged()
	}

	fun setPlaybackParameters(value: PlaybackParameters) {
		parameters = value
		coordinator.setPlaybackParameters(value)
		invalidate()
	}

	fun setVolume(value: Float) {
		userVolume = value.coerceIn(0f, 1f)
		fade.setUserVolume(userVolume)
		invalidate()
	}

	// endregion

	// region internals

	/** Load the current entry onto the primary deck, discarding any preloaded secondary. */
	private fun loadCurrent(startPositionMs: Long) {
		cancelScheduling()
		fade.cancelFade()
		releaseSecondaryDeck()

		val entry = queue.snapshot.currentEntry
		if (entry == null) {
			resetToIdle()
			return
		}
		loadedEntryId = entry.id
		playerError = null
		activatePrimary(entry.item, envelope = 1f)
		coordinator.loadPrimary(entry.item, startPositionMs, playWhenReady)
		stateMachine.transitionTo(PlaybackState.Preparing)
		invalidate()
	}

	private fun pauseInternal() {
		if (stateMachine.current == PlaybackState.Crossfading) abortCrossfade()
		coordinator.setPrimaryPlayWhenReady(false)
		cancelScheduling()
		if (stateMachine.current != PlaybackState.Idle &&
			stateMachine.current != PlaybackState.Ended
		) {
			stateMachine.transitionTo(PlaybackState.Paused)
		}
	}

	private fun resumePlayback(reason: Int) {
		if (!focus.requestFocus()) {
			playWhenReady = false
			playWhenReadyReason = reason
			pauseInternal()
			invalidate()
			return
		}

		playWhenReady = true
		playWhenReadyReason = reason
		if (loadedEntryId == null && queue.snapshot.currentEntry != null) {
			loadCurrent(0L)
		} else {
			coordinator.setPrimaryPlayWhenReady(true)
			if (stateMachine.current == PlaybackState.Ready ||
				stateMachine.current == PlaybackState.Paused
			) {
				stateMachine.transitionTo(PlaybackState.Playing)
			}
			scheduleTransitions(coordinator.durationMs)
		}
		invalidate()
	}

	/** Pause for a system interruption without treating it as an explicit user pause. */
	private fun pauseForInterruption(): Boolean {
		val wasAudiblyPlaying = playWhenReady && coordinator.isAudiblyPlaying
		if (playWhenReady) {
			playWhenReady = false
			playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
			pauseInternal()
			invalidate()
		}
		return wasAudiblyPlaying
	}

	private fun resetToIdle() {
		loadedEntryId = null
		fade.cancelFade()
		cancelScheduling()
		stateMachine.transitionTo(PlaybackState.Stopping)
		stateMachine.transitionTo(PlaybackState.Idle)
		coordinator.stop()
		invalidate()
	}

	/** Re-evaluate preload/crossfade after the auto-next target may have changed. */
	private fun onNextTargetChanged() {
		if (stateMachine.current != PlaybackState.Crossfading) {
			releaseSecondaryDeck()
			scheduleTransitions(coordinator.durationMs)
		}
		invalidate()
	}

	private fun onQueueEdited() {
		if (loadedEntryId == null && playWhenReady && queue.snapshot.currentEntry != null) {
			loadCurrent(0L)
		} else {
			onNextTargetChanged()
		}
	}

	private fun abortCrossfade() {
		fade.cancelFade()
		releaseSecondaryDeck()
		fade.setEnvelope(Deck.PRIMARY, 1f)
	}

	/** Configured crossfade, clamped so it never exceeds half the track (short tracks). */
	private fun effectiveCrossfadeMs(durationMs: Long): Long {
		val configured = preferenceManager.crossfadeDuration.coerceIn(0, 12) * 1000L
		return if (durationMs > 0) configured.coerceAtMost(durationMs / 2) else configured
	}

	/**
	 * Schedule the upcoming transition off the known [durationMs]; never polls.
	 *
	 * Always preloads the next track onto the secondary deck ahead of time (enabling a
	 * near-gapless hard cut when crossfade is 0), and additionally arms the fade when a
	 * crossfade duration is configured.
	 */
	private fun scheduleTransitions(durationMs: Long) {
		cancelScheduling()
		if (!playWhenReady || durationMs <= 0 || durationMs == C.TIME_UNSET) return
		val nextIndex = queue.peekNextIndex(auto = true)
		val hasDistinctNext = nextIndex != null && nextIndex != queue.snapshot.currentIndex
		if (!hasDistinctNext) return

		val crossfade = effectiveCrossfadeMs(durationMs)
		activeCrossfadeMs = crossfade
		val preloadAtMs = (durationMs - crossfade - PRELOAD_LEAD_MS).coerceAtLeast(0)

		preloadJob = scope.launch {
			awaitPosition(preloadAtMs)
			val idx = queue.peekNextIndex(auto = true) ?: return@launch
			if (idx != queue.snapshot.currentIndex) {
				queue.snapshot.entries.getOrNull(idx)?.let { coordinator.preloadSecondary(it.item) }
			}
		}
		if (crossfade > 0) {
			fadeStartJob = scope.launch {
				awaitPosition((durationMs - crossfade).coerceAtLeast(0))
				beginCrossfade()
			}
		}
	}

	private suspend fun awaitPosition(targetMs: Long) {
		val speed = parameters.speed.coerceAtLeast(0.1f)
		val waitMs = ((targetMs - coordinator.positionMs) / speed).toLong()
		if (waitMs > 0) delay(waitMs)
	}

	private fun cancelScheduling() {
		preloadJob?.cancel(); preloadJob = null
		fadeStartJob?.cancel(); fadeStartJob = null
	}

	private fun beginCrossfade() {
		if (stateMachine.current == PlaybackState.Crossfading) return
		val currentIndex = queue.snapshot.currentIndex
		val nextIndex = queue.peekNextIndex(auto = true) ?: return
		if (nextIndex == currentIndex) return
		val nextItem = queue.snapshot.entries.getOrNull(nextIndex)?.item ?: return

		coordinator.secondary ?: coordinator.preloadSecondary(nextItem)
		activateSecondary(nextItem, envelope = 0f)
		coordinator.startSecondary()
		stateMachine.transitionTo(PlaybackState.Crossfading)
		invalidate()
		fade.crossfade(Deck.PRIMARY, Deck.SECONDARY, activeCrossfadeMs) {
			completeCrossfade(nextIndex)
		}
	}

	private fun completeCrossfade(targetIndex: Int) {
		if (!coordinator.promoteSecondary()) return
		queue.seekToIndex(targetIndex)
		val entry = queue.snapshot.currentEntry
		loadedEntryId = entry?.id
		activatePrimary(entry?.item, envelope = 1f)
		clearSecondaryBinding()
		coordinator.setPrimaryPlayWhenReady(playWhenReady)
		stateMachine.transitionTo(PlaybackState.Playing)
		invalidate()
		scheduleTransitions(coordinator.durationMs)
	}

	private fun advanceOnEnd() {
		stateMachine.transitionTo(PlaybackState.Ended)
		invalidate()
		
		val currentIndex = queue.snapshot.currentIndex
		val nextIndex = queue.peekNextIndex(auto = true)
		when {
			nextIndex == null -> {
				playWhenReady = false
				focus.abandonFocus()
			}

			nextIndex == currentIndex -> { // repeat one: restart current
				coordinator.loadPrimary(queue.snapshot.currentEntry!!.item, 0L, playWhenReady)
				stateMachine.transitionTo(PlaybackState.Preparing)
				invalidate()
			}

			coordinator.secondary != null -> {
				// Near-gapless hard cut: the next track is already prepared on the secondary
				// deck, so start it at full volume and promote instead of reloading.
				activateSecondary(queue.snapshot.entries[nextIndex].item, envelope = 1f)
				coordinator.startSecondary()
				completeCrossfade(nextIndex)
			}

			else -> {
				queue.seekToIndex(nextIndex)
				loadCurrent(0L)
			}
		}
	}

	// region deck ↔ fade binding helpers

	/** Bind the primary deck to the fade chain: its ReplayGain, volume sink and envelope. */
	private fun activatePrimary(item: MediaItem?, envelope: Float) {
		fade.setReplayGain(Deck.PRIMARY, replayGain.linearGain(item))
		fade.bind(Deck.PRIMARY) { v -> coordinator.primary.volume = v }
		fade.setEnvelope(Deck.PRIMARY, envelope)
	}

	/** Bind the secondary deck to the fade chain. */
	private fun activateSecondary(item: MediaItem?, envelope: Float) {
		fade.setReplayGain(Deck.SECONDARY, replayGain.linearGain(item))
		fade.bind(Deck.SECONDARY) { v -> coordinator.secondary?.volume = v }
		fade.setEnvelope(Deck.SECONDARY, envelope)
	}

	/** Detach the secondary volume sink and silence its envelope (deck already gone/promoted). */
	private fun clearSecondaryBinding() {
		fade.bind(Deck.SECONDARY, null)
		fade.setEnvelope(Deck.SECONDARY, 0f)
	}

	/** Release the secondary deck entirely and clear its binding. */
	private fun releaseSecondaryDeck() {
		coordinator.clearSecondary()
		clearSecondaryBinding()
	}

	// endregion

	private fun invalidate() {
		player.requestInvalidateState()
	}

	// endregion

	private inner class DeckEvents : PlayerCoordinator.Listener {
		override fun onPrimaryStateChanged(playbackState: Int) {
			if (playbackState == Player.STATE_BUFFERING &&
				stateMachine.current == PlaybackState.Playing
			) {
				stateMachine.transitionTo(PlaybackState.Buffering)
			}
			invalidate()
		}

		override fun onPrimaryReady(durationMs: Long) {
			if (stateMachine.current == PlaybackState.Crossfading) return
			if (playWhenReady) {
				if (focus.requestFocus()) {
					coordinator.setPrimaryPlayWhenReady(true)
					stateMachine.transitionTo(PlaybackState.Playing)
				} else {
					playWhenReady = false
					coordinator.setPrimaryPlayWhenReady(false)
					stateMachine.transitionTo(PlaybackState.Ready)
				}
			} else {
				stateMachine.transitionTo(PlaybackState.Ready)
			}
			invalidate()
			scheduleTransitions(durationMs)
		}

		override fun onPrimaryEnded() {
			if (stateMachine.current == PlaybackState.Crossfading) return
			advanceOnEnd()
		}

		override fun onPrimaryError(error: PlaybackException) {
			focus.clearResumeOnGain()
			playerError = error
			stateMachine.transitionTo(PlaybackState.Error)
			invalidate()
		}

		override fun onPrimaryIsPlayingChanged(isPlaying: Boolean) = invalidate()

		override fun onPrimaryTracksChanged(tracks: Tracks) {
			currentTracks = tracks
			invalidate()
		}
	}

	private inner class FocusEvents : AudioFocusManager.Listener {
		override fun onPause(transient: Boolean): Boolean = pauseForInterruption()

		override fun onResume() {
			if (!playWhenReady) {
				resumePlayback(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
			}
		}

		override fun onDuck() = fade.setDuck(DUCK_FACTOR)
		override fun onUnduck() = fade.setDuck(1f)
	}

	private companion object {
		const val PRELOAD_LEAD_MS = 8_000L
		const val MAX_SEEK_TO_PREVIOUS_MS = 3_000L
		const val DUCK_FACTOR = 0.2f
	}
}
