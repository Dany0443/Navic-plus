package dan.sonora.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import dan.sonora.data.database.dao.AlbumDao
import dan.sonora.data.database.dao.ArtistDao
import dan.sonora.data.database.dao.DownloadDao
import dan.sonora.data.database.dao.GenreDao
import dan.sonora.data.database.dao.LyricDao
import dan.sonora.data.database.dao.PlaylistDao
import dan.sonora.data.database.dao.RadioDao
import dan.sonora.data.database.dao.SongDao
import dan.sonora.data.database.dao.SyncActionDao
import dan.sonora.data.database.dao.ScrobbleDao
import dan.sonora.data.database.entities.AlbumEntity
import dan.sonora.data.database.entities.ArtistEntity
import dan.sonora.data.database.entities.DownloadEntity
import dan.sonora.data.database.entities.GenreEntity
import dan.sonora.data.database.entities.LyricEntity
import dan.sonora.data.database.entities.PlaylistEntity
import dan.sonora.data.database.entities.PlaylistSongCrossRef
import dan.sonora.data.database.entities.RadioEntity
import dan.sonora.data.database.entities.SongEntity
import dan.sonora.data.database.entities.SyncActionEntity
import dan.sonora.data.database.entities.ScrobbleEntity

@Database(
	version = 18,
	entities = [
		AlbumEntity::class,
		GenreEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		SongEntity::class,
		ArtistEntity::class,
		RadioEntity::class,
		LyricEntity::class,
		SyncActionEntity::class,
		DownloadEntity::class,
		ScrobbleEntity::class
	]
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun genreDao(): GenreDao
	abstract fun downloadDao(): DownloadDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun songDao(): SongDao
	abstract fun artistDao(): ArtistDao
	abstract fun radioDao(): RadioDao
	abstract fun lyricDao(): LyricDao
	abstract fun syncActionDao(): SyncActionDao
	abstract fun scrobbleDao(): ScrobbleDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
	override fun initialize(): CacheDatabase
}
