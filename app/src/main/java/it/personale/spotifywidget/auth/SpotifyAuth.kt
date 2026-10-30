package it.personale.spotifywidget.auth

import android.net.Uri
import androidx.core.net.toUri
import it.personale.spotifywidget.BuildConfig
import it.personale.spotifywidget.api.SpotifyJson
import it.personale.spotifywidget.api.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class LoginException(message: String) : Exception(message)

/**
 * Spotify login with Authorization Code + PKCE: no client secret in the app,
 * the login happens in the browser and comes back to spotifywidget://callback
 */
class SpotifyAuth(private val http: OkHttpClient, private val tokens: TokenStore) {
    private val refreshMutex = Mutex()

    val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID
    val isConfigured: Boolean get() = clientId.isNotBlank()
    val isLoggedIn: Boolean get() = tokens.load() != null

    fun authorizationUri(): Uri {
        val verifier = Pkce.newVerifier()
        val state = UUID.randomUUID().toString()
        tokens.pendingVerifier = verifier
        tokens.pendingState = state
        return AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("state", state)
            .build()
    }

    /** Handles spotifywidget://callback?code=…&state=… */
    suspend fun completeLogin(callback: Uri) {
        callback.getQueryParameter("error")?.let { throw LoginException(it) }
        val code = callback.getQueryParameter("code") ?: throw LoginException("missing code")
        if (callback.getQueryParameter("state") != tokens.pendingState) {
            throw LoginException("state mismatch")
        }
        val verifier = tokens.pendingVerifier ?: throw LoginException("missing verifier")
        val response = requestToken(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", verifier)
                .build(),
        )
        val refreshToken = response.refreshToken ?: throw LoginException("missing refresh token")
        tokens.save(response.toTokens(refreshToken))
        tokens.pendingVerifier = null
        tokens.pendingState = null
    }

    /** A valid access token, refreshed when needed; null when logged out */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = refreshMutex.withLock {
        val current = tokens.load() ?: return null
        if (!forceRefresh && current.expiresAt - EXPIRY_MARGIN_MS > System.currentTimeMillis()) {
            return current.accessToken
        }
        val response = try {
            requestToken(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", current.refreshToken)
                    .add("client_id", clientId)
                    .build(),
            )
        } catch (error: LoginException) {
            // The refresh token was revoked: log in again from the app
            tokens.clear()
            return null
        }
        val updated = response.toTokens(response.refreshToken ?: current.refreshToken)
        tokens.save(updated)
        updated.accessToken
    }

    fun logout() = tokens.clear()

    private suspend fun requestToken(body: FormBody): TokenResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> SpotifyJson.decodeFromString<TokenResponse>(text)
                // invalid_grant or invalid_client: the code or the refresh
                // token is no longer valid. Other errors (e.g. 429) are temporary.
                response.code == 400 || response.code == 401 ->
                    throw LoginException("HTTP ${response.code}: $text")
                else -> throw java.io.IOException("HTTP ${response.code}")
            }
        }
    }

    private fun TokenResponse.toTokens(refreshToken: String) = Tokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = System.currentTimeMillis() + expiresIn * 1000,
    )

    companion object {
        const val REDIRECT_URI = "spotifywidget://callback"
        private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val EXPIRY_MARGIN_MS = 60_000L

        val SCOPES = listOf(
            "user-read-private",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-read-recently-played",
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-library-modify",
        )
    }
}
