package dan.sonora.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed class DomainSongListType {
	@Serializable
	@Immutable
	data object FrequentlyPlayed : DomainSongListType()

	/**
	 * Smart Playlist: "Most Played" (curated, limited list).
	 *
	 * Kept separate from [FrequentlyPlayed] so the Songs tab can still sort the *entire* library
	 * by play count without being limited to 30 items.
	 */
	@Serializable
	@Immutable
	data object SmartMostPlayed : DomainSongListType()

	@Serializable
	@Immutable
	data object Newest : DomainSongListType()

	@Serializable
	@Immutable
	data object Starred : DomainSongListType()

	@Serializable
	@Immutable
	data object Random : DomainSongListType()

	@Serializable
	@Immutable
	data object Downloaded : DomainSongListType()

	@Serializable
	@Immutable
	data object LocalMusic : DomainSongListType()

	@Serializable
	@Immutable
	data object Rating : DomainSongListType()

	@Serializable
	@Immutable
	data object Year : DomainSongListType()

	@Serializable
	@Immutable
	data object OnRepeat : DomainSongListType()

	/**
	 * Smart Playlist: "On Repeat" (last 14 days).
	 */
	@Serializable
	@Immutable
	data object SmartOnRepeat : DomainSongListType()

	@Serializable
	@Immutable
	data object NeverPlayed : DomainSongListType()

	/**
	 * Smart Playlist: "Never Played" (recommendation-style mix).
	 */
	@Serializable
	@Immutable
	data object SmartNeverPlayed : DomainSongListType()

	@Serializable
	@Immutable
	data class ByGenre(val genre: String) : DomainSongListType()

	@Serializable
	@Immutable
	data class ByArtist(val artistId: String) : DomainSongListType()
}
