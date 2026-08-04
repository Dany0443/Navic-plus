package dan.sonora.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.subtitle_about
import sonora.composeapp.generated.resources.subtitle_appearance
import sonora.composeapp.generated.resources.subtitle_bottom_app_bar
import sonora.composeapp.generated.resources.subtitle_data_storage
import sonora.composeapp.generated.resources.subtitle_developer
import sonora.composeapp.generated.resources.subtitle_insights_settings
import sonora.composeapp.generated.resources.subtitle_now_playing
import sonora.composeapp.generated.resources.subtitle_playback
import sonora.composeapp.generated.resources.title_about
import sonora.composeapp.generated.resources.title_appearance
import sonora.composeapp.generated.resources.title_bottom_app_bar
import sonora.composeapp.generated.resources.title_data_storage
import sonora.composeapp.generated.resources.title_developer
import sonora.composeapp.generated.resources.title_insights_settings
import sonora.composeapp.generated.resources.title_now_playing
import sonora.composeapp.generated.resources.title_playback
import sonora.composeapp.generated.resources.title_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import dan.sonora.LocalNavStack
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.icons.Icons
import dan.sonora.icons.filled.BottomNavigation
import dan.sonora.icons.filled.Info
import dan.sonora.icons.filled.Palette
import dan.sonora.icons.filled.Play
import dan.sonora.icons.outlined.ChevronForward
import dan.sonora.icons.outlined.Code
import dan.sonora.icons.outlined.DataTable
import dan.sonora.icons.outlined.History
import dan.sonora.icons.outlined.Note
import dan.sonora.ui.components.common.Form
import dan.sonora.ui.components.common.FormRow
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.theme.defaultFont

@Composable
fun SettingsScreen() {
	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.title_settings)) }) }
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp)
		) {
			Form {
				PageRow(
					destination = Screen.Settings.Appearance,
					icon = Icons.Filled.Palette,
					iconSize = 24.dp,
					title = Res.string.title_appearance,
					subtitle = Res.string.subtitle_appearance
				)
				PageRow(
					destination = Screen.Settings.NowPlaying,
					icon = Icons.Filled.Play,
					iconSize = 24.dp,
					title = Res.string.title_now_playing,
					subtitle = Res.string.subtitle_now_playing
				)
				PageRow(
					destination = Screen.Settings.BottomAppBar,
					icon = Icons.Filled.BottomNavigation,
					iconSize = 24.dp,
					title = Res.string.title_bottom_app_bar,
					subtitle = Res.string.subtitle_bottom_app_bar
				)
				PageRow(
					destination = Screen.Settings.Playback,
					icon = Icons.Outlined.Note,
					iconSize = 24.dp,
					title = Res.string.title_playback,
					subtitle = Res.string.subtitle_playback
				)
				PageRow(
					destination = Screen.Settings.Insights,
					icon = Icons.Outlined.History,
					iconSize = 24.dp,
					title = Res.string.title_insights_settings,
					subtitle = Res.string.subtitle_insights_settings
				)
				PageRow(
					destination = Screen.Settings.DataStorage,
					icon = Icons.Outlined.DataTable,
					iconSize = 24.dp,
					title = Res.string.title_data_storage,
					subtitle = Res.string.subtitle_data_storage
				)
				PageRow(
					destination = Screen.Settings.Developer,
					icon = Icons.Outlined.Code,
					iconSize = 24.dp,
					title = Res.string.title_developer,
					subtitle = Res.string.subtitle_developer
				)
			}
			Form {
				PageRow(
					destination = Screen.Settings.About,
					icon = Icons.Filled.Info,
					title = Res.string.title_about,
					subtitle = Res.string.subtitle_about
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageRow(
	destination: Screen? = null,
	icon: ImageVector,
	iconSize: Dp = 22.dp,
	title: StringResource,
	subtitle: StringResource
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	FormRow(
		onClick = dropUnlessResumed {
			destination?.let { destination ->
				backStack.lastOrNull()?.let {
					if (it is Screen.Settings) {
						if (it !is Screen.Settings.Root) {
							backStack.removeLastOrNull()
						}
						backStack.add(destination)
					}
				}
			}
		},
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(if (preferenceManager.theme.isMaterialLike()) 16.dp else 12.dp)
	) {
		if (preferenceManager.theme.isMaterialLike()) {
			Column(
				modifier = Modifier
					.size(40.dp)
					.background(MaterialTheme.colorScheme.primary, CircleShape),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Icon(
					icon,
					contentDescription = null,
					modifier = Modifier.size(iconSize),
					tint = MaterialTheme.colorScheme.onPrimary
				)
			}
		} else {
			Icon(
				icon,
				contentDescription = null,
				modifier = Modifier.padding(start = 8.dp, end = 5.dp).size(22.dp),
				tint = MaterialTheme.colorScheme.primary
			)
		}
		Column(
			Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(1.dp)
		) {
			Text(
				stringResource(title),
				style = MaterialTheme.typography.titleSmall.copy(
					fontFamily = defaultFont(100),
					fontSize = 16.sp,
					lineHeight = 16.sp
				)
			)
			Text(
				stringResource(subtitle),
				style = MaterialTheme.typography.bodyMedium.copy(
					fontFamily = defaultFont(grade = 10),
					lineHeight = 14.sp
				),
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		if (!preferenceManager.theme.isMaterialLike()) {
			Icon(
				Icons.Outlined.ChevronForward,
				null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
