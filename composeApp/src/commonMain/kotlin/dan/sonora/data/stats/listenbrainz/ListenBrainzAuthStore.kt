package dan.sonora.data.stats.listenbrainz

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ListenBrainz connection state: a username and the server it belongs to.
 *
 * ListenBrainz listening statistics are public, so there is no token to store — being
 * "connected" simply means a username has been verified against a server. The server is
 * kept alongside it because self-hosted instances serve entirely different accounts, so
 * a username is only meaningful paired with the server it was verified on.
 */
class ListenBrainzAuthStore(
	private val settings: Settings
) {
	val username: String?
		get() = settings.getStringOrNull(USERNAME_KEY)

	/** The API root, without a trailing slash. Falls back to the hosted instance. */
	val serverUrl: String
		get() = settings.getStringOrNull(SERVER_KEY) ?: DEFAULT_SERVER_URL

	private val connected = MutableStateFlow(username != null)
	val isConnected: StateFlow<Boolean> = connected.asStateFlow()

	/**
	 * Marks the account as connected. Callers must verify the username against
	 * [serverUrl] first — [ListenBrainzApi.userExists] does this — so a typo never
	 * reaches a state where the provider is connected but every request 404s.
	 */
	fun connect(username: String, serverUrl: String) {
		settings[USERNAME_KEY] = username
		settings[SERVER_KEY] = serverUrl.normalizeServerUrl()
		connected.value = true
	}

	fun clear() {
		settings.remove(USERNAME_KEY)
		settings.remove(SERVER_KEY)
		connected.value = false
	}

	companion object {
		const val DEFAULT_SERVER_URL = "https://api.listenbrainz.org"
		private const val USERNAME_KEY = "listenBrainzUsername"
		private const val SERVER_KEY = "listenBrainzServer"
	}
}

/**
 * Tolerates what users actually paste: a bare host, a trailing slash, or surrounding
 * whitespace. Without a scheme the request would fail to parse rather than 404, which
 * would read as "server down" instead of "check the address".
 */
internal fun String.normalizeServerUrl(): String {
	val trimmed = trim().trimEnd('/')
	if (trimmed.isEmpty()) return ListenBrainzAuthStore.DEFAULT_SERVER_URL
	return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
		trimmed
	} else {
		"https://$trimmed"
	}
}
