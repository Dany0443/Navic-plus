package paige.navic.domain.localmusic

import kotlinx.coroutines.flow.Flow
import paige.navic.domain.models.DomainSong

expect class LocalMusicScanner {
	suspend fun scan(): List<DomainSong>
	fun changes(): Flow<Unit>
}

fun String.isSupportedLocalMusicFile(): Boolean {
	val lower = lowercase()
	return lower.endsWith(".mp3") ||
		lower.endsWith(".m4a") ||
		lower.endsWith(".aac") ||
		lower.endsWith(".ogg") ||
		lower.endsWith(".oga") ||
		lower.endsWith(".wav") ||
		lower.endsWith(".flac") ||
		lower.endsWith(".opus") ||
		lower.endsWith(".wma") ||
		lower.endsWith(".m4b")
}
