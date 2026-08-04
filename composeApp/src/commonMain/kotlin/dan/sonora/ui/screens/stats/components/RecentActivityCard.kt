package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dan.sonora.data.database.entities.ScrobbleEntity
import dan.sonora.ui.components.common.CoverArt
import dan.sonora.LocalNavStack
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.stats.insightKey
import dan.sonora.ui.screens.stats.viewmodels.InsightsUiState
import dan.sonora.util.core.Logger

@Composable
fun RecentActivityCard(
	scrobbles: List<ScrobbleEntity>,
	albumCoverArtIds: Map<String, String>,
	albumIds: Map<String, String>,
	trackCoverArtIds: Map<String, String>,
	trackMatchDiagnostics: Map<String, InsightsUiState.SongMatchDiagnostic>,
	artworkDiagnosticsReady: Boolean
) {
	if (scrobbles.isEmpty()) return
	val backStack = LocalNavStack.current
	InsightSectionTitle("Recently in your world", "A timeline of your latest listens")
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		scrobbles.take(8).forEach { scrobble ->
			val key = insightKey(scrobble.albumName.orEmpty()) + "|||" + insightKey(scrobble.artistName)
			val trackKey = insightKey(scrobble.trackName) + "|||" + insightKey(scrobble.artistName)
			val albumCoverArtId = albumCoverArtIds[key]
			val trackCoverArtId = trackCoverArtIds[trackKey]
			val coverArtId = albumCoverArtId ?: trackCoverArtId
			if (artworkDiagnosticsReady && coverArtId == null) {
				LaunchedEffect(scrobble.timestamp, coverArtId) {
					val diagnostic = trackMatchDiagnostics[trackKey]
					val lookupResult = when (diagnostic?.count) {
						0 -> "0 songs"
						1 -> "1 song with blank coverArtId"
						null -> "0 songs"
						else -> "multiple matching songs (${diagnostic.count})"
					}
					val matches = diagnostic?.takeIf { it.count > 1 }
						?.songs
						?.joinToString { song -> "${song.songId} (album=${song.albumName})" }
					Logger.i(
						"InsightsArtwork",
						"Missing artwork | track=${scrobble.trackName} | artist=${scrobble.artistName} | album=${scrobble.albumName} | albumArtistLookupSucceeded=${albumCoverArtId != null} | titleArtistFallbackSucceeded=${trackCoverArtId != null} | titleArtistLookup=$lookupResult" +
							matches?.let { " | matches=$it" }.orEmpty()
					)
				}
			}
			Row(Modifier.fillMaxWidth().clickable(enabled = albumIds[key] != null) { albumIds[key]?.let { backStack.add(Screen.CollectionDetail(it, "insights")) } }, verticalAlignment = Alignment.CenterVertically) {
				CoverArt(coverArtId = coverArtId, contentDescription = scrobble.albumName ?: scrobble.trackName, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
				Spacer(Modifier.width(12.dp))
				Column(Modifier.weight(1f)) {
					Text(scrobble.trackName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text(listOfNotNull(scrobble.artistName, scrobble.albumName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				Text(formatScrobbleTime(scrobble.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}

private fun formatScrobbleTime(timestamp: Long): String {
		val local = Instant.fromEpochSeconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
		return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
