package dan.sonora.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dan.sonora.domain.stats.InsightsRepository
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.id
import dan.sonora.domain.manager.InsightsAutoSyncScheduler
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.models.settings.InsightsSyncInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderRow(
	val id: String,
	val displayName: String,
	val isConnected: Boolean,
	val isActive: Boolean,
	val accountName: String?,
	val lastSyncedAt: Long?
)

class InsightsSettingsViewModel(
	private val insightsRepository: InsightsRepository,
	private val preferenceManager: PreferenceManager,
	private val autoSyncScheduler: InsightsAutoSyncScheduler
) : ViewModel() {

	private val providers: List<StatsProvider> = insightsRepository.providers

	private val _autoSyncEnabled = MutableStateFlow(preferenceManager.insightsAutoSyncEnabled)
	val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

	private val _autoSyncInterval = MutableStateFlow(
		InsightsSyncInterval.fromMinutes(preferenceManager.insightsAutoSyncIntervalMinutes)
	)
	val autoSyncInterval: StateFlow<InsightsSyncInterval> = _autoSyncInterval.asStateFlow()

	val rows: StateFlow<List<ProviderRow>> = combine(
		combine(providers.map { it.isConnected }) { it.toList() },
		insightsRepository.activeProvider,
		insightsRepository.lastSyncedAt
	) { connected, active, lastSynced ->
		providers.mapIndexed { index, provider ->
			ProviderRow(
				id = provider.id,
				displayName = provider.displayName,
				isConnected = connected[index],
				isActive = active.id == provider.id,
				accountName = provider.accountName,
				lastSyncedAt = lastSynced[provider.id]
			)
		}
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	fun providerById(id: String): StatsProvider? = providers.firstOrNull { it.id == id }

	/** Ids of providers currently syncing, so each row shows only its own progress. */
	val syncingProviders: StateFlow<Set<String>> = insightsRepository.syncingProviders

	/** Why each provider's last sync failed, so a partial import is visible as one. */
	val syncErrors: StateFlow<Map<String, String>> = insightsRepository.syncErrors

	fun setAutoSyncEnabled(enabled: Boolean) {
		preferenceManager.insightsAutoSyncEnabled = enabled
		_autoSyncEnabled.value = enabled
		autoSyncScheduler.scheduleAutoSync(enabled, preferenceManager.insightsAutoSyncIntervalMinutes)
	}

	fun setAutoSyncInterval(interval: InsightsSyncInterval) {
		preferenceManager.insightsAutoSyncIntervalMinutes = interval.minutes
		_autoSyncInterval.value = interval
		autoSyncScheduler.scheduleAutoSync(preferenceManager.insightsAutoSyncEnabled, interval.minutes)
	}

	/** Switching is instant and preserves every provider's cache. */
	fun setActive(providerId: String) = insightsRepository.setActive(providerId)

	fun sync(providerId: String) = insightsRepository.sync(providerId)

	fun clearSyncError(providerId: String) = insightsRepository.clearSyncError(providerId)

	/** Throws when the account cannot be verified, so the dialog can report why. */
	suspend fun connectWithUsername(providerId: String, username: String, serverUrl: String) {
		insightsRepository.connectWithUsername(providerId, username, serverUrl)
	}

	fun disconnect(providerId: String) {
		viewModelScope.launch {
			insightsRepository.disconnect(providerId)
		}
	}
}
