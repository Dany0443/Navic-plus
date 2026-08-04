package dan.sonora.ui.screens.lyrics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.action_refresh
import sonora.composeapp.generated.resources.info_no_lyrics
import org.jetbrains.compose.resources.stringResource
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Lyrics
import dan.sonora.ui.components.common.ContentUnavailable

@Composable
fun LyricsScreenPlaceholder(
	onRefresh: () -> Unit
) {

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
	) {
		ContentUnavailable(
			modifier = Modifier,
			icon = Icons.Outlined.Lyrics,
			label = stringResource(Res.string.info_no_lyrics)
		)

		TextButton(onClick = dropUnlessResumed {
			onRefresh()
		}) {
			Text(stringResource(Res.string.action_refresh))
		}
	}
}
