package dan.sonora.domain.manager

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dan.sonora.domain.manager.base.BasePreferenceManager
import dan.sonora.domain.models.settings.AnimationStyle
import dan.sonora.domain.models.settings.AppIconVariant
import dan.sonora.domain.models.settings.BottomBarCollapseMode
import dan.sonora.domain.models.settings.BottomBarVisibilityMode
import dan.sonora.domain.models.settings.CoverArtQuality
import dan.sonora.domain.models.settings.CoverArtShape
import dan.sonora.domain.models.settings.ExplicitContentPlayback
import dan.sonora.domain.models.settings.EqualizerSettings
import dan.sonora.domain.models.settings.FiveBandGainsDb
import dan.sonora.domain.models.settings.FontOption
import dan.sonora.domain.models.settings.GridSize
import dan.sonora.domain.models.settings.LimiterSettings
import dan.sonora.domain.models.settings.InsightsCalendarMode
import dan.sonora.domain.models.settings.MarqueeSpeed
import dan.sonora.domain.models.settings.MiniPlayerProgressStyle
import dan.sonora.domain.models.settings.MiniPlayerStyle
import dan.sonora.domain.models.settings.NavigationBarLabelVisibility
import dan.sonora.domain.models.settings.NavigationBarStyle
import dan.sonora.domain.models.settings.NowPlayingBackgroundStyle
import dan.sonora.domain.models.settings.NowPlayingSliderStyle
import dan.sonora.domain.models.settings.OfflineMode
import dan.sonora.domain.models.settings.ReplayGainMode
import dan.sonora.domain.models.settings.StreamingQuality
import dan.sonora.domain.models.settings.Theme
import dan.sonora.domain.models.settings.ThemeMode
import dan.sonora.domain.models.settings.ToolbarPosition
import com.russhwolf.settings.Settings as KmpSettings

