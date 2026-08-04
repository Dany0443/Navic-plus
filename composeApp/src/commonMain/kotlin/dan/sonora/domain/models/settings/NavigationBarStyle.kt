package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_navigation_bar_style_normal
import sonora.composeapp.generated.resources.option_navigation_bar_style_short
import org.jetbrains.compose.resources.StringResource

enum class NavigationBarStyle(val displayName: StringResource) {
	Normal(Res.string.option_navigation_bar_style_normal),
	Short(Res.string.option_navigation_bar_style_short)
}
