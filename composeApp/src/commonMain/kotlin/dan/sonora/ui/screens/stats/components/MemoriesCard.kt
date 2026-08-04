package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dan.sonora.data.database.entities.ScrobbleEntity

@Composable
fun MemoriesCard(scrobbles: List<ScrobbleEntity>) {
	// Look for scrobbles exactly 1 year ago (±1 day)
	val now = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
	val oneYearAgo = now - 31536000 // approx 1 year in seconds
	val dayInSeconds = 86400
	
	val memoryScrobble = scrobbles.firstOrNull { 
		it.timestamp in (oneYearAgo - dayInSeconds)..(oneYearAgo + dayInSeconds)
	} ?: return

	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = "On This Day",
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface
		)
		Spacer(modifier = Modifier.height(16.dp))

		Card(
			modifier = Modifier.fillMaxWidth(),
			shape = RoundedCornerShape(16.dp),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text(
					text = "One year ago, you listened to:",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = memoryScrobble.trackName,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.Black,
					color = MaterialTheme.colorScheme.onTertiaryContainer,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = memoryScrobble.artistName,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}
