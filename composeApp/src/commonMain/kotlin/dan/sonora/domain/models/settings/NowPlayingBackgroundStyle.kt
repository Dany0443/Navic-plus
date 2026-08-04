package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_now_playing_background_style_dynamic
import sonora.composeapp.generated.resources.option_now_playing_background_style_static
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingBackgroundStyle(val displayName: StringResource) {
	Static(Res.string.option_now_playing_background_style_static),
	Dynamic(Res.string.option_now_playing_background_style_dynamic)
}
