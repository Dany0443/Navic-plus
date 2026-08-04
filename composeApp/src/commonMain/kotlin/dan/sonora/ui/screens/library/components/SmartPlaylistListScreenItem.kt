package dan.sonora.ui.screens.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dan.sonora.domain.models.SmartPlaylist
import dan.sonora.ui.components.layouts.ArtGridItem
import dan.sonora.ui.smartplaylists.SmartPlaylistCoverPlaceholderSlot
import dan.sonora.ui.smartplaylists.appearance

@Composable
fun SmartPlaylistListScreenItem(
	modifier: Modifier = Modifier,
	playlist: SmartPlaylist,
	onSelect: () -> Unit
) {
	val appearance = playlist.type.appearance()
	ArtGridItem(
		modifier = modifier,
		onClick = onSelect,
		coverArtId = null,
		placeholder = SmartPlaylistCoverPlaceholderSlot(playlist),
		title = appearance.title,
		subtitle = "${appearance.subtitle} · ${playlist.songCount} songs",
		id = playlist.id,
		tab = "library"
	)
}
