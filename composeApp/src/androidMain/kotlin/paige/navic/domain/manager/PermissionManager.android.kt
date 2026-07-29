package paige.navic.domain.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PermissionManager(
	private val context: Context
) {
	private var pendingContinuation: CancellableContinuation<Boolean>? = null
	private var permissionLauncher: ActivityResultLauncher<String>? = null
	private var permissionActivity: ComponentActivity? = null

	// I have no idea how else to get ComponentActivity
	fun registerLauncher(componentActivity: ComponentActivity) {
		permissionActivity = componentActivity
		permissionLauncher = componentActivity.registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { isGranted ->
			pendingContinuation?.resume(isGranted)
			pendingContinuation = null
		}
	}

	actual fun openPermissionsSettings() {
		val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
		intent.data = Uri.fromParts("package", context.packageName, null)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
		context.startActivity(intent)
	}

	actual suspend fun requestLocalNetworkPermission(): Boolean {
		if (Build.VERSION.SDK_INT < 37) {
			// this permission is only needed on android 17
			return true
		}

		val alreadyGranted = context.checkSelfPermission(
			Manifest.permission.ACCESS_LOCAL_NETWORK
		) == PackageManager.PERMISSION_GRANTED

		if (alreadyGranted) return true

		return suspendCancellableCoroutine { continuation ->
			pendingContinuation = continuation
			continuation.invokeOnCancellation {
				pendingContinuation = null
			}
			permissionLauncher!!.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
		}
	}

	actual suspend fun requestLocalMusicPermission(): PermissionRequestResult {
		val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			Manifest.permission.READ_MEDIA_AUDIO
		} else {
			Manifest.permission.READ_EXTERNAL_STORAGE
		}

		val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
		if (alreadyGranted) return PermissionRequestResult.Granted

		val isGranted = suspendCancellableCoroutine { continuation ->
			pendingContinuation = continuation
			continuation.invokeOnCancellation {
				pendingContinuation = null
			}
			permissionLauncher!!.launch(permission)
		}

		return if (isGranted) {
			PermissionRequestResult.Granted
		} else {
			val activity = permissionActivity
			if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
				PermissionRequestResult.Denied
			} else {
				PermissionRequestResult.PermanentlyDenied
			}
		}
	}
}
