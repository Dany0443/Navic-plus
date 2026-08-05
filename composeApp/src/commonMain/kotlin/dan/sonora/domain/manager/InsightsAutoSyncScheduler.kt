package dan.sonora.domain.manager

expect class InsightsAutoSyncScheduler {
	fun scheduleAutoSync(enabled: Boolean, intervalMinutes: Long)
}
