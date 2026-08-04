package dan.sonora.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.action_star
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import dan.sonora.icons.Icons
import dan.sonora.icons.filled.Star
import dan.sonora.icons.outlined.Star
import dan.sonora.shared.MediaPlayerViewModel

@Composable
fun NowPlayingStarButton(
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	IconButton(
		onClick = {
			onSetSongIsStarred(!songIsStarred)
		},
		colors = IconButtonDefaults.filledTonalIconButtonColors(),
		modifier = Modifier.size(32.dp),
		enabled = playerState.currentSong != null
	) {
		Icon(
			if (songIsStarred) Icons.Filled.Star else Icons.Outlined.Star,
			contentDescription = stringResource(Res.string.action_star)
		)
	}
}
