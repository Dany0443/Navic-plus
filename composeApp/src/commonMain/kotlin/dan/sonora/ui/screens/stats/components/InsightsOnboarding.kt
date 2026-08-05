package dan.sonora.ui.screens.stats.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dan.sonora.domain.stats.StatsProvider
import dan.sonora.domain.stats.UsernameConnectableProvider
import dan.sonora.icons.Icons
import dan.sonora.icons.brand.Lastfm
import dan.sonora.icons.brand.Musicbrainz

/**
 * The Insights empty state: one button per registered provider.
 *
 * The list is generated from the registry, and each button's flow comes from the
 * provider's own capabilities — a browser handshake when it exposes an authorization
 * URL, a username dialog when it connects by name. Nothing here names a provider,
 * so registering another one surfaces it without touching this file.
 */
@Composable
fun LazyItemScope.InsightsOnboarding(
	providers: List<StatsProvider>,
	onProviderSelected: (StatsProvider) -> Unit,
	onConnectWithUsername: suspend (StatsProvider, String, String) -> Unit,
	modifier: Modifier = Modifier
) {
	var usernameDialogProvider by remember { mutableStateOf<StatsProvider?>(null) }

	Column(
		modifier = modifier
			.fillParentMaxSize()
			.padding(horizontal = 24.dp)
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			contentAlignment = Alignment.Center
		) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(
					text = "See your listening stats",
					style = MaterialTheme.typography.headlineLarge,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center
				)
				Spacer(modifier = Modifier.height(16.dp))
				Text(
					text = "Track what you listen to and discover new music based on your taste.",
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
					textAlign = TextAlign.Center,
					maxLines = 2
				)
			}
		}

		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			providers.forEachIndexed { index, provider ->
				val onClick = {
					if (provider is UsernameConnectableProvider) {
						usernameDialogProvider = provider
					} else {
						onProviderSelected(provider)
					}
				}
				// The first provider carries the filled emphasis; the rest are tonal.
				if (index == 0) {
					Button(
						onClick = onClick,
						modifier = Modifier
							.fillMaxWidth()
							.height(56.dp),
						shape = RoundedCornerShape(28.dp)
					) {
						ProviderButtonContent(provider)
					}
				} else {
					FilledTonalButton(
						onClick = onClick,
						modifier = Modifier
							.fillMaxWidth()
							.height(56.dp),
						shape = RoundedCornerShape(28.dp),
						colors = ButtonDefaults.filledTonalButtonColors(
							containerColor = MaterialTheme.colorScheme.surfaceVariant,
							contentColor = MaterialTheme.colorScheme.onSurfaceVariant
						)
					) {
						ProviderButtonContent(provider)
					}
				}
			}
		}
	}

	usernameDialogProvider?.let { provider ->
		UsernameConnectDialog(
			provider = provider,
			defaultServerUrl = (provider as UsernameConnectableProvider).defaultServerUrl,
			onDismissRequest = { usernameDialogProvider = null },
			onConnect = { username, serverUrl -> onConnectWithUsername(provider, username, serverUrl) },
			onConnected = { usernameDialogProvider = null }
		)
	}
}

@Composable
private fun RowScope.ProviderButtonContent(provider: StatsProvider) {
	ProviderIcon(provider)
	Spacer(modifier = Modifier.width(8.dp))
	Text("Continue with ${provider.displayName}")
}

/**
 * Providers ship with a brand mark where one exists. An unrecognised provider still
 * gets a working button, just without an icon.
 */
@Composable
private fun ProviderIcon(provider: StatsProvider) {
	val icon = when (provider.id) {
		"lastfm" -> Icons.Brand.Lastfm
		"listenbrainz" -> Icons.Brand.Musicbrainz
		else -> null
	}
	icon?.let {
		Icon(
			imageVector = it,
			contentDescription = null,
			modifier = Modifier.size(24.dp)
		)
	}
}
