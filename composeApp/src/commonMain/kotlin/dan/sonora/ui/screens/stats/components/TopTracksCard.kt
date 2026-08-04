package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dan.sonora.LocalNavStack
import dan.sonora.domain.stats.ProviderTrack
import dan.sonora.ui.components.common.CoverArt
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.stats.insightKey

@Composable
fun TopTracksCard(tracks: List<ProviderTrack>, trackCoverArtIds: Map<String, String>, trackIds: Map<String, String>) {
	if (tracks.isEmpty()) return
	val backStack = LocalNavStack.current
	InsightSectionTitle("Tracks on repeat", "Your most played songs")
	Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
		Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			tracks.take(6).forEach { track ->
				val id = trackIds[insightKey(track.name)]
				Row(
					Modifier.fillMaxWidth().clickable(enabled = id != null) { id?.let { backStack.add(Screen.SongDetailScreen(it, trackCoverArtIds[insightKey(track.name)])) } }.padding(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					CoverArt(coverArtId = trackCoverArtIds[insightKey(track.name)], contentDescription = track.name, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
					Spacer(Modifier.width(12.dp))
					Column(Modifier.weight(1f)) {
						Text(track.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
						track.artistName.takeIf { it.isNotBlank() }?.let {
							Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
					Text("${track.playCount}\nplays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
				}
			}
		}
	}
}
