package dan.sonora.data.database.entities

import androidx.room3.Entity

/**
 * A cached scrobble. Keyed by provider as well as timestamp so several stats
 * providers can be synced side by side without colliding.
 */
@Entity(tableName = "scrobble", primaryKeys = ["provider", "timestamp"])
data class ScrobbleEntity(
	/** [dan.sonora.domain.stats.StatsProvider.id] that supplied this scrobble. */
	val provider: String,
	val timestamp: Long, // UNIX timestamp in seconds
	val trackName: String,
	val artistName: String,
	val albumName: String?,
	val url: String?,
	val coverArtUrl: String?
)
