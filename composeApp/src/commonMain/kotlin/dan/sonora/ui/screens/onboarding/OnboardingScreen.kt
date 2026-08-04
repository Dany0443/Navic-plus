package dan.sonora.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import dan.sonora.LocalNavStack
import dan.sonora.domain.manager.PermissionManager
import dan.sonora.domain.manager.PermissionRequestResult
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.repositories.LocalMusicRepository
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Check
import dan.sonora.icons.outlined.Error
import dan.sonora.icons.outlined.LibraryMusic
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.login.LoginScreen
import dan.sonora.util.core.PlatformType
import dan.sonora.LocalPlatformContext

/** Modular first-run flow. Login itself remains the existing reusable login screen. */
@Composable
fun OnboardingScreen() {
	var page by remember { mutableStateOf(OnboardingPage.Welcome) }
	when (page) {
		OnboardingPage.Welcome -> OnboardingMessagePage(
			title = "Welcome to Sonora",
			body = "Your music, your library.\n\nPlay music stored on your device or connect to your personal Navidrome/Subsonic server.\n\nLocal playback works completely offline. Connecting a server unlocks synchronized libraries, playlists, listening history, and more.",
			buttonLabel = "Continue",
			onContinue = { page = OnboardingPage.Server },
		)
		OnboardingPage.Server -> LoginScreen(
			onLoginSuccess = { page = OnboardingPage.LocalMusic },
			onSkip = { page = OnboardingPage.LocalMusic },
			heading = "Connect your server",
		)
		OnboardingPage.LocalMusic -> LocalMusicPage(
			onBack = { page = OnboardingPage.Server },
			onPermissionGranted = { page = OnboardingPage.Ready },
		)
		OnboardingPage.Ready -> ReadyPage()
	}
}

@Composable
private fun LocalMusicPage(
	onBack: () -> Unit,
	onPermissionGranted: () -> Unit,
) {
	val platformContext = LocalPlatformContext.current
	val permissionManager = koinInject<PermissionManager>()
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val localMusicRepository = koinInject<LocalMusicRepository>()
	val serverConnected by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	var status by remember { mutableStateOf<String?>(null) }

	OnboardingMessagePage(
		title = "Local Music",
		body = "Sonora can play music stored on this device, even when you are offline.",
		buttonLabel = "Allow Access",
		onContinue = {
			if (platformContext.platformType != PlatformType.Android) {
				onPermissionGranted()
				return@OnboardingMessagePage
			}
			scope.launch {
				when (permissionManager.requestLocalMusicPermission()) {
					PermissionRequestResult.Granted -> {
						preferenceManager.enableLocalMusic = true
						localMusicRepository.refresh()
						onPermissionGranted()
					}
					PermissionRequestResult.Denied,
					PermissionRequestResult.PermanentlyDenied -> {
						status = "Sonora needs access to your music library to play songs stored on your device. You can allow access now or later from Settings."
					}
				}
			}
		},
		extra = {
			if (status != null) {
				Text(status.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		},
		secondaryLabel = if (serverConnected) "Skip" else "Back",
		onSecondary = if (serverConnected) onPermissionGranted else onBack,
	)
}

@Composable
private fun ReadyPage() {
	val sessionManager = koinInject<SessionManager>()
	val serverConnected by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val preferenceManager = koinInject<PreferenceManager>()
	val localMusicEnabled = preferenceManager.enableLocalMusic
	val backStack = LocalNavStack.current
	val description = when {
		serverConnected && localMusicEnabled ->
			"You're all set.\nEnjoy your music from your device and your personal server."
		serverConnected -> "You can enable Local Music later from Settings."
		localMusicEnabled -> "You can connect a server later from the Library or Settings."
		else -> "Sonora works with Local Music and self-hosted servers.\n\nYou can enable Local Music from Settings or connect a server later from the Library or Settings."
	}

	Scaffold { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(24.dp).padding(padding),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Spacer(Modifier.weight(1f))
			Text("You're ready", style = MaterialTheme.typography.headlineLarge)
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				SetupSummaryRow(
					icon = if (serverConnected) Icons.Outlined.Check else Icons.Outlined.Error,
					label = if (serverConnected) "Server connected" else "No server connected",
					configured = serverConnected,
				)
				SetupSummaryRow(
					icon = if (localMusicEnabled) Icons.Outlined.LibraryMusic else Icons.Outlined.Error,
					label = if (localMusicEnabled) "Local Music ready" else "Local Music not enabled",
					configured = localMusicEnabled,
				)
			}
			Text(
				text = description,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(Modifier.weight(1f))
			Button(
				onClick = {
				preferenceManager.onboardingCompleted = true
				backStack.clear()
				backStack.add(Screen.Library())
			},
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Get Started")
			}
		}
	}
}

@Composable
private fun SetupSummaryRow(icon: ImageVector, label: String, configured: Boolean) {
	androidx.compose.foundation.layout.Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			modifier = Modifier.size(24.dp),
			tint = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
		)
		Text(label, style = MaterialTheme.typography.titleMedium)
	}
}

@Composable
private fun OnboardingMessagePage(
	title: String,
	body: String,
	buttonLabel: String,
	onContinue: () -> Unit,
	extra: @Composable (() -> Unit)? = null,
	secondaryLabel: String? = null,
	onSecondary: (() -> Unit)? = null,
) {
	Scaffold { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(24.dp).padding(padding),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Spacer(Modifier.weight(1f))
			Text(title, style = MaterialTheme.typography.headlineLarge)
			Text(body, style = MaterialTheme.typography.bodyLarge)
			Spacer(Modifier.weight(1f))
			extra?.invoke()
			if (secondaryLabel != null && onSecondary != null) {
				androidx.compose.material3.TextButton(
					onClick = onSecondary,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(secondaryLabel)
				}
			}
			Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
				Text(buttonLabel)
			}
		}
	}
}

private enum class OnboardingPage { Welcome, Server, LocalMusic, Ready }
