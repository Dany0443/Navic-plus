package dan.sonora.ui.screens.collection.models

import androidx.compose.runtime.Immutable
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_sort_album_artist
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class CollectionSongSortType(val displayName: StringResource) {
    AlbumArtist(Res.string.option_sort_album_artist)
}
