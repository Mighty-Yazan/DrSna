package com.example.demo.security

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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository
) {

    companion object {

        /**
         * Access token lifetime.
         *
         * Required application behavior:
         * approximately 1 minute.
         */
        private const val ACCESS_TOKEN_MINUTES = 60L

        /**
         * Refresh session lifetime.
         */
        private const val REFRESH_TOKEN_DAYS = 7L
    }

    // =========================================================================
    // ACCESS TOKEN
    // =========================================================================

    /**
     * Generates an access token linked to exactly one refresh session.
     *
     * The JWT contains:
     *
     * - userId
     * - roles
     * - securityVersion
     * - sessionId
     * - sessionVersion
     * - jti
     *
     * SecurityConfig validates these values against the current
     * database state.
     */
    fun generateAccessToken(
        user: User,
        session: RefreshToken
    ): String {

        val userId =
            user.id
                ?: throw IllegalStateException(
                    "User ID is required to issue an access token"
                )

        val now =
            Instant.now()

        val claims =
            JwtClaimsSet
                .builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(
                    now.plus(
                        ACCESS_TOKEN_MINUTES,
                        ChronoUnit.MINUTES
                    )
                )
                .subject(
                    user.email
                )
                .id(
                    UUID.randomUUID().toString()
                )
                .claim(
                    "userId",
                    userId.toString()
                )
                .claim(
                    "roles",
                    listOf(
                        "ROLE_${user.role.name}"
                    )
                )
                .claim(
                    "securityVersion",
                    user.securityVersion
                )
                .claim(
                    "sessionId",
                    session.sessionId.toString()
                )
                .claim(
                    "sessionVersion",
                    session.tokenVersion
                )
                .build()

        val parameters =
            JwtEncoderParameters.from(
                JwsHeader.with {
                    "RS256"
                }.build(),
                claims
            )

        return encoder
            .encode(parameters)
            .tokenValue
    }

    // =========================================================================
    // CREATE REFRESH SESSION
    // =========================================================================

    /**
     * Creates a completely new refresh session.
     *
     * Every successful login creates its own session.
     *
     * Initial values:
     *
     * tokenVersion    = 0
     * securityVersion = current user.securityVersion
     */
    @Transactional
    fun createRefreshSession(
        user: User
    ): RefreshToken {

        val refreshToken =
            RefreshToken(
                token =
                    UUID.randomUUID().toString(),
                user =
                    user,
                expiryDate =
                    Instant.now().plus(
                        REFRESH_TOKEN_DAYS,
                        ChronoUnit.DAYS
                    ),
                sessionId =
                    UUID.randomUUID(),
                tokenVersion =
                    0,
                securityVersion =
                    user.securityVersion
            )

        return refreshTokenRepository.save(
            refreshToken
        )
    }

    // =========================================================================
    // ROTATE REFRESH TOKEN
    // =========================================================================

    /**
     * Rotates one refresh token into a new refresh token.
     *
     * Security rules:
     *
     * 1. Lock USER first.
     * 2. Lock REFRESH TOKEN second.
     *
     * This keeps the same lock ordering used by
     * security-sensitive account operations.
     *
     * Old refresh token:
     *     immediately deleted
     *
     * New refresh token:
     *     same sessionId
     *     tokenVersion + 1
     *
     * Therefore the old Access Token, which carries
     * the previous sessionVersion, becomes invalid immediately.
     */
    @Transactional
    fun rotateRefreshToken(
        rawToken: String
    ): Pair<String, RefreshToken>? {

        // ---------------------------------------------------------------------
        // 1. Find candidate token
        // ---------------------------------------------------------------------

        /*
         * This first query is intentionally NOT locked.
         *
         * It only identifies the user that owns the presented
         * refresh token.
         */
        val candidate =
            refreshTokenRepository
                .findByToken(rawToken)
                ?: return null

        val userId =
            candidate.user.id
                ?: return null

        // ---------------------------------------------------------------------
        // 2. Lock USER
        // ---------------------------------------------------------------------

        /*
         * The user is locked before the refresh token.
         *
         * This serializes token rotation with:
         *
         * - password changes
         * - securityVersion changes
         * - doctor deactivation
         * - clinic deactivation
         * - account reset
         */
        val user =
            userRepository.findFirstById(
                userId
            )
                ?: return null

        // ---------------------------------------------------------------------
        // 3. Lock REFRESH TOKEN
        // ---------------------------------------------------------------------

        val existingToken =
            refreshTokenRepository
                .findByTokenForUpdate(
                    rawToken
                )
                ?: return null

        // ---------------------------------------------------------------------
        // 4. Verify ownership
        // ---------------------------------------------------------------------

        if (
            existingToken.user.id !=
            user.id
        ) {
            return null
        }

        // ---------------------------------------------------------------------
        // 5. Verify expiry
        // ---------------------------------------------------------------------

        if (
            existingToken.isExpired()
        ) {

            refreshTokenRepository.delete(
                existingToken
            )

            refreshTokenRepository.flush()

            return null
        }

        // ---------------------------------------------------------------------
        // 6. Verify account is active
        // ---------------------------------------------------------------------

        if (!user.isActive) {

            /*
             * The account has been disabled.
             *
             * The presented refresh token is removed as well
             * so it cannot accumulate in the database.
             */
            refreshTokenRepository.delete(
                existingToken
            )

            refreshTokenRepository.flush()

            return null
        }

        // ---------------------------------------------------------------------
        // 7. Verify clinic application state
        // ---------------------------------------------------------------------

        /*
         * A Clinic is allowed to have an authentication session
         * only while its application status is APPROVED.
         *
         * This check is intentionally performed during refresh
         * as well as during login.
         *
         * This prevents an old refresh session from restoring
         * access after the clinic is no longer operational.
         */
        if (
            user.role == Role.CLINIC
        ) {

            val clinic =
                clinicRepository
                    .findByUserEmail(
                        user.email
                    )
                    .orElse(null)

            /*
             * If the clinic profile no longer exists or its status
             * is not APPROVED, the refresh session cannot continue.
             */
            if (
                clinic == null ||
                clinic.applicationStatus !=
                ClinicApplicationStatus.APPROVED
            ) {

                refreshTokenRepository.delete(
                    existingToken
                )

                refreshTokenRepository.flush()

                return null
            }
        }

        // ---------------------------------------------------------------------
        // 8. Verify security version
        // ---------------------------------------------------------------------

        /*
         * Password changes, account resets and other global
         * security operations increment securityVersion.
         *
         * Therefore every old refresh session automatically
         * becomes invalid.
         */
        if (
            existingToken.securityVersion !=
            user.securityVersion
        ) {

            refreshTokenRepository.delete(
                existingToken
            )

            refreshTokenRepository.flush()

            return null
        }

        // ---------------------------------------------------------------------
        // 9. Rotate session version
        // ---------------------------------------------------------------------

        val sessionId =
            existingToken.sessionId

        val nextVersion =
            existingToken.tokenVersion + 1

        /*
         * Delete the old token BEFORE creating the new one.
         *
         * flush() forces Hibernate to execute the DELETE
         * before the replacement is inserted.
         */
        refreshTokenRepository.delete(
            existingToken
        )

        refreshTokenRepository.flush()

        // ---------------------------------------------------------------------
        // 10. Create replacement refresh token
        // ---------------------------------------------------------------------

        /*
         * The sessionId remains the same.
         *
         * Only tokenVersion changes.
         *
         * Example:
         *
         * old:
         *     sessionId = A
         *     tokenVersion = 0
         *
         * new:
         *     sessionId = A
         *     tokenVersion = 1
         */
        val newRefreshToken =
            RefreshToken(
                token =
                    UUID.randomUUID().toString(),
                user =
                    user,
                expiryDate =
                    Instant.now().plus(
                        REFRESH_TOKEN_DAYS,
                        ChronoUnit.DAYS
                    ),
                sessionId =
                    sessionId,
                tokenVersion =
                    nextVersion,
                securityVersion =
                    user.securityVersion
            )

        val savedToken =
            refreshTokenRepository.save(
                newRefreshToken
            )

        return Pair(
            savedToken.token,
            savedToken
        )
    }

    // =========================================================================
    // DELETE ONE REFRESH TOKEN
    // =========================================================================

    /**
     * Deletes exactly one refresh token.
     *
     * Used during logout.
     */
    @Transactional
    fun deleteByToken(
        token: String
    ) {

        val existing =
            refreshTokenRepository
                .findByToken(
                    token
                )

        if (existing != null) {

            refreshTokenRepository.delete(
                existing
            )

            refreshTokenRepository.flush()
        }
    }

    // =========================================================================
    // REVOKE ALL REFRESH TOKENS
    // =========================================================================

    /**
     * Deletes every refresh session belonging to a user.
     *
     * Used by SessionRevocationService when a security-sensitive
     * operation occurs.
     */
    @Transactional
    fun revokeAllRefreshTokens(
        user: User
    ) {

        refreshTokenRepository
            .deleteByUser(
                user
            )

        refreshTokenRepository.flush()
    }
}