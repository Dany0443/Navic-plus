package dan.sonora.domain.repositories

import kotlinx.coroutines.CancellationException
import dan.sonora.data.database.dao.AlbumDao
import dan.sonora.data.database.dao.ArtistDao
import dan.sonora.data.database.dao.PlaylistDao
import dan.sonora.data.database.dao.SongDao
import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.data.database.mappers.toEntity
import dan.sonora.domain.manager.ConnectivityManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.util.core.Logger

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val isOnline = connectivityManager.isOnline

	suspend fun search(query: String): List<Any> {
		return if (isOnline.value) {
			try {
				val data = sessionManager.api.searchID3(query)

				albumDao.insertAlbumsIgnoringConflicts(data.albums.map { it.toEntity() })
				artistDao.insertArtistsIgnoringConflicts(data.artists.map { it.toEntity() })
				songDao.insertSongsIgnoringConflicts(data.songs.map { it.toEntity() })

				val albums = albumDao.getAlbumsByIds(data.albums.map { it.id })
				val artists = artistDao.getArtistsByIds(data.artists.map { it.id })
				val songs = songDao.getSongsByIds(data.songs.map { it.id })
				val localPlaylists = playlistDao.searchPlaylistsList(query)

				(albums.map { it.toDomainModel() }
					+ artists.map { it.toDomainModel() }
					+ songs.map { it.toDomainModel() }
					+ localPlaylists.map { it.toDomainModel() })
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.e(
					"SearchRepository",
					"Online search failed despite network connection, falling back to local DB",
					e
				)
				performLocalSearch(query)
			}
		} else {
			Logger.i("SearchRepository", "Device offline, performing local search.")
			performLocalSearch(query)
		}
	}

	private suspend fun performLocalSearch(query: String): List<Any> {
		val localAlbums = albumDao.searchAlbumsList(query).map { it.toDomainModel() }
		val localArtists = artistDao.searchArtistsList(query).map { it.toDomainModel() }
		val localSongs = songDao.searchSongsList(query).map { it.toDomainModel() }
		val localPlaylists = playlistDao.searchPlaylistsList(query).map { it.toDomainModel() }

		return listOf(localAlbums, localArtists, localSongs, localPlaylists).flatten()
	}
}
