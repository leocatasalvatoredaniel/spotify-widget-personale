package it.personale.spotifywidget.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import it.personale.spotifywidget.api.SpotifyJson
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch milliseconds */
    val expiresAt: Long,
)

/**
 * Spotify tokens, encrypted with a key kept by the Android Keystore
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun load(): Tokens? = prefs.getString(KEY_TOKENS, null)?.let { encrypted ->
        runCatching { SpotifyJson.decodeFromString<Tokens>(decrypt(encrypted)) }.getOrNull()
    }

    fun save(tokens: Tokens) = prefs.edit {
        putString(KEY_TOKENS, encrypt(SpotifyJson.encodeToString(Tokens.serializer(), tokens)))
    }

    fun clear() = prefs.edit { remove(KEY_TOKENS) }

    /** Values kept between the start and the end of the login in the browser */
    var pendingVerifier: String?
        get() = prefs.getString(KEY_VERIFIER, null)
        set(value) = prefs.edit { putString(KEY_VERIFIER, value) }

    var pendingState: String?
        get() = prefs.getString(KEY_STATE, null)
        set(value) = prefs.edit { putString(KEY_STATE, value) }

    var pendingRedirect: String?
        get() = prefs.getString(KEY_REDIRECT, null)
        set(value) = prefs.edit { putString(KEY_REDIRECT, value) }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(text: String): String {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "spotify-tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_TOKENS = "tokens"
        const val KEY_VERIFIER = "verifier"
        const val KEY_STATE = "state"
        const val KEY_REDIRECT = "redirect"
    }
}
