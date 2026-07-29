package paige.navic.di

import org.koin.dsl.module
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.DownloadDatabase

val databaseModule = module {
	// CacheDatabase is initialized in the platform module because Android requires a Context.
	single { get<CacheDatabase>().albumDao() }
	single { get<CacheDatabase>().genreDao() }
	single { get<CacheDatabase>().playlistDao() }
	single { get<CacheDatabase>().songDao() }
	single { get<CacheDatabase>().artistDao() }
	single { get<CacheDatabase>().radioDao() }
	single { get<CacheDatabase>().lyricDao() }
	single { get<CacheDatabase>().syncActionDao() }
	single { get<CacheDatabase>().scrobbleDao() }
	single { get<DownloadDatabase>().downloadDao() }
}
