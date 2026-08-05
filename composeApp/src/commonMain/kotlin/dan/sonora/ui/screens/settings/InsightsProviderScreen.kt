package dan.sonora.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dan.sonora.LocalNavStack
import dan.sonora.ui.components.common.Form
import dan.sonora.ui.components.common.FormButton
import dan.sonora.ui.components.common.FormRow
import dan.sonora.ui.components.common.FormTitle
import dan.sonora.ui.components.dialogs.FormDialog
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.screens.settings.viewmodels.InsightsSettingsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * Detail page for one provider: who is connected, when it last synced, and the
 * actions to sync, make it active, or disconnect.
 */
@Composable
fun InsightsProviderScreen(providerId: String) {
	val viewModel = koinViewModel<InsightsSettingsViewModel>()
	val rows by viewModel.rows.collectAsStateWithLifecycle()
	val syncing by viewModel.syncingProviders.collectAsStateWithLifecycle()
	val syncErrors by viewModel.syncErrors.collectAsStateWithLifecycle()
	val backStack = LocalNavStack.current
	var confirmDisconnect by remember { mutableStateOf(false) }

	val row = rows.firstOrNull { it.id == providerId }
	val isSyncing = providerId in syncing
	val syncError = syncErrors[providerId]

	Scaffold(
		topBar = { NestedTopBar({ Text(row?.displayName ?: "Provider") }) }
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp)
		) {
			if (row == null) return@Column

			Form {
				FormRow {
					Text("Connected as")
					Text(
						text = row.accountName ?: "—",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				FormRow {
					Text("Last sync")
					Text(
						text = formatLastSync(row.lastSyncedAt),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}

			if (isSyncing) {
				LinearProgressIndicator(Modifier.padding(bottom = 16.dp))
			}

			// A failed sync leaves a partial import behind; saying so is what
			// distinguishes it from a complete one.
			syncError?.let { message ->
				Text(
					text = "Last sync did not finish: $message",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(bottom = 16.dp, start = 4.dp, end = 4.dp)
				)
			}

			FormTitle("Actions")
			Form {
				if (!row.isActive) {
					FormRow(onClick = { viewModel.setActive(row.id) }) {
						Text("Use for Insights")
					}
				}
				FormRow(onClick = { viewModel.sync(row.id) }) {
					Text(if (isSyncing) "Syncing…" else "Sync now")
				}
				FormRow(onClick = { confirmDisconnect = true }) {
					Text("Disconnect", color = MaterialTheme.colorScheme.error)
				}
			}
		}
	}

	if (confirmDisconnect && row != null) {
		FormDialog(
			onDismissRequest = { confirmDisconnect = false },
			title = { Text("Disconnect ${row.displayName}?") },
			content = {
				Text(
					"This removes your credentials and deletes the listening history " +
						"cached for ${row.displayName}. Other providers are unaffected."
				)
			},
			buttons = {
				FormButton(
					onClick = {
						viewModel.disconnect(row.id)
						confirmDisconnect = false
						backStack.removeLastOrNull()
					}
				) {
					Text("Disconnect", color = MaterialTheme.colorScheme.error)
				}
				FormButton(onClick = { confirmDisconnect = false }) {
					Text("Cancel")
				}
			}
		)
	}
}

private fun formatLastSync(epochSeconds: Long?): String {
	if (epochSeconds == null) return "Never"
	val zone = TimeZone.currentSystemDefault()
	val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone).date
	val today = Clock.System.now().toLocalDateTime(zone).date
	return when (date) {
		today -> "Today"
		else -> date.toString()
	}
}
