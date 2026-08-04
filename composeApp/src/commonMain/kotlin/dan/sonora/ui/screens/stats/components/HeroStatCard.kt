package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Repeat

@Composable
fun HeroStatCard(
	streakDays: Int,
	totalScrobbles: Int,
	scrobblesToday: Int,
	onSyncClick: () -> Unit,
	isSyncing: Boolean,
	syncProgress: Float
) {
	val colors = MaterialTheme.colorScheme
	ElevatedCard(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(28.dp),
		elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
	) {
		Column(
			modifier = Modifier
				.background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
				.padding(22.dp)
		) {
			Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				Column(modifier = Modifier.weight(1f)) {
					Text("Your music, in motion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = colors.onPrimary)
					Spacer(Modifier.height(4.dp))
					Text("A living snapshot of your listening life", style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary.copy(alpha = .78f))
				}
				IconButton(
					onClick = onSyncClick,
					enabled = !isSyncing,
					colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onPrimary)
				) {
					if (isSyncing) CircularProgressIndicator(Modifier.size(24.dp), color = colors.onPrimary, strokeWidth = 2.dp)
					else Icon(Icons.Outlined.Repeat, "Sync listening history")
				}
			}
			if (isSyncing) {
				LinearProgressIndicator(
					progress = { syncProgress },
					modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
					color = colors.onPrimary,
					trackColor = colors.onPrimary.copy(alpha = .25f)
				)
			}
			Spacer(Modifier.height(22.dp))
			Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				IdentityStat("SCROBBLES", totalScrobbles.toString(), Modifier.weight(1f))
				IdentityStat("TODAY", scrobblesToday.toString(), Modifier.weight(1f))
				IdentityStat("STREAK", "$streakDays d", Modifier.weight(1f))
			}
		}
	}
}

@Composable
private fun IdentityStat(label: String, value: String, modifier: Modifier) {
		Column(modifier) {
			Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
			Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f))
		}
}
