package dan.sonora.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import dan.sonora.domain.models.DomainSong
import dan.sonora.ui.components.dialogs.DeletionViewModel
import dan.sonora.ui.screens.album.viewmodels.AlbumListViewModel
import dan.sonora.ui.screens.artist.viewmodels.ArtistDetailViewModel
import dan.sonora.ui.screens.artist.viewmodels.ArtistListViewModel
import dan.sonora.ui.screens.collection.viewmodels.CollectionDetailViewModel
import dan.sonora.ui.screens.genre.viewmodels.GenreListViewModel
import dan.sonora.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import dan.sonora.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import dan.sonora.ui.screens.playlist.viewmodels.PlaylistCreateDialogViewModel
import dan.sonora.ui.screens.playlist.viewmodels.PlaylistListViewModel
import dan.sonora.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel
import dan.sonora.ui.screens.queue.viewmodels.QueueViewModel
import dan.sonora.ui.screens.radio.viewmodels.RadioCreateDialogViewModel
import dan.sonora.ui.screens.radio.viewmodels.RadioListViewModel
import dan.sonora.ui.screens.search.viewmodels.SearchViewModel
import dan.sonora.ui.screens.settings.viewmodels.LyricsPriorityViewModel
import dan.sonora.ui.screens.settings.viewmodels.NavtabsViewModel
import dan.sonora.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import dan.sonora.ui.screens.share.viewmodels.ShareDialogViewModel
import dan.sonora.ui.screens.share.viewmodels.ShareListViewModel
import dan.sonora.ui.screens.song.viewmodels.SongDetailViewModel
import dan.sonora.ui.screens.song.viewmodels.SongListViewModel
import dan.sonora.ui.screens.settings.viewmodels.InsightsSettingsViewModel
import dan.sonora.ui.screens.stats.viewmodels.AdvancedStatisticsViewModel
import dan.sonora.ui.screens.stats.viewmodels.InsightsViewModel

val viewModelModule = module {
	viewModel { (artistId: String) ->
		ArtistDetailViewModel(
			artistId = artistId,
			repository = get(),
			artistRepository = get(),
			songRepository = get(),
			albumRepository = get(),
			artistDao = get(),
			albumDao = get(),
			downloadManager = get(),
			snackBarManager = get(),
			connectivityManager = get()
		)
	}

	viewModel { (song: DomainSong?) ->
		LyricsScreenViewModel(
			song = song,
			repository = get()
		)
	}

	viewModel { (songs: List<DomainSong>, playlistToExclude: String?) ->
		PlaylistUpdateDialogViewModel(
			songs = songs,
			playlistToExclude = playlistToExclude,
			sessionManager = get(),
			snackBarManager = get()
		)
	}

	viewModelOf(::AlbumListViewModel)
	viewModelOf(::SongListViewModel)
	viewModelOf(::ArtistListViewModel)
	viewModelOf(::SearchViewModel)
	viewModelOf(::GenreListViewModel)
	viewModelOf(::RadioListViewModel)
	viewModelOf(::RadioCreateDialogViewModel)
	viewModelOf(::PlaylistListViewModel)
	viewModelOf(::QueueViewModel)
	viewModelOf(::ShareListViewModel)
	viewModelOf(::DeletionViewModel)
	viewModelOf(::ShareDialogViewModel)
	viewModel { (songs: List<DomainSong>) ->
		PlaylistCreateDialogViewModel(
			songs = songs,
			playlistDao = get(),
			sessionManager = get(),
			snackBarManager = get()
		)
	}
	viewModel { params ->
		CollectionDetailViewModel(
			collectionId = params.get(),
			repository = get(),
			songRepository = get(),
			albumRepository = get(),
			downloadManager = get(),
			sessionManager = get(),
			snackBarManager = get(),
			connectivityManager = get()
		)
	}
	viewModelOf(::SongDetailViewModel)
	viewModelOf(::SettingsDataStorageViewModel)
	viewModel { params ->
		NowPlayingViewModel(
			player = params.get(),
			songRepository = get()
		)
	}
	viewModelOf(::NavtabsViewModel)
	viewModelOf(::LyricsPriorityViewModel)
	viewModelOf(::InsightsViewModel)
	viewModelOf(::InsightsSettingsViewModel)
	viewModelOf(::AdvancedStatisticsViewModel)
}
