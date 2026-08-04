package dan.sonora.ui.screens.collection.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import dan.sonora.LocalNavStack
import dan.sonora.data.database.entities.DownloadStatus
import dan.sonora.domain.models.DomainAlbum
import dan.sonora.domain.models.DomainAlbumInfo
import dan.sonora.domain.models.DomainSongCollection
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.MoreVert
import dan.sonora.icons.outlined.Sort
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.components.layouts.TopBarButton
import dan.sonora.ui.components.sheets.CollectionSheet
import dan.sonora.ui.components.sheets.SortSheet
import dan.sonora.ui.screens.collection.models.CollectionSongSortType
import dan.sonora.ui.core.UiState
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun CollectionDetailScreenTopBar(
	collection: DomainSongCollection?,
	albumInfoState: UiState<DomainAlbumInfo>,
	titleAlpha: Float,
	onSetShareId: (shareId: String?) -> Unit,
	onDownloadAll: () -> Unit,
	onCancelDownloadAll: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	downloadStatus: DownloadStatus,
	rating: Int?,
	onSetRating: ((Int) -> Unit)?,
	starred: Boolean?,
	onSetStarred: ((Boolean) -> Unit)? = null,
	refreshCollection: () -> Unit,
	showSongSort: Boolean,
	selectedSongSorting: CollectionSongSortType,
	onSetSongSorting: (CollectionSongSortType) -> Unit,
	selectedSongSortingReversed: Boolean,
	onSetSongSortingReversed: (Boolean) -> Unit
) {
	val uriHandler = LocalUriHandler.current
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val backStack = LocalNavStack.current

	NestedTopBar(
		title = {
			Text(
				text = collection?.name.orEmpty(),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.alpha(titleAlpha)
			)
		},
		actions = {
			Box {
				var sortExpanded by remember { mutableStateOf(false) }
				var expanded by remember { mutableStateOf(false) }
				if (showSongSort) {
					TopBarButton({
						sortExpanded = true
					}) {
						Icon(
							Icons.Outlined.Sort,
							null
						)
					}
				}
				TopBarButton({
					expanded = true
					refreshCollection()
				}) {
					Icon(
						Icons.Outlined.MoreVert,
						stringResource(Res.string.action_more)
					)
				}
				if (expanded) {
					CollectionSheet(
						onDismissRequest = { expanded = false },
						collection = collection,
						albumInfo = (albumInfoState as? UiState.Success)?.data,
						onDownloadAll = onDownloadAll,
						onCancelDownloadAll = onCancelDownloadAll,
						downloadStatus = downloadStatus,
						onShare = { onSetShareId(collection?.id) },
						onPlayNext = onPlayNext,
						onAddToQueue = onAddToQueue,
						onAddAllToPlaylist = { playlistDialogShown = true },
						onViewOnLastFm = { url -> uriHandler.openUri(url) },
						onViewOnMusicBrainz = { id ->
							uriHandler.openUri("https://musicbrainz.org/release/$id")
						},
						onViewArtist =
							if (collection is DomainAlbum)
								dropUnlessResumed { backStack.add(Screen.ArtistDetail(collection.artistId)) }
							else null,
						rating = rating,
						onSetRating = onSetRating,
						starred = starred,
						onSetStarred = if (onSetStarred != null && starred != null) {
							{ onSetStarred(!starred) }
						} else null
					)
				}
				if (sortExpanded) {
					SortSheet(
						entries = CollectionSongSortType.entries.toImmutableList(),
						selectedSorting = selectedSongSorting,
						selectedReversed = selectedSongSortingReversed,
						label = { stringResource(it.displayName) },
						onSetSorting = onSetSongSorting,
						onSetReversed = onSetSongSortingReversed,
						onDismissRequest = { sortExpanded = false }
					)
				}
			}
		}
	)

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = collection?.songs.orEmpty().toPersistentList(),
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
