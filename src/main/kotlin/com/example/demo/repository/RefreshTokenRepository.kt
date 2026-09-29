package com.example.demo.repository

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    /*
     * The database stores only tokenHash, never the raw refresh token.
     */
    fun findByTokenHash(
        tokenHash: String
    ): RefreshToken?

    /*
     * Locked lookup used during refresh-token rotation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select r from RefreshToken r where r.tokenHash = :tokenHash"
    )
    fun findByTokenHashForUpdate(
        @Param("tokenHash") tokenHash: String
    ): RefreshToken?

    /*
     * Finds the currently active refresh session for a browser/device.
     */
    fun findBySessionId(
        sessionId: UUID
    ): RefreshToken?

    /*
     * Deletes every refresh session belonging to a user.
     */
    fun deleteByUser(
        user: User
    )

    /*
     * Deletes expired refresh sessions.
     */
    fun deleteByExpiryDateBefore(
        now: Instant
    ): Long
}
