package paige.navic.domain.models.settings

/**
 * Immutable DSP configuration shared by the playback decks.
 *
 * The five-band values drive the advanced graphic equalizer.
 */
data class EqualizerSettings(
	val enabled: Boolean = false,
	val preampDb: Float = 0f,
	val bassDb: Float = 0f,
	val trebleDb: Float = 0f,
	val fiveBandGainsDb: FiveBandGainsDb = FiveBandGainsDb(),
	val limiter: LimiterSettings = LimiterSettings(),
)

/** Immutable gains for the advanced five-band equalizer. */
data class FiveBandGainsDb(
	val band1Db: Float = 0f,
	val band2Db: Float = 0f,
	val band3Db: Float = 0f,
	val band4Db: Float = 0f,
	val band5Db: Float = 0f,
)
