package dan.sonora.domain.models

import androidx.compose.runtime.Immutable
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_sort_alphabetical_by_name
import sonora.composeapp.generated.resources.option_sort_random
import sonora.composeapp.generated.resources.option_sort_starred
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainArtistListType(val displayName: StringResource) {
	AlphabeticalByName(Res.string.option_sort_alphabetical_by_name),
	Starred(Res.string.option_sort_starred),
	Random(Res.string.option_sort_random)
}
