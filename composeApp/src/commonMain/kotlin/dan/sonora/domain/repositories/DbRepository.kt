package dan.sonora.domain.repositories

import androidx.room3.concurrent.AtomicInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_syncing
import sonora.composeapp.generated.resources.info_syncing_albums
import sonora.composeapp.generated.resources.info_syncing_artists
import sonora.composeapp.generated.resources.info_syncing_finished
import sonora.composeapp.generated.resources.info_syncing_genres
import sonora.composeapp.generated.resources.info_syncing_playlists
import sonora.composeapp.generated.resources.info_syncing_radios
import sonora.composeapp.generated.resources.info_syncing_saved
import org.jetbrains.compose.resources.StringResource
import dan.sonora.data.database.dao.AlbumDao
import dan.sonora.data.database.dao.ArtistDao
import dan.sonora.data.database.dao.GenreDao
import dan.sonora.data.database.dao.LyricDao
import dan.sonora.data.database.dao.PlaylistDao
import dan.sonora.data.database.dao.RadioDao
import dan.sonora.data.database.dao.SongDao
import dan.sonora.data.database.dao.SyncActionDao
import dan.sonora.data.database.entities.AlbumEntity
import dan.sonora.data.database.entities.PlaylistEntity
import dan.sonora.data.database.entities.PlaylistSongCrossRef
import dan.sonora.data.database.entities.SongEntity
import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.data.database.mappers.toEntity
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.models.DomainArtist
import dan.sonora.util.core.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.AlbumListType as ApiAlbumListType

class DbRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val genreDao: GenreDao,
	private val artistDao: ArtistDao,
	private val radioDao: RadioDao,
	private val lyricDao: LyricDao,
	private val syncDao: SyncActionDao,
	private val sessionManager: SessionManager
) {
	private val concurrentRequestLimit = Semaphore(20)

	private val syncMutex = Mutex()

	private val dbChunkSize = 500 // should be enough

	private suspend fun <T> runDbOp(block: suspend () -> T): Result<T> =
		withContext(Dispatchers.IO) {
			try {
				Result.success(block())
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Result.failure(e)
			}
		}

	suspend fun removeEverything(): Result<Unit> = runDbOp {
		albumDao.clearAllAlbums()
		playlistDao.clearAllPlaylists()
		songDao.clearAllSongs()
		genreDao.clearAllGenres()
		artistDao.clearAllArtists()
		radioDao.clearAllRadios()
		lyricDao.clearAllLyrics()
		syncDao.clearAllActions()
		Logger.i("DbRepository", "Database wiped completely.")
	}

	suspend fun syncEverything(
		onProgress: (Float, StringResource) -> Unit = { _, _ -> }
	): Result<Unit> = syncMutex.withLock {
		runDbOp {
		val progressCallback = { progress: Float, message: StringResource ->
			Logger.i("DbRepository", "$progress $message")
			onProgress(progress, message)
		}

		progressCallback(0.0f, Res.string.info_syncing)

		progressCallback(0.01f, Res.string.info_syncing_genres)
		syncGenres().getOrThrow()

		progressCallback(0.02f, Res.string.info_syncing_radios)
		syncRadios().getOrThrow()

		progressCallback(0.04f, Res.string.info_syncing_artists)
		syncArtists().getOrThrow()

		progressCallback(0.07f, Res.string.info_syncing_playlists)
		val playlists = syncPlaylists().getOrThrow()

		syncLibrarySongs { localProgress, message ->
			val globalProgress = 0.10f + (localProgress * 0.65f)
			progressCallback(globalProgress, message)
		}.getOrThrow()

		val totalPlaylists = playlists.size
		if (totalPlaylists > 0) {
			val completedPlaylists = AtomicInt(0)

			coroutineScope {
				playlists.map { playlist ->
					async {
						concurrentRequestLimit.withPermit {
							syncPlaylistSongs(playlist.playlistId).getOrThrow()
							val done = completedPlaylists.incrementAndGet()
							val globalProgress = 0.75f + (0.25f * (done.toFloat() / totalPlaylists))
							progressCallback(globalProgress, Res.string.info_syncing_playlists)
						}
					}
				}.awaitAll()
			}
		}

		progressCallback(1.0f, Res.string.info_syncing_finished)
		}
	}

	suspend fun syncLibrarySongs(
		onProgress: (Float, StringResource) -> Unit = { _, _ -> }
	): Result<Int> = runDbOp {
		val albumPageSize = 500
		val windowSize = 8
		var albumCap = 0
		var albumOffset = 0
		val allAlbumSummaries = mutableListOf<ApiAlbum>()

		onProgress(0.0f, Res.string.info_syncing_albums)
		while (true) {
			val batches = coroutineScope {
				(0 until windowSize).map { index ->
					async {
						concurrentRequestLimit.withPermit {
							sessionManager.api.getAlbums(
								ApiAlbumListType.AlphabeticalByName,
								albumPageSize,
								albumOffset + (index * albumPageSize)
							)
						}
					}
				}.awaitAll()
			}
			albumCap = maxOf(albumCap, batches.maxOf { it.size })
			if (batches.all { it.isEmpty() }) break
			allAlbumSummaries.addAll(batches.flatten())
			if (batches.last().size < albumCap) break
			albumOffset += windowSize * albumPageSize
		}

		if (allAlbumSummaries.isEmpty()) return@runDbOp 0

		val totalAlbums = allAlbumSummaries.size
		val estimatedTotalSongs = allAlbumSummaries.sumOf { it.songCount }.coerceAtLeast(1)
		var finalSongsSynced = 0

		val allValidAlbumIds = mutableSetOf<String>()
		val allValidSongIds = mutableSetOf<String>()

		onProgress(0.1f, Res.string.info_syncing_albums)

		val albumEntities = allAlbumSummaries.map { it.toEntity() }
		albumEntities.forEach { allValidAlbumIds.add(it.albumId) }
		albumEntities.chunked(dbChunkSize).forEach { chunk ->
			albumDao.insertAlbums(chunk)
		}

		val songPageSize = 2000
		val probe = sessionManager.api.searchID3(
			query = "",
			artistCount = 0,
			albumCount = 0,
			songCount = songPageSize,
			songOffset = 0
		)
		val effectivePageSize = probe.songs.size.takeIf { it in 1 until songPageSize } ?: songPageSize
		var songCap = effectivePageSize
		var songOffset = 0
		val existingSongIds = songDao.getAllSongIds().toMutableSet()
		Logger.i("DbRepository", "song walk start, estimated total=$estimatedTotalSongs, existing=${existingSongIds.size}, pageSize=$effectivePageSize")

		val probeEntities = probe.songs.map { it.toEntity() }
		val probeNew = probeEntities.filter { it.songId !in existingSongIds }
		probeEntities.forEach { allValidSongIds.add(it.songId) }
		probeNew.forEach { existingSongIds.add(it.songId) }
		if (probeNew.isNotEmpty()) {
			songDao.insertSongsPrepared(probeNew)
		}
		finalSongsSynced += probeEntities.size
		songOffset = effectivePageSize

		while (true) {
			val windowStart = Clock.System.now().toEpochMilliseconds()
			val results = coroutineScope {
				(0 until windowSize).map { index ->
					async {
						concurrentRequestLimit.withPermit {
							sessionManager.api.searchID3(
								query = "",
								artistCount = 0,
								albumCount = 0,
								songCount = effectivePageSize,
								songOffset = songOffset + (index * effectivePageSize)
							)
						}
					}
				}.awaitAll()
			}
			val fetchMs = Clock.System.now().toEpochMilliseconds() - windowStart
			songCap = maxOf(songCap, results.maxOf { it.songs.size })
			if (results.all { it.songs.isEmpty() }) break

			val mapStart = Clock.System.now().toEpochMilliseconds()
			val songEntities = results.flatMap { it.songs }.map { it.toEntity() }
			val newEntities = songEntities.filter { it.songId !in existingSongIds }
			songEntities.forEach { allValidSongIds.add(it.songId) }
			newEntities.forEach { existingSongIds.add(it.songId) }
			val mapMs = Clock.System.now().toEpochMilliseconds() - mapStart

			val insertStart = Clock.System.now().toEpochMilliseconds()
			if (newEntities.isNotEmpty()) {
				songDao.insertSongsPrepared(newEntities)
			}
			val insertMs = Clock.System.now().toEpochMilliseconds() - insertStart

			finalSongsSynced += songEntities.size

			Logger.i(
				"DbRepository",
				"song window offset=$songOffset pages=${results.map { it.songs.size }} fetched=${songEntities.size} new=${newEntities.size} fetchMs=$fetchMs mapMs=$mapMs insertMs=$insertMs"
			)

			val fetchProgress = 0.1f + (0.8f * (finalSongsSynced.toFloat() / estimatedTotalSongs))
			onProgress(fetchProgress.coerceIn(0.1f, 0.9f), Res.string.info_syncing_albums)

			if (results.last().songs.size < songCap) break
			songOffset += windowSize * effectivePageSize
		}

		val deleteStart = Clock.System.now().toEpochMilliseconds()
		albumDao.deleteObsoleteAlbums(allValidAlbumIds)
		// Album summaries are not a complete catalogue: valid tracks can be present only
		// in playlists (for example when their album metadata is unavailable).  Retain
		// those rows so the main library's SELECT * query and playlist/search paths share
		// the same complete song set.
		val playlistSongIds = playlistDao.getAllPlaylistSongIds().toSet()
		songDao.deleteObsoleteSongs(allValidSongIds + playlistSongIds)
		Logger.i("DbRepository", "deleteObsolete took ${Clock.System.now().toEpochMilliseconds() - deleteStart}ms")

		Logger.i(
			"DbRepository",
			"- Songs Synced: $totalAlbums albums, $finalSongsSynced songs"
		)

		onProgress(1.0f, Res.string.info_syncing_saved)
		finalSongsSynced
	}

	suspend fun syncPlaylists(): Result<List<PlaylistEntity>> = runDbOp {
		val remotePlaylists = sessionManager.api.getPlaylists()
		val playlistEntities = remotePlaylists.map { it.toEntity() }
		val validPlaylistIds = playlistEntities.map { it.playlistId }.toSet()

		playlistEntities.chunked(dbChunkSize).forEach { chunk ->
			playlistDao.insertPlaylists(chunk)
		}

		playlistDao.deleteObsoletePlaylists(validPlaylistIds)

		Logger.i("DbRepository", "- Playlists Synced: ${playlistEntities.size} playlists found")

		playlistEntities
	}

	suspend fun syncPlaylistSongs(playlistId: String): Result<Int> = runDbOp {
		val playlist = try {
			sessionManager.api.getPlaylist(playlistId)
		} catch (e: Exception) {
			if (e is SerializationException) {
				Logger.e(
					"DbRepository",
					"could not deserialize playlist $playlistId; skipping it",
					e
				)
				return@runDbOp 0
			} else {
				throw e
			}
		}
		val songEntities = playlist.songs.map { it.toEntity() }

		playlistDao.deletePlaylistSongCrossRefs(playlistId)

		val playlistStart = Clock.System.now().toEpochMilliseconds()

		if (songEntities.isNotEmpty()) {
			val existingIds = songDao
				.getSongsByIds(songEntities.map { it.songId })
				.mapTo(mutableSetOf()) { it.songId }
			val missingSongs = songEntities.filter { it.songId !in existingIds }
			missingSongs.chunked(dbChunkSize).forEach { chunk ->
				songDao.insertSongs(chunk)
			}

			val crossRefs = songEntities.mapIndexed { index, it ->
				PlaylistSongCrossRef(playlistId = playlistId, songId = it.songId, position = index)
			}

			crossRefs.chunked(5000).forEach { chunk ->
				playlistDao.insertPlaylistSongCrossRefs(chunk)
			}
		}

		Logger.i(
			"DbRepository",
			"playlist $playlistId took ${Clock.System.now().toEpochMilliseconds() - playlistStart}ms for ${songEntities.size} songs"
		)

		Logger.i("DbRepository", "- Playlist [$playlistId] synced: ${songEntities.size} songs")
		songEntities.size
	}

	suspend fun syncGenres(): Result<Unit> = runDbOp {
		val remoteGenres = sessionManager.api.getGenres()
		val entities = remoteGenres.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			genreDao.insertGenres(chunk)
		}
		genreDao.deleteObsoleteGenres(entities.map { it.genreName }.toSet())

		Logger.i("DbRepository", "- Genres Synced: ${entities.size} genres found")
	}

	suspend fun syncArtists(): Result<Unit> = runDbOp {
		val remoteArtistsWrapper = sessionManager.api.getArtists()
		val flatArtists = remoteArtistsWrapper.flatMap { indexGroup ->
			indexGroup.artists
		}
		val entities = flatArtists.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			artistDao.insertArtists(chunk)
		}
		artistDao.deleteObsoleteArtists(entities.map { it.artistId }.toSet())

		Logger.i("DbRepository", "- Artists Synced: ${entities.size} artists found")
	}

	suspend fun syncRadios(): Result<Unit> = runDbOp {
		val remoteRadios = sessionManager.api.getInternetRadioStations()
		val entities = remoteRadios.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			radioDao.insertRadios(chunk)
		}
		radioDao.deleteObsoleteRadios(entities.map { it.radioId }.toSet())

		Logger.i("DbRepository", "- Radios Synced: ${entities.size} stations found")
	}

	suspend fun fetchArtistMetadata(artistId: String): Result<DomainArtist> = runDbOp {
		val artistInfo = sessionManager.api.getArtistInfo(artistId)
		val simIds = artistInfo.similarArtists.map { it.id }

		val currentEntity = artistDao.getArtistById(artistId)
			?: throw Exception("Artist not found in local DB")

		val updatedEntity = currentEntity.copy(
			biography = artistInfo.biography,
			similarArtistIds = simIds,
			lastFmUrl = artistInfo.lastFmUrl
		)

		artistDao.insertArtist(updatedEntity)

		updatedEntity.toDomainModel()
	}
}
