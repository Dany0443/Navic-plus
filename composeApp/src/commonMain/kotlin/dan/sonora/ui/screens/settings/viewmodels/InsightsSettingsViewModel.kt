package dan.sonora.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dan.sonora.domain.stats.InsightsRepository
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.id
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
	private val insightsRepository: InsightsRepository
) : ViewModel() {

	private val providers: List<StatsProvider> = insightsRepository.providers

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

	/** Switching is instant and preserves every provider's cache. */
	fun setActive(providerId: String) = insightsRepository.setActive(providerId)

	fun sync(providerId: String) = insightsRepository.sync(providerId)

	fun disconnect(providerId: String) {
		viewModelScope.launch {
			insightsRepository.disconnect(providerId)
		}
	}
}
