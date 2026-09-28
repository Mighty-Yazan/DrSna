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
     * Normal lookup.
     *
     * Used when we only need to find whether a refresh token exists
     * and which user owns it.
     */
    fun findByToken(
        token: String
    ): RefreshToken?

    /*
     * Locked lookup used during refresh-token rotation.
     *
     * PESSIMISTIC_WRITE guarantees that two concurrent requests
     * cannot successfully rotate the same refresh token.
     *
     * Example:
     *
     * Request A -> finds Token A and locks it
     * Request B -> tries to find Token A with the same lock
     * Request B waits
     *
     * Request A -> deletes Token A
     * Request A -> creates Token B
     *
     * Request B -> re-checks Token A
     * Token A no longer exists
     * Request B -> returns INVALID
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select r from RefreshToken r where r.token = :token"
    )
    fun findByTokenForUpdate(
        @Param("token") token: String
    ): RefreshToken?

    /*
     * Finds the currently active refresh session for a browser/device.
     *
     * Every login creates its own sessionId.
     *
     * Example:
     *
     * Laptop -> sessionId A
     * Phone  -> sessionId B
     *
     * Both sessions can exist at the same time.
     */
    fun findBySessionId(
        sessionId: UUID
    ): RefreshToken?

    /*
     * Used when a security-sensitive change happens.
     *
     * Example:
     *
     * Password changed
     * Account deactivated
     * Admin reset
     * Clinic deactivated
     * Doctor deactivated
     *
     * All refresh sessions belonging to that user are deleted.
     */
    fun deleteByUser(
        user: User
    )

    /*
     * Optional cleanup method for expired refresh sessions.
     *
     * Can later be used by a scheduled cleanup job.
     */
    fun deleteByExpiryDateBefore(
        now: Instant
    ): Long
}