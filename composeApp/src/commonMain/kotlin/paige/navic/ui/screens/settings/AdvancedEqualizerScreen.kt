package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_equalizer_band_14khz
import navic.composeapp.generated.resources.option_equalizer_band_230hz
import navic.composeapp.generated.resources.option_equalizer_band_3600hz
import navic.composeapp.generated.resources.option_equalizer_band_60hz
import navic.composeapp.generated.resources.option_equalizer_band_910hz
import navic.composeapp.generated.resources.title_advanced_equalizer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.EqualizerSettingsUpdater
import paige.navic.domain.manager.PreferenceManager
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.util.core.PlatformType
import kotlin.math.roundToInt

@Composable
fun SettingsAdvancedEqualizerScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val equalizerSettingsUpdater = if (platformContext.platformType == PlatformType.Android) {
		koinInject<EqualizerSettingsUpdater>()
	} else {
		null
	}
	val publishSettings = { equalizerSettingsUpdater?.update(preferenceManager.equalizerSettings) }

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.title_advanced_equalizer)) }) }
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp)
			) {
				Form {
					EqualizerBandSlider(
						title = stringResource(Res.string.option_equalizer_band_60hz),
						value = preferenceManager.equalizerBand1Db,
						onValueChange = {
							preferenceManager.equalizerBand1Db = it
							publishSettings()
						},
					)
					EqualizerBandSlider(
						title = stringResource(Res.string.option_equalizer_band_230hz),
						value = preferenceManager.equalizerBand2Db,
						onValueChange = {
							preferenceManager.equalizerBand2Db = it
							publishSettings()
						},
					)
					EqualizerBandSlider(
						title = stringResource(Res.string.option_equalizer_band_910hz),
						value = preferenceManager.equalizerBand3Db,
						onValueChange = {
							preferenceManager.equalizerBand3Db = it
							publishSettings()
						},
					)
					EqualizerBandSlider(
						title = stringResource(Res.string.option_equalizer_band_3600hz),
						value = preferenceManager.equalizerBand4Db,
						onValueChange = {
							preferenceManager.equalizerBand4Db = it
							publishSettings()
						},
					)
					EqualizerBandSlider(
						title = stringResource(Res.string.option_equalizer_band_14khz),
						value = preferenceManager.equalizerBand5Db,
						onValueChange = {
							preferenceManager.equalizerBand5Db = it
							publishSettings()
						},
					)
				}
			}
		}
	}
}

@Composable
private fun EqualizerBandSlider(
	title: String,
	value: Float,
	onValueChange: (Float) -> Unit,
) {
	FormRow {
		Column(Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(title)
				Text(
					text = formatDecibels(value),
					fontFamily = FontFamily.Monospace,
					fontWeight = FontWeight(400),
					fontSize = 13.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Slider(
				value = value,
				onValueChange = onValueChange,
				valueRange = MIN_BAND_GAIN_DB..MAX_BAND_GAIN_DB,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					text = formatDecibels(MIN_BAND_GAIN_DB),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = formatDecibels(MAX_BAND_GAIN_DB),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

private fun formatDecibels(value: Float): String {
	val roundedValue = value.roundToInt()
	return "${if (roundedValue > 0) "+" else ""}$roundedValue dB"
}

private const val MIN_BAND_GAIN_DB = -10f
private const val MAX_BAND_GAIN_DB = 10f
