package dan.sonora.shared

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dan.sonora.domain.manager.AndroidScrobbleManager
import dan.sonora.domain.manager.ConnectivityManager
import dan.sonora.domain.manager.PreferenceManager
import dan.sonora.domain.manager.SessionManager
import dan.sonora.domain.manager.SyncManager
import dan.sonora.shared.playback.ExoPlayerFactory
import dan.sonora.shared.playback.EqualizerSettingsProvider
import dan.sonora.shared.playback.SonoraAudioRenderersFactory
import dan.sonora.shared.playback.PlaybackEngine
import dan.sonora.util.core.ResourceProvider

/**
 * The `MediaSessionService` host for playback.
 *
 * It builds the [PlaybackEngine] and binds the `MediaSession` to the engine's single public
 * `Player` ([dan.sonora.shared.playback.CrossfadePlayer]). Notifications, Android Auto,
 * Bluetooth, headset buttons and the scrobbler therefore all talk to that one player while the
 * engine overlaps two hidden `ExoPlayer` decks for crossfade underneath.
 *
 * The service owns only wiring and lifecycle; all playback behaviour lives in the engine.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), KoinComponent {

	private var mediaSession: MediaSession? = null
	private var engine: PlaybackEngine? = null
	private var scrobbleManager: AndroidScrobbleManager? = null
	private val serviceScope = MainScope()

	private val resourceProvider: ResourceProvider by inject()
	private val connectivityManager: ConnectivityManager by inject()
	private val syncManager: SyncManager by inject()
	private val sessionManager: SessionManager by inject()
	private val preferenceManager: PreferenceManager by inject()
	private val equalizerSettingsProvider: EqualizerSettingsProvider by inject()

	override fun onCreate() {
		super.onCreate()
		equalizerSettingsProvider.update(preferenceManager.equalizerSettings)

		setMediaNotificationProvider(
			DefaultMediaNotificationProvider.Builder(this).build().apply {
				setSmallIcon(resourceProvider.icSonora)
			},
		)

		val engine = PlaybackEngine(
			context = applicationContext,
			playerFactory = createPlayerFactory(),
			preferenceManager = preferenceManager,
		).also { this.engine = it }

		val player = engine.player

		scrobbleManager = AndroidScrobbleManager(
			player,
			serviceScope,
			connectivityManager,
			syncManager,
			sessionManager,
			preferenceManager,
		)

		mediaSession = MediaSession.Builder(this, player)
			.setSessionActivity(buildSessionActivityIntent())
			.setCallback(MediaSessionCallback(player))
			.setCustomLayout(makeButtons(player))
			.build()

		player.addListener(object : Player.Listener {
			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
				mediaSession?.setCustomLayout(makeButtons(player))
			}

			override fun onRepeatModeChanged(repeatMode: Int) {
				mediaSession?.setCustomLayout(makeButtons(player))
			}
		})
	}

	/**
	 * Factory for the engine's internal decks. Each deck holds a single item; audio focus and
	 * becoming-noisy are handled centrally by the engine, so both are disabled here.
	 */
	private fun createPlayerFactory(): ExoPlayerFactory {
		val httpDataSourceFactory = DefaultHttpDataSource.Factory()
			.setDefaultRequestProperties(preferenceManager.customHeadersMap())
		val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

		val audioAttributes = AudioAttributes.Builder()
			.setUsage(C.USAGE_MEDIA)
			.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
			.build()

		// Audio offload sends decoded audio directly to the hardware and bypasses Media3's
		// AudioProcessor chain. Keep it disabled while Sonora installs its real-time DSP sink.
		val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
			.setIsGaplessSupportRequired(preferenceManager.gaplessPlayback)
			.setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
			.build()

		return ExoPlayerFactory {
			val loadControl = DefaultLoadControl.Builder()
				.setBufferDurationsMs(32_000, 64_000, 2_500, 5_000)
				.setBackBuffer(10_000, true)
				.build()

			ExoPlayer.Builder(this, SonoraAudioRenderersFactory(this, equalizerSettingsProvider))
				.setLoadControl(loadControl)
				.setMediaSourceFactory(mediaSourceFactory)
				.setHandleAudioBecomingNoisy(false)
				.setWakeMode(C.WAKE_MODE_NETWORK)
				.build()
				.apply {
					setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
					trackSelectionParameters = trackSelectionParameters.buildUpon()
						.setAudioOffloadPreferences(offloadPreferences)
						.build()
				}
		}
	}

	private fun buildSessionActivityIntent(): PendingIntent {
		val sessionIntent = applicationContext.packageManager
			.getLaunchIntentForPackage(applicationContext.packageName)
			?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
		return PendingIntent.getActivity(
			this,
			0,
			sessionIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

	override fun onTaskRemoved(rootIntent: Intent?) {
		onDestroy()
	}

	override fun onDestroy() {
		scrobbleManager?.release()
		scrobbleManager = null
		stopForeground(STOP_FOREGROUND_REMOVE)
		mediaSession?.release()
		mediaSession = null
		// Releases both decks, the fade job, focus and the engine scope.
		engine?.player?.release()
		engine = null
		serviceScope.cancel()
		super.onDestroy()
		stopSelf()
	}

	/** Handles the custom shuffle/repeat session commands surfaced as notification buttons. */
	class MediaSessionCallback(private val player: Player) : MediaSession.Callback {
		override fun onConnect(
			session: MediaSession,
			controller: MediaSession.ControllerInfo,
		): MediaSession.ConnectionResult {
			val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
				.buildUpon()
				.add(SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY))
				.add(SessionCommand(COMMAND_REPEAT, Bundle.EMPTY))
				.build()
			return MediaSession.ConnectionResult.accept(
				sessionCommands,
				MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
			)
		}

		override fun onCustomCommand(
			session: MediaSession,
			controller: MediaSession.ControllerInfo,
			customCommand: SessionCommand,
			args: Bundle,
		): ListenableFuture<SessionResult> {
			when (customCommand.customAction) {
				COMMAND_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
				COMMAND_REPEAT -> player.repeatMode = when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
			}
			return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		}
	}

	companion object {
		const val COMMAND_SHUFFLE = "COMMAND_SHUFFLE"
		const val COMMAND_REPEAT = "COMMAND_REPEAT"

		private fun makeShuffleButton(enabled: Boolean): CommandButton =
			CommandButton.Builder(
				if (enabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
			)
				.setDisplayName("Shuffle")
				.setSessionCommand(SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY))
				.build()

		private fun makeRepeatButton(mode: Int): CommandButton =
			CommandButton.Builder(
				when (mode) {
					Player.REPEAT_MODE_OFF -> CommandButton.ICON_REPEAT_OFF
					Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
					else -> CommandButton.ICON_REPEAT_ONE
				},
			)
				.setDisplayName("Repeat")
				.setSessionCommand(SessionCommand(COMMAND_REPEAT, Bundle.EMPTY))
				.build()

		fun makeButtons(player: Player) = listOf(
			makeShuffleButton(player.shuffleModeEnabled),
			makeRepeatButton(player.repeatMode),
		)

		fun newSessionToken(context: Context): SessionToken =
			SessionToken(context, ComponentName(context, PlaybackService::class.java))
	}
}
