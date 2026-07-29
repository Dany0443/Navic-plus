package paige.navic.shared.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Installs a fresh DSP processor into each ExoPlayer deck's default Media3 audio sink. */
@OptIn(UnstableApi::class)
class NavicAudioRenderersFactory(
	context: Context,
	private val settingsProvider: EqualizerSettingsProvider,
) : DefaultRenderersFactory(context) {

	@Suppress("DEPRECATION")
	override fun buildAudioSink(
		context: Context,
		enableFloatOutput: Boolean,
		enableAudioTrackPlaybackParams: Boolean,
	): AudioSink = DefaultAudioSink.Builder(context)
		.setAudioProcessors(arrayOf(NavicDspAudioProcessor(settingsProvider)))
		.setEnableFloatOutput(enableFloatOutput)
		.setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
		.build()
}
