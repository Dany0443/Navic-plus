package paige.navic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_audio_offload
import navic.composeapp.generated.resources.option_advanced_equalizer
import navic.composeapp.generated.resources.option_crossfade
import navic.composeapp.generated.resources.option_crossfade_off
import navic.composeapp.generated.resources.option_enable_equalizer

import navic.composeapp.generated.resources.option_enable_loudness_protection
import navic.composeapp.generated.resources.option_equalizer_bass
import navic.composeapp.generated.resources.option_equalizer_preamp
import navic.composeapp.generated.resources.option_equalizer_treble
import navic.composeapp.generated.resources.option_loudness_protection_threshold
import navic.composeapp.generated.resources.subtitle_crossfade
import navic.composeapp.generated.resources.option_enable_local_music
import navic.composeapp.generated.resources.option_enable_scrobbling
import navic.composeapp.generated.resources.option_explicit_playback
import navic.composeapp.generated.resources.option_gapless_playback
import navic.composeapp.generated.resources.option_min_duration_to_scrobble
import navic.composeapp.generated.resources.option_replay_gain
import navic.composeapp.generated.resources.option_scrobble_percentage
import navic.composeapp.generated.resources.subtitle_audio_offload
import navic.composeapp.generated.resources.subtitle_enable_local_music
import navic.composeapp.generated.resources.subtitle_enable_scrobbling
import navic.composeapp.generated.resources.subtitle_gapless_playback
import navic.composeapp.generated.resources.subtitle_local_music_permission_needed
import navic.composeapp.generated.resources.subtitle_local_music_permission_permanently_denied
import navic.composeapp.generated.resources.subtitle_streaming_quality
import navic.composeapp.generated.resources.action_open_settings
import navic.composeapp.generated.resources.title_behaviour
import navic.composeapp.generated.resources.title_equalizer
import navic.composeapp.generated.resources.title_loudness_protection
import navic.composeapp.generated.resources.subtitle_loudness_protection
import navic.composeapp.generated.resources.title_local_music
import navic.composeapp.generated.resources.title_playback
import navic.composeapp.generated.resources.title_streaming_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PermissionManager
import paige.navic.domain.manager.PermissionRequestResult
import paige.navic.domain.manager.EqualizerSettingsUpdater
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.ExplicitContentPlayback
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.models.settings.LimiterSettings
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.util.core.PlatformType
import kotlin.math.roundToInt

