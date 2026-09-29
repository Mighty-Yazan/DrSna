package com.example.demo.security

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import com.example.demo.repository.RefreshTokenRepository
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    fun generateAccessToken(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(60, ChronoUnit.MINUTES))
            .subject(user.email)
            .id(UUID.randomUUID().toString()) // JTI
            .claim("userId", user.id.toString())
            .claim("roles", listOf("ROLE_${user.role.name}"))
            .claim("securityVersion", user.securityVersion)
            .build()

        val parameters = JwtEncoderParameters.from(JwsHeader.with { "RS256" }.build(), claims)
        return encoder.encode(parameters).tokenValue
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hashing helper
    // ─────────────────────────────────────────────────────────────────────────

    /*
     * Produces a hex-encoded SHA-256 digest of the raw token string.
     *
     * WHY SHA-256 and not bcrypt?
     *   Refresh tokens are randomly generated UUIDs (128-bit entropy).
     *   They are not user-chosen passwords, so brute-force dictionaries
     *   are useless against them. SHA-256 is fast enough for our lookup
     *   path and still provides a one-way, non-reversible representation.
     *
     * WHY no salt?
     *   A salt protects against rainbow-table attacks on LOW-entropy inputs
     *   (e.g., "password123"). Our tokens already have 128 bits of random
     *   entropy — a pre-computed table for UUID space is computationally
     *   infeasible. Adding a per-row salt would only complicate the lookup
     *   without adding meaningful security here.
     */
    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        // Convert each byte to a 2-char lowercase hex string, then join them all
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh token lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /*
     * Generates a new refresh token.
     * Cleans up existing tokens for this user to prevent accumulation.
     *
     * Returns the RAW token string — this is the ONLY moment the raw value
     * exists outside of the browser cookie. It is returned to the caller so
     * it can be placed into the HttpOnly cookie, then immediately discarded.
     * The DB row stores only the SHA-256 hash.
     */
    @Transactional
    fun generateRefreshToken(user: User): String {
        refreshTokenRepository.deleteByUser(user) // Remove any previous session

        val rawToken = UUID.randomUUID().toString()

        val refreshToken = RefreshToken(
            // Store the HASH, not the raw value
            tokenHash = hashToken(rawToken),
            user = user,
            expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)

        // Return the raw token so it can be placed into the cookie
        return rawToken
    }

    /*
     * Validates the raw token from the cookie, deletes it, and issues a new one.
     *
     * The lock (PESSIMISTIC_WRITE) inside findByTokenHashForUpdate() prevents
     * two concurrent requests from rotating the same token simultaneously.
     *
     * Returns Pair(newRawToken, savedRefreshTokenEntity) or null if invalid.
     */
    @Transactional
    fun rotateRefreshToken(rawToken: String): Pair<String, RefreshToken>? {
        // 1. Hash the incoming raw token, then look up the DB row by its hash
        val hash = hashToken(rawToken)
        val existingToken = refreshTokenRepository.findByTokenHashForUpdate(hash) ?: return null

        // 2. Check expiry
        if (existingToken.isExpired()) {
            refreshTokenRepository.delete(existingToken)
            return null
        }

        val user = existingToken.user

        // 3. Delete the old row — it is now consumed (single-use)
        refreshTokenRepository.delete(existingToken)

        // 4. Generate a new raw token, store only its hash
        val newRawToken = UUID.randomUUID().toString()
        val newRefreshToken = RefreshToken(
            tokenHash = hashToken(newRawToken),
            user = user,
            expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
        )

        val savedToken = refreshTokenRepository.save(newRefreshToken)
        return Pair(newRawToken, savedToken)
    }

    /*
     * Deletes the refresh session matching the raw token from the logout cookie.
     * We hash first, then look up by hash — same pattern as rotation.
     */
    @Transactional
    fun deleteByToken(rawToken: String) {
        val hash = hashToken(rawToken)
        val existing = refreshTokenRepository.findByTokenHash(hash)
        if (existing != null) {
            refreshTokenRepository.delete(existing)
        }
    }

    /*
     * Revokes ALL active refresh sessions for a given user.
     *
     * Called by SessionRevocationService when a security-sensitive event
     * happens (e.g. password change, account deactivation).
     *
     * No hashing is needed here — we are deleting by User object,
     * not looking up by token value.
     */
    @Transactional
    fun revokeAllRefreshTokens(user: User) {
        refreshTokenRepository.deleteByUser(user)
    }
}
