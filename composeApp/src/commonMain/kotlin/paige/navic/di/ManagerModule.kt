package paige.navic.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.data.remote.NavicApi
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LastFmManager
import paige.navic.domain.manager.LastFmSyncManager
import paige.navic.domain.manager.LoginManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SleepTimerManager
import paige.navic.domain.manager.SmartPlaylistManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.repositories.LocalMusicRepository

val managerModule = module {
	single { NavicApi(NavicApi.DEFAULT_BASE_URL) }
	singleOf(::SleepTimerManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	singleOf(::DownloadManager)
	singleOf(::LastFmManager)
	singleOf(::LastFmSyncManager)
	singleOf(::SessionManager)
	singleOf(::PreferenceManager)
	// SmartPlaylistManager has a default `rules` constructor parameter.
	// Koin's `singleOf(::SmartPlaylistManager)` uses constructor injection and will still try
	// to resolve `List<SmartPlaylistRule>` as a bean, which fails at runtime.
	// By instantiating manually, Kotlin will apply the default rules.
	single { SmartPlaylistManager(get(), get()) }
	singleOf(::SnackBarManager)
	singleOf(::LoginManager)
	single { LocalMusicRepository(get(), get()) }
}
