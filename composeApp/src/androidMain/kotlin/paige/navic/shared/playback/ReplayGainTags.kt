package paige.navic.shared.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import paige.navic.domain.models.DomainReplayGain

/**
 * Carries [DomainReplayGain] values across the `MediaController`↔`MediaSession` boundary inside
 * a `MediaItem`'s metadata extras, so the playback service can compute gain without a reference
 * to the app-side domain model.
 *
 * Only present (non-null) gains are stored; absent keys read back as null.
 */
object ReplayGainTags {

	private const val KEY_ALBUM_GAIN = "navic.rg.albumGain"
	private const val KEY_ALBUM_PEAK = "navic.rg.albumPeak"
	private const val KEY_TRACK_GAIN = "navic.rg.trackGain"
	private const val KEY_TRACK_PEAK = "navic.rg.trackPeak"
	private const val KEY_BASE_GAIN = "navic.rg.baseGain"
	private const val KEY_FALLBACK_GAIN = "navic.rg.fallbackGain"

	/** Merge ReplayGain fields into [extras], returning the same bundle for chaining. */
	fun writeInto(extras: Bundle, replayGain: DomainReplayGain?): Bundle {
		replayGain ?: return extras
		replayGain.albumGain?.let { extras.putFloat(KEY_ALBUM_GAIN, it) }
		replayGain.albumPeak?.let { extras.putFloat(KEY_ALBUM_PEAK, it) }
		replayGain.trackGain?.let { extras.putFloat(KEY_TRACK_GAIN, it) }
		replayGain.trackPeak?.let { extras.putFloat(KEY_TRACK_PEAK, it) }
		replayGain.baseGain?.let { extras.putFloat(KEY_BASE_GAIN, it) }
		replayGain.fallbackGain?.let { extras.putFloat(KEY_FALLBACK_GAIN, it) }
		return extras
	}

	/** Reconstruct [DomainReplayGain] from a media item, or null if it carries no gain tags. */
	fun read(mediaItem: MediaItem?): DomainReplayGain? {
		val extras = mediaItem?.mediaMetadata?.extras ?: return null
		if (!extras.keySet().any { it.startsWith("navic.rg.") }) return null
		return DomainReplayGain(
			albumGain = extras.floatOrNull(KEY_ALBUM_GAIN),
			albumPeak = extras.floatOrNull(KEY_ALBUM_PEAK),
			trackGain = extras.floatOrNull(KEY_TRACK_GAIN),
			trackPeak = extras.floatOrNull(KEY_TRACK_PEAK),
			baseGain = extras.floatOrNull(KEY_BASE_GAIN),
			fallbackGain = extras.floatOrNull(KEY_FALLBACK_GAIN),
		)
	}

	private fun Bundle.floatOrNull(key: String): Float? =
		if (containsKey(key)) getFloat(key) else null
}
