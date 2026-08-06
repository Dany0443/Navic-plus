package dan.sonora.domain.manager

enum class DownloadTrigger {
	MANUAL_USER_INITIATED,
	AUTOMATIC_BACKGROUND
}

class DownloadNetworkPolicy(
	private val connectivityManager: ConnectivityManager
) {
	fun isWifiOrUnmeteredConnected(): Boolean {
		return connectivityManager.isOnline.value && !connectivityManager.isCellular.value
	}

	fun canExecuteDownload(trigger: DownloadTrigger): Boolean {
		return when (trigger) {
			// Manual user triggers execute immediately over any network (Wi-Fi or Cellular)
			DownloadTrigger.MANUAL_USER_INITIATED -> true
			// Automatic background tasks strictly require unmetered Wi-Fi connection
			DownloadTrigger.AUTOMATIC_BACKGROUND -> isWifiOrUnmeteredConnected()
		}
	}
}
