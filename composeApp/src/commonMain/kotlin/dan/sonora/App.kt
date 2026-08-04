package dan.sonora

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import dan.sonora.di.initializeSingletonImageLoader
import dan.sonora.domain.manager.BottomBarScrollManager
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.manager.SnackBarManager
import dan.sonora.domain.models.settings.ExplicitContentPlayback
import dan.sonora.shared.MediaPlayerViewModel
import dan.sonora.ui.components.dialogs.SideloadingDialog
import dan.sonora.ui.components.snackbars.SonoraSnackBar
import dan.sonora.ui.navigation.BottomSheetSceneStrategy
import dan.sonora.ui.navigation.NowPlayingSceneStrategy
import dan.sonora.ui.navigation.Screen
import dan.sonora.ui.screens.album.AlbumListScreen
import dan.sonora.ui.screens.artist.ArtistDetailScreen
import dan.sonora.ui.screens.artist.ArtistListScreen
import dan.sonora.ui.screens.collection.CollectionDetailScreen
import dan.sonora.ui.screens.genre.GenreDetailScreen
import dan.sonora.ui.screens.genre.GenreListScreen
import dan.sonora.ui.screens.library.LibraryScreen
import dan.sonora.ui.screens.login.LoginScreen
import dan.sonora.ui.screens.lyrics.LyricsScreen
import dan.sonora.ui.screens.nowPlaying.NowPlayingScreen
import dan.sonora.ui.screens.nowPlaying.PlaybackSpeedScreen

import dan.sonora.ui.screens.onboarding.OnboardingScreen
import dan.sonora.ui.screens.playlist.PlaylistListScreen
import dan.sonora.ui.screens.queue.QueueScreen
import dan.sonora.ui.screens.radio.RadioListScreen
import dan.sonora.ui.screens.search.SearchScreen
import dan.sonora.ui.screens.settings.BottomBarScreen
import dan.sonora.ui.screens.settings.FontsScreen
import dan.sonora.ui.screens.settings.SettingsAboutScreen
import dan.sonora.ui.screens.settings.InsightsProviderScreen
import dan.sonora.ui.screens.settings.InsightsSettingsScreen
import dan.sonora.ui.screens.settings.SettingsAdvancedEqualizerScreen
import dan.sonora.ui.screens.settings.SettingsAcknowledgementsScreen
import dan.sonora.ui.screens.settings.SettingsAppIconScreen
import dan.sonora.ui.screens.settings.SettingsAppearanceScreen
import dan.sonora.ui.screens.settings.SettingsCustomHeadersScreen
import dan.sonora.ui.screens.settings.SettingsDataStorageScreen
import dan.sonora.ui.screens.settings.SettingsDeveloperScreen
import dan.sonora.ui.screens.settings.SettingsDownloadQualityScreen
import dan.sonora.ui.screens.settings.SettingsLogsScreen
import dan.sonora.ui.screens.settings.SettingsNowPlayingScreen
import dan.sonora.ui.screens.settings.SettingsPlaybackScreen
import dan.sonora.ui.screens.settings.SettingsScreen
import dan.sonora.ui.screens.settings.SettingsStreamingQualityScreen
import dan.sonora.ui.screens.settings.SettingsThemesScreen
import dan.sonora.ui.screens.share.ShareListScreen
import dan.sonora.ui.screens.song.SongDetailScreen
import dan.sonora.ui.screens.song.SongDetailSheet
import dan.sonora.ui.screens.song.SongListScreen
import dan.sonora.ui.screens.starred.StarredScreen
import dan.sonora.ui.screens.stats.AdvancedStatisticsScreen
import dan.sonora.ui.theme.SonoraTheme
import dan.sonora.util.core.PlatformContext
import dan.sonora.util.core.rememberPlatformContext
import dan.sonora.util.ui.Material3Transitions

@OptIn(ExperimentalSerializationApi::class)
private val config = SavedStateConfiguration {
	serializersModule = SerializersModule {
		polymorphic(NavKey::class) {
			subclassesOfSealed<Screen>()
		}
	}
}

val LocalPlatformContext =
	staticCompositionLocalOf<PlatformContext> { error("no platform context") }
val LocalNavStack = staticCompositionLocalOf<NavBackStack<NavKey>> { error("no backstack") }
val LocalSnackBarState = staticCompositionLocalOf<SnackbarHostState> { error("no snack bar state") }
val LocalSharedTransitionScope =
	staticCompositionLocalOf<SharedTransitionScope> { error("no shared transition scope") }

