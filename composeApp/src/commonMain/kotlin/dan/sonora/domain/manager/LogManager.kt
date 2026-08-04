package dan.sonora.domain.manager

import dan.sonora.domain.parser.LogLine

expect class LogManager {
	val logs: List<LogLine>
	fun startStreaming()
	fun stopStreaming()
	fun clearLogs()
}
