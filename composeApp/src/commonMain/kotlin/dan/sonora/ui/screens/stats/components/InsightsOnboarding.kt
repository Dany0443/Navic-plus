package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dan.sonora.domain.stats.StatsProvider
import org.jetbrains.compose.resources.stringResource
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_insights_onboarding
import sonora.composeapp.generated.resources.title_insights_onboarding

/**
 * Shown when no stats provider is connected.
 *
 * The provider buttons are generated from the registered providers, so adding a
 * provider surfaces it here with no change to this file.
 */
@Composable
fun InsightsOnboarding(
	providers: List<StatsProvider>,
	onProviderSelected: (StatsProvider) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier.fillMaxWidth()) {
		Text(
			text = stringResource(Res.string.title_insights_onboarding),
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.SemiBold
		)
		Text(
			text = stringResource(Res.string.info_insights_onboarding),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 12.dp)
		)

		HorizontalDivider(Modifier.padding(vertical = 24.dp))

		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			providers.forEach { provider ->
				Button(
					onClick = { onProviderSelected(provider) },
					modifier = Modifier.fillMaxWidth()
				) {
					Text("Continue with ${provider.displayName}")
				}
			}
		}
	}
}
