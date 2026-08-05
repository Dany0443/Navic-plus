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
		settings.remove(backfillKey(providerId))
		_lastSyncedAt.value = _lastSyncedAt.value - providerId
	}

	/**
	 * Whether this provider's history has been imported in full at least once.
	 *
	 * Incremental sync asks only for scrobbles newer than the newest one held locally,
	 * so it can never fill a gap beneath that point. Until a full import has run to
	 * completion, sync must keep requesting the whole history — otherwise a run that
	 * fails partway leaves the older half permanently unreachable, because the pages it
	 * did commit have already moved the watermark forward.
	 *
	 * Defaulting to false is deliberate for installs that predate this flag: their cache
	 * is exactly the one that may be short, so re-importing once repairs it. Rows are
	 * keyed by (provider, timestamp) and inserted with REPLACE, so a re-import is
	 * idempotent — it restores what is missing without duplicating what is already there.
	 */
	fun isBackfillComplete(providerId: String): Boolean =
		settings.getBoolean(backfillKey(providerId), false)

	fun setBackfillComplete(providerId: String) {
		settings[backfillKey(providerId)] = true
	}

	/** Seeds the observable state from disk so the UI shows timestamps before any sync. */
	fun refresh(providerIds: List<String>) {
		_lastSyncedAt.value = providerIds.mapNotNull { id ->
			get(id)?.let { id to it }
		}.toMap()
	}

	private fun key(providerId: String) = "statsLastSync_$providerId"

	private fun backfillKey(providerId: String) = "statsBackfillComplete_$providerId"
}
