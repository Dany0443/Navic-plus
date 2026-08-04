package dan.sonora.androidApp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import org.koin.android.ext.android.inject
import kotlinx.coroutines.launch
import dan.sonora.App
import dan.sonora.data.stats.lastfm.LastFmAuthStore
import dan.sonora.domain.manager.PermissionManager

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
	private val lastFmAuthStore: LastFmAuthStore by inject()
	private val permissionManager: PermissionManager by inject()

	/** Holds the system splash until Compose has drawn, so the two never both appear. */
	private var firstFrameDrawn = false

	/** Status bar appearance chosen by the app theme, saved while the splash overrides it. */
	private var themeLightStatusBars: Boolean? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen().apply {
			setKeepOnScreenCondition { !firstFrameDrawn }
			// Remove without the default exit animation: Compose's first frame draws
			// the same vinyl in the same place, so any zoom or fade here would read
			// as a jump between two splashes rather than one continuous surface.
			setOnExitAnimationListener { it.remove() }
		}
		super.onCreate(savedInstanceState)
		permissionManager.registerLauncher(this)
		enableEdgeToEdge()
		setContent {
			var showSplash by rememberSaveable { mutableStateOf(true) }
			val view = LocalView.current

			Box {
				// Composed from the first frame so initialisation happens behind the
				// splash rather than after it.
				App()
				if (showSplash) {
					SonoraSplash(onFinished = { showSplash = false })
				}
			}

			// Must come after App(), whose own SideEffect sets this from the theme:
			// SideEffects run in composition order, so the later one wins. App() is
			// skippable and may not re-run its effect, hence the explicit restore.
			SideEffect {
				val controller = WindowCompat.getInsetsController(window, view)
				if (showSplash) {
					if (themeLightStatusBars == null) {
						themeLightStatusBars = controller.isAppearanceLightStatusBars
					}
					controller.isAppearanceLightStatusBars = false
				} else {
					themeLightStatusBars?.let {
						controller.isAppearanceLightStatusBars = it
						themeLightStatusBars = null
					}
				}
				firstFrameDrawn = true
			}
		}
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
						lastFmAuthStore.exchangeTokenForSession(token)
					} catch (error: CancellationException) {
						throw error
					} catch (_: Exception) {
						// Authentication failures leave the user signed out without exposing request details.
					}
				}
			}
	}
}
