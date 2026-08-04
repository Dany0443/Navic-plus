package dan.sonora.ui.screens.radio.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_no_radios
import org.jetbrains.compose.resources.stringResource
import dan.sonora.domain.models.DomainRadio
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.Radio
import dan.sonora.ui.components.common.ContentUnavailable
import dan.sonora.ui.core.UiState

fun LazyGridScope.radioListScreenContent(
	state: UiState<List<DomainRadio>>,
	onRadioClick: (DomainRadio) -> Unit
) {
	val data = state.data.orEmpty()

	if (data.isNotEmpty()) {
		items(data, key = { it.id }) { radio ->
			RadioListScreenCard(
				modifier = Modifier.animateItem(),
				radio = radio,
				onPlayClick = { onRadioClick(radio) }
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				items(10) {
					RadioListScreenCardPlaceholder()
				}
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.Radio,
						label = stringResource(Res.string.info_no_radios)
					)
				}
			}
		}
	}
}
