package dan.sonora.ui.screens.playlist.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_no_playlists_short
import org.jetbrains.compose.resources.stringResource
import dan.sonora.domain.models.DomainPlaylist
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.PlaylistRemove
import dan.sonora.ui.components.common.ContentUnavailable
import dan.sonora.ui.components.layouts.artGridPlaceholder
import dan.sonora.ui.components.localmusic.LocalMusicCollectionItem
import dan.sonora.ui.core.UiState

fun LazyGridScope.playlistListScreenContent(
	state: UiState<List<DomainPlaylist>>,
	localMusicEnabled: Boolean,
	onOpenLocalMusic: () -> Unit,
	selectedPlaylist: DomainPlaylist?,
	onUpdateSelection: (DomainPlaylist) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
) {
	val data = state.data.orEmpty()
	if (localMusicEnabled) {
		item(key = "local-music") {
			LocalMusicCollectionItem(
				modifier = Modifier.animateItem(),
				tab = "playlists",
				onClick = onOpenLocalMusic
			)
		}
	}
	if (data.isNotEmpty()) {
		items(data, { it.id }) { playlist ->
			PlaylistListScreenItem(
				modifier = Modifier.animateItem(),
				tab = "playlists",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onUpdateSelection(playlist) },
				onDeselect = { onClearSelection() },
				onSetShareId = onSetShareId,
				onSetDeletionId = onSetDeletionId,
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
			)
		}
	} else if (!localMusicEnabled) {
		when (state) {
			is UiState.Loading -> {
				artGridPlaceholder()
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.PlaylistRemove,
						label = stringResource(Res.string.info_no_playlists_short)
					)
				}
			}
		}
	}
}
