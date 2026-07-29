package paige.navic.ui.screens.stats.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ScrobbleDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entities.ScrobbleEntity
import paige.navic.domain.manager.LastFmManager
import paige.navic.domain.manager.LastFmSyncManager
import paige.navic.domain.manager.LastFmUserInfo
import paige.navic.domain.manager.LastFmArtist
import paige.navic.domain.manager.LastFmTrack
import paige.navic.ui.screens.stats.insightKey
import paige.navic.ui.screens.stats.insightUrlKey
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed interface InsightsUiState {
	data object Unauthenticated : InsightsUiState
	data object Loading : InsightsUiState
	data class InsightAlbum(val name: String, val artist: String, val plays: Int)
	data class RotationArtist(val name: String, val plays: Int)
	data class RotationTrack(val name: String, val artist: String, val plays: Int)
	data class SongMatch(val songId: String, val albumName: String?)
	data class SongMatchDiagnostic(val count: Int, val songs: List<SongMatch>)
	data class Success(
		val userInfo: LastFmUserInfo?,
		val topArtists: List<LastFmArtist>,
		val topTracks: List<LastFmTrack>,
		val recentScrobbles: List<ScrobbleEntity>,
		val totalLocalScrobbles: Int,
		val streakDays: Int,
		val scrobblesToday: Int,
		val artistCoverArtIds: Map<String, String> = emptyMap(),
		val artistIds: Map<String, String> = emptyMap(),
		val albumCoverArtIds: Map<String, String> = emptyMap(),
		val albumIds: Map<String, String> = emptyMap(),
		val trackCoverArtIds: Map<String, String> = emptyMap(),
		val trackIds: Map<String, String> = emptyMap(),
		val recentActivityTrackCoverArtIds: Map<String, String> = emptyMap(),
		val recentActivitySongMatchDiagnostics: Map<String, SongMatchDiagnostic> = emptyMap(),
		val recentActivityArtworkReady: Boolean = false,
		val activityByDate: Map<String, Int> = emptyMap(),
		val listeningSecondsByDate: Map<String, Long> = emptyMap(),
		val topAlbums: List<InsightAlbum> = emptyList(),
		val rotationArtists: List<RotationArtist> = emptyList(),
		val rotationTracks: List<RotationTrack> = emptyList()
	) : InsightsUiState
	data class Error(val exception: Exception) : InsightsUiState
}

private data class LastFmData(
	val isAuthenticated: Boolean,
	val userInfo: LastFmUserInfo?,
	val topArtists: List<LastFmArtist>,
	val topTracks: List<LastFmTrack>
)

private data class LocalData(
	val recentScrobbles: List<ScrobbleEntity>,
	val totalScrobbles: Int,
	val error: Exception?
)

private data class LibraryArt(
	val artistCoverArtIds: Map<String, String>,
	val artistIds: Map<String, String>,
	val albumCoverArtIds: Map<String, String>,
	val albumIds: Map<String, String>,
	val trackCoverArtIds: Map<String, String>,
	val trackIds: Map<String, String>,
	val trackDurations: Map<String, Long>,
	val recentActivityArtwork: RecentActivityArtwork
)

private data class RecentActivityArtwork(
	val coverArtIds: Map<String, String> = emptyMap(),
	val songMatchDiagnostics: Map<String, InsightsUiState.SongMatchDiagnostic> = emptyMap(),
	val isReady: Boolean = false
)

