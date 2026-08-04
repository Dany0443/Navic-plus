package dan.sonora.domain.models.settings

import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_album_replay_gain
import sonora.composeapp.generated.resources.info_track_replay_gain
import sonora.composeapp.generated.resources.option_off
import org.jetbrains.compose.resources.StringResource

enum class ReplayGainMode(val displayName: StringResource) {
	Off(Res.string.option_off),
	Track(Res.string.info_track_replay_gain),
	Album(Res.string.info_album_replay_gain)
}
