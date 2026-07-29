package paige.navic.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.stats.components.AdvancedStatisticsSectionCard
import paige.navic.ui.screens.stats.viewmodels.AdvancedStatisticsUiState
import paige.navic.ui.screens.stats.viewmodels.AdvancedStatisticsViewModel

@Composable
fun AdvancedStatisticsScreen() {
	val viewModel = koinViewModel<AdvancedStatisticsViewModel>()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val uriHandler = LocalUriHandler.current

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text("Advanced Statistics") }
			)
		}
	) { paddingValues ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp)
		) {
			when (val currentState = state) {
				AdvancedStatisticsUiState.Unauthenticated -> item {
					Button(onClick = { uriHandler.openUri(viewModel.authorizationUrl) }) {
						Text("Connect to Last.fm")
					}
				}

				AdvancedStatisticsUiState.Loading -> item {
					Box(
						modifier = Modifier.fillMaxWidth(),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator()
					}
				}

				is AdvancedStatisticsUiState.Error -> item {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Unable to load Advanced Statistics")
						currentState.exception.message?.let { message ->
							Text(
								text = message,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Button(onClick = viewModel::refreshData) {
							Text("Retry")
						}
					}
				}

				is AdvancedStatisticsUiState.Success -> {
					currentState.sections.forEach { section ->
						item {
							AdvancedStatisticsSectionCard(section)
						}
					}
				}
			}
		}
	}
}
