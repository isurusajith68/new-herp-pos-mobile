package com.example.posmobile.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE helpers (RFC 7636) — mirrors the web auth-sdk's S256 flow. */
object Pkce {
    private val random = SecureRandom()

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** 43–128 char high-entropy verifier. */
    fun verifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    /** S256 challenge = BASE64URL(SHA-256(verifier)). */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }
}
