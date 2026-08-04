package dan.sonora.ui.screens.song.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_no_songs
import sonora.composeapp.generated.resources.state_scanning_local_music
import org.jetbrains.compose.resources.stringResource
import dan.sonora.data.database.entities.DownloadEntity
import dan.sonora.domain.models.DomainSong
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Note
import dan.sonora.ui.components.common.ContentUnavailable
import dan.sonora.ui.core.UiState

fun LazyListScope.songListScreenContent(
	state: UiState<ImmutableList<DomainSong>>,
	selectedSong: DomainSong?,
	selectedSongIsStarred: Boolean,
	selectedSongRating: Int,
	allDownloads: List<DownloadEntity>,
	onUpdateSelection: (DomainSong) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetStarred: (Boolean) -> Unit,
	onPlayNext: (DomainSong) -> Unit,
	onAddToQueue: (DomainSong) -> Unit,
	onPlaySong: (DomainSong) -> Unit,
	onSetRating: (Int) -> Unit,
	onDownload: (DomainSong) -> Unit,
	onCancelDownload: (DomainSong) -> Unit,
	onDeleteDownload: (DomainSong) -> Unit
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data) { song ->
			val download = allDownloads.find { it.songId == song.id }
			SongListScreenItem(
				modifier = Modifier.animateItem(),
				song = song,
				selected = song == selectedSong,
				starred = if (song == selectedSong) selectedSongIsStarred else song.starredAt != null,
				rating = if (song == selectedSong) selectedSongRating else 0,
				onSelect = { onUpdateSelection(song) },
				onDeselect = { onClearSelection() },
				onSetStarred = { onSetStarred(it) },
				onSetShareId = onSetShareId,
				onPlayNext = { onPlayNext(song) },
				onAddToQueue = { onAddToQueue(song) },
				onClick = { onPlaySong(song) },
				onSetRating = onSetRating,
				download = download,
				onDownload = { onDownload(song) },
				onCancelDownload = { onCancelDownload(song) },
				onDeleteDownload = { onDeleteDownload(song) }
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				item {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 32.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)
					) {
						CircularProgressIndicator()
						Spacer(Modifier.height(4.dp))
						Text(
							text = stringResource(Res.string.state_scanning_local_music),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			else -> {
				item {
					ContentUnavailable(
						icon = Icons.Outlined.Note,
						label = stringResource(Res.string.info_no_songs)
					)
				}
			}
		}
	}
}
