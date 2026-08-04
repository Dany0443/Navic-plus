package dan.sonora.domain.stats

/**
 * Which provider Insights is currently reading from. This is the single source of
 * truth for every statistic — data from different providers is never merged, so
 * exactly one provider is active at a time (or none).
 */
sealed interface ActiveProvider {
	/** No provider is connected; the UI shows the Insights onboarding. */
	data object None : ActiveProvider

	data class Connected(val provider: StatsProvider) : ActiveProvider
}

/** The active provider's id, or null when nothing is connected. */
val ActiveProvider.id: String?
	get() = (this as? ActiveProvider.Connected)?.provider?.id

val ActiveProvider.providerOrNull: StatsProvider?
	get() = (this as? ActiveProvider.Connected)?.provider

/** Thrown when a statistics request is made while no provider is connected. */
class NoActiveProviderException : Exception("No stats provider is connected")
