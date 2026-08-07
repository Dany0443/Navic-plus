package dan.sonora.ui.components.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.subtitle_local_music_collection
import sonora.composeapp.generated.resources.title_local_music
import org.jetbrains.compose.resources.stringResource
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Note
import dan.sonora.ui.components.layouts.ArtGridItem

private const val LOCAL_MUSIC_ID = "local-music"

@Composable
fun LocalMusicCollectionItem(
	modifier: Modifier = Modifier,
	tab: String,
	isListMode: Boolean = false,
	onClick: () -> Unit
) {
	if (isListMode) {
		SpotifyLocalMusicItem(
			modifier = modifier,
			onClick = onClick
		)
	} else {
		ArtGridItem(
			modifier = modifier,
			onClick = onClick,
			coverArtId = null,
			placeholder = { LocalMusicCoverPlaceholder() },
			title = stringResource(Res.string.title_local_music),
			subtitle = stringResource(Res.string.subtitle_local_music_collection),
			id = LOCAL_MUSIC_ID,
			tab = tab
		)
	}
}

@Composable
private fun SpotifyLocalMusicItem(
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick
			)
			.semantics { contentDescription = "Local Music" }
			.padding(horizontal = 16.dp, vertical = 8.dp)
			.then(modifier),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier = Modifier
				.size(68.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(MaterialTheme.colorScheme.secondaryContainer)
		) {
			LocalMusicCoverPlaceholder()
		}
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 16.dp),
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = stringResource(Res.string.title_local_music),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = stringResource(Res.string.subtitle_local_music_collection),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(top = 4.dp)
			)
		}
	}
}

@Composable
private fun BoxScope.LocalMusicCoverPlaceholder() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.secondaryContainer)
	)
	Box(
		modifier = Modifier
			.align(Alignment.Center)
			.size(48.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.inverseSurface)
	)
	Box(
		modifier = Modifier
			.align(Alignment.Center)
			.size(32.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f))
	)
	Box(
		modifier = Modifier
			.align(Alignment.Center)
			.size(18.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.secondaryContainer),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = Icons.Outlined.Note,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSecondaryContainer,
			modifier = Modifier.size(12.dp)
		)
	}
}
