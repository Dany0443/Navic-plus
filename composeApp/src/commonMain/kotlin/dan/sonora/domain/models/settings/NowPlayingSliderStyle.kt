package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_now_playing_slider_style_flat
import sonora.composeapp.generated.resources.option_now_playing_slider_style_slim
import sonora.composeapp.generated.resources.option_now_playing_slider_style_squiggly
import sonora.composeapp.generated.resources.option_now_playing_slider_style_yoyo
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingSliderStyle(val displayName: StringResource) {
	Flat(Res.string.option_now_playing_slider_style_flat),
	Squiggly(Res.string.option_now_playing_slider_style_squiggly),
	Slim(Res.string.option_now_playing_slider_style_slim),
	Yoyo(Res.string.option_now_playing_slider_style_yoyo)
}
