package paige.navic.ui.screens.login

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import paige.navic.ui.screens.login.pages.LoginScreenContent

@Composable
fun LoginScreen(
	onLoginSuccess: (() -> Unit)? = null,
	onSkip: (() -> Unit)? = null,
	heading: String? = null,
) {
	Scaffold { innerPadding ->
		LoginScreenContent(
			innerPadding = innerPadding,
			onLoginSuccess = onLoginSuccess,
			onSkip = onSkip,
			heading = heading,
		)
	}
}
