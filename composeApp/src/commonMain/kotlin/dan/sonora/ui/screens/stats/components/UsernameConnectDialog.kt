package dan.sonora.ui.screens.stats.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dan.sonora.domain.stats.StatsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Collects a username for a provider whose listening data is public.
 *
 * [onConnect] performs the real connection and throws if the account cannot be
 * verified, so the dialog stays open on failure with the reason shown inline rather
 * than dismissing into a half-connected state.
 */
@Composable
fun UsernameConnectDialog(
	provider: StatsProvider,
	defaultServerUrl: String,
	onDismissRequest: () -> Unit,
	onConnect: suspend (username: String, serverUrl: String) -> Unit,
	onConnected: () -> Unit
) {
	var username by remember { mutableStateOf("") }
	var serverUrl by remember { mutableStateOf(defaultServerUrl) }
	var useCustomServer by remember { mutableStateOf(false) }
	var isConnecting by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	val scope = rememberCoroutineScope()

	val canSubmit = username.isNotBlank() &&
		(!useCustomServer || serverUrl.isNotBlank()) &&
		!isConnecting

	fun submit() {
		if (!canSubmit) return
		isConnecting = true
		error = null
		scope.launch {
			try {
				onConnect(username, if (useCustomServer) serverUrl else defaultServerUrl)
				onConnected()
			} catch (cancellation: CancellationException) {
				throw cancellation
			} catch (failure: Exception) {
				error = failure.message ?: "Could not connect to ${provider.displayName}"
				isConnecting = false
			}
		}
	}

	AlertDialog(
		// A dismiss mid-request would leave the connection resolving with no UI to
		// report into, so the dialog holds until it settles.
		onDismissRequest = { if (!isConnecting) onDismissRequest() },
		title = { Text("Connect ${provider.displayName}") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
				Text(
					text = "Enter your ${provider.displayName} username. " +
						"${provider.displayName} listening statistics are public, " +
						"so no password or API key is required.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				OutlinedTextField(
					value = username,
					onValueChange = {
						username = it
						error = null
					},
					label = { Text("Username") },
					singleLine = true,
					enabled = !isConnecting,
					isError = error != null,
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
					keyboardActions = KeyboardActions(onDone = { submit() }),
					modifier = Modifier.fillMaxWidth()
				)

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clickable(enabled = !isConnecting) { useCustomServer = !useCustomServer }
						.padding(vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Checkbox(
						checked = useCustomServer,
						onCheckedChange = { useCustomServer = it },
						enabled = !isConnecting
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text("Use a custom server")
				}

				AnimatedVisibility(visible = useCustomServer) {
					OutlinedTextField(
						value = serverUrl,
						onValueChange = {
							serverUrl = it
							error = null
						},
						label = { Text("Server URL") },
						singleLine = true,
						enabled = !isConnecting,
						keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
						keyboardActions = KeyboardActions(onDone = { submit() }),
						modifier = Modifier.fillMaxWidth()
					)
				}

				error?.let {
					Text(
						text = it,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error
					)
				}
			}
		},
		confirmButton = {
			Button(onClick = { submit() }, enabled = canSubmit) {
				if (isConnecting) {
					CircularProgressIndicator(
						modifier = Modifier.size(16.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.onPrimary
					)
					Spacer(Modifier.width(8.dp))
				}
				Text(if (isConnecting) "Connecting…" else "Connect")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismissRequest, enabled = !isConnecting) {
				Text("Cancel")
			}
		}
	)
}
