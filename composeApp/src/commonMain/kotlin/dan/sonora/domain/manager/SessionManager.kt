package dan.sonora.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager
) {
	val isLoggedIn: StateFlow<Boolean>
		field = MutableStateFlow(false)

	var api: SubsonicClient = createClient(
		instanceUrl = settings.getString("instanceUrl", ""),
		username = settings.getString("username", ""),
		password = settings.getString("password", ""),
	)
		private set

	init {
		isLoggedIn.value = settings.getStringOrNull("username") != null
	}

	private fun createClient(
		instanceUrl: String,
		username: String,
		password: String,
	) = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(
			username = username,
			password = password,
		),
		client = "Sonora",
		clientConfig = {
			install(UserAgent) {
				agent = "Sonora"
			}

			install(HttpTimeout) {
				requestTimeoutMillis = 120_000
				connectTimeoutMillis = 30_000
				socketTimeoutMillis = 120_000
			}

			val customHeaders = preferenceManager.customHeadersMap()
			if (customHeaders.isNotEmpty()) {
				defaultRequest {
					customHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		}
	)

	private fun isTlsOrSslException(e: Throwable): Boolean {
		var cause: Throwable? = e
		while (cause != null) {
			val msg = cause.message ?: ""
			val className = cause::class.simpleName ?: ""
			if (className.contains("SSL", ignoreCase = true) ||
				className.contains("TLS", ignoreCase = true) ||
				msg.contains("TLS", ignoreCase = true) ||
				msg.contains("SSL", ignoreCase = true) ||
				msg.contains("handshake", ignoreCase = true) ||
				msg.contains("packet header", ignoreCase = true)
			) {
				return true
			}
			cause = cause.cause
		}
		return false
	}

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String
	) {
		val cleanInput = instanceUrl.trim().removeSuffix("/")
		val hasScheme = cleanInput.startsWith("http://", ignoreCase = true) || cleanInput.startsWith("https://", ignoreCase = true)

		val candidateUrls = if (hasScheme) {
			if (cleanInput.startsWith("https://", ignoreCase = true)) {
				val fallbackHttp = "http://" + cleanInput.substring(8)
				listOf(cleanInput, fallbackHttp)
			} else {
				listOf(cleanInput)
			}
		} else {
			listOf("https://$cleanInput", "http://$cleanInput")
		}

		var lastException: Exception? = null
		var successfulUrl: String? = null
		var successfulClient: SubsonicClient? = null

		for (url in candidateUrls) {
			val client = createClient(url, username, password)
			try {
				client.ping()
				successfulUrl = url
				successfulClient = client
				break
			} catch (e: Exception) {
				lastException = e
				if (url.startsWith("https://", ignoreCase = true) && isTlsOrSslException(e)) {
					continue
				}
				if (!hasScheme) {
					continue
				}
			}
		}

		if (successfulUrl == null || successfulClient == null) {
			throw Exception(
				"Failed to connect to the instance. Please check your credentials and try again.",
				lastException
			)
		}

		settings["instanceUrl"] = successfulUrl
		settings["username"] = username
		settings["password"] = password

		api = successfulClient
		isLoggedIn.value = true
	}

	fun logout() {
		settings["username"] = null
		settings["password"] = null
		isLoggedIn.value = false
	}

	fun refreshClient() {
		api = createClient(
			instanceUrl = settings.getString("instanceUrl", ""),
			username = settings.getString("username", ""),
			password = settings.getString("password", ""),
		)
	}

	fun getCoverArtUrl(coverArtId: String) = api.getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${preferenceManager.coverArtQuality.value}"
	)
}
