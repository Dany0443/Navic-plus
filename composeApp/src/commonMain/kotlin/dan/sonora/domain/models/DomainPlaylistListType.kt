package dan.sonora.domain.models

import androidx.compose.runtime.Immutable
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_sort_downloaded
import sonora.composeapp.generated.resources.option_sort_playlist_by_name
import sonora.composeapp.generated.resources.option_sort_playlist_date_added
import sonora.composeapp.generated.resources.option_sort_playlist_duration
import sonora.composeapp.generated.resources.option_sort_random
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainPlaylistListType(val displayName: StringResource) {
	Name(Res.string.option_sort_playlist_by_name),
	DateAdded(Res.string.option_sort_playlist_date_added),
	Duration(Res.string.option_sort_playlist_duration),
	Random(Res.string.option_sort_random),
	Downloaded(Res.string.option_sort_downloaded)
}
