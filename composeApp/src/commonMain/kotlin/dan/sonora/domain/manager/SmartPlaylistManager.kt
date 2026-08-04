package dan.sonora.domain.manager

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dan.sonora.data.database.dao.ScrobbleDao
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.domain.models.DomainSong
import dan.sonora.domain.models.DomainSongListType
import dan.sonora.domain.models.SmartPlaylist
import dan.sonora.domain.models.SmartPlaylistType
import dan.sonora.domain.repositories.SongRepository
import dan.sonora.ui.core.UiState
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class SmartPlaylistManager(
    private val songRepository: SongRepository,
	private val scrobbleDao: ScrobbleDao,
    private val rules: List<SmartPlaylistRule> = defaultRules
) {
    val playlistRules: List<SmartPlaylistRule>
        get() = rules

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val cacheMutex = Mutex()
	private val cachedSnapshot = kotlinx.coroutines.flow.MutableStateFlow<SmartPlaylistSnapshot?>(null)

	/**
	 * Hot, cached Smart Playlists.
	 *
	 * The expensive work (loading all songs + scrobbles, then running the rules) is only done
	 * when the listening-statistics signature changes. Repeated Library visits and re-compositions
	 * reuse the cached snapshot instead of rebuilding everything from scratch.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	private val smartPlaylists: StateFlow<UiState<List<SmartPlaylist>>> =
		scrobbleDao.getTotalScrobbleCount()
			.distinctUntilChanged()
			.mapLatest { totalCount ->
				val signature = loadSignature(totalCount)
				val snapshot = getSnapshot(signature)
				UiState.Success(snapshot.playlists) as UiState<List<SmartPlaylist>>
			}
			.flowOn(Dispatchers.IO)
			.stateIn(scope, SharingStarted.Lazily, UiState.Loading())

	fun getSmartPlaylistsFlow(fullRefresh: Boolean = false): Flow<UiState<List<SmartPlaylist>>> =
		smartPlaylists

	/**
	 * Produces the actual curated songs for a Smart Playlist. This is used by the Song list screen
	 * so the UI remains free of Smart Playlist generation logic.
	 */
	fun getSmartPlaylistSongsFlow(
		fullRefresh: Boolean,
		listType: DomainSongListType
	): Flow<UiState<ImmutableList<DomainSong>>> = flow {
		emit(UiState.Loading())
		val signature = loadSignature()
		val snapshot = getSnapshot(signature, forceRefresh = fullRefresh)
		val selected = snapshot.songsByType[listType] ?: persistentListOf()
		emit(UiState.Success(selected))
	}.flowOn(Dispatchers.IO)

	private suspend fun loadContext(): SmartPlaylistContext {
		val now = Clock.System.now()
		// One scrobble query: the 14-day window is a subset of the 365-day window.
		val scrobbles365d = getScrobblesSince(now - 365.days)
		val cutoff14d = (now - 14.days).epochSeconds
		return SmartPlaylistContext(
			allSongs = songRepository.getAllSongs(),
			recentScrobbles14d = scrobbles365d.filter { it.timestamp >= cutoff14d },
			recentScrobbles365d = scrobbles365d
		)
	}

	private suspend fun loadSignature(totalCount: Int? = null): SmartPlaylistSignature {
		val latestScrobbleTimestamp = scrobbleDao.getLatestScrobbleTimestamp() ?: 0L
		val oldestScrobbleTimestamp = scrobbleDao.getOldestScrobbleTimestamp() ?: 0L
		return SmartPlaylistSignature(
			totalCount = totalCount ?: scrobbleDao.getTotalScrobbleCount().first(),
			latestScrobbleTimestamp = latestScrobbleTimestamp,
			oldestScrobbleTimestamp = oldestScrobbleTimestamp
		)
	}

	private suspend fun getSnapshot(
		signature: SmartPlaylistSignature,
		forceRefresh: Boolean = false
	): SmartPlaylistSnapshot = cacheMutex.withLock {
		val current = cachedSnapshot.value
		if (!forceRefresh && current != null && current.signature == signature) {
			return current
		}

		val context = loadContext()
		val rules = buildRules(context)
		val playlists = rules.map { rule -> rule.execute(context.allSongs) }.toImmutableList()
		val songsByType = linkedMapOf<DomainSongListType, ImmutableList<DomainSong>>()
		rules.forEach { rule ->
			val songs = rule.select?.invoke(context.allSongs) ?: emptyList()
			songsByType[rule.listType] = songs.toImmutableList()
		}

		val snapshot = SmartPlaylistSnapshot(
			signature = signature,
			context = context,
			playlists = playlists,
			songsByType = songsByType
		)
		cachedSnapshot.value = snapshot
		return snapshot
	}

    companion object {
		// Default rules are only used for metadata/structure; selection logic is driven by
		// SmartPlaylistContext (play counts + recent scrobble activity).
		private val defaultRules = listOf(
            SmartPlaylistRule(
                id = SmartPlaylistType.MostPlayed.id,
                type = SmartPlaylistType.MostPlayed,
                title = "Most played",
                icon = "most_played",
				listType = DomainSongListType.SmartMostPlayed,
				filter = { it }
            ),
            SmartPlaylistRule(
                id = SmartPlaylistType.OnRepeat.id,
                type = SmartPlaylistType.OnRepeat,
                title = "On repeat",
                icon = "on_repeat",
				listType = DomainSongListType.SmartOnRepeat,
				filter = { it }
            ),
            SmartPlaylistRule(
                id = SmartPlaylistType.NeverPlayed.id,
                type = SmartPlaylistType.NeverPlayed,
                title = "Discover",
                icon = "never_played",
				listType = DomainSongListType.SmartNeverPlayed,
				filter = { it }
            )
        )
    }

	private data class TrackKey(val title: String, val artist: String)

	private data class SmartPlaylistSignature(
		val totalCount: Int,
		val latestScrobbleTimestamp: Long,
		val oldestScrobbleTimestamp: Long
	)

	private data class SmartPlaylistSnapshot(
		val signature: SmartPlaylistSignature,
		val context: SmartPlaylistContext,
		val playlists: ImmutableList<SmartPlaylist>,
		val songsByType: Map<DomainSongListType, ImmutableList<DomainSong>>
	)

	private data class SmartPlaylistContext(
		val allSongs: List<DomainSong>,
		/** Scrobbles in the last 14 days (used for "On Repeat"). */
		val recentScrobbles14d: List<ScrobbleEntity>,
		/** Recent-ish scrobbles (used for tie-breaking "Most Played"). */
		val recentScrobbles365d: List<ScrobbleEntity>
	)

	private fun buildRules(context: SmartPlaylistContext): List<SmartPlaylistRule> {
		// Build maps once so all rules use consistent data.
		val lastPlayedByKey = context.recentScrobbles365d
			.groupBy { it.trackKey() }
			.mapValues { (_, entries) -> entries.maxOf { it.timestamp } }

		val onRepeatEntries = context.recentScrobbles14d
		val onRepeatCountByKey = onRepeatEntries
			.groupBy { it.trackKey() }
			.mapValues { (_, entries) -> entries.size }
		val onRepeatLastPlayedByKey = onRepeatEntries
			.groupBy { it.trackKey() }
			.mapValues { (_, entries) -> entries.maxOf { it.timestamp } }

		// Rebuild the three default rules with custom selectors.
		return listOf(
			SmartPlaylistRule(
				id = SmartPlaylistType.MostPlayed.id,
				type = SmartPlaylistType.MostPlayed,
				title = "Most played",
				icon = "most_played",
				listType = DomainSongListType.SmartMostPlayed,
				select = { songs ->
					songs
						.sortedWith(
							compareByDescending<DomainSong> { it.playCount }
								.thenByDescending { song ->
									lastPlayedByKey[song.trackKey()] ?: 0L
								}
								.thenBy { it.title.lowercase() }
						)
						.take(30)
				},
				filter = { it }
			),
			SmartPlaylistRule(
				id = SmartPlaylistType.OnRepeat.id,
				type = SmartPlaylistType.OnRepeat,
				title = "On repeat",
				icon = "on_repeat",
				listType = DomainSongListType.SmartOnRepeat,
				select = { songs ->
					// Use ONLY activity from last 14 days.
					// Prefer repeated plays, but always try to return 30 items by filling with
					// one-off plays, then (if needed) random unseen songs.
					val scored = songs.map { song ->
						val key = song.trackKey()
						val count = onRepeatCountByKey[key] ?: 0
						val last = onRepeatLastPlayedByKey[key] ?: 0L
						Triple(song, count, last)
					}

					val repeated = scored
						.filter { (_, count, _) -> count >= 2 }
						.sortedWith(
							compareByDescending<Triple<DomainSong, Int, Long>> { it.second }
								.thenByDescending { it.third }
								.thenBy { it.first.title.lowercase() }
						)
						.map { it.first }

					val single = scored
						.filter { (_, count, _) -> count == 1 }
						.sortedByDescending { it.third }
						.map { it.first }

					val selected = (repeated + single)
						.distinctBy { it.id }
						.take(30)
						.toMutableList()

					if (selected.size < 30) {
						val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
						val filler = songs
							.filterNot { s -> s.id in selectedIds }
							.shuffled(Random(Clock.System.now().epochSeconds.toInt()))
							.take(30 - selected.size)
							.toList()
						selected += filler
					}

					selected
				},
				filter = { it }
			),
			SmartPlaylistRule(
				id = SmartPlaylistType.NeverPlayed.id,
				type = SmartPlaylistType.NeverPlayed,
				title = "Never played",
				icon = "never_played",
				listType = DomainSongListType.SmartNeverPlayed,
				select = { songs ->
					// Recommendation-style: pick from songs with the lowest play counts.
					// Shuffle on every regeneration.
					val seed = Clock.System.now().epochSeconds.toInt()
					val random = Random(seed)

					// Build a reasonably sized candidate pool to make the playlist feel varied.
					val sortedByLeastPlayed = songs.sortedWith(
						compareBy<DomainSong> { it.playCount }
							.thenBy { it.title.lowercase() }
					)

					var threshold = 0
					var candidates = sortedByLeastPlayed.filter { it.playCount <= threshold }
					while (candidates.size < 60 && threshold < 5) {
						threshold += 1
						candidates = sortedByLeastPlayed.filter { it.playCount <= threshold }
					}

					// If the library is tiny or everything is heavily played, just fall back to
					// the least-played slice.
					if (candidates.size < 30) {
						candidates = sortedByLeastPlayed.take(60)
					}

					candidates.shuffled(random).take(30)
				},
				filter = { it }
			)
		)
	}

	private suspend fun getScrobblesSince(since: kotlin.time.Instant): List<ScrobbleEntity> {
		// Room stores timestamps in seconds; kotlin.time.Instant is high-res.
		val sinceEpochSeconds = since.epochSeconds
		return scrobbleDao.getScrobblesSince(sinceEpochSeconds).first()
	}

	private fun DomainSong.trackKey(): TrackKey =
		TrackKey(
			title = normalizeKey(title),
			artist = normalizeKey(artistName)
		)

	private fun ScrobbleEntity.trackKey(): TrackKey =
		TrackKey(
			title = normalizeKey(trackName),
			artist = normalizeKey(artistName)
		)

	private fun normalizeKey(value: String): String =
		value
			.lowercase()
			.replace(Regex("[^a-z0-9 ]"), " ")
			.replace(Regex("\\s+"), " ")
			.trim()

}
