package dan.sonora.shared.playback

import java.util.concurrent.atomic.AtomicReference
import dan.sonora.domain.manager.EqualizerSettingsUpdater
import dan.sonora.domain.models.settings.EqualizerSettings

/**
 * Shares immutable DSP settings with both audio threads without sharing processor state.
 */
class EqualizerSettingsProvider(
	initialSettings: EqualizerSettings = EqualizerSettings(),
) : EqualizerSettingsUpdater {
	private val settings = AtomicReference(initialSettings)

	fun snapshot(): EqualizerSettings = settings.get()

	override fun update(settings: EqualizerSettings) {
		this.settings.set(settings)
	}
}
