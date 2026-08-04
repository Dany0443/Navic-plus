package dan.sonora.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.option_bottom_bar_collapse_mode
import sonora.composeapp.generated.resources.option_bottom_bar_visibility_mode
import sonora.composeapp.generated.resources.option_mini_player_progress_style
import sonora.composeapp.generated.resources.option_mini_player_style
import sonora.composeapp.generated.resources.option_navigation_bar_label_visibility
import sonora.composeapp.generated.resources.option_navigation_bar_style
import sonora.composeapp.generated.resources.option_navigation_bar_tabs
import sonora.composeapp.generated.resources.option_swipe_to_skip
import sonora.composeapp.generated.resources.title_bottom_app_bar
import sonora.composeapp.generated.resources.title_mini_player
import sonora.composeapp.generated.resources.title_navigation_bar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import dan.sonora.LocalPlatformContext
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.models.settings.BottomBarCollapseMode
import dan.sonora.domain.models.settings.BottomBarVisibilityMode
import dan.sonora.domain.models.settings.MiniPlayerProgressStyle
import dan.sonora.domain.models.settings.MiniPlayerStyle
import dan.sonora.domain.models.settings.NavigationBarLabelVisibility
import dan.sonora.domain.models.settings.NavigationBarStyle
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.ChevronForward
import dan.sonora.ui.components.common.Form
import dan.sonora.ui.components.common.FormRow
import dan.sonora.ui.components.common.FormTitle
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.screens.settings.components.SettingSelectionRow
import dan.sonora.ui.screens.settings.components.SettingSwitchRow
import dan.sonora.ui.screens.settings.dialogs.NavtabsDialog

@Composable
fun BottomBarScreen() {
	val platformContext = LocalPlatformContext.current
	var showNavtabsDialog by rememberSaveable { mutableStateOf(false) }
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_bottom_app_bar)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp)
			) {
				Form {
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						value = preferenceManager.swipeToSkip,
						onSetValue = { preferenceManager.swipeToSkip = it }
					)

					SettingSelectionRow(
						items = BottomBarCollapseMode.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.bottomBarCollapseMode,
						onSelect = { preferenceManager.bottomBarCollapseMode = it },
						title = { Text(stringResource(Res.string.option_bottom_bar_collapse_mode)) },
					)

					SettingSelectionRow(
						items = BottomBarVisibilityMode.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.bottomBarVisibilityMode,
						onSelect = { preferenceManager.bottomBarVisibilityMode = it },
						title = { Text(stringResource(Res.string.option_bottom_bar_visibility_mode)) },
					)
				}

				FormTitle(stringResource(Res.string.title_navigation_bar))
				Form {
					SettingSelectionRow(
						items = NavigationBarStyle.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.navigationBarStyle,
						onSelect = { preferenceManager.navigationBarStyle = it },
						title = { Text(stringResource(Res.string.option_navigation_bar_style)) },
					)

					SettingSelectionRow(
						items = NavigationBarLabelVisibility.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.navigationBarLabelVisibility,
						onSelect = { preferenceManager.navigationBarLabelVisibility = it },
						title = { Text(stringResource(Res.string.option_navigation_bar_label_visibility)) },
					)

					FormRow(
						onClick = { showNavtabsDialog = true }
					) {
						Text(stringResource(Res.string.option_navigation_bar_tabs))
						Icon(Icons.Outlined.ChevronForward, null)
					}
				}

				FormTitle(stringResource(Res.string.title_mini_player))
				Form {
					SettingSelectionRow(
						items = MiniPlayerStyle.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.miniPlayerStyle,
						onSelect = { preferenceManager.miniPlayerStyle = it },
						title = { Text(stringResource(Res.string.option_mini_player_style)) },
					)

					SettingSelectionRow(
						items = MiniPlayerProgressStyle.entries.toImmutableList(),
						label = { stringResource(it.displayName) },
						selection = preferenceManager.miniPlayerProgressStyle,
						onSelect = { preferenceManager.miniPlayerProgressStyle = it },
						title = { Text(stringResource(Res.string.option_mini_player_progress_style)) },
					)
				}
			}
		}
		NavtabsDialog(
			presented = showNavtabsDialog,
			onDismissRequest = { showNavtabsDialog = false }
		)
	}
}
