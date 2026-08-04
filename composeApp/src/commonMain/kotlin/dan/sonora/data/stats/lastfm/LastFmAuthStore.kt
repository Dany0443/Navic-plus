package dan.sonora.data.stats.lastfm

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dan.sonora.data.remote.SonoraApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last.fm session persistence and the browser authorization handshake.
 *
 * The settings keys are unchanged from the previous `LastFmManager` so existing
 * installs stay signed in across this refactor.
 */
class LastFmAuthStore(
	private val settings: Settings,
	private val sonoraApi: SonoraApi
) {
	val token: String?
		get() = settings.getStringOrNull(TOKEN_KEY)

	val sessionKey: String?
		get() = settings.getStringOrNull(SESSION_KEY)

	val username: String?
		get() = settings.getStringOrNull(USERNAME_KEY)

	private val authenticated = MutableStateFlow(sessionKey != null && username != null)
	val isAuthenticated: StateFlow<Boolean> = authenticated.asStateFlow()

	val authorizationUrl: String
		get() = "https://www.last.fm/api/auth/?api_key=$API_KEY&cb=sonora://lastfm"

	fun setToken(token: String) {
		settings[TOKEN_KEY] = token
	}

	suspend fun exchangeTokenForSession(token: String) {
		val response = sonoraApi.createLastFmSession(token)

		setToken(token)
		settings[SESSION_KEY] = response.sessionKey
		settings[USERNAME_KEY] = response.username
		authenticated.value = true
	}

	/** Forgets the session so the user is signed out of Last.fm. */
	fun clear() {
		settings.remove(TOKEN_KEY)
		settings.remove(SESSION_KEY)
		settings.remove(USERNAME_KEY)
		authenticated.value = false
	}

	internal companion object {
		const val API_KEY = "8376f431933316a329bb37dc902f532a"
		private const val TOKEN_KEY = "lastFmToken"
		private const val SESSION_KEY = "lastFmSessionKey"
		private const val USERNAME_KEY = "lastFmUsername"
	}
}
