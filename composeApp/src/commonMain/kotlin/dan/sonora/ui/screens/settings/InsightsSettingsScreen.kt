package dan.sonora.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dan.sonora.LocalNavStack
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.UsernameConnectableProvider
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Check
import dan.sonora.ui.components.common.Form
import dan.sonora.ui.components.common.FormRow
import dan.sonora.ui.components.common.FormTitle
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.settings.viewmodels.InsightsSettingsViewModel
import dan.sonora.ui.screens.stats.components.UsernameConnectDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lists every registered stats provider. Tapping a connected provider opens its detail
 * page; tapping a disconnected one starts its connect flow.
 */
@Composable
fun InsightsSettingsScreen() {
	val viewModel = koinViewModel<InsightsSettingsViewModel>()
	val rows by viewModel.rows.collectAsStateWithLifecycle()
	val backStack = LocalNavStack.current
	val uriHandler = LocalUriHandler.current
	var usernameDialogProvider by remember { mutableStateOf<StatsProvider?>(null) }

	Scaffold(
		topBar = { NestedTopBar({ Text("Insights") }) }
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp)
		) {
			FormTitle("Providers")
			Form {
				rows.forEach { row ->
					FormRow(
						onClick = {
							if (row.isConnected) {
								// Connected providers open their detail page; making one
								// active is done from there, so a stray tap on this list
								// can never silently change what Insights is showing.
								backStack.add(Screen.Settings.InsightsProvider(row.id))
							} else {
								val provider = viewModel.providerById(row.id)
								val authorizationUrl = provider?.authorizationUrl
								when {
									provider is UsernameConnectableProvider ->
										usernameDialogProvider = provider
									authorizationUrl != null -> uriHandler.openUri(authorizationUrl)
								}
							}
						}
					) {
						Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
							if (row.isActive) {
								Icon(
									imageVector = Icons.Outlined.Check,
									contentDescription = "Active",
									modifier = Modifier.size(18.dp),
									tint = MaterialTheme.colorScheme.primary
								)
								Spacer(Modifier.width(8.dp))
							}
							Text(row.displayName)
						}
						Text(
							text = if (row.isConnected) "Connected" else "Not connected",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			Text(
				text = "Insights shows statistics from the selected provider only. " +
					"Switching keeps each provider's data intact.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 4.dp)
			)
		}
	}

	usernameDialogProvider?.let { provider ->
		UsernameConnectDialog(
			provider = provider,
			defaultServerUrl = (provider as UsernameConnectableProvider).defaultServerUrl,
			onDismissRequest = { usernameDialogProvider = null },
			onConnect = { username, serverUrl ->
				viewModel.connectWithUsername(provider.id, username, serverUrl)
			},
			onConnected = { usernameDialogProvider = null }
		)
	}
}
