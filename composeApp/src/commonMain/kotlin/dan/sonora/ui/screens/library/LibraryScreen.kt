package dan.sonora.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.title_library
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import dan.sonora.LocalBottomBarScrollManager
import dan.sonora.LocalNavStack
import dan.sonora.domain.manager.LoginManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.manager.SmartPlaylistManager
import dan.sonora.domain.models.DomainAlbumListType
import dan.sonora.ui.navigation.Screen
import dan.sonora.domain.models.DomainArtistListType
import dan.sonora.domain.models.DomainSongCollection
import dan.sonora.domain.models.SmartPlaylist
import dan.sonora.shared.MediaPlayerViewModel
import dan.sonora.ui.components.dialogs.DeletionDialog
import dan.sonora.ui.components.dialogs.DeletionEndpoint
import dan.sonora.ui.components.layouts.PullToRefreshBox
import dan.sonora.ui.components.layouts.RootBottomBar
import dan.sonora.ui.components.layouts.RootTopBar
import dan.sonora.ui.components.snackbars.ErrorSnackBar
import dan.sonora.ui.core.LoginUiState
import dan.sonora.ui.core.UiState
import dan.sonora.ui.navigation.PersistentViewModelStoreOwner
import dan.sonora.ui.screens.album.viewmodels.AlbumListViewModel
import dan.sonora.ui.screens.artist.viewmodels.ArtistListViewModel
import dan.sonora.ui.screens.genre.viewmodels.GenreListViewModel
import dan.sonora.ui.screens.library.components.LibraryScreenContent
import dan.sonora.ui.screens.playlist.dialogs.PlaylistCreateDialog
import dan.sonora.ui.screens.playlist.viewmodels.PlaylistListViewModel
import dan.sonora.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
	val sessionManager = koinInject<SessionManager>()
	val serverConfigured by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	if (!serverConfigured) {
		ServerLibraryEmptyState()
		return
	}
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "libraryAlbums",
		parameters = { parametersOf(DomainAlbumListType.Recent) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val playlistsViewModel = koinViewModel<PlaylistListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val playlistsState by playlistsViewModel.playlistsState.collectAsStateWithLifecycle()
	val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "libraryArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	val genresViewModel = koinViewModel<GenreListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val genresState by genresViewModel.genresState.collectAsStateWithLifecycle()

	val loginManager = koinInject<LoginManager>()
	val loginState by loginManager.loginState.collectAsStateWithLifecycle()
	val smartPlaylistManager = koinInject<SmartPlaylistManager>()

	val smartPlaylistsState by smartPlaylistManager
		.getSmartPlaylistsFlow(fullRefresh = false)
		.collectAsStateWithLifecycle(initialValue = UiState.Loading())
	val preferenceManager = koinInject<dan.sonora.domain.manager.PreferenceManager>()
	val localMusicEnabled = preferenceManager.enableLocalMusic

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var playlistCreateDialogShown by rememberSaveable { mutableStateOf(false) }

	val player = koinInject<MediaPlayerViewModel>()
    val navStack = LocalNavStack.current
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	LaunchedEffect(loginState is LoginUiState.Success) {
		albumsViewModel.refreshAlbums(false)
		playlistsViewModel.refreshPlaylists(false)
		artistsViewModel.refreshArtists(false)
		genresViewModel.refreshGenres(false)
	}

	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(Res.string.title_library)) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = albumsState !is UiState.Loading &&
				playlistsState !is UiState.Loading &&
				artistsState !is UiState.Loading &&
				genresState !is UiState.Loading,
			onRefresh = {
				albumsViewModel.refreshAlbums(true)
				playlistsViewModel.refreshPlaylists(true)
				artistsViewModel.refreshArtists(true)
				genresViewModel.refreshGenres(true)
			},
			key = listOf(albumsState, playlistsState, artistsState, genresState)
		) {
			LibraryScreenContent(
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },
				smartPlaylistsState = smartPlaylistsState,
				onSelectSmartPlaylist = { playlist ->
					navStack.add(Screen.SongList(true, playlist.listType))
				},

				albumsState = albumsState,
				selectedAlbum = selectedAlbum,
				selectedAlbumIsStarred = selectedAlbumIsStarred,
				selectedAlbumRating = selectedAlbumRating,
				onSelectAlbum = { albumsViewModel.selectAlbum(it) },
				onClearAlbumSelection = { albumsViewModel.clearSelection() },
				onStarSelectedAlbum = { albumsViewModel.starAlbum(it) },
				onPlayAlbumNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection) },
				onAddAlbumToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection) },
				onRateSelectedAlbum = { albumsViewModel.setRating(it) },

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = {
					if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(
						player
					)
				},
				onAddArtistToQueue = {
					if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(
						player
					)
				},

				playlistsState = playlistsState,
				selectedPlaylist = selectedPlaylist,
				onSelectPlaylist = { playlistsViewModel.selectPlaylist(it) },
				onClearPlaylistSelection = { playlistsViewModel.clearSelection() },
				onDeletePlaylist = { playlistDeletionId = it },
				onPlayPlaylistNext = {
					if (selectedPlaylist != null) player.playNext(
						selectedPlaylist as DomainSongCollection
					)
				},
				onAddPlaylistToQueue = {
					if (selectedPlaylist != null) player.addToQueue(
						selectedPlaylist as DomainSongCollection
					)
				},

				genresState = genresState,
				localMusicEnabled = localMusicEnabled
			)
		}
	}

	val flattenedErrors = listOf(
		(albumsState as? UiState.Error)?.error,
		(playlistsState as? UiState.Error)?.error,
		(artistsState as? UiState.Error)?.error,
		(genresState as? UiState.Error)?.error
	).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackBar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			albumsViewModel.clearError()
			playlistsViewModel.clearError()
			artistsViewModel.clearError()
			genresViewModel.clearError()
		}
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = playlistDeletionId,
		onIdClear = { playlistDeletionId = null },
		onRefresh = { playlistsViewModel.refreshPlaylists(false) }
	)

	if (playlistCreateDialogShown) {
		PlaylistCreateDialog(
			onDismissRequest = { playlistCreateDialogShown = false },
			onRefresh = { playlistsViewModel.refreshPlaylists(true) }
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerLibraryEmptyState() {
	val navStack = LocalNavStack.current
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(Res.string.title_library)) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
			verticalArrangement = Arrangement.Center,
		) {
			Text("Music, your way.", style = MaterialTheme.typography.headlineSmall)
			Text(
				"Sonora works with music stored on your device and with your personal server. Connect whenever you want synchronized libraries, playlists, and history.",
				modifier = Modifier.padding(top = 12.dp),
				style = MaterialTheme.typography.bodyLarge,
			)
			Button(
				onClick = { navStack.add(Screen.Login) },
				modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
			) {
				Text("Connect Server")
			}
		}
	}
}
