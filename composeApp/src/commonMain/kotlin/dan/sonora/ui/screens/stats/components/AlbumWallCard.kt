package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dan.sonora.LocalNavStack
import dan.sonora.ui.components.common.CoverArt
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.stats.insightKey
import dan.sonora.ui.screens.stats.viewmodels.InsightsUiState

@Composable
fun AlbumWallCard(albums: List<InsightsUiState.InsightAlbum>, albumCoverArtIds: Map<String, String>, albumIds: Map<String, String>) {
	if (albums.isEmpty()) return
	val backStack = LocalNavStack.current
	InsightSectionTitle("The album wall", "The records that shaped your recent listening")
	LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
		items(albums, key = { "${it.name}-${it.artist}" }) { album ->
			val key = insightKey(album.name) + "|||" + insightKey(album.artist)
			val albumId = albumIds[key]
			Column(Modifier.width(146.dp).clickable(enabled = albumId != null) { albumId?.let { backStack.add(Screen.CollectionDetail(it, "insights")) } }) {
				CoverArt(
					coverArtId = albumCoverArtIds[key],
					contentDescription = album.name,
					modifier = Modifier.size(146.dp).clip(RoundedCornerShape(22.dp))
				)
				Spacer(Modifier.height(8.dp))
				Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(album.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text("${album.plays} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
			}
		}
	}
}
