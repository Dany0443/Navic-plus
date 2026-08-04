package dan.sonora.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import dan.sonora.data.remote.SonoraApi
import dan.sonora.data.stats.lastfm.LastFmApi
import dan.sonora.data.stats.lastfm.LastFmAuthStore
import dan.sonora.data.stats.lastfm.LastFmStatsProvider
import dan.sonora.domain.manager.DownloadManager
import dan.sonora.domain.manager.LoginManager
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.ScrobbleSyncManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.manager.SleepTimerManager
import dan.sonora.domain.manager.SmartPlaylistManager
import dan.sonora.domain.manager.SnackBarManager
import dan.sonora.domain.manager.SyncManager
import dan.sonora.domain.repositories.LocalMusicRepository
import dan.sonora.domain.stats.InsightsRepository
import dan.sonora.domain.stats.ProviderSyncStore
import dan.sonora.domain.stats.StatsProviderRegistry

val managerModule = module {
	single { SonoraApi(SonoraApi.DEFAULT_BASE_URL) }
	singleOf(::SleepTimerManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	singleOf(::DownloadManager)
	singleOf(::LastFmAuthStore)
	singleOf(::LastFmApi)
	singleOf(::LastFmStatsProvider)
	singleOf(::ProviderSyncStore)
	// Registering an additional provider here is all that is needed to surface it in
	// Insights onboarding and settings — both are generated from this list.
	single {
		StatsProviderRegistry(
			providers = listOf(get<LastFmStatsProvider>()),
			preferenceManager = get()
		)
	}
	singleOf(::ScrobbleSyncManager)
	singleOf(::InsightsRepository)
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
