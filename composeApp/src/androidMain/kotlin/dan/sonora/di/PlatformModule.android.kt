package dan.sonora.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import dan.sonora.data.database.CacheDatabase
import dan.sonora.data.database.DownloadDatabase
import dan.sonora.domain.manager.AppIconManager
import dan.sonora.domain.manager.ConnectivityManager
import dan.sonora.domain.manager.EqualizerSettingsUpdater
import dan.sonora.domain.manager.InsightsAutoSyncScheduler
import dan.sonora.domain.manager.LogManager
import dan.sonora.domain.manager.PermissionManager
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.ShareManager
import dan.sonora.domain.manager.StorageManager
import dan.sonora.domain.localmusic.LocalMusicScanner
import dan.sonora.domain.repositories.PlayerStateRepository
import dan.sonora.shared.AndroidMediaPlayerViewModel
import dan.sonora.shared.MediaPlayerViewModel
import dan.sonora.shared.playback.EqualizerSettingsProvider
import dan.sonora.util.core.PlatformType

actual val platformModule = module {
	single { PlatformType.Android }
	single<CacheDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("cache.db")
			.absolutePath
		Room
			.databaseBuilder<CacheDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<DownloadDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("downloads.db")
			.absolutePath
		Room
			.databaseBuilder<DownloadDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<PlayerStateRepository> {
		val context = androidApplication()
		val producePath = {
			context.filesDir.resolve(PlayerStateRepository.DATASTORE_FILE_NAME).absolutePath
		}
		PlayerStateRepository(PlayerStateRepository.getInstance(producePath))
	}

	single<MediaPlayerViewModel> {
		AndroidMediaPlayerViewModel(
			application = androidApplication(),
			stateRepository = get(),
			albumDao = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get(),
			platformContext = get(),
			preferenceManager = get(),
			snackBarManager = get()
		)
	}

	singleOf(::ShareManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	single { LocalMusicScanner(get()) }
	singleOf(::AppIconManager)
	singleOf(::PermissionManager)
	single { InsightsAutoSyncScheduler(androidApplication()) }
	single {
		EqualizerSettingsProvider(
			initialSettings = get<PreferenceManager>().equalizerSettings,
		)
	}
	single<EqualizerSettingsUpdater> { get<EqualizerSettingsProvider>() }
}
