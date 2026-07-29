package paige.navic.ui.screens.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.LocalNavStack
import paige.navic.domain.manager.LastFmArtist
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Album
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.stats.insightKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment

@Composable
fun TopArtistsCard(artists: List<LastFmArtist>, artistCoverArtIds: Map<String, String>, artistIds: Map<String, String>) {
	if (artists.isEmpty()) return
	val backStack = LocalNavStack.current
	InsightSectionTitle("Your sound, by artist", "Most played across your Last.fm history")
	LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
		items(artists.take(12), key = { it.name }) { artist ->
			val id = artistIds[insightKey(artist.name)]
			ArtistInsightItem(
				artist = artist,
				coverArtId = artistCoverArtIds[artist.name],
				onClick = { id?.let { backStack.add(Screen.ArtistDetail(it)) } }
			)
		}
	}
}

@Composable
private fun ArtistInsightItem(artist: LastFmArtist, coverArtId: String?, onClick: () -> Unit) {
	Column(modifier = Modifier.width(112.dp).clickable(onClick = onClick)) {
		if (coverArtId.isNullOrBlank()) {
			Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
				androidx.compose.material3.Icon(Icons.Outlined.Album, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
			}
		} else {
			CoverArt(coverArtId = coverArtId, contentDescription = artist.name, modifier = Modifier.size(96.dp), shape = CircleShape)
		}
		Text(artist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
		Text("${artist.playcount} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
