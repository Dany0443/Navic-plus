package dan.sonora.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import dan.sonora.data.database.dao.DownloadDao
import dan.sonora.data.database.dao.PlaylistDao
import dan.sonora.data.database.entities.DownloadStatus
import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.domain.models.DomainPlaylist
import dan.sonora.domain.models.DomainPlaylistListType
import dan.sonora.ui.core.UiState

class PlaylistRepository(
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val downloadDao: DownloadDao
) {
	private suspend fun getLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean
	): ImmutableList<DomainPlaylist> {
		val sorted = when (listType) {
			DomainPlaylistListType.Name -> playlistDao.getAllPlaylistsByName()
			DomainPlaylistListType.DateAdded -> playlistDao.getAllPlaylistsByDateAdded()
			DomainPlaylistListType.Duration -> playlistDao.getAllPlaylistsByDuration()
			DomainPlaylistListType.Random -> playlistDao.getAllPlaylistsRandom()
			DomainPlaylistListType.Downloaded -> {
				playlistDao.getAllPlaylistsByDateAdded().filter { (_, songs) ->
					downloadDao.getAllDownloadsList()
						.filter { it.status == DownloadStatus.DOWNLOADED }
						.map { it.songId }
						.containsAll(songs.map { it.song.songId })
				}
			}
		}.map { it.toDomainModel() }.toImmutableList()
		return if (reversed) {
			sorted.reversed().toImmutableList()
		} else {
			sorted
		}
	}

	private suspend fun refreshLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean
	): ImmutableList<DomainPlaylist> {
		dbRepository.syncPlaylists().getOrThrow().forEach { playlist ->
			dbRepository.syncPlaylistSongs(playlist.playlistId).getOrThrow()
		}
		return getLocalData(listType, reversed)
	}

	fun getPlaylistsFlow(
		fullRefresh: Boolean,
		listType: DomainPlaylistListType,
		reversed: Boolean
	): Flow<UiState<ImmutableList<DomainPlaylist>>> = flow {
		val localData = getLocalData(listType, reversed)
		val missingPlaylistMetadata = localData.isEmpty() || localData.any { playlist ->
			playlist.songCount > 0 && playlist.songs.isEmpty()
		}
		if (fullRefresh || missingPlaylistMetadata) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)
}
