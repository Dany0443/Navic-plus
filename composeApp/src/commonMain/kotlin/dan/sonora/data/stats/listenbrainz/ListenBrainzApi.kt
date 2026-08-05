package dan.sonora.data.stats.listenbrainz

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ListenBrainzApiException(message: String) : Exception(message)

/** The username is not present on the server it was looked up on. */
class ListenBrainzUnknownUserException(username: String) :
	Exception("ListenBrainz has no user named \"$username\"")

/**
 * Thin Ktor wrapper over the ListenBrainz 1 API. Returns wire DTOs; mapping to the
 * provider-agnostic models happens in [ListenBrainzStatsProvider].
 *
 * Every endpoint used here is public, so no request carries a token.
 */
class ListenBrainzApi(
	private val authStore: ListenBrainzAuthStore
) {
	private val json = Json { ignoreUnknownKeys = true }

	private val client = HttpClient {
		install(ContentNegotiation) {
			json(json)
		}
	}

	/**
	 * Verifies a username against a server before it is stored. Uses the explicit
	 * arguments rather than the store because this runs during the connect flow, when
	 * nothing has been persisted yet.
	 */
	suspend fun userExists(username: String, serverUrl: String): Boolean {
		val response = client.get("${serverUrl.normalizeServerUrl()}/1/user/$username/listen-count")
		return when {
			response.status.isSuccess() -> true
			response.status == HttpStatusCode.NotFound -> false
			else -> throw ListenBrainzApiException(
				"ListenBrainz returned HTTP ${response.status.value} while checking the username"
			)
		}
	}

	suspend fun getListenCount(): Int =
		request<ListenCountResponse>("/1/user/${requireUser()}/listen-count").payload.count

	/**
	 * One page of listens, newest first.
	 *
	 * [maxTs] and [minTs] are exclusive bounds in epoch seconds. Paging walks backwards
	 * by passing the oldest timestamp seen so far as [maxTs]; [minTs] stops an
	 * incremental sync once it reaches history already held locally.
	 */
	internal suspend fun getListens(
		maxTs: Long? = null,
		minTs: Long? = null,
		count: Int = PAGE_SIZE
	): ListensPayload = request<ListensResponse>("/1/user/${requireUser()}/listens") {
		parameter("count", count.toString())
		maxTs?.let { parameter("max_ts", it.toString()) }
		minTs?.let { parameter("min_ts", it.toString()) }
	}.payload

	internal suspend fun getTopArtists(range: String, count: Int = STATS_COUNT): List<ListenBrainzArtist> =
		request<ArtistStatsResponse>("/1/stats/user/${requireUser()}/artists") {
			parameter("range", range)
			parameter("count", count.toString())
		}.payload.artists

	internal suspend fun getTopRecordings(range: String, count: Int = STATS_COUNT): List<ListenBrainzRecording> =
		request<RecordingStatsResponse>("/1/stats/user/${requireUser()}/recordings") {
			parameter("range", range)
			parameter("count", count.toString())
		}.payload.recordings

	private suspend inline fun <reified T> request(
		path: String,
		noinline block: HttpRequestBuilder.() -> Unit = {}
	): T {
		val response = client.get("${authStore.serverUrl}$path") { block() }

		if (response.status == HttpStatusCode.NotFound) {
			throw ListenBrainzUnknownUserException(authStore.username.orEmpty())
		}
		if (!response.status.isSuccess()) {
			throw ListenBrainzApiException("ListenBrainz returned HTTP ${response.status.value}")
		}
		// Statistics are computed in batches, so an account with too little history —
		// or a brand new one — yields 204 with an empty body rather than a payload.
		if (response.status == HttpStatusCode.NoContent) {
			return json.decodeFromString<T>(EMPTY_PAYLOAD)
		}
		return json.decodeFromString<T>(response.bodyAsText())
	}

	private fun requireUser(): String =
		authStore.username ?: error("ListenBrainz has not been connected")

	internal companion object {
		/** ListenBrainz's maximum listens per request. */
		const val PAGE_SIZE = 1000
		private const val STATS_COUNT = 100

		/** Deserializes to the empty case of every response type used here. */
		private const val EMPTY_PAYLOAD = """{"payload":{}}"""
	}
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
