package com.example.demo.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Produces the deterministic, non-reversible value stored for a refresh token.
 *
 * The browser receives the raw token. The database receives only:
 *
 *     HMAC-SHA-256(server-secret, raw-token)
 *
 * A keyed HMAC is used instead of a plain hash so that an attacker who gets a
 * database dump cannot cheaply precompute hashes of candidate tokens.
 */
@Component
class RefreshTokenHasher(
    @Value("\${security.refresh-token.hmac-secret}")
    secret: String
) {

    private val key: SecretKeySpec

    init {
        val decoded = try {
            Base64.getDecoder().decode(secret)
        } catch (ex: IllegalArgumentException) {
            throw IllegalStateException(
                "security.refresh-token.hmac-secret must be a Base64-encoded secret",
                ex
            )
        }

        require(decoded.size >= 32) {
            "security.refresh-token.hmac-secret must contain at least 32 bytes"
        }

        key = SecretKeySpec(decoded, "HmacSHA256")
    }

    fun hash(rawToken: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val digest = mac.doFinal(rawToken.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun matches(rawToken: String, storedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(rawToken).toByteArray(StandardCharsets.UTF_8),
            storedHash.toByteArray(StandardCharsets.UTF_8)
        )
}
