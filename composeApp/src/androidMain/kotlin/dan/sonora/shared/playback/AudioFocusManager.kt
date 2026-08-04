package dan.sonora.shared.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * The single audio-focus authority for the engine.
 *
 * The internal `ExoPlayer`s are built with `handleAudioFocus = false` precisely so that focus
 * is not requested twice; this class owns it once for the whole engine and translates focus
 * changes (phone calls, other media, "can duck" losses) and the becoming-noisy broadcast
 * (headphones unplugged) into engine callbacks. It never touches a player directly.
 *
 * Handles both the modern [AudioFocusRequest] API and the pre-26 deprecated API (minSdk 24).
 */
class AudioFocusManager(
	private val context: Context,
	private val listener: Listener,
) {
	/** Engine-facing effects of focus changes. Implemented by [PlaybackEngine]. */
	interface Listener {
		/**
		 * Pause because focus was lost.
		 *
		 * @return whether audio was actually playing before the interruption and may therefore
		 * resume on a subsequent focus gain.
		 */
		fun onPause(transient: Boolean): Boolean

		/** Focus regained after a transient loss while we intended to keep playing. */
		fun onResume()

		/** Attenuate volume (transient "can duck" loss). */
		fun onDuck()

		/** Restore volume after ducking. */
		fun onUnduck()
	}

	private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	private var focusRequest: AudioFocusRequest? = null
	private var hasFocus = false
	private var resumeOnGain = false
	private var noisyReceiverRegistered = false

	private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
		when (change) {
			AudioManager.AUDIOFOCUS_GAIN -> {
				listener.onUnduck()
				if (resumeOnGain) {
					resumeOnGain = false
					listener.onResume()
				}
			}

			AudioManager.AUDIOFOCUS_LOSS -> {
				resumeOnGain = false
				hasFocus = false
				unregisterNoisyReceiver()
				listener.onPause(transient = false)
			}

			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
				resumeOnGain = listener.onPause(transient = true)
			}

			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> listener.onDuck()
		}
	}

	private val noisyReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
				resumeOnGain = false
				listener.onPause(transient = false)
				abandonFocus()
			}
		}
	}

	/** Request focus for playback. Returns true if granted. */
	fun requestFocus(): Boolean {
		if (hasFocus) return true
		val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			requestFocusModern()
		} else {
			requestFocusLegacy()
		}
		hasFocus = granted
		if (granted) registerNoisyReceiver()
		return granted
	}

	fun abandonFocus() {
		if (hasFocus) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
			} else {
				@Suppress("DEPRECATION")
				audioManager.abandonAudioFocus(focusChangeListener)
			}
		}
		hasFocus = false
		resumeOnGain = false
		unregisterNoisyReceiver()
	}

	/** Prevent any previous transient interruption from resuming playback. */
	fun clearResumeOnGain() {
		resumeOnGain = false
	}

	fun release() = abandonFocus()

	private fun requestFocusModern(): Boolean {
		val attributes = AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_MEDIA)
			.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
			.build()
		val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
			.setAudioAttributes(attributes)
			.setWillPauseWhenDucked(false)
			.setOnAudioFocusChangeListener(focusChangeListener)
			.build()
			.also { focusRequest = it }
		return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
	}

	@Suppress("DEPRECATION")
	private fun requestFocusLegacy(): Boolean {
		val result = audioManager.requestAudioFocus(
			focusChangeListener,
			AudioManager.STREAM_MUSIC,
			AudioManager.AUDIOFOCUS_GAIN,
		)
		return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
	}

	private fun registerNoisyReceiver() {
		if (noisyReceiverRegistered) return
		context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
		noisyReceiverRegistered = true
	}

	private fun unregisterNoisyReceiver() {
		if (!noisyReceiverRegistered) return
		runCatching { context.unregisterReceiver(noisyReceiver) }
		noisyReceiverRegistered = false
	}
}
