package dan.sonora.domain.stats

import dan.sonora.domain.manager.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The set of available [StatsProvider]s and which one Insights currently reads from.
 *
 * Registering a provider here is the only step needed to surface it in onboarding and
 * settings — both are generated from [providers].
 */
class StatsProviderRegistry(
	val providers: List<StatsProvider>,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	/**
	 * The user's explicit choice. Empty means "no explicit choice" — the registry then
	 * falls back to whichever provider is connected, so connecting the first provider
	 * activates it without the caller having to select it.
	 */
	private val selectedId = MutableStateFlow(preferenceManager.activeStatsProvider)

	/** Providers the user has connected, in registration order. */
	val connectedProviders: StateFlow<List<StatsProvider>> =
		combine(providers.map { it.isConnected }) { flags ->
			providers.filterIndexed { index, _ -> flags[index] }
		}.stateIn(
			scope,
			SharingStarted.Eagerly,
			providers.filter { it.isConnected.value }
		)

	/**
	 * Derived from live connection state rather than captured once, so a provider that
	 * connects mid-session (for example when the Last.fm browser callback returns)
	 * becomes active immediately instead of on the next app launch.
	 */
	val activeProvider: StateFlow<ActiveProvider> =
		combine(connectedProviders, selectedId) { connected, selected ->
			resolve(connected, selected)
		}.stateIn(
			scope,
			SharingStarted.Eagerly,
			resolve(providers.filter { it.isConnected.value }, preferenceManager.activeStatsProvider)
		)

	private fun resolve(connected: List<StatsProvider>, selectedId: String): ActiveProvider {
		val provider = connected.firstOrNull { it.id == selectedId } ?: connected.firstOrNull()
		return provider?.let { ActiveProvider.Connected(it) } ?: ActiveProvider.None
	}

	/**
	 * Switches the active provider. Instant and non-destructive: each provider keeps
	 * its own cache, so switching back shows the previous data unchanged.
	 */
	fun setActive(id: String) {
		if (providers.none { it.id == id }) return
		preferenceManager.activeStatsProvider = id
		selectedId.value = id
	}

	/**
	 * Clears the stored selection after [id] was disconnected so the registry falls back
	 * to another connected provider, or to [ActiveProvider.None] when none remain.
	 */
	fun onDisconnected(id: String) {
		if (selectedId.value != id) return
		preferenceManager.activeStatsProvider = ""
		selectedId.value = ""
	}
}