class InsightsViewModel(
	private val lastFmManager: LastFmManager,
	private val syncManager: LastFmSyncManager,
	private val scrobbleDao: ScrobbleDao,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao
) : ViewModel() {

	val isSyncing = syncManager.isSyncing
	val syncProgress = syncManager.syncProgress
	val authorizationUrl = lastFmManager.authorizationUrl

	private val _error = MutableStateFlow<Exception?>(null)

	private val _userInfo = MutableStateFlow<LastFmUserInfo?>(null)
	private val _topArtists = MutableStateFlow<List<LastFmArtist>>(emptyList())
	private val _topTracks = MutableStateFlow<List<LastFmTrack>>(emptyList())

	private val _artistCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _albumCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _artistIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _albumIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
	private val _recentActivityArtwork = MutableStateFlow(RecentActivityArtwork())

	private val lastFmData = combine(
		lastFmManager.isAuthenticated, _userInfo, _topArtists, _topTracks
	) { a, b, c, d -> LastFmData(a, b, c, d) }

	private val localData = combine(
		scrobbleDao.getRecentScrobbles(5000), scrobbleDao.getTotalScrobbleCount(), _error
	) { a, b, c -> LocalData(a, b, c) }
	private val artistArt = combine(_artistCoverArtIds, _artistIds) { art, ids -> art to ids }
	private val albumArt = combine(_albumCoverArtIds, _albumIds) { art, ids -> art to ids }
	private val trackArt = combine(_trackCoverArtIds, _trackIds, _trackDurations) { art, ids, durations -> Triple(art, ids, durations) }
	private val libraryArt = combine(artistArt, albumArt, trackArt, _recentActivityArtwork) { artists, albums, tracks, recentActivityArtwork ->
		LibraryArt(artists.first, artists.second, albums.first, albums.second, tracks.first, tracks.second, tracks.third, recentActivityArtwork)
	}

	val state: StateFlow<InsightsUiState> = combine(
		lastFmData, localData, libraryArt
	) { lastFm, local, library ->
		if (!lastFm.isAuthenticated) return@combine InsightsUiState.Unauthenticated
		if (local.error != null) return@combine InsightsUiState.Error(local.error)
		
		val now = Clock.System.now().toEpochMilliseconds() / 1000
		val startOfDay = now - (now % 86400)
		
		val scrobblesToday = local.recentScrobbles.count { it.timestamp >= startOfDay }
		val activityByDate = local.recentScrobbles
			.groupingBy { Instant.fromEpochSeconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }
			.eachCount()
		val listeningSecondsByDate = local.recentScrobbles.mapNotNull { scrobble ->
			library.trackDurations[insightKey(scrobble.trackName) + "|||" + insightKey(scrobble.artistName)]
				?.let { duration ->
					val date = Instant.fromEpochSeconds(scrobble.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
					date to duration
				}
		}.groupingBy { it.first }.fold(0L) { total, entry -> total + entry.second }
		val topAlbums = local.recentScrobbles.filter { !it.albumName.isNullOrBlank() }
			.groupingBy { it.albumName.orEmpty() to it.artistName }
			.eachCount().entries.sortedByDescending { it.value }.take(10)
			.map { InsightsUiState.InsightAlbum(it.key.first, it.key.second, it.value) }

		// What the user is listening to right now — last 14 days only, independent of lifetime counts
		val rotationCutoff = now - 14 * 86400
		val rotationScrobbles = local.recentScrobbles.filter { it.timestamp >= rotationCutoff }
		val rotationArtists = rotationScrobbles.groupingBy { it.artistName }
			.eachCount().entries.sortedByDescending { it.value }.take(5)
			.map { InsightsUiState.RotationArtist(it.key, it.value) }
		val rotationTracks = rotationScrobbles.groupingBy { it.trackName to it.artistName }
			.eachCount().entries.sortedByDescending { it.value }.take(10)
			.map { InsightsUiState.RotationTrack(it.key.first, it.key.second, it.value) }
		
		val activeDays = local.recentScrobbles.map { it.timestamp / 86400 }.toSet()
		var streak = 0
		var day = startOfDay / 86400
		if (day !in activeDays) day--
		while (day in activeDays && streak < 365) {
			streak++
			day--
		}

		InsightsUiState.Success(
			userInfo = lastFm.userInfo,
			topArtists = lastFm.topArtists,
			topTracks = lastFm.topTracks,
			recentScrobbles = local.recentScrobbles,
			totalLocalScrobbles = local.totalScrobbles,
			streakDays = streak,
			scrobblesToday = scrobblesToday,
			artistCoverArtIds = library.artistCoverArtIds,
			artistIds = library.artistIds,
			albumCoverArtIds = library.albumCoverArtIds,
			albumIds = library.albumIds,
			trackCoverArtIds = library.trackCoverArtIds,
			trackIds = library.trackIds,
			recentActivityTrackCoverArtIds = library.recentActivityArtwork.coverArtIds,
			recentActivitySongMatchDiagnostics = library.recentActivityArtwork.songMatchDiagnostics,
			recentActivityArtworkReady = library.recentActivityArtwork.isReady,
			activityByDate = activityByDate,
			listeningSecondsByDate = listeningSecondsByDate,
			topAlbums = topAlbums,
			rotationArtists = rotationArtists,
			rotationTracks = rotationTracks
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState.Loading)

	init {
		viewModelScope.launch {
			lastFmManager.isAuthenticated.collect { isAuthenticated ->
				if (isAuthenticated) {
					refreshData()
				}
			}
		}
	}

	fun refreshData() {
		viewModelScope.launch {
			try {
				_error.value = null
				_userInfo.value = lastFmManager.getUserInfo()
				val topArtists = lastFmManager.getTopArtists()
				_topArtists.value = topArtists
				_topTracks.value = lastFmManager.getTopTracks()

				// Look up cover art IDs for top artists from local DB
				val allArtists = artistDao.getAllArtistsList()
				val artistsByName = allArtists.associateBy { insightKey(it.name) }
				val artistsByUrl = allArtists.mapNotNull { artist ->
					artist.lastFmUrl?.let { insightUrlKey(it) to artist }
				}.toMap()
				val artistsByMbid = allArtists.mapNotNull { artist ->
					artist.musicBrainzId?.takeIf { it.isNotBlank() }?.let { it.lowercase() to artist }
				}.toMap()
				val matchedArtists = topArtists.mapNotNull { lfmArtist ->
					val localArtist = lfmArtist.mbid?.lowercase()?.let { artistsByMbid[it] }
						?: artistsByUrl[insightUrlKey(lfmArtist.url)]
						?: artistsByName[insightKey(lfmArtist.name)]
					localArtist?.let { lfmArtist to it }
				}
				_artistCoverArtIds.value = matchedArtists.mapNotNull { (lfmArtist, localArtist) ->
					localArtist.coverArtId?.takeIf { it.isNotBlank() }?.let { lfmArtist.name to it }
				}.toMap()
				_artistIds.value = allArtists.associate { insightKey(it.name) to it.artistId } +
					matchedArtists.associate { (lfmArtist, localArtist) -> insightKey(lfmArtist.name) to localArtist.artistId }

				// Look up cover art IDs for albums from local DB
				val allAlbums = albumDao.getAllAlbumsList()
				val albumKeyToArt = allAlbums.associate { insightKey(it.album.name) + "|||" + insightKey(it.album.artistName) to it.album.coverArtId }
				_albumCoverArtIds.value = albumKeyToArt
				_albumIds.value = allAlbums.associate { insightKey(it.album.name) + "|||" + insightKey(it.album.artistName) to it.album.albumId }

				val allSongs = allAlbums.flatMap { it.songs }
				_trackCoverArtIds.value = allSongs.associate { insightKey(it.title) to it.coverArtId.orEmpty() }
				_trackIds.value = allSongs.associate { insightKey(it.title) to it.songId }
				_trackDurations.value = allSongs.associate { song ->
					insightKey(song.title) + "|||" + insightKey(song.artistName) to song.duration.inWholeSeconds
				}
				val songsByTrackKey = songDao.getAllSongs()
					.groupBy { song -> insightKey(song.title) + "|||" + insightKey(song.artistName) }
				_recentActivityArtwork.value = RecentActivityArtwork(
					coverArtIds = songsByTrackKey.mapNotNull { (key, songs) ->
						songs.singleOrNull()?.coverArtId?.takeIf { it.isNotBlank() }?.let { key to it }
					}.toMap(),
					songMatchDiagnostics = songsByTrackKey.mapValues { (_, songs) ->
						InsightsUiState.SongMatchDiagnostic(
							count = songs.size,
							songs = songs.map { song ->
								InsightsUiState.SongMatch(song.songId, song.albumTitle)
							}
						)
					},
					isReady = true
				)
			} catch (e: Exception) {
				_error.value = e
			}
		}
	}

	fun triggerSync() {
		syncManager.syncRecentScrobbles()
	}
}
