package com.example.demo.security

import com.example.demo.repository.RefreshTokenRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Periodically removes refresh-token hashes that are past their expiry. */
@Service
class RefreshTokenCleanupService(
    private val refreshTokenRepository: RefreshTokenRepository
) {

    @Scheduled(fixedDelayString = "\${security.refresh-token.cleanup-interval-ms:3600000}")
    @Transactional
    fun deleteExpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now())
    }
}
