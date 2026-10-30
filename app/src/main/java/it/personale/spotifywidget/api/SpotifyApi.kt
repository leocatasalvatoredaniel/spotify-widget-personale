package it.personale.spotifywidget.api

import it.personale.spotifywidget.auth.SpotifyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class NotLoggedInException : Exception("Not logged in to Spotify")

class SpotifyApiException(
    val code: Int,
    val reason: String?,
    message: String,
) : Exception(message) {
    val isNoActiveDevice: Boolean get() = code == 404 || reason == "NO_ACTIVE_DEVICE"
    val isPremiumRequired: Boolean get() = reason == "PREMIUM_REQUIRED"
}

/**
 * The Spotify Web API endpoints used by the widget
 */
class SpotifyApi(private val http: OkHttpClient, private val auth: SpotifyAuth) {

    suspend fun me(): SpotifyUser = decode(execute("GET", url("me")))

    /** Null when nothing is playing (204) */
    suspend fun currentlyPlaying(): CurrentlyPlaying? =
        execute("GET", url("me/player/currently-playing"))?.let { decode(it) }

    suspend fun devices(): List<SpotifyDevice> =
        decode<SpotifyDevices>(execute("GET", url("me/player/devices"))).devices

    suspend fun play(request: PlayRequest, deviceId: String? = null) {
        val target = url("me/player/play") { deviceId?.let { addQueryParameter("device_id", it) } }
        execute(
            "PUT",
            target,
            SpotifyJson.encodeToString(PlayRequest.serializer(), request)
                .toRequestBody(JSON),
        )
    }

    suspend fun recentlyPlayed(limit: Int = 30): List<PlayHistory> =
        decode<Paging<PlayHistory>>(
            execute(
                "GET",
                url("me/player/recently-played") { addQueryParameter("limit", "$limit") },
            ),
        ).items.filterNotNull()

    /** The playlists of the account, up to [max] */
    suspend fun myPlaylists(max: Int = 200): List<SpotifyPlaylist> {
        val playlists = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (playlists.size < max) {
            val page = decode<Paging<SpotifyPlaylist>>(
                execute(
                    "GET",
                    url("me/playlists") {
                        addQueryParameter("limit", "50")
                        addQueryParameter("offset", "$offset")
                    },
                ),
            )
            playlists += page.items.filterNotNull()
            if (page.next == null || page.items.isEmpty()) break
            offset += page.items.size
        }
        return playlists.take(max)
    }

    suspend fun isSaved(uri: String): Boolean =
        decode<List<Boolean>>(
            execute("GET", url("me/library/contains") { addQueryParameter("uris", uri) }),
        ).firstOrNull() ?: false

    suspend fun save(uri: String) {
        execute("PUT", url("me/library") { addQueryParameter("uris", uri) }, EMPTY_BODY)
    }

    suspend fun remove(uri: String) {
        execute("DELETE", url("me/library") { addQueryParameter("uris", uri) }, EMPTY_BODY)
    }

    private fun url(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        BASE_URL.toHttpUrl().newBuilder().addPathSegments(path).apply(query).build()

    private inline fun <reified T> decode(body: String?): T =
        SpotifyJson.decodeFromString(body ?: throw SpotifyApiException(204, null, "Empty response"))

    /** Response body, null for 204 No Content */
    private suspend fun execute(
        method: String,
        url: HttpUrl,
        body: RequestBody? = null,
        retry: Boolean = true,
    ): String? = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: throw NotLoggedInException()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()
        val (code, text) = http.newCall(request).execute().use { it.code to it.body?.string() }
        when {
            code == 204 -> null
            code in 200..299 -> text.takeUnless { it.isNullOrBlank() }
            code == 401 && retry -> {
                // Expired token: refresh it once and try again
                auth.accessToken(forceRefresh = true) ?: throw NotLoggedInException()
                execute(method, url, body, retry = false)
            }
            else -> {
                val error = text?.let {
                    runCatching { SpotifyJson.decodeFromString<SpotifyErrorBody>(it).error }
                        .getOrNull()
                }
                throw SpotifyApiException(
                    code = code,
                    reason = error?.reason,
                    message = error?.message?.takeIf { it.isNotBlank() } ?: "HTTP $code",
                )
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.spotify.com/v1/"
        val JSON = "application/json".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
