package paige.navic.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class NavicApi(
	private val baseUrl: String = DEFAULT_BASE_URL,
	private val client: HttpClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}
) {
	suspend fun createLastFmSession(token: String): LastFmSessionResponse {
		try {
			val response = client.post("$baseUrl/v1/lastfm/session") {
				header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
				setBody(LastFmSessionRequest(token))
			}

			return when (response.status.value) {
				in 200..299 -> response.body<LastFmSessionResponse>().also { session ->
					if (!session.success || session.username.isBlank() || session.sessionKey.isBlank()) {
						throw NavicApiException.InvalidLastFmToken
					}
				}
				400, 401, 403 -> throw NavicApiException.InvalidLastFmToken
				in 500..599 -> throw NavicApiException.Unavailable
				else -> throw NavicApiException.UnexpectedResponse(response.status.value)
			}
		} catch (error: CancellationException) {
			throw error
		} catch (error: NavicApiException) {
			throw error
		} catch (error: IOException) {
			throw NavicApiException.Network(error)
		} catch (error: SerializationException) {
			throw NavicApiException.MalformedResponse(error)
		}
	}

	companion object {
		const val DEFAULT_BASE_URL = "https://nvcapi.webjuniors.org"
	}
}

@Serializable
data class LastFmSessionRequest(val token: String)

@Serializable
data class LastFmSessionResponse(
	val success: Boolean,
	val username: String = "",
	val sessionKey: String = ""
)

sealed class NavicApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
	data object InvalidLastFmToken : NavicApiException("The Last.fm authorization token is invalid or expired")
	data object Unavailable : NavicApiException("The Navic API is temporarily unavailable")
	class Network(cause: Throwable) : NavicApiException("Unable to reach the Navic API", cause)
	class MalformedResponse(cause: Throwable) : NavicApiException("The Navic API returned an invalid response", cause)
	class UnexpectedResponse(statusCode: Int) : NavicApiException("The Navic API returned HTTP $statusCode")
}
