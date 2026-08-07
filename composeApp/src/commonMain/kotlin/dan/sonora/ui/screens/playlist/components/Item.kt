package dan.sonora.ui.screens.playlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.count_songs
import sonora.composeapp.generated.resources.notice_deleted_download
import sonora.composeapp.generated.resources.notice_download_started
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.koinInject
import dan.sonora.LocalNavStack
import dan.sonora.data.database.entities.DownloadStatus
import dan.sonora.domain.manager.DownloadManager
import dan.sonora.domain.manager.SnackBarManager
import dan.sonora.domain.models.DomainPlaylist
import dan.sonora.ui.components.layouts.ArtGridItem
import dan.sonora.ui.components.sheets.CollectionSheet
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.playlist.dialogs.PlaylistUpdateDialog

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import dan.sonora.LocalSharedTransitionScope
import dan.sonora.ui.components.common.CoverArt
import dan.sonora.util.ui.EmphasizedDecelerateEasing

@Composable
fun PlaylistListScreenItem(
	modifier: Modifier = Modifier,
	tab: String,
	playlist: DomainPlaylist,
	selected: Boolean,
	isListMode: Boolean = false,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit
) {
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(playlist.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Box(modifier) {
		if (isListMode) {
			SpotifyPlaylistItem(
				tab = tab,
				playlist = playlist,
				onClick = dropUnlessResumed {
					scope.launch {
						backStack.add(Screen.CollectionDetail(playlist.id, tab))
					}
				},
				onLongClick = onSelect
			)
		} else {
			ArtGridItem(
				onClick = dropUnlessResumed {
					scope.launch {
						backStack.add(Screen.CollectionDetail(playlist.id, tab))
					}
				},
				onLongClick = onSelect,
				coverArtId = playlist.coverArtId,
				title = playlist.name,
				subtitle = buildString {
					append(
						pluralStringResource(
							Res.plurals.count_songs,
							playlist.songCount,
							playlist.songCount
						)
					)
					playlist.comment?.let {
						append("\n${playlist.comment}\n")
					}
				},
				id = playlist.id,
				tab = tab
			)
		}
		if (selected) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = playlist,
				onShare = { onSetShareId(playlist.id) },
				onDelete = { onSetDeletionId(playlist.id) },
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onAddAllToPlaylist = { playlistDialogShown = true },
				downloadStatus = downloadStatus,
				onDownloadAll = {
					scope.launch {
						downloadManager.downloadCollection(playlist)
						snackBarManager.notify(Res.string.notice_download_started)
					}
				},
				onCancelDownloadAll = {
					scope.launch {
						playlist.songs.forEach { downloadManager.cancelDownload(it.id) }
					}
				},
				onDeleteDownloadAll = {
					scope.launch {
						downloadManager.deleteDownloadedCollection(playlist)
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				}
			)
		}

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = playlist.songs.toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpotifyPlaylistItem(
	modifier: Modifier = Modifier,
	tab: String,
	playlist: DomainPlaylist,
	onClick: () -> Unit,
	onLongClick: () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }
	val songCountStr = pluralStringResource(Res.plurals.count_songs, playlist.songCount, playlist.songCount)
	val subtitleText = buildString {
		append(songCountStr)
		playlist.comment?.takeIf { it.isNotBlank() }?.let {
			append(" • ")
			append(it)
		}
	}

	with(LocalSharedTransitionScope.current) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.combinedClickable(
					interactionSource = interactionSource,
					indication = null,
					onClick = onClick,
					onLongClick = onLongClick
				)
				.semantics { contentDescription = playlist.name }
				.padding(horizontal = 16.dp, vertical = 8.dp)
				.then(modifier),
			verticalAlignment = Alignment.CenterVertically
		) {
			CoverArt(
				coverArtId = playlist.coverArtId,
				contentDescription = playlist.name,
				modifier = Modifier
					.size(68.dp)
					.clip(RoundedCornerShape(8.dp))
					.sharedElement(
						sharedContentState = this@with.rememberSharedContentState("${tab}-${playlist.id}-cover"),
						boundsTransform = BoundsTransform { _, _ ->
							tween(durationMillis = 500, easing = EmphasizedDecelerateEasing)
						},
						animatedVisibilityScope = LocalNavAnimatedContentScope.current
					),
				interactionSource = interactionSource
			)
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = 16.dp),
				verticalArrangement = Arrangement.Center
			) {
				Text(
					text = playlist.name,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = subtitleText,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 4.dp)
				)
			}
		}
	}
}
