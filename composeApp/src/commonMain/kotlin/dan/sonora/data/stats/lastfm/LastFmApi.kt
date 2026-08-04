package dan.sonora.data.stats.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LastFmApiException(message: String) : Exception(message)

/**
 * Thin Ktor wrapper over the Last.fm 2.0 API. Returns wire DTOs; mapping to the
 * provider-agnostic models happens in [LastFmStatsProvider].
 */
class LastFmApi(
	private val authStore: LastFmAuthStore
) {
	private val client = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	private val json = Json { ignoreUnknownKeys = true }

	suspend fun getTopArtists(period: String?): List<LastFmArtist> {
		val payload = requestJson {
			addUserParameters("user.gettopartists")
			period?.let { parameter("period", it) }
		}
		if (payload["topartists"] == null) {
			throw LastFmApiException("Last.fm top artists response did not contain topartists")
		}
		return json.decodeFromJsonElement<TopArtistsResponse>(payload).topartists.artist
	}

	suspend fun getTopTracks(period: String?): List<LastFmTrack> {
		val payload = requestJson {
			addUserParameters("user.gettoptracks")
			period?.let { parameter("period", it) }
		}
		if (payload["toptracks"] == null) {
			throw LastFmApiException("Last.fm top tracks response did not contain toptracks")
		}
		return json.decodeFromJsonElement<TopTracksResponse>(payload).toptracks.track
	}

	suspend fun getRecentTracks(
		limit: Int = 200,
		page: Int = 1,
		from: Long? = null,
		to: Long? = null
	): RecentTracksResponse {
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

	private fun HttpRequestBuilder.addUserParameters(method: String) {
		val key = authStore.sessionKey ?: error("Last.fm session has not been established")
		val user = authStore.username ?: error("Last.fm session has not been established")
		parameter("api_key", LastFmAuthStore.API_KEY)
		parameter("method", method)
		parameter("sk", key)
		parameter("user", user)
		parameter("format", "json")
	}

	private suspend fun requestJson(block: HttpRequestBuilder.() -> Unit): JsonObject {
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
	}
}
