package dan.sonora.domain.models.settings

enum class InsightsSyncInterval(val minutes: Long, val displayName: String) {
	FifteenMinutes(15, "15 minutes"),
	ThirtyMinutes(30, "30 minutes"),
	OneHour(60, "1 hour"),
	SixHours(360, "6 hours"),
	TwelveHours(720, "12 hours"),
	TwentyFourHours(1440, "24 hours");

	companion object {
		fun fromMinutes(minutes: Long): InsightsSyncInterval =
			entries.firstOrNull { it.minutes == minutes } ?: ThirtyMinutes
	}
}
