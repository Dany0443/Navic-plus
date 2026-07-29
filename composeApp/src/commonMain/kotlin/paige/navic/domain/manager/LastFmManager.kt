package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import paige.navic.data.remote.NavicApi

class LastFmManager(
	private val settings: Settings,
	private val navicApi: NavicApi
) {
	private val client = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	val token: String?
		get() = settings.getStringOrNull(TOKEN_KEY)

	val sessionKey: String?
		get() = settings.getStringOrNull(SESSION_KEY)

	val username: String?
		get() = settings.getStringOrNull(USERNAME_KEY)

	private val authenticated = MutableStateFlow(sessionKey != null && username != null)
	val isAuthenticated: StateFlow<Boolean> = authenticated.asStateFlow()

	val authorizationUrl: String
		get() = "https://www.last.fm/api/auth/?api_key=$API_KEY&cb=navic://lastfm"

	fun setToken(token: String) {
		settings[TOKEN_KEY] = token
	}

	suspend fun exchangeTokenForSession(token: String) {
		val response = navicApi.createLastFmSession(token)

		setToken(token)
		settings[SESSION_KEY] = response.sessionKey
		settings[USERNAME_KEY] = response.username
		authenticated.value = true
	}

	suspend fun getTopArtists(): List<LastFmArtist> {
		val payload = requestJson { addUserParameters("user.gettopartists") }
		if (payload["topartists"] == null) {
			throw LastFmApiException("Last.fm top artists response did not contain topartists")
		}
		return json.decodeFromJsonElement<TopArtistsResponse>(payload).topartists.artist
	}

	suspend fun getTopTracks(): List<LastFmTrack> {
		val payload = requestJson { addUserParameters("user.gettoptracks") }
		if (payload["toptracks"] == null) {
			throw LastFmApiException("Last.fm top tracks response did not contain toptracks")
		}
		return json.decodeFromJsonElement<TopTracksResponse>(payload).toptracks.track
	}

	suspend fun getRecentTracks(limit: Int = 200, page: Int = 1, from: Long? = null, to: Long? = null): RecentTracksResponse {
		val payload = requestJson {
			addUserParameters("user.getrecenttracks")
			parameter("limit", limit.toString())
			parameter("page", page.toString())
			from?.let { parameter("from", it.toString()) }
			to?.let { parameter("to", it.toString()) }
		}
		if (payload["recenttracks"] == null) {
			throw LastFmApiException("Last.fm recent tracks response did not contain recenttracks")
		}
		return json.decodeFromJsonElement<RecentTracksResponse>(payload)
	}

	suspend fun getUserInfo(): LastFmUserInfo {
		val payload = requestJson { addUserParameters("user.getinfo") }
		if (payload["user"] == null) {
			throw LastFmApiException("Last.fm user info response did not contain user")
		}
		return json.decodeFromJsonElement<UserInfoResponse>(payload).user
	}

	private fun io.ktor.client.request.HttpRequestBuilder.addUserParameters(method: String) {
		val key = sessionKey ?: error("Last.fm session has not been established")
		val user = username ?: error("Last.fm session has not been established")
		parameter("api_key", API_KEY)
		parameter("method", method)
		parameter("sk", key)
		parameter("user", user)
		parameter("format", "json")
	}

	private val json = Json { ignoreUnknownKeys = true }

	private suspend fun requestJson(
		block: io.ktor.client.request.HttpRequestBuilder.() -> Unit
	): JsonObject {
		val response = client.get(API_URL, block)
		val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
		if (payload["error"] != null) {
			val error = payload["error"]?.jsonPrimitive?.content ?: "unknown"
			val message = payload["message"]?.jsonPrimitive?.content ?: "Unknown error"
			throw LastFmApiException("Last.fm error $error: $message")
		}
		return payload
	}

	private companion object {
		const val API_URL = "https://ws.audioscrobbler.com/2.0/"
		const val API_KEY = "8376f431933316a329bb37dc902f532a"
		const val TOKEN_KEY = "lastFmToken"
		const val SESSION_KEY = "lastFmSessionKey"
		const val USERNAME_KEY = "lastFmUsername"
	}
}

private class LastFmApiException(message: String) : Exception(message)

@Serializable
data class LastFmArtist(
	val name: String,
	val url: String,
	val playcount: String,
	val mbid: String? = null
)

@Serializable
data class LastFmTrack(
	val name: String,
	val url: String,
	val playcount: String,
	val artist: LastFmTrackArtist? = null
)

@Serializable
data class LastFmTrackArtist(
	val name: String = "",
	@SerialName("#text") val text: String = ""
)

@Serializable
private data class TopArtistsResponse(val topartists: Artists)

@Serializable
private data class Artists(val artist: List<LastFmArtist>)

@Serializable
private data class TopTracksResponse(val toptracks: Tracks)

@Serializable
private data class Tracks(val track: List<LastFmTrack>)

@Serializable
data class RecentTracksResponse(val recenttracks: RecentTracks)

@Serializable
data class RecentTracks(
	val track: List<RecentTrack>,
	@SerialName("@attr") val attr: RecentTracksAttr
)

@Serializable
data class RecentTracksAttr(
	val page: String,
	val total: String,
	val user: String,
	val perPage: String,
	val totalPages: String
)

@Serializable
data class RecentTrack(
	val artist: RecentTrackArtist,
	val name: String,
	val album: RecentTrackAlbum,
	val url: String,
	val date: RecentTrackDate? = null,
	@SerialName("@attr") val attr: NowPlayingAttr? = null
)

@Serializable
data class RecentTrackArtist(
	val mbid: String,
	@SerialName("#text") val text: String
)

@Serializable
data class RecentTrackAlbum(
	val mbid: String,
	@SerialName("#text") val text: String
)

@Serializable
data class RecentTrackDate(
	val uts: String,
	@SerialName("#text") val text: String
)

@Serializable
data class NowPlayingAttr(
	val nowplaying: String
)

@Serializable
private data class UserInfoResponse(val user: LastFmUserInfo)

@Serializable
data class LastFmUserInfo(
	val name: String,
	val playcount: String,
	val registered: Registered
)

@Serializable
data class Registered(
	val unixtime: String
)
