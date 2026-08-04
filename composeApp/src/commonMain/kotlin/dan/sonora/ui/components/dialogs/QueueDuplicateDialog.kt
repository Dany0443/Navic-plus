package dan.sonora.ui.components.dialogs

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.action_cancel
import sonora.composeapp.generated.resources.action_ok
import sonora.composeapp.generated.resources.notice_queue_duplicate
import sonora.composeapp.generated.resources.title_confirm
import org.jetbrains.compose.resources.stringResource
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.PlaylistAdd
import dan.sonora.ui.components.common.FormButton

@Composable
fun QueueDuplicateDialog(
	onDismissRequest: () -> Unit,
	onConfirm: () -> Unit
) {
	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.PlaylistAdd, contentDescription = null) },
		title = { Text(stringResource(Res.string.title_confirm)) },
		content = { Text(stringResource(Res.string.notice_queue_duplicate)) },
		buttons = {
			FormButton(
				onClick = {
					onConfirm()
					onDismissRequest()
				}
			) {
				Text(stringResource(Res.string.action_ok))
			}
			FormButton(
				onClick = onDismissRequest
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
	)
}
