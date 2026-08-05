package dan.sonora.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.title_insights
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import dan.sonora.LocalBottomBarScrollManager
import dan.sonora.LocalNavStack
import dan.sonora.ui.components.layouts.RootBottomBar
import dan.sonora.ui.components.layouts.RootTopBar
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.stats.components.*
import dan.sonora.ui.screens.stats.viewmodels.InsightsUiState
import dan.sonora.ui.screens.stats.viewmodels.InsightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(nested: Boolean = false) {
	val viewModel = koinViewModel<InsightsViewModel>()
	val player = koinInject<dan.sonora.shared.MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val hasMiniPlayer = playerState.currentSong != null
	
	val state by viewModel.state.collectAsStateWithLifecycle()
	val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle(initialValue = false)
	val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle(initialValue = 0f)
	
	val uriHandler = LocalUriHandler.current
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val scrollManager = LocalBottomBarScrollManager.current
	val backStack = LocalNavStack.current

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(stringResource(Res.string.title_insights)) },
				scrollBehavior = scrollBehavior
			)
		},
		bottomBar = {
			RootBottomBar(scrollManager.isTriggered)
		}
	) { paddingValues ->
		val bottomPadding = if (state is InsightsUiState.NoProvider) {
			if (hasMiniPlayer) 24.dp else 0.dp
		} else {
			32.dp
		}
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
			verticalArrangement = Arrangement.spacedBy(26.dp)
		) {
			when (val currentState = state) {
				InsightsUiState.NoProvider -> item {
					InsightsOnboarding(
						providers = viewModel.providers,
						onProviderSelected = { provider ->
							provider.authorizationUrl?.let(uriHandler::openUri)
						},
						onConnectWithUsername = { provider, username, serverUrl ->
							viewModel.connectWithUsername(provider.id, username, serverUrl)
						}
					)
				}
				InsightsUiState.Loading -> item {
					Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
						CircularProgressIndicator()
					}
				}
				is InsightsUiState.Error -> item {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Unable to load Insights")
						currentState.exception.message?.let { message ->
							Text(
								text = message,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Button(onClick = viewModel::refreshData) { Text("Retry") }
					}
				}
				is InsightsUiState.Success -> {
					item {
						HeroStatCard(
							streakDays = currentState.streakDays,
							totalScrobbles = currentState.totalLocalScrobbles,
							scrobblesToday = currentState.scrobblesToday,
							onSyncClick = { viewModel.triggerSync() },
							isSyncing = isSyncing,
							syncProgress = syncProgress
						) 
					}

					item {
						TopArtistsCard(
							artists = currentState.topArtists,
							artistCoverArtIds = currentState.artistCoverArtIds,
							artistIds = currentState.artistIds,
							providerName = currentState.providerName
						)
					}
					
					item {
						HeatmapCard(
							scrobbles = currentState.recentScrobbles,
							activityByDate = currentState.activityByDate,
							listeningSecondsByDate = currentState.listeningSecondsByDate
						)
					}

					item {
						AlbumWallCard(
							albums = currentState.topAlbums,
							albumCoverArtIds = currentState.albumCoverArtIds,
							albumIds = currentState.albumIds
						)
					}

					item {
						TopTracksCard(
							tracks = currentState.topTracks,
							trackCoverArtIds = currentState.trackCoverArtIds,
							trackIds = currentState.trackIds
						)
					}

					item {
						RecentRotationCard(
							artists = currentState.rotationArtists,
							tracks = currentState.rotationTracks,
							artistIds = currentState.artistIds,
							trackIds = currentState.trackIds,
							trackCoverArtIds = currentState.trackCoverArtIds
						)
					}
					
					item {
						RecentActivityCard(
							scrobbles = currentState.recentScrobbles.take(20),
							albumCoverArtIds = currentState.albumCoverArtIds,
							albumIds = currentState.albumIds,
							trackCoverArtIds = currentState.recentActivityTrackCoverArtIds,
							trackMatchDiagnostics = currentState.recentActivitySongMatchDiagnostics,
							artworkDiagnosticsReady = currentState.recentActivityArtworkReady
						)
					}

					item { MemoriesCard(scrobbles = currentState.recentScrobbles) }

					item {
						FilledTonalButton(
							onClick = dropUnlessResumed { backStack.add(Screen.AdvancedStatistics) },
							modifier = Modifier.fillMaxWidth()
						) {
							Text("View Advanced Statistics")
						}
					}
				}
			}
		}
	}
}
