package dan.sonora.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.info_app_version
import sonora.composeapp.generated.resources.title_about
import sonora.composeapp.generated.resources.title_acknowledgements
import sonora.composeapp.generated.resources.title_source
import org.jetbrains.compose.resources.stringResource
import dan.sonora.LocalNavStack
import dan.sonora.LocalPlatformContext
import dan.sonora.icons.Icons
import dan.sonora.icons.outlined.ChevronForward
import dan.sonora.ui.components.common.Form
import dan.sonora.ui.components.common.FormRow
import dan.sonora.ui.components.layouts.NestedTopBar
import dan.sonora.ui.navigation.Screen

@Composable
fun SettingsAboutScreen() {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val uriHandler = LocalUriHandler.current
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_about)) },
				hideBack = hideBack
			)
		}
	) { innerPadding ->
		Column(
			Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp)
		) {
			Form {
				SelectionContainer {
					val text = buildString {
						append(platformContext.name + "\n")
						append(
							stringResource(
								Res.string.info_app_version,
								platformContext.appVersion
							)
						)
					}
					FormRow(onClick = {
						clipboard.setText(AnnotatedString(text))
					}) {
						Text(text)
					}
				}
			}
			Form {
				FormRow(onClick = {
					uriHandler.openUri("https://github.com/Dany0443")
				}) {
					Text(stringResource(Res.string.title_source))
					Icon(Icons.Outlined.ChevronForward, null)
				}
				FormRow(onClick = dropUnlessResumed {
					backStack.add(Screen.Settings.Acknowledgements)
				}) {
					Text(stringResource(Res.string.title_acknowledgements))
					Icon(Icons.Outlined.ChevronForward, null)
				}
			}
		}
	}
}
