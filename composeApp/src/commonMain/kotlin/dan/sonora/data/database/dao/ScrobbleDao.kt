package dan.sonora.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import dan.sonora.data.database.entities.ScrobbleEntity

@Dao
interface ScrobbleDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertScrobbles(scrobbles: List<ScrobbleEntity>)

	// Unfiltered queries: smart playlists intentionally consider listening history
	// from every provider, not just the one Insights is currently showing.

	@Query("SELECT * FROM scrobble ORDER BY timestamp DESC LIMIT :limit")
	fun getRecentScrobbles(limit: Int): Flow<List<ScrobbleEntity>>

	@Query("SELECT * FROM scrobble WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
	fun getScrobblesSince(sinceTimestamp: Long): Flow<List<ScrobbleEntity>>

	@Query("SELECT COUNT(*) FROM scrobble")
	fun getTotalScrobbleCount(): Flow<Int>

	@Query("SELECT timestamp FROM scrobble ORDER BY timestamp DESC LIMIT 1")
	suspend fun getLatestScrobbleTimestamp(): Long?

	@Query("SELECT timestamp FROM scrobble ORDER BY timestamp ASC LIMIT 1")
	suspend fun getOldestScrobbleTimestamp(): Long?

	// Provider-scoped queries: Insights reports on the active provider alone.

	@Query("SELECT * FROM scrobble WHERE provider = :provider ORDER BY timestamp DESC LIMIT :limit")
	fun getRecentScrobbles(provider: String, limit: Int): Flow<List<ScrobbleEntity>>

	@Query("SELECT * FROM scrobble WHERE provider = :provider AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
	fun getScrobblesSince(provider: String, sinceTimestamp: Long): Flow<List<ScrobbleEntity>>

	@Query("SELECT * FROM scrobble WHERE provider = :provider AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
	suspend fun getScrobblesSinceList(provider: String, sinceTimestamp: Long): List<ScrobbleEntity>

	@Query("SELECT COUNT(*) FROM scrobble WHERE provider = :provider")
	fun getTotalScrobbleCount(provider: String): Flow<Int>

	@Query("SELECT timestamp FROM scrobble WHERE provider = :provider ORDER BY timestamp DESC LIMIT 1")
	suspend fun getLatestScrobbleTimestamp(provider: String): Long?

	@Query("DELETE FROM scrobble WHERE provider = :provider")
	suspend fun deleteByProvider(provider: String)

	@Query("DELETE FROM scrobble")
	suspend fun clearAll()
}
