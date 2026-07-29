package paige.navic.shared.playback

import androidx.media3.common.MediaItem
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.util.core.effectiveGain

/**
 * Computes the per-track ReplayGain factor for the volume chain.
 *
 * It **never writes `ExoPlayer.volume`** — it only returns a linear multiplier that
 * [FadeController] folds into its product. Gain is resolved lazily from the current
 * [ReplayGainMode] preference so toggling the mode at runtime takes effect on the next read.
 */
class ReplayGainManager(private val preferenceManager: PreferenceManager) {

	/** Linear gain in `(0, 1]` for [mediaItem]; `1.0` when disabled or untagged. */
	fun linearGain(mediaItem: MediaItem?): Float {
		val mode = preferenceManager.replayGainMode
		if (mode == ReplayGainMode.Off) return 1f
		val replayGain = ReplayGainTags.read(mediaItem) ?: return 1f
		return replayGain.effectiveGain(mode)
	}
}
