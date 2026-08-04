package dan.sonora.ui.screens.genre.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_no_genres
import org.jetbrains.compose.resources.stringResource
import dan.sonora.domain.models.DomainGenre
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Genre
import dan.sonora.ui.components.common.ContentUnavailable
import dan.sonora.ui.core.UiState

fun LazyGridScope.genreListScreenContent(
	state: UiState<List<DomainGenre>>
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data, { it.name }) { genre ->
			GenreListScreenCard(
				modifier = Modifier.animateItem(),
				genre = genre
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> items(10) {
				GenreListScreenCardPlaceholder()
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.Genre,
						label = stringResource(Res.string.info_no_genres)
					)
				}
			}
		}
	}
}
