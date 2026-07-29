package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.SyncActionType
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.ui.core.UiState
import paige.navic.util.core.toSqlQuery
import kotlin.time.Clock

class AlbumRepository(
	private val albumDao: AlbumDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository
) {
	private fun sortByAlbumArtist(
		albums: List<DomainAlbum>,
		reversed: Boolean
	): List<DomainAlbum> {
		return albums.sortedWith { first, second ->
			val firstArtist = first.artistName.trim()
			val secondArtist = second.artistName.trim()
			val firstBlank = firstArtist.isBlank()
			val secondBlank = secondArtist.isBlank()

			if (firstBlank != secondBlank) {
				return@sortedWith if (firstBlank) 1 else -1
			}

			val artistComparison = if (reversed) {
				secondArtist.compareTo(firstArtist, ignoreCase = true)
			} else {
				firstArtist.compareTo(secondArtist, ignoreCase = true)
			}
			if (artistComparison != 0) return@sortedWith artistComparison

			first.name.compareTo(second.name, ignoreCase = true)
		}
	}

	private suspend fun getLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean
	): ImmutableList<DomainAlbum> {
		val downloadedSongIds = if (listType == DomainAlbumListType.Downloaded) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.map { it.songId }
				.toSet()
		} else null

		val albums = albumDao
			.getAlbumsByQuery(listType.toSqlQuery())
			.map { it.toDomainModel() }

		val sortedAlbums = when (listType) {
			DomainAlbumListType.AlphabeticalByAlbumArtist -> sortByAlbumArtist(albums, reversed)
			else -> if (reversed) albums.asReversed() else albums
		}

		return sortedAlbums
			.filter { album -> downloadedSongIds == null || downloadedSongIds.containsAll(album.songs.map { it.id }) }
			.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean
	): ImmutableList<DomainAlbum> {
		dbRepository.syncLibrarySongs().getOrThrow()
		return getLocalData(listType, reversed)
	}

	fun getAlbumsFlow(
		fullRefresh: Boolean,
		listType: DomainAlbumListType,
		reversed: Boolean
	): Flow<UiState<ImmutableList<DomainAlbum>>> = flow {
		val localData = getLocalData(listType, reversed)
		if (fullRefresh) {
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

	suspend fun isAlbumStarred(album: DomainAlbum) = albumDao.isAlbumStarred(album.id)
	suspend fun getAlbumRating(album: DomainAlbum) = albumDao.getAlbumRating(album.id) ?: 0

	suspend fun starAlbum(album: DomainAlbum) {
		val starredEntity = album.toEntity().copy(
			starredAt = Clock.System.now()
		)
		albumDao.insertAlbum(starredEntity)
		syncManager.enqueueAction(SyncActionType.STAR, album.id)
	}

	suspend fun unstarAlbum(album: DomainAlbum) {
		val unstarredEntity = album.toEntity().copy(
			starredAt = null
		)
		albumDao.insertAlbum(unstarredEntity)
		syncManager.enqueueAction(SyncActionType.UNSTAR, album.id)
	}

	suspend fun rateAlbum(album: DomainAlbum, rating: Int) {
		val ratedEntity = album.toEntity().copy(
			userRating = rating
		)
		albumDao.insertAlbum(ratedEntity)
		when (rating) {
			0 -> syncManager.enqueueAction(SyncActionType.STAR_0, album.id)
			1 -> syncManager.enqueueAction(SyncActionType.STAR_1, album.id)
			2 -> syncManager.enqueueAction(SyncActionType.STAR_2, album.id)
			3 -> syncManager.enqueueAction(SyncActionType.STAR_3, album.id)
			4 -> syncManager.enqueueAction(SyncActionType.STAR_4, album.id)
			5 -> syncManager.enqueueAction(SyncActionType.STAR_5, album.id)
		}
	}
}
