package paige.navic.shared.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/** Creates a fully-configured internal [ExoPlayer]. Supplied by the service. */
fun interface ExoPlayerFactory {
	fun create(): ExoPlayer
}

/**
 * Owns exactly two internal [ExoPlayer] decks and their lifecycle.
 *
 * - **primary** — the audible deck the listener observes.
 * - **secondary** — an optional deck preloaded with the upcoming track for crossfade.
 *
 * Each deck holds a single media item; the engine (not ExoPlayer) drives queue advancement, so
 * gapless auto-transition is intentionally off. On [promoteSecondary] the old primary is
 * released (never leaked) and the secondary takes its place, with the observer moved across.
 *
 * These `ExoPlayer`s are never exposed outside the engine; `CrossfadePlayer` is the only public
 * `Player`. Owned and mutated on the playback thread.
 */
class PlayerCoordinator(
	private val factory: ExoPlayerFactory,
	private val listener: Listener,
) {
	/** Primary-deck events the engine reacts to. */
	interface Listener {
		fun onPrimaryStateChanged(playbackState: Int)
		fun onPrimaryReady(durationMs: Long)
		fun onPrimaryEnded()
		fun onPrimaryError(error: PlaybackException)
		fun onPrimaryIsPlayingChanged(isPlaying: Boolean)
		fun onPrimaryTracksChanged(tracks: Tracks)
	}

	private val primaryObserver = object : Player.Listener {
		override fun onPlaybackStateChanged(playbackState: Int) {
			listener.onPrimaryStateChanged(playbackState)
			when (playbackState) {
				Player.STATE_READY -> listener.onPrimaryReady(primary.duration)
				Player.STATE_ENDED -> listener.onPrimaryEnded()
			}
		}

		override fun onPlayerError(error: PlaybackException) = listener.onPrimaryError(error)

		override fun onIsPlayingChanged(isPlaying: Boolean) =
			listener.onPrimaryIsPlayingChanged(isPlaying)

		override fun onTracksChanged(tracks: Tracks) = listener.onPrimaryTracksChanged(tracks)
	}

	var primary: ExoPlayer = factory.create().apply { addListener(primaryObserver) }
		private set

	var secondary: ExoPlayer? = null
		private set

	val positionMs: Long get() = primary.currentPosition.coerceAtLeast(0)
	val bufferedPositionMs: Long get() = primary.bufferedPosition.coerceAtLeast(0)
	val durationMs: Long get() = primary.duration
	val hasKnownDuration: Boolean get() = primary.duration != C.TIME_UNSET
	val isLoading: Boolean get() = primary.playbackState == Player.STATE_BUFFERING
	val isAudiblyPlaying: Boolean get() = primary.isPlaying || secondary?.isPlaying == true
	val primaryState: Int get() = primary.playbackState
	val secondaryReady: Boolean get() = secondary?.playbackState == Player.STATE_READY

	/** Load [item] onto the primary deck and prepare it. */
	fun loadPrimary(item: MediaItem, startPositionMs: Long, playWhenReady: Boolean) {
		primary.apply {
			setMediaItem(item)
			if (startPositionMs > 0) seekTo(startPositionMs)
			this.playWhenReady = playWhenReady
			prepare()
		}
	}

	/** Prepare [item] on the secondary deck (paused), creating it on demand. Returns the deck. */
	fun preloadSecondary(item: MediaItem): ExoPlayer {
		val deck = secondary ?: factory.create().also { secondary = it }
		deck.apply {
			setMediaItem(item)
			playWhenReady = false
			prepare()
		}
		return deck
	}

	fun startSecondary() {
		secondary?.play()
	}

	/**
	 * Promote the secondary deck to primary: release the old primary and move observation to the
	 * new one. Returns false if there was no secondary.
	 */
	fun promoteSecondary(): Boolean {
		val next = secondary ?: return false
		primary.removeListener(primaryObserver)
		primary.release()
		primary = next.apply { addListener(primaryObserver) }
		secondary = null
		return true
	}

	fun clearSecondary() {
		secondary?.release()
		secondary = null
	}

	fun seekPrimary(positionMs: Long) = primary.seekTo(positionMs)

	fun setPrimaryPlayWhenReady(playWhenReady: Boolean) {
		primary.playWhenReady = playWhenReady
	}

	fun setPlaybackParameters(parameters: PlaybackParameters) {
		primary.playbackParameters = parameters
		secondary?.playbackParameters = parameters
	}

	fun stop() {
		clearSecondary()
		primary.stop()
	}

	fun release() {
		clearSecondary()
		primary.removeListener(primaryObserver)
		primary.release()
	}
}
