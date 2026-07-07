package com.example.posmobile.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** In-memory access token. The refresh token lives only in the cookie jar. */
object TokenStore {
    @Volatile private var token: String? = null
    @Volatile private var expiresAtMillis: Long = 0

    /** A token that is present and not within 30s of expiry, else null. */
    fun valid(): String? = token?.takeIf { System.currentTimeMillis() < expiresAtMillis - 30_000 }

    fun set(newToken: String, expiresInSeconds: Long) {
        token = newToken
        expiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000
    }

    fun clear() { token = null; expiresAtMillis = 0 }
}

class AuthException(message: String) : Exception(message)

/**
 * Drives the IdP's PKCE login and refresh entirely from native code — no
 * browser. Login is a direct POST that returns the auth code as JSON, which we
 * immediately exchange for an access token. [authClient] carries the persistent
 * cookie jar so the refresh cookie survives restarts.
 */
class AuthRepository(
    private val settings: Settings,
    private val authClient: OkHttpClient,
    private val json: Json,
) {
    private val jsonMedia = "application/json".toMediaType()

    /** Step 1+2: credentials -> auth code -> access token. Throws on failure. */
    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) throw AuthException("Set a workspace (tenant) first")

        val verifier = Pkce.verifier()
        val challenge = Pkce.challenge(verifier)

        val loginUrl = Uri.parse("${settings.authBase}/login").buildUpon()
            .appendQueryParameter("client_id", settings.clientId)
            .appendQueryParameter("redirect_uri", settings.redirectUri)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build().toString()

        val loginBody = json.encodeToString(LoginBody.serializer(), LoginBody(email, password))
        val loginResp = postJson(loginUrl, loginBody, LoginResponse.serializer())
        val redirect = loginResp.redirect
            ?: throw AuthException(loginResp.error ?: "Login failed")
        val code = Uri.parse(redirect).getQueryParameter("code")
            ?: throw AuthException("No authorization code returned")

        val tokenBody = json.encodeToString(
            TokenBody.serializer(),
            TokenBody(
                grantType = "authorization_code",
                clientId = settings.clientId,
                code = code,
                codeVerifier = verifier,
                redirectUri = settings.redirectUri,
            ),
        )
        val tokens = postJson("${settings.authBase}/token", tokenBody, TokenResponse.serializer())
        TokenStore.set(tokens.accessToken, tokens.expiresIn)
    }

    /** Returns a usable access token, refreshing via the cookie if needed. */
    fun ensureAccessToken(): String? = TokenStore.valid() ?: refreshBlocking()

    /** Synchronous refresh_token grant. Returns the new access token or null. */
    @Synchronized
    fun refreshBlocking(): String? {
        TokenStore.valid()?.let { return it } // another thread may have refreshed
        if (!settings.isConfigured) return null
        return try {
            val body = json.encodeToString(
                TokenBody.serializer(),
                TokenBody(grantType = "refresh_token", clientId = settings.clientId),
            )
            val tokens = postJson("${settings.authBase}/token", body, TokenResponse.serializer())
            TokenStore.set(tokens.accessToken, tokens.expiresIn)
            tokens.accessToken
        } catch (_: Exception) {
            TokenStore.clear()
            null
        }
    }

    fun logout() {
        // Best-effort server-side revoke; always clear locally.
        try {
            val req = Request.Builder()
                .url("${settings.authBase}/logout")
                .post(ByteArray(0).toRequestBody())
                .build()
            authClient.newCall(req).execute().close()
        } catch (_: Exception) { /* ignore */ }
        TokenStore.clear()
    }

    private fun <T> postJson(
        url: String,
        jsonBody: String,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val req = Request.Builder().url(url).post(jsonBody.toRequestBody(jsonMedia)).build()
        authClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    json.decodeFromString(LoginResponse.serializer(), text).error
                }.getOrNull()
                throw AuthException(msg ?: "Request failed (${resp.code})")
            }
            return json.decodeFromString(deserializer, text)
        }
    }
}
