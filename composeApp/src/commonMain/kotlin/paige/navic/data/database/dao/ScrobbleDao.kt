package paige.navic.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import paige.navic.data.database.entities.ScrobbleEntity

@Dao
interface ScrobbleDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertScrobbles(scrobbles: List<ScrobbleEntity>)

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

	@Query("DELETE FROM scrobble")
	suspend fun clearAll()
}
