package com.example.demo.security

import com.example.demo.exception.RefreshTokenReuseException
import com.example.demo.model.ClinicApplicationStatus
import com.example.demo.model.RefreshToken
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.repository.UserRepository
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/** Raw refresh token plus its server-side session entity. */
data class CreatedRefreshSession(
    val rawToken: String,
    val session: RefreshToken
)

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val refreshTokenHasher: RefreshTokenHasher
) {

    companion object {
        private const val ACCESS_TOKEN_MINUTES = 60L
        private const val REFRESH_TOKEN_DAYS = 7L
        private const val REFRESH_TOKEN_BYTES = 32
        private val secureRandom = SecureRandom()
    }

    fun generateAccessToken(user: User, session: RefreshToken): String {
        val userId = user.id ?: throw IllegalStateException("User ID is required to issue an access token")
        val now = Instant.now()

        val claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
            .subject(user.email)
            .id(UUID.randomUUID().toString())
            .claim("userId", userId.toString())
            .claim("roles", listOf("ROLE_${user.role.name}"))
            .claim("securityVersion", user.securityVersion)
            .claim("sessionId", session.sessionId.toString())
            .claim("sessionVersion", session.tokenVersion)
            .build()

        val parameters = JwtEncoderParameters.from(
            JwsHeader.with { "RS256" }.build(),
            claims
        )

        return encoder.encode(parameters).tokenValue
    }

    /**
     * Creates a new refresh-token family.
     *
     * Only the HMAC digest is persisted. The raw 256-bit value is returned once
     * to the controller so it can be placed in the HttpOnly cookie.
     */
    @Transactional
    fun createRefreshSession(user: User): CreatedRefreshSession {
        val rawToken = generateOpaqueRefreshToken()
        val session = RefreshToken(
            tokenHash = refreshTokenHasher.hash(rawToken),
            user = user,
            expiryDate = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS),
            sessionId = UUID.randomUUID(),
            tokenVersion = 0,
            securityVersion = user.securityVersion
        )

        return CreatedRefreshSession(
            rawToken = rawToken,
            session = refreshTokenRepository.save(session)
        )
    }

    /**
     * Rotates a refresh token.
     *
     * Every token in a family remains represented by a one-way HMAC digest.
     * Rotated/revoked rows are retained until expiry so that replaying an old
     * token can be detected. A replay revokes the entire family.
     */
    @Transactional
    fun rotateRefreshToken(rawToken: String): Pair<String, RefreshToken>? {
        val tokenHash = refreshTokenHasher.hash(rawToken)

        val candidate = refreshTokenRepository.findByTokenHash(tokenHash) ?: return null
        val userId = candidate.user.id ?: return null

        // Preserve the project's existing lock ordering: USER first, token second.
        val user = userRepository.findFirstById(userId) ?: return null
        val existingToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash) ?: return null

        if (existingToken.user.id != user.id) return null

        if (existingToken.isExpired()) {
            refreshTokenRepository.delete(existingToken)
            refreshTokenRepository.flush()
            return null
        }

        // A previously rotated/revoked token has been presented again.
        // Treat this as refresh-token reuse and kill the complete family.
        if (existingToken.isRevoked()) {
            refreshTokenRepository.revokeActiveBySessionId(
                existingToken.sessionId,
                Instant.now()
            )
            refreshTokenRepository.flush()
            throw RefreshTokenReuseException()
        }

        if (!user.isActive) {
            revokeFamily(existingToken)
            return null
        }

        if (user.role == Role.CLINIC) {
            val clinic = clinicRepository.findByUserEmail(user.email).orElse(null)
            if (clinic == null || clinic.applicationStatus != ClinicApplicationStatus.APPROVED) {
                revokeFamily(existingToken)
                return null
            }
        }

        if (existingToken.securityVersion != user.securityVersion) {
            revokeFamily(existingToken)
            return null
        }

        val sessionId = existingToken.sessionId
        val nextVersion = existingToken.tokenVersion + 1

        // Keep the old HMAC row so replay can be detected later.
        existingToken.revokedAt = Instant.now()
        refreshTokenRepository.saveAndFlush(existingToken)

        val newRawToken = generateOpaqueRefreshToken()
        val newRefreshToken = RefreshToken(
            tokenHash = refreshTokenHasher.hash(newRawToken),
            user = user,
            expiryDate = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS),
            sessionId = sessionId,
            tokenVersion = nextVersion,
            securityVersion = user.securityVersion
        )

        val savedToken = refreshTokenRepository.save(newRefreshToken)
        return Pair(newRawToken, savedToken)
    }

    /** Used by logout; the raw bearer token is converted to its HMAC first. */
    @Transactional
    fun deleteByToken(rawToken: String) {
        val tokenHash = refreshTokenHasher.hash(rawToken)
        val existing = refreshTokenRepository.findByTokenHashForUpdate(tokenHash) ?: return

        if (existing.revokedAt == null) {
            existing.revokedAt = Instant.now()
            refreshTokenRepository.saveAndFlush(existing)
        }
    }

    /**
     * Revokes all active refresh sessions for a user without retaining any raw
     * token value. Historical HMAC rows remain until their expiry for replay
     * detection/auditing.
     */
    @Transactional
    fun revokeAllRefreshTokens(user: User) {
        refreshTokenRepository.revokeActiveByUser(user, Instant.now())
        refreshTokenRepository.flush()
    }

    private fun revokeFamily(token: RefreshToken) {
        refreshTokenRepository.revokeActiveBySessionId(token.sessionId, Instant.now())
        refreshTokenRepository.flush()
    }

    private fun generateOpaqueRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Transactional
    fun revokeRefreshSession(sessionId: UUID) {
        refreshTokenRepository.revokeActiveBySessionId(
            sessionId,
            Instant.now()
        )

        refreshTokenRepository.flush()
    }
}
