package dan.sonora.ui.core

import kotlinx.serialization.Serializable
import dan.sonora.domain.models.DomainSong
import dan.sonora.domain.models.DomainSongCollection

@Serializable
data class PlayerUiState(
	val queue: List<DomainSong> = emptyList(),
	val currentSong: DomainSong? = null,
	val currentCollection: DomainSongCollection? = null,
	val currentIndex: Int = -1,
	val isPaused: Boolean = false,
	val isShuffleEnabled: Boolean = false,
	val repeatMode: Int = 0,
	val progress: Float = 0f,
	val isLoading: Boolean = false,
	val playbackSpeed: Float = 1.0f,
	val playbackBitrate: Int? = null,
	val playbackSampleRate: Int? = null,
	val playbackMimeType: String? = null,
	val requestedBitrate: Int? = null,
	val requestedMimeType: String? = null
)

@Serializable
data class QueueUiState(
	val queue: List<DomainSong> = emptyList(),
	val currentIndex: Int = -1,
	val isPaused: Boolean = false
)
