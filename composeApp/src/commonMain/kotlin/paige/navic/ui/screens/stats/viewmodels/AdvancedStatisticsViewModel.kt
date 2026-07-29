package paige.navic.ui.screens.stats.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.GenreDao
import paige.navic.data.database.dao.ScrobbleDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.entities.ScrobbleEntity
import paige.navic.domain.manager.LastFmManager
import paige.navic.ui.screens.stats.insightKey
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock

sealed interface AdvancedStatisticsUiState {
	data object Unauthenticated : AdvancedStatisticsUiState
	data object Loading : AdvancedStatisticsUiState
	data class Success(
		val sections: List<AdvancedStatisticsSection>
	) : AdvancedStatisticsUiState
	data class Error(val exception: Exception) : AdvancedStatisticsUiState
}

data class AdvancedStatisticsSection(
	val title: String,
	val subtitle: String? = null,
	val items: List<AdvancedStatisticItem>
)

data class AdvancedStatisticItem(
	val label: String,
	val value: String,
	val supportingText: String? = null
)

class AdvancedStatisticsViewModel(
	private val lastFmManager: LastFmManager,
	private val scrobbleDao: ScrobbleDao,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val genreDao: GenreDao
) : ViewModel() {

	private val _state = MutableStateFlow<AdvancedStatisticsUiState>(AdvancedStatisticsUiState.Loading)
	val state: StateFlow<AdvancedStatisticsUiState> = _state
	val authorizationUrl = lastFmManager.authorizationUrl

	init {
		viewModelScope.launch {
			lastFmManager.isAuthenticated.collectLatest { isAuthenticated ->
				if (!isAuthenticated) {
					_state.value = AdvancedStatisticsUiState.Unauthenticated
				} else {
					refreshData()
				}
			}
		}
	}

	fun refreshData() {
		viewModelScope.launch {
			_state.value = AdvancedStatisticsUiState.Loading
			try {
				val scrobbles = scrobbleDao.getScrobblesSince(0).first()
				val artists = artistDao.getAllArtistsList()
				val albums = albumDao.getAllAlbumsList()
				val songs = songDao.getAllSongs()
				val genres = genreDao.getGenres()

				val durationsByTrack = songs.associate { song ->
					trackKey(song.title, song.artistName) to song.duration.inWholeSeconds
				}
				val listeningSecondsByDate = scrobbles.mapNotNull { scrobble ->
					durationsByTrack[trackKey(scrobble.trackName, scrobble.artistName)]?.let { duration ->
						scrobbleDate(scrobble) to duration
					}
				}.groupingBy { it.first }
					.fold(0L) { total, entry -> total + entry.second }

				val totalListeningSeconds = listeningSecondsByDate.values.sum()
				val activityDates = scrobbles.map(::scrobbleDate).distinct()
				val activityDays = activityDates.size
				val streaks = calculateStreaks(scrobbles)

				val favoriteGenre = genres.maxByOrNull { it.songCount }
				val favoriteDecade = songs
					.mapNotNull { song -> song.year?.let { (it / 10) * 10 } }
					.groupingBy { it }
					.eachCount()
					.maxByOrNull { it.value }

				val topArtistByPlayCount = songs
					.groupBy { it.artistName }
					.mapValues { (_, artistSongs) -> artistSongs.sumOf { it.playCount } }
					.maxByOrNull { it.value }
					?.takeIf { it.value > 0 }
				val topAlbumByPlayCount = albums
					.maxByOrNull { it.album.playCount }
					?.takeIf { it.album.playCount > 0 }
				val topSongByPlayCount = songs
					.maxByOrNull { it.playCount }
					?.takeIf { it.playCount > 0 }

				val artistScrobbles = scrobbles.groupingBy { it.artistName }.eachCount()
				val albumScrobbles = scrobbles
					.filter { !it.albumName.isNullOrBlank() }
					.groupingBy { it.albumName.orEmpty() to it.artistName }
					.eachCount()
				val songScrobbles = scrobbles
					.groupingBy { it.trackName to it.artistName }
					.eachCount()

				val firstScrobble = scrobbles.minByOrNull { it.timestamp }
				val latestScrobble = scrobbles.maxByOrNull { it.timestamp }
				val oldestSong = songs.filter { it.year != null }.minByOrNull { it.year!! }
				val newestSong = songs.filter { it.year != null }.maxByOrNull { it.year!! }
				val longestListeningDay = listeningSecondsByDate.maxByOrNull { it.value }

				_state.value = AdvancedStatisticsUiState.Success(
					sections = listOf(
						AdvancedStatisticsSection(
							title = "General",
							subtitle = "A broad view of your listening history",
							items = listOf(
								stat("Total listening time", formatDuration(totalListeningSeconds), "The total amount of time you've spent listening"),
								stat("Total listening days", activityDays.toString(), "Days with at least one scrobble"),
								stat("Total scrobbles", scrobbles.size.toString(), "Tracks scrobbled across your history"),
								stat("Total unique songs", scrobbles.map { it.trackName to it.artistName }.distinct().size.toString(), "Distinct tracks in your scrobble history"),
								stat("Total unique artists", scrobbles.map { it.artistName }.distinct().size.toString(), "Distinct artists you've listened to"),
								stat("Total unique albums", scrobbles.mapNotNull { scrobble ->
									scrobble.albumName?.takeIf { it.isNotBlank() }?.let { it to scrobble.artistName }
								}.distinct().size.toString(), "Distinct albums in your scrobble history")
							)
						),
						AdvancedStatisticsSection(
							title = "Activity",
							subtitle = "Patterns across your listening sessions",
							items = listOf(
								stat(
									label = "Average listening per day",
									value = formatDuration(if (activityDays > 0) totalListeningSeconds / activityDays else 0L),
									supportingText = "Across all days with at least one scrobble"
								),
								stat(
									label = "Longest listening day",
									value = longestListeningDay?.let { formatDuration(it.value) } ?: "0",
									supportingText = longestListeningDay?.key ?: "none"
								),
								stat("Longest listening streak", formatDays(streaks.longest), "The most consecutive days you've listened to music"),
								stat("Current listening streak", formatDays(streaks.current), "How many consecutive days you've listened to music")
							)
						),
						AdvancedStatisticsSection(
							title = "Library",
							subtitle = "What stands out in the music you keep around",
							items = listOf(
								stat(
									label = "Favorite genre",
									value = favoriteGenre?.genreName ?: "none",
									supportingText = favoriteGenre?.songCount?.let { "$it songs" }
								),
								stat(
									label = "Favorite decade",
									value = favoriteDecade?.key?.let { "${it}s" } ?: "none",
									supportingText = favoriteDecade?.value?.let { "$it songs" }
								),
								stat(
									label = "Most played artist",
									value = topArtistByPlayCount?.key ?: artistScrobbles.maxByOrNull { it.value }?.key ?: "none",
									supportingText = topArtistByPlayCount?.value?.let { "$it plays" }
										?: artistScrobbles.maxByOrNull { it.value }?.value?.let { "$it scrobbles" }
								),
								stat(
									label = "Most played album",
									value = topAlbumByPlayCount?.album?.name
										?: albumScrobbles.maxByOrNull { it.value }?.key?.first
										?: "none",
									supportingText = topAlbumByPlayCount?.album?.let { "${it.artistName} • ${it.playCount} plays" }
										?: albumScrobbles.maxByOrNull { it.value }?.let { "${it.key.second} • ${it.value} scrobbles" }
								),
								stat(
									label = "Most played song",
									value = topSongByPlayCount?.title
										?: songScrobbles.maxByOrNull { it.value }?.key?.first
										?: "none",
									supportingText = topSongByPlayCount?.let { "${it.artistName} • ${it.playCount} plays" }
										?: songScrobbles.maxByOrNull { it.value }?.let { "${it.key.second} • ${it.value} scrobbles" }
								)
							)
						),
						AdvancedStatisticsSection(
							title = "History",
							subtitle = "The oldest and newest points in your timeline",
							items = listOf(
								stat(
									label = "First scrobble",
									value = firstScrobble?.trackName ?: "none",
									supportingText = firstScrobble?.let { "${it.artistName} • ${formatScrobbleTimestamp(it.timestamp)}" }
								),
								stat(
									label = "Most recent scrobble",
									value = latestScrobble?.trackName ?: "none",
									supportingText = latestScrobble?.let { "${it.artistName} • ${formatScrobbleTimestamp(it.timestamp)}" }
								),
								stat(
									label = "Oldest song in library",
									value = oldestSong?.title ?: "none",
									supportingText = oldestSong?.let { "${it.artistName} • ${it.year}" }
								),
								stat(
									label = "Newest song in library",
									value = newestSong?.title ?: "none",
									supportingText = newestSong?.let { "${it.artistName} • ${it.year}" }
								)
							)
						)
					)
				)
			} catch (exception: Exception) {
				_state.value = AdvancedStatisticsUiState.Error(exception)
			}
		}
	}
}

