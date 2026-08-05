package dan.sonora.ui.screens.stats.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dan.sonora.data.database.dao.AlbumDao
import dan.sonora.data.database.dao.ArtistDao
import dan.sonora.data.database.dao.SongDao
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.domain.stats.ActiveProvider
import dan.sonora.domain.stats.InsightsRepository
import dan.sonora.domain.stats.ProviderArtist
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.domain.stats.ProviderUserInfo
import dan.sonora.domain.stats.providerOrNull
import dan.sonora.ui.screens.stats.insightKey
import dan.sonora.ui.screens.stats.insightUrlKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed interface InsightsUiState {
	/** No provider is connected — the screen shows onboarding. */
	data object NoProvider : InsightsUiState
	data object Loading : InsightsUiState
	data class InsightAlbum(val name: String, val artist: String, val plays: Int)
	data class RotationArtist(val name: String, val plays: Int)
	data class RotationTrack(val name: String, val artist: String, val plays: Int)
	data class SongMatch(val songId: String, val albumName: String?)
	data class SongMatchDiagnostic(val count: Int, val songs: List<SongMatch>)
	data class Success(
		val providerName: String,
		val userInfo: ProviderUserInfo?,
		val topArtists: List<ProviderArtist>,
		val topTracks: List<ProviderTrack>,
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

private data class ProviderData(
	val providerName: String,
	val hasProvider: Boolean,
	val userInfo: ProviderUserInfo?,
	val topArtists: List<ProviderArtist>,
	val topTracks: List<ProviderTrack>
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

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
	private val insightsRepository: InsightsRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao
) : ViewModel() {

	val isSyncing = insightsRepository.isSyncing
	val syncProgress = insightsRepository.syncProgress

	/** Every registered provider, for the onboarding buttons. */
	val providers = insightsRepository.providers

	private val _error = MutableStateFlow<Exception?>(null)

	private val _userInfo = MutableStateFlow<ProviderUserInfo?>(null)
	private val _topArtists = MutableStateFlow<List<ProviderArtist>>(emptyList())
	private val _topTracks = MutableStateFlow<List<ProviderTrack>>(emptyList())

	private val _artistCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _albumCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _artistIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _albumIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackCoverArtIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackIds = MutableStateFlow<Map<String, String>>(emptyMap())
	private val _trackDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
	private val _recentActivityArtwork = MutableStateFlow(RecentActivityArtwork())

	// Re-emits when the active provider changes so the UI follows a provider switch.
	private val providerData = combine(
		insightsRepository.activeProvider, _userInfo, _topArtists, _topTracks
	) { active, userInfo, artists, tracks ->
		ProviderData(
			providerName = active.providerOrNull?.displayName.orEmpty(),
			hasProvider = active is ActiveProvider.Connected,
			userInfo = userInfo,
			topArtists = artists,
			topTracks = tracks
		)
	}

	private val localData = combine(
		insightsRepository.observeScrobbles(5000),
		insightsRepository.observeScrobbleCount(),
		_error
	) { a, b, c -> LocalData(a, b, c) }
	private val artistArt = combine(_artistCoverArtIds, _artistIds) { art, ids -> art to ids }
	private val albumArt = combine(_albumCoverArtIds, _albumIds) { art, ids -> art to ids }
	private val trackArt = combine(_trackCoverArtIds, _trackIds, _trackDurations) { art, ids, durations -> Triple(art, ids, durations) }
	private val libraryArt = combine(artistArt, albumArt, trackArt, _recentActivityArtwork) { artists, albums, tracks, recentActivityArtwork ->
		LibraryArt(artists.first, artists.second, albums.first, albums.second, tracks.first, tracks.second, tracks.third, recentActivityArtwork)
	}

	val state: StateFlow<InsightsUiState> = combine(
		providerData, localData, libraryArt
	) { provider, local, library ->
		if (!provider.hasProvider) return@combine InsightsUiState.NoProvider
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
			providerName = provider.providerName,
			userInfo = provider.userInfo,
			topArtists = provider.topArtists,
			topTracks = provider.topTracks,
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
			// Refetch whenever the active provider changes, including on first connect.
			insightsRepository.activeProvider.collect { active ->
				if (active is ActiveProvider.Connected) {
					refreshData()
				}
			}
		}
	}

	fun refreshData() {
		viewModelScope.launch {
			try {
				_error.value = null
				_userInfo.value = insightsRepository.getUserInfo()
				val topArtists = insightsRepository.getTopArtists()
				_topArtists.value = topArtists
				_topTracks.value = insightsRepository.getTopTracks()

				// Look up cover art IDs for top artists from local DB
				val allArtists = artistDao.getAllArtistsList()
				val artistsByName = allArtists.associateBy { insightKey(it.name) }
				val artistsByUrl = allArtists.mapNotNull { artist ->
					artist.lastFmUrl?.let { insightUrlKey(it) to artist }
				}.toMap()
				val artistsByMbid = allArtists.mapNotNull { artist ->
					artist.musicBrainzId?.takeIf { it.isNotBlank() }?.let { it.lowercase() to artist }
				}.toMap()
				val matchedArtists = topArtists.mapNotNull { providerArtist ->
					val localArtist = providerArtist.mbid?.lowercase()?.let { artistsByMbid[it] }
						?: providerArtist.url?.let { artistsByUrl[insightUrlKey(it)] }
						?: artistsByName[insightKey(providerArtist.name)]
					localArtist?.let { providerArtist to it }
				}
				_artistCoverArtIds.value = matchedArtists.mapNotNull { (providerArtist, localArtist) ->
					localArtist.coverArtId?.takeIf { it.isNotBlank() }?.let { providerArtist.name to it }
				}.toMap()
				_artistIds.value = allArtists.associate { insightKey(it.name) to it.artistId } +
					matchedArtists.associate { (providerArtist, localArtist) -> insightKey(providerArtist.name) to localArtist.artistId }

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
		insightsRepository.syncActiveProvider()
	}

	/**
	 * Connects a username-based provider. Suspends so the dialog can show progress and
	 * report failures, and rethrows so it stays open when the account cannot be verified.
	 */
	suspend fun connectWithUsername(providerId: String, username: String, serverUrl: String) {
		insightsRepository.connectWithUsername(providerId, username, serverUrl)
	}
}
