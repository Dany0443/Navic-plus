package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_mini_player_style_detached
import sonora.composeapp.generated.resources.option_mini_player_style_unified
import org.jetbrains.compose.resources.StringResource

enum class MiniPlayerStyle(val displayName: StringResource) {
	Unified(Res.string.option_mini_player_style_unified),
	Detached(Res.string.option_mini_player_style_detached)
}
