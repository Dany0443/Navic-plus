package paige.navic.androidApp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import org.koin.android.ext.android.inject
import kotlinx.coroutines.launch
import paige.navic.App
import paige.navic.domain.manager.LastFmManager
import paige.navic.domain.manager.PermissionManager

class MainActivity : ComponentActivity() {
	private val lastFmManager: LastFmManager by inject()
	private val permissionManager: PermissionManager by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		permissionManager.registerLauncher(this)
		enableEdgeToEdge()
		setContent { App() }
		handleIntent(intent)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}

	private fun handleIntent(intent: Intent) {
		intent.data?.getQueryParameter("token")
			?.takeIf(String::isNotBlank)
			?.let { token ->
				lifecycleScope.launch {
					try {
						lastFmManager.exchangeTokenForSession(token)
					} catch (error: CancellationException) {
						throw error
					} catch (_: Exception) {
						// Authentication failures leave the user signed out without exposing request details.
					}
				}
			}
	}
}
