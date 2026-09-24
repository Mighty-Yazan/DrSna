package com.example.demo.security

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory JWT token blacklist based on JTI (JWT ID).
 * Tokens are stored until their original expiry time, then cleaned up.
 * Suitable for single-instance MVP; use Redis for production multi-instance deployments.
 */
@Service
class TokenBlacklistService {


    private val blacklistedTokens = ConcurrentHashMap<String, Instant>()
    private val userRevokedAfter = ConcurrentHashMap<String, Instant>()
    /**
     * Add a token's JTI to the blacklist.
     * @param jti The JWT ID to blacklist
     * @param expiresAt The token's original expiry time (used for cleanup)
     */
    fun blacklist(jti: String, expiresAt: Instant) {
        blacklistedTokens[jti] = expiresAt
    }

    /**
     * Check if a token's JTI has been blacklisted.
     */
    fun isBlacklisted(jti: String): Boolean {
        return blacklistedTokens.containsKey(jti)
    }
    /**
     * Revokes all access tokens issued for a user before this exact instant.
     */
    fun revokeAllForUser(userId: String) {
        userRevokedAfter[userId] = Instant.now()
    }
    fun isUserTokenRevoked(userId: String, issuedAt: Instant): Boolean {
        val revokedTime = userRevokedAfter[userId] ?: return false
        return issuedAt.isBefore(revokedTime)
    }
    /**
     * Periodically remove expired entries from the blacklist (every 10 minutes).
     */
    @Scheduled(fixedRate = 600_000)
    fun cleanup() {
        val now = Instant.now()
        blacklistedTokens.entries.removeIf { it.value.isBefore(now) }
        // Clean up user revocation timestamps older than max access token lifespan (60 minutes)
        userRevokedAfter.entries.removeIf { it.value.isBefore(now.minusSeconds(3600)) }
    }
}