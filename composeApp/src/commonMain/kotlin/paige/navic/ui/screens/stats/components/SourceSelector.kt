package paige.navic.ui.screens.stats.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun SourceSelector() {
	var expanded by remember { mutableStateOf(false) }
	var selectedSource by remember { mutableStateOf("Last.fm") }

	Box {
		TextButton(
			onClick = { expanded = true },
			colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
		) {
			Text(selectedSource)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			DropdownMenuItem(
				text = { Text("Last.fm") },
				onClick = {
					selectedSource = "Last.fm"
					expanded = false
				}
			)
			DropdownMenuItem(
				text = { Text("Navic (Coming Soon)") },
				onClick = { expanded = false },
				enabled = false
			)
			DropdownMenuItem(
				text = { Text("Combined (Coming Soon)") },
				onClick = { expanded = false },
				enabled = false
			)
		}
	}
}
