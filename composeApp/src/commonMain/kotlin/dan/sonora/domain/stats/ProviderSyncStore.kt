package dan.sonora.domain.stats

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last-successful-sync timestamps, one per provider.
 *
 * Kept in [Settings] rather than a Room table: it is a single scalar per provider, and
 * credentials already live here, so disconnecting clears credentials and sync metadata
 * through the same store.
 */
class ProviderSyncStore(
	private val settings: Settings
) {
	private val _lastSyncedAt = MutableStateFlow(emptyMap<String, Long>())

	/** Epoch seconds of each provider's last successful sync. */
	val lastSyncedAt: StateFlow<Map<String, Long>> = _lastSyncedAt.asStateFlow()

	fun get(providerId: String): Long? =
		settings.getLongOrNull(key(providerId))

	fun set(providerId: String, epochSeconds: Long) {
		settings[key(providerId)] = epochSeconds
		_lastSyncedAt.value = _lastSyncedAt.value + (providerId to epochSeconds)
	}

	fun clear(providerId: String) {
		settings.remove(key(providerId))
		_lastSyncedAt.value = _lastSyncedAt.value - providerId
	}

	/** Seeds the observable state from disk so the UI shows timestamps before any sync. */
	fun refresh(providerIds: List<String>) {
		_lastSyncedAt.value = providerIds.mapNotNull { id ->
			get(id)?.let { id to it }
		}.toMap()
	}

	private fun key(providerId: String) = "statsLastSync_$providerId"
}
