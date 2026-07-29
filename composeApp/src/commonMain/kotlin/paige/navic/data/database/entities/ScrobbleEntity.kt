package paige.navic.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "scrobble")
data class ScrobbleEntity(
	@PrimaryKey val timestamp: Long, // UNIX timestamp in seconds
	val trackName: String,
	val artistName: String,
	val albumName: String?,
	val url: String?,
	val coverArtUrl: String?
)
