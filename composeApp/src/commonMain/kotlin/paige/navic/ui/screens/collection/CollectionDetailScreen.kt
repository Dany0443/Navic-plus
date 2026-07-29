package paige.navic.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.dynamiccolor.ColorSpec
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_no_songs
import navic.composeapp.generated.resources.title_disc_number
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Album
import paige.navic.icons.outlined.Note
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.snackbars.ErrorSnackBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.collection.components.CollectionDetailScreenFooterRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenHeadingRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenHeadingRowButtons
import paige.navic.ui.screens.collection.components.CollectionDetailScreenSongRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenSongRowDropdown
import paige.navic.ui.screens.collection.components.CollectionDetailScreenTopBar
import paige.navic.ui.screens.collection.components.collectionDetailScreenMoreByArtistRow
import paige.navic.ui.screens.collection.models.CollectionSongSortType
import paige.navic.ui.screens.collection.viewmodels.CollectionDetailViewModel
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.theme.NavicTheme
import paige.navic.util.core.Logger
import paige.navic.util.ui.rememberColorSchemeFromCoverArt
import paige.navic.util.ui.withoutTop
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    tab: String
) {
    val preferenceManager = koinInject<PreferenceManager>()

    val viewModel = koinViewModel<CollectionDetailViewModel>(
        key = collectionId,
        parameters = { parametersOf(collectionId) }
    )

    val player = koinInject<MediaPlayerViewModel>()
    val playerState by player.uiState.collectAsStateWithLifecycle()

    val collectionState by viewModel.collectionState.collectAsState()
    val collection = collectionState.data
    val selection by viewModel.selectedSong.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val starred by viewModel.starred.collectAsState()

    var shareId by remember { mutableStateOf<String?>(null) }
    var shareExpiry by remember { mutableStateOf<Duration?>(null) }

    val albumInfoState by viewModel.albumInfoState.collectAsState()
    val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
    val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
    val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
    val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()
    val otherAlbums by viewModel.otherAlbums.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val playlistAlbumsById by viewModel.playlistAlbumsById.collectAsStateWithLifecycle()
    val selectedSongSorting by viewModel.selectedSongSorting.collectAsStateWithLifecycle()
    val selectedSongSortingReversed by viewModel.selectedSongSortingReversed.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.collectionDownloadStatus()
        .collectAsState(DownloadStatus.NOT_DOWNLOADED)

    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val displayCollection = remember(
        collection,
        playlistAlbumsById,
        selectedSongSorting,
        selectedSongSortingReversed
    ) {
        when (collection) {
            is DomainPlaylist -> collection.copy(
                songs = collection.songs.sortedByPlaylistSongSort(
                    sortType = selectedSongSorting,
                    reversed = selectedSongSortingReversed,
                    albumsById = playlistAlbumsById
                )
            )
            else -> collection
        }
    }

	val titleAlpha by remember {
		derivedStateOf {
			if (viewModel.listState.firstVisibleItemIndex >= 1) return@derivedStateOf 1f
			val height =
				viewModel.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat()
					?: 0f
			if (height > 0f) {
				val threshold = height * 0.4f
				((viewModel.listState.firstVisibleItemScrollOffset.toFloat() - threshold) / (height - threshold)).coerceIn(
					0f,
					1f
				)
			} else {
				0f
			}
		}
	}
	val colorScheme = if (preferenceManager.dynamicTheming) {
		rememberColorSchemeFromCoverArt(
			coverArtId = collection?.coverArtId,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}

    NavicTheme(colorScheme) {
        Scaffold(
            topBar = {
                CollectionDetailScreenTopBar(
                    albumInfoState = albumInfoState,
                    collection = displayCollection,
                    titleAlpha = titleAlpha,
                    onSetShareId = { shareId = it },
                    onDownloadAll = { viewModel.downloadAll() },
                    onCancelDownloadAll = { viewModel.cancelDownloadAll() },
                    onPlayNext = { if (displayCollection != null) player.playNext(displayCollection) },
                    onAddToQueue = { if (displayCollection != null) player.addToQueue(displayCollection) },
                    downloadStatus = downloadStatus,
                    rating = if (collection !is DomainPlaylist) rating else null,
                    onSetRating = if (collection !is DomainPlaylist) {
                        { viewModel.rateAlbum(it) }
                    } else null,
                    starred = if (collection !is DomainPlaylist) starred else null,
                    onSetStarred = if (collection !is DomainPlaylist) {
                        { viewModel.starAlbum(it) }
                    } else null,
                    refreshCollection = { viewModel.refreshCollection(false) },
                    showSongSort = collection is DomainPlaylist,
                    selectedSongSorting = selectedSongSorting,
                    onSetSongSorting = { viewModel.setSongSorting(it) },
                    selectedSongSortingReversed = selectedSongSortingReversed,
                    onSetSongSortingReversed = { viewModel.setSongSortingReversed(it) }
                )
            },
            bottomBar = {
                val scrollManager = LocalBottomBarScrollManager.current
                if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
                    RootBottomBar(scrolled = scrollManager.isTriggered)
                }
            }
        ) { contentPadding ->
            PullToRefreshBox(
                modifier = Modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .background(MaterialTheme.colorScheme.surface),
                finished = collectionState !is UiState.Loading,
                onRefresh = { viewModel.refreshCollection(true) },
                key = collectionState
            ) {
                LazyColumn(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = contentPadding.withoutTop(),
                    state = viewModel.listState
                ) {
                    if (collection == null) return@LazyColumn

                    item {
                        CollectionDetailScreenHeadingRow(
                            collection = displayCollection ?: collection,
                            tab = tab,
                            titleAlpha = 1f - titleAlpha
                        )
                    }

                    item {
                        CollectionDetailScreenHeadingRowButtons(
                            collection = displayCollection ?: collection
                        )
                    }

                    if (collection is DomainAlbum) {
                        collection.copy(
                            songs = collection.songs.sortedWith(
                                compareBy(
                                    { it.discNumber },
                                    { it.trackNumber }
                                ))
                        ).let { album ->
                            album.songs.groupBy { it.discNumber }.forEach { group ->
                                val multipleDiscs = album.songs.groupBy { it.discNumber }.size > 1
                                if (group.key != null && multipleDiscs) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(
                                                    top = if (group.key == 1) 0.dp else 12.dp,
                                                    bottom = 4.dp
                                                )
                                                .heightIn(min = 32.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Album,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = stringResource(
                                                    Res.string.title_disc_number,
                                                    group.key as Int
                                                ),
                                                style = MaterialTheme.typography.titleMediumEmphasized,
                                                fontWeight = FontWeight(600),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(group.value) { index, song ->
                                    val download = allDownloads.find { it.songId == song.id }
                                    Box {
                                        CollectionDetailScreenSongRow(
                                            song = song,
                                            index = index,
                                            count = group.value.count(),
                                            isPlaylist = false,
                                            onClick = {
                                                if (playerState.currentSong?.id != song.id) {
                                                    player.playCollectionAt(
                                                        collection = album,
                                                        index = album.songs.indexOfFirst { it.id == song.id }
                                                    )
                                                } else {
                                                    player.togglePlay()
                                                }
                                            },
                                            onLongClick = {
                                                viewModel.selectSong(song)
                                            },
                                            onPlayNext = {
                                                player.playNextSingle(song)
                                            },
                                            onAddToQueue = {
                                                player.addToQueueSingle(song)
                                            },
                                            isStarred = song.starredAt != null,
                                            download = download,
                                            isOffline = !isOnline
                                        )
                                        CollectionDetailScreenSongRowDropdown(
                                            expanded = selection == song,
                                            onDismissRequest = { viewModel.clearSelection() },
                                            onRemoveStar = { viewModel.unstarSelectedSong() },
                                            onAddStar = { viewModel.starSelectedSong() },
                                            onShare = { shareId = song.id },
                                            collection = collection,
                                            song = song,
                                            onRemoveFromPlaylist = { viewModel.removeFromPlaylist() },
                                            starred = selectedSongIsStarred,
                                            downloadStatus = download?.status,
                                            onDownload = { viewModel.downloadSong(song) },
                                            onCancelDownload = { viewModel.cancelDownload(song.id) },
                                            onDeleteDownload = { viewModel.deleteDownload(song.id) },
                                            onPlayNext = { player.playNextSingle(song) },
                                            onAddToQueue = { player.addToQueueSingle(song) },
                                            rating = selectedSongRating,
                                            onSetRating = { viewModel.rateSelectedSong(it) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val playlist = displayCollection as? DomainPlaylist
                            ?: (collection as DomainPlaylist)
                        itemsIndexed(playlist.songs) { index, song ->
                            val download = allDownloads.find { it.songId == song.id }
                            Box {
                                CollectionDetailScreenSongRow(
                                    song = song,
                                    index = index,
                                    count = playlist.songs.count(),
                                    isPlaylist = true,
                                    onClick = {
                                        if (playerState.currentSong?.id != song.id) {
                                            player.playCollectionAt(
                                                collection = playlist,
                                                index = index
                                            )
                                        } else {
                                            player.togglePlay()
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.selectSong(song)
                                    },
                                    onPlayNext = {
                                        player.playNextSingle(song)
                                    },
                                    onAddToQueue = {
                                        player.addToQueueSingle(song)
                                    },
                                    isStarred = song.starredAt != null,
                                    download = download,
                                    isOffline = !isOnline
                                )
                                CollectionDetailScreenSongRowDropdown(
                                    expanded = selection == song,
                                    onDismissRequest = { viewModel.clearSelection() },
                                    onRemoveStar = { viewModel.unstarSelectedSong() },
                                    onAddStar = { viewModel.starSelectedSong() },
                                    onShare = { shareId = song.id },
                                    collection = collection,
                                    song = song,
                                    onRemoveFromPlaylist = { viewModel.removeFromPlaylist() },
                                    starred = selectedSongIsStarred,
                                    downloadStatus = download?.status,
                                    onDownload = { viewModel.downloadSong(song) },
                                    onCancelDownload = { viewModel.cancelDownload(song.id) },
                                    onDeleteDownload = { viewModel.deleteDownload(song.id) },
                                    onPlayNext = { player.playNextSingle(song) },
                                    onAddToQueue = { player.addToQueueSingle(song) },
                                    rating = selectedSongRating,
                                    onSetRating = { viewModel.rateSelectedSong(it) }
                                )
                            }
                        }
                    }

                    if ((displayCollection ?: collection).songs.isEmpty()) {
                        item {
                            ContentUnavailable(
                                icon = Icons.Outlined.Note,
                                label = stringResource(Res.string.info_no_songs)
                            )
                        }
                    }

                    item { CollectionDetailScreenFooterRow(displayCollection ?: collection) }

                    (collection as? DomainAlbum)?.artistName?.let { artistName ->
                        collectionDetailScreenMoreByArtistRow(
                            artistName = artistName,
                            artistAlbums = otherAlbums,
                            selectedAlbum = selectedAlbum,
                            onSetShareId = { shareId = it },
                            onPlayNext = if (selectedAlbum != null) {
                                { player.playNext(selectedAlbum as DomainSongCollection) }
                            } else null,
                            onAddToQueue = if (selectedAlbum != null) {
                                { player.addToQueue(selectedAlbum as DomainSongCollection) }
                            } else null,
                            selectedAlbumRating = selectedAlbumRating,
                            selectedAlbumStarred = selectedAlbumIsStarred,
                            onSetAlbumRating = { viewModel.rateSelectedAlbum(it) },
                            onSetAlbumStarred = { viewModel.starSelectedAlbum(it) },
                            onSelect = { viewModel.selectAlbum(it) },
                            onDeselect = { viewModel.clearSelection() },
                            tab = tab
                        )
                    }
                }
            }
        }

		ErrorSnackBar(
			error = (collectionState as? UiState.Error)?.error,
			onClearError = { viewModel.clearError() }
		)

        ShareDialog(
            id = shareId,
            onIdClear = { shareId = null; viewModel.clearSelection() },
            expiry = shareExpiry,
            onExpiryChange = { shareExpiry = it }
        )
    }
}

private fun List<DomainSong>.sortedByPlaylistSongSort(
    sortType: CollectionSongSortType,
    reversed: Boolean,
    albumsById: Map<String, DomainAlbum>
): List<DomainSong> {
    return when (sortType) {
        CollectionSongSortType.AlbumArtist -> {
            val sorted = sortedWith { first, second ->
                val firstResolution = resolveAlbumArtistSortKey(first, albumsById)
                val secondResolution = resolveAlbumArtistSortKey(second, albumsById)
                val firstArtist = firstResolution.key
                val secondArtist = secondResolution.key
                val firstBlank = firstArtist.isBlank()
                val secondBlank = secondArtist.isBlank()

                if (firstBlank != secondBlank) {
                    return@sortedWith if (firstBlank) 1 else -1
                }

                val artistComparison = if (reversed) {
                    secondArtist.compareTo(firstArtist, ignoreCase = true)
                } else {
                    firstArtist.compareTo(secondArtist, ignoreCase = true)
                }
                if (artistComparison != 0) return@sortedWith artistComparison

                val firstAlbumName = resolveAlbumNameSortKey(first, albumsById)
                val secondAlbumName = resolveAlbumNameSortKey(second, albumsById)
                val albumNameComparison = firstAlbumName.compareTo(secondAlbumName, ignoreCase = true)
                if (albumNameComparison != 0) return@sortedWith albumNameComparison

                val firstDisc = first.discNumber ?: Int.MAX_VALUE
                val secondDisc = second.discNumber ?: Int.MAX_VALUE
                val discComparison = firstDisc.compareTo(secondDisc)
                if (discComparison != 0) return@sortedWith discComparison

                val firstTrack = first.trackNumber ?: Int.MAX_VALUE
                val secondTrack = second.trackNumber ?: Int.MAX_VALUE
                val trackComparison = firstTrack.compareTo(secondTrack)
                if (trackComparison != 0) return@sortedWith trackComparison

                first.title.compareTo(second.title, ignoreCase = true)
            }

            sorted.forEach { song ->
                val resolution = resolveAlbumArtistSortKey(song, albumsById)
                Logger.i(
                    "PlaylistAlbumArtistSort",
                    "title=${song.title} | artistName=${song.artistName} | albumName=${resolveAlbumNameSortKey(song, albumsById)} | albumId=${song.albumId} | resolvedAlbumArtistSortKey=${resolution.key} | source=${resolution.source}"
                )
            }

            sorted
        }
    }
}

private data class AlbumArtistResolution(
    val key: String,
    val source: String
)

private fun resolveAlbumArtistSortKey(
    song: DomainSong,
    albumsById: Map<String, DomainAlbum>
): AlbumArtistResolution {
    val displayAlbumArtist = song.displayAlbumArtist
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (displayAlbumArtist != null) {
        return AlbumArtistResolution(
            key = displayAlbumArtist,
            source = "song.displayAlbumArtist"
        )
    }

    val albumArtistName = song.albumArtistName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (albumArtistName != null) {
        return AlbumArtistResolution(
            key = albumArtistName,
            source = "song.albumArtistName"
        )
    }

    val joinedAlbumArtists = song.albumArtists
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
    if (joinedAlbumArtists != null) {
        return AlbumArtistResolution(
            key = joinedAlbumArtists,
            source = "song.albumArtists"
        )
    }

    val albumArtistFromAlbumLookup = song.albumId
        ?.let { albumId -> albumsById[albumId] }
        ?.artistName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (albumArtistFromAlbumLookup != null) {
        return AlbumArtistResolution(
            key = albumArtistFromAlbumLookup,
            source = "albumLookup.album.artistName"
        )
    }

    return AlbumArtistResolution(
        key = song.artistName.trim(),
        source = "song.artistName(fallback)"
    )
}

private fun resolveAlbumNameSortKey(
    song: DomainSong,
    albumsById: Map<String, DomainAlbum>
): String {
    return song.albumTitle
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: song.albumId
            ?.let { albumId -> albumsById[albumId] }
            ?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        ?: ""
}