private data class ListeningStreaks(
	val current: Int,
	val longest: Int
)

private fun stat(
	label: String,
	value: String,
	supportingText: String? = null
) = AdvancedStatisticItem(
	label = label,
	value = value,
	supportingText = supportingText
)

private fun trackKey(trackName: String, artistName: String): String =
	"${insightKey(trackName)}|||${insightKey(artistName)}"

private fun scrobbleDate(scrobble: ScrobbleEntity): String =
	Instant.fromEpochSeconds(scrobble.timestamp)
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.date
		.toString()

private fun formatScrobbleTimestamp(timestamp: Long): String =
	Instant.fromEpochSeconds(timestamp)
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.date
		.toString()

private fun calculateStreaks(scrobbles: List<ScrobbleEntity>): ListeningStreaks {
	val activeDays = scrobbles.map { it.timestamp / 86_400 }.toSet()
	if (activeDays.isEmpty()) {
		return ListeningStreaks(current = 0, longest = 0)
	}

	val sortedDays = activeDays.sorted()
	var longest = 1
	var currentRun = 1
	for (index in 1 until sortedDays.size) {
		if (sortedDays[index] == sortedDays[index - 1] + 1) {
			currentRun++
			if (currentRun > longest) {
				longest = currentRun
			}
		} else {
			currentRun = 1
		}
	}

	val today = Clock.System.now().toEpochMilliseconds() / 1000 / 86_400
	var current = 0
	var day = if (today in activeDays) today else today - 1
	while (day in activeDays) {
		current++
		day--
	}

	return ListeningStreaks(current = current, longest = longest)
}

private fun formatDuration(totalSeconds: Long): String {
	if (totalSeconds <= 0) return "0m"
	val duration = totalSeconds.seconds
	val days = duration.inWholeDays
	val hours = duration.inWholeHours % 24
	val minutes = duration.inWholeMinutes % 60
	return buildString {
		if (days > 0) append("${days}d ")
		if (hours > 0 || days > 0) append("${hours}h ")
		append("${minutes}m")
	}.trim()
}

private fun formatDays(days: Int): String = if (days <= 0) "0 days" else "$days days"
