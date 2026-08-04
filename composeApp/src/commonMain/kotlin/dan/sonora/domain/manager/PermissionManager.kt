package dan.sonora.domain.manager

sealed interface PermissionRequestResult {
	data object Granted : PermissionRequestResult
	data object Denied : PermissionRequestResult
	data object PermanentlyDenied : PermissionRequestResult
}

expect class PermissionManager {
	fun openPermissionsSettings()
	suspend fun requestLocalNetworkPermission(): Boolean
	suspend fun requestLocalMusicPermission(): PermissionRequestResult
}