class PreferenceManager(
	settings: KmpSettings
) : BasePreferenceManager(settings) {
	var appIconVariant by preference(AppIconVariant.Default)
	var font by preference(FontOption.GoogleSans)
	var fontPath by preference("")
	var animationStyle by preference(AnimationStyle.Expressive)
	var nowPlayingBackgroundStyle by preference(NowPlayingBackgroundStyle.Dynamic)
	var swipeToSkip by preference(true)
	var gridSize by preference(GridSize.TwoByTwo)
	var playlistIsListMode by preference(false)
	var coverArtShape by preference(CoverArtShape.Soft)
	var artistImageShape by preference(CoverArtShape.Soft)
	var coverArtQuality by preference(CoverArtQuality.High)
	var artGridItemSize by preference(150f)
	var marqueeSpeed by preference(MarqueeSpeed.Slow)
	var alphabeticalScroll by preference(false)
	var lyricsAutoscroll by preference(true)
	var lyricsBeatByBeat by preference(true)
	var lyricsKeepAlive by preference(true)
	var lyricsBlur by preference(false)
	var lyricsBrightInactive by preference(false)
	var enableScrobbling by preference(true)
	var enableLocalMusic by preference(false)
	/** Id of the active Insights [dan.sonora.domain.stats.StatsProvider]; empty means none. */
	var activeStatsProvider by preference("")
	var scrobblePercentage by preference(.5f)
	var minDurationToScrobble by preference(30f)
	var replayGainMode by preference(ReplayGainMode.Off)
	var equalizerEnabled by preference(false)
	var equalizerPreampDb by preference(0f)
	var equalizerBassDb by preference(0f)
	var equalizerTrebleDb by preference(0f)
	var equalizerBand1Db by preference(0f)
	var equalizerBand2Db by preference(0f)
	var equalizerBand3Db by preference(0f)
	var equalizerBand4Db by preference(0f)
	var equalizerBand5Db by preference(0f)
	var limiterEnabled by preference(false)
	var limiterThresholdDb by preference(LimiterSettings.DEFAULT_THRESHOLD_DB)
	val equalizerSettings: EqualizerSettings
		get() = EqualizerSettings(
			enabled = equalizerEnabled,
			preampDb = equalizerPreampDb,
			bassDb = equalizerBassDb,
			trebleDb = equalizerTrebleDb,
			fiveBandGainsDb = FiveBandGainsDb(
				band1Db = equalizerBand1Db,
				band2Db = equalizerBand2Db,
				band3Db = equalizerBand3Db,
				band4Db = equalizerBand4Db,
				band5Db = equalizerBand5Db,
			),
			limiter = LimiterSettings(
				enabled = limiterEnabled,
				thresholdDb = limiterThresholdDb,
			),
		)
	var gaplessPlayback by preference(true)
	var audioOffload by preference(false)
	var autoFillQueue by preference(false)
	/**
	 * Crossfade duration in seconds. 0 means crossfade is disabled.
	 */
	var crossfadeDuration by preference(0)

	var streamingQualityWifi by preference(StreamingQuality.Lossless)
	var streamingQualityCellular by preference(StreamingQuality.Lossless)
	var isAdvancedTranscodingActive by preference(false)
	var customMaxBitrateWifi by preference(0)
	var customMaxBitrateCellular by preference(0)
	var customFormatWifi by preference("")
	var customFormatCellular by preference("")

	var downloadQualityWifi by preference(StreamingQuality.Lossless)
	var downloadQualityCellular by preference(StreamingQuality.Lossless)
	var isAdvancedDownloadTranscodingActive by preference(false)
	var customDownloadMaxBitrateWifi by preference(0)
	var customDownloadMaxBitrateCellular by preference(0)
	var customDownloadFormatWifi by preference("")
	var customDownloadFormatCellular by preference("")
	var autoCacheStarredWifi by preference(true)
	var autoCachePlayedSongs by preference(true)
	var maxMediaCacheSizeMb by preference(2048L)

	var nowPlayingToolbarPosition by preference(ToolbarPosition.Bottom)
	var nowPlayingSongInfo by preference(true)
	var nowPlayingSliderStyle by preference(NowPlayingSliderStyle.Squiggly)
	var customHeaders by preference("")
	var explicitContentPlayback by preference(ExplicitContentPlayback.Allowed)

	// navigation bar settings
	var bottomBarCollapseMode by preference(BottomBarCollapseMode.OnScroll)
	var bottomBarVisibilityMode by preference(BottomBarVisibilityMode.AllScreens)
	var navigationBarStyle by preference(NavigationBarStyle.Normal)
	var navigationBarLabelVisibility by preference(
		NavigationBarLabelVisibility.Always
	)
	var miniPlayerStyle by preference(MiniPlayerStyle.Detached)
	var miniPlayerProgressStyle by preference(MiniPlayerProgressStyle.Seekable)
	var insightsCalendarMode by preference(InsightsCalendarMode.Month)

	/**
	 * If we have informed the user (on Android) about
	 * Google locking down sideloading.
	 */
	var showedSideloadingWarning by preference(false)
	/** Set after the optional server and local-music first-run flow has been completed. */
	var onboardingCompleted by preference(false)

	// theme related settings
	var theme by preference(Theme.Dynamic)
	var themeMode by preference(ThemeMode.System)
	var dynamicTheming by preference(false)
	var paletteStyle by preference(PaletteStyle.TonalSpot)
	var paletteSpec by preference(ColorSpec.SpecVersion.SPEC_2025)
	var paletteAccentH by preference(0f)

	// sync related settings
	var lastFullSyncTime by preference(0L)
	var insightsAutoSyncEnabled by preference(true)
	var insightsAutoSyncIntervalMinutes by preference(30L)

	fun customHeadersMap(): Map<String, String> = buildMap {
		for (line in customHeaders.lines()) {
			val parts = line.split(":", limit = 2)
			if (parts.size < 2) continue

			val rawKey = parts[0]
			val rawValue = parts[1]

			val key = rawKey.trim()
			val value = rawValue.trim()
			if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
		}
	}

	var offlineMode by preference(OfflineMode.Auto)
}
