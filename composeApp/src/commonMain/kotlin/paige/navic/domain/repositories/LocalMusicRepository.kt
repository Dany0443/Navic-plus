package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import paige.navic.domain.localmusic.LocalMusicScanner
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.UiState

class LocalMusicRepository(
	private val scanner: LocalMusicScanner,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val songsState = MutableStateFlow<UiState<ImmutableList<DomainSong>>>(UiState.Loading())
	private var cachedSongs: List<DomainSong>? = null
	private var scanJob: kotlinx.coroutines.Job? = null

	init {
		scope.launch {
			scanner.changes().collect {
				if (preferenceManager.enableLocalMusic) {
					refreshFromScanner(force = true)
				}
			}
		}
	}

	fun getSongsFlow(fullRefresh: Boolean, reversed: Boolean): Flow<UiState<ImmutableList<DomainSong>>> {
		if (!preferenceManager.enableLocalMusic) {
			setEmptyState()
			return songsState.map { mapState(it, reversed) }
		}

		if (fullRefresh || cachedSongs == null) {
			refreshFromScanner(force = fullRefresh || cachedSongs == null)
		}

		return songsState.map { mapState(it, reversed) }
	}

	/** Starts the same scan used by the Local Music library after permission is granted. */
	fun refresh() {
		refreshFromScanner(force = true)
	}

	private fun refreshFromScanner(force: Boolean) {
		if (scanJob?.isActive == true) return
		if (!preferenceManager.enableLocalMusic) {
			setEmptyState()
			return
		}

		scanJob = scope.launch {
			val previousData = cachedSongs?.let(::buildResult)
			if (force || previousData == null) {
				songsState.value = UiState.Loading(data = previousData)
			}

			try {
				val scanned = scanner.scan()
				cachedSongs = scanned
				songsState.value = UiState.Success(buildResult(scanned))
			} catch (error: Exception) {
				songsState.value = UiState.Error(error = error, data = previousData)
			}
		}
	}

	private fun setEmptyState() {
		cachedSongs = emptyList()
		songsState.value = UiState.Success(persistentListOf())
	}

	private fun mapState(state: UiState<ImmutableList<DomainSong>>, reversed: Boolean): UiState<ImmutableList<DomainSong>> {
		return when (state) {
			is UiState.Loading -> UiState.Loading(data = state.data?.let(::applyReversed))
			is UiState.Success -> UiState.Success(applyReversed(state.data))
			is UiState.Error -> UiState.Error(error = state.error, data = state.data?.let(::applyReversed))
		}
	}

	private fun applyReversed(songs: ImmutableList<DomainSong>): ImmutableList<DomainSong> {
		return songs
	}

	private fun buildResult(songs: List<DomainSong>): ImmutableList<DomainSong> {
		val sorted = songs.sortedBy { it.title.lowercase() }
		return sorted.toImmutableList()
	}
}