@Composable
fun SettingsPlaybackScreen() {
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val permissionManager = koinInject<PermissionManager>()
	val equalizerSettingsUpdater = if (platformContext.platformType == PlatformType.Android) {
		koinInject<EqualizerSettingsUpdater>()
	} else {
		null
	}
	val scope = rememberCoroutineScope()
	val deniedLocalMusicMessage = stringResource(Res.string.subtitle_local_music_permission_needed)
	val permanentlyDeniedLocalMusicMessage = stringResource(Res.string.subtitle_local_music_permission_permanently_denied)
	var pendingLocalMusicRequest by remember { mutableStateOf(false) }
	var localMusicDenialState by remember { mutableStateOf<LocalMusicDenialState?>(null) }
	val publishEqualizerSettings = {
		equalizerSettingsUpdater?.update(preferenceManager.equalizerSettings)
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_playback)) },
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
					FormRow(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.StreamingQuality) },
						horizontalArrangement = Arrangement.Start
					) {
						Column(Modifier.weight(1f)) {
							Text(stringResource(Res.string.title_streaming_quality))
							Text(
								text = stringResource(Res.string.subtitle_streaming_quality),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Icon(Icons.Outlined.ChevronForward, null)
					}
					if (platformContext.platformType == PlatformType.Android) {
						SettingSelectionRow(
							title = { Text(stringResource(Res.string.option_replay_gain)) },
							items = ReplayGainMode.entries.toImmutableList(),
							label = { stringResource(it.displayName) },
							selection = preferenceManager.replayGainMode,
							onSelect = { preferenceManager.replayGainMode = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_gapless_playback)) },
							subtitle = { Text(stringResource(Res.string.subtitle_gapless_playback)) },
							value = preferenceManager.gaplessPlayback,
							onSetValue = { preferenceManager.gaplessPlayback = it }
						)
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_audio_offload)) },
							subtitle = { Text(stringResource(Res.string.subtitle_audio_offload)) },
							value = preferenceManager.audioOffload,
							onSetValue = { preferenceManager.audioOffload = it }
						)
						FormRow {
							Column(Modifier.fillMaxWidth()) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween
								) {
									Text(stringResource(Res.string.option_crossfade))
									Text(
										if (preferenceManager.crossfadeDuration == 0) stringResource(Res.string.option_crossfade_off)
										else "${preferenceManager.crossfadeDuration}s",
										fontFamily = FontFamily.Monospace,
										fontWeight = FontWeight(400),
										fontSize = 13.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								}
								Slider(
									value = preferenceManager.crossfadeDuration.toFloat(),
									onValueChange = {
										preferenceManager.crossfadeDuration = it.toInt()
									},
									valueRange = 0f..12f,
									steps = 11
								)
								Text(
									text = stringResource(Res.string.subtitle_crossfade),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_explicit_playback)) },
						label = { stringResource(it.displayName) },
						items = ExplicitContentPlayback.entries.toImmutableList(),
						selection = preferenceManager.explicitContentPlayback,
						onSelect = { preferenceManager.explicitContentPlayback = it }
					)
				}

				if (platformContext.platformType == PlatformType.Android) {
					FormTitle(stringResource(Res.string.title_equalizer))
					Form(bottomPadding = 0.dp) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_enable_equalizer)) },
							value = preferenceManager.equalizerEnabled,
							onSetValue = {
								preferenceManager.equalizerEnabled = it
								publishEqualizerSettings()
							}
						)
					}
					AnimatedVisibility(visible = preferenceManager.equalizerEnabled) {
						Form {
							EqualizerSlider(
								title = stringResource(Res.string.option_equalizer_preamp),
								value = preferenceManager.equalizerPreampDb,
								onValueChange = {
									preferenceManager.equalizerPreampDb = it
									publishEqualizerSettings()
								},
								valueRange = -12f..12f
							)
							EqualizerSlider(
								title = stringResource(Res.string.option_equalizer_bass),
								value = preferenceManager.equalizerBassDb,
								onValueChange = {
									preferenceManager.equalizerBassDb = it
									publishEqualizerSettings()
								},
								valueRange = -10f..10f
							)
							EqualizerSlider(
								title = stringResource(Res.string.option_equalizer_treble),
								value = preferenceManager.equalizerTrebleDb,
								onValueChange = {
									preferenceManager.equalizerTrebleDb = it
									publishEqualizerSettings()
								},
								valueRange = -10f..10f
							)
							FormRow(
								onClick = dropUnlessResumed { backStack.add(Screen.Settings.AdvancedEqualizer) }
							) {
								Text(stringResource(Res.string.option_advanced_equalizer))
								Icon(Icons.Outlined.ChevronForward, contentDescription = null)
							}
						}
					}

					FormTitle(stringResource(Res.string.title_loudness_protection))
					Form {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_enable_loudness_protection)) },
							subtitle = { Text(stringResource(Res.string.subtitle_loudness_protection)) },
							value = preferenceManager.limiterEnabled,
							onSetValue = {
								preferenceManager.limiterEnabled = it
								publishEqualizerSettings()
							},
						)
						AnimatedVisibility(visible = preferenceManager.limiterEnabled) {
							EqualizerSlider(
								title = stringResource(Res.string.option_loudness_protection_threshold),
								value = preferenceManager.limiterThresholdDb,
								onValueChange = {
									preferenceManager.limiterThresholdDb = it
									publishEqualizerSettings()
								},
								valueRange = LimiterSettings.MIN_THRESHOLD_DB..LimiterSettings.MAX_THRESHOLD_DB,
							)
						}
					}
				}

				FormTitle(stringResource(Res.string.title_behaviour))
				Form {
					if (platformContext.platformType == PlatformType.Android) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_enable_local_music)) },
							subtitle = { Text(stringResource(Res.string.subtitle_enable_local_music)) },
							value = preferenceManager.enableLocalMusic,
							onSetValue = {
								if (!it) {
									preferenceManager.enableLocalMusic = false
									return@SettingSwitchRow
								}
								pendingLocalMusicRequest = true
								scope.launch {
									val result = permissionManager.requestLocalMusicPermission()
									when (result) {
										PermissionRequestResult.Granted -> preferenceManager.enableLocalMusic = true
										PermissionRequestResult.Denied -> localMusicDenialState = LocalMusicDenialState.Denied(deniedLocalMusicMessage)
										PermissionRequestResult.PermanentlyDenied -> localMusicDenialState = LocalMusicDenialState.PermanentlyDenied(permanentlyDeniedLocalMusicMessage)
									}
									pendingLocalMusicRequest = false
								}
							}
						)
					}
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_enable_scrobbling)) },
						subtitle = { Text(stringResource(Res.string.subtitle_enable_scrobbling)) },
						value = preferenceManager.enableScrobbling,
						onSetValue = { preferenceManager.enableScrobbling = it }
					)

					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(Res.string.option_scrobble_percentage))
								Text(
									"${(preferenceManager.scrobblePercentage * 100).roundToInt()}%",
									fontFamily = FontFamily.Monospace,
									fontWeight = FontWeight(400),
									fontSize = 13.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Slider(
								value = preferenceManager.scrobblePercentage,
								onValueChange = {
									preferenceManager.scrobblePercentage = it
								},
								valueRange = 0f..1f,
							)
						}
					}
					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween
							) {
								Text(stringResource(Res.string.option_min_duration_to_scrobble))
								Text(
									"${preferenceManager.minDurationToScrobble.toInt()}s",
									fontFamily = FontFamily.Monospace,
									fontWeight = FontWeight(400),
									fontSize = 13.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Slider(
								value = preferenceManager.minDurationToScrobble,
								onValueChange = {
									preferenceManager.minDurationToScrobble = it
								},
								valueRange = 0f..400f,
							)
						}
					}
				}
			}
		}
	}

	if (localMusicDenialState != null) {
		FormDialog(
			onDismissRequest = { localMusicDenialState = null },
			title = { Text(stringResource(Res.string.title_local_music)) },
			content = { Text(localMusicDenialState?.message.orEmpty()) },
			buttons = {
				if (localMusicDenialState is LocalMusicDenialState.PermanentlyDenied) {
					FormButton(
						onClick = {
							localMusicDenialState = null
							permissionManager.openPermissionsSettings()
						}
					) {
						Text(stringResource(Res.string.action_open_settings))
					}
				}
			}
		)
	}
}

@Composable
private fun EqualizerSlider(
	title: String,
	value: Float,
	onValueChange: (Float) -> Unit,
	valueRange: ClosedFloatingPointRange<Float>,
) {
	FormRow {
		Column(Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
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
				valueRange = valueRange,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					text = formatDecibels(valueRange.start),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = formatDecibels(valueRange.endInclusive),
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

private sealed class LocalMusicDenialState(val message: String) {
	class Denied(message: String) : LocalMusicDenialState(message)
	class PermanentlyDenied(message: String) : LocalMusicDenialState(message)
}
