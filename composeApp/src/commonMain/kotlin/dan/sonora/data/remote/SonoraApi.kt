package dan.sonora.data.remote

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

class SonoraApi(
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
						throw SonoraApiException.InvalidLastFmToken
					}
				}
				400, 401, 403 -> throw SonoraApiException.InvalidLastFmToken
				in 500..599 -> throw SonoraApiException.Unavailable
				else -> throw SonoraApiException.UnexpectedResponse(response.status.value)
			}
		} catch (error: CancellationException) {
			throw error
		} catch (error: SonoraApiException) {
			throw error
		} catch (error: IOException) {
			throw SonoraApiException.Network(error)
		} catch (error: SerializationException) {
			throw SonoraApiException.MalformedResponse(error)
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

sealed class SonoraApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
	data object InvalidLastFmToken : SonoraApiException("The Last.fm authorization token is invalid or expired")
	data object Unavailable : SonoraApiException("The Sonora API is temporarily unavailable")
	class Network(cause: Throwable) : SonoraApiException("Unable to reach the Sonora API", cause)
	class MalformedResponse(cause: Throwable) : SonoraApiException("The Sonora API returned an invalid response", cause)
	class UnexpectedResponse(statusCode: Int) : SonoraApiException("The Sonora API returned HTTP $statusCode")
}
