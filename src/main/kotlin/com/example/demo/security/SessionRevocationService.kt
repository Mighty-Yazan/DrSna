package com.example.demo.security

import com.example.demo.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Centralized service for revoking all active sessions of a user.
 *
 * This service is used whenever a security-sensitive change happens,
 * such as:
 * - password change
 * - account deactivation
 * - doctor deactivation
 * - clinic deactivation
 * - administrative password reset
 *
 * The combination of:
 * 1. incrementing securityVersion
 * 2. deleting all refresh sessions
 *
 * makes both existing access tokens and refresh tokens invalid.
 */
@Service
class SessionRevocationService(
    private val tokenService: TokenService
) {

    /**
     * Revokes every active session belonging to the given user.
     *
     * securityVersion invalidates all previously issued JWT access tokens.
     *
     * Removing all refresh tokens prevents old refresh cookies/tokens
     * from creating new access tokens.
     */
    @Transactional
    fun revokeAllSessions(user: User) {
        user.securityVersion += 1

        tokenService.revokeAllRefreshTokens(user)
    }
}