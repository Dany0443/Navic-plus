package dan.sonora.ui.components.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
	onClick: () -> Unit
) {
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
			.size(76.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.inverseSurface)
	)
	Box(
		modifier = Modifier
			.align(Alignment.Center)
			.size(49.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f))
	)
	Box(
		modifier = Modifier
			.align(Alignment.Center)
			.size(24.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.secondaryContainer),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = Icons.Outlined.Note,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSecondaryContainer,
			modifier = Modifier.size(16.dp)
		)
	}
}
