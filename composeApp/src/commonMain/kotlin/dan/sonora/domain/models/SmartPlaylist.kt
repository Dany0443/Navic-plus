package dan.sonora.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface SmartPlaylistType {
    val id: String

    @Immutable
    @Serializable
    data object MostPlayed : SmartPlaylistType {
        override val id: String = "MostPlayed"
    }

    @Immutable
    @Serializable
    data object OnRepeat : SmartPlaylistType {
        override val id: String = "OnRepeat"
    }

    @Immutable
    @Serializable
    data object NeverPlayed : SmartPlaylistType {
        override val id: String = "NeverPlayed"
    }
}

@Immutable
@Serializable
data class SmartPlaylist(
    val id: String,
    val type: SmartPlaylistType,
    val title: String,
    val icon: String,
    val songCount: Int,
    val listType: DomainSongListType
)