val LocalBottomBarScrollManager = staticCompositionLocalOf<BottomBarScrollManager> {
	error("No BottomBarScrollManager provided")
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
	setSingletonImageLoaderFactory { platformContext ->
		initializeSingletonImageLoader(platformContext)
	}

	val platformContext = rememberPlatformContext()
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	LaunchedEffect(isLoggedIn) {
		// Migration for installations that predate onboarding: an existing account or local
		// library means the user has already completed the old setup experience.
		if (!preferenceManager.onboardingCompleted &&
			(isLoggedIn || preferenceManager.enableLocalMusic)
		) {
			preferenceManager.onboardingCompleted = true
		}
	}
	val shouldShowOnboarding = !preferenceManager.onboardingCompleted &&
		!isLoggedIn && !preferenceManager.enableLocalMusic
	val backStack = rememberNavBackStack(
		config, if (shouldShowOnboarding) {
			Screen.Onboarding
		} else {
			Screen.Library()
		}
	)
	val snackBarState = remember { SnackbarHostState() }
	val snackBarManager = koinInject<SnackBarManager>()

	LaunchedEffect(Unit) {
		snackBarManager.events.collectLatest { event ->
			snackBarState.showSnackbar(getString(event.resource, *event.args.toTypedArray()))
		}
	}

	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val scrollManager = remember {
		BottomBarScrollManager(with(density) { 50.dp.toPx() })
	}

	var appStarted by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		if (!appStarted) {
			appStarted = true
			if (preferenceManager.explicitContentPlayback == ExplicitContentPlayback.SkipForThisSession) {
				preferenceManager.explicitContentPlayback = ExplicitContentPlayback.Allowed
			}
		}
	}

	SharedTransitionLayout {
		CompositionLocalProvider(
			LocalPlatformContext provides platformContext,
			LocalNavStack provides backStack,
			LocalSnackBarState provides snackBarState,
			LocalSharedTransitionScope provides this@SharedTransitionLayout,
			LocalBottomBarScrollManager provides scrollManager
		) {
			SonoraTheme {
				Scaffold(
					modifier = Modifier.nestedScroll(scrollManager.connection),
					snackbarHost = {
						SnackbarHost(hostState = snackBarState) { snackBarData ->
							SonoraSnackBar(snackBarData = snackBarData)
						}
					}
				) { contentPadding ->
					NavDisplay(
						modifier = Modifier
							.padding(
								start = contentPadding
									.calculateStartPadding(layoutDirection),
								end = contentPadding
									.calculateEndPadding(layoutDirection)
							)
							.fillMaxSize()
							.background(MaterialTheme.colorScheme.surface),
						backStack = backStack,
						sceneStrategies = listOf(
							remember { NowPlayingSceneStrategy() },
							remember { BottomSheetSceneStrategy() },
							rememberListDetailSceneStrategy()
						),
						entryDecorators = listOf(
							rememberSaveableStateHolderNavEntryDecorator(),

							// makes it so that ViewModels get destroyed if their
							// associated screen is removed from the back stack
							//
							// this might not always be desirable, so the
							// `PersistentViewModelStoreOwner` class is used for
							// certain ViewModels to work around this
							rememberViewModelStoreNavEntryDecorator()
						),
						onBack = {
							if (backStack.size >= 2) {
								backStack.removeLastOrNull()
							}
						},
						entryProvider = entryProvider(backStack),
						transitionSpec = {
							Material3Transitions.SharedXAxisEnterTransition(
								density
							) togetherWith Material3Transitions.SharedXAxisExitTransition(
								density
							)
						},
						popTransitionSpec = {
							Material3Transitions.SharedXAxisPopEnterTransition(
								density
							) togetherWith Material3Transitions.SharedXAxisPopExitTransition(
								density
							)
						},
						predictivePopTransitionSpec = {
							slideInHorizontally(
								animationSpec = tween(300, easing = EaseOutQuart),
								initialOffsetX = { -it }
							) togetherWith slideOutHorizontally(
								animationSpec = tween(300, easing = EaseOutQuart),
								targetOffsetX = { it }
							)
						}
					)
				}
				if (!preferenceManager.showedSideloadingWarning
					&& platformContext.name.lowercase().contains("android")
				) {
					SideloadingDialog()
				}
				// Automatic update checks and prompts are intentionally disabled for Sonora.
				// Reintroduce this only with a Sonora-owned update provider and user-facing flow.
			}
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
private fun entryProvider(
	backStack: NavBackStack<NavKey>
): (NavKey) -> (NavEntry<NavKey>) {
	val navtabMetadata = if (backStack.size == 1)
		listPane("root") + transitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		} + popTransitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		} + predictivePopTransitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		}
	else listPane("root")
	return androidx.navigation3.runtime.entryProvider {
		// tabs
		entry<Screen.Library>(metadata = navtabMetadata) {
			LibraryScreen()
		}
		entry<Screen.Starred>(metadata = navtabMetadata) {
			StarredScreen()
		}
		entry<Screen.AlbumList>(metadata = navtabMetadata) { key ->
			AlbumListScreen(key.nested, key.listType)
		}
		entry<Screen.PlaylistList>(metadata = navtabMetadata) { key ->
			PlaylistListScreen(key.nested)
		}
		entry<Screen.ArtistList>(metadata = navtabMetadata) { key ->
			ArtistListScreen(key.nested, key.listType)
		}
		entry<Screen.GenreList>(metadata = navtabMetadata) { key ->
			GenreListScreen(key.nested)
		}
		entry<Screen.GenreDetail> { key ->
			GenreDetailScreen(key.genreName)
		}
		entry<Screen.SongList>(metadata = navtabMetadata) { key ->
			SongListScreen(key.nested, key.listType)
		}

		entry<Screen.RadioList>(metadata = navtabMetadata) { key ->
			RadioListScreen(key.nested)
		}

		entry<Screen.Insights>(metadata = navtabMetadata) { key ->
			dan.sonora.ui.screens.stats.InsightsScreen(key.nested)
		}
		entry<Screen.AdvancedStatistics> {
			AdvancedStatisticsScreen()
		}

		// misc
		entry<Screen.Onboarding> {
			OnboardingScreen()
		}
		entry<Screen.Login> {
			LoginScreen()
		}
		entry<Screen.NowPlaying>(
			metadata = NowPlayingSceneStrategy.bottomSheet(maxWidth = Dp.Unspecified)
		) {
			NowPlayingScreen()
		}
		entry<Screen.Lyrics>(metadata = NowPlayingSceneStrategy.bottomSheet(isTransparent = true)) {
			val player = koinInject<MediaPlayerViewModel>()
			val playerState by player.uiState.collectAsState()
			val song = playerState.currentSong
			LyricsScreen(song)
		}
		entry<Screen.Queue>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			QueueScreen()
		}
		entry<Screen.PlaybackSpeed>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			PlaybackSpeedScreen()
		}
		entry<Screen.CollectionDetail>(metadata = detailPane("root")) { key ->
			CollectionDetailScreen(key.collectionId, key.tab)
		}
		entry<Screen.SongDetailScreen> { key ->
			SongDetailScreen(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.SongDetailSheet>(
			metadata = { key ->
				BottomSheetSceneStrategy.bottomSheet(coverArtId = key.coverArtId)
			}
		) { key ->
			SongDetailSheet(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.Search>(metadata = navtabMetadata) { key ->
			SearchScreen(key.nested)
		}
		entry<Screen.ShareList> {
			ShareListScreen()
		}
		entry<Screen.ArtistDetail> { key ->
			ArtistDetailScreen(key.artist)
		}

		// settings
		entry<Screen.Settings.Root>(metadata = listPane("settings")) {
			SettingsScreen()
		}
		entry<Screen.Settings.Appearance>(metadata = detailPane("settings")) {
			SettingsAppearanceScreen()
		}
		entry<Screen.Settings.BottomAppBar>(metadata = detailPane("settings")) {
			BottomBarScreen()
		}
		entry<Screen.Settings.NowPlaying>(metadata = detailPane("settings")) {
			SettingsNowPlayingScreen()
		}
		entry<Screen.Settings.Playback>(metadata = detailPane("settings")) {
			SettingsPlaybackScreen()
		}
		entry<Screen.Settings.AdvancedEqualizer> {
			SettingsAdvancedEqualizerScreen()
		}
		entry<Screen.Settings.Developer>(metadata = detailPane("settings")) {
			SettingsDeveloperScreen()
		}
		entry<Screen.Settings.About>(metadata = detailPane("settings")) {
			SettingsAboutScreen()
		}
		entry<Screen.Settings.Insights>(metadata = detailPane("settings")) {
			InsightsSettingsScreen()
		}
		entry<Screen.Settings.InsightsProvider>(metadata = detailPane("settings")) { key ->
			InsightsProviderScreen(key.providerId)
		}
		entry<Screen.Settings.Acknowledgements> {
			SettingsAcknowledgementsScreen()
		}
		entry<Screen.Settings.DataStorage>(metadata = detailPane("settings")) {
			SettingsDataStorageScreen()
		}
		entry<Screen.Settings.Fonts> {
			FontsScreen()
		}
		entry<Screen.Settings.Themes> {
			SettingsThemesScreen()
		}
		entry<Screen.Settings.CustomHeaders> {
			SettingsCustomHeadersScreen()
		}
		entry<Screen.Settings.StreamingQuality> {
			SettingsStreamingQualityScreen()
		}
		entry<Screen.Settings.DownloadQuality> {
			SettingsDownloadQualityScreen()
		}
		entry<Screen.Settings.Logs> {
			SettingsLogsScreen()
		}
		entry<Screen.Settings.AppIcon>(metadata = detailPane("settings")) {
			SettingsAppIconScreen()
		}
	}
}
