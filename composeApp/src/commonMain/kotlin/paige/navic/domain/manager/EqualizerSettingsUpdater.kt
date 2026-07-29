package paige.navic.domain.manager

import paige.navic.domain.models.settings.EqualizerSettings

/** Accepts immutable Equalizer settings snapshots from settings UI or persistence bridges. */
fun interface EqualizerSettingsUpdater {
	fun update(settings: EqualizerSettings)
}
