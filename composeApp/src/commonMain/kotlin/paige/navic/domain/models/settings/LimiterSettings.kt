package paige.navic.domain.models.settings

/** Immutable configuration for the peak-only loudness protection stage. */
data class LimiterSettings(
	val enabled: Boolean = false,
	val thresholdDb: Float = DEFAULT_THRESHOLD_DB,
) {
	companion object {
		const val MIN_THRESHOLD_DB = -12f
		const val MAX_THRESHOLD_DB = 0f
		const val DEFAULT_THRESHOLD_DB = -1f
	}
}
