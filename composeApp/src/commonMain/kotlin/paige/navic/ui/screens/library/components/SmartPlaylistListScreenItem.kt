package paige.navic.ui.screens.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import paige.navic.domain.models.SmartPlaylist
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.smartplaylists.SmartPlaylistCoverPlaceholderSlot
import paige.navic.ui.smartplaylists.appearance

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
