package dan.sonora.domain.repositories

import dan.sonora.data.database.mappers.toDomainModel
import dan.sonora.domain.manager.SessionManager

class ShareRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getShares() = sessionManager.api.getShares().map { it.toDomainModel() }
}
