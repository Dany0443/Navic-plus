package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_explicit_playback_allowed
import sonora.composeapp.generated.resources.option_explicit_playback_skip
import sonora.composeapp.generated.resources.option_explicit_playback_skip_session
import org.jetbrains.compose.resources.StringResource

enum class ExplicitContentPlayback(val displayName: StringResource) {
	Allowed(Res.string.option_explicit_playback_allowed),
	Skip(Res.string.option_explicit_playback_skip),
	SkipForThisSession(Res.string.option_explicit_playback_skip_session)
}
