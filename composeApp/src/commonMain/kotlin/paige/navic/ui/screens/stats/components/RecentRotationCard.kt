package paige.navic.ui.screens.stats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.LocalNavStack
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.stats.insightKey
import paige.navic.ui.screens.stats.viewmodels.InsightsUiState

@Composable
fun RecentRotationCard(
	artists: List<InsightsUiState.RotationArtist>,
	tracks: List<InsightsUiState.RotationTrack>,
	artistIds: Map<String, String>,
	trackIds: Map<String, String>,
	trackCoverArtIds: Map<String, String>
) {
	if (artists.isEmpty() && tracks.isEmpty()) return
	val backStack = LocalNavStack.current

	AnalyticsCard("Recent rotation", "What you're listening to these days — the last 14 days") {
		if (artists.isNotEmpty()) {
			RotationGroupLabel("Artists in rotation")
			artists.forEachIndexed { index, artist ->
				val id = artistIds[insightKey(artist.name)]
				RotationRow(
					rank = index + 1,
					title = artist.name,
					subtitle = null,
					plays = artist.plays,
					onClick = id?.let { { backStack.add(Screen.ArtistDetail(it)) } }
				)
			}
		}
		if (artists.isNotEmpty() && tracks.isNotEmpty()) {
			HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
		}
		if (tracks.isNotEmpty()) {
			RotationGroupLabel("Songs in rotation")
			tracks.forEachIndexed { index, track ->
				val id = trackIds[insightKey(track.name)]
				RotationRow(
					rank = index + 1,
					title = track.name,
					subtitle = track.artist,
					plays = track.plays,
					onClick = id?.let { { backStack.add(Screen.SongDetailScreen(it, trackCoverArtIds[insightKey(track.name)])) } }
				)
			}
		}
	}
}

@Composable
private fun RotationGroupLabel(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelLarge,
		fontWeight = FontWeight.Bold,
		color = MaterialTheme.colorScheme.primary
	)
	Spacer(Modifier.height(6.dp))
}

@Composable
private fun RotationRow(
	rank: Int,
	title: String,
	subtitle: String?,
	plays: Int,
	onClick: (() -> Unit)?
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = "$rank",
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.width(22.dp)
		)
		Column(Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			subtitle?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
		Text(
			text = "$plays plays",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}
